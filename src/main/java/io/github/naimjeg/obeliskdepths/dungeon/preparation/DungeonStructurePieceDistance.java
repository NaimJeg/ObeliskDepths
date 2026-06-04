package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import net.minecraft.world.level.levelgen.structure.BoundingBox;

public record DungeonStructurePieceDistance(
        String label,
        BoundingBox blockBounds,
        DungeonStructureDistanceReport.ChunkBounds chunkBounds,
        int chebyshevDistanceFromStart,
        boolean primaryEntry
) {
    public DungeonStructurePieceDistance {
        if (label == null || label.isBlank()) {
            label = "unknown";
        }
        if (blockBounds == null) {
            throw new IllegalArgumentException("Piece block bounds must be present.");
        }
        if (chunkBounds == null) {
            throw new IllegalArgumentException("Piece chunk bounds must be present.");
        }
        if (chebyshevDistanceFromStart < 0) {
            throw new IllegalArgumentException(
                    "Piece distance must be non-negative: "
                            + chebyshevDistanceFromStart
            );
        }
    }
}
