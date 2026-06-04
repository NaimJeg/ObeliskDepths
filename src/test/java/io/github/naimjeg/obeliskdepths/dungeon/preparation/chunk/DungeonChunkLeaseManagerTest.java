package io.github.naimjeg.obeliskdepths.dungeon.preparation.chunk;

import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonAsyncTestSupport;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class DungeonChunkLeaseManagerTest {
    private DungeonChunkLeaseManagerTest() {
    }

    public static void main(String[] args) {
        DungeonAsyncTestSupport.bootstrapMinecraft();

        initialStateIsPending();
        successBecomesReadyAfterOwnerDrain();
        failureBecomesFailedAfterOwnerDrainAndOutcomeVisible();
        typedNullCompletionBecomesFailureAfterOwnerDrain();
        typedExceptionalCompletionBecomesFailureAfterOwnerDrain();
        duplicateAcquisitionSharesOneBackendRequest();
        oneOfTwoCloseRetainsTicket();
        finalCloseReleasesTicketOnce();
        finalCloseBackendExceptionRetainsCleanupDebtUntilRetry();
        pendingReleaseRetriesAreBounded();
        pendingReleaseReacquisitionReassertsPhysicalTicket();
        reacquisitionFailurePreservesCleanupDebt();
        nullReacquisitionRequestPreservesCleanupDebt();
        nullReacquisitionCompletionPreservesCleanupDebt();
        missingReassertedTicketDoesNotCreateLease();
        rejectedReacquisitionCompletionCannotMutateOldEntry();
        rejectedReacquisitionStillClearsPhysically();
        staleOriginalCompletionCannotOverwriteReassertion();
        multipleReacquisitionsShareReassertedEntry();
        doubleCloseIsHarmless();
        offThreadCloseDoesNotConsumeHandle();
        replacementEntryReceivesNewToken();
        oldSuccessCallbackCannotAffectReplacement();
        oldFailureCallbackCannotAffectReplacement();
        oldHandleCannotReleaseReplacement();
        oldHandleCannotObserveReplacementReadyState();
        clearReleasesEveryTicketOnce();
        terminalClearSucceedsOnLaterBoundedPass();
        terminalClearExhaustsFixedPassLimitAndReportsDebt();
        terminalClearAttemptsPendingEntriesFairly();
        terminalClearFailureDoesNotPreventIndependentRelease();
        terminalClearAggregatesRuntimeFailures();
        clearAttemptsEveryTicketAfterReleaseError();
        repeatedClearIsHarmless();
        lateCompletionAfterClearIsIgnored();
        acquisitionThrowLeavesNoEntryOrTicket();
        backendFutureIsNeverCancelled();
        differentChunksAreIndependent();
    }

    private static void initialStateIsPending() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager manager = new DungeonChunkLeaseManager(backend);
        ChunkPos pos = new ChunkPos(0, 0);

        DungeonChunkLease lease = manager.acquire(pos);

        check(lease.state() == DungeonChunkLeaseState.PENDING,
                "initial: PENDING");
        check(manager.refCountFor(pos) == 1, "initial: ref count");
        lease.close();
    }

    private static void successBecomesReadyAfterOwnerDrain() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager manager = new DungeonChunkLeaseManager(backend);
        ChunkPos pos = new ChunkPos(0, 0);
        DungeonChunkLease lease = manager.acquire(pos);

        backend.request(0).completeSuccessfully();

        check(lease.state() == DungeonChunkLeaseState.PENDING,
                "success: deferred until owner drain");
        backend.executePending();
        check(lease.state() == DungeonChunkLeaseState.READY,
                "success: READY");
        check(lease.outcome().isEmpty(), "success: no failure outcome");
        lease.close();
    }

    private static void failureBecomesFailedAfterOwnerDrainAndOutcomeVisible() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager manager = new DungeonChunkLeaseManager(backend);
        ChunkPos pos = new ChunkPos(0, 0);
        DungeonChunkLease lease = manager.acquire(pos);
        RuntimeException cause = new RuntimeException("load failed");

        backend.request(0).completeExceptionally(cause);

        check(lease.state() == DungeonChunkLeaseState.PENDING,
                "failure: deferred until owner drain");
        backend.executePending();
        check(lease.state() == DungeonChunkLeaseState.FAILED,
                "failure: FAILED");
        check(lease.outcome().isPresent(), "failure: outcome visible");
        DungeonChunkLoadOutcome outcome = lease.outcome().get();
        check(outcome instanceof DungeonChunkLoadOutcome.ExceptionalCompletion,
                "failure: exceptional outcome");
        check(((DungeonChunkLoadOutcome.ExceptionalCompletion) outcome)
                        .throwable() == cause,
                "failure: throwable preserved");
        lease.close();
    }

    private static void typedNullCompletionBecomesFailureAfterOwnerDrain() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager manager = new DungeonChunkLeaseManager(backend);
        ChunkPos pos = new ChunkPos(0, 0);
        DungeonChunkLease lease = manager.acquire(pos);

        backend.request(0).completeWithNull();

        check(lease.state() == DungeonChunkLeaseState.PENDING,
                "typed null: deferred until owner drain");
        check(backend.releaseCalls.isEmpty(),
                "typed null: ticket retained while pending");
        backend.executePending();
        check(lease.state() == DungeonChunkLeaseState.FAILED,
                "typed null: FAILED");
        DungeonChunkLoadOutcome outcome = lease.outcome().orElseThrow();
        check(outcome instanceof DungeonChunkLoadOutcome.UnexpectedResultType,
                "typed null: unexpected result outcome");
        check(outcome.detail().equals("Null typed chunk-load outcome"),
                "typed null: stable detail");
        check(backend.releaseCalls.isEmpty(),
                "typed null: ticket retained after failure");

        lease.close();
        check(backend.releaseCalls.size() == 1,
                "typed null: close releases once");
    }

    private static void typedExceptionalCompletionBecomesFailureAfterOwnerDrain() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager manager = new DungeonChunkLeaseManager(backend);
        ChunkPos pos = new ChunkPos(0, 0);
        DungeonChunkLease lease = manager.acquire(pos);
        RuntimeException cause = new RuntimeException("typed boom");

        backend.request(0).completeFutureExceptionally(cause);

        check(lease.state() == DungeonChunkLeaseState.PENDING,
                "typed exceptional: deferred until owner drain");
        check(backend.releaseCalls.isEmpty(),
                "typed exceptional: ticket retained while pending");
        backend.executePending();
        check(lease.state() == DungeonChunkLeaseState.FAILED,
                "typed exceptional: FAILED");
        DungeonChunkLoadOutcome outcome = lease.outcome().orElseThrow();
        check(outcome instanceof DungeonChunkLoadOutcome.ExceptionalCompletion,
                "typed exceptional: exceptional outcome");
        check(((DungeonChunkLoadOutcome.ExceptionalCompletion) outcome)
                        .throwable() == cause,
                "typed exceptional: throwable preserved");
        check(backend.releaseCalls.isEmpty(),
                "typed exceptional: ticket retained after failure");

        lease.close();
        lease.close();
        check(backend.releaseCalls.size() == 1,
                "typed exceptional: final close releases once");
    }

    private static void duplicateAcquisitionSharesOneBackendRequest() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager manager = new DungeonChunkLeaseManager(backend);
        ChunkPos pos = new ChunkPos(0, 0);

        DungeonChunkLease first = manager.acquire(pos);
        DungeonChunkLease second = manager.acquire(pos);

        check(backend.requests.size() == 1,
                "shared: one backend request");
        check(manager.refCountFor(pos) == 2, "shared: ref count");
        first.close();
        second.close();
        check(backend.releaseCalls.size() == 1, "shared: one release");
    }

    private static void oneOfTwoCloseRetainsTicket() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager manager = new DungeonChunkLeaseManager(backend);
        ChunkPos pos = new ChunkPos(0, 0);
        DungeonChunkLease first = manager.acquire(pos);
        DungeonChunkLease second = manager.acquire(pos);

        first.close();

        check(backend.releaseCalls.isEmpty(), "retain: no release");
        check(manager.refCountFor(pos) == 1, "retain: one owner");
        check(manager.hasActiveEntry(pos), "retain: entry active");
        second.close();
    }

    private static void finalCloseReleasesTicketOnce() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager manager = new DungeonChunkLeaseManager(backend);
        ChunkPos pos = new ChunkPos(0, 0);
        DungeonChunkLease lease = manager.acquire(pos);

        lease.close();

        check(backend.releaseCalls.size() == 1, "final: one release");
        check(backend.releaseCalls.get(0).equals(pos),
                "final: correct chunk");
        check(!manager.hasActiveEntry(pos), "final: removed");
    }

    private static void finalCloseBackendExceptionRetainsCleanupDebtUntilRetry() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager manager = new DungeonChunkLeaseManager(backend);
        ChunkPos pos = new ChunkPos(0, 0);
        DungeonChunkLease lease = manager.acquire(pos);
        FakeRequest oldRequest = backend.request(0);
        long oldToken = manager.tokenFor(pos);
        backend.failRelease(pos, "release");

        try {
            lease.close();
            check(false, "final failure: should throw");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().contains("release"),
                    "final failure: propagated");
        }

        check(!manager.hasActiveEntry(pos), "final failure: no active lease");
        check(manager.activeLeaseCount() == 0, "final failure: active zero");
        check(manager.pendingReleaseCount() == 1,
                "final failure: physical cleanup retained as debt");
        check(manager.pendingReleaseFailureCount() == 1L,
                "final failure: diagnostic counter records failure");
        lease.close();
        check(backend.releaseCalls.size() == 1,
                "final failure: no second release");

        oldRequest.completeSuccessfully();
        backend.executePending();
        check(manager.retryPendingReleases(1, () -> true) == 1,
                "final failure: later owner tick retries debt");
        check(manager.pendingReleaseCount() == 0,
                "final failure: successful retry clears debt");
        check(backend.releaseCalls.size() == 2,
                "final failure: physical release retried once");
        check(manager.pendingReleaseFailureCount() == 1L,
                "final failure: successful retry does not increment diagnostics");

        DungeonChunkLease replacement = manager.acquire(pos);
        long replacementToken = manager.tokenFor(pos);
        check(replacementToken != oldToken,
                "final failure: replacement token");
        check(replacement.state() == DungeonChunkLeaseState.PENDING,
                "final failure: old completion ignored");
        replacement.close();
    }

    private static void pendingReleaseRetriesAreBounded() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager manager = new DungeonChunkLeaseManager(backend);
        ChunkPos first = new ChunkPos(0, 0);
        ChunkPos second = new ChunkPos(1, 0);
        DungeonChunkLease firstLease = manager.acquire(first);
        DungeonChunkLease secondLease = manager.acquire(second);
        backend.failRelease(first, "first release");
        backend.failRelease(second, "second release");
        try {
            firstLease.close();
        } catch (RuntimeException expected) {
        }
        try {
            secondLease.close();
        } catch (RuntimeException expected) {
        }

        check(manager.pendingReleaseCount() == 2,
                "bounded retry: two debts retained");
        check(manager.retryPendingReleases(4, () -> false) == 0,
                "bounded retry: wall-clock guard prevents attempts");
        check(manager.retryPendingReleases(1, () -> true) == 1,
                "bounded retry: only one debt attempted");
        check(manager.pendingReleaseCount() == 1,
                "bounded retry: one debt remains for later tick");
        check(manager.retryPendingReleases(4, () -> true) == 1,
                "bounded retry: later tick clears remaining debt");
        check(manager.pendingReleaseCount() == 0,
                "bounded retry: no permanent cleanup debt");
    }

    private static void pendingReleaseReacquisitionReassertsPhysicalTicket() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager manager = new DungeonChunkLeaseManager(backend);
        ChunkPos pos = new ChunkPos(4, -3);
        DungeonChunkLease first = manager.acquire(pos);
        long token = manager.tokenFor(pos);
        backend.failReleaseAfterRemoval(pos, "removed then failed");
        try {
            first.close();
        } catch (RuntimeException expected) {
        }
        check(!backend.physicalTickets.contains(pos),
                "reassert: failed release may have removed physical ticket");

        DungeonChunkLease reclaimed = manager.acquire(pos);
        check(manager.tokenFor(pos) == token,
                "reassert: logical entry and lease token retained");
        check(backend.requests.size() == 2,
                "reassert: backend acquire invoked again");
        check(backend.physicalTickets.contains(pos),
                "reassert: physical ticket restored");
        check(manager.pendingReleaseCount() == 0,
                "reassert: cleanup debt converted to active ownership");
        check(manager.activeLeaseCount() == 1,
                "reassert: active count accurate");
        reclaimed.close();
        check(backend.releaseCalls.size() == 2,
                "reassert: close releases reasserted ticket exactly once");
        check(!backend.physicalTickets.contains(pos),
                "reassert: physical ticket released");
    }

    private static void reacquisitionFailurePreservesCleanupDebt() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager manager = new DungeonChunkLeaseManager(backend);
        ChunkPos pos = new ChunkPos(5, -3);
        DungeonChunkLease first = manager.acquire(pos);
        EntrySnapshot old = snapshot(manager, pos);
        backend.failRelease(pos, "release failed");
        try {
            first.close();
        } catch (RuntimeException expected) {
        }
        backend.nextAcquireFailure = new IllegalStateException("reassert failed");

        try {
            manager.acquire(pos);
            check(false, "reassert failure: should throw");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().contains("reassert failed"),
                    "reassert failure: original failure propagated");
        }

        assertUnchangedPending(manager, pos, old, "reassert failure");
        check(manager.retryPendingReleases(1, () -> true) == 1,
                "reassert failure: later cleanup retried");
        check(manager.pendingReleaseCount() == 0,
                "reassert failure: cleanup debt cleared");
    }

    private static void nullReacquisitionRequestPreservesCleanupDebt() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager manager = new DungeonChunkLeaseManager(backend);
        ChunkPos pos = new ChunkPos(51, -3);
        EntrySnapshot old = createPendingDebt(manager, backend, pos);
        backend.nextAcquireReturnsNull = true;

        assertRejectedReacquisition(manager, pos, "no request");
        assertUnchangedPending(manager, pos, old, "null request");
        check(manager.retryPendingReleases(1, () -> true) == 1,
                "null request: later release retried");
        check(backend.releaseCalls.size() == 2,
                "null request: retry calls backend release");
    }

    private static void nullReacquisitionCompletionPreservesCleanupDebt() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager manager = new DungeonChunkLeaseManager(backend);
        ChunkPos pos = new ChunkPos(52, -3);
        EntrySnapshot old = createPendingDebt(manager, backend, pos);
        backend.nextAcquireHasNullCompletion = true;

        assertRejectedReacquisition(manager, pos, "no completion");
        assertUnchangedPending(manager, pos, old, "null completion");
        check(manager.retryPendingReleases(1, () -> true) == 1,
                "null completion: later release retried");
        check(backend.releaseCalls.size() == 2,
                "null completion: retry calls backend release");
    }

    private static void missingReassertedTicketDoesNotCreateLease() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager manager = new DungeonChunkLeaseManager(backend);
        ChunkPos pos = new ChunkPos(6, -3);
        EntrySnapshot old = createPendingDebt(manager, backend, pos);
        backend.nextAcquireReportsTicketInstalled = false;

        assertRejectedReacquisition(manager, pos, "did not reinstall");
        assertUnchangedPending(manager, pos, old, "missing ticket");
        manager.retryPendingReleases(1, () -> true);
        check(manager.pendingReleaseCount() == 0,
                "missing ticket: owner tick retires empty debt");
    }

    private static void rejectedReacquisitionCompletionCannotMutateOldEntry() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager manager = new DungeonChunkLeaseManager(backend);
        ChunkPos pos = new ChunkPos(53, -3);
        EntrySnapshot old = createPendingDebt(manager, backend, pos);
        backend.nextAcquireReportsTicketInstalled = false;

        assertRejectedReacquisition(manager, pos, "did not reinstall");
        FakeRequest rejected = backend.request(1);
        rejected.completeExceptionally(new RuntimeException("rejected"));
        backend.executePending();
        assertUnchangedPending(manager, pos, old, "rejected completion");
        manager.retryPendingReleases(1, () -> true);
    }

    private static void rejectedReacquisitionStillClearsPhysically() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager manager = new DungeonChunkLeaseManager(backend);
        ChunkPos pos = new ChunkPos(54, -3);
        EntrySnapshot old = createPendingDebt(manager, backend, pos);
        backend.nextAcquireReportsTicketInstalled = false;

        assertRejectedReacquisition(manager, pos, "did not reinstall");
        assertUnchangedPending(manager, pos, old, "clear after rejection");
        manager.clear();
        check(backend.releaseCalls.size() == 2,
                "clear after rejection: old physical ticket release attempted");
    }

    private static void staleOriginalCompletionCannotOverwriteReassertion() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager manager = new DungeonChunkLeaseManager(backend);
        ChunkPos pos = new ChunkPos(7, -3);
        DungeonChunkLease first = manager.acquire(pos);
        FakeRequest original = backend.request(0);
        backend.failReleaseAfterRemoval(pos, "removed");
        try {
            first.close();
        } catch (RuntimeException expected) {
        }

        DungeonChunkLease reasserted = manager.acquire(pos);
        check(manager.requestGenerationFor(pos) == 2L,
                "stale completion: exactly one generation published");
        check(manager.futureFor(pos) == backend.request(1).completion,
                "stale completion: new future published");
        FakeRequest authoritative = backend.request(1);
        authoritative.completeSuccessfully();
        backend.executePending();
        check(reasserted.state() == DungeonChunkLeaseState.READY,
                "stale completion: reassertion becomes ready");

        original.completeExceptionally(new RuntimeException("stale failure"));
        backend.executePending();
        check(reasserted.state() == DungeonChunkLeaseState.READY,
                "stale completion: original cannot overwrite reassertion");
        reasserted.close();
        check(backend.releaseCalls.size() == 2,
                "stale completion: final close releases reasserted ticket once");
    }

    private static EntrySnapshot createPendingDebt(
            DungeonChunkLeaseManager manager,
            FakeBackend backend,
            ChunkPos pos
    ) {
        DungeonChunkLease lease = manager.acquire(pos);
        backend.request(0).completeSuccessfully();
        backend.executePending();
        backend.failRelease(pos, "release failed");
        try {
            lease.close();
        } catch (RuntimeException expected) {
        }
        return snapshot(manager, pos);
    }

    private static EntrySnapshot snapshot(
            DungeonChunkLeaseManager manager,
            ChunkPos pos
    ) {
        return new EntrySnapshot(
                manager.tokenFor(pos),
                manager.requestGenerationFor(pos),
                manager.futureFor(pos),
                manager.stateFor(pos, manager.tokenFor(pos)),
                manager.outcomeFor(pos, manager.tokenFor(pos)).orElse(null),
                manager.ticketInstalledFor(pos)
        );
    }

    private static void assertRejectedReacquisition(
            DungeonChunkLeaseManager manager,
            ChunkPos pos,
            String expectedMessage
    ) {
        try {
            manager.acquire(pos);
            check(false, expectedMessage + ": should reject logical lease");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().contains(expectedMessage),
                    expectedMessage + ": diagnostic");
        }
    }

    private static void assertUnchangedPending(
            DungeonChunkLeaseManager manager,
            ChunkPos pos,
            EntrySnapshot old,
            String label
    ) {
        EntrySnapshot current = snapshot(manager, pos);
        check(current.equals(old), label + ": original entry state preserved");
        check(manager.refCountFor(pos) == 0, label + ": ref count zero");
        check(manager.activeLeaseCount() == 0, label + ": active count zero");
        check(manager.pendingReleaseCount() == 1,
                label + ": pending cleanup retained");
    }

    private static void multipleReacquisitionsShareReassertedEntry() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager manager = new DungeonChunkLeaseManager(backend);
        ChunkPos pos = new ChunkPos(8, -3);
        DungeonChunkLease original = manager.acquire(pos);
        backend.failRelease(pos, "release failed");
        try {
            original.close();
        } catch (RuntimeException expected) {
        }

        DungeonChunkLease first = manager.acquire(pos);
        DungeonChunkLease second = manager.acquire(pos);
        check(backend.requests.size() == 2,
                "multiple reassert: only one physical reassertion");
        check(manager.refCountFor(pos) == 2,
                "multiple reassert: shared logical ref count");
        check(manager.activeLeaseCount() == 1,
                "multiple reassert: one active entry");
        first.close();
        check(backend.releaseCalls.size() == 1,
                "multiple reassert: first close retains ticket");
        second.close();
        check(backend.releaseCalls.size() == 2,
                "multiple reassert: final close releases once");
    }

    private static void doubleCloseIsHarmless() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager manager = new DungeonChunkLeaseManager(backend);
        DungeonChunkLease lease = manager.acquire(new ChunkPos(0, 0));

        lease.close();
        lease.close();

        check(backend.releaseCalls.size() == 1, "double: one release");
    }

    private static void offThreadCloseDoesNotConsumeHandle() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager manager = new DungeonChunkLeaseManager(backend);
        DungeonChunkLease lease = manager.acquire(new ChunkPos(0, 0));

        backend.ownerThread = false;
        try {
            lease.close();
            check(false, "off-thread: should throw");
        } catch (IllegalStateException expected) {
            check(backend.releaseCalls.isEmpty(), "off-thread: no release");
        }

        backend.ownerThread = true;
        lease.close();
        check(backend.releaseCalls.size() == 1,
                "off-thread: later close releases");
    }

    private static void replacementEntryReceivesNewToken() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager manager = new DungeonChunkLeaseManager(backend);
        ChunkPos pos = new ChunkPos(0, 0);

        DungeonChunkLease first = manager.acquire(pos);
        long firstToken = manager.tokenFor(pos);
        first.close();
        DungeonChunkLease second = manager.acquire(pos);
        long secondToken = manager.tokenFor(pos);

        check(secondToken != firstToken, "replace: new token");
        second.close();
    }

    private static void oldSuccessCallbackCannotAffectReplacement() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager manager = new DungeonChunkLeaseManager(backend);
        ChunkPos pos = new ChunkPos(0, 0);
        DungeonChunkLease first = manager.acquire(pos);
        FakeRequest firstRequest = backend.request(0);
        first.close();
        DungeonChunkLease second = manager.acquire(pos);
        FakeRequest secondRequest = backend.request(1);

        firstRequest.completeSuccessfully();
        backend.executePending();
        check(second.state() == DungeonChunkLeaseState.PENDING,
                "stale success: second pending");

        secondRequest.completeSuccessfully();
        backend.executePending();
        check(second.state() == DungeonChunkLeaseState.READY,
                "stale success: second ready");
        second.close();
    }

    private static void oldFailureCallbackCannotAffectReplacement() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager manager = new DungeonChunkLeaseManager(backend);
        ChunkPos pos = new ChunkPos(0, 0);
        DungeonChunkLease first = manager.acquire(pos);
        FakeRequest firstRequest = backend.request(0);
        first.close();
        DungeonChunkLease second = manager.acquire(pos);
        FakeRequest secondRequest = backend.request(1);

        firstRequest.completeExceptionally(new RuntimeException("old"));
        backend.executePending();
        check(second.state() == DungeonChunkLeaseState.PENDING,
                "stale failure: second pending");

        secondRequest.completeSuccessfully();
        backend.executePending();
        check(second.state() == DungeonChunkLeaseState.READY,
                "stale failure: second ready");
        second.close();
    }

    private static void oldHandleCannotReleaseReplacement() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager manager = new DungeonChunkLeaseManager(backend);
        ChunkPos pos = new ChunkPos(0, 0);
        DungeonChunkLease oldHandle = manager.acquire(pos);
        long firstToken = manager.tokenFor(pos);
        manager.release(pos, firstToken);
        DungeonChunkLease replacement = manager.acquire(pos);

        oldHandle.close();

        check(manager.hasActiveEntry(pos),
                "old release: replacement remains");
        check(manager.refCountFor(pos) == 1,
                "old release: ref count unchanged");
        check(backend.releaseCalls.size() == 1,
                "old release: no replacement release");
        replacement.close();
    }

    private static void oldHandleCannotObserveReplacementReadyState() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager manager = new DungeonChunkLeaseManager(backend);
        ChunkPos pos = new ChunkPos(0, 0);
        DungeonChunkLease oldHandle = manager.acquire(pos);
        long firstToken = manager.tokenFor(pos);
        manager.release(pos, firstToken);
        DungeonChunkLease replacement = manager.acquire(pos);

        backend.request(1).completeSuccessfully();
        backend.executePending();

        check(replacement.state() == DungeonChunkLeaseState.READY,
                "old observe: replacement ready");
        check(oldHandle.state() == DungeonChunkLeaseState.CANCELLED,
                "old observe: old cancelled");
        replacement.close();
    }

    private static void clearReleasesEveryTicketOnce() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager manager = new DungeonChunkLeaseManager(backend);
        manager.acquire(new ChunkPos(0, 0));
        manager.acquire(new ChunkPos(1, 0));

        manager.clear();

        check(backend.releaseCalls.size() == 2, "clear: two releases");
        check(manager.activeLeaseCount() == 0, "clear: no entries");
    }

    private static void terminalClearSucceedsOnLaterBoundedPass() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager manager = new DungeonChunkLeaseManager(backend);
        ChunkPos pos = new ChunkPos(0, 0);
        manager.acquire(pos);
        backend.failRelease(pos, "transient");

        DungeonChunkLeaseManager.TerminalCleanupResult result = manager.clear();

        check(result.passes() == 2, "terminal retry: two bounded passes");
        check(result.attempts() == 2, "terminal retry: two attempts");
        check(result.releaseFailureCount() == 1L,
                "terminal retry: transient failure observed");
        check(result.unresolvedCount() == 0,
                "terminal retry: no unresolved debt");
        check(manager.activeLeaseCount() == 0
                        && manager.pendingReleaseCount() == 0,
                "terminal retry: cleanup reaches zero");
    }

    private static void terminalClearExhaustsFixedPassLimitAndReportsDebt() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager manager = new DungeonChunkLeaseManager(backend);
        ChunkPos pos = new ChunkPos(5, 7);
        manager.acquire(pos);
        backend.failReleaseTimes(pos, 10, "persistent");

        try {
            manager.clearTerminal(3, 1);
            check(false, "terminal exhaustion: should aggregate failures");
        } catch (IllegalStateException exception) {
            check(exception.getSuppressed().length == 3,
                    "terminal exhaustion: every failed pass retained");
        }
        DungeonChunkLeaseManager.TerminalCleanupResult result =
                manager.lastTerminalCleanupResult();
        check(result.passes() == 3 && result.attempts() == 3,
                "terminal exhaustion: hard bounds honored");
        check(result.unresolvedCount() == 1,
                "terminal exhaustion: truthful unresolved count");
        check(result.unresolvedPositions().equals(List.of(pos)),
                "terminal exhaustion: bounded position evidence");
        check(manager.activeLeaseCount() == 0
                        && manager.pendingReleaseCount() == 1,
                "terminal exhaustion: logical and physical counts differ");
    }

    private static void terminalClearAttemptsPendingEntriesFairly() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager manager = new DungeonChunkLeaseManager(backend);
        List<ChunkPos> positions = List.of(
                new ChunkPos(0, 0), new ChunkPos(1, 0), new ChunkPos(2, 0)
        );
        for (ChunkPos pos : positions) {
            manager.acquire(pos);
            backend.failReleaseTimes(pos, 10, "persistent " + pos);
        }
        try {
            manager.clearTerminal(3, 1);
            check(false, "terminal fairness: unresolved cleanup should throw");
        } catch (IllegalStateException expected) {
            check(expected.getSuppressed().length == 3,
                    "terminal fairness: one retained failure per entry");
        }
        check(backend.releaseCalls.equals(positions),
                "terminal fairness: rotating passes reach every pending entry");
    }

    private static void terminalClearFailureDoesNotPreventIndependentRelease() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager manager = new DungeonChunkLeaseManager(backend);
        ChunkPos failing = new ChunkPos(0, 0);
        ChunkPos successful = new ChunkPos(1, 0);
        manager.acquire(failing);
        manager.acquire(successful);
        backend.failReleaseTimes(failing, 10, "persistent");
        try {
            manager.clearTerminal(1, 2);
            check(false, "terminal independent: unresolved cleanup should throw");
        } catch (IllegalStateException expected) {
            check(expected.getSuppressed().length == 1,
                    "terminal independent: failure retained");
        }
        check(backend.releaseCalls.equals(List.of(failing, successful)),
                "terminal independent: later release still attempted");
        check(manager.pendingReleaseCount() == 1,
                "terminal independent: only failing entry remains");
    }

    private static void terminalClearAggregatesRuntimeFailures() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager manager = new DungeonChunkLeaseManager(backend);
        ChunkPos first = new ChunkPos(0, 0);
        ChunkPos second = new ChunkPos(1, 0);
        manager.acquire(first);
        manager.acquire(second);
        backend.failReleaseTimes(first, 10, "first");
        backend.failReleaseTimes(second, 10, "second");

        try {
            manager.clearTerminal(1, 2);
            check(false, "terminal aggregate: should throw");
        } catch (IllegalStateException exception) {
            check(exception.getSuppressed().length == 2,
                    "terminal aggregate: both RuntimeExceptions suppressed");
        }
    }

    private static void clearAttemptsEveryTicketAfterReleaseError() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager manager = new DungeonChunkLeaseManager(backend);
        ChunkPos first = new ChunkPos(0, 0);
        ChunkPos second = new ChunkPos(1, 0);
        manager.acquire(first);
        manager.acquire(second);
        Error fatal = new AssertionError("fatal release");
        backend.releaseErrors.put(first, fatal);

        try {
            manager.clear();
            check(false, "clear Error: should escape after cleanup");
        } catch (Error observed) {
            check(observed == fatal, "clear Error: identity preserved");
        }
        check(backend.releaseCalls.size() == 3,
                "clear Error: every ticket attempted and fatal entry retried");
        check(manager.activeLeaseCount() == 0,
                "clear Error: logical entries cleared");
        check(manager.pendingReleaseCount() == 0,
                "clear Error: later bounded pass still cleans physical ticket");
    }

    private static void repeatedClearIsHarmless() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager manager = new DungeonChunkLeaseManager(backend);
        manager.acquire(new ChunkPos(0, 0));

        manager.clear();
        manager.clear();

        check(backend.releaseCalls.size() == 1,
                "clear twice: one release");
    }

    private static void lateCompletionAfterClearIsIgnored() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager manager = new DungeonChunkLeaseManager(backend);
        DungeonChunkLease lease = manager.acquire(new ChunkPos(0, 0));
        FakeRequest request = backend.request(0);

        manager.clear();
        request.completeSuccessfully();
        backend.executePending();

        check(lease.state() == DungeonChunkLeaseState.CANCELLED,
                "late clear: cancelled");
        check(manager.activeLeaseCount() == 0, "late clear: no entry");
    }

    private static void acquisitionThrowLeavesNoEntryOrTicket() {
        FakeBackend backend = new FakeBackend();
        backend.throwAfterInstallingTicket = true;
        DungeonChunkLeaseManager manager = new DungeonChunkLeaseManager(backend);

        try {
            manager.acquire(new ChunkPos(0, 0));
            check(false, "throw: should throw");
        } catch (IllegalStateException expected) {
            check(manager.activeLeaseCount() == 0, "throw: no entry");
            check(backend.releaseCalls.size() == 1,
                    "throw: compensating release");
        }
    }

    private static void backendFutureIsNeverCancelled() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager manager = new DungeonChunkLeaseManager(backend);
        DungeonChunkLease lease = manager.acquire(new ChunkPos(0, 0));
        FakeRequest request = backend.request(0);

        lease.close();
        manager.clear();

        check(!request.completion.isCancelled(),
                "future: not cancelled by release or clear");
    }

    private static void differentChunksAreIndependent() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager manager = new DungeonChunkLeaseManager(backend);
        ChunkPos first = new ChunkPos(0, 0);
        ChunkPos second = new ChunkPos(1, 0);
        DungeonChunkLease firstLease = manager.acquire(first);
        DungeonChunkLease secondLease = manager.acquire(second);

        check(backend.requests.size() == 2, "independent: two requests");
        firstLease.close();
        check(!manager.hasActiveEntry(first), "independent: first gone");
        check(manager.hasActiveEntry(second), "independent: second remains");
        secondLease.close();
    }

    private static final class FakeBackend implements DungeonChunkTicketBackend {
        final List<FakeRequest> requests = new ArrayList<>();
        final List<ChunkPos> releaseCalls = new ArrayList<>();
        final List<Runnable> pending = new ArrayList<>();
        final Map<ChunkPos, RuntimeException> releaseFailures = new HashMap<>();
        final Map<ChunkPos, Integer> persistentReleaseFailures = new HashMap<>();
        final Map<ChunkPos, String> persistentReleaseMessages = new HashMap<>();
        final Map<ChunkPos, Error> releaseErrors = new HashMap<>();
        final Set<ChunkPos> removeBeforeFailure = new HashSet<>();
        final Set<ChunkPos> physicalTickets = new HashSet<>();
        boolean ownerThread = true;
        boolean throwAfterInstallingTicket;
        RuntimeException nextAcquireFailure;
        boolean nextAcquireReturnsNull;
        boolean nextAcquireHasNullCompletion;
        boolean nextAcquireReportsTicketInstalled = true;

        @Override
        public DungeonChunkLoadRequest acquire(ChunkPos pos) {
            if (this.nextAcquireReturnsNull) {
                this.nextAcquireReturnsNull = false;
                return null;
            }
            if (this.nextAcquireFailure != null) {
                RuntimeException failure = this.nextAcquireFailure;
                this.nextAcquireFailure = null;
                throw failure;
            }
            if (this.throwAfterInstallingTicket) {
                this.physicalTickets.add(pos);
                release(pos);
                throw new IllegalStateException("synthetic acquire failure");
            }
            if (this.nextAcquireHasNullCompletion) {
                this.nextAcquireHasNullCompletion = false;
                return new DungeonChunkLoadRequest(null, true);
            }
            FakeRequest request = new FakeRequest(pos);
            this.requests.add(request);
            boolean installed = this.nextAcquireReportsTicketInstalled;
            this.nextAcquireReportsTicketInstalled = true;
            if (installed) {
                this.physicalTickets.add(pos);
            }
            return new DungeonChunkLoadRequest(request.completion, installed);
        }

        @Override
        public void release(ChunkPos pos) {
            this.releaseCalls.add(pos);
            Error error = this.releaseErrors.remove(pos);
            if (error != null) {
                throw error;
            }
            Integer remainingFailures = this.persistentReleaseFailures.get(pos);
            if (remainingFailures != null && remainingFailures > 0) {
                if (remainingFailures == 1) {
                    this.persistentReleaseFailures.remove(pos);
                    this.persistentReleaseMessages.remove(pos);
                } else {
                    this.persistentReleaseFailures.put(pos, remainingFailures - 1);
                }
                throw new IllegalStateException(
                        this.persistentReleaseMessages.getOrDefault(
                                pos, "persistent release failure"
                        )
                );
            }
            RuntimeException failure = this.releaseFailures.remove(pos);
            if (failure != null) {
                if (this.removeBeforeFailure.remove(pos)) {
                    this.physicalTickets.remove(pos);
                }
                throw failure;
            }
            this.physicalTickets.remove(pos);
        }

        @Override
        public boolean isOwnerThread() {
            return this.ownerThread;
        }

        @Override
        public void executeOnOwnerThread(Runnable task) {
            this.pending.add(task);
        }

        FakeRequest request(int index) {
            return this.requests.get(index);
        }

        void failRelease(ChunkPos pos, String message) {
            this.releaseFailures.put(pos, new IllegalStateException(message));
        }

        void failReleaseTimes(ChunkPos pos, int attempts, String message) {
            this.persistentReleaseFailures.put(pos, attempts);
            this.persistentReleaseMessages.put(pos, message);
        }

        void failReleaseAfterRemoval(ChunkPos pos, String message) {
            failRelease(pos, message);
            this.removeBeforeFailure.add(pos);
        }

        void executePending() {
            List<Runnable> tasks = new ArrayList<>(this.pending);
            this.pending.clear();
            for (Runnable task : tasks) {
                task.run();
            }
        }
    }

    private record EntrySnapshot(
            long token,
            long generation,
            CompletableFuture<DungeonChunkLoadOutcome> future,
            DungeonChunkLeaseState state,
            DungeonChunkLoadOutcome outcome,
            boolean ticketInstalled
    ) {
    }

    private static final class FakeRequest {
        final ChunkPos chunkPos;
        final CompletableFuture<DungeonChunkLoadOutcome> completion =
                new CompletableFuture<>();

        FakeRequest(ChunkPos chunkPos) {
            this.chunkPos = chunkPos;
        }

        void completeSuccessfully() {
            this.completion.complete(DungeonChunkLoadOutcome.Success.INSTANCE);
        }

        void completeWithNull() {
            this.completion.complete(null);
        }

        void completeFutureExceptionally(Throwable throwable) {
            this.completion.completeExceptionally(throwable);
        }

        void completeExceptionally(Throwable throwable) {
            this.completion.complete(
                    new DungeonChunkLoadOutcome.ExceptionalCompletion(
                            throwable,
                            throwable == null ? "" : throwable.getMessage()
                    )
            );
        }
    }

    private static void check(boolean condition, String message) {
        DungeonAsyncTestSupport.check(condition, message);
    }
}
