package io.github.naimjeg.obeliskdepths.dungeon.site;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class DungeonSafeSpawnResolver {
    private DungeonSafeSpawnResolver() {
    }

    public static Optional<Vec3> resolvePrimaryEntrySpawn(
            ServerLevel level,
            DungeonSite site
    ) {
        return site.primaryEntryRoom()
                .filter(room -> room.type() == DungeonRoomType.START)
                .flatMap(room -> resolveInRoom(level, room));
    }

    private static Optional<Vec3> resolveInRoom(
            ServerLevel level,
            DungeonGeneratedRoom room
    ) {
        BlockPos anchor = room.anchorPos();
        int feetY = anchor.getY();

        return positionsInRoom(room, feetY)
                .stream()
                .sorted(Comparator
                        .comparingInt((BlockPos pos) -> manhattan(pos, anchor))
                        .thenComparingInt(BlockPos::getX)
                        .thenComparingInt(BlockPos::getZ))
                .filter(pos -> validSpawn(level, room, pos))
                .findFirst()
                .map(Vec3::atCenterOf);
    }

    private static java.util.List<BlockPos> positionsInRoom(
            DungeonGeneratedRoom room,
            int feetY
    ) {
        java.util.ArrayList<BlockPos> positions = new java.util.ArrayList<>();

        for (int x = room.bounds().minX() + 1; x <= room.bounds().maxX() - 1; x++) {
            for (int z = room.bounds().minZ() + 1; z <= room.bounds().maxZ() - 1; z++) {
                BlockPos pos = new BlockPos(x, feetY, z);

                if (room.contains(pos)) {
                    positions.add(pos);
                }
            }
        }

        return positions;
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
        return state.isAir()
                || state.canBeReplaced();
    }

    private static int manhattan(
            BlockPos first,
            BlockPos second
    ) {
        return Math.abs(first.getX() - second.getX())
                + Math.abs(first.getY() - second.getY())
                + Math.abs(first.getZ() - second.getZ());
    }
}
