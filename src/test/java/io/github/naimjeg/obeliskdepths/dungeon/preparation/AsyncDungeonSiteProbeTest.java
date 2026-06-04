package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonAsyncTestSupport.ControlledProbeBackend;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

public final class AsyncDungeonSiteProbeTest {
    private static final Map<AsyncDungeonSiteProbe, DungeonPersistedProbePermitPool>
            PERMIT_POOLS = new IdentityHashMap<>();

    private AsyncDungeonSiteProbeTest() {
    }

    public static void main(String[] args) {
        DungeonAsyncTestSupport.bootstrapMinecraft();

        startDoesNotSubmitUntilAdvanced();
        completingOneProbeRequiresLaterAdvanceForReplacement();
        peakInFlightNeverExceedsConfiguredMaximum();
        concurrencyOneIsSequential();
        concurrencyAboveCandidateCountDoesNotOversubmit();
        genuineOutOfOrderCompletionPreservesResultAndAvailableOrder();
        failedCandidateDoesNotAbortLaterCandidates();
        exceptionalBackendFuturePreservesThrowable();
        synchronousBackendFailureBecomesCandidateFailure();
        nullBackendFutureBecomesCandidateFailure();
        nullBackendResultBecomesCandidateFailure();
        mismatchedBackendResultIsNormalizedToRequestedCandidate();
        zeroCandidatesCompleteImmediately();
        loadedFastPathAvoidsDiskProbeAndRetainsActualStatus();
        callbackWaitsForBudgetedMailboxDrain();
        completionMailboxDrainIsBounded();
        workerCompletionUsesMailboxWithoutOwnerDispatch();
        ownerDispatchRejectionTerminatesThroughMailbox();
        permitPoolFullPausesUntilCompletionReleases();
        wrongThreadPublicMutationIsRejected();
        externalFutureCompletionCannotCompleteAuthoritativeFuture();

        cancellationBeforeStartMarksEveryCandidateCancelled();
        cancellationWhileRunningMarksEveryUnresolvedCandidateCancelled();
        cancellationStopsFurtherSubmissionsAndIgnoresLateCompletions();
        cancellationRetainsPermitUntilLatePhysicalCompletion();
        cancellationIsIdempotent();
        completedScannerCannotTransitionToCancelled();
        terminalPublicationHappensOnce();
    }

    private static void startDoesNotSubmitUntilAdvanced() {
        ControlledProbeBackend backend = new ControlledProbeBackend();
        AsyncDungeonSiteProbe scanner = scanner(backend, 5, 2);

        scanner.start();

        check(backend.probeCalls() == 0, "start: no immediate submissions");
        check(scanner.hasSubmissionWork(), "start: submission work pending");
        advance(scanner);
        check(backend.probeCalls() == 2, "advance: submitted max concurrency");
        DungeonSiteProbeProgress progress = scanner.progress();
        check(progress.submittedCandidates() == 2, "advance: submitted count");
        check(progress.currentlyInFlight() == 2, "advance: in-flight count");
    }

    private static void completingOneProbeRequiresLaterAdvanceForReplacement() {
        ControlledProbeBackend backend = new ControlledProbeBackend();
        AsyncDungeonSiteProbe scanner = scanner(backend, 5, 2);
        startAndAdvance(scanner);

        backend.completeAvailable(0);
        drainOwner(backend, scanner);

        check(backend.probeCalls() == 2,
                "replacement: no immediate refill during completion drain");
        advance(scanner);
        check(backend.probeCalls() == 3,
                "replacement: later advance submits one replacement");
        DungeonSiteProbeProgress progress = scanner.progress();
        check(progress.completedCandidates() == 1,
                "replacement: completed one");
        check(progress.currentlyInFlight() == 2,
                "replacement: still at bound");
    }

    private static void peakInFlightNeverExceedsConfiguredMaximum() {
        ControlledProbeBackend backend = new ControlledProbeBackend();
        AsyncDungeonSiteProbe scanner = scanner(backend, 5, 2);
        startAndAdvance(scanner);

        for (int i = 0; i < 5; i++) {
            backend.completeAvailable(i);
            drainOwner(backend, scanner);
            advance(scanner);
        }

        DungeonSiteProbeReport report = completedReport(scanner);
        check(report.peakInFlight() == 2, "peak: bounded at 2");
        check(report.results().size() == 5, "peak: complete result set");
    }

    private static void concurrencyOneIsSequential() {
        ControlledProbeBackend backend = new ControlledProbeBackend();
        AsyncDungeonSiteProbe scanner = scanner(backend, 3, 1);
        startAndAdvance(scanner);
        check(backend.probeCalls() == 1, "sequential: first only");

        backend.completeAvailable(0);
        drainOwner(backend, scanner);
        advance(scanner);
        check(backend.probeCalls() == 2, "sequential: second after first");

        backend.completeAvailable(1);
        drainOwner(backend, scanner);
        advance(scanner);
        check(backend.probeCalls() == 3, "sequential: third after second");

        backend.completeAvailable(2);
        drainOwner(backend, scanner);
        DungeonSiteProbeReport report = completedReport(scanner);
        check(report.peakInFlight() == 1, "sequential: peak one");
    }

    private static void concurrencyAboveCandidateCountDoesNotOversubmit() {
        ControlledProbeBackend backend = new ControlledProbeBackend();
        AsyncDungeonSiteProbe scanner = scanner(backend, 3, 100);

        startAndAdvance(scanner);

        check(backend.probeCalls() == 3, "oversubmit: only candidates");
        check(scanner.progress().currentlyInFlight() == 3,
                "oversubmit: all candidates in flight");
    }

    private static void genuineOutOfOrderCompletionPreservesResultAndAvailableOrder() {
        ControlledProbeBackend backend = new ControlledProbeBackend();
        AsyncDungeonSiteProbe scanner = scanner(backend, 4, 4);
        startAndAdvance(scanner);
        check(backend.probeCalls() == 4, "out-of-order: four futures held");

        int[] completionOrder = {3, 0, 2, 1};
        for (int submissionIndex : completionOrder) {
            backend.completeAvailable(submissionIndex);
            drainOwner(backend, scanner);
        }

        DungeonSiteProbeReport report = completedReport(scanner);
        for (int i = 0; i < 4; i++) {
            check(report.results().get(i).chunkPos().equals(chunk(i)),
                    "out-of-order: result order " + i);
            check(report.availableKeys().get(i).equals(new DungeonSiteKey(i, 0)),
                    "out-of-order: available key order " + i);
        }
    }

    private static void failedCandidateDoesNotAbortLaterCandidates() {
        ControlledProbeBackend backend = new ControlledProbeBackend();
        AsyncDungeonSiteProbe scanner = scanner(backend, 3, 1);
        startAndAdvance(scanner);

        backend.complete(0, DungeonAsyncTestSupport.failed(
                chunk(0),
                new RuntimeException("candidate failed")
        ));
        drainOwner(backend, scanner);
        advance(scanner);
        backend.completeAvailable(1);
        drainOwner(backend, scanner);
        advance(scanner);
        backend.completeAvailable(2);
        drainOwner(backend, scanner);

        DungeonSiteProbeReport report = completedReport(scanner);
        check(report.failedCount() == 1, "partial failure: failed count");
        check(report.availableCount() == 2, "partial failure: later available");
        check(report.results().size() == 3, "partial failure: all results");
    }

    private static void exceptionalBackendFuturePreservesThrowable() {
        ControlledProbeBackend backend = new ControlledProbeBackend();
        AsyncDungeonSiteProbe scanner = scanner(backend, 2, 1);
        RuntimeException cause = new RuntimeException("storage failed");
        startAndAdvance(scanner);

        backend.completeExceptionally(0, cause);
        drainOwner(backend, scanner);
        advance(scanner);
        backend.completeAvailable(1);
        drainOwner(backend, scanner);

        DungeonPersistedChunkProbeResult failure =
                completedReport(scanner).results().get(0);
        check(failure.classification()
                        == DungeonPersistedChunkProbeResult.Classification
                        .SCAN_FAILED,
                "exceptional future: classification");
        check(failure.failure().isPresent(), "exceptional future: failure");
        check(failure.failure().get() == cause,
                "exceptional future: throwable preserved");
        check(failure.chunkPos().equals(chunk(0)),
                "exceptional future: requested chunk retained");
    }

    private static void synchronousBackendFailureBecomesCandidateFailure() {
        ControlledProbeBackend backend = new ControlledProbeBackend();
        RuntimeException cause = new RuntimeException("sync storage failed");
        backend.addSynchronousFailure(chunk(0), cause);
        AsyncDungeonSiteProbe scanner = scanner(backend, 2, 1);

        startAndAdvance(scanner);
        advance(scanner);
        backend.completeAvailable(1);
        drainOwner(backend, scanner);

        DungeonPersistedChunkProbeResult failure =
                completedReport(scanner).results().get(0);
        check(failure.classification()
                        == DungeonPersistedChunkProbeResult.Classification
                        .SCAN_FAILED,
                "sync failure: classification");
        check(failure.failure().get() == cause,
                "sync failure: throwable preserved");
        check(completedReport(scanner).availableCount() == 1,
                "sync failure: later candidate scanned");
    }

    private static void nullBackendFutureBecomesCandidateFailure() {
        ControlledProbeBackend backend = new ControlledProbeBackend();
        backend.addNullFutureChunk(chunk(0));
        AsyncDungeonSiteProbe scanner = scanner(backend, 2, 1);

        startAndAdvance(scanner);
        advance(scanner);
        backend.completeAvailable(1);
        drainOwner(backend, scanner);

        DungeonPersistedChunkProbeResult failure =
                completedReport(scanner).results().get(0);
        check(failure.classification()
                        == DungeonPersistedChunkProbeResult.Classification
                        .SCAN_FAILED,
                "null future: classification");
        check(failure.failure().isPresent(), "null future: diagnostic cause");
        check(completedReport(scanner).availableCount() == 1,
                "null future: later candidate scanned");
    }

    private static void nullBackendResultBecomesCandidateFailure() {
        ControlledProbeBackend backend = new ControlledProbeBackend();
        AsyncDungeonSiteProbe scanner = scanner(backend, 1, 1);
        startAndAdvance(scanner);

        backend.submission(0).future().complete(null);
        drainOwner(backend, scanner);

        DungeonPersistedChunkProbeResult failure =
                completedReport(scanner).results().get(0);
        check(failure.classification()
                        == DungeonPersistedChunkProbeResult.Classification
                        .SCAN_FAILED,
                "null result: classification");
        check(failure.failure().isEmpty(), "null result: no throwable");
        check(failure.chunkPos().equals(chunk(0)),
                "null result: requested chunk retained");
    }

    private static void mismatchedBackendResultIsNormalizedToRequestedCandidate() {
        ControlledProbeBackend backend = new ControlledProbeBackend();
        AsyncDungeonSiteProbe scanner = scanner(backend, 1, 1);
        startAndAdvance(scanner);

        backend.complete(0, DungeonAsyncTestSupport.available(new ChunkPos(99, 0)));
        drainOwner(backend, scanner);

        DungeonPersistedChunkProbeResult failure =
                completedReport(scanner).results().get(0);
        check(failure.classification()
                        == DungeonPersistedChunkProbeResult.Classification
                        .SCAN_FAILED,
                "mismatch: classification");
        check(failure.chunkPos().equals(chunk(0)),
                "mismatch: requested chunk retained");
        check(failure.detail().contains("99"),
                "mismatch: diagnostic detail");
    }

    private static void zeroCandidatesCompleteImmediately() {
        ControlledProbeBackend backend = new ControlledProbeBackend();
        AsyncDungeonSiteProbe scanner = new AsyncDungeonSiteProbe(
                List.of(),
                fullStatus(),
                4,
                backend
        );

        startAndAdvance(scanner);

        DungeonSiteProbeReport report = completedReport(scanner);
        check(report.totalCandidates() == 0, "zero: total");
        check(report.results().isEmpty(), "zero: results");
        check(scanner.state() == AsyncDungeonSiteProbeState.COMPLETED,
                "zero: state");
    }

    private static void loadedFastPathAvoidsDiskProbeAndRetainsActualStatus() {
        ControlledProbeBackend backend = new ControlledProbeBackend();
        backend.addLoadedResult(
                chunk(0),
                DungeonAsyncTestSupport.available(chunk(0), ChunkStatus.FULL)
        );
        AsyncDungeonSiteProbe scanner = new AsyncDungeonSiteProbe(
                DungeonAsyncTestSupport.candidates(1),
                ChunkStatus.STRUCTURE_STARTS,
                4,
                backend
        );

        startAndAdvance(scanner);

        DungeonSiteProbeReport report = completedReport(scanner);
        check(backend.probeCalls() == 0, "loaded: no disk probe");
        check(backend.loadedProbeCalls() == 1, "loaded: fast path invoked");
        check(backend.loadedProbeWasOwnerThread(0),
                "loaded: owner thread");
        check(report.results().get(0).persistedStatus()
                        .equals(Optional.of(ChunkStatus.FULL)),
                "loaded: actual status retained");
    }

    private static void callbackWaitsForBudgetedMailboxDrain() {
        ControlledProbeBackend backend = new ControlledProbeBackend();
        AsyncDungeonSiteProbe scanner = scanner(backend, 1, 1);
        startAndAdvance(scanner);

        backend.completeAvailable(0);
        check(scanner.pendingCompletionCount() == 1,
                "callback: completion added to mailbox");
        check(scanner.progress().completedCandidates() == 0,
                "callback: state unchanged before drain");
        requireNotCompleted(scanner, "callback: terminal not published");

        drainOwner(backend, scanner);

        check(scanner.progress().completedCandidates() == 1,
                "callback: state changed after drain");
        completedReport(scanner);
    }

    private static void completionMailboxDrainIsBounded() {
        ControlledProbeBackend backend = new ControlledProbeBackend();
        AsyncDungeonSiteProbe scanner = scanner(backend, 4, 4);
        startAndAdvance(scanner);

        for (int i = 0; i < 4; i++) {
            backend.completeAvailable(i);
        }
        check(scanner.pendingCompletionCount() == 4,
                "bounded drain: four queued");

        check(scanner.drainCompletionMailbox(2) == 2,
                "bounded drain: drained requested count");
        check(scanner.progress().completedCandidates() == 2,
                "bounded drain: completed two");
        check(scanner.pendingCompletionCount() == 2,
                "bounded drain: two remain");
        requireNotCompleted(scanner, "bounded drain: not complete after partial drain");

        check(scanner.drainCompletionMailbox(2) == 2,
                "bounded drain: drained remaining");
        check(scanner.pendingCompletionCount() == 0,
                "bounded drain: queue empty");
        completedReport(scanner);
    }

    private static void workerCompletionUsesMailboxWithoutOwnerDispatch() {
        ControlledProbeBackend backend = new ControlledProbeBackend();
        AsyncDungeonSiteProbe scanner = scanner(backend, 1, 1);
        startAndAdvance(scanner);

        backend.ownerExecutor.setOwnerThread(false);
        backend.completeAvailable(0);

        check(backend.ownerExecutor.pendingTaskCount() == 0,
                "worker mailbox: no server-executor dispatch");
        backend.ownerExecutor.setOwnerThread(true);
        check(scanner.pendingCompletionCount() == 1,
                "worker mailbox: immutable completion retained");
        check(scanner.progress().completedCandidates() == 0,
                "worker mailbox: no off-thread scanner mutation");

        check(scanner.drainCompletionMailbox(1) == 1,
                "worker mailbox: owner drain applies completion");
        completedReport(scanner);
    }

    private static void ownerDispatchRejectionTerminatesThroughMailbox() {
        ControlledProbeBackend backend = new ControlledProbeBackend();
        AsyncDungeonSiteProbe scanner = scanner(backend, 1, 1);
        DungeonPersistedProbePermitPool permits =
                new DungeonPersistedProbePermitPool(1);
        scanner.start();
        scanner.advanceSubmissions(1, 1, 1, permits);

        check(permits.outstandingCount() == 1,
                "rejection mailbox: permit held while backend pending");
        backend.ownerExecutor.setOwnerThread(false);
        backend.completeExceptionally(
                0,
                new RejectedExecutionException("owner dispatch rejected")
        );

        check(permits.outstandingCount() == 0,
                "rejection mailbox: terminal backend future releases permit");
        check(backend.ownerExecutor.pendingTaskCount() == 0,
                "rejection mailbox: scanner performs no second dispatch");
        backend.ownerExecutor.setOwnerThread(true);
        check(scanner.currentlyInFlight() == 1,
                "rejection mailbox: in-flight retained until owner drain");
        check(scanner.drainCompletionMailbox(1) == 1,
                "rejection mailbox: owner drain observes failure");
        check(scanner.currentlyInFlight() == 0,
                "rejection mailbox: owner drain decrements in-flight");

        DungeonSiteProbeReport report = completedReport(scanner);
        check(report.failedCount() == 1,
                "rejection mailbox: candidate terminates as failed");
    }

    private static void permitPoolFullPausesUntilCompletionReleases() {
        ControlledProbeBackend backend = new ControlledProbeBackend();
        AsyncDungeonSiteProbe scanner = scanner(backend, 3, 2);
        DungeonPersistedProbePermitPool permits =
                new DungeonPersistedProbePermitPool(1);
        scanner.start();
        scanner.advanceSubmissions(3, 3, 3, permits);

        check(backend.probeCalls() == 1, "permit full: first submission only");
        check(permits.outstandingCount() == 1,
                "permit full: first permit held");

        scanner.advanceSubmissions(3, 3, 3, permits);
        check(backend.probeCalls() == 1,
                "permit full: occupied pool blocks submission");

        backend.completeAvailable(0);
        check(permits.outstandingCount() == 0,
                "permit full: completion releases permit");
        scanner.drainCompletionMailbox(1);
        scanner.advanceSubmissions(3, 3, 3, permits);

        check(backend.probeCalls() == 2,
                "permit full: later advance submits replacement");
        check(permits.outstandingCount() == 1,
                "permit full: replacement holds permit");
    }

    private static void wrongThreadPublicMutationIsRejected() {
        ControlledProbeBackend backend = new ControlledProbeBackend();
        backend.ownerExecutor.setOwnerThread(false);
        AsyncDungeonSiteProbe notStarted = scanner(backend, 1, 1);
        expectIllegalState(notStarted::start, "wrong thread: start");

        ControlledProbeBackend runningBackend = new ControlledProbeBackend();
        AsyncDungeonSiteProbe running = scanner(runningBackend, 1, 1);
        startAndAdvance(running);
        runningBackend.ownerExecutor.setOwnerThread(false);
        expectIllegalState(running::cancel, "wrong thread: cancel");
        expectIllegalState(running::progress, "wrong thread: progress");
        expectIllegalState(running::state, "wrong thread: state");
        expectIllegalState(
                () -> running.drainCompletionMailbox(1),
                "wrong thread: mailbox drain"
        );
    }

    private static void externalFutureCompletionCannotCompleteAuthoritativeFuture() {
        ControlledProbeBackend backend = new ControlledProbeBackend();
        AsyncDungeonSiteProbe scanner = scanner(backend, 1, 1);
        startAndAdvance(scanner);

        CompletableFuture<DungeonSiteProbeReport> external =
                scanner.completion().toCompletableFuture();
        check(external.complete(DungeonAsyncTestSupport.emptyReport()),
                "external future: dependent completed");
        requireNotCompleted(scanner, "external future: scanner still pending");

        backend.completeAvailable(0);
        drainOwner(backend, scanner);
        DungeonSiteProbeReport report = completedReport(scanner);
        check(report.totalCandidates() == 1,
                "external future: authoritative report");
    }

    private static void cancellationBeforeStartMarksEveryCandidateCancelled() {
        ControlledProbeBackend backend = new ControlledProbeBackend();
        AsyncDungeonSiteProbe scanner = scanner(backend, 3, 2);

        scanner.cancel();

        DungeonSiteProbeReport report = completedReport(scanner);
        DungeonSiteProbeProgress progress = scanner.progress();
        check(report.wasCancelled(), "cancel before start: flag");
        check(report.cancelledCount() == 3,
                "cancel before start: cancelled count");
        check(report.results().size() == 3,
                "cancel before start: one result per candidate");
        check(progress.submittedCandidates() == 3,
                "cancel before start: logical submitted");
        check(progress.completedCandidates() == 3,
                "cancel before start: logical completed");
        check(progress.currentlyInFlight() == 0,
                "cancel before start: no in-flight");
        check(backend.probeCalls() == 0, "cancel before start: no probes");
    }

    private static void cancellationWhileRunningMarksEveryUnresolvedCandidateCancelled() {
        ControlledProbeBackend backend = new ControlledProbeBackend();
        AsyncDungeonSiteProbe scanner = scanner(backend, 5, 2);
        startAndAdvance(scanner);
        backend.completeAvailable(0);
        drainOwner(backend, scanner);

        scanner.cancel();

        DungeonSiteProbeReport report = completedReport(scanner);
        DungeonSiteProbeProgress progress = scanner.progress();
        check(report.results().size() == 5,
                "cancel running: one result per candidate");
        check(report.availableCount() == 1,
                "cancel running: completed retained");
        check(report.cancelledCount() == 4,
                "cancel running: unresolved cancelled");
        check(progress.currentlyInFlight() == 0,
                "cancel running: logical in-flight zero");
        check(progress.completedCandidates() == 5,
                "cancel running: logical complete");
    }

    private static void cancellationStopsFurtherSubmissionsAndIgnoresLateCompletions() {
        ControlledProbeBackend backend = new ControlledProbeBackend();
        AsyncDungeonSiteProbe scanner = scanner(backend, 5, 2);
        startAndAdvance(scanner);

        scanner.cancel();
        DungeonSiteProbeReport beforeReport = completedReport(scanner);
        DungeonSiteProbeProgress beforeProgress = scanner.progress();

        backend.completeAvailable(0);
        backend.completeAvailable(1);
        drainOwner(backend, scanner);

        DungeonSiteProbeReport afterReport = completedReport(scanner);
        DungeonSiteProbeProgress afterProgress = scanner.progress();
        check(backend.probeCalls() == 2,
                "cancel late: no replacement submissions");
        check(afterReport.equals(beforeReport),
                "cancel late: report unchanged");
        check(afterProgress.equals(beforeProgress),
                "cancel late: progress unchanged");
    }

    private static void cancellationRetainsPermitUntilLatePhysicalCompletion() {
        ControlledProbeBackend backend = new ControlledProbeBackend();
        AsyncDungeonSiteProbe scanner = scanner(backend, 1, 1);
        DungeonPersistedProbePermitPool permits =
                new DungeonPersistedProbePermitPool(1);
        scanner.start();
        scanner.advanceSubmissions(1, 1, 1, permits);

        scanner.cancel();
        DungeonSiteProbeReport published = completedReport(scanner);
        check(permits.outstandingCount() == 1,
                "cancel permit: logical cancellation retains physical permit");

        backend.ownerExecutor.setOwnerThread(false);
        backend.completeAvailable(0);
        check(permits.outstandingCount() == 0,
                "cancel permit: late physical completion releases permit");
        backend.ownerExecutor.setOwnerThread(true);
        check(scanner.drainCompletionMailbox(1) == 1,
                "cancel permit: late envelope can be drained");
        check(completedReport(scanner).equals(published),
                "cancel permit: late completion does not republish");
        check(scanner.progress().currentlyInFlight() == 0,
                "cancel permit: no logical ownership remains");
    }

    private static void cancellationIsIdempotent() {
        ControlledProbeBackend backend = new ControlledProbeBackend();
        AsyncDungeonSiteProbe scanner = scanner(backend, 4, 2);
        startAndAdvance(scanner);

        scanner.cancel();
        DungeonSiteProbeReport firstReport = completedReport(scanner);
        DungeonSiteProbeProgress firstProgress = scanner.progress();
        scanner.cancel();

        check(completedReport(scanner).equals(firstReport),
                "cancel idempotent: report");
        check(scanner.progress().equals(firstProgress),
                "cancel idempotent: progress");
    }

    private static void completedScannerCannotTransitionToCancelled() {
        ControlledProbeBackend backend = new ControlledProbeBackend();
        AsyncDungeonSiteProbe scanner = scanner(backend, 1, 1);
        startAndAdvance(scanner);
        backend.completeAvailable(0);
        drainOwner(backend, scanner);
        DungeonSiteProbeReport beforeReport = completedReport(scanner);
        DungeonSiteProbeProgress beforeProgress = scanner.progress();

        scanner.cancel();

        check(scanner.state() == AsyncDungeonSiteProbeState.COMPLETED,
                "completed cancel: state remains completed");
        check(completedReport(scanner).equals(beforeReport),
                "completed cancel: report unchanged");
        check(scanner.progress().equals(beforeProgress),
                "completed cancel: progress unchanged");
    }

    private static void terminalPublicationHappensOnce() {
        ControlledProbeBackend backend = new ControlledProbeBackend();
        AsyncDungeonSiteProbe scanner = scanner(backend, 1, 1);
        AtomicInteger publications = new AtomicInteger();
        scanner.completion().whenComplete((report, throwable) ->
                publications.incrementAndGet());
        startAndAdvance(scanner);

        scanner.cancel();
        scanner.cancel();
        backend.completeAvailable(0);
        drainOwner(backend, scanner);

        check(publications.get() == 1, "terminal publication: once");
    }

    private static AsyncDungeonSiteProbe scanner(
            ControlledProbeBackend backend,
            int candidates,
            int maxConcurrentProbes
    ) {
        return new AsyncDungeonSiteProbe(
                DungeonAsyncTestSupport.candidates(candidates),
                fullStatus(),
                maxConcurrentProbes,
                backend
        );
    }

    private static DungeonSiteProbeReport completedReport(
            AsyncDungeonSiteProbe scanner
    ) {
        return DungeonAsyncTestSupport.requireCompleted(
                scanner.completion(),
                "scanner completion"
        );
    }

    private static void requireNotCompleted(
            AsyncDungeonSiteProbe scanner,
            String message
    ) {
        DungeonAsyncTestSupport.requireNotCompleted(
                scanner.completion(),
                message
        );
    }

    private static void startAndAdvance(AsyncDungeonSiteProbe scanner) {
        scanner.start();
        advance(scanner);
    }

    private static void advance(AsyncDungeonSiteProbe scanner) {
        scanner.advanceSubmissions(
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                PERMIT_POOLS.computeIfAbsent(
                        scanner,
                        ignored -> new DungeonPersistedProbePermitPool(
                                Integer.MAX_VALUE
                        )
                )
        );
    }

    private static void drainOwner(
            ControlledProbeBackend backend,
            AsyncDungeonSiteProbe scanner
    ) {
        scanner.drainCompletionMailbox(Integer.MAX_VALUE);
    }

    private static ChunkPos chunk(int index) {
        return DungeonAsyncTestSupport.chunk(index);
    }

    private static ChunkStatus fullStatus() {
        return ChunkStatus.FULL;
    }

    private static void expectIllegalState(
            Runnable runnable,
            String message
    ) {
        try {
            runnable.run();
            check(false, message + ": should throw");
        } catch (IllegalStateException expected) {
            // expected
        }
    }

    private static void check(boolean condition, String message) {
        DungeonAsyncTestSupport.check(condition, message);
    }
}

