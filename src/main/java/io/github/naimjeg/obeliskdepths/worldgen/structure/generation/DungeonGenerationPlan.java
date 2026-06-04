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
        return this.translated(0, offset, 0);
    }

    public DungeonGenerationPlan translated(
            int offsetX,
            int offsetY,
            int offsetZ
    ) {
        if (offsetX == 0 && offsetY == 0 && offsetZ == 0) {
            return this;
        }

        return new DungeonGenerationPlan(
                this.origin.offset(offsetX, offsetY, offsetZ),
                translate(this.siteBounds, offsetX, offsetY, offsetZ),
                this.primaryEntryRoomId,
                this.rooms.stream()
                        .map(room -> room.translated(offsetX, offsetY, offsetZ))
                        .toList(),
                this.connections
        );
    }

    private static BoundingBox translate(
            BoundingBox bounds,
            int offsetX,
            int offsetY,
            int offsetZ
    ) {
        return new BoundingBox(
                Math.addExact(bounds.minX(), offsetX),
                Math.addExact(bounds.minY(), offsetY),
                Math.addExact(bounds.minZ(), offsetZ),
                Math.addExact(bounds.maxX(), offsetX),
                Math.addExact(bounds.maxY(), offsetY),
                Math.addExact(bounds.maxZ(), offsetZ)
        );
    }
}
