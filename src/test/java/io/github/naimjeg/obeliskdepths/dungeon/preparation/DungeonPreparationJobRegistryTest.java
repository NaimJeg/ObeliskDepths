package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.tribute.ResolvedTribute;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class DungeonPreparationJobRegistryTest {
    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION,
                    Identifier.fromNamespaceAndPath("minecraft", "overworld"));

    private DungeonPreparationJobRegistryTest() {}

    static {
        DungeonAsyncTestSupport.bootstrapMinecraft();
    }

    public static void main(String[] args) {
        duplicatePlayerSubmissionRejected();
        duplicateObeliskSubmissionRejected();
        duplicateActiveJobIdRejected();
        duplicateRetainedTerminalJobIdRejected();
        terminalOperationsImmediatelyClearActiveIndexes();
        terminalJobRetainedByFindById();
        terminalJobAbsentFromActiveLookup();
        staleTerminalMappingCannotRemoveNewMapping();
        purgeRemovesAllIndexes();
        activeCountExcludesTerminalJobs();
        activeJobsSnapshotPreservesSubmissionOrderAndExcludesTerminalJobs();
        retentionPreservesThenRemovesTerminalJobs();
        clearAllReleasesJobLeases();
        failCleanupExceptionRemovesIndexesAndRetainsJob();
        cancelCleanupExceptionRemovesIndexesAndRetainsJob();
        invalidNonterminalTransitionDoesNotClearIndexes();
        clearAllActiveAggregatesFailuresAndClearsIndexes();
        snapshotsAreImmutable();
    }

    private static void duplicatePlayerSubmissionRejected() {
        DungeonPreparationJobRegistry reg = new DungeonPreparationJobRegistry();
        UUID player = UUID.randomUUID();
        DungeonPreparationJob first = job(player, new BlockPos(0, 64, 0));
        DungeonPreparationJob second = job(player, new BlockPos(100, 64, 100));
        check(reg.submit(first).isAccepted(), "dup-player: first accepted");
        DungeonPreparationJobRegistry.SubmissionResult result = reg.submit(second);
        check(!result.isAccepted(), "dup-player: second rejected");
        check(result.rejectionReason()
                        == DungeonPreparationJobRegistry.SubmissionRejectionReason.DUPLICATE_PLAYER,
                "dup-player: reason");
        check(result.conflictingJobId().equals(first.id()), "dup-player: conflict id");
    }

    private static void duplicateObeliskSubmissionRejected() {
        DungeonPreparationJobRegistry reg = new DungeonPreparationJobRegistry();
        BlockPos obelisk = new BlockPos(0, 64, 0);
        DungeonPreparationJob first = job(UUID.randomUUID(), obelisk);
        DungeonPreparationJob second = job(UUID.randomUUID(), obelisk);
        check(reg.submit(first).isAccepted(), "dup-obelisk: first accepted");
        DungeonPreparationJobRegistry.SubmissionResult result = reg.submit(second);
        check(!result.isAccepted(), "dup-obelisk: second rejected");
        check(result.rejectionReason()
                        == DungeonPreparationJobRegistry.SubmissionRejectionReason.DUPLICATE_OBELISK,
                "dup-obelisk: reason");
    }

    private static void duplicateActiveJobIdRejected() {
        DungeonPreparationJobRegistry reg = new DungeonPreparationJobRegistry();
        DungeonPreparationJobId id = DungeonPreparationJobId.create();
        UUID firstPlayer = UUID.randomUUID();
        BlockPos firstObelisk = new BlockPos(0, 64, 0);
        DungeonPreparationJob first = job(id, firstPlayer, firstObelisk);
        DungeonPreparationJob duplicate =
                job(id, UUID.randomUUID(), new BlockPos(100, 64, 100));
        check(reg.submit(first).isAccepted(), "dup-id active: first accepted");
        DungeonPreparationJobRegistry.SubmissionResult result = reg.submit(duplicate);
        check(!result.isAccepted(), "dup-id active: duplicate rejected");
        check(result.rejectionReason()
                        == DungeonPreparationJobRegistry.SubmissionRejectionReason.DUPLICATE_JOB_ID,
                "dup-id active: reason");
        check(reg.findActiveByPlayer(firstPlayer).orElseThrow() == first,
                "dup-id active: player index intact");
        check(reg.findActiveByObelisk(OVERWORLD, firstObelisk).orElseThrow() == first,
                "dup-id active: obelisk index intact");
        check(reg.findById(id).orElseThrow() == first, "dup-id active: original intact");
    }

    private static void duplicateRetainedTerminalJobIdRejected() {
        DungeonPreparationJobRegistry reg = new DungeonPreparationJobRegistry();
        DungeonPreparationJobId id = DungeonPreparationJobId.create();
        DungeonPreparationJob first = job(id, UUID.randomUUID(), new BlockPos(0, 64, 0));
        check(reg.submit(first).isAccepted(), "dup-id retained: first accepted");
        reg.cancel(
                first.id(),
                DungeonPreparationCancellationReason.USER_CANCELLED,
                "test",
                10L
        );

        UUID duplicatePlayer = UUID.randomUUID();
        BlockPos duplicateObelisk = new BlockPos(100, 64, 100);
        DungeonPreparationJob duplicate = job(id, duplicatePlayer, duplicateObelisk);
        DungeonPreparationJobRegistry.SubmissionResult result = reg.submit(duplicate);
        check(!result.isAccepted(), "dup-id retained: duplicate rejected");
        check(result.rejectionReason()
                        == DungeonPreparationJobRegistry.SubmissionRejectionReason.DUPLICATE_JOB_ID,
                "dup-id retained: reason");
        check(reg.findActiveByPlayer(duplicatePlayer).isEmpty(),
                "dup-id retained: player index unchanged");
        check(reg.findActiveByObelisk(OVERWORLD, duplicateObelisk).isEmpty(),
                "dup-id retained: obelisk index unchanged");
        check(reg.findById(id).orElseThrow() == first, "dup-id retained: original intact");
    }

    private static void activeJobsSnapshotPreservesSubmissionOrderAndExcludesTerminalJobs() {
        DungeonPreparationJobRegistry reg = new DungeonPreparationJobRegistry();
        DungeonPreparationJob first = job(UUID.randomUUID(), new BlockPos(0, 64, 0));
        DungeonPreparationJob second = job(UUID.randomUUID(), new BlockPos(100, 64, 100));
        DungeonPreparationJob third = job(UUID.randomUUID(), new BlockPos(200, 64, 200));
        check(reg.submit(first).isAccepted(), "active snapshot: first accepted");
        check(reg.submit(second).isAccepted(), "active snapshot: second accepted");
        check(reg.submit(third).isAccepted(), "active snapshot: third accepted");

        reg.cancel(
                second.id(),
                DungeonPreparationCancellationReason.USER_CANCELLED,
                "test",
                5L
        );

        List<DungeonPreparationJob> snapshot = reg.activeJobsSnapshot();
        check(snapshot.size() == 2, "active snapshot: terminal excluded");
        check(snapshot.get(0) == first, "active snapshot: first remains first");
        check(snapshot.get(1) == third, "active snapshot: third remains second");
        try {
            snapshot.clear();
            check(false, "active snapshot: should be immutable");
        } catch (UnsupportedOperationException expected) {
            check(reg.activeCount() == 2, "active snapshot: registry unchanged");
        }
    }

    private static void terminalOperationsImmediatelyClearActiveIndexes() {
        DungeonPreparationJobRegistry reg = new DungeonPreparationJobRegistry();
        UUID player = UUID.randomUUID();
        BlockPos obelisk = new BlockPos(0, 64, 0);
        DungeonPreparationJob job = job(player, obelisk);
        reg.submit(job);
        reg.cancel(
                job.id(),
                DungeonPreparationCancellationReason.USER_CANCELLED,
                "test",
                10L
        );
        check(reg.findActiveByPlayer(player).isEmpty(), "terminal cleanup: player");
        check(reg.findActiveByObelisk(OVERWORLD, obelisk).isEmpty(), "terminal cleanup: obelisk");
        check(reg.activeCount() == 0, "terminal cleanup: active count");
    }

    private static void terminalJobRetainedByFindById() {
        DungeonPreparationJobRegistry reg = new DungeonPreparationJobRegistry();
        DungeonPreparationJob job = job(UUID.randomUUID(), new BlockPos(0, 64, 0));
        reg.submit(job);
        reg.cancel(
                job.id(),
                DungeonPreparationCancellationReason.USER_CANCELLED,
                "test",
                10L
        );
        check(reg.findById(job.id()).orElseThrow() == job, "findById: retained terminal");
    }

    private static void terminalJobAbsentFromActiveLookup() {
        DungeonPreparationJobRegistry reg = new DungeonPreparationJobRegistry();
        DungeonPreparationJob job = job(UUID.randomUUID(), new BlockPos(0, 64, 0));
        reg.submit(job);
        reg.cancel(
                job.id(),
                DungeonPreparationCancellationReason.USER_CANCELLED,
                "test",
                10L
        );
        check(reg.findActiveById(job.id()).isEmpty(), "activeById: terminal absent");
    }

    private static void staleTerminalMappingCannotRemoveNewMapping() {
        DungeonPreparationJobRegistry reg = new DungeonPreparationJobRegistry();
        UUID player = UUID.randomUUID();
        BlockPos obelisk = new BlockPos(0, 64, 0);
        DungeonPreparationJob oldJob = job(player, obelisk);
        reg.submit(oldJob);
        oldJob.cancel(DungeonPreparationCancellationReason.USER_CANCELLED, "direct", 10L);

        DungeonPreparationJob newJob = job(player, obelisk);
        check(reg.submit(newJob).isAccepted(), "stale: new accepted");
        reg.removeFromActiveIndexes(oldJob);
        check(reg.findActiveByPlayer(player).orElseThrow() == newJob, "stale: player kept");
        check(reg.findActiveByObelisk(OVERWORLD, obelisk).orElseThrow() == newJob,
                "stale: obelisk kept");
    }

    private static void purgeRemovesAllIndexes() {
        DungeonPreparationJobRegistry reg = new DungeonPreparationJobRegistry();
        UUID player = UUID.randomUUID();
        BlockPos obelisk = new BlockPos(0, 64, 0);
        DungeonPreparationJob job = job(player, obelisk);
        reg.submit(job);
        job.cancel(DungeonPreparationCancellationReason.USER_CANCELLED, "direct", 10L);
        reg.purgeTerminal(110L, DungeonPreparationJobRegistry.TERMINAL_RETENTION_TICKS);
        check(reg.findById(job.id()).isEmpty(), "purge: job removed");
        check(reg.findActiveByPlayer(player).isEmpty(), "purge: player removed");
        check(reg.findActiveByObelisk(OVERWORLD, obelisk).isEmpty(), "purge: obelisk removed");
    }

    private static void activeCountExcludesTerminalJobs() {
        DungeonPreparationJobRegistry reg = new DungeonPreparationJobRegistry();
        DungeonPreparationJob job = job(UUID.randomUUID(), new BlockPos(0, 64, 0));
        reg.submit(job);
        job.cancel(DungeonPreparationCancellationReason.USER_CANCELLED, "direct", 10L);
        check(reg.activeCount() == 0, "activeCount: terminal excluded");
    }

    private static void retentionPreservesThenRemovesTerminalJobs() {
        DungeonPreparationJobRegistry reg = new DungeonPreparationJobRegistry();
        DungeonPreparationJob job = job(UUID.randomUUID(), new BlockPos(0, 64, 0));
        reg.submit(job);
        reg.cancel(
                job.id(),
                DungeonPreparationCancellationReason.USER_CANCELLED,
                "test",
                10L
        );
        reg.purgeTerminal(109L, DungeonPreparationJobRegistry.TERMINAL_RETENTION_TICKS);
        check(reg.findById(job.id()).isPresent(), "retention: preserved before threshold");
        reg.purgeTerminal(110L, DungeonPreparationJobRegistry.TERMINAL_RETENTION_TICKS);
        check(reg.findById(job.id()).isEmpty(), "retention: removed at threshold");
    }

    private static void clearAllReleasesJobLeases() {
        DungeonPreparationJobRegistry reg = new DungeonPreparationJobRegistry();
        DungeonPreparationJob first = job(UUID.randomUUID(), new BlockPos(0, 64, 0));
        DungeonPreparationJob second = job(UUID.randomUUID(), new BlockPos(100, 64, 100));
        CountingLease firstLease = new CountingLease();
        CountingLease secondLease = new CountingLease();
        first.addCloseableLease(firstLease);
        second.addCloseableLease(secondLease);
        reg.submit(first);
        reg.submit(second);
        reg.clearAllActive(
                DungeonPreparationCancellationReason.SERVER_STOPPING,
                "test",
                20L
        );
        check(firstLease.closed == 1, "clearAll: first lease");
        check(secondLease.closed == 1, "clearAll: second lease");
        check(reg.activeCount() == 0, "clearAll: active cleared");
    }

    private static void failCleanupExceptionRemovesIndexesAndRetainsJob() {
        DungeonPreparationJobRegistry reg = new DungeonPreparationJobRegistry();
        UUID player = UUID.randomUUID();
        BlockPos obelisk = new BlockPos(0, 64, 0);
        DungeonPreparationJob job = job(player, obelisk);
        ThrowingLease lease = new ThrowingLease("fail");
        job.addCloseableLease(lease);
        reg.submit(job);
        reg.advance(job.id(), DungeonPreparationStage.VALIDATING, 1L);
        try {
            reg.fail(job.id(), DungeonPreparationJobFailureReason.INTERNAL_ERROR, "test", 2L);
            check(false, "fail cleanup: should throw");
        } catch (IllegalStateException exception) {
            check(exception.getSuppressed().length == 1, "fail cleanup: suppressed");
        }
        check(job.stage() == DungeonPreparationStage.FAILED, "fail cleanup: failed");
        check(reg.findActiveByPlayer(player).isEmpty(), "fail cleanup: player removed");
        check(reg.findActiveByObelisk(OVERWORLD, obelisk).isEmpty(),
                "fail cleanup: obelisk removed");
        check(reg.findById(job.id()).orElseThrow() == job, "fail cleanup: retained");
    }

    private static void cancelCleanupExceptionRemovesIndexesAndRetainsJob() {
        DungeonPreparationJobRegistry reg = new DungeonPreparationJobRegistry();
        UUID player = UUID.randomUUID();
        BlockPos obelisk = new BlockPos(0, 64, 0);
        DungeonPreparationJob job = job(player, obelisk);
        ThrowingLease lease = new ThrowingLease("cancel");
        job.addCloseableLease(lease);
        reg.submit(job);
        try {
            reg.cancel(
                    job.id(),
                    DungeonPreparationCancellationReason.USER_CANCELLED,
                    "test",
                    2L
            );
            check(false, "cancel cleanup: should throw");
        } catch (IllegalStateException exception) {
            check(exception.getSuppressed().length == 1, "cancel cleanup: suppressed");
        }
        check(job.stage() == DungeonPreparationStage.CANCELLED,
                "cancel cleanup: cancelled");
        check(reg.findActiveByPlayer(player).isEmpty(), "cancel cleanup: player removed");
        check(reg.findActiveByObelisk(OVERWORLD, obelisk).isEmpty(),
                "cancel cleanup: obelisk removed");
        check(reg.findById(job.id()).orElseThrow() == job, "cancel cleanup: retained");
    }

    private static void invalidNonterminalTransitionDoesNotClearIndexes() {
        DungeonPreparationJobRegistry reg = new DungeonPreparationJobRegistry();
        UUID player = UUID.randomUUID();
        BlockPos obelisk = new BlockPos(0, 64, 0);
        DungeonPreparationJob job = job(player, obelisk);
        reg.submit(job);
        try {
            reg.fail(job.id(), DungeonPreparationJobFailureReason.INTERNAL_ERROR, "test", 2L);
            check(false, "invalid nonterminal: should throw");
        } catch (IllegalStateException expected) {
            // expected
        }
        check(job.stage() == DungeonPreparationStage.QUEUED,
                "invalid nonterminal: still queued");
        check(reg.findActiveByPlayer(player).orElseThrow() == job,
                "invalid nonterminal: player retained");
        check(reg.findActiveByObelisk(OVERWORLD, obelisk).orElseThrow() == job,
                "invalid nonterminal: obelisk retained");
    }

    private static void clearAllActiveAggregatesFailuresAndClearsIndexes() {
        DungeonPreparationJobRegistry reg = new DungeonPreparationJobRegistry();
        DungeonPreparationJob first = job(UUID.randomUUID(), new BlockPos(0, 64, 0));
        DungeonPreparationJob second = job(UUID.randomUUID(), new BlockPos(100, 64, 100));
        DungeonPreparationJob third = job(UUID.randomUUID(), new BlockPos(200, 64, 200));
        ThrowingLease firstLease = new ThrowingLease("first");
        CountingLease secondLease = new CountingLease();
        ThrowingLease thirdLease = new ThrowingLease("third");
        first.addCloseableLease(firstLease);
        second.addCloseableLease(secondLease);
        third.addCloseableLease(thirdLease);
        reg.submit(first);
        reg.submit(second);
        reg.submit(third);
        try {
            reg.clearAllActive(
                    DungeonPreparationCancellationReason.SERVER_STOPPING,
                    "test",
                    20L
            );
            check(false, "clearAll aggregate: should throw");
        } catch (IllegalStateException exception) {
            check(exception.getSuppressed().length == 2,
                    "clearAll aggregate: suppressed");
        }
        check(firstLease.closed == 1, "clearAll aggregate: first attempted");
        check(secondLease.closed == 1, "clearAll aggregate: second attempted");
        check(thirdLease.closed == 1, "clearAll aggregate: third attempted");
        check(first.stage() == DungeonPreparationStage.CANCELLED,
                "clearAll aggregate: first cancelled");
        check(second.stage() == DungeonPreparationStage.CANCELLED,
                "clearAll aggregate: second cancelled");
        check(third.stage() == DungeonPreparationStage.CANCELLED,
                "clearAll aggregate: third cancelled");
        check(reg.activeCount() == 0, "clearAll aggregate: active indexes empty");
        check(reg.findById(first.id()).orElseThrow() == first,
                "clearAll aggregate: first retained");
    }

    private static void snapshotsAreImmutable() {
        DungeonPreparationJob job = job(UUID.randomUUID(), new BlockPos(0, 64, 0));
        DungeonPreparationJobSnapshot snap = job.snapshot();
        job.advanceTo(DungeonPreparationStage.VALIDATING, 1L);
        check(snap.stage() == DungeonPreparationStage.QUEUED, "snapshot: immutable");
    }

    private static DungeonPreparationJob job(UUID player, BlockPos obelisk) {
        return job(DungeonPreparationJobId.create(), player, obelisk);
    }

    private static DungeonPreparationJob job(
            DungeonPreparationJobId id,
            UUID player,
            BlockPos obelisk
    ) {
        return new DungeonPreparationJob(
                id,
                DungeonPreparationRequest.forTests(
                        player,
                        OVERWORLD,
                        obelisk,
                        validTribute(),
                        1
                ),
                0L
        );
    }

    private static ResolvedTribute validTribute() {
        return new ResolvedTribute(true, 1, 1, 0.0F, 1.0F, 1);
    }

    private static final class CountingLease implements AutoCloseable {
        int closed;

        @Override
        public void close() {
            this.closed++;
        }
    }

    private static final class ThrowingLease implements AutoCloseable {
        final String name;
        int closed;

        ThrowingLease(String name) {
            this.name = name;
        }

        @Override
        public void close() {
            this.closed++;
            throw new IllegalStateException(this.name);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
