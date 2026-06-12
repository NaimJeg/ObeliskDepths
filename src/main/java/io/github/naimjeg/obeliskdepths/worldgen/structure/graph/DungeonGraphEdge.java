package io.github.naimjeg.obeliskdepths.worldgen.structure.graph;

/**
 * Directed topology edge rooted at START. Direction describes generation
 * parentage only; spatial connector sides and corridor volumes are embedding
 * concerns.
 */
public record DungeonGraphEdge(
        String id,
        String sourceNodeId,
        String targetNodeId
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
    }
}
