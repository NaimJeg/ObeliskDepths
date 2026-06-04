package io.github.naimjeg.obeliskdepths.dungeon.preparation.chunk;

import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationProfiler;
import io.github.naimjeg.obeliskdepths.registry.ModTicketTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

public final class DungeonChunkLeaseManager {
    static final int TERMINAL_RELEASE_DRAIN_PASSES = 3;
    static final int TERMINAL_RELEASE_ATTEMPTS_PER_PASS = 1_024;
    private static final int MAX_REPORTED_UNRESOLVED_POSITIONS = 16;

    private final Map<ChunkPos, LeaseEntry> entries = new LinkedHashMap<>();
    private final DungeonChunkTicketBackend backend;
    private long nextEntryToken;
    private long pendingReleaseFailureCount;
    private int pendingReleaseCursor;
    private boolean cleared;
    private TerminalCleanupResult lastTerminalCleanupResult =
            TerminalCleanupResult.empty();

    DungeonChunkLeaseManager(DungeonChunkTicketBackend backend) {
        this.backend = backend;
        this.nextEntryToken = 0L;
        this.pendingReleaseFailureCount = 0L;
        this.pendingReleaseCursor = 0;
        this.cleared = false;
    }

    public static DungeonChunkLeaseManager createForLevel(ServerLevel level) {
        return new DungeonChunkLeaseManager(new ServerChunkTicketBackend(level));
    }

    public DungeonChunkLease acquire(ChunkPos chunkPos) {
        assertOwnerThread();
        if (this.cleared) {
            throw new IllegalStateException("Chunk lease manager has been cleared");
        }

        LeaseEntry entry = this.entries.get(chunkPos);
        if (entry == null) {
            long token = ++this.nextEntryToken;
            DungeonChunkLoadRequest request = requireInstalledRequest(
                    this.backend.acquire(chunkPos),
                    "install"
            );
            entry = new LeaseEntry(
                    token,
                    request.completion(),
                    1,
                    DungeonChunkLeaseState.PENDING,
                    null,
                    request.ticketInstalled()
            );
            this.entries.put(chunkPos, entry);
            registerCompletion(chunkPos, entry, request, entry.requestGeneration);
        } else if (entry.pendingRelease) {
            reacquirePendingEntry(chunkPos, entry);
        } else {
            entry.refCount++;
        }

        return new DungeonChunkLease(chunkPos, entry.token, this);
    }

    private void reacquirePendingEntry(ChunkPos chunkPos, LeaseEntry entry) {
        // Prepare without touching the ambiguous old physical-ownership state.
        DungeonChunkLoadRequest request = requireInstalledRequest(
                this.backend.acquire(chunkPos),
                "reinstall a pending-release"
        );

        // Publish only after the replacement request is known to be usable.
        long generation = entry.requestGeneration + 1L;
        entry.requestGeneration = generation;
        entry.future = request.completion();
        entry.ticketInstalled = true;
        entry.pendingRelease = false;
        entry.refCount = 1;
        if (entry.state != DungeonChunkLeaseState.READY) {
            entry.state = DungeonChunkLeaseState.PENDING;
            entry.outcome = null;
        }
        registerCompletion(chunkPos, entry, request, generation);
    }

    private static DungeonChunkLoadRequest requireInstalledRequest(
            DungeonChunkLoadRequest request,
            String operation
    ) {
        if (request == null) {
            throw new IllegalStateException(
                    "Chunk ticket backend returned no request while attempting to "
                            + operation + " ticket"
            );
        }
        if (request.completion() == null) {
            throw new IllegalStateException(
                    "Chunk ticket backend returned no completion while attempting to "
                            + operation + " ticket"
            );
        }
        if (!request.ticketInstalled()) {
            throw new IllegalStateException(
                    "Chunk ticket backend did not " + operation + " ticket"
            );
        }
        return request;
    }

    private void registerCompletion(
            ChunkPos chunkPos,
            LeaseEntry capturedEntry,
            DungeonChunkLoadRequest request,
            long requestGeneration
    ) {
        request.completion().whenComplete((outcome, ex) -> {
            DungeonChunkLoadOutcome finalOutcome =
                    normalizeTypedCompletion(outcome, ex);
            Runnable mutator = () -> {
                LeaseEntry current = this.entries.get(chunkPos);
                if (this.cleared || current != capturedEntry
                        || current.requestGeneration != requestGeneration
                        || !current.ticketInstalled) {
                    return;
                }
                if (finalOutcome.isSuccess()) {
                    current.state = DungeonChunkLeaseState.READY;
                    current.outcome = finalOutcome;
                } else {
                    current.state = DungeonChunkLeaseState.FAILED;
                    current.outcome = finalOutcome;
                }
            };
            this.backend.executeOnOwnerThread(mutator);
        });
    }

    private static DungeonChunkLoadOutcome normalizeTypedCompletion(
            DungeonChunkLoadOutcome outcome,
            Throwable completionFailure
    ) {
        if (completionFailure != null) {
            return new DungeonChunkLoadOutcome.ExceptionalCompletion(
                    completionFailure,
                    diagnosticDetail(completionFailure)
            );
        }
        if (outcome != null) {
            return outcome;
        }
        return new DungeonChunkLoadOutcome.UnexpectedResultType(
                "Null typed chunk-load outcome"
        );
    }

    private static String diagnosticDetail(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getName();
        }
        return throwable.getClass().getName() + ": " + message;
    }

    void release(ChunkPos chunkPos, long entryToken) {
        assertOwnerThread();
        LeaseEntry entry = this.entries.get(chunkPos);
        if (entry == null || entry.token != entryToken) {
            return;
        }
        if (entry.refCount <= 0) {
            return;
        }

        entry.refCount--;
        if (entry.refCount > 0) {
            return;
        }

        entry.pendingRelease = true;
        RuntimeException failure = attemptPhysicalRelease(chunkPos, entry);
        if (failure != null) {
            throw failure;
        }
    }

    public int retryPendingReleases(
            int maximumAttempts,
            BooleanSupplier hasTimeRemaining
    ) {
        assertOwnerThread();
        Objects.requireNonNull(hasTimeRemaining, "hasTimeRemaining");
        if (maximumAttempts < 0) {
            throw new IllegalArgumentException(
                    "maximumAttempts must be non-negative"
            );
        }
        List<Map.Entry<ChunkPos, LeaseEntry>> pending = pendingEntriesSnapshot();
        if (pending.isEmpty()) {
            this.pendingReleaseCursor = 0;
            return 0;
        }
        int attempted = 0;
        int start = Math.floorMod(this.pendingReleaseCursor, pending.size());
        int attemptLimit = Math.min(maximumAttempts, pending.size());
        while (attempted < attemptLimit && hasTimeRemaining.getAsBoolean()) {
            Map.Entry<ChunkPos, LeaseEntry> mapEntry = pending.get(
                    (start + attempted) % pending.size()
            );
            attempted++;
            attemptPhysicalRelease(mapEntry.getKey(), mapEntry.getValue());
        }
        this.pendingReleaseCursor = (start + attempted) % pending.size();
        return attempted;
    }

    private List<Map.Entry<ChunkPos, LeaseEntry>> pendingEntriesSnapshot() {
        ArrayList<Map.Entry<ChunkPos, LeaseEntry>> pending = new ArrayList<>();
        for (Map.Entry<ChunkPos, LeaseEntry> mapEntry : this.entries.entrySet()) {
            LeaseEntry entry = mapEntry.getValue();
            if (entry.pendingRelease && entry.refCount == 0) {
                pending.add(Map.entry(mapEntry.getKey(), entry));
            }
        }
        return pending;
    }

    public int pendingReleaseCount() {
        assertOwnerThread();
        int pending = 0;
        for (LeaseEntry entry : this.entries.values()) {
            if (entry.pendingRelease) {
                pending++;
            }
        }
        return pending;
    }

    private RuntimeException attemptPhysicalRelease(
            ChunkPos chunkPos,
            LeaseEntry entry
    ) {
        if (this.entries.get(chunkPos) != entry
                || !entry.pendingRelease
                || entry.refCount != 0) {
            return null;
        }
        if (entry.ticketInstalled) {
            try {
                this.backend.release(chunkPos);
                entry.ticketInstalled = false;
            } catch (RuntimeException failure) {
                this.pendingReleaseFailureCount++;
                return failure;
            }
        }
        entry.pendingRelease = false;
        entry.state = DungeonChunkLeaseState.CANCELLED;
        this.entries.remove(chunkPos, entry);
        return null;
    }

    DungeonChunkLeaseState stateFor(ChunkPos chunkPos, long entryToken) {
        assertOwnerThread();
        LeaseEntry entry = this.entries.get(chunkPos);
        if (entry == null || entry.token != entryToken) {
            return DungeonChunkLeaseState.CANCELLED;
        }
        return entry.state;
    }

    Optional<DungeonChunkLoadOutcome> outcomeFor(ChunkPos chunkPos, long entryToken) {
        assertOwnerThread();
        LeaseEntry entry = this.entries.get(chunkPos);
        if (entry == null || entry.token != entryToken) {
            return Optional.empty();
        }
        return Optional.ofNullable(entry.outcome);
    }

    public TerminalCleanupResult clear() {
        return clearTerminal(
                TERMINAL_RELEASE_DRAIN_PASSES,
                TERMINAL_RELEASE_ATTEMPTS_PER_PASS
        );
    }

    TerminalCleanupResult clearTerminal(
            int maximumPasses,
            int maximumAttemptsPerPass
    ) {
        assertOwnerThread();
        if (maximumPasses < 1 || maximumAttemptsPerPass < 1) {
            throw new IllegalArgumentException(
                    "Terminal cleanup bounds must both be positive"
            );
        }

        if (!this.cleared) {
            this.cleared = true;
            for (Map.Entry<ChunkPos, LeaseEntry> mapEntry
                    : List.copyOf(this.entries.entrySet())) {
                LeaseEntry entry = mapEntry.getValue();
                entry.state = DungeonChunkLeaseState.CANCELLED;
                entry.refCount = 0;
                entry.pendingRelease = entry.ticketInstalled;
                if (!entry.ticketInstalled) {
                    this.entries.remove(mapEntry.getKey(), entry);
                }
            }
        }

        long failuresBefore = this.pendingReleaseFailureCount;
        TerminalReleaseFailures failures = new TerminalReleaseFailures();
        int passes = 0;
        int attempts = 0;
        int released = 0;
        while (passes < maximumPasses && pendingReleaseCount() > 0) {
            passes++;
            List<Map.Entry<ChunkPos, LeaseEntry>> pending = pendingEntriesSnapshot();
            if (pending.isEmpty()) {
                break;
            }
            int start = Math.floorMod(this.pendingReleaseCursor, pending.size());
            int passAttempts = Math.min(maximumAttemptsPerPass, pending.size());
            for (int index = 0; index < passAttempts; index++) {
                Map.Entry<ChunkPos, LeaseEntry> mapEntry = pending.get(
                        (start + index) % pending.size()
                );
                attempts++;
                if (attemptTerminalPhysicalRelease(
                        mapEntry.getKey(), mapEntry.getValue(), failures
                )) {
                    released++;
                }
            }
            this.pendingReleaseCursor = (start + passAttempts) % pending.size();
        }

        List<ChunkPos> unresolved = pendingEntriesSnapshot().stream()
                .map(Map.Entry::getKey)
                .limit(MAX_REPORTED_UNRESOLVED_POSITIONS)
                .toList();
        this.lastTerminalCleanupResult = new TerminalCleanupResult(
                passes,
                attempts,
                released,
                pendingReleaseCount(),
                this.pendingReleaseFailureCount - failuresBefore,
                unresolved
        );

        failures.throwIfRequired(this.lastTerminalCleanupResult.unresolvedCount());
        return this.lastTerminalCleanupResult;
    }

    private boolean attemptTerminalPhysicalRelease(
            ChunkPos chunkPos,
            LeaseEntry entry,
            TerminalReleaseFailures failures
    ) {
        if (this.entries.get(chunkPos) != entry
                || !entry.pendingRelease
                || entry.refCount != 0) {
            return false;
        }
        if (!entry.ticketInstalled) {
            entry.pendingRelease = false;
            this.entries.remove(chunkPos, entry);
            return true;
        }
        try {
            this.backend.release(chunkPos);
            entry.ticketInstalled = false;
            entry.pendingRelease = false;
            this.entries.remove(chunkPos, entry);
            return true;
        } catch (RuntimeException failure) {
            this.pendingReleaseFailureCount++;
            failures.capture(failure);
        } catch (Error error) {
            this.pendingReleaseFailureCount++;
            failures.capture(error);
        }
        return false;
    }

    public TerminalCleanupResult lastTerminalCleanupResult() {
        assertOwnerThread();
        return this.lastTerminalCleanupResult;
    }

    public record TerminalCleanupResult(
            int passes,
            int attempts,
            int releasedCount,
            int unresolvedCount,
            long releaseFailureCount,
            List<ChunkPos> unresolvedPositions
    ) {
        public TerminalCleanupResult {
            unresolvedPositions = List.copyOf(unresolvedPositions);
        }

        static TerminalCleanupResult empty() {
            return new TerminalCleanupResult(0, 0, 0, 0, 0L, List.of());
        }
    }

    private static final class TerminalReleaseFailures {
        private IllegalStateException ordinaryFailures;
        private Error firstError;

        void capture(Throwable failure) {
            if (failure instanceof Error error) {
                if (this.firstError == null) {
                    this.firstError = error;
                    if (this.ordinaryFailures != null) {
                        addSuppressedIfDistinct(error, this.ordinaryFailures);
                        this.ordinaryFailures = null;
                    }
                } else {
                    addSuppressedIfDistinct(this.firstError, error);
                }
                return;
            }
            if (this.ordinaryFailures == null) {
                this.ordinaryFailures = new IllegalStateException(
                        "Failed to release one or more dungeon preparation tickets"
                );
            }
            this.ordinaryFailures.addSuppressed(failure);
        }

        void throwIfRequired(int unresolvedCount) {
            if (this.firstError != null) {
                throw this.firstError;
            }
            if (unresolvedCount > 0 && this.ordinaryFailures != null) {
                throw this.ordinaryFailures;
            }
        }
    }

    private static void addSuppressedIfDistinct(
            Throwable target,
            Throwable suppressed
    ) {
        if (target != suppressed) {
            target.addSuppressed(suppressed);
        }
    }

    public int activeLeaseCount() {
        assertOwnerThread();
        int active = 0;
        for (LeaseEntry entry : this.entries.values()) {
            if (entry.refCount > 0) {
                active++;
            }
        }
        return active;
    }

    /** Monotonic diagnostic count; avoids emitting a warning on every retry tick. */
    public long pendingReleaseFailureCount() {
        assertOwnerThread();
        return this.pendingReleaseFailureCount;
    }

    int refCountFor(ChunkPos chunkPos) {
        assertOwnerThread();
        LeaseEntry entry = this.entries.get(chunkPos);
        if (entry == null) {
            return 0;
        }
        return entry.refCount;
    }

    boolean hasActiveEntry(ChunkPos chunkPos) {
        assertOwnerThread();
        LeaseEntry entry = this.entries.get(chunkPos);
        return entry != null && entry.refCount > 0;
    }

    void assertOwnerThread() {
        if (!this.backend.isOwnerThread()) {
            throw new IllegalStateException(
                    "Chunk lease manager must be accessed on the owning server thread"
            );
        }
    }

    long tokenFor(ChunkPos chunkPos) {
        assertOwnerThread();
        LeaseEntry entry = this.entries.get(chunkPos);
        return entry == null ? 0L : entry.token;
    }

    long requestGenerationFor(ChunkPos chunkPos) {
        assertOwnerThread();
        LeaseEntry entry = this.entries.get(chunkPos);
        return entry == null ? 0L : entry.requestGeneration;
    }

    CompletableFuture<DungeonChunkLoadOutcome> futureFor(ChunkPos chunkPos) {
        assertOwnerThread();
        LeaseEntry entry = this.entries.get(chunkPos);
        return entry == null ? null : entry.future;
    }

    boolean ticketInstalledFor(ChunkPos chunkPos) {
        assertOwnerThread();
        LeaseEntry entry = this.entries.get(chunkPos);
        return entry != null && entry.ticketInstalled;
    }

    private static final class LeaseEntry {
        final long token;
        CompletableFuture<DungeonChunkLoadOutcome> future;
        long requestGeneration;
        int refCount;
        DungeonChunkLeaseState state;
        DungeonChunkLoadOutcome outcome;
        boolean ticketInstalled;
        boolean pendingRelease;

        LeaseEntry(
                long token,
                CompletableFuture<DungeonChunkLoadOutcome> future,
                int refCount,
                DungeonChunkLeaseState state,
                DungeonChunkLoadOutcome outcome,
                boolean ticketInstalled
        ) {
            this.token = token;
            this.future = future;
            this.requestGeneration = 1L;
            this.refCount = refCount;
            this.state = state;
            this.outcome = outcome;
            this.ticketInstalled = ticketInstalled;
            this.pendingRelease = false;
        }
    }

    private static final class ServerChunkTicketBackend implements DungeonChunkTicketBackend {
        private final ServerLevel level;

        ServerChunkTicketBackend(ServerLevel level) {
            this.level = level;
        }

        @Override
        public DungeonChunkLoadRequest acquire(ChunkPos chunkPos) {
            boolean ticketInstalled = false;
            DungeonPreparationProfiler profiler = DungeonPreparationProfiler.global();
            try {
                ticketInstalled = true;
                long submissionStartNanos = profiler.start();
                CompletableFuture<?> raw =
                        this.level.getChunkSource().addTicketAndLoadWithRadius(
                                ModTicketTypes.DUNGEON_PREPARATION.get(),
                                chunkPos,
                                0
                        );
                profiler.record(
                        DungeonPreparationProfiler.Operation.ADD_TICKET_SUBMISSION,
                        submissionStartNanos,
                        isOwnerThread()
                );
                CompletableFuture<DungeonChunkLoadOutcome> normalized =
                        normalizeChunkFuture(raw);
                return new DungeonChunkLoadRequest(normalized, true);
            } catch (RuntimeException | Error e) {
                if (ticketInstalled) {
                    this.level.getChunkSource().removeTicketWithRadius(
                            ModTicketTypes.DUNGEON_PREPARATION.get(),
                            chunkPos,
                            0
                    );
                }
                throw e;
            }
        }

        /**
        * Normalize the wildcard future from
        * {@code ServerChunkCache.addTicketAndLoadWithRadius}.
        *
        * <p>The vanilla API exposes {@code CompletableFuture<?>}, but the
        * current radius-zero implementation returns the result of
        * {@code ChunkMap.getChunkRangeFuture}, whose runtime value is a
        * {@code ChunkResult<List<ChunkAccess>>}. No unchecked cast is
        * performed: the completion value is passed as
        * {@code Object} and validated with {@code instanceof ChunkResult<?>}
        * inside {@link DungeonChunkLoadOutcomeNormalizer#normalize}.
        *
        * <p>The normalization callback may run on any completion thread. It
        * retains neither the mutable chunk collection nor any
        * {@code ChunkAccess}; only an immutable {@link DungeonChunkLoadOutcome}
        * crosses into the later owner-thread lease mutation.
        */
        private static CompletableFuture<DungeonChunkLoadOutcome> normalizeChunkFuture(
                CompletableFuture<?> raw
        ) {
            DungeonPreparationProfiler profiler = DungeonPreparationProfiler.global();
            long completionStartNanos = profiler.start();
            return raw.handle((value, failure) -> {
                profiler.record(
                        DungeonPreparationProfiler.Operation.CHUNK_LOAD_COMPLETION_LATENCY,
                        completionStartNanos,
                        false
                );
                return DungeonChunkLoadOutcomeNormalizer.normalize(value, failure);
            });
        }

        @Override
        public void release(ChunkPos chunkPos) {
            this.level.getChunkSource().removeTicketWithRadius(
                    ModTicketTypes.DUNGEON_PREPARATION.get(),
                    chunkPos,
                    0
            );
        }

        @Override
        public boolean isOwnerThread() {
            return this.level.getServer().isSameThread();
        }

        @Override
        public void executeOnOwnerThread(Runnable task) {
            this.level.getServer().execute(task);
        }
    }
}
