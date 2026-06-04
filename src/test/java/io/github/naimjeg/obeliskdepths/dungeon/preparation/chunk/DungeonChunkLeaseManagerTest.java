package io.github.naimjeg.obeliskdepths.dungeon.preparation.chunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.ChunkPos;

public final class DungeonChunkLeaseManagerTest {
    private DungeonChunkLeaseManagerTest() {}

    static {
        try {
            net.minecraft.server.Bootstrap.bootStrap();
        } catch (Throwable ignored) {
        }
    }

    public static void main(String[] args) {
        initialStateIsPending();
        successBecomesReady();
        exceptionBecomesFailed();
        duplicateAcquisitionSharesOneBackendRequest();
        oneOfTwoCloseRetainsTicket();
        finalCloseReleasesTicketOnce();
        finalCloseBackendExceptionRemovesEntryAndAllowsReplacement();
        doubleCloseIsHarmless();
        offThreadCloseDoesNotConsumeHandle();
        replacementEntryReceivesNewToken();
        oldSuccessCallbackCannotAffectReplacement();
        oldFailureCallbackCannotAffectReplacement();
        oldHandleCannotReleaseReplacement();
        oldHandleCannotObserveReplacementReadyState();
        clearReleasesEveryTicketOnce();
        clearAttemptsEveryTicketAfterReleaseFailure();
        clearAggregatesMultipleReleaseFailures();
        repeatedClearIsHarmless();
        lateCompletionAfterClearIsIgnored();
        acquisitionThrowLeavesNoEntryOrTicket();
        backendFutureIsNeverCancelled();
        differentChunksAreIndependent();
    }

    private static void initialStateIsPending() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager mgr = new DungeonChunkLeaseManager(backend);
        ChunkPos pos = new ChunkPos(0, 0);
        DungeonChunkLease lease = mgr.acquire(pos);
        check(lease.state() == DungeonChunkLeaseState.PENDING, "initial: PENDING");
        check(mgr.refCountFor(pos) == 1, "initial: ref count");
        lease.close();
    }

    private static void successBecomesReady() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager mgr = new DungeonChunkLeaseManager(backend);
        ChunkPos pos = new ChunkPos(0, 0);
        DungeonChunkLease lease = mgr.acquire(pos);
        backend.request(0).completeSuccessfully();
        check(lease.state() == DungeonChunkLeaseState.PENDING, "success: deferred");
        backend.executePending();
        check(lease.state() == DungeonChunkLeaseState.READY, "success: READY");
        lease.close();
    }

    private static void exceptionBecomesFailed() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager mgr = new DungeonChunkLeaseManager(backend);
        ChunkPos pos = new ChunkPos(0, 0);
        DungeonChunkLease lease = mgr.acquire(pos);
        backend.request(0).completeExceptionally(new RuntimeException("fail"));
        backend.executePending();
        check(lease.state() == DungeonChunkLeaseState.FAILED, "exception: FAILED");
        lease.close();
    }

    private static void duplicateAcquisitionSharesOneBackendRequest() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager mgr = new DungeonChunkLeaseManager(backend);
        ChunkPos pos = new ChunkPos(0, 0);
        DungeonChunkLease a = mgr.acquire(pos);
        DungeonChunkLease b = mgr.acquire(pos);
        check(backend.requests.size() == 1, "shared: one backend request");
        check(mgr.refCountFor(pos) == 2, "shared: ref count");
        a.close();
        b.close();
        check(backend.releaseCalls.size() == 1, "shared: one release");
    }

    private static void oneOfTwoCloseRetainsTicket() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager mgr = new DungeonChunkLeaseManager(backend);
        ChunkPos pos = new ChunkPos(0, 0);
        DungeonChunkLease a = mgr.acquire(pos);
        DungeonChunkLease b = mgr.acquire(pos);
        a.close();
        check(backend.releaseCalls.isEmpty(), "retain: no release");
        check(mgr.refCountFor(pos) == 1, "retain: one owner");
        check(mgr.hasActiveEntry(pos), "retain: entry active");
        b.close();
    }

    private static void finalCloseReleasesTicketOnce() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager mgr = new DungeonChunkLeaseManager(backend);
        ChunkPos pos = new ChunkPos(0, 0);
        DungeonChunkLease lease = mgr.acquire(pos);
        lease.close();
        check(backend.releaseCalls.size() == 1, "final: one release");
        check(backend.releaseCalls.get(0).equals(pos), "final: correct chunk");
        check(!mgr.hasActiveEntry(pos), "final: removed");
    }

    private static void finalCloseBackendExceptionRemovesEntryAndAllowsReplacement() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager mgr = new DungeonChunkLeaseManager(backend);
        ChunkPos pos = new ChunkPos(0, 0);
        DungeonChunkLease lease = mgr.acquire(pos);
        FakeRequest oldRequest = backend.request(0);
        long oldToken = mgr.tokenFor(pos);
        backend.failRelease(pos, "release");
        try {
            lease.close();
            check(false, "final failure: should throw");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().contains("release"), "final failure: propagated");
        }
        check(!mgr.hasActiveEntry(pos), "final failure: entry removed");
        check(mgr.activeLeaseCount() == 0, "final failure: active zero");
        lease.close();
        check(backend.releaseCalls.size() == 1, "final failure: no second release");

        DungeonChunkLease replacement = mgr.acquire(pos);
        long replacementToken = mgr.tokenFor(pos);
        check(replacementToken != oldToken, "final failure: replacement token");
        oldRequest.completeSuccessfully();
        backend.executePending();
        check(replacement.state() == DungeonChunkLeaseState.PENDING,
                "final failure: old completion ignored");
        replacement.close();
    }

    private static void doubleCloseIsHarmless() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager mgr = new DungeonChunkLeaseManager(backend);
        DungeonChunkLease lease = mgr.acquire(new ChunkPos(0, 0));
        lease.close();
        lease.close();
        check(backend.releaseCalls.size() == 1, "double: one release");
    }

    private static void offThreadCloseDoesNotConsumeHandle() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager mgr = new DungeonChunkLeaseManager(backend);
        DungeonChunkLease lease = mgr.acquire(new ChunkPos(0, 0));
        backend.ownerThread = false;
        try {
            lease.close();
            check(false, "off-thread: should throw");
        } catch (IllegalStateException expected) {
            check(backend.releaseCalls.isEmpty(), "off-thread: no release");
        }
        backend.ownerThread = true;
        lease.close();
        check(backend.releaseCalls.size() == 1, "off-thread: later close releases");
    }

    private static void replacementEntryReceivesNewToken() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager mgr = new DungeonChunkLeaseManager(backend);
        ChunkPos pos = new ChunkPos(0, 0);
        DungeonChunkLease a = mgr.acquire(pos);
        long tokenA = mgr.tokenFor(pos);
        a.close();
        DungeonChunkLease b = mgr.acquire(pos);
        long tokenB = mgr.tokenFor(pos);
        check(tokenB != tokenA, "replace: new token");
        b.close();
    }

    private static void oldSuccessCallbackCannotAffectReplacement() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager mgr = new DungeonChunkLeaseManager(backend);
        ChunkPos pos = new ChunkPos(0, 0);
        DungeonChunkLease a = mgr.acquire(pos);
        FakeRequest requestA = backend.request(0);
        a.close();
        DungeonChunkLease b = mgr.acquire(pos);
        FakeRequest requestB = backend.request(1);
        requestA.completeSuccessfully();
        backend.executePending();
        check(b.state() == DungeonChunkLeaseState.PENDING, "stale success: B pending");
        requestB.completeSuccessfully();
        backend.executePending();
        check(b.state() == DungeonChunkLeaseState.READY, "stale success: B ready");
        b.close();
    }

    private static void oldFailureCallbackCannotAffectReplacement() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager mgr = new DungeonChunkLeaseManager(backend);
        ChunkPos pos = new ChunkPos(0, 0);
        DungeonChunkLease a = mgr.acquire(pos);
        FakeRequest requestA = backend.request(0);
        a.close();
        DungeonChunkLease b = mgr.acquire(pos);
        FakeRequest requestB = backend.request(1);
        requestA.completeExceptionally(new RuntimeException("old"));
        backend.executePending();
        check(b.state() == DungeonChunkLeaseState.PENDING, "stale failure: B pending");
        requestB.completeSuccessfully();
        backend.executePending();
        check(b.state() == DungeonChunkLeaseState.READY, "stale failure: B ready");
        b.close();
    }

    private static void oldHandleCannotReleaseReplacement() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager mgr = new DungeonChunkLeaseManager(backend);
        ChunkPos pos = new ChunkPos(0, 0);
        DungeonChunkLease oldHandle = mgr.acquire(pos);
        long tokenA = mgr.tokenFor(pos);
        mgr.release(pos, tokenA);
        DungeonChunkLease replacement = mgr.acquire(pos);
        oldHandle.close();
        check(mgr.hasActiveEntry(pos), "old release: replacement remains");
        check(mgr.refCountFor(pos) == 1, "old release: ref count unchanged");
        check(backend.releaseCalls.size() == 1, "old release: no replacement release");
        replacement.close();
    }

    private static void oldHandleCannotObserveReplacementReadyState() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager mgr = new DungeonChunkLeaseManager(backend);
        ChunkPos pos = new ChunkPos(0, 0);
        DungeonChunkLease oldHandle = mgr.acquire(pos);
        long tokenA = mgr.tokenFor(pos);
        mgr.release(pos, tokenA);
        DungeonChunkLease replacement = mgr.acquire(pos);
        backend.request(1).completeSuccessfully();
        backend.executePending();
        check(replacement.state() == DungeonChunkLeaseState.READY, "old observe: replacement ready");
        check(oldHandle.state() == DungeonChunkLeaseState.CANCELLED, "old observe: old cancelled");
        replacement.close();
    }

    private static void clearReleasesEveryTicketOnce() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager mgr = new DungeonChunkLeaseManager(backend);
        mgr.acquire(new ChunkPos(0, 0));
        mgr.acquire(new ChunkPos(1, 0));
        mgr.clear();
        check(backend.releaseCalls.size() == 2, "clear: two releases");
        check(mgr.activeLeaseCount() == 0, "clear: no entries");
    }

    private static void clearAttemptsEveryTicketAfterReleaseFailure() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager mgr = new DungeonChunkLeaseManager(backend);
        ChunkPos first = new ChunkPos(0, 0);
        ChunkPos second = new ChunkPos(1, 0);
        DungeonChunkLease firstLease = mgr.acquire(first);
        DungeonChunkLease secondLease = mgr.acquire(second);
        FakeRequest firstRequest = backend.request(0);
        backend.failRelease(first, "first");
        try {
            mgr.clear();
            check(false, "clear failure: should throw");
        } catch (IllegalStateException exception) {
            check(exception.getSuppressed().length == 1,
                    "clear failure: suppressed");
        }
        check(backend.releaseCalls.size() == 2, "clear failure: both attempted");
        check(mgr.activeLeaseCount() == 0, "clear failure: no entries");
        check(firstLease.state() == DungeonChunkLeaseState.CANCELLED,
                "clear failure: first cancelled");
        check(secondLease.state() == DungeonChunkLeaseState.CANCELLED,
                "clear failure: second cancelled");
        try {
            mgr.acquire(new ChunkPos(2, 0));
            check(false, "clear failure: acquire rejected");
        } catch (IllegalStateException expected) {
            // expected
        }
        firstRequest.completeSuccessfully();
        backend.executePending();
        check(mgr.activeLeaseCount() == 0, "clear failure: late completion ignored");
        mgr.clear();
        check(backend.releaseCalls.size() == 2, "clear failure: retry no re-release");
    }

    private static void clearAggregatesMultipleReleaseFailures() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager mgr = new DungeonChunkLeaseManager(backend);
        ChunkPos first = new ChunkPos(0, 0);
        ChunkPos second = new ChunkPos(1, 0);
        mgr.acquire(first);
        mgr.acquire(second);
        backend.failRelease(first, "first");
        backend.failRelease(second, "second");
        try {
            mgr.clear();
            check(false, "clear multi failure: should throw");
        } catch (IllegalStateException exception) {
            check(exception.getSuppressed().length == 2,
                    "clear multi failure: suppressed");
        }
        check(backend.releaseCalls.size() == 2, "clear multi failure: both attempted");
        check(mgr.activeLeaseCount() == 0, "clear multi failure: no entries");
    }

    private static void repeatedClearIsHarmless() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager mgr = new DungeonChunkLeaseManager(backend);
        mgr.acquire(new ChunkPos(0, 0));
        mgr.clear();
        mgr.clear();
        check(backend.releaseCalls.size() == 1, "clear twice: one release");
    }

    private static void lateCompletionAfterClearIsIgnored() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager mgr = new DungeonChunkLeaseManager(backend);
        DungeonChunkLease lease = mgr.acquire(new ChunkPos(0, 0));
        FakeRequest request = backend.request(0);
        mgr.clear();
        request.completeSuccessfully();
        backend.executePending();
        check(lease.state() == DungeonChunkLeaseState.CANCELLED, "late clear: cancelled");
        check(mgr.activeLeaseCount() == 0, "late clear: no entry");
    }

    private static void acquisitionThrowLeavesNoEntryOrTicket() {
        FakeBackend backend = new FakeBackend();
        backend.throwAfterInstallingTicket = true;
        DungeonChunkLeaseManager mgr = new DungeonChunkLeaseManager(backend);
        try {
            mgr.acquire(new ChunkPos(0, 0));
            check(false, "throw: should throw");
        } catch (IllegalStateException expected) {
            check(mgr.activeLeaseCount() == 0, "throw: no entry");
            check(backend.releaseCalls.size() == 1, "throw: compensating release");
        }
    }

    private static void backendFutureIsNeverCancelled() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager mgr = new DungeonChunkLeaseManager(backend);
        DungeonChunkLease lease = mgr.acquire(new ChunkPos(0, 0));
        FakeRequest request = backend.request(0);
        lease.close();
        check(!request.completion.isCancelled(), "future: not cancelled on release");
        mgr.clear();
        check(!request.completion.isCancelled(), "future: not cancelled on clear");
    }

    private static void differentChunksAreIndependent() {
        FakeBackend backend = new FakeBackend();
        DungeonChunkLeaseManager mgr = new DungeonChunkLeaseManager(backend);
        ChunkPos a = new ChunkPos(0, 0);
        ChunkPos b = new ChunkPos(1, 0);
        DungeonChunkLease la = mgr.acquire(a);
        DungeonChunkLease lb = mgr.acquire(b);
        check(backend.requests.size() == 2, "independent: two requests");
        la.close();
        check(!mgr.hasActiveEntry(a), "independent: A gone");
        check(mgr.hasActiveEntry(b), "independent: B remains");
        lb.close();
    }

    private static final class FakeBackend implements DungeonChunkTicketBackend {
        final List<FakeRequest> requests = new ArrayList<>();
        final List<ChunkPos> releaseCalls = new ArrayList<>();
        final List<Runnable> pending = new ArrayList<>();
        final Map<ChunkPos, RuntimeException> releaseFailures = new HashMap<>();
        boolean ownerThread = true;
        boolean throwAfterInstallingTicket;

        @Override
        public DungeonChunkLoadRequest acquire(ChunkPos pos) {
            if (this.throwAfterInstallingTicket) {
                this.release(pos);
                throw new IllegalStateException("synthetic acquire failure");
            }
            FakeRequest request = new FakeRequest(pos);
            this.requests.add(request);
            return new DungeonChunkLoadRequest(request.completion, true);
        }

        @Override
        public void release(ChunkPos pos) {
            this.releaseCalls.add(pos);
            RuntimeException failure = this.releaseFailures.remove(pos);
            if (failure != null) {
                throw failure;
            }
        }

        @Override
        public boolean isOwnerThread() {
            return this.ownerThread;
        }

        @Override
        public void executeOnOwnerThread(Runnable r) {
            this.pending.add(r);
        }

        FakeRequest request(int index) {
            return this.requests.get(index);
        }

        void failRelease(ChunkPos pos, String message) {
            this.releaseFailures.put(pos, new IllegalStateException(message));
        }

        void executePending() {
            List<Runnable> tasks = new ArrayList<>(this.pending);
            this.pending.clear();
            for (Runnable task : tasks) {
                task.run();
            }
        }
    }

    private static final class FakeRequest {
        final ChunkPos chunkPos;
        final java.util.concurrent.CompletableFuture<Object> completion =
                new java.util.concurrent.CompletableFuture<>();

        FakeRequest(ChunkPos chunkPos) {
            this.chunkPos = chunkPos;
        }

        void completeSuccessfully() {
            this.completion.complete(new Object());
        }

        void completeExceptionally(Throwable throwable) {
            this.completion.completeExceptionally(throwable);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
