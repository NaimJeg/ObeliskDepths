package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

public record DungeonConnectionSpec(
        String id,
        String fromRoomId,
        String toRoomId,
        int width
) {
    public DungeonConnectionSpec {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Dungeon corridor id must be non-empty");
        }

        if (fromRoomId == null || fromRoomId.isBlank()) {
            throw new IllegalArgumentException("Dungeon corridor " + id + " has no from room id");
        }

        if (toRoomId == null || toRoomId.isBlank()) {
            throw new IllegalArgumentException("Dungeon corridor " + id + " has no to room id");
        }

        if (fromRoomId.equals(toRoomId)) {
            throw new IllegalArgumentException("Dungeon corridor " + id + " connects a room to itself");
        }

        if (width <= 0) {
            throw new IllegalArgumentException("Dungeon corridor width must be positive for " + id);
        }
    }
}
