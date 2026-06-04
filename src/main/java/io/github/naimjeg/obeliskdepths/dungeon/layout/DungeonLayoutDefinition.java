package io.github.naimjeg.obeliskdepths.dungeon.layout;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record DungeonLayoutDefinition(
        String primaryEntryRoomId,
        List<AuthoredDungeonRoom> rooms,
        List<AuthoredDungeonConnection> connections
) {
    public static final Codec<DungeonLayoutDefinition> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING
                            .fieldOf("primary_entry")
                            .forGetter(DungeonLayoutDefinition::primaryEntryRoomId),
                    AuthoredDungeonRoom.CODEC
                            .listOf()
                            .fieldOf("rooms")
                            .forGetter(DungeonLayoutDefinition::rooms),
                    AuthoredDungeonConnection.CODEC
                            .listOf()
                            .optionalFieldOf("connections", List.of())
                            .forGetter(DungeonLayoutDefinition::connections)
            ).apply(instance, DungeonLayoutDefinition::new));

    public DungeonLayoutDefinition {
        if (primaryEntryRoomId == null || primaryEntryRoomId.isBlank()) {
            throw new IllegalArgumentException(
                    "Dungeon layout primary entry room id must be non-empty"
            );
        }
        rooms = rooms == null ? List.of() : List.copyOf(rooms);
        connections = connections == null
                ? List.of()
                : List.copyOf(connections);
        validateUniqueRoomIds(rooms);
        validateUniqueConnectionIds(connections);
        if (rooms.stream().noneMatch(room -> room.id().equals(primaryEntryRoomId))) {
            throw new IllegalArgumentException(
                    "Dungeon layout missing primary entry room: "
                            + primaryEntryRoomId
            );
        }
    }

    private static void validateUniqueRoomIds(List<AuthoredDungeonRoom> rooms) {
        Set<String> seen = new HashSet<>();
        for (AuthoredDungeonRoom room : rooms) {
            if (!seen.add(room.id())) {
                throw new IllegalArgumentException(
                        "Duplicate authored dungeon room id: " + room.id()
                );
            }
        }
    }

    private static void validateUniqueConnectionIds(
            List<AuthoredDungeonConnection> connections
    ) {
        Set<String> seen = new HashSet<>();
        for (AuthoredDungeonConnection connection : connections) {
            if (!seen.add(connection.id())) {
                throw new IllegalArgumentException(
                        "Duplicate authored dungeon connection id: "
                                + connection.id()
                );
            }
        }
    }
}
