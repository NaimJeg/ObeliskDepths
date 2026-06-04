package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

import io.github.naimjeg.obeliskdepths.dungeon.geometry.*;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraph;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphEdge;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonGraphEdgeKind;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public final class DungeonResolvedTopologyValidator {
    private DungeonResolvedTopologyValidator() {
    }

    public record EdgeDescriptor(
            String id,
            String firstRoomId,
            String secondRoomId,
            DungeonGraphEdgeKind kind
    ) {
    }

    public static void validateProcedural(
            DungeonGraph graph,
            DungeonLayoutPlan layout,
            ResolvedDungeonLayout resolved,
            int layoutAttempt
    ) {
        Set<String> graphRoomIds = graph.nodes()
                .stream()
                .map(DungeonGraphNode::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> layoutRoomIds = layout.nodes()
                .stream()
                .map(DungeonLayoutNode::roomId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> resolvedRoomIds = resolved.rooms()
                .stream()
                .map(ResolvedDungeonRoom::roomId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        List<String> failures = new ArrayList<>();
        addSetFailure(
                failures,
                "layoutRoomsVsGraph",
                graphRoomIds,
                layoutRoomIds
        );
        addSetFailure(
                failures,
                "resolvedRoomsVsGraph",
                graphRoomIds,
                resolvedRoomIds
        );

        Map<String, EdgeDescriptor> graphEdges =
                graphEdgeDescriptors(graph, failures, layoutAttempt);
        Map<String, EdgeDescriptor> layoutEdges =
                layoutEdgeDescriptors(layout, failures, layoutAttempt);
        Map<String, EdgeDescriptor> resolvedConnections =
                resolvedConnectionDescriptors(resolved, failures, layoutAttempt);
        validateEdgeStage(
                "graph-to-layout",
                graphEdges,
                layoutEdges,
                failures,
                layoutAttempt
        );
        validateEdgeStage(
                "layout-to-resolved",
                layoutEdges,
                resolvedConnections,
                failures,
                layoutAttempt
        );

        if (resolved.connections().size() != layout.edges().size()) {
            failures.add("connectionCount expected="
                    + layout.edges().size()
                    + " actual="
                    + resolved.connections().size());
        }

        for (ResolvedDungeonConnection actual : resolved.connections()) {
            if (actual.routeCells().isEmpty()) {
                failures.add("emptyRoute id=" + actual.id());
            } else if (!contiguous(actual.routeCells())) {
                failures.add("nonContiguousRoute id=" + actual.id());
            } else {
                ResolvedDungeonRoom fromRoom =
                        resolved.requireRoom(actual.from().roomId());
                ResolvedDungeonRoom toRoom =
                        resolved.requireRoom(actual.to().roomId());
                DungeonCellPos expectedStart =
                        fromRoom.requirePort(actual.from().portId())
                                .outsideCell(fromRoom.cellOrigin());
                DungeonCellPos expectedEnd =
                        toRoom.requirePort(actual.to().portId())
                                .outsideCell(toRoom.cellOrigin());
                if (!actual.routeCells().getFirst().equals(expectedStart)
                        || !actual.routeCells().getLast().equals(expectedEnd)) {
                    failures.add("routeEndpointMismatch id="
                            + actual.id()
                            + " expectedStart="
                            + expectedStart
                            + " actualStart="
                            + actual.routeCells().getFirst()
                            + " expectedEnd="
                            + expectedEnd
                            + " actualEnd="
                            + actual.routeCells().getLast());
                }
            }
        }

        Map<String, List<String>> adjacency = adjacency(
                resolved.rooms(),
                resolved.connections()
        );
        int componentCount = componentCount(adjacency);
        if (componentCount != 1) {
            failures.add("resolvedComponents expected=1 actual="
                    + componentCount);
        }

        for (DungeonGraphNode node : graph.nodes()) {
            if (node.type() == DungeonRoomType.START
                    && !reaches(adjacency, node.id(), graph.rootNodeId())) {
                failures.add("startCannotReachBoss start="
                        + node.id()
                        + " boss="
                        + graph.rootNodeId());
            }
        }

        int graphCycleRank = cycleRank(
                graph.nodes().size(),
                graph.edges().size(),
                1
        );
        int resolvedCycleRank = cycleRank(
                resolved.rooms().size(),
                resolved.connections().size(),
                componentCount
        );
        if (graphCycleRank < 1) {
            failures.add("graphCycleRank expectedAtLeast=1 actual="
                    + graphCycleRank);
        }
        if (resolvedCycleRank < graphCycleRank) {
            failures.add("resolvedCycleRank expectedAtLeastGraph="
                    + graphCycleRank
                    + " actual="
                    + resolvedCycleRank);
        }
        if (resolvedCycleRank < 1) {
            failures.add("resolvedCycleRank expectedAtLeast=1 actual="
                    + resolvedCycleRank);
        }

        if (!failures.isEmpty()) {
            throw new DungeonLayoutGenerationException(
                    "Resolved procedural dungeon topology validation failed: "
                            + "layoutAttempt="
                            + layoutAttempt
                            + " missingEdgeIds="
                            + missing(layoutEdges.keySet(),
                            resolvedConnections.keySet())
                            + " extraEdgeIds="
                            + extra(layoutEdges.keySet(),
                            resolvedConnections.keySet())
                            + " expectedCycleRank="
                            + graphCycleRank
                            + " actualCycleRank="
                            + resolvedCycleRank
                            + " failures="
                            + failures
            );
        }
    }

    public static int cycleRank(
            int vertexCount,
            int edgeCount,
            int connectedComponentCount
    ) {
        return edgeCount - vertexCount + connectedComponentCount;
    }

    private static Map<String, EdgeDescriptor> graphEdgeDescriptors(
            DungeonGraph graph,
            List<String> failures,
            int layoutAttempt
    ) {
        Map<String, EdgeDescriptor> descriptors = new LinkedHashMap<>();
        Set<String> graphIds = new HashSet<>();
        for (DungeonGraphEdge edge : graph.edges()) {
            if (!graphIds.add(edge.id())) {
                failures.add("stage=graph edge="
                        + edge.id()
                        + " layoutAttempt="
                        + layoutAttempt
                        + " reason=duplicate graph edge id");
            }
            String layoutEdgeId = DungeonGraphEmbeddingPlanner.layoutEdgeIdFor(edge);
            EdgeDescriptor descriptor = descriptor(
                    layoutEdgeId,
                    edge.sourceNodeId(),
                    edge.targetNodeId(),
                    edge.kind()
            );
            EdgeDescriptor previous = descriptors.putIfAbsent(
                    layoutEdgeId,
                    descriptor
            );
            if (previous != null) {
                failures.add("stage=graph-to-layout edge="
                        + layoutEdgeId
                        + " layoutAttempt="
                        + layoutAttempt
                        + " reason=duplicate mapped layout edge id"
                        + " previous="
                        + previous
                        + " actual="
                        + descriptor);
            }
        }
        return descriptors;
    }

    private static Map<String, EdgeDescriptor> layoutEdgeDescriptors(
            DungeonLayoutPlan layout,
            List<String> failures,
            int layoutAttempt
    ) {
        Map<String, EdgeDescriptor> descriptors = new LinkedHashMap<>();
        for (DungeonLayoutEdge edge : layout.edges()) {
            EdgeDescriptor descriptor = descriptor(
                    edge.id(),
                    edge.fromRoomId(),
                    edge.toRoomId(),
                    edge.kind()
            );
            EdgeDescriptor previous = descriptors.putIfAbsent(edge.id(), descriptor);
            if (previous != null) {
                failures.add("stage=layout edge="
                        + edge.id()
                        + " layoutAttempt="
                        + layoutAttempt
                        + " reason=duplicate layout edge id");
            }
        }
        return descriptors;
    }

    private static Map<String, EdgeDescriptor> resolvedConnectionDescriptors(
            ResolvedDungeonLayout resolved,
            List<String> failures,
            int layoutAttempt
    ) {
        Map<String, EdgeDescriptor> descriptors = new LinkedHashMap<>();
        for (ResolvedDungeonConnection connection : resolved.connections()) {
            EdgeDescriptor descriptor = descriptor(
                    connection.id(),
                    connection.from().roomId(),
                    connection.to().roomId(),
                    connection.kind()
            );
            EdgeDescriptor previous = descriptors.putIfAbsent(
                    connection.id(),
                    descriptor
            );
            if (previous != null) {
                failures.add("stage=resolved edge="
                        + connection.id()
                        + " layoutAttempt="
                        + layoutAttempt
                        + " reason=duplicate resolved connection id");
            }
        }
        return descriptors;
    }

    private static void validateEdgeStage(
            String stage,
            Map<String, EdgeDescriptor> expected,
            Map<String, EdgeDescriptor> actual,
            List<String> failures,
            int layoutAttempt
    ) {
        addSetFailure(failures, stage, expected.keySet(), actual.keySet());
        for (Map.Entry<String, EdgeDescriptor> entry : expected.entrySet()) {
            EdgeDescriptor actualDescriptor = actual.get(entry.getKey());
            if (actualDescriptor == null) {
                failures.add("stage="
                        + stage
                        + " edge="
                        + entry.getKey()
                        + " expected="
                        + entry.getValue()
                        + " actual=<missing>"
                        + " layoutAttempt="
                        + layoutAttempt);
                continue;
            }
            if (!entry.getValue().equals(actualDescriptor)) {
                failures.add("stage="
                        + stage
                        + " edge="
                        + entry.getKey()
                        + " expected="
                        + entry.getValue()
                        + " actual="
                        + actualDescriptor
                        + " layoutAttempt="
                        + layoutAttempt);
            }
        }
        for (Map.Entry<String, EdgeDescriptor> entry : actual.entrySet()) {
            if (!expected.containsKey(entry.getKey())) {
                failures.add("stage="
                        + stage
                        + " edge="
                        + entry.getKey()
                        + " expected=<none>"
                        + " actual="
                        + entry.getValue()
                        + " layoutAttempt="
                        + layoutAttempt);
            }
        }
    }

    private static EdgeDescriptor descriptor(
            String id,
            String firstRoomId,
            String secondRoomId,
            DungeonGraphEdgeKind kind
    ) {
        if (firstRoomId.compareTo(secondRoomId) <= 0) {
            return new EdgeDescriptor(id, firstRoomId, secondRoomId, kind);
        }
        return new EdgeDescriptor(id, secondRoomId, firstRoomId, kind);
    }

    private static void addSetFailure(
            List<String> failures,
            String label,
            Set<String> expected,
            Set<String> actual
    ) {
        Set<String> missing = missing(expected, actual);
        Set<String> extra = extra(expected, actual);
        if (!missing.isEmpty() || !extra.isEmpty()) {
            failures.add(label
                    + " missing="
                    + missing
                    + " extra="
                    + extra);
        }
    }

    private static Set<String> missing(
            Set<String> expected,
            Set<String> actual
    ) {
        Set<String> missing = new LinkedHashSet<>(expected);
        missing.removeAll(actual);
        return missing;
    }

    private static Set<String> extra(
            Set<String> expected,
            Set<String> actual
    ) {
        Set<String> extra = new LinkedHashSet<>(actual);
        extra.removeAll(expected);
        return extra;
    }

    private static boolean contiguous(List<DungeonCellPos> route) {
        for (int index = 1; index < route.size(); index++) {
            DungeonCellPos previous = route.get(index - 1);
            DungeonCellPos current = route.get(index);
            int distance = Math.abs(previous.x() - current.x())
                    + Math.abs(previous.y() - current.y())
                    + Math.abs(previous.z() - current.z());
            if (distance != 1) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, List<String>> adjacency(
            List<ResolvedDungeonRoom> rooms,
            List<ResolvedDungeonConnection> connections
    ) {
        Map<String, List<String>> adjacency = new HashMap<>();
        for (ResolvedDungeonRoom room : rooms) {
            adjacency.put(room.roomId(), new ArrayList<>());
        }
        for (ResolvedDungeonConnection connection : connections) {
            adjacency.get(connection.from().roomId())
                    .add(connection.to().roomId());
            adjacency.get(connection.to().roomId())
                    .add(connection.from().roomId());
        }
        return adjacency;
    }

    private static int componentCount(Map<String, List<String>> adjacency) {
        Set<String> visited = new HashSet<>();
        int components = 0;
        for (String roomId : adjacency.keySet()) {
            if (visited.contains(roomId)) {
                continue;
            }
            components++;
            Queue<String> queue = new ArrayDeque<>();
            queue.add(roomId);
            while (!queue.isEmpty()) {
                String current = queue.remove();
                if (!visited.add(current)) {
                    continue;
                }
                queue.addAll(adjacency.getOrDefault(current, List.of()));
            }
        }
        return components;
    }

    private static boolean reaches(
            Map<String, List<String>> adjacency,
            String source,
            String target
    ) {
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(source);
        while (!queue.isEmpty()) {
            String current = queue.remove();
            if (!visited.add(current)) {
                continue;
            }
            if (current.equals(target)) {
                return true;
            }
            queue.addAll(adjacency.getOrDefault(current, List.of()));
        }
        return false;
    }
}
