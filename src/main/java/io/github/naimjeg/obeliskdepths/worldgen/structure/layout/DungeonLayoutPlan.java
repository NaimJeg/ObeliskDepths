package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

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

    public List<DungeonLayoutNode> criticalPathNodes() {
        return this.nodes.stream()
                .filter(DungeonLayoutNode::criticalPath)
                .toList();
    }

    public List<DungeonLayoutNode> branchCaps() {
        return this.nodes.stream()
                .filter(DungeonLayoutNode::branchCap)
                .toList();
    }

    public void validateTree() {
        validateTree(this.nodes, this.edges);
    }

    public void validateSpatial() {
        validateNoNodeOverlap(this.nodes);
        validateBranchCaps(this.nodes);
        validateTree(this.nodes, this.edges);
    }

    private static void validateNodeIds(List<DungeonLayoutNode> nodes) {
        Set<String> seen = new HashSet<>();

        for (DungeonLayoutNode node : nodes) {
            if (!seen.add(node.roomId())) {
                throw new IllegalArgumentException("Duplicate layout node id: " + node.roomId());
            }
        }
    }

    private static void validateEdgeIds(List<DungeonLayoutEdge> edges) {
        Set<String> seen = new HashSet<>();

        for (DungeonLayoutEdge edge : edges) {
            if (!seen.add(edge.id())) {
                throw new IllegalArgumentException("Duplicate layout edge id: " + edge.id());
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
            if (!ids.contains(edge.fromRoomId()) || !ids.contains(edge.toRoomId())) {
                throw new IllegalArgumentException(
                        "Layout edge has missing endpoint: " + edge
                );
            }
        }
    }

    private static void validateNoNodeOverlap(List<DungeonLayoutNode> nodes) {
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

    private static void validateBranchCaps(List<DungeonLayoutNode> nodes) {
        for (DungeonLayoutNode node : nodes) {
            if (node.branchCap() && node.connectorSides().size() != 1) {
                throw new IllegalArgumentException(
                        "Branch cap layout node must have exactly one connector: "
                                + node.roomId()
                                + " connectors="
                                + node.connectorSides()
                );
            }
        }
    }

    private static void validateTree(
            List<DungeonLayoutNode> nodes,
            List<DungeonLayoutEdge> edges
    ) {
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("Dungeon layout tree requires at least one node");
        }

        if (edges.size() != nodes.size() - 1) {
            throw new IllegalArgumentException(
                    "Dungeon layout must be a tree for now: nodes="
                            + nodes.size()
                            + ", edges="
                            + edges.size()
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
        queue.add(nodes.get(0).roomId());

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
