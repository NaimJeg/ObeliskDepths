package io.github.naimjeg.obeliskdepths.worldgen.structure.generation;

import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonCellPos;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonConnectorSide;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.ResolvedDungeonPort;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DungeonCorridorCellResolver {
    private DungeonCorridorCellResolver() {
    }

    public static List<DungeonCorridorCell> resolve(DungeonGenerationPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("Dungeon generation plan is required");
        }

        Map<String, PlacedDungeonRoom> roomsById = new LinkedHashMap<>();
        for (PlacedDungeonRoom room : plan.rooms()) {
            roomsById.put(room.id(), room);
        }

        Map<DungeonCellPos, EnumSet<DungeonConnectorSide>> openings =
                new LinkedHashMap<>();
        for (RoutedDungeonConnection connection : plan.connections()) {
            List<DungeonCellPos> cells = connection.cells();
            if (cells.isEmpty()) {
                continue;
            }

            ResolvedDungeonPort fromPort = requirePort(
                    roomsById,
                    connection.from().roomId(),
                    connection.from().portId(),
                    connection.id()
            );
            ResolvedDungeonPort toPort = requirePort(
                    roomsById,
                    connection.to().roomId(),
                    connection.to().portId(),
                    connection.id()
            );
            addOpening(openings, cells.getFirst(), fromPort.facing().opposite());
            addOpening(openings, cells.getLast(), toPort.facing().opposite());

            for (int index = 1; index < cells.size(); index++) {
                DungeonCellPos previous = cells.get(index - 1);
                DungeonCellPos current = cells.get(index);
                DungeonConnectorSide direction = directionBetween(previous, current);
                addOpening(openings, previous, direction);
                addOpening(openings, current, direction.opposite());
            }
        }

        List<DungeonCorridorCell> result = new ArrayList<>();
        for (Map.Entry<DungeonCellPos, EnumSet<DungeonConnectorSide>> entry
                : openings.entrySet()) {
            result.add(new DungeonCorridorCell(
                    entry.getKey(),
                    DungeonCorridorCell.maskOf(entry.getValue())
            ));
        }
        result.sort(Comparator
                .comparingInt((DungeonCorridorCell cell) -> cell.cell().y())
                .thenComparingInt(cell -> cell.cell().x())
                .thenComparingInt(cell -> cell.cell().z())
                .thenComparingInt(DungeonCorridorCell::openingMask));
        return List.copyOf(result);
    }

    private static ResolvedDungeonPort requirePort(
            Map<String, PlacedDungeonRoom> roomsById,
            String roomId,
            String portId,
            String connectionId
    ) {
        PlacedDungeonRoom room = roomsById.get(roomId);
        if (room == null) {
            throw new IllegalArgumentException(
                    "Plan connection references unknown room: connection="
                            + connectionId
                            + " room="
                            + roomId
            );
        }
        for (ResolvedDungeonPort port : room.ports()) {
            if (port.id().equals(portId)) {
                return port;
            }
        }
        throw new IllegalArgumentException(
                "Plan connection references unknown port: connection="
                        + connectionId
                        + " room="
                        + roomId
                        + " port="
                        + portId
        );
    }

    private static void addOpening(
            Map<DungeonCellPos, EnumSet<DungeonConnectorSide>> openings,
            DungeonCellPos cell,
            DungeonConnectorSide side
    ) {
        openings.computeIfAbsent(
                cell,
                ignored -> EnumSet.noneOf(DungeonConnectorSide.class)
        ).add(side);
    }

    private static DungeonConnectorSide directionBetween(
            DungeonCellPos previous,
            DungeonCellPos current
    ) {
        int dx = current.x() - previous.x();
        int dy = current.y() - previous.y();
        int dz = current.z() - previous.z();
        int distance = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
        if (distance != 1) {
            throw new IllegalArgumentException(
                    "Corridor cells are not adjacent: "
                            + previous
                            + " -> "
                            + current
            );
        }
        if (dx > 0) {
            return DungeonConnectorSide.EAST;
        }
        if (dx < 0) {
            return DungeonConnectorSide.WEST;
        }
        if (dz > 0) {
            return DungeonConnectorSide.SOUTH;
        }
        if (dz < 0) {
            return DungeonConnectorSide.NORTH;
        }
        return dy > 0 ? DungeonConnectorSide.UP : DungeonConnectorSide.DOWN;
    }
}
