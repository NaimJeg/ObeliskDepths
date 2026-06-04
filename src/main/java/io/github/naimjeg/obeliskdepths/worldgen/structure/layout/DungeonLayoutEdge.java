package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

import io.github.naimjeg.obeliskdepths.dungeon.geometry.*;

import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonGraphEdgeKind;
import java.util.List;

public record DungeonLayoutEdge(
        String id,
        String fromRoomId,
        String toRoomId,
        DungeonConnectorSide fromSide,
        DungeonConnectorSide toSide,
        int widthCells,
        DungeonGraphEdgeKind kind,
        List<DungeonCellPos> plannedPath
) {
    public DungeonLayoutEdge {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Layout edge id must be non-empty");
        }

        if (fromRoomId == null || fromRoomId.isBlank()) {
            throw new IllegalArgumentException("Layout edge missing from room: " + id);
        }

        if (toRoomId == null || toRoomId.isBlank()) {
            throw new IllegalArgumentException("Layout edge missing to room: " + id);
        }

        if (fromRoomId.equals(toRoomId)) {
            throw new IllegalArgumentException("Layout edge connects a room to itself: " + id);
        }

        if (fromSide == null || toSide == null) {
            throw new IllegalArgumentException("Layout edge sides must be present: " + id);
        }

        if (widthCells <= 0) {
            throw new IllegalArgumentException("Layout edge width must be positive: " + id);
        }

        if (kind == null) {
            throw new IllegalArgumentException("Layout edge kind must be present: " + id);
        }

        plannedPath = plannedPath == null ? List.of() : List.copyOf(plannedPath);
        validatePlannedPath(id, plannedPath);
    }

    public boolean directOppositeConnection() {
        return this.fromSide.opposite() == this.toSide;
    }

    private static void validatePlannedPath(
            String edgeId,
            List<DungeonCellPos> path
    ) {
        for (int index = 1; index < path.size(); index++) {
            DungeonCellPos previous = path.get(index - 1);
            DungeonCellPos current = path.get(index);
            int distance = Math.abs(previous.x() - current.x())
                    + Math.abs(previous.y() - current.y())
                    + Math.abs(previous.z() - current.z());

            if (distance != 1) {
                throw new IllegalArgumentException(
                        "Layout edge planned path is not contiguous: edge="
                                + edgeId
                                + " previous="
                                + previous
                                + " current="
                                + current
                );
            }
        }
    }
}
