package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.preparation.chunk.DungeonChunkLeaseState;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.chunk.DungeonChunkLoadOutcome;
import io.github.naimjeg.obeliskdepths.dungeon.site.*;
import io.github.naimjeg.obeliskdepths.dungeon.site.reader.DungeonSiteCandidateCursor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

import java.util.*;

final class DungeonPreparationJobExecutor {
    static final String CANDIDATE_ACCEPTED = "candidate_accepted";

    private final DungeonPreparationJobRegistry registry;
    private final DungeonPreparationExecutionBackend backend;
    private final DungeonSiteClaimManager claimManager;
    private final DungeonPreparationCommitter committer;
    private final DungeonPreparationFailureMetrics failureMetrics;
    private final DungeonPersistedProbePermitPool persistedProbePermits;
    private final Map<DungeonPreparationJobId, DungeonPreparationExecutionContext> contexts =
            new HashMap<>();

    DungeonPreparationJobExecutor(
            DungeonPreparationJobRegistry registry,
            DungeonPreparationExecutionBackend backend,
            DungeonSiteClaimManager claimManager
    ) {
        this(
                registry,
                backend,
                claimManager,
                (job, context) -> DungeonActivationCommitResult.failure(
                        DungeonActivationCommitFailureReason.INTERNAL_ERROR,
                        "no commit coordinator configured"
                ),
                DungeonPreparationFailureMetrics.NO_OP
        );
    }

    DungeonPreparationJobExecutor(
            DungeonPreparationJobRegistry registry,
            DungeonPreparationExecutionBackend backend,
            DungeonSiteClaimManager claimManager,
            DungeonPreparationCommitter committer
    ) {
        this(registry, backend, claimManager, committer,
                DungeonPreparationFailureMetrics.NO_OP);
    }

    DungeonPreparationJobExecutor(
            DungeonPreparationJobRegistry registry,
            DungeonPreparationExecutionBackend backend,
            DungeonSiteClaimManager claimManager,
            DungeonPreparationCommitter committer,
            DungeonPreparationFailureMetrics failureMetrics
    ) {
        this(
                registry,
                backend,
                claimManager,
                committer,
                failureMetrics,
                new DungeonPersistedProbePermitPool(
                        DungeonPreparationLimits
                                .MAX_IN_FLIGHT_PERSISTED_PROBES_PER_LEVEL
                )
        );
    }

    DungeonPreparationJobExecutor(
            DungeonPreparationJobRegistry registry,
            DungeonPreparationExecutionBackend backend,
            DungeonSiteClaimManager claimManager,
            DungeonPreparationCommitter committer,
            DungeonPreparationFailureMetrics failureMetrics,
            DungeonPersistedProbePermitPool persistedProbePermits
    ) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.backend = Objects.requireNonNull(backend, "backend");
        this.claimManager = Objects.requireNonNull(claimManager, "claimManager");
        this.committer = Objects.requireNonNull(committer, "committer");
        this.failureMetrics = Objects.requireNonNull(
                failureMetrics,
                "failureMetrics"
        );
        this.persistedProbePermits = Objects.requireNonNull(
                persistedProbePermits,
                "persistedProbePermits"
        );
    }

    void createContext(DungeonPreparationJob job) {
        this.backend.assertOwnerThread();
        if (job.isTerminal()) {
            throw new IllegalArgumentException(
                    "Cannot create execution context for terminal job " + job.id()
            );
        }
        this.contexts.put(
                job.id(),
                new DungeonPreparationExecutionContext(job.id())
        );
    }

    /**
     * Processes at most one significant Phase 4 progression unit for one job.
     */
    void tick(DungeonPreparationJob job) {
        tick(job, DungeonPreparationTickBudget.unlimitedForTests());
    }

    void tick(
            DungeonPreparationJob job,
            DungeonPreparationTickBudget budget
    ) {
        this.backend.assertOwnerThread();
        Objects.requireNonNull(budget, "budget");
        DungeonPreparationProfiler profiler = DungeonPreparationProfiler.global();
        if (profiler.enabled()) {
            long startNanos = profiler.start();
            try {
                tickProfiled(job, budget);
            } finally {
                profiler.record(
                        DungeonPreparationProfiler.Operation.JOB_EXECUTOR_TICK,
                        startNanos,
                        true
                );
            }
            return;
        }
        tickProfiled(job, budget);
    }

    private void tickProfiled(
            DungeonPreparationJob job,
            DungeonPreparationTickBudget budget
    ) {
        if (job.isTerminal()) {
            cleanupContext(job.id());
            return;
        }
        if (!budget.hasTimeRemaining()) {
            return;
        }

        try {
            DungeonPreparationExecutionContext context = requireContext(job);
            switch (job.stage()) {
                case QUEUED -> advance(job, DungeonPreparationStage.VALIDATING);
                case VALIDATING -> startPersistedScan(job, context, budget);
                case SCANNING_EXISTING_SITES -> finishPersistedScanIfReady(job, context, budget);
                case SELECTING_CANDIDATE -> selectCandidateIfAvailable(job, context, budget);
                case REQUESTING_START_CHUNK -> requestStartChunk(job, context, budget);
                case WAITING_FOR_START_CHUNK -> inspectStartChunkLease(job, context);
                case READING_STRUCTURE_START -> readStructureStart(job, context);
                case PLANNING_ENTRY_CHUNKS -> planEntryChunks(job, context);
                case REQUESTING_ENTRY_CHUNKS -> requestEntryChunks(job, context, budget);
                case WAITING_FOR_ENTRY_CHUNKS -> waitForEntryChunks(job, context, budget);
                case VALIDATING_ENTRY_CHUNKS -> validateEntryChunksLoaded(job, context, budget);
                case VALIDATING_ENTRY -> advanceSafeEntryScan(job, context, budget);
                case READY_TO_COMMIT -> commitReadyJob(job, context);
                case COMMITTING -> {
                    if (context.committedResult() != null) {
                        tryPublishCommitted(job, context);
                    }
                }
                case READY, FAILED, CANCELLED -> cleanupContext(job.id());
            }
        } catch (RuntimeException exception) {
            DungeonPreparationExecutionContext catchContext = this.contexts.get(job.id());
            if (catchContext != null
                    && catchContext.committedResult() != null
                    && job.stage() == DungeonPreparationStage.COMMITTING) {
                this.backend.logJobRuntimeFailure(job, exception);
                return;
            }
            this.backend.logJobRuntimeFailure(job, exception);
            failJob(
                    job,
                    DungeonPreparationJobFailureReason.INTERNAL_ERROR,
                    diagnosticDetail(exception)
            );
        }
    }

    Optional<DungeonSite> resolvedSiteFor(DungeonPreparationJobId id) {
        this.backend.assertOwnerThread();
        DungeonPreparationExecutionContext context = this.contexts.get(id);
        return context == null ? Optional.empty() : context.resolvedSite();
    }

    List<DungeonSiteKey> generationCandidatesFor(DungeonPreparationJobId id) {
        this.backend.assertOwnerThread();
        DungeonPreparationExecutionContext context = this.contexts.get(id);
        return context == null ? List.of() : context.generationCandidates();
    }

    Optional<DungeonPreparationProgressSnapshot> progressSnapshot(
            DungeonPreparationJobId id
    ) {
        this.backend.assertOwnerThread();
        return this.registry.findById(id)
                .map(job -> progressSnapshot(job, this.contexts.get(id)));
    }

    void cancelContext(DungeonPreparationJobId id) {
        this.backend.assertOwnerThread();
        DungeonPreparationExecutionContext context = this.contexts.remove(id);
        if (context == null) {
            return;
        }
        DungeonPreparationJob job = this.registry.findById(id).orElse(null);
        context.invalidateGeneration();
        context.scanner().ifPresent(scanner -> {
            if (!scanner.state().isTerminal()) {
                scanner.cancel();
            }
        });
        releaseCurrentClaim(context);
        if (job != null && job.isTerminal()) {
            context.clearTransientCandidateState();
            context.clearEntryChunkLeases();
            context.resetEntryChunkRequestIndex();
            context.resolvedSite(null);
            context.entryChunkPlan(null);
            context.clearCommitPlan();
            context.preparedDestination(null);
            return;
        }
        closeAllEntryLeases(job, context);
        clearCurrentCandidateState(context, job);
    }

    void cancelAllContexts() {
        this.backend.assertOwnerThread();
        List<DungeonPreparationJobId> ids = List.copyOf(this.contexts.keySet());
        for (DungeonPreparationJobId id : ids) {
            cancelContext(id);
        }
    }

    void recordScanCompletion(
            DungeonPreparationJobId id,
            int generation,
            DungeonSiteProbeReport report,
            Throwable failure
    ) {
        this.backend.assertOwnerThread();
        DungeonPreparationExecutionContext context = this.contexts.get(id);
        if (context == null || context.generation() != generation) {
            return;
        }
        if (failure != null) {
            context.completeScan(null, failure);
            return;
        }
        if (report == null) {
            context.completeScan(
                    null,
                    new IllegalStateException(
                            "Persisted-site scanner completed with a null report"
                    )
            );
            return;
        }
        context.completeScan(report, null);
    }

    // ── Tick handlers ────────────────────────────────────────────────

    private void startPersistedScan(
            DungeonPreparationJob job,
            DungeonPreparationExecutionContext context,
            DungeonPreparationTickBudget budget
    ) {
        DungeonSiteCandidateCursor cursor = context.candidateCursor()
                .orElseGet(() -> {
                    DungeonSiteCandidateCursor created =
                            this.backend.createCandidateCursor(
                        job.request(),
                        this.backend.maxCandidateCount()
                            );
                    context.candidateCursor(created);
                    return created;
                });
        int enumerationAllowance = budget.claimCandidateKeysEnumerated(
                DungeonPreparationLimits.CANDIDATE_KEYS_ENUMERATED_PER_LEVEL_TICK
        );
        cursor.advance(enumerationAllowance, context::addEnumeratedCandidateKey);
        if (!cursor.exhausted()) {
            return;
        }

        if (!budget.tryStartPersistedScanner(activePersistedScannerCount())) {
            return;
        }

        List<DungeonSiteKey> candidates =
                copyKeys(context.enumeratedCandidateKeys());
        context.orderedCandidateKeys(candidates);
        context.clearCandidateCursor();

        AsyncDungeonSiteProbe scanner = this.backend.createSiteProbe(
                candidates,
                ChunkStatus.STRUCTURE_STARTS,
                Math.min(
                        budget.maxInFlightPersistedProbesPerLevel(),
                        this.persistedProbePermits.maximumOutstanding()
                )
        );
        context.scanner(scanner);
        int generation = context.generation();
        scanner.completion().whenComplete((report, failure) -> {
            this.backend.assertOwnerThread();
            recordScanCompletion(job.id(), generation, report, failure);
        });
        scanner.start();
        advance(job, DungeonPreparationStage.SCANNING_EXISTING_SITES);
    }

    private void finishPersistedScanIfReady(
            DungeonPreparationJob job,
            DungeonPreparationExecutionContext context,
            DungeonPreparationTickBudget budget
    ) {
        context.scanner().ifPresent(scanner -> {
            while (budget.hasTimeRemaining()
                    && scanner.pendingCompletionCount() > 0
                    && budget.remainingPersistedProbeCompletionDrains() > 0) {
                int drained = scanner.drainCompletionMailbox(1);
                budget.consumePersistedProbeCompletionDrains(drained);
                if (drained == 0) {
                    break;
                }
            }

            if (!budget.hasTimeRemaining()) {
                return;
            }
            AsyncDungeonSiteProbe.SubmissionProgress progress =
                    scanner.advanceSubmissions(
                            budget.remainingLoadedFastPathProbes(),
                            budget.remainingLoadedFastPathProbes(),
                            budget.remainingPersistedProbeSubmissions(),
                            this.persistedProbePermits
                    );
            budget.consumeLoadedFastPathProbes(
                    progress.loadedFastPathChecks()
            );
            budget.consumePersistedProbeSubmissions(
                    progress.persistedProbeSubmissions()
            );
        });

        if (context.scanFailure().isPresent()) {
            Throwable failure = context.scanFailure().get();
            failJob(
                    job,
                    DungeonPreparationJobFailureReason.INTERNAL_ERROR,
                    diagnosticDetail(failure)
            );
            return;
        }
        Optional<DungeonSiteProbeReport> report = context.scanReport();
        if (report.isEmpty()) {
            return;
        }

        List<DungeonPersistedChunkProbeResult> results = report.get().results();
        List<DungeonSiteKey> candidates = context.orderedCandidateKeys();
        DungeonCandidateClassificationState classification =
                context.classificationState();
        int remainingResults = Math.max(
                0,
                results.size() - classification.nextIndex()
        );
        int classificationAllowance = Math.min(
                remainingResults,
                budget.remainingPersistedProbeResultsClassified()
        );
        int classified = classification.advance(
                results,
                candidates,
                classificationAllowance,
                this.backend::generatedReservationRejectionReason,
                budget::hasTimeRemaining
        );
        budget.consumePersistedProbeResultsClassified(classified);
        if (!classification.complete(results.size())) {
            return;
        }

        context.clearScanner();
        context.candidateLists(
                classification.persistedCandidates(),
                classification.generationCandidates()
        );
        if (context.hasRemainingPersistedCandidates()) {
            selectNextCandidate(job, context);
            return;
        }
        if (!context.generationCandidates().isEmpty()) {
            advance(job, DungeonPreparationStage.SELECTING_CANDIDATE);
            return;
        }
        failJob(
                job,
                DungeonPreparationJobFailureReason.NO_SITE_AVAILABLE,
                "no persisted or generation-safe dungeon site candidates"
        );
    }

    private void selectCandidateIfAvailable(
            DungeonPreparationJob job,
            DungeonPreparationExecutionContext context,
            DungeonPreparationTickBudget budget
    ) {
        if (context.hasRemainingPersistedCandidates()) {
            selectNextCandidate(job, context);
            return;
        }
        if (context.hasRemainingGenerationCandidates()) {
            selectNextCandidate(job, context);
            return;
        }
        failJob(
                job,
                DungeonPreparationJobFailureReason.NO_SITE_AVAILABLE,
                context.diagnosticDetail().isBlank()
                        ? "no remaining dungeon site candidates"
                        : context.diagnosticDetail()
        );
    }

    // ── Candidate claim and acquisition ─────────────────────────────

    private void selectNextCandidate(
            DungeonPreparationJob job,
            DungeonPreparationExecutionContext context
    ) {
        Optional<DungeonSiteKey> maybeKey = context.nextAvailableCandidate();
        if (maybeKey.isEmpty()) {
            advance(job, DungeonPreparationStage.SELECTING_CANDIDATE);
            return;
        }

        DungeonSiteKey key = maybeKey.get();

        if (!CANDIDATE_ACCEPTED.equals(
                this.backend.generatedReservationRejectionReason(key))) {
            context.diagnosticDetail("candidate became unavailable before request");
            advance(job, DungeonPreparationStage.SELECTING_CANDIDATE);
            return;
        }

        context.currentCandidate(key);
        advance(job, DungeonPreparationStage.REQUESTING_START_CHUNK);
    }

    private void requestStartChunk(
            DungeonPreparationJob job,
            DungeonPreparationExecutionContext context,
            DungeonPreparationTickBudget budget
    ) {
        if (context.currentStartLease().isPresent()) {
            advance(job, DungeonPreparationStage.WAITING_FOR_START_CHUNK);
            return;
        }
        if (!budget.tryConsumeStartChunkRequest()) {
            return;
        }

        DungeonSiteKey key = context.currentCandidate()
                .orElseThrow(() -> new IllegalStateException(
                        "Requesting start chunk without a selected candidate"
                ));

        if (!CANDIDATE_ACCEPTED.equals(
                this.backend.generatedReservationRejectionReason(key))) {
            context.diagnosticDetail("candidate became unavailable before claim");
            advance(job, DungeonPreparationStage.SELECTING_CANDIDATE);
            return;
        }

        Optional<DungeonSiteClaim> claim = this.claimManager.tryClaim(
                key,
                job.id(),
                this.backend.gameTime()
        );
        if (claim.isEmpty()) {
            context.diagnosticDetail("candidate " + key + " already claimed");
            advance(job, DungeonPreparationStage.SELECTING_CANDIDATE);
            return;
        }

        context.currentClaim(claim.get());

        DungeonPreparationStartChunkLease lease;
        try {
            lease = Objects.requireNonNull(
                    this.backend.acquireStartChunk(key),
                    "backend returned a null start chunk lease"
            );
        } catch (RuntimeException | Error failure) {
            DungeonPreparationCompensation.runAll(
                    failure,
                    () -> releaseCurrentClaim(context)
            );
            throw failure;
        }

        try {
            job.addCloseableLease(lease);
        } catch (RuntimeException | Error failure) {
            /*
             * DungeonPreparationJob.addCloseableLease closes a rejected lease
             * when the job has become terminal.  Do not close it a second time.
             */
            DungeonPreparationCompensation.runAll(
                    failure,
                    () -> releaseCurrentClaim(context)
            );
            throw failure;
        }

        try {
            context.currentStartLease(key, lease);
        } catch (RuntimeException | Error failure) {
            DungeonPreparationCompensation.runAll(
                    failure,
                    () -> job.closeAndRemoveLease(lease),
                    () -> releaseCurrentClaim(context)
            );
            throw failure;
        }
        advance(job, DungeonPreparationStage.WAITING_FOR_START_CHUNK);
    }

    // ── Start chunk inspection ──────────────────────────────────────

    private void inspectStartChunkLease(
            DungeonPreparationJob job,
            DungeonPreparationExecutionContext context
    ) {
        DungeonPreparationStartChunkLease lease = context.currentStartLease()
                .orElseThrow(() -> new IllegalStateException(
                        "Waiting for start chunk without an owned lease"
                ));

        DungeonChunkLeaseState state = lease.state();
        switch (state) {
            case PENDING -> {
            }
            case READY -> advance(job, DungeonPreparationStage.READING_STRUCTURE_START);
            case FAILED -> {
                String detail = lease.outcome()
                        .map(DungeonChunkLoadOutcome::detail)
                        .filter(value -> !value.isBlank())
                        .orElse("start chunk load failed");
                context.diagnosticDetail(detail);
                releaseCurrentCandidateResources(job, context);
                retryOrFail(job, context, detail);
            }
            case CANCELLED -> failJob(
                    job,
                    DungeonPreparationJobFailureReason.INTERNAL_ERROR,
                    "active start chunk lease was cancelled"
            );
        }
    }

    // ── Structure start reading and owner-thread projection ─────────
    //
    // ServerDungeonPreparationExecutionBackend.readLoadedSite performs the
    // loaded structure-start lookup and projection in one owner-thread call.

    private void readStructureStart(
            DungeonPreparationJob job,
            DungeonPreparationExecutionContext context
    ) {
        DungeonSiteKey key = context.currentCandidate()
                .orElseThrow(() -> new IllegalStateException(
                        "Reading structure start without a current candidate"
                ));

        DungeonPreparationProfiler profiler = DungeonPreparationProfiler.global();
        DungeonPreparationLoadedSiteResult loaded;
        if (profiler.enabled()) {
            long startNanos = profiler.start();
            try {
                loaded = this.backend.readLoadedSite(key);
            } finally {
                profiler.record(
                        DungeonPreparationProfiler.Operation.READ_LOADED_SITE,
                        startNanos,
                        true
                );
            }
        } else {
            loaded = this.backend.readLoadedSite(key);
        }
        if (!loaded.accepted()) {
            context.diagnosticDetail(loaded.detail());
            releaseCurrentCandidateResources(job, context);
            retryOrFail(job, context, loaded.detail());
            return;
        }

        if (!CANDIDATE_ACCEPTED.equals(
                this.backend.generatedReservationRejectionReason(key))) {
            context.diagnosticDetail("candidate became unavailable before acceptance");
            releaseCurrentCandidateResources(job, context);
            retryOrFail(job, context,
                    "candidate became unavailable before acceptance");
            return;
        }

        DungeonSite site = loaded.site().orElseThrow();
        context.resolvedSite(site);
        advance(job, DungeonPreparationStage.PLANNING_ENTRY_CHUNKS);
    }

    private void planEntryChunks(
            DungeonPreparationJob job,
            DungeonPreparationExecutionContext context
    ) {
        if (context.entryChunkPlan().isPresent()) {
            advance(job, DungeonPreparationStage.REQUESTING_ENTRY_CHUNKS);
            return;
        }
        DungeonSite site = context.resolvedSite()
                .orElseThrow(() -> new IllegalStateException(
                        "Planning entry chunks without a resolved site"
                ));
        DungeonEntryChunkPlan plan = DungeonEntryChunkPlanner.plan(site, 0);
        validateEntryChunkPlan(plan);
        context.entryChunkPlan(plan);
        advance(job, DungeonPreparationStage.REQUESTING_ENTRY_CHUNKS);
    }

    private static void validateEntryChunkPlan(DungeonEntryChunkPlan plan) {
        Objects.requireNonNull(plan, "plan");
        Set<ChunkPos> seen = new HashSet<>();
        for (ChunkPos chunkPos : plan.chunks()) {
            if (chunkPos == null) {
                throw new IllegalStateException(
                        "Entry chunk plan contains a null chunk"
                );
            }
            if (!seen.add(chunkPos)) {
                throw new IllegalStateException(
                        "Entry chunk plan contains duplicate chunk " + chunkPos
                );
            }
        }
    }

    // ── Entry chunk scheduling ──────────────────────────────────────

    private void requestEntryChunks(
            DungeonPreparationJob job,
            DungeonPreparationExecutionContext context,
            DungeonPreparationTickBudget budget
    ) {
        DungeonEntryChunkPlan plan = context.entryChunkPlan()
                .orElseThrow(() -> new IllegalStateException(
                        "Waiting for entry chunks without a plan"
                ));

        List<ChunkPos> plannedChunks = plan.chunks();
        int requestCount = 0;

        for (int i = context.nextEntryChunkRequestIndex();
                i < plannedChunks.size();
                i++) {
            if (!budget.tryConsumeEntryChunkRequest()) {
                break;
            }
            ChunkPos chunkPos = plannedChunks.get(i);
            DungeonPreparationStartChunkLease lease =
                    this.backend.acquireEntryChunk(chunkPos);
            job.addCloseableLease(lease);
            context.addEntryChunkLease(lease);
            requestCount++;
        }
        context.advanceEntryChunkRequestIndex(requestCount);

        if (context.nextEntryChunkRequestIndex() < plannedChunks.size()) {
            return;
        }

        advance(job, DungeonPreparationStage.WAITING_FOR_ENTRY_CHUNKS);
    }

    private void waitForEntryChunks(
            DungeonPreparationJob job,
            DungeonPreparationExecutionContext context,
            DungeonPreparationTickBudget budget
    ) {
        DungeonEntryChunkPlan plan = context.entryChunkPlan()
                .orElseThrow(() -> new IllegalStateException(
                        "Waiting for entry chunks without a plan"
                ));
        if (context.entryChunkLeaseCount() != plan.chunks().size()) {
            failJob(
                    job,
                    DungeonPreparationJobFailureReason.INTERNAL_ERROR,
                    "entry chunk lease invariant violated: expected "
                            + plan.chunks().size()
                            + " leases but found "
                            + context.entryChunkLeaseCount()
            );
            return;
        }

        while (context.nextEntryLeaseValidationIndex()
                < context.entryChunkLeaseCount()) {
            if (!budget.hasTimeRemaining()) {
                return;
            }
            DungeonPreparationStartChunkLease lease = context.entryChunkLease(
                    context.nextEntryLeaseValidationIndex()
            );
            DungeonChunkLeaseState state = lease.state();
            switch (state) {
                case PENDING -> {
                    return;
                }
                case READY -> context.advanceEntryLeaseValidationIndex();
                case FAILED -> {
                    String detail = lease.outcome()
                            .map(DungeonChunkLoadOutcome::detail)
                            .filter(value -> !value.isBlank())
                            .orElse("entry chunk load failed");
                    context.diagnosticDetail(detail);
                    releaseCurrentCandidateResources(job, context);
                    retryOrFail(job, context, detail);
                    return;
                }
                case CANCELLED -> {
                    failJob(
                            job,
                            DungeonPreparationJobFailureReason.INTERNAL_ERROR,
                            "active entry chunk lease was cancelled"
                    );
                    return;
                }
            }
        }

        advance(job, DungeonPreparationStage.VALIDATING_ENTRY_CHUNKS);
    }

    // ── Validate loaded entry ───────────────────────────────────────

    private void validateEntryChunksLoaded(
            DungeonPreparationJob job,
            DungeonPreparationExecutionContext context,
            DungeonPreparationTickBudget budget
    ) {
        DungeonEntryChunkPlan plan = context.entryChunkPlan()
                .orElseThrow(() -> new IllegalStateException(
                        "Validating entry chunks without a plan"
                ));

        List<ChunkPos> chunks = plan.chunks();
        while (context.nextLoadedChunkValidationIndex() < chunks.size()) {
            if (!budget.hasTimeRemaining()) {
                return;
            }
            ChunkPos chunkPos = chunks.get(
                    context.nextLoadedChunkValidationIndex()
            );
            if (!this.backend.isChunkLoaded(chunkPos)) {
                context.diagnosticDetail(
                        "entry chunk " + chunkPos
                                + " is no longer loaded"
                );
                releaseCurrentCandidateResources(job, context);
                retryOrFail(job, context,
                        "entry chunk no longer loaded");
                return;
            }
            context.advanceLoadedChunkValidationIndex();
        }
        advance(job, DungeonPreparationStage.VALIDATING_ENTRY);
    }

    private void advanceSafeEntryScan(
            DungeonPreparationJob job,
            DungeonPreparationExecutionContext context,
            DungeonPreparationTickBudget budget
    ) {
        DungeonSite site = context.resolvedSite()
                .orElseThrow(() -> new IllegalStateException(
                        "Resolving safe entry without a resolved site"
                ));

        DungeonSafeSpawnScan scan = context.safeSpawnScan().orElseGet(() -> {
            DungeonSafeSpawnScan created = this.backend.createSafeEntryScan(site);
            context.safeSpawnScan(created);
            return created;
        });
        DungeonSafeSpawnScanResult result = scan.advance(
                budget,
                DungeonSafeSpawnScanPurpose.PREPARATION
        );
        if (result.state() == DungeonSafeSpawnScanState.RUNNING) {
            return;
        }
        if (result.state() != DungeonSafeSpawnScanState.FOUND) {
            context.diagnosticDetail(
                    "no safe spawn position in primary-entry room"
            );
            releaseCurrentCandidateResources(job, context);
            retryOrFail(job, context,
                    "no safe spawn position");
            return;
        }

        Vec3 spawnPos = result.resolvedPosition().orElseThrow();
        context.clearSafeSpawnScan();
        PreparedDungeonDestination preparedDestination =
                new PreparedDungeonDestination(spawnPos);
        context.preparedDestination(preparedDestination);
        advance(job, DungeonPreparationStage.READY_TO_COMMIT);
    }

    // ── Retry and cleanup helpers ───────────────────────────────────

    private void releaseCurrentCandidateResources(
            DungeonPreparationJob job,
            DungeonPreparationExecutionContext context
    ) {
        releaseCurrentClaim(context);
        closeAllEntryLeases(job, context);
        clearCurrentCandidateState(context, job);
    }

    private void releaseCurrentClaim(
            DungeonPreparationExecutionContext context
    ) {
        DungeonSiteClaim claim = context.currentClaim();
        if (claim != null) {
            this.claimManager.release(claim);
            context.currentClaim(null);
        }
    }

    private void closeAllEntryLeases(
            DungeonPreparationJob job,
            DungeonPreparationExecutionContext context
    ) {
        List<DungeonPreparationStartChunkLease> entryLeases =
                context.entryChunkLeases();
        context.clearEntryChunkLeases();
        context.resetEntryChunkRequestIndex();

        RuntimeException aggregateFailure = null;
        for (DungeonPreparationStartChunkLease lease : entryLeases) {
            try {
                if (job != null && !job.isTerminal()) {
                    job.closeAndRemoveLease(lease);
                } else {
                    lease.close();
                }
            } catch (RuntimeException exception) {
                if (aggregateFailure == null) {
                    aggregateFailure = new IllegalStateException(
                            "Failed to close one or more entry leases"
                    );
                }
                aggregateFailure.addSuppressed(exception);
            }
        }
        if (aggregateFailure != null) {
            throw aggregateFailure;
        }
    }

    private void clearCurrentCandidateState(
            DungeonPreparationExecutionContext context,
            DungeonPreparationJob job
    ) {
        DungeonPreparationStartChunkLease startLease =
                context.currentStartLease().orElse(null);
        context.clearCurrentStartLease();
        context.resolvedSite(null);
        context.entryChunkPlan(null);
        context.clearSafeSpawnScan();
        context.clearCommitPlan();
        context.preparedDestination(null);

        if (startLease != null) {
            if (job != null && !job.isTerminal()) {
                job.closeAndRemoveLease(startLease);
            } else {
                startLease.close();
            }
        }
    }

    private void retryOrFail(
            DungeonPreparationJob job,
            DungeonPreparationExecutionContext context,
            String failureDetail
    ) {
        if (context.hasRemainingPersistedCandidates()
                || context.hasRemainingGenerationCandidates()) {
            advance(job, DungeonPreparationStage.SELECTING_CANDIDATE);
        } else {
            failJob(
                    job,
                    DungeonPreparationJobFailureReason.CHUNK_LOAD_FAILED,
                    failureDetail
            );
        }
    }

    private void advance(
            DungeonPreparationJob job,
            DungeonPreparationStage stage
    ) {
        this.registry.advance(job.id(), stage, this.backend.gameTime());
    }

    private void failJob(
            DungeonPreparationJob job,
            DungeonPreparationJobFailureReason reason,
            String detail
    ) {
        try {
            if (!job.isTerminal()) {
                this.registry.fail(
                        job.id(),
                        reason,
                        detail,
                        this.backend.gameTime()
                );
            }
        } catch (RuntimeException exception) {
            this.backend.logJobRuntimeFailure(job, exception);
        } finally {
            cleanupContext(job.id());
        }
    }

    private void commitReadyJob(
            DungeonPreparationJob job,
            DungeonPreparationExecutionContext context
    ) {
        if (context.commitPlan().isEmpty()) {
            DungeonPreparationProfiler profiler = DungeonPreparationProfiler.global();
            long startNanos = profiler.start();
            DungeonActivationPreflightResult preflight;
            try {
                preflight = this.committer.preflight(job, context);
            } finally {
                profiler.record(
                        DungeonPreparationProfiler.Operation.COMMIT_PREFLIGHT,
                        startNanos,
                        true
                );
            }
            if (preflight.plan().isEmpty()) {
                failJob(
                        job,
                        failureReasonFor(preflight.failureReason().orElse(
                                DungeonActivationCommitFailureReason.INTERNAL_ERROR
                        )),
                        preflight.detail()
                );
                return;
            }
            context.commitPlan(preflight.plan().orElseThrow());
            return;
        }
        advance(job, DungeonPreparationStage.COMMITTING);
        DungeonActivationCommitResult result = this.committer.commit(job, context);
        if (!result.success()) {
            result.failureReason().ifPresent(reason -> {
                if (reason == DungeonActivationCommitFailureReason.SITE_CLAIM_LOST) {
                    this.failureMetrics.recordClaimReleaseInvariantFailure();
                }
            });
            failJob(
                    job,
                    failureReasonFor(result.failureReason()
                            .orElse(DungeonActivationCommitFailureReason.INTERNAL_ERROR)),
                    result.detail()
                );
            return;
        }

        context.committedResult(result);
        tryPublishCommitted(job, context);
    }

    private void tryPublishCommitted(
            DungeonPreparationJob job,
            DungeonPreparationExecutionContext context
    ) {
        DungeonActivationCommitResult result = context.committedResult();
        if (result == null) {
            return;
        }
        try {
            this.registry.publishCommitted(job.id(), this.backend.gameTime());
        } catch (RuntimeException exception) {
            this.backend.logJobRuntimeFailure(job, exception);
            this.failureMetrics.recordCommittedPublicationFailure();
            return;
        }

        cleanupSuccessfulContext(job.id());

        try {
            this.committer.afterCommitReady(job, result);
        } catch (RuntimeException exception) {
            this.backend.logJobRuntimeFailure(job, exception);
        }
    }

    private static DungeonPreparationJobFailureReason failureReasonFor(
            DungeonActivationCommitFailureReason reason
    ) {
        return switch (reason) {
            case INVALID_TRIBUTE ->
                    DungeonPreparationJobFailureReason.INVALID_TRIBUTE;
            case SITE_CONFLICT ->
                    DungeonPreparationJobFailureReason.SITE_CONFLICT;
            case SITE_CLAIM_LOST ->
                    DungeonPreparationJobFailureReason.SITE_CLAIM_LOST;
            case NON_AUTHORITATIVE_SITE ->
                    DungeonPreparationJobFailureReason.NON_AUTHORITATIVE_SITE;
            case PORTAL_SPAWN_FAILED ->
                    DungeonPreparationJobFailureReason.PORTAL_SPAWN_FAILED;
            case PREPARED_ENTRY_REGISTRATION_FAILED ->
                    DungeonPreparationJobFailureReason.PREPARED_ENTRY_REGISTRATION_FAILED;
            case PLAYER_OFFLINE,
                    WRONG_SOURCE_DIMENSION,
                    INVALID_OBELISK,
                    EXISTING_TARGET_UNAVAILABLE ->
                    DungeonPreparationJobFailureReason.COMMIT_VALIDATION_FAILED;
            case INTERNAL_ERROR ->
                    DungeonPreparationJobFailureReason.INTERNAL_ERROR;
        };
    }

    private void cleanupContext(DungeonPreparationJobId id) {
        DungeonPreparationExecutionContext context = this.contexts.remove(id);
        if (context == null) {
            return;
        }
        DungeonPreparationJob job = this.registry.findById(id).orElse(null);
        context.invalidateGeneration();
        context.scanner().ifPresent(scanner -> {
            if (!scanner.state().isTerminal()) {
                scanner.cancel();
            }
        });
        releaseCurrentClaim(context);
        if (job != null && job.isTerminal()) {
            context.clearTransientCandidateState();
            context.clearEntryChunkLeases();
            context.resetEntryChunkRequestIndex();
            context.resolvedSite(null);
            context.entryChunkPlan(null);
            context.clearCommitPlan();
            context.preparedDestination(null);
            return;
        }
        closeAllEntryLeases(job, context);
        clearCurrentCandidateState(context, job);
    }

    private void cleanupSuccessfulContext(DungeonPreparationJobId id) {
        DungeonPreparationExecutionContext context = this.contexts.remove(id);
        if (context == null) {
            return;
        }
        context.invalidateGeneration();
        context.clearTransientCandidateState();
        releaseCurrentClaim(context);
        context.clearEntryChunkLeases();
        context.resetEntryChunkRequestIndex();
        context.resolvedSite(null);
        context.entryChunkPlan(null);
        context.clearCommitPlan();
        context.preparedDestination(null);
    }

    // ── Utility ─────────────────────────────────────────────────────

    private DungeonPreparationExecutionContext requireContext(
            DungeonPreparationJob job
    ) {
        DungeonPreparationExecutionContext context = this.contexts.get(job.id());
        if (context == null) {
            throw new IllegalStateException(
                    "Missing preparation execution context for job " + job.id()
            );
        }
        return context;
    }

    private static DungeonPreparationProgressSnapshot progressSnapshot(
            DungeonPreparationJob job,
            DungeonPreparationExecutionContext context
    ) {
        if (context == null) {
            return DungeonPreparationProgressSnapshot.empty(
                    job.stage(),
                    Optional.ofNullable(job.terminalCause())
            );
        }

        int totalCandidateChunks = context.orderedCandidateKeys().size();
        int submittedCandidateChunks = 0;
        int completedCandidateChunks = 0;
        int inFlightCandidateChunks = 0;

        Optional<AsyncDungeonSiteProbe> scanner = context.scanner();
        if (scanner.isPresent()) {
            DungeonSiteProbeProgress progress = scanner.get().progress();
            totalCandidateChunks = progress.totalCandidates();
            submittedCandidateChunks = progress.submittedCandidates();
            completedCandidateChunks = progress.completedCandidates();
            inFlightCandidateChunks = progress.currentlyInFlight();
        } else if (context.scanReport().isPresent()) {
            DungeonSiteProbeReport report = context.scanReport().get();
            totalCandidateChunks = report.totalCandidates();
            submittedCandidateChunks = report.totalCandidates();
            completedCandidateChunks = report.results().size();
            inFlightCandidateChunks = 0;
        }

        int totalEntryChunks = context.entryChunkPlan()
                .map(plan -> plan.chunks().size())
                .orElse(0);
        int requestedEntryChunks = Math.min(
                context.nextEntryChunkRequestIndex(),
                totalEntryChunks
        );
        int readyEntryChunks = 0;
        for (DungeonPreparationStartChunkLease lease : context.entryChunkLeases()) {
            if (lease.state() == DungeonChunkLeaseState.READY) {
                readyEntryChunks++;
            }
        }

        long totalSafeSpawnCandidates = 0L;
        long checkedSafeSpawnCandidates = 0L;
        Optional<DungeonSafeSpawnScan> safeSpawnScan = context.safeSpawnScan();
        if (safeSpawnScan.isPresent()) {
            DungeonSafeSpawnScanResult safeSpawnProgress =
                    safeSpawnScan.get().result();
            totalSafeSpawnCandidates = safeSpawnProgress.totalCandidates();
            checkedSafeSpawnCandidates = safeSpawnProgress.candidatesChecked();
        }

        return new DungeonPreparationProgressSnapshot(
                job.stage(),
                totalCandidateChunks,
                submittedCandidateChunks,
                completedCandidateChunks,
                inFlightCandidateChunks,
                totalEntryChunks,
                requestedEntryChunks,
                Math.min(readyEntryChunks, requestedEntryChunks),
                totalSafeSpawnCandidates,
                checkedSafeSpawnCandidates,
                Math.min(
                        context.attemptedGenerationCandidateCount(),
                        DungeonPreparationLimits.MAX_GENERATION_ATTEMPTS
                ),
                DungeonPreparationLimits.MAX_GENERATION_ATTEMPTS,
                Optional.ofNullable(job.terminalCause())
        );
    }

   int activePersistedScannerCount() {
        int count = 0;
        for (DungeonPreparationExecutionContext context : this.contexts.values()) {
            Optional<AsyncDungeonSiteProbe> scanner = context.scanner();
            if (scanner.isPresent() && !scanner.get().state().isTerminal()) {
                count++;
            }
        }
        return count;
    }

   DungeonPreparationExecutionContext ctx(DungeonPreparationJobId id) {
       return this.contexts.get(id);
   }


    int activePersistedProbeCount() {
        return this.persistedProbePermits.outstandingCount();
    }

    int pendingScannerCompletionCount() {
        int count = 0;
        for (DungeonPreparationExecutionContext context : this.contexts.values()) {
            Optional<AsyncDungeonSiteProbe> scanner = context.scanner();
            if (scanner.isPresent()) {
                count += scanner.get().pendingCompletionCount();
            }
        }
        return count;
    }

    private static List<DungeonSiteKey> copyKeys(List<DungeonSiteKey> keys) {
        Objects.requireNonNull(keys, "keys");
        ArrayList<DungeonSiteKey> copy = new ArrayList<>(keys.size());
        for (DungeonSiteKey key : keys) {
            copy.add(Objects.requireNonNull(key, "key"));
        }
        return List.copyOf(copy);
    }

    private static String diagnosticDetail(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getName();
        }
        return throwable.getClass().getName() + ": " + message;
    }
}
