package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

import io.github.naimjeg.obeliskdepths.worldgen.structure.ObeliskDungeonPieceRole;

public record DungeonRoomSpec(
        String id,
        ObeliskDungeonPieceRole role,
        int width,
        int height,
        int depth
) {
    public DungeonRoomSpec {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Dungeon room id must be stable and non-empty");
        }

        if (role == null || !role.isRoom()) {
            throw new IllegalArgumentException("Dungeon room spec requires a room role: " + role);
        }

        if (width <= 0 || height <= 0 || depth <= 0) {
            throw new IllegalArgumentException(
                    "Dungeon room dimensions must be positive for room " + id
            );
        }
    }
}
