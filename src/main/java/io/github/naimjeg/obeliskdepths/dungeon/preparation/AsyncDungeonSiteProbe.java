package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import io.github.naimjeg.obeliskdepths.dungeon.site.reader.DungeonPersistedChunkProbeBackend;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;

import static io.github.naimjeg.obeliskdepths.dungeon.preparation.AsyncDungeonSiteProbeState.*;

/**
 * Bounded asynchronous scanner that probes persisted chunk status for an
 * ordered list of candidate dungeon site keys.
 *
 * <p>All mutable scanner state is owner-thread confined. A backend future may
 * complete on any thread; its callback only appends an immutable completion
 * envelope to a thread-safe mailbox and releases the physical-I/O permit.
 * Owner ticking later drains that mailbox under the existing count budget and
 * performs every counter, state, and report mutation. No completion callback
 * dispatches to the server executor or submits replacement work.</p>
 *
 * <p>Actual outstanding backend futures are bounded by the level-owned
 * {@link DungeonPersistedProbePermitPool} supplied during progression. Logical
 * cancellation publishes once on the owner thread, while a late physical
 * completion only releases its permit and is ignored when its envelope is
 * eventually drained.</p>
 */
public final class AsyncDungeonSiteProbe {
    public static final int DEFAULT_MAX_CONCURRENT_PROBES = 4;

    private final List<DungeonSiteKey> candidates;
    private final ChunkStatus requiredStatus;
    private final int maxConcurrentProbes;
    private final DungeonPersistedChunkProbeBackend backend;
    private final Builder reportBuilder;
    private final ConcurrentLinkedQueue<ProbeCompletion> completionMailbox =
            new ConcurrentLinkedQueue<>();

    private int generation;
    private int nextCandidateIndex;
    private int inFlight;
    private int completed;
    private int peakInFlight;
    private AsyncDungeonSiteProbeState state;
    private boolean drainingCompletionMailbox;

    /*
     * At most one inspected candidate may be waiting for a real backend permit.
     * Its loaded fast path has already been checked, so a later Tick does not
     * repeat the loaded-world read merely because global I/O capacity was full.
     */
    private int pendingPersistedCandidateIndex = -1;
    private DungeonSiteKey pendingPersistedCandidate;
    private ChunkPos pendingPersistedChunkPos;

    private final CompletableFuture<DungeonSiteProbeReport> completionFuture =
            new CompletableFuture<>();

    AsyncDungeonSiteProbe(
            List<DungeonSiteKey> candidates,
            ChunkStatus requiredStatus,
            int maxConcurrentProbes,
            DungeonPersistedChunkProbeBackend backend
    ) {
        this.candidates = copyCandidates(candidates);
        this.requiredStatus = Objects.requireNonNull(
                requiredStatus,
                "requiredStatus"
        );
        this.backend = Objects.requireNonNull(backend, "backend");
        if (maxConcurrentProbes <= 0) {
            throw new IllegalArgumentException(
                    "maxConcurrentProbes must be greater than zero"
            );
        }
        this.maxConcurrentProbes = maxConcurrentProbes;
        this.reportBuilder = new Builder(this.candidates.size());
        this.state = CREATED;
    }

    public static AsyncDungeonSiteProbe createForLevel(
            ServerLevel level,
            List<DungeonSiteKey> candidates,
            ChunkStatus requiredStatus,
            int maxConcurrentProbes
    ) {
        Objects.requireNonNull(level, "level");
        return new AsyncDungeonSiteProbe(
                candidates,
                requiredStatus,
                maxConcurrentProbes,
                DungeonPersistedChunkProbeBackend.forLevel(level)
        );
    }

    public static AsyncDungeonSiteProbe createForLevel(
            ServerLevel level,
            List<DungeonSiteKey> candidates,
            ChunkStatus requiredStatus
    ) {
        return createForLevel(
                level,
                candidates,
                requiredStatus,
                DEFAULT_MAX_CONCURRENT_PROBES
        );
    }

    public AsyncDungeonSiteProbeState state() {
        assertOwnerThread();
        return this.state;
    }

    public CompletionStage<DungeonSiteProbeReport> completion() {
        return this.completionFuture.minimalCompletionStage();
    }

    public DungeonSiteProbeProgress progress() {
        assertOwnerThread();
        int submittedCandidates = this.completed + this.inFlight;
        return new DungeonSiteProbeProgress(
                this.candidates.size(),
                submittedCandidates,
                this.completed,
                this.inFlight,
                this.reportBuilder.availableCount,
                this.reportBuilder.notPersistedCount,
                this.reportBuilder.belowStatusCount,
                this.reportBuilder.failedCount,
                this.reportBuilder.malformedCount,
                this.reportBuilder.cancelledCount,
                this.state
        );
    }

    int pendingCompletionCount() {
        assertOwnerThread();
        return this.completionMailbox.size();
    }

    int currentlyInFlight() {
        assertOwnerThread();
        return this.inFlight;
    }

    int nextCandidateIndex() {
        assertOwnerThread();
        return this.nextCandidateIndex;
    }

    boolean hasSubmissionWork() {
        assertOwnerThread();
        return this.state == RUNNING
                && (hasPendingPersistedCandidate()
                || this.nextCandidateIndex < this.candidates.size());
    }

    /** Begins scanning. Must be called once, on the owner thread. */
    public void start() {
        assertOwnerThread();
        if (this.state != CREATED) {
            throw new IllegalStateException(
                    "Cannot start scan from state " + this.state
            );
        }

        if (this.candidates.isEmpty()) {
            this.state = COMPLETED;
            publishReport(false);
            return;
        }

        this.state = RUNNING;
    }

    /**
     * Cancels logical scan publication. Already submitted backend futures keep
     * their level permit until their real completion callback runs.
     */
    public void cancel() {
        assertOwnerThread();
        if (this.state.isTerminal()) {
            return;
        }

        this.state = CANCELLED;
        this.generation++;
        this.reportBuilder.cancelUnresolved(this.candidates);
        this.inFlight = 0;
        clearPendingPersistedCandidate();
        this.nextCandidateIndex = this.candidates.size();
        this.completed = this.candidates.size();
        publishReport(true);
    }

    /**
     * Advances loaded checks and real persisted-probe submissions under exact
     * per-Tick allowances. The returned counts are the operations actually
     * performed and should be charged to the shared level Tick budget.
     */
    SubmissionProgress advanceSubmissions(
            int maxCandidateInspections,
            int maxLoadedFastPathProbes,
            int maxPersistedProbeSubmissions,
            DungeonPersistedProbePermitPool permitPool
    ) {
        assertOwnerThread();
        Objects.requireNonNull(permitPool, "permitPool");
        if (maxCandidateInspections < 0
                || maxLoadedFastPathProbes < 0
                || maxPersistedProbeSubmissions < 0) {
            throw new IllegalArgumentException(
                    "submission allowances must be non-negative"
            );
        }

        int candidateInspections = 0;
        int loadedFastPathChecks = 0;
        int persistedProbeSubmissions = 0;

        while (this.state == RUNNING) {
            if (hasPendingPersistedCandidate()) {
                if (this.inFlight >= this.maxConcurrentProbes
                        || persistedProbeSubmissions
                        >= maxPersistedProbeSubmissions) {
                    break;
                }

                DungeonPersistedProbePermitPool.Permit permit =
                        permitPool.tryAcquire();
                if (permit == null) {
                    break;
                }

                int candidateIndex = this.pendingPersistedCandidateIndex;
                DungeonSiteKey key = this.pendingPersistedCandidate;
                ChunkPos chunkPos = this.pendingPersistedChunkPos;
                clearPendingPersistedCandidate();
                submitAsyncProbe(
                        candidateIndex,
                        key,
                        chunkPos,
                        permit
                );
                persistedProbeSubmissions++;
                checkComplete();
                continue;
            }

            if (this.nextCandidateIndex >= this.candidates.size()) {
                checkComplete();
                break;
            }
            if (candidateInspections >= maxCandidateInspections
                    || loadedFastPathChecks >= maxLoadedFastPathProbes) {
                break;
            }

            int candidateIndex = this.nextCandidateIndex;
            DungeonSiteKey key = this.candidates.get(candidateIndex);
            ChunkPos chunkPos = key.toChunkPos();
            this.nextCandidateIndex++;
            candidateInspections++;
            loadedFastPathChecks++;

            if (tryLoadedFastPath(candidateIndex, chunkPos)) {
                checkComplete();
                continue;
            }

            setPendingPersistedCandidate(candidateIndex, key, chunkPos);
        }

        return new SubmissionProgress(
                candidateInspections,
                loadedFastPathChecks,
                persistedProbeSubmissions
        );
    }

    private boolean tryLoadedFastPath(
            int candidateIndex,
            ChunkPos chunkPos
    ) {
        Optional<DungeonPersistedChunkProbeResult> loadedResult;
        try {
            loadedResult = this.backend.probeLoadedChunk(
                    chunkPos,
                    this.requiredStatus
            );
        } catch (RuntimeException exception) {
            recordCandidateResult(
                    candidateIndex,
                    failedResult(
                            chunkPos,
                            exception,
                            "Loaded chunk probe failed"
                    )
            );
            return true;
        }

        if (loadedResult == null) {
            recordCandidateResult(
                    candidateIndex,
                    failedResult(
                            chunkPos,
                            null,
                            "Loaded chunk probe returned null Optional"
                    )
            );
            return true;
        }

        if (loadedResult.isEmpty()) {
            return false;
        }

        recordCandidateResult(
                candidateIndex,
                normalizeBackendResult(
                        chunkPos,
                        loadedResult.get(),
                        null
                )
        );
        return true;
    }

    private void submitAsyncProbe(
            int candidateIndex,
            DungeonSiteKey key,
            ChunkPos chunkPos,
            DungeonPersistedProbePermitPool.Permit permit
    ) {
        this.inFlight++;
        if (this.inFlight > this.peakInFlight) {
            this.peakInFlight = this.inFlight;
        }

        int capturedGeneration = this.generation;
        CompletableFuture<DungeonPersistedChunkProbeResult> future;
        try {
            future = this.backend.probe(chunkPos, this.requiredStatus);
        } catch (RuntimeException exception) {
            this.inFlight--;
            permit.close();
            recordCandidateResult(
                    candidateIndex,
                    failedResult(
                            chunkPos,
                            exception,
                            "Backend probe threw before returning a future"
                    )
            );
            return;
        }

        if (future == null) {
            this.inFlight--;
            permit.close();
            recordCandidateResult(
                    candidateIndex,
                    failedResult(
                            chunkPos,
                            new IllegalStateException(
                                    "Backend returned a null probe future"
                            ),
                            "Backend returned a null probe future"
                    )
            );
            return;
        }

        future.whenComplete((result, throwable) -> {
            try {
                this.completionMailbox.add(new ProbeCompletion(
                        capturedGeneration,
                        candidateIndex,
                        key,
                        chunkPos,
                        result,
                        throwable
                ));
            } finally {
                /*
                 * Release only when the real backend future is complete. This
                 * executes even when the scanner was cancelled or owner
                 * dispatch rejects the late logical mutation.
                 */
                permit.close();
            }
        });
    }

    /**
     * Applies immutable completion envelopes on the owner thread. Enqueueing
     * is thread-safe; all scanner mutation remains here and is count-budgeted.
     */
    int drainCompletionMailbox(int maxCompletions) {
        assertOwnerThread();
        if (maxCompletions < 0) {
            throw new IllegalArgumentException(
                    "maxCompletions must be non-negative"
            );
        }
        if (maxCompletions == 0 || this.drainingCompletionMailbox) {
            return 0;
        }

        int drained = 0;
        this.drainingCompletionMailbox = true;
        try {
            ProbeCompletion completion;
            while (drained < maxCompletions
                    && (completion = this.completionMailbox.poll()) != null) {
                onProbeComplete(
                        completion.capturedGeneration(),
                        completion.candidateIndex(),
                        completion.key(),
                        completion.chunkPos(),
                        completion.result(),
                        completion.failure()
                );
                drained++;
            }
        } finally {
            this.drainingCompletionMailbox = false;
        }
        return drained;
    }

    private void onProbeComplete(
            int capturedGeneration,
            int capturedIndex,
            DungeonSiteKey key,
            ChunkPos chunkPos,
            DungeonPersistedChunkProbeResult result,
            Throwable throwable
    ) {
        assertOwnerThread();
        Objects.requireNonNull(key, "key");
        if (this.state.isTerminal()
                || capturedGeneration != this.generation) {
            return;
        }

        if (this.inFlight <= 0) {
            throw new IllegalStateException(
                    "Probe completion observed with no in-flight probe"
            );
        }

        this.inFlight--;
        DungeonPersistedChunkProbeResult normalized =
                normalizeBackendResult(chunkPos, result, throwable);
        recordCandidateResult(capturedIndex, normalized);
        checkComplete();
    }

    private boolean hasPendingPersistedCandidate() {
        return this.pendingPersistedCandidateIndex >= 0;
    }

    private void setPendingPersistedCandidate(
            int candidateIndex,
            DungeonSiteKey key,
            ChunkPos chunkPos
    ) {
        if (hasPendingPersistedCandidate()) {
            throw new IllegalStateException(
                    "A persisted candidate is already waiting for submission"
            );
        }
        this.pendingPersistedCandidateIndex = candidateIndex;
        this.pendingPersistedCandidate = Objects.requireNonNull(key, "key");
        this.pendingPersistedChunkPos = Objects.requireNonNull(
                chunkPos,
                "chunkPos"
        );
    }

    private void clearPendingPersistedCandidate() {
        this.pendingPersistedCandidateIndex = -1;
        this.pendingPersistedCandidate = null;
        this.pendingPersistedChunkPos = null;
    }

    private record ProbeCompletion(
            int capturedGeneration,
            int candidateIndex,
            DungeonSiteKey key,
            ChunkPos chunkPos,
            DungeonPersistedChunkProbeResult result,
            Throwable failure
    ) {
        private ProbeCompletion {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(chunkPos, "chunkPos");
        }
    }

    record SubmissionProgress(
            int candidateInspections,
            int loadedFastPathChecks,
            int persistedProbeSubmissions
    ) {
        SubmissionProgress {
            if (candidateInspections < 0
                    || loadedFastPathChecks < 0
                    || persistedProbeSubmissions < 0) {
                throw new IllegalArgumentException(
                        "submission progress counts must be non-negative"
                );
            }
            if (loadedFastPathChecks > candidateInspections) {
                throw new IllegalArgumentException(
                        "loaded fast-path checks exceed candidate inspections"
                );
            }
        }
    }

    private DungeonPersistedChunkProbeResult normalizeBackendResult(
            ChunkPos requestedChunkPos,
            DungeonPersistedChunkProbeResult result,
            Throwable throwable
    ) {
        if (throwable != null) {
            return failedResult(
                    requestedChunkPos,
                    throwable,
                    "Backend probe future completed exceptionally"
            );
        }

        if (result == null) {
            return failedResult(
                    requestedChunkPos,
                    null,
                    "Backend probe completed with a null result"
            );
        }

        if (!requestedChunkPos.equals(result.chunkPos())) {
            return failedResult(
                    requestedChunkPos,
                    null,
                    "Backend probe returned result for "
                            + result.chunkPos()
                            + " while probing "
                            + requestedChunkPos
            );
        }

        return result;
    }

    private static DungeonPersistedChunkProbeResult failedResult(
            ChunkPos chunkPos,
            Throwable throwable,
            String detail
    ) {
        String fullDetail = detail;
        if (throwable != null) {
            String message = throwable.getMessage();
            fullDetail = detail
                    + ": "
                    + throwable.getClass().getName()
                    + (message == null || message.isBlank() ? "" : ": " + message);
        }
        return new DungeonPersistedChunkProbeResult(
                chunkPos,
                DungeonPersistedChunkProbeResult.Classification.SCAN_FAILED,
                Optional.empty(),
                fullDetail,
                throwable == null ? Optional.empty() : Optional.of(throwable)
        );
    }

    private void recordCandidateResult(
            int candidateIndex,
            DungeonPersistedChunkProbeResult result
    ) {
        assertOwnerThread();
        this.reportBuilder.recordResult(candidateIndex, result);
        this.completed++;
    }

    private void checkComplete() {
        assertOwnerThread();
        if (this.state.isTerminal()) {
            return;
        }
        if (this.completed != this.candidates.size()) {
            return;
        }
        if (this.inFlight != 0 || hasPendingPersistedCandidate()) {
            return;
        }
        this.state = COMPLETED;
        publishReport(false);
    }

    private void publishReport(boolean cancelled) {
        assertOwnerThread();
        this.completionFuture.complete(this.reportBuilder.build(
                this.candidates,
                this.peakInFlight,
                cancelled
        ));
    }

    private void assertOwnerThread() {
        if (!this.backend.isOwnerThread()) {
            throw new IllegalStateException(
                    "AsyncDungeonSiteProbe must be accessed on the owner thread"
            );
        }
    }

    private static List<DungeonSiteKey> copyCandidates(
            List<DungeonSiteKey> candidates
    ) {
        Objects.requireNonNull(candidates, "candidates");
        ArrayList<DungeonSiteKey> copy = new ArrayList<>(candidates.size());
        for (DungeonSiteKey candidate : candidates) {
            copy.add(Objects.requireNonNull(candidate, "candidate"));
        }
        return List.copyOf(copy);
    }

    static final class Builder {
        private final DungeonPersistedChunkProbeResult[] results;
        private final int totalCandidates;
        int availableCount;
        int notPersistedCount;
        int belowStatusCount;
        int failedCount;
        int malformedCount;
        int cancelledCount;

        Builder(int totalCandidates) {
            this.totalCandidates = totalCandidates;
            this.results = new DungeonPersistedChunkProbeResult[totalCandidates];
        }

        void recordResult(
                int index,
                DungeonPersistedChunkProbeResult result
        ) {
            Objects.requireNonNull(result, "result");
            if (index < 0 || index >= this.results.length) {
                throw new IllegalStateException(
                        "Probe result index out of bounds: " + index
                );
            }
            if (this.results[index] != null) {
                throw new IllegalStateException(
                        "Probe result already recorded for index " + index
                );
            }
            this.results[index] = result;
            tally(result);
        }

        int cancelUnresolved(List<DungeonSiteKey> candidates) {
            int newlyCancelled = 0;
            for (int i = 0; i < this.results.length; i++) {
                if (this.results[i] == null) {
                    recordResult(
                            i,
                            new DungeonPersistedChunkProbeResult(
                                    candidates.get(i).toChunkPos(),
                                    DungeonPersistedChunkProbeResult
                                            .Classification.CANCELLED,
                                    Optional.empty(),
                                    "Probe cancelled before completion",
                                    Optional.empty()
                            )
                    );
                    newlyCancelled++;
                }
            }
            return newlyCancelled;
        }

        void tally(DungeonPersistedChunkProbeResult result) {
            switch (result.classification()) {
                case AVAILABLE_AT_REQUIRED_STATUS -> this.availableCount++;
                case NOT_PERSISTED -> this.notPersistedCount++;
                case BELOW_REQUIRED_STATUS -> this.belowStatusCount++;
                case SCAN_FAILED -> this.failedCount++;
                case MALFORMED_STATUS -> this.malformedCount++;
                case CANCELLED -> this.cancelledCount++;
            }
        }

        List<DungeonPersistedChunkProbeResult> buildResults() {
            ArrayList<DungeonPersistedChunkProbeResult> list =
                    new ArrayList<>(this.totalCandidates);
            for (DungeonPersistedChunkProbeResult result : this.results) {
                list.add(result);
            }
            return List.copyOf(list);
        }

        List<DungeonSiteKey> buildAvailableKeys(
                List<DungeonSiteKey> candidates
        ) {
            ArrayList<DungeonSiteKey> availableKeys = new ArrayList<>();
            for (int i = 0; i < this.results.length; i++) {
                DungeonPersistedChunkProbeResult result = this.results[i];
                if (result != null
                        && result.classification()
                        == DungeonPersistedChunkProbeResult.Classification
                        .AVAILABLE_AT_REQUIRED_STATUS) {
                    availableKeys.add(candidates.get(i));
                }
            }
            return List.copyOf(availableKeys);
        }

        DungeonSiteProbeReport build(
                List<DungeonSiteKey> candidates,
                int peakInFlight,
                boolean cancelled
        ) {
            return new DungeonSiteProbeReport(
                    buildResults(),
                    buildAvailableKeys(candidates),
                    this.totalCandidates,
                    this.availableCount,
                    this.notPersistedCount,
                    this.belowStatusCount,
                    this.failedCount,
                    this.malformedCount,
                    this.cancelledCount,
                    peakInFlight,
                    cancelled
            );
        }
    }
}
