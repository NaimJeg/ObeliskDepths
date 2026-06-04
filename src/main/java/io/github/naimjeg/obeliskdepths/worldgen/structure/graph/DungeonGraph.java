package io.github.naimjeg.obeliskdepths.worldgen.structure.graph;

import io.github.naimjeg.obeliskdepths.dungeon.geometry.*;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
        String rootNodeId,
        Set<String> entryNodeIds,
        String primaryEntryNodeId,
        List<DungeonGraphNode> nodes,
        List<DungeonGraphEdge> edges
) {
    private static final String DUPLICATE_NODE_LOOKUP_MESSAGE =
            "Graph contains duplicate node ids; validate before lookup: ";

    public DungeonGraph {
        requireId(rootNodeId, "Graph root node id must be non-empty");
        requireId(primaryEntryNodeId, "Graph primary entry node id must be non-empty");
        if (entryNodeIds == null) {
            throw new IllegalArgumentException("Graph entry node ids must be present");
        }
        LinkedHashSet<String> copiedEntries = new LinkedHashSet<>();
        for (String entryNodeId : entryNodeIds) {
            requireId(entryNodeId, "Graph entry node id must be non-empty");
            copiedEntries.add(entryNodeId);
        }
        entryNodeIds = Collections.unmodifiableSet(copiedEntries);
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

    public DungeonGraphNode bossNode() {
        DungeonGraphNode node = requireNode(this.rootNodeId);
        if (node.type() != DungeonRoomType.BOSS) {
            throw new IllegalStateException("Graph root is not a BOSS node: " + this.rootNodeId);
        }
        return node;
    }

    public List<DungeonGraphNode> entryNodes() {
        return this.entryNodeIds.stream()
                .map(this::requireNode)
                .toList();
    }

    public DungeonGraphNode primaryEntryNode() {
        return requireNode(this.primaryEntryNodeId);
    }

    public List<DungeonGraphEdge> treeEdges() {
        return edgesOfKind(DungeonGraphEdgeKind.TREE);
    }

    public List<DungeonGraphEdge> loopEdges() {
        return edgesOfKind(DungeonGraphEdgeKind.LOOP);
    }

    public List<DungeonGraphEdge> secretEdges() {
        return edgesOfKind(DungeonGraphEdgeKind.SECRET);
    }

    public List<DungeonGraphEdge> incidentEdges(String nodeId) {
        return this.edges.stream()
                .filter(edge -> edge.sourceNodeId().equals(nodeId) || edge.targetNodeId().equals(nodeId))
                .toList();
    }

    public List<String> neighbors(String nodeId) {
        return incidentEdges(nodeId).stream()
                .map(edge -> edge.sourceNodeId().equals(nodeId) ? edge.targetNodeId() : edge.sourceNodeId())
                .toList();
    }

    public List<DungeonGraphNode> treeChildren(String nodeId) {
        return this.treeEdges().stream()
                .filter(edge -> edge.sourceNodeId().equals(nodeId))
                .map(edge -> requireNode(edge.targetNodeId()))
                .toList();
    }

    public Optional<DungeonGraphNode> treeParent(String nodeId) {
        List<DungeonGraphEdge> parents = this.treeEdges().stream()
                .filter(edge -> edge.targetNodeId().equals(nodeId))
                .toList();
        if (parents.isEmpty()) {
            return Optional.empty();
        }
        if (parents.size() > 1) {
            throw new IllegalStateException("Graph node has multiple TREE parents: " + nodeId);
        }
        return Optional.of(requireNode(parents.get(0).sourceNodeId()));
    }

    public boolean containsPhysicalConnection(
            String firstNodeId,
            String secondNodeId
    ) {
        return this.edges.stream().anyMatch(edge -> samePhysicalConnection(edge, firstNodeId, secondNodeId));
    }

    public boolean containsPhysicalConnection(
            String firstNodeId,
            String secondNodeId,
            DungeonGraphEdgeKind kind
    ) {
        return this.edges.stream()
                .filter(edge -> edge.kind() == kind)
                .anyMatch(edge -> samePhysicalConnection(edge, firstNodeId, secondNodeId));
    }

    private List<DungeonGraphEdge> edgesOfKind(DungeonGraphEdgeKind kind) {
        return this.edges.stream()
                .filter(edge -> edge.kind() == kind)
                .toList();
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

    private static boolean samePhysicalConnection(
            DungeonGraphEdge edge,
            String firstNodeId,
            String secondNodeId
    ) {
        return (edge.sourceNodeId().equals(firstNodeId) && edge.targetNodeId().equals(secondNodeId))
                || (edge.sourceNodeId().equals(secondNodeId) && edge.targetNodeId().equals(firstNodeId));
    }

    private static void requireId(
            String id,
            String message
    ) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
