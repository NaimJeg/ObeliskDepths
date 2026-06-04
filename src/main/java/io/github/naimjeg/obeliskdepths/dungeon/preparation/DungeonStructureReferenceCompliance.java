package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.worldgen.structure.piece.DungeonPieceMetadata;
import io.github.naimjeg.obeliskdepths.worldgen.structure.piece.DungeonPiecePlan;
import java.util.List;
import net.minecraft.world.level.ChunkPos;

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
