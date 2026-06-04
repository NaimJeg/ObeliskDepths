package io.github.naimjeg.obeliskdepths.worldgen.structure.graph;

import io.github.naimjeg.obeliskdepths.dungeon.geometry.*;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;

public final class DungeonGraphValidator {
    private DungeonGraphValidator() {
    }

    public static void validate(DungeonGraph graph) {
        validateIdentifiers(graph);
        validateRequiredNodes(graph);
        validateRootMetadata(graph);
        validateEntryMetadata(graph);
        validateEdges(graph);
        validateTreeSpanningStructure(graph);
        validateFullConnectivity(graph);
        validateLoops(graph);
        DungeonGraphAnalysis analysis = DungeonGraphAnalyzer.analyze(graph);
        validateEntryDistribution(graph, analysis);
        validateProgressionQuality(graph, analysis);
    }

    private static void validateIdentifiers(DungeonGraph graph) {
        require(!graph.nodes().isEmpty(), "Dungeon graph must contain at least one node");
        Set<String> nodeIds = new HashSet<>();
        for (DungeonGraphNode node : graph.nodes()) {
            require(nodeIds.add(node.id()), "Duplicate graph node id: " + node.id());
        }

        Set<String> edgeIds = new HashSet<>();
        for (DungeonGraphEdge edge : graph.edges()) {
            require(edgeIds.add(edge.id()), "Duplicate graph edge id: " + edge.id());
        }
    }

    private static void validateRequiredNodes(DungeonGraph graph) {
        require(countType(graph, DungeonRoomType.BOSS) == 1,
                "Dungeon graph must contain exactly one BOSS node");
        require(countType(graph, DungeonRoomType.START) >= 2,
                "Dungeon graph must contain at least two START nodes");
    }

    private static void validateRootMetadata(DungeonGraph graph) {
        DungeonGraphNode root = graph.requireNode(graph.rootNodeId());
        require(root.type() == DungeonRoomType.BOSS,
                "Graph rootNodeId must point to the BOSS node: " + graph.rootNodeId());
    }

    private static void validateEntryMetadata(DungeonGraph graph) {
        Set<String> startIds = idsOfType(graph, DungeonRoomType.START);
        require(graph.entryNodeIds().equals(startIds),
                "Graph entryNodeIds must contain exactly START nodes: entries="
                        + graph.entryNodeIds() + ", starts=" + startIds);
        require(graph.entryNodeIds().contains(graph.primaryEntryNodeId()),
                "Graph primaryEntryNodeId must belong to entryNodeIds: " + graph.primaryEntryNodeId());
    }

    private static void validateEdges(DungeonGraph graph) {
        Set<String> nodeIds = graph.nodes().stream()
                .map(DungeonGraphNode::id)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> physicalByKind = new HashSet<>();

        for (DungeonGraphEdge edge : graph.edges()) {
            require(!edge.sourceNodeId().equals(edge.targetNodeId()),
                    "Graph edge self-edge: " + edge.id() + " node=" + edge.sourceNodeId());
            require(nodeIds.contains(edge.sourceNodeId()),
                    "Graph edge references missing source node: " + edge.id() + " source=" + edge.sourceNodeId());
            require(nodeIds.contains(edge.targetNodeId()),
                    "Graph edge references missing target node: " + edge.id() + " target=" + edge.targetNodeId());

            String key = physicalKey(edge.sourceNodeId(), edge.targetNodeId()) + ":" + edge.kind();
            require(physicalByKind.add(key),
                    "Duplicate physical graph connection of same kind: edge=" + edge.id()
                            + " endpoints=" + edge.sourceNodeId() + "/" + edge.targetNodeId()
                            + " kind=" + edge.kind());
        }
    }

    private static void validateTreeSpanningStructure(DungeonGraph graph) {
        Map<String, Integer> incomingTreeParents = new LinkedHashMap<>();
        Set<String> normalNodeIds = normalNodeIds(graph);
        int normalTreeEdges = 0;

        for (String nodeId : normalNodeIds) {
            incomingTreeParents.put(nodeId, 0);
        }

        for (DungeonGraphEdge edge : graph.treeEdges()) {
            require(normalNodeIds.contains(edge.sourceNodeId()) && normalNodeIds.contains(edge.targetNodeId()),
                    "TREE edge in radial tree must connect normal dungeon nodes: " + edge.id());
            incomingTreeParents.put(edge.targetNodeId(), incomingTreeParents.get(edge.targetNodeId()) + 1);
            normalTreeEdges++;
        }

        require(incomingTreeParents.get(graph.rootNodeId()) == 0,
                "BOSS must have no TREE parent in radial tree: " + graph.rootNodeId());

        for (String nodeId : normalNodeIds) {
            if (nodeId.equals(graph.rootNodeId())) {
                continue;
            }
            require(incomingTreeParents.get(nodeId) == 1,
                    "Every non-boss normal node must have exactly one TREE parent: "
                            + nodeId + " parents=" + incomingTreeParents.get(nodeId));
        }

        require(normalTreeEdges == normalNodeIds.size() - 1,
                "TREE edges must form a boss-rooted spanning tree over normal nodes: normalNodes="
                        + normalNodeIds.size() + ", treeEdges=" + normalTreeEdges);
        require(treeReachableCount(graph) == normalNodeIds.size(),
                "TREE radial hierarchy must reach every normal node from BOSS");
    }

    private static void validateFullConnectivity(DungeonGraph graph) {
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(graph.rootNodeId());

        while (!queue.isEmpty()) {
            String current = queue.remove();
            if (!visited.add(current)) {
                continue;
            }
            queue.addAll(graph.neighbors(current));
        }

        require(visited.size() == graph.nodes().size(),
                "Complete dungeon graph must be connected: visited="
                        + visited.size() + ", nodes=" + graph.nodes().size());
    }

    private static void validateLoops(DungeonGraph graph) {
        for (DungeonGraphEdge loop : graph.loopEdges()) {
            require(!graph.containsPhysicalConnection(loop.sourceNodeId(), loop.targetNodeId(), DungeonGraphEdgeKind.TREE),
                    "LOOP edge duplicates TREE physical connection: " + loop.id());
        }

        int cyclomatic = graph.edges().size() - graph.nodes().size() + 1;
        require(cyclomatic >= 1, "Generated dungeon graph must contain at least one genuine cycle");
    }

    private static void validateEntryDistribution(
            DungeonGraph graph,
            DungeonGraphAnalysis analysis
    ) {
        for (String entry : graph.entryNodeIds()) {
            DungeonNodeAnalysis node = analysis.requireNode(entry);
            require(node.distanceToBoss() >= 3,
                    "START nodes must not be directly adjacent to BOSS: " + entry
                            + " distance=" + node.distanceToBoss());
            require(node.treeDepth() >= DungeonGraphGenerationConfig.DEFAULT.minArmDepth(),
                    "START nodes must be in outer depth bands: " + entry
                            + " treeDepth=" + node.treeDepth()
                            + " minArmDepth=" + DungeonGraphGenerationConfig.DEFAULT.minArmDepth());
        }

        TreeSet<Integer> sectors = new TreeSet<>();
        for (String entry : graph.entryNodeIds()) {
            OptionalIntAdapter.requirePresent(analysis.requireNode(entry).sectorIndex(),
                    "START node missing sector analysis: " + entry);
            int sector = analysis.requireNode(entry).sectorIndex().getAsInt();
            require(sectors.add(sector), "START nodes must occupy distinct sectors: sector=" + sector);
        }
    }

    private static void validateProgressionQuality(
            DungeonGraph graph,
            DungeonGraphAnalysis analysis
    ) {
        DungeonGraphGenerationConfig config = DungeonGraphGenerationConfig.DEFAULT;
        require(graph.nodes().size() <= config.maxNodeCount(),
                "Dungeon graph exceeds max node count: nodes="
                        + graph.nodes().size() + ", max=" + config.maxNodeCount());

        for (DungeonGraphNode node : graph.nodes()) {
            DungeonNodeAnalysis nodeAnalysis = analysis.requireNode(node.id());
            if (node.id().equals(DungeonGraphGenerator.BOSS_HUB_ID)) {
                require(nodeAnalysis.totalDegree() <= 4,
                        "Boss hub exceeds authored port capacity: "
                                + node.id() + " degree="
                                + nodeAnalysis.totalDegree());
                continue;
            }
            if (!node.id().equals(graph.rootNodeId())) {
                require(nodeAnalysis.totalDegree() <= config.maxOrdinaryDegree(),
                        "Ordinary node exceeds max degree: "
                                + node.id() + " degree=" + nodeAnalysis.totalDegree());
            }
            if (node.type() == DungeonRoomType.TREASURE) {
                require(nodeAnalysis.totalDegree() <= 1,
                        "TREASURE nodes must be terminal: " + node.id()
                                + " degree=" + nodeAnalysis.totalDegree());
            }
        }

        for (String entry : graph.entryNodeIds()) {
            require(analysis.requireNode(entry).distanceToBoss() < Integer.MAX_VALUE,
                    "START cannot reach BOSS: " + entry);
        }
    }

    private static int treeReachableCount(DungeonGraph graph) {
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(graph.rootNodeId());

        while (!queue.isEmpty()) {
            String current = queue.remove();
            if (!visited.add(current)) {
                continue;
            }
            for (DungeonGraphEdge edge : graph.treeEdges()) {
                if (edge.sourceNodeId().equals(current)) {
                    queue.add(edge.targetNodeId());
                }
            }
        }

        return visited.size();
    }

    private static Set<String> normalNodeIds(DungeonGraph graph) {
        Set<String> result = new HashSet<>();
        for (DungeonGraphNode node : graph.nodes()) {
            result.add(node.id());
        }
        return result;
    }

    private static long countType(
            DungeonGraph graph,
            DungeonRoomType type
    ) {
        return graph.nodes().stream().filter(node -> node.type() == type).count();
    }

    private static Set<String> idsOfType(
            DungeonGraph graph,
            DungeonRoomType type
    ) {
        Set<String> result = new java.util.LinkedHashSet<>();
        for (DungeonGraphNode node : graph.nodes()) {
            if (node.type() == type) {
                result.add(node.id());
            }
        }
        return java.util.Collections.unmodifiableSet(result);
    }

    private static String physicalKey(
            String first,
            String second
    ) {
        return first.compareTo(second) <= 0 ? first + "|" + second : second + "|" + first;
    }

    private static void require(
            boolean condition,
            String message
    ) {
        if (!condition) {
            throw new DungeonGraphValidationException(message);
        }
    }

    private static final class OptionalIntAdapter {
        private OptionalIntAdapter() {
        }

        private static void requirePresent(
                java.util.OptionalInt value,
                String message
        ) {
            require(value.isPresent(), message);
        }
    }
}
