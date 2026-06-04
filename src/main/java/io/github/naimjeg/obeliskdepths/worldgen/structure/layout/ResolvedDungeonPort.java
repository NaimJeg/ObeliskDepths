package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

import io.github.naimjeg.obeliskdepths.dungeon.geometry.*;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/**
 * A room port after rotation and mirroring have been applied. Coordinates are
 * template-local block/cell coordinates in the transformed room orientation.
 */
public record ResolvedDungeonPort(
        String id,
        DungeonConnectorSide facing,
        DungeonCellPos boundaryCell,
        BlockPos openingMin,
        Identifier connectorType,
        int widthBlocks,
        int heightBlocks,
        boolean required
) {
    public ResolvedDungeonPort {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Resolved dungeon port id must be non-empty"
            );
        }
        if (facing == null || boundaryCell == null || openingMin == null
                || connectorType == null) {
            throw new IllegalArgumentException(
                    "Resolved dungeon port metadata is incomplete: " + id
            );
        }
        if (widthBlocks <= 0 || heightBlocks <= 0) {
            throw new IllegalArgumentException(
                    "Resolved dungeon port opening dimensions must be positive: "
                            + id
            );
        }
    }

    public DungeonCellPos outsideCell(DungeonCellPos roomOrigin) {
        return new DungeonCellPos(
                roomOrigin.x() + this.boundaryCell.x() + this.facing.dx(),
                roomOrigin.y() + this.boundaryCell.y() + this.facing.dy(),
                roomOrigin.z() + this.boundaryCell.z() + this.facing.dz()
        );
    }
}
