package io.github.naimjeg.obeliskdepths.worldgen.structure.piece;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public record DungeonPiecePlan(
        BlockPos layoutOrigin,
        BoundingBox siteBounds,
        String primaryEntryRoomId,
        BlockPos primaryEntryAnchor,
        List<DungeonRoutedCorridor> routedCorridors,
        List<DungeonPieceMetadata> pieces
) {
    public DungeonPiecePlan {
        if (layoutOrigin == null || siteBounds == null || primaryEntryAnchor == null) {
            throw new IllegalArgumentException("Dungeon piece plan origin and bounds must be present");
        }

        if (primaryEntryRoomId == null || primaryEntryRoomId.isBlank()) {
            throw new IllegalArgumentException("Dungeon piece plan primary entry room id must be non-empty");
        }

        routedCorridors = List.copyOf(routedCorridors);
        pieces = List.copyOf(pieces);

        if (pieces.isEmpty()) {
            throw new IllegalArgumentException("Dungeon piece plan requires at least one room or corridor piece");
        }

        boolean containsPrimaryEntry = pieces.stream()
                .anyMatch(piece -> piece.primaryEntry()
                        && piece.id().equals(primaryEntryRoomId)
                        && piece.role() == io.github.naimjeg.obeliskdepths.worldgen.structure.ObeliskDungeonPieceRole.START_ROOM);

        if (!containsPrimaryEntry) {
            throw new IllegalArgumentException("Dungeon piece plan missing authoritative primary START room: " + primaryEntryRoomId);
        }
    }

    public long roomCount() {
        return this.pieces.stream()
                .filter(piece -> piece.role().isRoom())
                .count();
    }

    public long corridorCount() {
        return this.pieces.stream()
                .filter(piece -> piece.role() == io.github.naimjeg.obeliskdepths.worldgen.structure.ObeliskDungeonPieceRole.CORRIDOR)
                .count();
    }

    public DungeonPiecePlan translatedY(int offset) {
        return this.translated(0, offset, 0);
    }

    public DungeonPiecePlan translated(
            int offsetX,
            int offsetY,
            int offsetZ
    ) {
        if (offsetX == 0 && offsetY == 0 && offsetZ == 0) {
            return this;
        }
        return new DungeonPiecePlan(
                this.layoutOrigin.offset(offsetX, offsetY, offsetZ),
                translate(this.siteBounds, offsetX, offsetY, offsetZ),
                this.primaryEntryRoomId,
                this.primaryEntryAnchor.offset(offsetX, offsetY, offsetZ),
                this.routedCorridors,
                this.pieces.stream()
                        .map(piece -> piece.translated(offsetX, offsetY, offsetZ))
                        .toList()
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
