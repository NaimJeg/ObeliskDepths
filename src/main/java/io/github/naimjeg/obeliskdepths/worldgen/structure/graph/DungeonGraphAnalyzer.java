package io.github.naimjeg.obeliskdepths.worldgen.structure.graph;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Queue;
import java.util.Set;

public final class DungeonGraphAnalyzer {
    private DungeonGraphAnalyzer() {
    }

    public static DungeonGraphAnalysis analyze(DungeonGraph graph) {
        Map<String, String> treeParentByNode = treeParents(graph);
        Map<String, List<String>> treeChildrenByNode = treeChildren(graph);
        Map<String, Integer> treeDepthByNode = treeDepths(graph, treeChildrenByNode);
        Map<String, Integer> distanceToBoss = distancesFrom(graph, graph.rootNodeId());
        Map<String, Integer> distanceToEntry = multiSourceDistances(graph, graph.entryNodeIds());
        Map<String, Integer> sectorByNode = sectors(graph, treeParentByNode);
        Set<String> articulations = articulationPoints(graph);

        Map<String, DungeonNodeAnalysis> nodeAnalyses = new LinkedHashMap<>();
        Map<Integer, List<String>> depthBands = new LinkedHashMap<>();
        Map<Integer, List<String>> sectorNodes = new LinkedHashMap<>();

        for (DungeonGraphNode node : graph.nodes()) {
            int distance = distanceToBoss.getOrDefault(node.id(), Integer.MAX_VALUE);
            int entryDistance = distanceToEntry.getOrDefault(node.id(), Integer.MAX_VALUE);
            int treeDepth = treeDepthByNode.getOrDefault(node.id(), Integer.MAX_VALUE);
            int degree = graph.incidentEdges(node.id()).size();
            OptionalInt sector = optional(sectorByNode.get(node.id()));
            OptionalInt depthBand = distance == Integer.MAX_VALUE
                    ? OptionalInt.empty()
                    : OptionalInt.of(distance);

            nodeAnalyses.put(node.id(), new DungeonNodeAnalysis(
                    distance,
                    entryDistance,
                    treeDepth,
                    treeChildrenByNode.getOrDefault(node.id(), List.of()).size(),
                    degree,
                    graph.entryNodeIds().contains(node.id()),
                    degree <= 1 && node.type() != DungeonRoomType.BOSS,
                    articulations.contains(node.id()),
                    sector,
                    depthBand
            ));

            if (distance != Integer.MAX_VALUE) {
                depthBands.computeIfAbsent(distance, ignored -> new ArrayList<>()).add(node.id());
            }

            if (sector.isPresent()) {
                sectorNodes.computeIfAbsent(sector.getAsInt(), ignored -> new ArrayList<>()).add(node.id());
            }
        }

        return new DungeonGraphAnalysis(
                nodeAnalyses,
                treeParentByNode,
                treeChildrenByNode,
                depthBands.entrySet().stream()
                        .map(entry -> new DungeonDepthBand(entry.getKey(), entry.getValue()))
                        .toList(),
                sectorNodes.entrySet().stream()
                        .map(entry -> new DungeonSector(entry.getKey(), entry.getValue()))
                        .toList()
        );
    }

    private static Map<String, String> treeParents(DungeonGraph graph) {
        Map<String, String> result = new LinkedHashMap<>();
        for (DungeonGraphEdge edge : graph.treeEdges()) {
            result.put(edge.targetNodeId(), edge.sourceNodeId());
        }
        return result;
    }

    private static Map<String, List<String>> treeChildren(DungeonGraph graph) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (DungeonGraphNode node : graph.nodes()) {
            result.put(node.id(), new ArrayList<>());
        }
        for (DungeonGraphEdge edge : graph.treeEdges()) {
            result.get(edge.sourceNodeId()).add(edge.targetNodeId());
        }
        return result;
    }

    private static Map<String, Integer> treeDepths(
            DungeonGraph graph,
            Map<String, List<String>> treeChildrenByNode
    ) {
        Map<String, Integer> result = new LinkedHashMap<>();
        Queue<String> queue = new ArrayDeque<>();
        result.put(graph.rootNodeId(), 0);
        queue.add(graph.rootNodeId());

        while (!queue.isEmpty()) {
            String current = queue.remove();
            int depth = result.get(current);
            for (String child : treeChildrenByNode.getOrDefault(current, List.of())) {
                result.put(child, depth + 1);
                queue.add(child);
            }
        }

        return result;
    }

    private static Map<String, Integer> sectors(
            DungeonGraph graph,
            Map<String, String> treeParentByNode
    ) {
        Map<String, Integer> result = new LinkedHashMap<>();
        List<String> rootChildren = graph.treeEdges().stream()
                .filter(edge -> edge.sourceNodeId().equals(graph.rootNodeId()))
                .map(DungeonGraphEdge::targetNodeId)
                .toList();
        if (rootChildren.size() == 1
                && rootChildren.getFirst().equals(DungeonGraphGenerator.BOSS_HUB_ID)) {
            String hub = rootChildren.getFirst();
            rootChildren = graph.treeEdges().stream()
                    .filter(edge -> edge.sourceNodeId().equals(hub))
                    .map(DungeonGraphEdge::targetNodeId)
                    .toList();
            result.put(hub, -1);
        }

        for (int index = 0; index < rootChildren.size(); index++) {
            String rootChild = rootChildren.get(index);
            result.put(rootChild, index);
        }

        boolean changed = true;
        while (changed) {
            changed = false;
            for (Map.Entry<String, String> entry : treeParentByNode.entrySet()) {
                String nodeId = entry.getKey();
                String parentId = entry.getValue();
                if (!result.containsKey(nodeId) && result.containsKey(parentId)) {
                    result.put(nodeId, result.get(parentId));
                    changed = true;
                }
            }
        }

        return result;
    }

    private static Map<String, Integer> distancesFrom(
            DungeonGraph graph,
            String sourceNodeId
    ) {
        return multiSourceDistances(graph, Set.of(sourceNodeId));
    }

    private static Map<String, Integer> multiSourceDistances(
            DungeonGraph graph,
            Set<String> sourceNodeIds
    ) {
        Map<String, Integer> result = new LinkedHashMap<>();
        Queue<String> queue = new ArrayDeque<>();
        for (String source : sourceNodeIds) {
            result.put(source, 0);
            queue.add(source);
        }

        while (!queue.isEmpty()) {
            String current = queue.remove();
            int distance = result.get(current);

            for (String neighbor : graph.neighbors(current)) {
                if (result.containsKey(neighbor)) {
                    continue;
                }
                result.put(neighbor, distance + 1);
                queue.add(neighbor);
            }
        }

        return result;
    }

    private static Set<String> articulationPoints(DungeonGraph graph) {
        Set<String> result = new LinkedHashSet<>();
        for (DungeonGraphNode removed : graph.nodes()) {
            if (!connectedWithout(graph, removed.id())) {
                result.add(removed.id());
            }
        }
        return result;
    }

    private static boolean connectedWithout(
            DungeonGraph graph,
            String removedNodeId
    ) {
        String start = graph.nodes().stream()
                .map(DungeonGraphNode::id)
                .filter(id -> !id.equals(removedNodeId))
                .findFirst()
                .orElse(null);
        if (start == null) {
            return true;
        }

        Set<String> visited = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(start);

        while (!queue.isEmpty()) {
            String current = queue.remove();
            if (!visited.add(current)) {
                continue;
            }
            for (String neighbor : graph.neighbors(current)) {
                if (!neighbor.equals(removedNodeId)) {
                    queue.add(neighbor);
                }
            }
        }

        long normalNodeCount = graph.nodes().stream()
                .filter(node -> !node.id().equals(removedNodeId))
                .count();
        return visited.size() == normalNodeCount;
    }

    private static OptionalInt optional(Integer value) {
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }
}
