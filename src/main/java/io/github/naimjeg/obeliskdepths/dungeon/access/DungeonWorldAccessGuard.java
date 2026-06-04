package io.github.naimjeg.obeliskdepths.dungeon.access;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonTracker;
import io.github.naimjeg.obeliskdepths.dungeon.spatial.DungeonSpatialIndex;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public final class DungeonWorldAccessGuard {
    private DungeonWorldAccessGuard() {
    }

    public static boolean canEditBlock(
            ServerPlayer player,
            ServerLevel level,
            BlockPos pos
    ) {
        if (!level.dimension().equals(ModDimensions.OBELISK_DEPTHS_LEVEL)) {
            return true;
        }

        if (level.getServer().getPlayerList().isOp(player.nameAndId())) {
            return true;
        }

        Optional<DungeonInstanceId> playerInstance =
                PlayerDungeonTracker.currentInstanceId(player);

        if (playerInstance.isEmpty()) {
            return false;
        }

        Optional<DungeonInstanceId> physicalOwner =
                DungeonSpatialIndex.findPhysicalOwnerAt(level, pos);

        if (physicalOwner.isEmpty()) {
            return false;
        }

        return physicalOwner.get().equals(playerInstance.get());
    }

    public static boolean canUseDungeonBlock(
            ServerPlayer player,
            ServerLevel level,
            BlockPos pos
    ) {
        return canEditBlock(player, level, pos);
    }
}
