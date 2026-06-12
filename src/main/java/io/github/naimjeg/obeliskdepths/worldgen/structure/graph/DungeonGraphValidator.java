package io.github.naimjeg.obeliskdepths.worldgen.structure.graph;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;

public final class DungeonGraphValidator {
    private DungeonGraphValidator() {
    }

    public static void validate(DungeonGraph graph) {
        require(!graph.nodes().isEmpty(), "Dungeon graph must contain at least one node");
        validateUniqueNodeIds(graph.nodes());
        validateUniqueEdgeIds(graph.edges());
        validateEdges(graph);
        validateRequiredRoomCounts(graph);
        validateTreeShape(graph);
        validateSemanticTerminals(graph);
        validateCriticalPath(graph);
    }

    private static void validateUniqueNodeIds(List<DungeonGraphNode> nodes) {
        Set<String> seen = new HashSet<>();

        for (DungeonGraphNode node : nodes) {
            require(seen.add(node.id()), "Duplicate graph node id: " + node.id());
        }
    }

    private static void validateUniqueEdgeIds(List<DungeonGraphEdge> edges) {
        Set<String> seen = new HashSet<>();

        for (DungeonGraphEdge edge : edges) {
            require(seen.add(edge.id()), "Duplicate graph edge id: " + edge.id());
        }
    }

    private static void validateEdges(DungeonGraph graph) {
        Set<String> nodeIds = new HashSet<>();

        for (DungeonGraphNode node : graph.nodes()) {
            nodeIds.add(node.id());
        }

        for (DungeonGraphEdge edge : graph.edges()) {
            require(!edge.sourceNodeId().equals(edge.targetNodeId()),
                    "Graph edge self-loop: " + edge.id() + " node=" + edge.sourceNodeId());
            require(nodeIds.contains(edge.sourceNodeId()),
                    "Graph edge references missing source node: " + edge.id() + " source=" + edge.sourceNodeId());
            require(nodeIds.contains(edge.targetNodeId()),
                    "Graph edge references missing target node: " + edge.id() + " target=" + edge.targetNodeId());
        }
    }

    private static void validateRequiredRoomCounts(DungeonGraph graph) {
        require(countNodes(graph, node -> node.type() == DungeonRoomType.START) == 1,
                "Dungeon graph must contain exactly one START node");
        require(countNodes(graph, node -> node.type() == DungeonRoomType.BOSS) == 1,
                "Dungeon graph must contain exactly one BOSS node");
        require(countNodes(graph, node -> node.type() == DungeonRoomType.EXIT) == 1,
                "Dungeon graph must contain exactly one EXIT node");
    }

    private static void validateTreeShape(DungeonGraph graph) {
        Map<String, List<String>> outgoing = new HashMap<>();
        Map<String, Integer> incomingCounts = new HashMap<>();

        for (DungeonGraphNode node : graph.nodes()) {
            outgoing.put(node.id(), new ArrayList<>());
            incomingCounts.put(node.id(), 0);
        }

        for (DungeonGraphEdge edge : graph.edges()) {
            outgoing.get(edge.sourceNodeId()).add(edge.targetNodeId());
            incomingCounts.put(edge.targetNodeId(), incomingCounts.get(edge.targetNodeId()) + 1);
        }

        List<String> roots = incomingCounts.entrySet()
                .stream()
                .filter(entry -> entry.getValue() == 0)
                .map(Map.Entry::getKey)
                .toList();

        require(roots.size() == 1,
                "Dungeon graph must have exactly one root/connected component, roots=" + roots);
        DungeonGraphNode start = onlyNode(graph, node -> node.type() == DungeonRoomType.START);
        require(roots.get(0).equals(start.id()),
                "Dungeon graph root must be START: root=" + roots.get(0) + " start=" + start.id());

        for (DungeonGraphNode node : graph.nodes()) {
            int incoming = incomingCounts.get(node.id());
            if (node.type() == DungeonRoomType.START) {
                require(incoming == 0, "START node has incoming edges: " + node.id() + " incoming=" + incoming);
            } else {
                require(incoming == 1, "Non-root node must have exactly one incoming edge: "
                        + node.id() + " incoming=" + incoming);
            }
        }

        validateAcyclic(graph, outgoing);

        require(graph.edges().size() == graph.nodes().size() - 1,
                "Dungeon graph edge count must equal node count minus one: nodes="
                        + graph.nodes().size() + ", edges=" + graph.edges().size());

        validateConnected(graph, outgoing, start.id());
    }

    private static void validateConnected(
            DungeonGraph graph,
            Map<String, List<String>> outgoing,
            String rootId
    ) {
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(rootId);

        while (!queue.isEmpty()) {
            String current = queue.remove();

            if (!visited.add(current)) {
                continue;
            }

            queue.addAll(outgoing.getOrDefault(current, List.of()));
        }

        require(visited.size() == graph.nodes().size(),
                "Dungeon graph must be connected from START: visited="
                        + visited.size() + ", nodes=" + graph.nodes().size());
    }

    private static void validateAcyclic(
            DungeonGraph graph,
            Map<String, List<String>> outgoing
    ) {
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();

        for (DungeonGraphNode node : graph.nodes()) {
            require(!hasCycle(node.id(), outgoing, visiting, visited),
                    "Dungeon graph must be acyclic; cycle includes or is reachable from node " + node.id());
        }
    }

    private static boolean hasCycle(
            String nodeId,
            Map<String, List<String>> outgoing,
            Set<String> visiting,
            Set<String> visited
    ) {
        if (visiting.contains(nodeId)) {
            return true;
        }

        if (visited.contains(nodeId)) {
            return false;
        }

        visiting.add(nodeId);

        for (String target : outgoing.getOrDefault(nodeId, List.of())) {
            if (hasCycle(target, outgoing, visiting, visited)) {
                return true;
            }
        }

        visiting.remove(nodeId);
        visited.add(nodeId);
        return false;
    }

    private static void validateSemanticTerminals(DungeonGraph graph) {
        DungeonGraphNode exit = onlyNode(graph, node -> node.type() == DungeonRoomType.EXIT);
        require(graph.outgoingEdges(exit.id()).isEmpty(),
                "EXIT node must have no outgoing edges: " + exit.id());

        for (DungeonGraphNode node : graph.nodes()) {
            if (node.branchCap()) {
                require(graph.outgoingEdges(node.id()).isEmpty(),
                        "Branch cap node must have no outgoing edges: " + node.id());
            }
        }
    }

    private static void validateCriticalPath(DungeonGraph graph) {
        List<DungeonGraphNode> critical = graph.criticalPathNodes();
        require(!critical.isEmpty(), "Dungeon graph critical path must be nonempty");
        require(critical.size() >= 4,
                "Dungeon graph critical path must contain START, at least one COMBAT, BOSS, and EXIT");

        for (int index = 0; index < critical.size(); index++) {
            DungeonGraphNode node = critical.get(index);
            require(node.criticalPathIndex().isPresent(),
                    "Critical path node missing index: " + node.id());
            require(node.criticalPathIndex().getAsInt() == index,
                    "Critical path index gap or duplicate at node " + node.id()
                            + ": expected=" + index
                            + ", actual=" + node.criticalPathIndex().getAsInt());
        }

        require(critical.get(0).type() == DungeonRoomType.START,
                "Critical path must start with START node: " + critical.get(0).id());
        require(critical.get(critical.size() - 1).type() == DungeonRoomType.EXIT,
                "Critical path must end with EXIT node: " + critical.get(critical.size() - 1).id());

        int bossIndex = -1;
        int exitIndex = -1;

        for (int index = 0; index < critical.size(); index++) {
            DungeonGraphNode node = critical.get(index);

            if (node.type() == DungeonRoomType.BOSS) {
                bossIndex = index;
            }

            if (node.type() == DungeonRoomType.EXIT) {
                exitIndex = index;
            }

            if (index > 0 && index < critical.size() - 2) {
                require(node.type() == DungeonRoomType.COMBAT,
                        "Critical path middle node must be COMBAT before BOSS: "
                                + node.id() + " type=" + node.type());
            }
        }

        require(bossIndex >= 0, "Critical path must include BOSS node");
        require(exitIndex >= 0, "Critical path must include EXIT node");
        require(bossIndex == critical.size() - 2,
                "BOSS must appear immediately before EXIT on critical path: bossIndex="
                        + bossIndex + ", criticalLength=" + critical.size());
        require(bossIndex < exitIndex,
                "BOSS must appear before EXIT on critical path: bossIndex="
                        + bossIndex + ", exitIndex=" + exitIndex);

        Set<String> criticalIds = new HashSet<>();

        for (DungeonGraphNode node : critical) {
            criticalIds.add(node.id());
        }

        for (DungeonGraphNode node : graph.nodes()) {
            if (!node.criticalPath()) {
                require(node.criticalPathIndex().isEmpty(),
                        "Branch node incorrectly has critical path index: " + node.id());
                require(node.type() != DungeonRoomType.START
                                && node.type() != DungeonRoomType.BOSS
                                && node.type() != DungeonRoomType.EXIT,
                        "Required path room type is not marked critical: "
                                + node.id() + " type=" + node.type());
            }
        }

        for (int index = 0; index < critical.size() - 1; index++) {
            DungeonGraphNode source = critical.get(index);
            DungeonGraphNode target = critical.get(index + 1);
            boolean connected = graph.outgoingEdges(source.id())
                    .stream()
                    .anyMatch(edge -> edge.targetNodeId().equals(target.id()));

            require(connected,
                    "Critical path continuity missing edge: "
                            + source.id() + " -> " + target.id());
        }

        for (DungeonGraphEdge edge : graph.edges()) {
            if (!criticalIds.contains(edge.sourceNodeId()) || !criticalIds.contains(edge.targetNodeId())) {
                continue;
            }

            DungeonGraphNode source = graph.requireNode(edge.sourceNodeId());
            DungeonGraphNode target = graph.requireNode(edge.targetNodeId());
            require(source.criticalPathIndex().getAsInt() + 1 == target.criticalPathIndex().getAsInt(),
                    "Critical path edge skips or reverses order: " + edge.id()
                            + " source=" + edge.sourceNodeId()
                            + " target=" + edge.targetNodeId());
        }
    }

    private static long countNodes(
            DungeonGraph graph,
            Predicate<DungeonGraphNode> predicate
    ) {
        return graph.nodes().stream().filter(predicate).count();
    }

    private static DungeonGraphNode onlyNode(
            DungeonGraph graph,
            Predicate<DungeonGraphNode> predicate
    ) {
        List<DungeonGraphNode> nodes = graph.nodes().stream().filter(predicate).toList();
        require(nodes.size() == 1, "Expected exactly one matching graph node, found=" + nodes.size());
        return nodes.get(0);
    }

    private static void require(
            boolean condition,
            String message
    ) {
        if (!condition) {
            throw new DungeonGraphValidationException(message);
        }
    }
}
