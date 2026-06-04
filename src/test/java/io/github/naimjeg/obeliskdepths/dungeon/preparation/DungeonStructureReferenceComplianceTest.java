package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import java.util.List;
import java.util.Optional;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class DungeonStructureReferenceComplianceTest {
    private DungeonStructureReferenceComplianceTest() {
    }

    public static void main(String[] args) {
        pieceAtDistanceExactlyEightIsAccepted();
        pieceAtDistanceNineIsRejected();
        negativeStartChunksUseFloorChunkMath();
        pieceSpanningChunksEightAndNineIsRejected();
        seventeenChunkWideFootprintFitsWhenCentered();
        eighteenChunkWideFootprintCannotFitByTranslation();
        logicalSiteBoundsBeyondEnvelopeDoNotMatter();
        corridorApronBoundsAreIncluded();
        exactlyOnePrimaryEntryIsRequired();
    }

    private static void pieceAtDistanceExactlyEightIsAccepted() {
        DungeonStructureReferenceCompliance compliance = compliance(
                chunk(0, 0),
                List.of(piece("start", box(0, 64, 0, 15, 70, 15), true),
                        piece("edge", box(128, 64, 0, 143, 70, 15), false))
        );
        assertTrue(compliance.compliant(), "distance 8 should be accepted");
    }

    private static void pieceAtDistanceNineIsRejected() {
        DungeonStructureReferenceCompliance compliance = compliance(
                chunk(0, 0),
                List.of(piece("start", box(0, 64, 0, 15, 70, 15), true),
                        piece("edge", box(144, 64, 0, 159, 70, 15), false))
        );
        assertFalse(compliance.compliant(), "distance 9 should be rejected");
    }

    private static void negativeStartChunksUseFloorChunkMath() {
        DungeonStructureReferenceCompliance compliance = compliance(
                chunk(-3, -3),
                List.of(piece("start", box(-48, 64, -48, -33, 70, -33), true),
                        piece("negative", box(-176, 64, -176, -161, 70, -161), false))
        );
        assertTrue(compliance.compliant(), "negative chunks at distance 8 should be accepted");
    }

    private static void pieceSpanningChunksEightAndNineIsRejected() {
        DungeonStructureReferenceCompliance compliance = compliance(
                chunk(0, 0),
                List.of(piece("start", box(0, 64, 0, 15, 70, 15), true),
                        piece("span", box(143, 64, 0, 144, 70, 15), false))
        );
        assertFalse(compliance.compliant(), "piece touching chunk 9 should be rejected");
    }

    private static void seventeenChunkWideFootprintFitsWhenCentered() {
        DungeonStructureReferenceEnvelope envelope =
                DungeonStructureReferenceEnvelope.vanilla(chunk(0, 0));
        assertTrue(
                envelope.contains(box(-128, 64, 0, 143, 70, 15)),
                "17 chunk footprint should fit the inclusive envelope"
        );
        Optional<DungeonStructureReferenceEnvelope.ChunkAlignedBlockOffset> offset =
                envelope.chunkAlignedOffsetToContain(
                        new DungeonStructureDistanceReport.ChunkBounds(-9, 0, 7, 0)
                );
        assertTrue(offset.isPresent(), "17 chunk footprint should be translatable");
        assertEquals(16, offset.get().x(), "17 chunk footprint offset x");
    }

    private static void eighteenChunkWideFootprintCannotFitByTranslation() {
        DungeonStructureReferenceEnvelope envelope =
                DungeonStructureReferenceEnvelope.vanilla(chunk(0, 0));
        Optional<DungeonStructureReferenceEnvelope.ChunkAlignedBlockOffset> offset =
                envelope.chunkAlignedOffsetToContain(
                        new DungeonStructureDistanceReport.ChunkBounds(-9, 0, 8, 0)
                );
        assertFalse(offset.isPresent(), "18 chunk footprint cannot be translated into 17 chunks");
    }

    private static void logicalSiteBoundsBeyondEnvelopeDoNotMatter() {
        DungeonStructureReferenceCompliance compliance = compliance(
                chunk(0, 0),
                List.of(piece("start", box(0, 64, 0, 15, 70, 15), true),
                        piece("room", box(128, 64, 0, 143, 70, 15), false))
        );
        DungeonStructureReferenceEnvelope envelope =
                DungeonStructureReferenceEnvelope.vanilla(chunk(0, 0));
        assertFalse(
                envelope.contains(new DungeonStructureDistanceReport.ChunkBounds(-9, -9, 9, 9)),
                "inflated logical site bounds may exceed the envelope"
        );
        assertTrue(
                compliance.compliant(),
                "only emitted piece bounds should control reference compliance"
        );
    }

    private static void corridorApronBoundsAreIncluded() {
        DungeonStructureReferenceCompliance compliance = compliance(
                chunk(0, 0),
                List.of(piece("start", box(0, 64, 0, 15, 70, 15), true),
                        piece("corridor:edge_apron_0", box(144, 64, 0, 151, 68, 7), false))
        );
        assertFalse(compliance.compliant(), "corridor apron bounds must be checked");
    }

    private static void exactlyOnePrimaryEntryIsRequired() {
        assertFalse(
                compliance(
                        chunk(0, 0),
                        List.of(piece("room", box(0, 64, 0, 15, 70, 15), false))
                ).compliant(),
                "missing primary entry must be rejected"
        );
        assertFalse(
                compliance(
                        chunk(0, 0),
                        List.of(piece("start_a", box(0, 64, 0, 15, 70, 15), true),
                                piece("start_b", box(16, 64, 0, 31, 70, 15), true))
                ).compliant(),
                "duplicate primary entry must be rejected"
        );
    }

    private static DungeonStructureReferenceCompliance compliance(
            DungeonStructureDistanceReport.ChunkCoordinate startChunk,
            List<DungeonStructureDistanceValidator.PieceBounds> pieces
    ) {
        return DungeonStructureReferenceCompliance.analyze(startChunk, pieces);
    }

    private static DungeonStructureDistanceReport.ChunkCoordinate chunk(
            int x,
            int z
    ) {
        return new DungeonStructureDistanceReport.ChunkCoordinate(x, z);
    }

    private static DungeonStructureDistanceValidator.PieceBounds piece(
            String label,
            BoundingBox bounds,
            boolean primaryEntry
    ) {
        return new DungeonStructureDistanceValidator.PieceBounds(
                label,
                bounds,
                primaryEntry
        );
    }

    private static BoundingBox box(
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ
    ) {
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static void assertTrue(
            boolean value,
            String message
    ) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(
            boolean value,
            String message
    ) {
        if (value) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(
            Object expected,
            Object actual,
            String message
    ) {
        if (!expected.equals(actual)) {
            throw new AssertionError(
                    message + ": expected=" + expected + ", actual=" + actual
            );
        }
    }
}
