package io.github.naimjeg.obeliskdepths.worldgen.structure;

import io.github.naimjeg.obeliskdepths.registry.ModBlocks;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class DungeonFoundationPlacer {
    public static final int MAX_DEPTH = 12;
    public static final int SUPPORT_SPACING_BLOCKS = 8;

    private DungeonFoundationPlacer() {
    }

    public static int placeForBounds(
            WorldGenLevel level,
            BoundingBox chunkBounds,
            BoundingBox floorBounds,
            BlockState foundationState
    ) {
        return place(level, chunkBounds, supportPositions(floorBounds), foundationState);
    }

    public static int place(
            WorldGenLevel level,
            BoundingBox chunkBounds,
            List<BlockPos> floorSupportPositions,
            BlockState foundationState
    ) {
        int written = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (BlockPos support : floorSupportPositions) {
            for (int depth = 1; depth <= MAX_DEPTH; depth++) {
                pos.set(support.getX(), support.getY() - depth, support.getZ());
                if (level.isOutsideBuildHeight(pos) || !chunkBounds.isInside(pos)) {
                    break;
                }

                BlockState current = level.getBlockState(pos);
                if (isExistingDungeonBlock(current)
                        || isProtected(current)
                        || level.getBlockEntity(pos) != null) {
                    break;
                }
                if (isSturdySupport(level, pos, current)) {
                    break;
                }
                if (!canReplaceForFoundation(current)) {
                    break;
                }

                level.setBlock(pos, foundationState, 2);
                written++;
            }
        }

        return written;
    }

    public static List<BlockPos> supportPositions(BoundingBox floorBounds) {
        if (floorBounds == null) {
            return List.of();
        }

        Set<BlockPos> positions = new LinkedHashSet<>();
        int y = floorBounds.minY();
        addPerimeter(positions, floorBounds, y);
        positions.add(new BlockPos(
                floorBounds.getCenter().getX(),
                y,
                floorBounds.getCenter().getZ()
        ));
        return List.copyOf(positions);
    }

    private static void addPerimeter(
            Set<BlockPos> positions,
            BoundingBox bounds,
            int y
    ) {
        for (int x = bounds.minX(); x <= bounds.maxX(); x += SUPPORT_SPACING_BLOCKS) {
            positions.add(new BlockPos(x, y, bounds.minZ()));
            positions.add(new BlockPos(x, y, bounds.maxZ()));
        }
        for (int z = bounds.minZ(); z <= bounds.maxZ(); z += SUPPORT_SPACING_BLOCKS) {
            positions.add(new BlockPos(bounds.minX(), y, z));
            positions.add(new BlockPos(bounds.maxX(), y, z));
        }
        positions.add(new BlockPos(bounds.maxX(), y, bounds.minZ()));
        positions.add(new BlockPos(bounds.maxX(), y, bounds.maxZ()));
        positions.add(new BlockPos(bounds.minX(), y, bounds.maxZ()));
    }

    private static boolean isSturdySupport(
            WorldGenLevel level,
            BlockPos pos,
            BlockState state
    ) {
        return !state.isAir()
                && state.getFluidState().isEmpty()
                && !state.canBeReplaced()
                && state.isFaceSturdy(level, pos, Direction.UP);
    }

    static boolean canReplaceForFoundation(BlockState state) {
        return state.isAir()
                || !state.getFluidState().isEmpty()
                || state.canBeReplaced()
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)
                || state.is(Blocks.SEAGRASS)
                || state.is(Blocks.TALL_SEAGRASS)
                || state.is(Blocks.VINE)
                || state.is(Blocks.BROWN_MUSHROOM)
                || state.is(Blocks.RED_MUSHROOM);
    }

    private static boolean isProtected(BlockState state) {
        return state.is(Blocks.BEDROCK)
                || state.hasBlockEntity()
                || state.is(ModBlocks.OBELISK.get())
                || state.is(ModBlocks.OBELISK_CHEST.get());
    }

    private static boolean isExistingDungeonBlock(BlockState state) {
        return state.is(ModBlocks.DUNGEON_BRICKS.get())
                || state.is(ModBlocks.DUNGEON_CRACKED_BRICKS.get())
                || state.is(ModBlocks.DUNGEON_STONE.get())
                || state.is(ModBlocks.DUNGEON_TILES.get())
                || state.is(ModBlocks.DUNGEON_CRACKED_TILES.get())
                || state.is(ModBlocks.REINFORCED_DUNGEON_STONE.get())
                || state.is(ModBlocks.DUNGEON_LAMP.get());
    }
}
