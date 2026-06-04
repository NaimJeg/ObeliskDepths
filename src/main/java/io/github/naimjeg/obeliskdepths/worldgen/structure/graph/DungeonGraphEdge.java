package io.github.naimjeg.obeliskdepths.worldgen.structure.graph;

import io.github.naimjeg.obeliskdepths.dungeon.geometry.*;

/**
 * Topology edge. TREE edges are directed from the boss-rooted parent toward
 * the child. LOOP and SECRET source/target order is deterministic metadata;
 * traversal is physically bidirectional for every edge kind.
 */
public record DungeonGraphEdge(
        String id,
        String sourceNodeId,
        String targetNodeId,
        DungeonGraphEdgeKind kind
) {
    public DungeonGraphEdge {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Graph edge id must be non-empty");
        }

        if (sourceNodeId == null || sourceNodeId.isBlank()) {
            throw new IllegalArgumentException("Graph edge missing source node: " + id);
        }

        if (targetNodeId == null || targetNodeId.isBlank()) {
            throw new IllegalArgumentException("Graph edge missing target node: " + id);
        }

        if (kind == null) {
            throw new IllegalArgumentException("Graph edge kind must be present: " + id);
        }
    }
}
