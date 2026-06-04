package io.github.naimjeg.obeliskdepths.dungeon.geometry;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public record DungeonCellBox(
        int minX,
        int minY,
        int minZ,
        int sizeX,
        int sizeY,
        int sizeZ
) {
    public DungeonCellBox {
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            throw new IllegalArgumentException("Dungeon cell box sizes must be positive");
        }
    }

    public int maxXExclusive() {
        return this.minX + this.sizeX;
    }

    public int maxYExclusive() {
        return this.minY + this.sizeY;
    }

    public int maxZExclusive() {
        return this.minZ + this.sizeZ;
    }

    public boolean intersects(DungeonCellBox other) {
        return this.minX < other.maxXExclusive()
                && this.maxXExclusive() > other.minX
                && this.minY < other.maxYExclusive()
                && this.maxYExclusive() > other.minY
                && this.minZ < other.maxZExclusive()
                && this.maxZExclusive() > other.minZ;
    }

    public boolean contains(DungeonCellPos pos) {
        return pos.x() >= this.minX
                && pos.x() < this.maxXExclusive()
                && pos.y() >= this.minY
                && pos.y() < this.maxYExclusive()
                && pos.z() >= this.minZ
                && pos.z() < this.maxZExclusive();
    }

    public BoundingBox toBlockBounds(BlockPos layoutOrigin) {
        int minBlockX = Math.addExact(
                layoutOrigin.getX(),
                DungeonLayoutConstants.cellToBlockX(this.minX)
        );
        int minBlockY = Math.addExact(
                layoutOrigin.getY(),
                DungeonLayoutConstants.cellToBlockY(this.minY)
        );
        int minBlockZ = Math.addExact(
                layoutOrigin.getZ(),
                DungeonLayoutConstants.cellToBlockZ(this.minZ)
        );
        int sizeBlocksX = DungeonLayoutConstants.cellToBlockX(this.sizeX);
        int sizeBlocksY = DungeonLayoutConstants.cellToBlockY(this.sizeY);
        int sizeBlocksZ = DungeonLayoutConstants.cellToBlockZ(this.sizeZ);

        return new BoundingBox(
                minBlockX,
                minBlockY,
                minBlockZ,
                Math.addExact(minBlockX, sizeBlocksX) - 1,
                Math.addExact(minBlockY, sizeBlocksY) - 1,
                Math.addExact(minBlockZ, sizeBlocksZ) - 1
        );
    }

    public DungeonCellBox expanded(int cells) {
        return new DungeonCellBox(
                this.minX - cells,
                this.minY - cells,
                this.minZ - cells,
                this.sizeX + cells * 2,
                this.sizeY + cells * 2,
                this.sizeZ + cells * 2
        );
    }
}
