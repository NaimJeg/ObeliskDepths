package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

import io.github.naimjeg.obeliskdepths.dungeon.geometry.*;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomRotation;
import net.minecraft.core.BlockPos;

/**
 * Exact untransformed NBT template dimensions in block space.
 */
public record DungeonTemplateGeometry(
        int sizeX,
        int sizeY,
        int sizeZ
) {
    public DungeonTemplateGeometry {
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
            throw new IllegalArgumentException(
                    "Template dimensions must be positive: "
                            + sizeX
                            + "x"
                            + sizeY
                            + "x"
                            + sizeZ
            );
        }
    }

    public int routingCellsX() {
        return DungeonLayoutConstants.blockSizeToCellCountX(this.sizeX);
    }

    public int routingCellsY() {
        return DungeonLayoutConstants.blockSizeToCellCountY(this.sizeY);
    }

    public int routingCellsZ() {
        return DungeonLayoutConstants.blockSizeToCellCountZ(this.sizeZ);
    }

    public DungeonTemplateGeometry transformed(DungeonRoomRotation rotation) {
        DungeonRoomRotation resolved = rotation == null
                ? DungeonRoomRotation.NONE
                : rotation;

        return switch (resolved) {
            case NONE, CLOCKWISE_180 -> this;
            case CLOCKWISE_90, COUNTERCLOCKWISE_90 ->
                    new DungeonTemplateGeometry(
                            this.sizeZ,
                            this.sizeY,
                            this.sizeX
                    );
        };
    }

    public DungeonBlockBox boxAt(BlockPos exactWorldOrigin) {
        return DungeonBlockBox.fromMinAndSize(
                exactWorldOrigin,
                this.sizeX,
                this.sizeY,
                this.sizeZ
        );
    }
}
