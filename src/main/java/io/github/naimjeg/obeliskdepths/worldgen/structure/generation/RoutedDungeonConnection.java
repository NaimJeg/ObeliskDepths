package io.github.naimjeg.obeliskdepths.worldgen.structure.generation;

import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonGraphEdgeKind;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonCellPos;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonPortReference;
import java.util.List;

public record RoutedDungeonConnection(
        String id,
        DungeonPortReference from,
        DungeonPortReference to,
        DungeonGraphEdgeKind kind,
        List<DungeonCellPos> cells
) {
    public RoutedDungeonConnection {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Routed connection id must be non-empty"
            );
        }
        if (from == null || to == null || kind == null) {
            throw new IllegalArgumentException(
                    "Routed connection metadata is incomplete: " + id
            );
        }
        cells = cells == null ? List.of() : List.copyOf(cells);
        if (cells.isEmpty()) {
            throw new IllegalArgumentException(
                    "Routed connection path must be non-empty: " + id
            );
        }
        for (int index = 1; index < cells.size(); index++) {
            DungeonCellPos previous = cells.get(index - 1);
            DungeonCellPos current = cells.get(index);
            int distance = Math.abs(previous.x() - current.x())
                    + Math.abs(previous.y() - current.y())
                    + Math.abs(previous.z() - current.z());
            if (distance != 1) {
                throw new IllegalArgumentException(
                        "Routed connection is not contiguous: "
                                + id
                                + " previous="
                                + previous
                                + " current="
                                + current
                );
            }
        }
    }
}
