package io.github.naimjeg.obeliskdepths.worldgen.structure.graph;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import java.util.OptionalInt;

/**
 * Pure dungeon topology node. It carries gameplay semantics only; cell/block
 * placement, footprints, connector sides, and piece bounds are assigned later
 * by the embedding layer.
 */
public record DungeonGraphNode(
        String id,
        DungeonRoomType type,
        boolean criticalPath,
        boolean branchCap,
        OptionalInt criticalPathIndex
) {
    public DungeonGraphNode {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Graph node id must be non-empty");
        }

        if (type == null) {
            throw new IllegalArgumentException("Graph node type must be present: " + id);
        }

        criticalPathIndex = criticalPathIndex == null
                ? OptionalInt.empty()
                : criticalPathIndex;

        if (!criticalPath && criticalPathIndex.isPresent()) {
            throw new IllegalArgumentException(
                    "Non-critical graph node cannot have a critical path index: " + id
            );
        }
    }

    public static DungeonGraphNode critical(
            String id,
            DungeonRoomType type,
            int criticalPathIndex
    ) {
        return new DungeonGraphNode(
                id,
                type,
                true,
                false,
                OptionalInt.of(criticalPathIndex)
        );
    }

    public static DungeonGraphNode branchCap(
            String id,
            DungeonRoomType type
    ) {
        return new DungeonGraphNode(
                id,
                type,
                false,
                true,
                OptionalInt.empty()
        );
    }
}
