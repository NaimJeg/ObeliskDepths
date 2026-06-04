package io.github.naimjeg.obeliskdepths.dungeon.encounter;

import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonGeneratedRoom;
import io.github.naimjeg.obeliskdepths.dungeon.territory.DungeonBounds;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public final class DungeonSpawnPositionResolver {
    private static final int[][] OFFSETS = {
            {0, 0},
            {2, 0},
            {-2, 0},
            {0, 2},
            {0, -2},
            {3, 2},
            {-3, 2},
            {3, -2},
            {-3, -2},
            {4, 0},
            {-4, 0},
            {0, 4},
            {0, -4}
    };

    private DungeonSpawnPositionResolver() {
    }

    public static Optional<BlockPos> findSpawnPos(
            DungeonGeneratedRoom room,
            ServerLevel level,
            int sequence
    ) {
        for (int i = 0; i < OFFSETS.length; i++) {
            int[] offset = OFFSETS[(sequence + i) % OFFSETS.length];
            BlockPos base = room.anchorPos().offset(offset[0], 0, offset[1]);

            for (int yOffset = 0; yOffset >= -2; yOffset--) {
                BlockPos candidate = base.offset(0, yOffset, 0);

                if (isValidSpawnPos(room.bounds(), level, candidate)) {
                    return Optional.of(candidate);
                }
            }

            for (int yOffset = 1; yOffset <= 2; yOffset++) {
                BlockPos candidate = base.offset(0, yOffset, 0);

                if (isValidSpawnPos(room.bounds(), level, candidate)) {
                    return Optional.of(candidate);
                }
            }
        }

        return Optional.empty();
    }

    private static boolean isValidSpawnPos(
            DungeonBounds bounds,
            ServerLevel level,
            BlockPos pos
    ) {
        if (!bounds.contains(pos) || !bounds.contains(pos.above())) {
            return false;
        }

        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        BlockState floor = level.getBlockState(pos.below());

        return feet.isAir()
                && head.isAir()
                && floor.isFaceSturdy(level, pos.below(), Direction.UP);
    }
}
