package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.worldgen.structure.ObeliskDungeonPiece;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

public final class DungeonStructureDistanceValidator {
    /*
     * Current Minecraft 26.1.2 source:
     * net.minecraft.world.level.chunk.status.ChunkStatus.MAX_STRUCTURE_DISTANCE = 8.
     * Keep this as a plain constant so pure JVM tests do not bootstrap vanilla
     * registries by touching ChunkStatus during class initialization.
     */
    public static final int VANILLA_REFERENCE_DISTANCE_CHUNKS = 8;

    private DungeonStructureDistanceValidator() {
    }

    public static DungeonStructureDistanceReport analyze(StructureStart start) {
        if (start == null) {
            throw new IllegalArgumentException("Structure start must be present.");
        }

        List<PieceBounds> pieces = new ArrayList<>();
        for (StructurePiece piece : start.getPieces()) {
            pieces.add(PieceBounds.from(piece));
        }
        return analyzePieces(
                new DungeonStructureDistanceReport.ChunkCoordinate(
                        start.getChunkPos().x(),
                        start.getChunkPos().z()
                ),
                pieces
        );
    }

    public static DungeonStructureDistanceReport analyzePieces(
            DungeonStructureDistanceReport.ChunkCoordinate startChunk,
            List<PieceBounds> pieces
    ) {
        if (startChunk == null) {
            throw new IllegalArgumentException("Start chunk must be present.");
        }
        if (pieces == null) {
            throw new IllegalArgumentException("Piece bounds list must be present.");
        }

        int minChunkX = startChunk.x();
        int minChunkZ = startChunk.z();
        int maxChunkX = startChunk.x();
        int maxChunkZ = startChunk.z();
        int maximumDistance = 0;
        int primaryEntryPieceCount = 0;
        OptionalInt primaryEntryDistance = OptionalInt.empty();
        List<DungeonStructurePieceDistance> distances = new ArrayList<>();
        List<DungeonStructurePieceDistance> outsideDistance8 = new ArrayList<>();

        for (PieceBounds piece : pieces) {
            if (piece == null) {
                throw new IllegalArgumentException(
                        "Piece bounds list must not contain null entries."
                );
            }

            DungeonStructureDistanceReport.ChunkBounds chunkBounds =
                    chunkBounds(piece.blockBounds());
            int distance = chebyshevDistance(startChunk, chunkBounds);
            DungeonStructurePieceDistance pieceDistance =
                    new DungeonStructurePieceDistance(
                            piece.label(),
                            piece.blockBounds(),
                            chunkBounds,
                            distance,
                            piece.primaryEntry()
                    );

            distances.add(pieceDistance);
            maximumDistance = Math.max(maximumDistance, distance);
            minChunkX = Math.min(minChunkX, chunkBounds.minChunkX());
            minChunkZ = Math.min(minChunkZ, chunkBounds.minChunkZ());
            maxChunkX = Math.max(maxChunkX, chunkBounds.maxChunkX());
            maxChunkZ = Math.max(maxChunkZ, chunkBounds.maxChunkZ());

            if (piece.primaryEntry()) {
                primaryEntryPieceCount++;
                primaryEntryDistance = primaryEntryDistance.isPresent()
                        ? OptionalInt.of(Math.max(
                        primaryEntryDistance.getAsInt(),
                        distance
                ))
                        : OptionalInt.of(distance);
            }

            if (distance > VANILLA_REFERENCE_DISTANCE_CHUNKS) {
                outsideDistance8.add(pieceDistance);
            }
        }

        return new DungeonStructureDistanceReport(
                startChunk,
                distances.size(),
                primaryEntryPieceCount,
                maximumDistance,
                outsideDistance8,
                primaryEntryDistance,
                new DungeonStructureDistanceReport.ChunkBounds(
                        minChunkX,
                        minChunkZ,
                        maxChunkX,
                        maxChunkZ
                ),
                distances
        );
    }

    public static DungeonStructureDistanceReport requireWithinVanillaReferenceDistance(
            StructureStart start
    ) {
        return requireWithinVanillaReferenceDistance(analyze(start));
    }

    public static DungeonStructureDistanceReport requireWithinVanillaReferenceDistance(
            DungeonStructureDistanceReport report
    ) {
        if (!report.withinVanillaReferenceDistance()) {
            throw new IllegalStateException(
                            "Obelisk dungeon pieces exceed vanilla structure reference distance "
                            + VANILLA_REFERENCE_DISTANCE_CHUNKS
                            + ": "
                            + report.describeSummary()
                            + " outsidePieces="
                            + report.describeOutsidePieces()
            );
        }
        return report;
    }

    public static DungeonStructureDistanceReport.ChunkBounds chunkBounds(
            BoundingBox bounds
    ) {
        if (bounds == null) {
            throw new IllegalArgumentException("Block bounds must be present.");
        }
        return new DungeonStructureDistanceReport.ChunkBounds(
                Math.floorDiv(bounds.minX(), 16),
                Math.floorDiv(bounds.minZ(), 16),
                Math.floorDiv(bounds.maxX(), 16),
                Math.floorDiv(bounds.maxZ(), 16)
        );
    }

    private static int chebyshevDistance(
            DungeonStructureDistanceReport.ChunkCoordinate startChunk,
            DungeonStructureDistanceReport.ChunkBounds bounds
    ) {
        int maxDx = Math.max(
                Math.abs(bounds.minChunkX() - startChunk.x()),
                Math.abs(bounds.maxChunkX() - startChunk.x())
        );
        int maxDz = Math.max(
                Math.abs(bounds.minChunkZ() - startChunk.z()),
                Math.abs(bounds.maxChunkZ() - startChunk.z())
        );
        return Math.max(maxDx, maxDz);
    }

    public record PieceBounds(
            String label,
            BoundingBox blockBounds,
            boolean primaryEntry
    ) {
        public PieceBounds {
            if (label == null || label.isBlank()) {
                label = "piece";
            }
            if (blockBounds == null) {
                throw new IllegalArgumentException("Piece block bounds must be present.");
            }
        }

        private static PieceBounds from(StructurePiece piece) {
            if (piece instanceof ObeliskDungeonPiece dungeonPiece) {
                return new PieceBounds(
                        dungeonPiece.role().serializedName()
                                + ":"
                                + dungeonPiece.roomId(),
                        dungeonPiece.getBoundingBox(),
                        dungeonPiece.primaryEntry()
                );
            }

            return new PieceBounds(
                    piece.getClass().getSimpleName(),
                    piece.getBoundingBox(),
                    false
            );
        }
    }
}
