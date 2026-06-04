package io.github.naimjeg.obeliskdepths.dungeon.site;

import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationProfiler;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationTickBudget;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.Optional;

public final class DungeonSafeSpawnResolver {
    private DungeonSafeSpawnResolver() {
    }

    /** Creates, but does not eagerly advance, an owner-thread scan. */
    public static DungeonSafeSpawnScan createPrimaryEntryScan(
            ServerLevel level,
            DungeonSite site
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(site, "site");
        Runnable ownerAssertion = () -> {
            if (!level.getServer().isSameThread()) {
                throw new IllegalStateException(
                        "Safe-spawn scanning must run on the server thread"
                );
            }
        };
        Optional<DungeonGeneratedRoom> room = site.primaryEntryRoom()
                .filter(candidate -> candidate.type() == DungeonRoomType.START);
        if (room.isEmpty()) {
            return IncrementalScan.empty(ownerAssertion);
        }
        return new IncrementalScan(
                room.get(),
                feet -> validSpawn(level, room.get(), feet),
                ownerAssertion
        );
    }

    @FunctionalInterface
    interface CandidateValidator {
        boolean isValid(BlockPos pos);
    }

    static DungeonSafeSpawnScan createForTests(
            DungeonGeneratedRoom room,
            CandidateValidator candidateValidator,
            Runnable ownerAssertion
    ) {
        return new IncrementalScan(room, candidateValidator, ownerAssertion);
    }

    private static boolean validSpawn(
            ServerLevel level,
            DungeonGeneratedRoom room,
            BlockPos feet
    ) {
        BlockPos floor = feet.below();
        BlockPos head = feet.above();
        if (!room.contains(feet) || !room.contains(head)) {
            return false;
        }
        BlockState floorState = level.getBlockState(floor);
        BlockState feetState = level.getBlockState(feet);
        BlockState headState = level.getBlockState(head);
        return !floorState.isAir()
                && !floorState.is(Blocks.LAVA)
                && safeAir(feetState)
                && safeAir(headState);
    }

    private static boolean safeAir(BlockState state) {
        return state.isAir() || state.canBeReplaced();
    }

    private static final class IncrementalScan implements DungeonSafeSpawnScan {
        private final CandidateValidator candidateValidator;
        private final Runnable ownerAssertion;
        private final DungeonSafeSpawnCandidateCursor cursor;
        private final long totalCandidates;

        private DungeonSafeSpawnScanState state;
        private long candidatesChecked;
        private Vec3 resolvedPosition;
        private BlockPos pendingCandidate;

        private IncrementalScan(
                DungeonGeneratedRoom room,
                CandidateValidator candidateValidator,
                Runnable ownerAssertion
        ) {
            Objects.requireNonNull(room, "room");
            this.candidateValidator = Objects.requireNonNull(
                    candidateValidator,
                    "candidateValidator"
            );
            this.ownerAssertion = Objects.requireNonNull(
                    ownerAssertion,
                    "ownerAssertion"
            );
            this.cursor = new DungeonSafeSpawnCandidateCursor(room);
            this.totalCandidates = this.cursor.totalCandidates();
            this.state = this.totalCandidates == 0L
                    ? DungeonSafeSpawnScanState.EXHAUSTED
                    : DungeonSafeSpawnScanState.RUNNING;
        }

        private IncrementalScan(Runnable ownerAssertion) {
            this.candidateValidator = null;
            this.ownerAssertion = Objects.requireNonNull(
                    ownerAssertion,
                    "ownerAssertion"
            );
            this.cursor = null;
            this.totalCandidates = 0L;
            this.state = DungeonSafeSpawnScanState.EXHAUSTED;
        }

        private static IncrementalScan empty(Runnable ownerAssertion) {
            return new IncrementalScan(ownerAssertion);
        }

        @Override
        public DungeonSafeSpawnScanResult advance(
                DungeonPreparationTickBudget budget,
                DungeonSafeSpawnScanPurpose purpose
        ) {
            this.ownerAssertion.run();
            Objects.requireNonNull(budget, "budget");
            Objects.requireNonNull(purpose, "purpose");
            if (this.state != DungeonSafeSpawnScanState.RUNNING) {
                return snapshot();
            }

            DungeonPreparationProfiler profiler = DungeonPreparationProfiler.global();
            long startNanos = profiler.start();
            try {
                advanceCursor(budget, purpose, profiler);
            } finally {
                profiler.record(
                        purpose == DungeonSafeSpawnScanPurpose.PREPARATION
                                ? DungeonPreparationProfiler.Operation.PREPARATION_SAFE_SPAWN_ADVANCE
                                : DungeonPreparationProfiler.Operation.RECOVERY_SAFE_SPAWN_ADVANCE,
                        startNanos,
                        true
                );
            }
            return snapshot();
        }

        private void advanceCursor(
                DungeonPreparationTickBudget budget,
                DungeonSafeSpawnScanPurpose purpose,
                DungeonPreparationProfiler profiler
        ) {
            while (this.state == DungeonSafeSpawnScanState.RUNNING) {
                if (!budget.hasTimeRemaining()) {
                    profiler.recordElapsed(
                            budgetExhaustedOperation(purpose),
                            0L,
                            true
                    );
                    return;
                }
                if (this.pendingCandidate == null) {
                    DungeonSafeSpawnCandidateCursor.CursorStep step =
                            this.cursor.step();
                    if (step.exhausted()) {
                        this.state = DungeonSafeSpawnScanState.EXHAUSTED;
                        profiler.recordElapsed(
                                exhaustedOperation(purpose),
                                0L,
                                true
                        );
                        return;
                    }
                    this.pendingCandidate = step.candidate().orElse(null);
                    if (this.pendingCandidate == null) {
                        continue;
                    }
                }
                if (!budget.tryConsumeSafeSpawnCandidate()) {
                    profiler.recordElapsed(
                            budgetExhaustedOperation(purpose),
                            0L,
                            true
                    );
                    return;
                }

                BlockPos candidate = this.pendingCandidate;
                this.pendingCandidate = null;
                this.candidatesChecked++;
                profiler.recordElapsed(
                        purpose == DungeonSafeSpawnScanPurpose.PREPARATION
                                ? DungeonPreparationProfiler.Operation.PREPARATION_SAFE_SPAWN_CANDIDATE
                                : DungeonPreparationProfiler.Operation.RECOVERY_SAFE_SPAWN_CANDIDATE,
                        0L,
                        true
                );
                if (this.candidateValidator.isValid(candidate)) {
                    this.resolvedPosition = Vec3.atCenterOf(candidate);
                    this.state = DungeonSafeSpawnScanState.FOUND;
                    profiler.recordElapsed(
                            foundOperation(purpose),
                            0L,
                            true
                    );
                }
            }
        }

        private static DungeonPreparationProfiler.Operation budgetExhaustedOperation(
                DungeonSafeSpawnScanPurpose purpose
        ) {
            return purpose == DungeonSafeSpawnScanPurpose.PREPARATION
                    ? DungeonPreparationProfiler.Operation.PREPARATION_SAFE_SPAWN_BUDGET_EXHAUSTED
                    : DungeonPreparationProfiler.Operation.RECOVERY_SAFE_SPAWN_BUDGET_EXHAUSTED;
        }

        private static DungeonPreparationProfiler.Operation foundOperation(
                DungeonSafeSpawnScanPurpose purpose
        ) {
            return purpose == DungeonSafeSpawnScanPurpose.PREPARATION
                    ? DungeonPreparationProfiler.Operation.PREPARATION_SAFE_SPAWN_FOUND
                    : DungeonPreparationProfiler.Operation.RECOVERY_SAFE_SPAWN_FOUND;
        }

        private static DungeonPreparationProfiler.Operation exhaustedOperation(
                DungeonSafeSpawnScanPurpose purpose
        ) {
            return purpose == DungeonSafeSpawnScanPurpose.PREPARATION
                    ? DungeonPreparationProfiler.Operation.PREPARATION_SAFE_SPAWN_EXHAUSTED
                    : DungeonPreparationProfiler.Operation.RECOVERY_SAFE_SPAWN_EXHAUSTED;
        }

        @Override
        public DungeonSafeSpawnScanResult result() {
            this.ownerAssertion.run();
            return snapshot();
        }

        @Override
        public void cancel() {
            this.ownerAssertion.run();
            if (this.state == DungeonSafeSpawnScanState.RUNNING) {
                this.state = DungeonSafeSpawnScanState.CANCELLED;
                this.pendingCandidate = null;
            }
        }

        private DungeonSafeSpawnScanResult snapshot() {
            return new DungeonSafeSpawnScanResult(
                    this.state,
                    this.candidatesChecked,
                    this.totalCandidates,
                    Optional.ofNullable(this.resolvedPosition)
            );
        }
    }
}
