package io.github.naimjeg.obeliskdepths.worldgen.structure.generation;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public record DungeonGenerationPlan(
        BlockPos origin,
        BoundingBox siteBounds,
        String primaryEntryRoomId,
        List<PlacedDungeonRoom> rooms,
        List<RoutedDungeonConnection> connections
) {
    public DungeonGenerationPlan {
        if (origin == null || siteBounds == null) {
            throw new IllegalArgumentException("Dungeon generation plan bounds are required");
        }
        if (primaryEntryRoomId == null || primaryEntryRoomId.isBlank()) {
            throw new IllegalArgumentException("Primary entry room id is required");
        }
        rooms = rooms == null ? List.of() : List.copyOf(rooms);
        connections = connections == null ? List.of() : List.copyOf(connections);
        if (rooms.isEmpty()) {
            throw new IllegalArgumentException("Dungeon generation plan has no rooms");
        }
        boolean hasPrimary = false;
        for (PlacedDungeonRoom room : rooms) {
            if (room.id().equals(primaryEntryRoomId)) {
                hasPrimary = true;
                break;
            }
        }
        if (!hasPrimary) {
            throw new IllegalArgumentException(
                    "Dungeon generation plan missing primary entry room: "
                            + primaryEntryRoomId
            );
        }
    }

    public BlockPos primaryEntryAnchor() {
        for (PlacedDungeonRoom room : this.rooms) {
            if (room.id().equals(this.primaryEntryRoomId)) {
                return room.anchor();
            }
        }
        throw new IllegalStateException(
                "Dungeon generation plan missing primary entry room: "
                        + this.primaryEntryRoomId
        );
    }

    public int routedCellCount() {
        int total = 0;
        for (RoutedDungeonConnection connection : this.connections) {
            total += connection.cells().size();
        }
        return total;
    }

    public DungeonGenerationPlan translatedY(int offset) {
        if (offset == 0) {
            return this;
        }

        return new DungeonGenerationPlan(
                this.origin.offset(0, offset, 0),
                translate(this.siteBounds, offset),
                this.primaryEntryRoomId,
                this.rooms.stream()
                        .map(room -> room.translatedY(offset))
                        .toList(),
                this.connections
        );
    }

    private static BoundingBox translate(
            BoundingBox bounds,
            int offset
    ) {
        return new BoundingBox(
                bounds.minX(),
                Math.addExact(bounds.minY(), offset),
                bounds.minZ(),
                bounds.maxX(),
                Math.addExact(bounds.maxY(), offset),
                bounds.maxZ()
        );
    }
}
