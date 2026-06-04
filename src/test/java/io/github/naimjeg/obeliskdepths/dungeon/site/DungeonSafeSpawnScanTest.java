package io.github.naimjeg.obeliskdepths.dungeon.site;

import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonAsyncTestSupport;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationTickBudget;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.territory.DungeonBounds;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class DungeonSafeSpawnScanTest {
    private DungeonSafeSpawnScanTest() {
    }

    static {
        DungeonAsyncTestSupport.bootstrapMinecraft();
    }

    public static void main(String[] args) {
        cursorMatchesLegacyComparatorForRepresentativeRooms();
        cursorPauseResumeWorksAtEveryTraversalStep();
        thinAndEmptyInteriorsAreHandled();
        totalCandidateReportingIsOverflowSafe();
        foundReturnsFirstLegacyCandidate();
        exhaustedChecksEveryCandidateOnce();
        scanPausesAndResumesWithoutDuplication();
        preparationAndRecoveryShareOneCandidateBudget();
        recoveryScanPausesAndResumes();
        wallClockExhaustionPausesWithoutFailure();
        cancellationDiscardsPendingWork();
    }

    private static void cursorMatchesLegacyComparatorForRepresentativeRooms() {
        List<DungeonGeneratedRoom> rooms = List.of(
                room(new DungeonBounds(-4, 60, -2, 5, 68, 3), new BlockPos(0, 64, 0)),
                room(new DungeonBounds(10, 60, 20, 14, 68, 28), new BlockPos(11, 64, 26)),
                room(new DungeonBounds(-8, 60, -8, -2, 68, -3), new BlockPos(-8, 64, -8)),
                room(new DungeonBounds(0, 60, 0, 2, 68, 9), new BlockPos(2, 64, 8)),
                room(new DungeonBounds(-5, 60, 7, 3, 68, 12), new BlockPos(-12, 64, 15))
        );
        for (DungeonGeneratedRoom room : rooms) {
            check(actualOrder(room).equals(legacyOrder(room)),
                    "cursor order must match legacy comparator for " + room.bounds());
        }
    }

    private static void cursorPauseResumeWorksAtEveryTraversalStep() {
        DungeonGeneratedRoom room = room(
                new DungeonBounds(-3, 60, 4, 4, 68, 9),
                new BlockPos(-3, 64, 9)
        );
        List<BlockPos> expected = legacyOrder(room);
        int stepCount = cursorStepCount(room);
        for (int pauseAfter = 0; pauseAfter <= stepCount; pauseAfter++) {
            DungeonSafeSpawnCandidateCursor cursor =
                    new DungeonSafeSpawnCandidateCursor(room);
            ArrayList<BlockPos> actual = new ArrayList<>();
            for (int step = 0; step < pauseAfter; step++) {
                cursor.step().candidate().ifPresent(actual::add);
            }
            while (true) {
                DungeonSafeSpawnCandidateCursor.CursorStep step = cursor.step();
                step.candidate().ifPresent(actual::add);
                if (step.exhausted()) {
                    break;
                }
            }
            check(actual.equals(expected),
                    "pause at traversal step " + pauseAfter
                            + " must not duplicate or omit candidates");
        }
    }

    private static void thinAndEmptyInteriorsAreHandled() {
        DungeonGeneratedRoom thin = room(
                new DungeonBounds(0, 60, 0, 2, 68, 5),
                new BlockPos(1, 64, 1)
        );
        check(actualOrder(thin).size() == 4, "thin room interior");

        DungeonGeneratedRoom empty = room(
                new DungeonBounds(0, 60, 0, 1, 68, 8),
                new BlockPos(0, 64, 0)
        );
        DungeonSafeSpawnScan scan = scan(empty, null, new ArrayList<>());
        check(scan.result().state() == DungeonSafeSpawnScanState.EXHAUSTED,
                "empty interior is immediately exhausted");
        check(scan.result().totalCandidates() == 0L,
                "empty interior total is zero");
    }

    private static void foundReturnsFirstLegacyCandidate() {
        DungeonGeneratedRoom room = room(
                new DungeonBounds(-3, 60, -3, 4, 68, 4),
                new BlockPos(0, 64, 0)
        );
        BlockPos expected = legacyOrder(room).get(7);
        ArrayList<BlockPos> checked = new ArrayList<>();
        DungeonSafeSpawnScan scan = scan(room, expected, checked);
        DungeonSafeSpawnScanResult result = scan.advance(
                DungeonPreparationTickBudget.unlimitedForTests(),
                DungeonSafeSpawnScanPurpose.PREPARATION
        );
        check(result.state() == DungeonSafeSpawnScanState.FOUND,
                "target candidate is found");
        check(result.resolvedPosition().orElseThrow().equals(
                        net.minecraft.world.phys.Vec3.atCenterOf(expected)),
                "found position is centered target");
        check(checked.equals(legacyOrder(room).subList(0, 8)),
                "found uses the first valid legacy-ordered candidate");
    }

    private static void totalCandidateReportingIsOverflowSafe() {
        DungeonGeneratedRoom huge = room(
                new DungeonBounds(
                        Integer.MIN_VALUE, 0, Integer.MIN_VALUE,
                        Integer.MAX_VALUE, 4, Integer.MAX_VALUE
                ),
                new BlockPos(0, 1, 0)
        );
        DungeonSafeSpawnCandidateCursor cursor =
                new DungeonSafeSpawnCandidateCursor(huge);
        check(cursor.totalCandidates() == Long.MAX_VALUE,
                "overflowing candidate total saturates safely");

        DungeonGeneratedRoom exact = room(
                new DungeonBounds(-2, 0, 10, 0, 4, 14),
                new BlockPos(-1, 1, 12)
        );
        check(new DungeonSafeSpawnCandidateCursor(exact).totalCandidates() == 3L,
                "one-block-wide candidate total remains exact");
    }

    private static void exhaustedChecksEveryCandidateOnce() {
        DungeonGeneratedRoom room = room(
                new DungeonBounds(0, 60, 0, 6, 68, 4),
                new BlockPos(5, 64, 1)
        );
        ArrayList<BlockPos> checked = new ArrayList<>();
        DungeonSafeSpawnScan scan = scan(room, null, checked);
        DungeonSafeSpawnScanResult result = scan.advance(
                DungeonPreparationTickBudget.unlimitedForTests(),
                DungeonSafeSpawnScanPurpose.PREPARATION
        );
        check(result.state() == DungeonSafeSpawnScanState.EXHAUSTED,
                "invalid room exhausts");
        check(checked.equals(legacyOrder(room)),
                "every candidate checked exactly once in legacy order");
        check(result.candidatesChecked() == result.totalCandidates(),
                "exhausted checked equals total");
    }

    private static void scanPausesAndResumesWithoutDuplication() {
        DungeonGeneratedRoom room = room(
                new DungeonBounds(0, 60, 0, 12, 68, 12),
                new BlockPos(6, 64, 6)
        );
        ArrayList<BlockPos> checked = new ArrayList<>();
        DungeonSafeSpawnScan scan = scan(room, null, checked);
        DungeonSafeSpawnScanResult first = scan.advance(
                budget(new MutableClock(), 7, 9_999L),
                DungeonSafeSpawnScanPurpose.PREPARATION
        );
        check(first.running() && first.candidatesChecked() == 7L,
                "first allowance pauses at seven");
        DungeonSafeSpawnScanResult second = scan.advance(
                budget(new MutableClock(), 5, 9_999L),
                DungeonSafeSpawnScanPurpose.PREPARATION
        );
        check(second.running() && second.candidatesChecked() == 12L,
                "second allowance resumes at prior cursor");
        check(checked.stream().distinct().count() == checked.size(),
                "resume does not duplicate candidates");
    }

    private static void wallClockExhaustionPausesWithoutFailure() {
        DungeonGeneratedRoom room = room(
                new DungeonBounds(0, 60, 0, 12, 68, 12),
                new BlockPos(6, 64, 6)
        );
        MutableClock clock = new MutableClock();
        ArrayList<BlockPos> checked = new ArrayList<>();
        DungeonSafeSpawnScan scan = DungeonSafeSpawnResolver.createForTests(
                room,
                pos -> {
                    checked.add(pos.immutable());
                    clock.advance(5L);
                    return false;
                },
                () -> { }
        );
        DungeonSafeSpawnScanResult first = scan.advance(
                budget(clock, 64, 5L),
                DungeonSafeSpawnScanPurpose.RECOVERY
        );
        check(first.running(), "time exhaustion pauses without failure");
        check(first.candidatesChecked() == 1L,
                "time guard stops before second candidate");
    }

    private static void preparationAndRecoveryShareOneCandidateBudget() {
        DungeonGeneratedRoom room = room(
                new DungeonBounds(0, 60, 0, 20, 68, 20),
                new BlockPos(10, 64, 10)
        );
        ArrayList<BlockPos> preparationChecks = new ArrayList<>();
        ArrayList<BlockPos> recoveryChecks = new ArrayList<>();
        DungeonSafeSpawnScan preparation = scan(
                room,
                legacyOrder(room).get(49),
                preparationChecks
        );
        DungeonSafeSpawnScan recovery = scan(room, null, recoveryChecks);
        DungeonPreparationTickBudget shared =
                budget(new MutableClock(), 64, 9_999L);

        preparation.advance(shared, DungeonSafeSpawnScanPurpose.PREPARATION);
        recovery.advance(shared, DungeonSafeSpawnScanPurpose.RECOVERY);

        check(preparationChecks.size() + recoveryChecks.size() == 64,
                "preparation and recovery combined consume at most 64");
        check(preparationChecks.size() == 50 && recoveryChecks.size() == 14,
                "recovery receives only the preparation budget remainder");
        check(shared.remainingSafeSpawnCandidates() == 0,
                "combined work exhausts one shared level allowance");

        recovery.advance(
                budget(new MutableClock(), 64, 9_999L),
                DungeonSafeSpawnScanPurpose.RECOVERY
        );
        check(recoveryChecks.size() > 14,
                "the 65th combined candidate waits for the next tick");
    }

    private static void recoveryScanPausesAndResumes() {
        DungeonGeneratedRoom room = room(
                new DungeonBounds(0, 60, 0, 12, 68, 12),
                new BlockPos(6, 64, 6)
        );
        ArrayList<BlockPos> checked = new ArrayList<>();
        DungeonSafeSpawnScan scan = scan(room, null, checked);
        DungeonSafeSpawnScanResult first = scan.advance(
                budget(new MutableClock(), 3, 9_999L),
                DungeonSafeSpawnScanPurpose.RECOVERY
        );
        DungeonSafeSpawnScanResult second = scan.advance(
                budget(new MutableClock(), 4, 9_999L),
                DungeonSafeSpawnScanPurpose.RECOVERY
        );
        check(first.running() && first.candidatesChecked() == 3L,
                "recovery scan pauses on first allowance");
        check(second.running() && second.candidatesChecked() == 7L,
                "recovery scan resumes without restarting");
    }

    private static void cancellationDiscardsPendingWork() {
        DungeonGeneratedRoom room = room(
                new DungeonBounds(0, 60, 0, 8, 68, 8),
                new BlockPos(4, 64, 4)
        );
        DungeonSafeSpawnScan scan = scan(room, null, new ArrayList<>());
        scan.advance(
                budget(new MutableClock(), 1, 9_999L),
                DungeonSafeSpawnScanPurpose.PREPARATION
        );
        long checked = scan.result().candidatesChecked();
        scan.cancel();
        check(scan.result().state() == DungeonSafeSpawnScanState.CANCELLED,
                "cancelled state");
        scan.advance(
                DungeonPreparationTickBudget.unlimitedForTests(),
                DungeonSafeSpawnScanPurpose.PREPARATION
        );
        check(scan.result().candidatesChecked() == checked,
                "cancelled scan performs no later checks");
    }

    private static DungeonSafeSpawnScan scan(
            DungeonGeneratedRoom room,
            BlockPos valid,
            List<BlockPos> checked
    ) {
        return DungeonSafeSpawnResolver.createForTests(
                room,
                pos -> {
                    checked.add(pos.immutable());
                    return valid != null && pos.equals(valid);
                },
                () -> { }
        );
    }

    private static List<BlockPos> actualOrder(DungeonGeneratedRoom room) {
        DungeonSafeSpawnCandidateCursor cursor =
                new DungeonSafeSpawnCandidateCursor(room);
        ArrayList<BlockPos> result = new ArrayList<>();
        long guard = 0L;
        while (true) {
            DungeonSafeSpawnCandidateCursor.CursorStep step = cursor.step();
            step.candidate().ifPresent(result::add);
            if (step.exhausted()) {
                return List.copyOf(result);
            }
            if (++guard > 1_000_000L) {
                throw new AssertionError("cursor failed to terminate");
            }
        }
    }

    private static int cursorStepCount(DungeonGeneratedRoom room) {
        DungeonSafeSpawnCandidateCursor cursor =
                new DungeonSafeSpawnCandidateCursor(room);
        int steps = 0;
        while (true) {
            DungeonSafeSpawnCandidateCursor.CursorStep step = cursor.step();
            steps++;
            if (step.exhausted()) {
                return steps;
            }
        }
    }

    private static List<BlockPos> legacyOrder(DungeonGeneratedRoom room) {
        ArrayList<BlockPos> result = new ArrayList<>();
        int feetY = room.anchorPos().getY();
        for (long x = (long)room.bounds().minX() + 1L;
             x <= (long)room.bounds().maxX() - 1L; x++) {
            for (long z = (long)room.bounds().minZ() + 1L;
                 z <= (long)room.bounds().maxZ() - 1L; z++) {
                BlockPos pos = new BlockPos((int)x, feetY, (int)z);
                if (room.contains(pos)) {
                    result.add(pos);
                }
            }
        }
        result.sort(Comparator
                .comparingLong((BlockPos pos) ->
                        Math.abs((long)pos.getX() - room.anchorPos().getX())
                                + Math.abs((long)pos.getZ()
                                - room.anchorPos().getZ()))
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ));
        return List.copyOf(result);
    }

    private static DungeonGeneratedRoom room(
            DungeonBounds bounds,
            BlockPos anchor
    ) {
        return new DungeonGeneratedRoom(
                DungeonRoomId.of("safe_spawn_test"),
                DungeonRoomType.START,
                bounds,
                anchor
        );
    }

    private static DungeonPreparationTickBudget budget(
            MutableClock clock,
            int safeCandidates,
            long maxNanos
    ) {
        return DungeonPreparationTickBudget.boundedForTests(
                clock,
                maxNanos,
                0, 0, 0, 0, 0, 0, 0, 0,
                safeCandidates,
                0, 0, 0
        );
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class MutableClock
            implements DungeonPreparationTickBudget.NanoClock {
        private long nanos;

        @Override
        public long nanoTime() {
            return this.nanos;
        }

        private void advance(long amount) {
            this.nanos += amount;
        }
    }
}
