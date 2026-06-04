package io.github.naimjeg.obeliskdepths.worldgen.structure.graph;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;

/**
 * Pure dungeon topology node. It carries gameplay semantics only; cell/block
 * placement, footprints, connector sides, and piece bounds are assigned later
 * by the embedding layer.
 */
public record DungeonGraphNode(
        String id,
        DungeonRoomType type
) {
    public DungeonGraphNode {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Graph node id must be non-empty");
        }

        if (type == null) {
            throw new IllegalArgumentException("Graph node type must be present: " + id);
        }
    }
}
