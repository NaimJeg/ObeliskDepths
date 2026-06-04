package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import java.util.List;
import java.util.OptionalInt;

public record DungeonStructureDistanceReport(
        ChunkCoordinate startChunk,
        int pieceCount,
        int primaryEntryPieceCount,
        int maximumPieceDistance,
        List<DungeonStructurePieceDistance> piecesOutsideDistance8,
        OptionalInt primaryEntryDistance,
        ChunkBounds overallChunkBounds,
        List<DungeonStructurePieceDistance> pieces
) {
    public DungeonStructureDistanceReport {
        if (startChunk == null) {
            throw new IllegalArgumentException("Start chunk must be present.");
        }
        if (pieceCount < 0) {
            throw new IllegalArgumentException(
                    "Piece count must be non-negative: " + pieceCount
            );
        }
        if (primaryEntryPieceCount < 0) {
            throw new IllegalArgumentException(
                    "Primary entry piece count must be non-negative: "
                            + primaryEntryPieceCount
            );
        }
        if (maximumPieceDistance < 0) {
            throw new IllegalArgumentException(
                    "Maximum piece distance must be non-negative: "
                            + maximumPieceDistance
            );
        }
        if (piecesOutsideDistance8 == null) {
            throw new IllegalArgumentException(
                    "Outside-distance piece list must be present."
            );
        }
        piecesOutsideDistance8 = List.copyOf(piecesOutsideDistance8);
        primaryEntryDistance = primaryEntryDistance == null
                ? OptionalInt.empty()
                : primaryEntryDistance;
        if (overallChunkBounds == null) {
            throw new IllegalArgumentException("Overall chunk bounds must be present.");
        }
        if (pieces == null) {
            throw new IllegalArgumentException("Piece distance list must be present.");
        }
        pieces = List.copyOf(pieces);
        if (pieceCount != pieces.size()) {
            throw new IllegalArgumentException(
                    "Piece count does not match piece distance list: count="
                            + pieceCount
                            + " list="
                            + pieces.size()
            );
        }

        int actualPrimaryEntryPieceCount = 0;
        int actualMaximumPieceDistance = 0;
        OptionalInt actualPrimaryEntryDistance = OptionalInt.empty();
        for (DungeonStructurePieceDistance piece : pieces) {
            if (piece == null) {
                throw new IllegalArgumentException(
                        "Piece distance list must not contain null entries."
                );
            }
            actualMaximumPieceDistance = Math.max(
                    actualMaximumPieceDistance,
                    piece.chebyshevDistanceFromStart()
            );
            if (!encloses(overallChunkBounds, piece.chunkBounds())) {
                throw new IllegalArgumentException(
                        "Overall chunk bounds must enclose every piece: bounds="
                                + overallChunkBounds
                                + " piece="
                                + piece
                );
            }
            if (piece.primaryEntry()) {
                actualPrimaryEntryPieceCount++;
                actualPrimaryEntryDistance = actualPrimaryEntryDistance.isPresent()
                        ? OptionalInt.of(Math.max(
                        actualPrimaryEntryDistance.getAsInt(),
                        piece.chebyshevDistanceFromStart()
                ))
                        : OptionalInt.of(piece.chebyshevDistanceFromStart());
            }
        }

        if (primaryEntryPieceCount != actualPrimaryEntryPieceCount) {
            throw new IllegalArgumentException(
                    "Primary entry piece count does not match piece list: count="
                            + primaryEntryPieceCount
                            + " actual="
                            + actualPrimaryEntryPieceCount
            );
        }

        List<DungeonStructurePieceDistance> actualOutsideDistance8 = pieces.stream()
                .filter(piece -> piece.chebyshevDistanceFromStart()
                        > DungeonStructureDistanceValidator.VANILLA_REFERENCE_DISTANCE_CHUNKS)
                .toList();
        if (!piecesOutsideDistance8.equals(actualOutsideDistance8)) {
            throw new IllegalArgumentException(
                    "Outside-distance piece list does not match piece distances."
            );
        }

        if (maximumPieceDistance != actualMaximumPieceDistance) {
            throw new IllegalArgumentException(
                    "Maximum piece distance does not match piece list: maximum="
                            + maximumPieceDistance
                            + " actual="
                            + actualMaximumPieceDistance
            );
        }

        if (primaryEntryDistance.isPresent() != actualPrimaryEntryDistance.isPresent()
                || (primaryEntryDistance.isPresent()
                && primaryEntryDistance.getAsInt()
                != actualPrimaryEntryDistance.getAsInt())) {
            throw new IllegalArgumentException(
                    "Primary entry distance must contain the maximum primary-entry distance."
            );
        }
    }

    private static boolean encloses(ChunkBounds outer, ChunkBounds inner) {
        return outer.minChunkX() <= inner.minChunkX()
                && outer.minChunkZ() <= inner.minChunkZ()
                && outer.maxChunkX() >= inner.maxChunkX()
                && outer.maxChunkZ() >= inner.maxChunkZ();
    }

    public boolean hasPieces() {
        return this.pieceCount > 0;
    }

    public boolean hasExactlyOnePrimaryEntry() {
        return this.primaryEntryPieceCount == 1;
    }

    public boolean withinVanillaReferenceDistance() {
        return this.piecesOutsideDistance8.isEmpty();
    }

    public boolean safeForEntryOnlyPreparation() {
        return this.hasPieces()
                && this.hasExactlyOnePrimaryEntry()
                && this.withinVanillaReferenceDistance();
    }

    public String describeSummary() {
        return "startChunk="
                + this.startChunk
                + " pieceCount="
                + this.pieceCount
                + " primaryEntryPieceCount="
                + this.primaryEntryPieceCount
                + " maximumPieceDistance="
                + this.maximumPieceDistance
                + " primaryEntryDistance="
                + (this.primaryEntryDistance.isPresent()
                ? this.primaryEntryDistance.getAsInt()
                : "<missing>")
                + " overallChunkBounds="
                + this.overallChunkBounds
                + " outsideDistance"
                + DungeonStructureDistanceValidator.VANILLA_REFERENCE_DISTANCE_CHUNKS
                + "="
                + this.piecesOutsideDistance8.size();
    }

    public String describeOutsidePieces() {
        if (this.piecesOutsideDistance8.isEmpty()) {
            return "[]";
        }

        return this.piecesOutsideDistance8.stream()
                .map(piece -> piece.label()
                        + " distance="
                        + piece.chebyshevDistanceFromStart()
                        + " chunkBounds="
                        + piece.chunkBounds()
                        + " blockBounds="
                        + piece.blockBounds()
                        + " primaryEntry="
                        + piece.primaryEntry())
                .toList()
                .toString();
    }

    public record ChunkCoordinate(
            int x,
            int z
    ) {
        @Override
        public String toString() {
            return "[" + this.x + ", " + this.z + "]";
        }
    }

    public record ChunkBounds(
            int minChunkX,
            int minChunkZ,
            int maxChunkX,
            int maxChunkZ
    ) {
        public ChunkBounds {
            if (maxChunkX < minChunkX || maxChunkZ < minChunkZ) {
                throw new IllegalArgumentException(
                        "Chunk bounds are inverted: "
                                + minChunkX
                                + ","
                                + minChunkZ
                                + ".."
                                + maxChunkX
                                + ","
                                + maxChunkZ
                );
            }
        }
    }
}
