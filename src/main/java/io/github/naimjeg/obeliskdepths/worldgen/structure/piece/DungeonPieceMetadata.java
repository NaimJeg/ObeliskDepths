package io.github.naimjeg.obeliskdepths.worldgen.structure.piece;

import io.github.naimjeg.obeliskdepths.worldgen.structure.ObeliskDungeonPieceRole;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public record DungeonPieceMetadata(
        ObeliskDungeonPieceRole role,
        String id,
        BlockPos anchor,
        BoundingBox bounds
) {
    public DungeonPieceMetadata {
        if (role == null) {
            throw new IllegalArgumentException("Dungeon piece role must be present: " + id);
        }

        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Dungeon piece id must be non-empty");
        }

        if (anchor == null || bounds == null) {
            throw new IllegalArgumentException("Dungeon piece anchor and bounds must be present: " + id);
        }
    }
}
