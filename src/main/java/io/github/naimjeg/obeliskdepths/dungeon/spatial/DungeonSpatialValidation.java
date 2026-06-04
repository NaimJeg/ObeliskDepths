package io.github.naimjeg.obeliskdepths.dungeon.spatial;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class DungeonSpatialValidation {
    private DungeonSpatialValidation() {
    }

    public static boolean playerIsPhysicallyInsideInstance(
            ServerLevel dungeonLevel,
            ServerPlayer player,
            DungeonInstanceId instanceId
    ) {
        if (player.level() != dungeonLevel) {
            return false;
        }

        return DungeonSpatialIndex.isPhysicallyInsideInstance(
                dungeonLevel,
                player.blockPosition(),
                instanceId
        );
    }

    public static boolean entityIsPhysicallyInsideInstance(
            ServerLevel dungeonLevel,
            Entity entity,
            DungeonInstanceId instanceId
    ) {
        return DungeonSpatialIndex.isPhysicallyInsideInstance(
                dungeonLevel,
                entity.blockPosition(),
                instanceId
        );
    }

    public static boolean blockBelongsToInstance(
            ServerLevel dungeonLevel,
            BlockPos pos,
            DungeonInstanceId instanceId
    ) {
        return DungeonSpatialIndex.isPhysicallyInsideInstance(
                dungeonLevel,
                pos,
                instanceId
        );
    }
}