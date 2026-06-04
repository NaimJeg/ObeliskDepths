package io.github.naimjeg.obeliskdepths.dungeon.geometry;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Exact block-space bounding box with inclusive minimum and exclusive maximum
 * coordinates. Use this for template dimensions, collision, and placement;
 * convert to {@link DungeonCellBox} only for routing broad-phase indexing.
 */
public record DungeonBlockBox(
        int minX,
        int minY,
        int minZ,
        int maxXExclusive,
        int maxYExclusive,
        int maxZExclusive
) {
    public DungeonBlockBox {
        if (maxXExclusive <= minX
                || maxYExclusive <= minY
                || maxZExclusive <= minZ) {
            throw new IllegalArgumentException(
                    "Block box must have positive size: "
                            + minX
                            + ","
                            + minY
                            + ","
                            + minZ
                            + " -> "
                            + maxXExclusive
                            + ","
                            + maxYExclusive
                            + ","
                            + maxZExclusive
            );
        }
    }

    public static DungeonBlockBox fromMinAndSize(
            BlockPos min,
            int sizeX,
            int sizeY,
            int sizeZ
    ) {
        if (min == null) {
            throw new IllegalArgumentException("Block box minimum is required");
        }
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            throw new IllegalArgumentException(
                    "Block box sizes must be positive: "
                            + sizeX
                            + "x"
                            + sizeY
                            + "x"
                            + sizeZ
            );
        }

        return new DungeonBlockBox(
                min.getX(),
                min.getY(),
                min.getZ(),
                Math.addExact(min.getX(), sizeX),
                Math.addExact(min.getY(), sizeY),
                Math.addExact(min.getZ(), sizeZ)
        );
    }

    public static DungeonBlockBox fromInclusive(BoundingBox box) {
        if (box == null) {
            throw new IllegalArgumentException("Bounding box is required");
        }

        return new DungeonBlockBox(
                box.minX(),
                box.minY(),
                box.minZ(),
                Math.addExact(box.maxX(), 1),
                Math.addExact(box.maxY(), 1),
                Math.addExact(box.maxZ(), 1)
        );
    }

    public int width() {
        return this.maxXExclusive - this.minX;
    }

    public int height() {
        return this.maxYExclusive - this.minY;
    }

    public int depth() {
        return this.maxZExclusive - this.minZ;
    }

    public BlockPos minPos() {
        return new BlockPos(this.minX, this.minY, this.minZ);
    }

    public DungeonBlockBox translate(int dx, int dy, int dz) {
        return new DungeonBlockBox(
                Math.addExact(this.minX, dx),
                Math.addExact(this.minY, dy),
                Math.addExact(this.minZ, dz),
                Math.addExact(this.maxXExclusive, dx),
                Math.addExact(this.maxYExclusive, dy),
                Math.addExact(this.maxZExclusive, dz)
        );
    }

    public DungeonBlockBox expand(int blocks) {
        if (blocks < 0) {
            throw new IllegalArgumentException(
                    "Expansion must be non-negative: " + blocks
            );
        }

        return new DungeonBlockBox(
                Math.subtractExact(this.minX, blocks),
                Math.subtractExact(this.minY, blocks),
                Math.subtractExact(this.minZ, blocks),
                Math.addExact(this.maxXExclusive, blocks),
                Math.addExact(this.maxYExclusive, blocks),
                Math.addExact(this.maxZExclusive, blocks)
        );
    }

    public boolean intersects(DungeonBlockBox other) {
        return this.minX < other.maxXExclusive
                && this.maxXExclusive > other.minX
                && this.minY < other.maxYExclusive
                && this.maxYExclusive > other.minY
                && this.minZ < other.maxZExclusive
                && this.maxZExclusive > other.minZ;
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= this.minX
                && pos.getX() < this.maxXExclusive
                && pos.getY() >= this.minY
                && pos.getY() < this.maxYExclusive
                && pos.getZ() >= this.minZ
                && pos.getZ() < this.maxZExclusive;
    }

    public BoundingBox toInclusiveBoundingBox() {
        return new BoundingBox(
                this.minX,
                this.minY,
                this.minZ,
                this.maxXExclusive - 1,
                this.maxYExclusive - 1,
                this.maxZExclusive - 1
        );
    }

    public DungeonCellBox toRoutingCellBox(BlockPos layoutOrigin) {
        int relativeMinX = Math.subtractExact(this.minX, layoutOrigin.getX());
        int relativeMinY = Math.subtractExact(this.minY, layoutOrigin.getY());
        int relativeMinZ = Math.subtractExact(this.minZ, layoutOrigin.getZ());
        int relativeLastX = Math.subtractExact(
                this.maxXExclusive - 1,
                layoutOrigin.getX()
        );
        int relativeLastY = Math.subtractExact(
                this.maxYExclusive - 1,
                layoutOrigin.getY()
        );
        int relativeLastZ = Math.subtractExact(
                this.maxZExclusive - 1,
                layoutOrigin.getZ()
        );

        int minCellX = DungeonLayoutConstants.blockToCellFloorX(relativeMinX);
        int minCellY = DungeonLayoutConstants.blockToCellFloorY(relativeMinY);
        int minCellZ = DungeonLayoutConstants.blockToCellFloorZ(relativeMinZ);
        int maxCellX = DungeonLayoutConstants.blockToCellFloorX(relativeLastX) + 1;
        int maxCellY = DungeonLayoutConstants.blockToCellFloorY(relativeLastY) + 1;
        int maxCellZ = DungeonLayoutConstants.blockToCellFloorZ(relativeLastZ) + 1;

        return new DungeonCellBox(
                minCellX,
                minCellY,
                minCellZ,
                maxCellX - minCellX,
                maxCellY - minCellY,
                maxCellZ - minCellZ
        );
    }
}
