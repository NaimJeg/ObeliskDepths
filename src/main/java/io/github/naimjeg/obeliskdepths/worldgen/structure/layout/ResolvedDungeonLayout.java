package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

import io.github.naimjeg.obeliskdepths.dungeon.geometry.*;

import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record ResolvedDungeonLayout(
        BlockPos layoutOrigin,
        String primaryEntryRoomId,
        List<ResolvedDungeonRoom> rooms,
        List<ResolvedDungeonConnection> connections
) {
    public ResolvedDungeonLayout {
        if (layoutOrigin == null) {
            throw new IllegalArgumentException(
                    "Resolved dungeon layout origin must be present"
            );
        }
        if (primaryEntryRoomId == null || primaryEntryRoomId.isBlank()) {
            throw new IllegalArgumentException(
                    "Resolved dungeon layout primary entry id must be non-empty"
            );
        }
        rooms = rooms == null ? List.of() : List.copyOf(rooms);
        connections = connections == null ? List.of() : List.copyOf(connections);
        validateUniqueRooms(rooms);
        validateUniqueConnections(connections);
        if (rooms.stream().noneMatch(room -> room.roomId().equals(primaryEntryRoomId))) {
            throw new IllegalArgumentException(
                    "Resolved dungeon layout missing primary entry room: "
                            + primaryEntryRoomId
            );
        }
    }

    public Optional<ResolvedDungeonRoom> room(String roomId) {
        return this.rooms.stream()
                .filter(room -> room.roomId().equals(roomId))
                .findFirst();
    }

    public ResolvedDungeonRoom requireRoom(String roomId) {
        return room(roomId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown resolved dungeon room: " + roomId
                ));
    }

    public ResolvedDungeonPort requirePort(DungeonPortReference reference) {
        return requireRoom(reference.roomId()).requirePort(reference.portId());
    }

    public ResolvedDungeonLayout withConnections(
            List<ResolvedDungeonConnection> resolvedConnections
    ) {
        return new ResolvedDungeonLayout(
                this.layoutOrigin,
                this.primaryEntryRoomId,
                this.rooms,
                resolvedConnections
        );
    }

    private static void validateUniqueRooms(List<ResolvedDungeonRoom> rooms) {
        Set<String> seen = new HashSet<>();
        for (ResolvedDungeonRoom room : rooms) {
            if (!seen.add(room.roomId())) {
                throw new IllegalArgumentException(
                        "Duplicate resolved room id: " + room.roomId()
                );
            }
        }
    }

    private static void validateUniqueConnections(
            List<ResolvedDungeonConnection> connections
    ) {
        Set<String> seen = new HashSet<>();
        for (ResolvedDungeonConnection connection : connections) {
            if (!seen.add(connection.id())) {
                throw new IllegalArgumentException(
                        "Duplicate resolved connection id: "
                                + connection.id()
                );
            }
        }
    }
}
