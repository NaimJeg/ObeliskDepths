package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

import io.github.naimjeg.obeliskdepths.dungeon.geometry.*;

import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonGraphEdgeKind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

public record DungeonLayoutPlan(
        int cellSize,
        List<DungeonLayoutNode> nodes,
        List<DungeonLayoutEdge> edges
) {
    public DungeonLayoutPlan {
        if (cellSize != DungeonLayoutConstants.CELL_SIZE) {
            throw new IllegalArgumentException(
                    "Unsupported dungeon layout cell size: " + cellSize
            );
        }

        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);

        validateNodeIds(nodes);
        validateEdgeIds(edges);
        validateEndpoints(nodes, edges);
    }

    public Optional<DungeonLayoutNode> node(String roomId) {
        return this.nodes.stream()
                .filter(node -> node.roomId().equals(roomId))
                .findFirst();
    }

    public DungeonLayoutNode requireNode(String roomId) {
        return node(roomId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown layout node: " + roomId
                ));
    }

    public void validateConnected() {
        validateConnected(this.nodes, this.edges);
    }

    /**
     * Validates invariants that depend on the complete planned layout.
     *
     * The current prototype requires:
     * - no overlapping room footprints;
     * - at least one room;
     * - a connected graph;
     * - a tree-shaped topology with no loops.
     */
    public void validateSpatial() {
        validateNoNodeOverlap(this.nodes);
        validateTreeSpanningStructure(this.nodes, this.edges);
        validateConnected(this.nodes, this.edges);
    }

    private static void validateTreeSpanningStructure(
            List<DungeonLayoutNode> nodes,
            List<DungeonLayoutEdge> edges
    ) {
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException(
                    "Dungeon layout requires at least one node"
            );
        }

        List<DungeonLayoutEdge> treeEdges = edges.stream()
                .filter(edge -> edge.kind() == DungeonGraphEdgeKind.TREE)
                .toList();

        int expectedTreeEdgeCount = nodes.size() - 1;

        if (treeEdges.size() != expectedTreeEdgeCount) {
            throw new IllegalArgumentException(
                    "Dungeon layout TREE edges must form a spanning tree: nodes="
                            + nodes.size()
                            + ", treeEdges="
                            + treeEdges.size()
                            + ", expectedTreeEdges="
                            + expectedTreeEdgeCount
            );
        }

        validateConnected(nodes, treeEdges);
    }

    private static void validateNodeIds(List<DungeonLayoutNode> nodes) {
        Set<String> seen = new HashSet<>();

        for (DungeonLayoutNode node : nodes) {
            if (!seen.add(node.roomId())) {
                throw new IllegalArgumentException(
                        "Duplicate layout node id: " + node.roomId()
                );
            }
        }
    }

    private static void validateEdgeIds(List<DungeonLayoutEdge> edges) {
        Set<String> seen = new HashSet<>();

        for (DungeonLayoutEdge edge : edges) {
            if (!seen.add(edge.id())) {
                throw new IllegalArgumentException(
                        "Duplicate layout edge id: " + edge.id()
                );
            }
        }
    }

    private static void validateEndpoints(
            List<DungeonLayoutNode> nodes,
            List<DungeonLayoutEdge> edges
    ) {
        Set<String> ids = new HashSet<>();

        for (DungeonLayoutNode node : nodes) {
            ids.add(node.roomId());
        }

        for (DungeonLayoutEdge edge : edges) {
            if (!ids.contains(edge.fromRoomId())
                    || !ids.contains(edge.toRoomId())) {
                throw new IllegalArgumentException(
                        "Layout edge has missing endpoint: " + edge
                );
            }
        }
    }

    private static void validateNoNodeOverlap(
            List<DungeonLayoutNode> nodes
    ) {
        for (int i = 0; i < nodes.size(); i++) {
            DungeonLayoutNode first = nodes.get(i);

            for (int j = i + 1; j < nodes.size(); j++) {
                DungeonLayoutNode second = nodes.get(j);

                if (first.intersects(second)) {
                    throw new IllegalArgumentException(
                            "Layout node footprints overlap: "
                                    + first.roomId()
                                    + " and "
                                    + second.roomId()
                    );
                }
            }
        }
    }

    private static void validateConnected(
            List<DungeonLayoutNode> nodes,
            List<DungeonLayoutEdge> edges
    ) {
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException(
                    "Dungeon layout requires at least one node"
            );
        }

        Map<String, List<String>> adjacency = new HashMap<>();

        for (DungeonLayoutNode node : nodes) {
            adjacency.put(node.roomId(), new ArrayList<>());
        }

        for (DungeonLayoutEdge edge : edges) {
            adjacency.get(edge.fromRoomId()).add(edge.toRoomId());
            adjacency.get(edge.toRoomId()).add(edge.fromRoomId());
        }

        Set<String> visited = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(nodes.getFirst().roomId());

        while (!queue.isEmpty()) {
            String current = queue.remove();

            if (!visited.add(current)) {
                continue;
            }

            for (String next : adjacency.get(current)) {
                if (!visited.contains(next)) {
                    queue.add(next);
                }
            }
        }

        if (visited.size() != nodes.size()) {
            throw new IllegalArgumentException(
                    "Dungeon layout graph must be connected: visited="
                            + visited.size()
                            + ", nodes="
                            + nodes.size()
            );
        }
    }
}