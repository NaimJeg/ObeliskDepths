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
}
