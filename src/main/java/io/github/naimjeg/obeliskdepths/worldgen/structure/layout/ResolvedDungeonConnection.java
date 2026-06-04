package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

import io.github.naimjeg.obeliskdepths.dungeon.geometry.*;

import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonGraphEdgeKind;

import java.util.List;

public record ResolvedDungeonConnection(
        String id,
        DungeonPortReference from,
        DungeonPortReference to,
        DungeonGraphEdgeKind kind,
        List<DungeonCellPos> routeCells
) {
    public ResolvedDungeonConnection {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Resolved dungeon connection id must be non-empty"
            );
        }
        if (from == null || to == null || kind == null) {
            throw new IllegalArgumentException(
                    "Resolved dungeon connection metadata is incomplete: " + id
            );
        }
        routeCells = routeCells == null ? List.of() : List.copyOf(routeCells);
        validateContiguous(id, routeCells);
    }

    private static void validateContiguous(
            String connectionId,
            List<DungeonCellPos> routeCells
    ) {
        for (int index = 1; index < routeCells.size(); index++) {
            DungeonCellPos previous = routeCells.get(index - 1);
            DungeonCellPos current = routeCells.get(index);
            int distance = Math.abs(previous.x() - current.x())
                    + Math.abs(previous.y() - current.y())
                    + Math.abs(previous.z() - current.z());
            if (distance != 1) {
                throw new IllegalArgumentException(
                        "Resolved connection route is not contiguous: "
                                + connectionId
                                + " previous="
                                + previous
                                + " current="
                                + current
                );
            }
        }
    }
}
