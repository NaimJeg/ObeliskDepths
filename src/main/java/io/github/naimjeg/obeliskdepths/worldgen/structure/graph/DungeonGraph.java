package io.github.naimjeg.obeliskdepths.worldgen.structure.graph;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable authoritative dungeon topology. This type preserves the supplied
 * node/edge order and exposes directed queries without assigning any spatial
 * Minecraft-world data.
 */
public record DungeonGraph(
        List<DungeonGraphNode> nodes,
        List<DungeonGraphEdge> edges
) {
    private static final String DUPLICATE_NODE_LOOKUP_MESSAGE =
            "Graph contains duplicate node ids; validate before lookup: ";

    public DungeonGraph {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    public Optional<DungeonGraphNode> node(String nodeId) {
        Map<String, DungeonGraphNode> lookup = uniqueNodeLookup();
        if (duplicateNodeIds().contains(nodeId)) {
            throw new IllegalStateException(DUPLICATE_NODE_LOOKUP_MESSAGE + nodeId);
        }
        return Optional.ofNullable(lookup.get(nodeId));
    }

    public DungeonGraphNode requireNode(String nodeId) {
        return node(nodeId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown graph node: " + nodeId));
    }

    public List<DungeonGraphEdge> outgoingEdges(String nodeId) {
        return this.edges.stream()
                .filter(edge -> edge.sourceNodeId().equals(nodeId))
                .toList();
    }

    public List<DungeonGraphEdge> incomingEdges(String nodeId) {
        return this.edges.stream()
                .filter(edge -> edge.targetNodeId().equals(nodeId))
                .toList();
    }

    public List<DungeonGraphNode> criticalPathNodes() {
        return this.nodes.stream()
                .filter(DungeonGraphNode::criticalPath)
                .sorted((first, second) -> Integer.compare(
                        first.criticalPathIndex().orElse(Integer.MAX_VALUE),
                        second.criticalPathIndex().orElse(Integer.MAX_VALUE)
                ))
                .toList();
    }

    public int branchCount() {
        return (int) this.nodes.stream()
                .filter(node -> !node.criticalPath())
                .count();
    }

    private Map<String, DungeonGraphNode> uniqueNodeLookup() {
        Map<String, DungeonGraphNode> lookup = new LinkedHashMap<>();
        Set<String> duplicates = duplicateNodeIds();

        for (DungeonGraphNode node : this.nodes) {
            if (!duplicates.contains(node.id())) {
                lookup.put(node.id(), node);
            }
        }

        return lookup;
    }

    private Set<String> duplicateNodeIds() {
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new HashSet<>();

        for (DungeonGraphNode node : this.nodes) {
            if (!seen.add(node.id())) {
                duplicates.add(node.id());
            }
        }

        return duplicates;
    }
}
