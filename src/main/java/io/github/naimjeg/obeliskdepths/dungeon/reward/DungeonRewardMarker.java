package io.github.naimjeg.obeliskdepths.dungeon.reward;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record DungeonRewardMarker(
        BlockPos mainPos,
        Direction facing
) {
}
