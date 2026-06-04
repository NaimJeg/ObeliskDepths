package io.github.naimjeg.obeliskdepths.dungeon.geometry;

public final class DungeonLayoutConstants {
    /**
     * Coarse routing-grid volume used for candidate placement, pathfinding, and
     * spatial indexing. Cells are always 8x8x8 blocks. Authored NBT templates
     * keep their exact block dimensions, a room may occupy multiple cells, and
     * unused space inside a reserved routing cell is intentional.
     */
    public static final int CELL_SIZE_X = 8;
    public static final int CELL_SIZE_Y = 8;
    public static final int CELL_SIZE_Z = 8;

    /**
     * Compatibility value for data types that only need an identifier for the
     * routing grid. Do not use this for block conversion.
     */
    public static final int CELL_SIZE = 8;

    private DungeonLayoutConstants() {
    }

    public static int cellToBlockX(int cellX) {
        return Math.multiplyExact(cellX, CELL_SIZE_X);
    }

    public static int cellToBlockY(int cellY) {
        return Math.multiplyExact(cellY, CELL_SIZE_Y);
    }

    public static int cellToBlockZ(int cellZ) {
        return Math.multiplyExact(cellZ, CELL_SIZE_Z);
    }

    public static int blockToCellFloorX(int blockX) {
        return Math.floorDiv(blockX, CELL_SIZE_X);
    }

    public static int blockToCellFloorY(int blockY) {
        return Math.floorDiv(blockY, CELL_SIZE_Y);
    }

    public static int blockToCellFloorZ(int blockZ) {
        return Math.floorDiv(blockZ, CELL_SIZE_Z);
    }

    public static int blockSizeToCellCountX(int blockSize) {
        return positiveCeilDiv(blockSize, CELL_SIZE_X);
    }

    public static int blockSizeToCellCountY(int blockSize) {
        return positiveCeilDiv(blockSize, CELL_SIZE_Y);
    }

    public static int blockSizeToCellCountZ(int blockSize) {
        return positiveCeilDiv(blockSize, CELL_SIZE_Z);
    }

    private static int positiveCeilDiv(int value, int divisor) {
        if (value <= 0) {
            throw new IllegalArgumentException(
                    "Block size must be positive: " + value
            );
        }

        return Math.floorDiv(value - 1, divisor) + 1;
    }
}
