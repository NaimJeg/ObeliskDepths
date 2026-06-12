package io.github.naimjeg.obeliskdepths.worldgen.structure.piece;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public record DungeonPiecePlan(
        BlockPos layoutOrigin,
        BoundingBox siteBounds,
        List<DungeonPieceMetadata> pieces
) {
    public DungeonPiecePlan {
        if (layoutOrigin == null || siteBounds == null) {
            throw new IllegalArgumentException("Dungeon piece plan origin and bounds must be present");
        }

        pieces = List.copyOf(pieces);

        if (pieces.isEmpty()) {
            throw new IllegalArgumentException("Dungeon piece plan requires at least one room or corridor piece");
        }
    }

    public long roomCount() {
        return this.pieces.stream()
                .filter(piece -> piece.role().isRoom())
                .count();
    }

    public long corridorCount() {
        return this.pieces.stream()
                .filter(piece -> piece.role() == io.github.naimjeg.obeliskdepths.worldgen.structure.ObeliskDungeonPieceRole.CORRIDOR)
                .count();
    }
}
