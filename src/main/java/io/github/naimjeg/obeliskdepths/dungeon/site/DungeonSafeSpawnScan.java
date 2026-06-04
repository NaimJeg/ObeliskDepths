package io.github.naimjeg.obeliskdepths.dungeon.site;

import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationTickBudget;

/**
 * Owner-thread-confined incremental safe-spawn work. Implementations retain a
 * cursor, never a complete candidate collection, and preserve their progress
 * when either the candidate or wall-clock budget pauses an advance.
 */
public interface DungeonSafeSpawnScan {
    DungeonSafeSpawnScanResult advance(
            DungeonPreparationTickBudget budget,
            DungeonSafeSpawnScanPurpose purpose
    );

    DungeonSafeSpawnScanResult result();

    void cancel();
}
