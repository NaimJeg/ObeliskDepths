package io.github.naimjeg.obeliskdepths.dungeon.portal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class DungeonPortalPositionResolver {
    private static final Direction[] FALLBACK_ORDER = {
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST
    };

    private DungeonPortalPositionResolver() {
    }

    public static Optional<BlockPos> resolve(
            ServerLevel sourceLevel,
            BlockPos obeliskBottomPos,
            ServerPlayer opener
    ) {
        for (Direction direction : orderedDirections(obeliskBottomPos, opener)) {
            BlockPos anchor = obeliskBottomPos.relative(direction, 2);

            if (isValidAnchor(sourceLevel, anchor)) {
                return Optional.of(anchor.immutable());
            }
        }

        return Optional.empty();
    }

    private static List<Direction> orderedDirections(
            BlockPos obeliskBottomPos,
            ServerPlayer opener
    ) {
        Direction preferred = preferredDirection(obeliskBottomPos, opener);
        List<Direction> result = new ArrayList<>(4);
        result.add(preferred);

        for (Direction direction : FALLBACK_ORDER) {
            if (direction != preferred) {
                result.add(direction);
            }
        }

        return result;
    }

    private static Direction preferredDirection(
            BlockPos obeliskBottomPos,
            ServerPlayer opener
    ) {
        double dx = opener.getX() - (obeliskBottomPos.getX() + 0.5D);
        double dz = opener.getZ() - (obeliskBottomPos.getZ() + 0.5D);

        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0.0D ? Direction.EAST : Direction.WEST;
        }

        return dz >= 0.0D ? Direction.SOUTH : Direction.NORTH;
    }

    private static boolean isValidAnchor(
            ServerLevel sourceLevel,
            BlockPos anchor
    ) {
        BlockPos floor = anchor.below();
        BlockState floorState = sourceLevel.getBlockState(floor);

        if (!floorState.isFaceSturdy(sourceLevel, floor, Direction.UP)) {
            return false;
        }

        for (int y = 0; y <= 2; y++) {
            BlockPos pos = anchor.above(y);

            if (!sourceLevel.getBlockState(pos).canBeReplaced()) {
                return false;
            }
        }

        AABB trigger = AABB.ofSize(
                Vec3.atBottomCenterOf(anchor).add(0.0D, 1.25D, 0.0D),
                1.5D,
                2.5D,
                1.5D
        );

        return sourceLevel.getEntities(
                EntityTypeTest.forClass(net.minecraft.world.entity.Entity.class),
                trigger,
                entity -> entity.isAlive()
        ).isEmpty();
    }
}
