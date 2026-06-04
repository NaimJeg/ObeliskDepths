package io.github.naimjeg.obeliskdepths.dungeon.spatial;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import io.github.naimjeg.obeliskdepths.dungeon.territory.DungeonTerritory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

public final class DungeonSpatialIndex {
    private DungeonSpatialIndex() {
    }

    public static Optional<DungeonTerritory> findTerritoryAt(
            ServerLevel dungeonLevel,
            BlockPos pos
    ) {
        return DungeonManagerSavedData.get(dungeonLevel)
                .territories()
                .findContaining(pos);
    }

    public static Optional<DungeonInstanceId> findPhysicalOwnerAt(
            ServerLevel dungeonLevel,
            BlockPos pos
    ) {
        return findTerritoryAt(dungeonLevel, pos)
                .map(DungeonTerritory::instanceId);
    }

    public static boolean isPhysicallyInsideInstance(
            ServerLevel dungeonLevel,
            BlockPos pos,
            DungeonInstanceId instanceId
    ) {
        return findPhysicalOwnerAt(dungeonLevel, pos)
                .map(instanceId::equals)
                .orElse(false);
    }

    /*
     * Future:
     * Replace linear territory lookup with chunk-indexed lookup:
     *
     * Map<Long, DungeonTerritoryId> territoryByChunk;
     *
     * This class should remain a physical world-space lookup service only.
     * It must not become the authority for player membership, raid membership,
     * room ownership, or reward eligibility.
     */
}
