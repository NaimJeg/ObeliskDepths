package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.worldgen.structure.piece.DungeonPieceMetadata;
import io.github.naimjeg.obeliskdepths.worldgen.structure.piece.DungeonPiecePlan;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;

public record DungeonStructureReferenceCompliance(
        DungeonStructureReferenceEnvelope referenceEnvelope,
        DungeonStructureDistanceReport distanceReport
) {
    public DungeonStructureReferenceCompliance {
        if (referenceEnvelope == null) {
            throw new IllegalArgumentException("Reference envelope must be present.");
        }
        if (distanceReport == null) {
            throw new IllegalArgumentException("Distance report must be present.");
        }
    }

    public static DungeonStructureReferenceCompliance analyze(
            ChunkPos startChunk,
            DungeonPiecePlan piecePlan
    ) {
        if (startChunk == null) {
            throw new IllegalArgumentException("Start chunk must be present.");
        }
        if (piecePlan == null) {
            throw new IllegalArgumentException("Piece plan must be present.");
        }
        DungeonStructureDistanceReport.ChunkCoordinate coordinate =
                new DungeonStructureDistanceReport.ChunkCoordinate(
                        startChunk.x(),
                        startChunk.z()
                );
        List<DungeonStructureDistanceValidator.PieceBounds> pieces =
                piecePlan.pieces()
                        .stream()
                        .map(DungeonStructureReferenceCompliance::pieceBounds)
                        .toList();
        return analyze(coordinate, pieces);
    }

    public static DungeonStructureReferenceCompliance analyze(
            DungeonStructureDistanceReport.ChunkCoordinate startChunk,
            List<DungeonStructureDistanceValidator.PieceBounds> pieces
    ) {
        DungeonStructureDistanceReport report =
                DungeonStructureDistanceValidator.analyzePieces(
                        startChunk,
                        pieces
                );
        return new DungeonStructureReferenceCompliance(
                DungeonStructureReferenceEnvelope.vanilla(startChunk),
                report
        );
    }

    public boolean compliant() {
        return this.distanceReport.safeForEntryOnlyPreparation();
    }

    public int pieceCount() {
        return this.distanceReport.pieceCount();
    }

    public int primaryEntryPieceCount() {
        return this.distanceReport.primaryEntryPieceCount();
    }

    public int maximumPieceDistance() {
        return this.distanceReport.maximumPieceDistance();
    }

    public List<DungeonStructurePieceDistance> outsidePieces() {
        return this.distanceReport.piecesOutsideDistance8();
    }

    public DungeonStructureDistanceReport.ChunkBounds overallPieceChunkBounds() {
        return this.distanceReport.overallChunkBounds();
    }

    public BoundingBox overallPieceBlockBounds() {
        List<DungeonStructurePieceDistance> pieces = this.distanceReport.pieces();
        if (pieces.isEmpty()) {
            throw new IllegalStateException("No piece bounds are available.");
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (DungeonStructurePieceDistance piece : pieces) {
            BoundingBox bounds = piece.blockBounds();
            minX = Math.min(minX, bounds.minX());
            minY = Math.min(minY, bounds.minY());
            minZ = Math.min(minZ, bounds.minZ());
            maxX = Math.max(maxX, bounds.maxX());
            maxY = Math.max(maxY, bounds.maxY());
            maxZ = Math.max(maxZ, bounds.maxZ());
        }
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public boolean effectiveStartBoundsCompliant(BoundingBox effectiveBounds) {
        return this.referenceEnvelope.contains(effectiveBounds);
    }

    public String describeSummary() {
        return this.distanceReport.describeSummary()
                + " envelopeChunks="
                + this.referenceEnvelope.chunkBounds()
                + " envelopeBlocks=["
                + this.referenceEnvelope.minBlockX()
                + ","
                + this.referenceEnvelope.minBlockZ()
                + ".."
                + this.referenceEnvelope.maxBlockX()
                + ","
                + this.referenceEnvelope.maxBlockZ()
                + "]";
    }

    private static DungeonStructureDistanceValidator.PieceBounds pieceBounds(
            DungeonPieceMetadata piece
    ) {
        return new DungeonStructureDistanceValidator.PieceBounds(
                piece.role().serializedName() + ":" + piece.id(),
                piece.bounds(),
                piece.primaryEntry()
        );
    }
}
