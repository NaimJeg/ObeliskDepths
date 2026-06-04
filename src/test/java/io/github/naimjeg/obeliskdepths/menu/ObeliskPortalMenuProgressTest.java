package io.github.naimjeg.obeliskdepths.menu;

import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationProgressSnapshot;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationStage;

import java.util.Optional;

public final class ObeliskPortalMenuProgressTest {
    private ObeliskPortalMenuProgressTest() {
    }

    public static void main(String[] args) {
        check(ObeliskPortalMenu.menuDataValue(-1L) == 0,
                "negative progress clamps to zero");
        check(ObeliskPortalMenu.menuDataValue(123L) == 123,
                "small progress remains exact");
        check(ObeliskPortalMenu.menuDataValue(Long.MAX_VALUE)
                        == DungeonPreparationProgressSnapshot.MAX_MENU_DATA_VALUE,
                "large progress clamps to the data-slot maximum");
        long[][] progressPairs = {
                {0L, 0L},
                {5L, 10L},
                {40_000L, 50_000L},
                {Long.MAX_VALUE - 1L, Long.MAX_VALUE}
        };
        for (long[] pair : progressPairs) {
            int checked = ObeliskPortalMenu.menuDataValue(pair[0]);
            int total = ObeliskPortalMenu.menuDataValue(pair[1]);
            check(checked >= 0 && checked <= total,
                    "display clamping preserves checked <= total");
        }
        stageSpecificProgressUsesMeaningfulUnits();
        zeroTotalIsIndeterminate();
    }

    private static void stageSpecificProgressUsesMeaningfulUnits() {
        checkProgress(DungeonPreparationStage.SCANNING_EXISTING_SITES, 5, 10);
        checkProgress(DungeonPreparationStage.REQUESTING_ENTRY_CHUNKS, 6, 8);
        checkProgress(DungeonPreparationStage.WAITING_FOR_ENTRY_CHUNKS, 4, 8);
        checkProgress(DungeonPreparationStage.VALIDATING_ENTRY_CHUNKS, 4, 8);
        checkProgress(DungeonPreparationStage.VALIDATING_ENTRY, 25, 100);
        checkProgress(DungeonPreparationStage.REQUESTING_START_CHUNK, 2, 3);
        checkProgress(DungeonPreparationStage.COMMITTING, 0, 0);
        checkProgress(DungeonPreparationStage.READY, 0, 0);
    }

    private static void zeroTotalIsIndeterminate() {
        DungeonPreparationMenuProgress.Progress progress =
                DungeonPreparationMenuProgress.normalize(
                        DungeonPreparationProgressSnapshot.empty(
                                DungeonPreparationStage.WAITING_FOR_ENTRY_CHUNKS,
                                Optional.empty()
                        )
                );
        check(progress.completed() == 0 && progress.total() == 0,
                "zero total: status-only progress");
    }

    private static void checkProgress(
            DungeonPreparationStage stage,
            int expectedCompleted,
            int expectedTotal
    ) {
        DungeonPreparationMenuProgress.Progress progress =
                DungeonPreparationMenuProgress.normalize(snapshot(stage));
        check(progress.completed() == expectedCompleted,
                stage + ": completed unit");
        check(progress.total() == expectedTotal, stage + ": total unit");
    }

    private static DungeonPreparationProgressSnapshot snapshot(
            DungeonPreparationStage stage
    ) {
        return new DungeonPreparationProgressSnapshot(
                stage,
                10,
                7,
                5,
                2,
                8,
                6,
                4,
                100L,
                25L,
                2,
                3,
                Optional.empty()
        );
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
