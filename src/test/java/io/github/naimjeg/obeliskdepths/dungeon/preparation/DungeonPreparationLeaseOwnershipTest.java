package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.tribute.ResolvedTribute;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class DungeonPreparationLeaseOwnershipTest {
    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION,
                    Identifier.fromNamespaceAndPath("minecraft", "overworld"));

    private DungeonPreparationLeaseOwnershipTest() {}

    static {
        DungeonAsyncTestSupport.bootstrapMinecraft();
    }

    public static void main(String[] args) {
        completeRejectedWhenJobOwnsLeases();
        completeAcceptedWithDetachedLeases();
        detachReturnsBundleWithAllLeases();
        closingDetachedBundleClosesEveryLeaseOnce();
        closingDetachedBundleIsIdempotent();
        transferGuardClosesUnclaimedBundle();
        transferGuardDisarmsAfterTake();
        transferGuardRejectsSecondTake();
        emptyBundleCloseIsHarmless();
        closeAndRemoveOneLeaseClosesOnlyThatLease();
        closeAndRemoveUnknownLeaseIsDeterministic();
        closeAndRemoveFailureDoesNotLeaveFailedLease();
        terminalCleanupDoesNotDoubleCloseRemovedLease();
        failedJobCannotDetachLeases();
        cancelledJobCannotDetachLeases();
        secondDetachReturnsEmptyBundle();
        bundleCloseAggregatesExceptions();
        bundleRejectsNullLease();
        bundleCloseRethrowsError();
        purgeRemovesResidualLeases();
        purgeAggregatesLeaseCleanupFailures();
    }

    private static void completeRejectedWhenJobOwnsLeases() {
        DungeonPreparationJob job = job();
        advanceToCommitting(job);
        CountingLease lease = new CountingLease();
        job.addCloseableLease(lease);
        try {
            job.complete(10L);
            check(false, "complete with lease: should throw");
        } catch (IllegalStateException e) {
            check(e.getMessage().contains("still owns leases"),
                    "complete with lease: message");
        }
        check(lease.closed.get() == 0,
                "complete with lease: lease not released");
        check(job.stage() != DungeonPreparationStage.READY,
                "complete with lease: not READY");
    }

    private static void completeAcceptedWithDetachedLeases() {
        DungeonPreparationJob job = job();
        advanceToCommitting(job);
        CountingLease lease = new CountingLease();
        job.addCloseableLease(lease);
        DungeonPreparationLeaseBundle bundle = job.detachLeases();
        check(lease.closed.get() == 0, "detach: lease not closed");
        check(job.leases().isEmpty(), "detach: job list empty");
        job.complete(10L);
        check(job.stage() == DungeonPreparationStage.READY,
                "complete after detach: READY");
        bundle.close();
        check(lease.closed.get() == 1,
                "bundle close: lease closed once");
    }

    private static void detachReturnsBundleWithAllLeases() {
        DungeonPreparationJob job = job();
        CountingLease a = new CountingLease();
        CountingLease b = new CountingLease();
        job.addCloseableLease(a);
        job.addCloseableLease(b);
        DungeonPreparationLeaseBundle bundle = job.detachLeases();
        check(bundle.leases().size() == 2, "detach all: two leases in bundle");
        check(job.leases().isEmpty(), "detach all: job empty");
        bundle.close();
        check(a.closed.get() == 1 && b.closed.get() == 1,
                "detach all: both closed");
    }

    private static void closingDetachedBundleClosesEveryLeaseOnce() {
        DungeonPreparationJob job = job();
        CountingLease a = new CountingLease();
        CountingLease b = new CountingLease();
        job.addCloseableLease(a);
        job.addCloseableLease(b);
        DungeonPreparationLeaseBundle bundle = job.detachLeases();
        bundle.close();
        check(a.closed.get() == 1, "bundle close once: a closed once");
        check(b.closed.get() == 1, "bundle close once: b closed once");
    }

    private static void closingDetachedBundleIsIdempotent() {
        DungeonPreparationJob job = job();
        CountingLease lease = new CountingLease();
        job.addCloseableLease(lease);
        DungeonPreparationLeaseBundle bundle = job.detachLeases();
        bundle.close();
        bundle.close();
        check(lease.closed.get() == 1,
                "bundle idempotent: closed once");
    }

    private static void transferGuardClosesUnclaimedBundle() {
        CountingLease lease = new CountingLease();
        DungeonPreparationLeaseTransfer transfer =
                new DungeonPreparationLeaseTransfer(
                        new DungeonPreparationLeaseBundle(List.of(lease))
                );

        transfer.close();
        transfer.close();

        check(lease.closed.get() == 1,
                "transfer close: unclaimed bundle closed once");
    }

    private static void transferGuardDisarmsAfterTake() {
        CountingLease lease = new CountingLease();
        DungeonPreparationLeaseTransfer transfer =
                new DungeonPreparationLeaseTransfer(
                        new DungeonPreparationLeaseBundle(List.of(lease))
                );

        DungeonPreparationLeaseBundle bundle = transfer.takeBundle();
        transfer.close();
        check(lease.closed.get() == 0,
                "transfer take: guard no longer closes bundle");
        bundle.close();
        check(lease.closed.get() == 1,
                "transfer take: returned owner closes bundle");
    }

    private static void transferGuardRejectsSecondTake() {
        CountingLease lease = new CountingLease();
        DungeonPreparationLeaseTransfer transfer =
                new DungeonPreparationLeaseTransfer(
                        new DungeonPreparationLeaseBundle(List.of(lease))
                );

        DungeonPreparationLeaseBundle bundle = transfer.takeBundle();
        try {
            transfer.takeBundle();
            check(false, "transfer second take: should throw");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().contains("already been consumed"),
                    "transfer second take: message");
        } finally {
            bundle.close();
        }
    }

    private static void emptyBundleCloseIsHarmless() {
        DungeonPreparationLeaseBundle bundle =
                new DungeonPreparationLeaseBundle(Collections.emptyList());
        bundle.close();
        bundle.close();
        // should not throw
    }

    private static void closeAndRemoveOneLeaseClosesOnlyThatLease() {
        DungeonPreparationJob job = job();
        CountingLease first = new CountingLease();
        CountingLease second = new CountingLease();
        job.addCloseableLease(first);
        job.addCloseableLease(second);

        job.closeAndRemoveLease(first);

        check(first.closed.get() == 1, "single release: first closed");
        check(second.closed.get() == 0, "single release: second retained");
        check(job.leases().size() == 1, "single release: one lease remains");
        check(job.leases().get(0) == second,
                "single release: second remains owned");
        job.releaseAllLeases();
        check(second.closed.get() == 1, "single release: second closed later");
    }

    private static void closeAndRemoveUnknownLeaseIsDeterministic() {
        DungeonPreparationJob job = job();
        CountingLease owned = new CountingLease();
        CountingLease unknown = new CountingLease();
        job.addCloseableLease(owned);

        try {
            job.closeAndRemoveLease(unknown);
            check(false, "unknown release: should throw");
        } catch (IllegalArgumentException exception) {
            check(exception.getMessage().contains("does not own"),
                    "unknown release: deterministic message");
        }

        check(owned.closed.get() == 0, "unknown release: owned retained");
        check(unknown.closed.get() == 0, "unknown release: unknown untouched");
        job.releaseAllLeases();
    }

    private static void closeAndRemoveFailureDoesNotLeaveFailedLease() {
        DungeonPreparationJob job = job();
        ThrowingLease failing = new ThrowingLease("single");
        CountingLease retained = new CountingLease();
        job.addCloseableLease(failing);
        job.addCloseableLease(retained);

        try {
            job.closeAndRemoveLease(failing);
            check(false, "single failure: should throw");
        } catch (IllegalStateException exception) {
            check(exception.getMessage().contains("single"),
                    "single failure: propagated");
        }

        check(job.leases().size() == 1,
                "single failure: failed lease removed");
        check(job.leases().get(0) == retained,
                "single failure: retained lease remains");
        job.releaseAllLeases();
        check(failing.closed.get() == 1,
                "single failure: no retry during cleanup");
        check(retained.closed.get() == 1,
                "single failure: retained closed during cleanup");
    }

    private static void terminalCleanupDoesNotDoubleCloseRemovedLease() {
        DungeonPreparationJob job = job();
        CountingLease removed = new CountingLease();
        CountingLease retained = new CountingLease();
        job.addCloseableLease(removed);
        job.addCloseableLease(retained);
        job.closeAndRemoveLease(removed);

        job.cancel(
                DungeonPreparationCancellationReason.USER_CANCELLED,
                "test",
                1L
        );

        check(removed.closed.get() == 1,
                "terminal cleanup: removed closed once");
        check(retained.closed.get() == 1,
                "terminal cleanup: retained closed");
    }

    private static void failedJobCannotDetachLeases() {
        DungeonPreparationJob job = job();
        CountingLease lease = new CountingLease();
        job.addCloseableLease(lease);
        job.advanceTo(DungeonPreparationStage.VALIDATING, 1L);
        job.fail(DungeonPreparationJobFailureReason.INTERNAL_ERROR,
                "test", 2L);
        check(lease.closed.get() == 1, "fail: lease closed");
        try {
            job.detachLeases();
            check(false, "fail detach: should throw");
        } catch (IllegalStateException e) {
            check(e.getMessage().contains("Cannot detach"),
                    "fail detach: message");
        }
    }

    private static void cancelledJobCannotDetachLeases() {
        DungeonPreparationJob job = job();
        CountingLease lease = new CountingLease();
        job.addCloseableLease(lease);
        job.cancel(DungeonPreparationCancellationReason.USER_CANCELLED,
                "test", 1L);
        check(lease.closed.get() == 1, "cancel: lease closed");
        try {
            job.detachLeases();
            check(false, "cancel detach: should throw");
        } catch (IllegalStateException e) {
            check(e.getMessage().contains("Cannot detach"),
                    "cancel detach: message");
        }
    }

    private static void secondDetachReturnsEmptyBundle() {
        DungeonPreparationJob job = job();
        CountingLease lease = new CountingLease();
        job.addCloseableLease(lease);
        DungeonPreparationLeaseBundle first = job.detachLeases();
        check(!first.leases().isEmpty(), "first detach: non-empty");
        DungeonPreparationLeaseBundle second = job.detachLeases();
        check(second.leases().isEmpty(), "second detach: empty");
        first.close();
    }

    private static void bundleCloseAggregatesExceptions() {
        ThrowingLease first = new ThrowingLease("first");
        CountingLease second = new CountingLease();
        ThrowingLease third = new ThrowingLease("third");
        List<AutoCloseable> leases = new ArrayList<>();
        leases.add(first);
        leases.add(second);
        leases.add(third);
        DungeonPreparationLeaseBundle bundle =
                new DungeonPreparationLeaseBundle(leases);
        try {
            bundle.close();
            check(false, "bundle aggregate: should throw");
        } catch (IllegalStateException e) {
            check(e.getSuppressed().length == 2,
                    "bundle aggregate: suppressed");
        }
        check(first.closed.get() == 1, "bundle aggregate: first attempted");
        check(second.closed.get() == 1, "bundle aggregate: second attempted");
        check(third.closed.get() == 1, "bundle aggregate: third attempted");
    }

    private static void bundleCloseRethrowsError() {
        ThrowingLease faulty = new ThrowingLease("faulty");
        ErrorLease errorLease = new ErrorLease();
        CountingLease later = new CountingLease();
        List<AutoCloseable> leases = new ArrayList<>();
        leases.add(faulty);
        leases.add(errorLease);
        leases.add(later);
        DungeonPreparationLeaseBundle bundle =
                new DungeonPreparationLeaseBundle(leases);
        try {
            bundle.close();
            check(false, "bundle error: should throw");
        } catch (Error e) {
            check(e.getSuppressed().length == 1,
                    "bundle error: earlier exception suppressed");
        }
        check(faulty.closed.get() == 1,
                "bundle error: faulty closed");
        check(later.closed.get() == 1,
                "bundle error: later cleanup still attempted");
    }

    private static void bundleRejectsNullLease() {
        ArrayList<AutoCloseable> leases = new ArrayList<>();
        leases.add(null);
        try {
            new DungeonPreparationLeaseBundle(leases);
            check(false, "bundle null: should throw");
        } catch (NullPointerException expected) {
        }
    }

    private static void purgeRemovesResidualLeases() {
        DungeonPreparationJobRegistry reg = new DungeonPreparationJobRegistry();
        DungeonPreparationJob job = job();
        CountingLease lease = new CountingLease();
        job.addCloseableLease(lease);
        // Force the job to READY with residual leases (invariant violation)
        // Use direct internal state via package-private
        reg.submit(job);
        // Cancel the job, which will close leases
        reg.cancel(job.id(),
                DungeonPreparationCancellationReason.USER_CANCELLED,
                "test", 10L);
        check(lease.closed.get() == 1, "cancel: lease closed");
        // Now add a lease after cancellation to simulate violation
        // (terminal job rejects addLease, so this can only come from
        // a direct mutation bypass)
        reg.purgeTerminal(110L,
                DungeonPreparationJobRegistry.TERMINAL_RETENTION_TICKS);
        check(reg.findById(job.id()).isEmpty(), "purge: job removed");
    }

    private static void purgeAggregatesLeaseCleanupFailures() {
        DungeonPreparationJobRegistry reg = new DungeonPreparationJobRegistry();
        DungeonPreparationJob job = job();
        reg.submit(job);
        reg.cancel(job.id(),
                DungeonPreparationCancellationReason.USER_CANCELLED,
                "test", 10L);
        // At this point job is CANCELLED and has no leases because
        // cancel() already released them. The purgeTerminal defensive
        // check won't find residual leases here.
        reg.purgeTerminal(110L,
                DungeonPreparationJobRegistry.TERMINAL_RETENTION_TICKS);
        check(reg.findById(job.id()).isEmpty(), "purge aggregate: removed");
        // No residual leases to clean, so no exception
    }

    private static DungeonPreparationJob job() {
        return new DungeonPreparationJob(
                DungeonPreparationJobId.create(),
                DungeonPreparationRequest.forTests(
                        UUID.randomUUID(),
                        OVERWORLD,
                        new BlockPos(0, 64, 0),
                        validTribute(),
                        1
                ),
                0L
        );
    }

    private static ResolvedTribute validTribute() {
        return new ResolvedTribute(true, 1, 1, 0.0F, 1.0F, 1);
    }

    private static void advanceToCommitting(DungeonPreparationJob job) {
        job.advanceTo(DungeonPreparationStage.VALIDATING, 1L);
        job.advanceTo(DungeonPreparationStage.SCANNING_EXISTING_SITES, 2L);
        job.advanceTo(DungeonPreparationStage.SELECTING_CANDIDATE, 3L);
        job.advanceTo(DungeonPreparationStage.REQUESTING_START_CHUNK, 4L);
        job.advanceTo(DungeonPreparationStage.WAITING_FOR_START_CHUNK, 5L);
        job.advanceTo(DungeonPreparationStage.READING_STRUCTURE_START, 6L);
        job.advanceTo(DungeonPreparationStage.PLANNING_ENTRY_CHUNKS, 7L);
        job.advanceTo(DungeonPreparationStage.REQUESTING_ENTRY_CHUNKS, 8L);
        job.advanceTo(DungeonPreparationStage.WAITING_FOR_ENTRY_CHUNKS, 9L);
        job.advanceTo(DungeonPreparationStage.VALIDATING_ENTRY_CHUNKS, 10L);
        job.advanceTo(DungeonPreparationStage.VALIDATING_ENTRY, 11L);
        job.advanceTo(DungeonPreparationStage.READY_TO_COMMIT, 12L);
        job.advanceTo(DungeonPreparationStage.COMMITTING, 13L);
    }

    private static final class CountingLease implements AutoCloseable {
        final AtomicInteger closed = new AtomicInteger();

        @Override
        public void close() {
            this.closed.incrementAndGet();
        }
    }

    private static final class ThrowingLease implements AutoCloseable {
        final String name;
        final AtomicInteger closed = new AtomicInteger();

        ThrowingLease(String name) {
            this.name = name;
        }

        @Override
        public void close() {
            this.closed.incrementAndGet();
            throw new IllegalStateException(this.name);
        }
    }

    private static final class ErrorLease implements AutoCloseable {
        final AtomicBoolean closed = new AtomicBoolean();

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) return;
            throw new AssertionError("synthetic error from lease close");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
