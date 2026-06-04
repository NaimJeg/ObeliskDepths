package io.github.naimjeg.obeliskdepths.worldgen.structure.piece;

import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonGraphEdgeKind;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonCellPos;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonConnectorSide;
import java.util.List;

public record DungeonRoutedCorridor(
        String edgeId,
        String fromRoomId,
        String toRoomId,
        DungeonConnectorSide fromSide,
        DungeonConnectorSide toSide,
        DungeonGraphEdgeKind kind,
        List<DungeonCellPos> path
) {
    public DungeonRoutedCorridor {
        if (edgeId == null || edgeId.isBlank()) {
            throw new IllegalArgumentException("Routed corridor edge id must be non-empty");
        }
        if (fromSide == null || toSide == null || kind == null) {
            throw new IllegalArgumentException("Routed corridor metadata is incomplete: " + edgeId);
        }
        path = List.copyOf(path);
        if (path.isEmpty()) {
            throw new IllegalArgumentException("Routed corridor path must be non-empty: " + edgeId);
        }
    }

    public int lengthCells() {
        return this.path.size();
    }
}
