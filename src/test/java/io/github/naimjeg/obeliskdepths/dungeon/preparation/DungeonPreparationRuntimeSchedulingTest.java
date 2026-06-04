package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class DungeonPreparationRuntimeSchedulingTest {
    private DungeonPreparationRuntimeSchedulingTest() {
    }

    public static void main(String[] args) {
        wallClockStopsBeforeStartingAnotherJob();
        partialTicksResumeAtFirstUnprocessedJob();
        completeTicksRotateTheStartingJob();
        recoveryAdmissionReusesBeforeApplyingCap();
        removalMakesRecoveryCapacityAvailable();
        insertionAndRemovalKeepRoundRobinOrderStable();
        boundedReconciliationBatchStopsAtCountLimit();
        boundedReconciliationBatchObservesTimeGuard();
        postTeleportHandoffWaitsForTrackedDestination();
        postTeleportHandoffReleasesOnLifecycleExit();
        postTeleportHandoffTimesOutAtFixedBound();
    }

    private static void wallClockStopsBeforeStartingAnotherJob() {
        AtomicInteger checks = new AtomicInteger();
        ArrayList<Integer> processed = new ArrayList<>();
        int count = DungeonPreparationRuntime.runRoundRobin(
                4,
                0,
                () -> checks.getAndIncrement() < 2,
                processed::add
        );
        check(count == 2, "time guard: exactly two jobs started");
        check(processed.equals(List.of(0, 1)),
                "time guard: no later job starts after exhaustion");
    }

    private static void partialTicksResumeAtFirstUnprocessedJob() {
        check(DungeonPreparationRuntime.nextRoundRobinCursor(0, 2, 4) == 2,
                "partial round robin resumes at first unprocessed entry");
        check(DungeonPreparationRuntime.nextRoundRobinCursor(3, 2, 4) == 1,
                "partial round robin wraps deterministically");
        check(DungeonPreparationRuntime.nextRoundRobinCursor(2, 0, 4) == 2,
                "no work preserves cursor");
    }

    private static void completeTicksRotateTheStartingJob() {
        check(DungeonPreparationRuntime.nextRoundRobinCursor(0, 4, 4) == 1,
                "full tick rotates start to prevent count-budget starvation");
        check(DungeonPreparationRuntime.nextRoundRobinCursor(3, 4, 4) == 0,
                "full tick rotation wraps");
    }

    private static void recoveryAdmissionReusesBeforeApplyingCap() {
        check(DungeonPreparationRuntime.recoveryAdmission(true, 4)
                        == DungeonPreparedEntryRecoveryStatus.REUSED,
                "same session is reused at cap");
        check(DungeonPreparationRuntime.recoveryAdmission(false, 4)
                        == DungeonPreparedEntryRecoveryStatus.REJECTED,
                "fifth distinct recovery is rejected");
    }

    private static void removalMakesRecoveryCapacityAvailable() {
        check(DungeonPreparationRuntime.recoveryAdmission(false, 3)
                        == DungeonPreparedEntryRecoveryStatus.STARTED,
                "removing one of four recoveries restores capacity");
    }

    private static void insertionAndRemovalKeepRoundRobinOrderStable() {
        ArrayList<Integer> first = new ArrayList<>();
        DungeonPreparationRuntime.runRoundRobin(
                4,
                1,
                () -> true,
                first::add
        );
        check(first.equals(List.of(1, 2, 3, 0)),
                "insertion order is traversed from stable cursor");

        ArrayList<Integer> afterRemoval = new ArrayList<>();
        DungeonPreparationRuntime.runRoundRobin(
                3,
                2,
                () -> true,
                afterRemoval::add
        );
        check(afterRemoval.equals(List.of(2, 0, 1)),
                "removal preserves stable cyclic traversal");
    }

    private static void boundedReconciliationBatchStopsAtCountLimit() {
        ArrayList<Integer> processed = new ArrayList<>();
        int count = DungeonPreparationRuntime.runBoundedRoundRobin(
                5, 3, 2, () -> true, processed::add
        );
        check(count == 2, "bounded batch: exact count");
        check(processed.equals(List.of(3, 4)),
                "bounded batch: deterministic wrapped order");
        check(DungeonPreparationRuntime.nextRoundRobinCursor(3, count, 5) == 0,
                "bounded batch: resumes at first unprocessed entry");
    }

    private static void boundedReconciliationBatchObservesTimeGuard() {
        AtomicInteger checks = new AtomicInteger();
        ArrayList<Integer> processed = new ArrayList<>();
        int count = DungeonPreparationRuntime.runBoundedRoundRobin(
                5, 1, 4,
                () -> checks.getAndIncrement() < 1,
                processed::add
        );
        check(count == 1, "bounded time: only one entry inspected");
        check(processed.equals(List.of(1)),
                "bounded time: later entries wait for another tick");
    }

    private static void postTeleportHandoffWaitsForTrackedDestination() {
        check(handoff(true, true, true, true, true, false, 1)
                        == DungeonPreparationRuntime.PostTeleportHandoffDecision.WAIT,
                "handoff waits while the destination chunk packet is pending");
        check(handoff(true, true, true, true, false, true, 1)
                        == DungeonPreparationRuntime.PostTeleportHandoffDecision.WAIT,
                "handoff waits while any required entry chunk is unavailable");
        check(handoff(true, true, true, true, true, true, 1)
                        == DungeonPreparationRuntime.PostTeleportHandoffDecision.TRACKING_ESTABLISHED,
                "handoff releases only after public chunk tracking is established");
    }

    private static void postTeleportHandoffReleasesOnLifecycleExit() {
        check(handoff(false, true, true, true, true, true, 1)
                        == DungeonPreparationRuntime.PostTeleportHandoffDecision.PLAYER_UNAVAILABLE,
                "disconnect releases the handoff");
        check(handoff(true, false, true, true, true, true, 1)
                        == DungeonPreparationRuntime.PostTeleportHandoffDecision.PLAYER_UNAVAILABLE,
                "death releases the handoff");
        check(handoff(true, true, false, false, true, false, 1)
                        == DungeonPreparationRuntime.PostTeleportHandoffDecision.PLAYER_LEFT_DESTINATION,
                "dimension departure releases the handoff");
        check(handoff(true, true, true, false, true, false, 1)
                        == DungeonPreparationRuntime.PostTeleportHandoffDecision.PLAYER_LEFT_DESTINATION,
                "leaving the destination chunk releases the handoff");
    }

    private static void postTeleportHandoffTimesOutAtFixedBound() {
        long timeout = DungeonPreparationLimits.POST_TELEPORT_HANDOFF_TIMEOUT_TICKS;
        check(handoff(true, true, true, true, true, false, timeout - 1)
                        == DungeonPreparationRuntime.PostTeleportHandoffDecision.WAIT,
                "handoff remains active before its timeout");
        check(handoff(true, true, true, true, true, false, timeout)
                        == DungeonPreparationRuntime.PostTeleportHandoffDecision.TIMED_OUT,
                "handoff releases at its fixed timeout");
        check(DungeonPreparationLimits.POST_TELEPORT_HANDOFFS_PER_LEVEL_TICK > 0,
                "handoff maintenance has a fixed positive per-tick cap");
    }

    private static DungeonPreparationRuntime.PostTeleportHandoffDecision handoff(
            boolean samePlayer,
            boolean alive,
            boolean sameLevel,
            boolean sameChunk,
            boolean chunksAvailable,
            boolean tracked,
            long elapsed
    ) {
        return DungeonPreparationRuntime.postTeleportHandoffDecision(
                samePlayer,
                alive,
                sameLevel,
                sameChunk,
                chunksAvailable,
                tracked,
                elapsed
        );
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
