package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;
import java.util.Optional;

/**
 * Tests for {@link DungeonStructureReferenceCompliance} covering distance
 * boundaries, primary-entry cardinality, effective bounds, and translation.
 *
 * <p>Uses the pure list-based {@code analyze(ChunkCoordinate, List<PieceBounds>)}
 * API to avoid constructing a full {@code DungeonPiecePlan} for zero-piece cases.
 * Real piece-metadata-based tests go through the
 * {@code analyze(ChunkPos, DungeonPiecePlan)} entry point.</p>
 */
public final class DungeonStructureReferenceComplianceTest {
    private DungeonStructureReferenceComplianceTest() {
    }

    public static void main(String[] args) {
        distanceBoundary_touchingDistance8_isCompliant();
        distanceBoundary_distance9_isRejected();
        distanceBoundary_maximumPieceDistance_reportsExpected();
        distanceBoundary_outsidePieceDiagnostics_includeViolatingPiece();
        primaryEntry_exactlyOne_isCompliant();
        primaryEntry_zero_isRejected();
        primaryEntry_multiple_isRejected();
        effectiveBounds_insideEnvelope_isAccepted();
        effectiveBounds_oneBlockBeyond_isRejected();
        effectiveBounds_verticalExpansion_doesNotAffectHorizontal();
        translation_alreadyFitting_returnsZeroOffset();
        translation_translatablePlan_returnsNonzeroOffset();
        translation_widerThanEnvelope_returnsNoOffset();
        translation_afterApplyingOffset_everyPieceInside();
        piecePlan_basedCompliance_happyPath();
        pieceCount_reportedCorrectly();
    }

    // ── distance boundary ──

    private static void distanceBoundary_touchingDistance8_isCompliant() {
        DungeonStructureReferenceCompliance compliance =
                DungeonStructureReferenceCompliance.analyze(
                        chunk(4, -3),
                        List.of(
                                piece("start", box(4 * 16, -3 * 16, 4 * 16 + 15, -3 * 16 + 15), true),
                                piece("edge", box(12 * 16, -11 * 16, 12 * 16 + 15, -11 * 16 + 15), false)
                        )
                );

        assertTrue(compliance.compliant(), "distance-8 edge piece is compliant");
        assertEquals(8, compliance.maximumPieceDistance(), "maximumPieceDistance=8");
    }

    private static void distanceBoundary_distance9_isRejected() {
        DungeonStructureReferenceCompliance compliance =
                DungeonStructureReferenceCompliance.analyze(
                        chunk(0, 0),
                        List.of(
                                piece("start", box(0, 0, 15, 15), true),
                                piece("far", box(9 * 16, 0, 9 * 16 + 15, 15), false)
                        )
                );

        assertFalse(compliance.compliant(), "distance-9 piece must be rejected");
        assertEquals(9, compliance.maximumPieceDistance(), "maximumPieceDistance=9");
        assertEquals(1, compliance.outsidePieces().size(), "one outside piece");
        assertEquals("far", compliance.outsidePieces().getFirst().label(), "outside piece label");
    }

    private static void distanceBoundary_maximumPieceDistance_reportsExpected() {
        DungeonStructureReferenceCompliance compliance =
                DungeonStructureReferenceCompliance.analyze(
                        chunk(5, 5),
                        List.of(
                                piece("near", box(5 * 16, 5 * 16, 5 * 16 + 15, 5 * 16 + 15), true),
                                piece("mid", box(8 * 16, 5 * 16, 8 * 16 + 15, 5 * 16 + 15), false),
                                piece("far", box(5 * 16, 12 * 16, 5 * 16 + 15, 12 * 16 + 15), false)
                        )
                );

        assertEquals(7, compliance.maximumPieceDistance(), "maximum distance is 7");
        assertTrue(compliance.compliant(), "all pieces within distance 8");
    }

    private static void distanceBoundary_outsidePieceDiagnostics_includeViolatingPiece() {
        DungeonStructureReferenceCompliance compliance =
                DungeonStructureReferenceCompliance.analyze(
                        chunk(0, 0),
                        List.of(
                                piece("start", box(0, 0, 15, 15), true),
                                piece("violator", box(0, -10 * 16, 15, -10 * 16 + 15), false)
                        )
                );

        assertFalse(compliance.compliant(), "distance-10 piece must be rejected");
        assertEquals(1, compliance.outsidePieces().size(), "one outside piece");
        assertEquals("violator", compliance.outsidePieces().getFirst().label(),
                "violating piece label");
        assertTrue(
                compliance.outsidePieces().getFirst().label().equals("violator"),
                "outside pieces includes violator label"
        );
    }

    // ── primary-entry cardinality ──

    private static void primaryEntry_exactlyOne_isCompliant() {
        DungeonStructureReferenceCompliance compliance =
                DungeonStructureReferenceCompliance.analyze(
                        chunk(0, 0),
                        List.of(
                                piece("primary", box(0, 0, 15, 15), true),
                                piece("side", box(16, 0, 31, 15), false)
                        )
                );

        assertTrue(compliance.compliant(), "exactly one primary entry is compliant");
        assertEquals(1, compliance.primaryEntryPieceCount(), "primary count is 1");
    }

    private static void primaryEntry_zero_isRejected() {
        DungeonStructureReferenceCompliance compliance =
                DungeonStructureReferenceCompliance.analyze(
                        chunk(0, 0),
                        List.of(
                                piece("a", box(0, 0, 15, 15), false),
                                piece("b", box(16, 0, 31, 15), false)
                        )
                );

        assertFalse(compliance.compliant(), "zero primary entries is rejected");
        assertEquals(0, compliance.primaryEntryPieceCount(), "primary count is 0");
    }

    private static void primaryEntry_multiple_isRejected() {
        DungeonStructureReferenceCompliance compliance =
                DungeonStructureReferenceCompliance.analyze(
                        chunk(0, 0),
                        List.of(
                                piece("primary-a", box(0, 0, 15, 15), true),
                                piece("primary-b", box(16, 0, 31, 15), true)
                        )
                );

        assertFalse(compliance.compliant(), "multiple primary entries is rejected");
        assertEquals(2, compliance.primaryEntryPieceCount(), "primary count is 2");
    }

    // ── effective bounds ──

    private static void effectiveBounds_insideEnvelope_isAccepted() {
        DungeonStructureReferenceCompliance compliance =
                DungeonStructureReferenceCompliance.analyze(
                        chunk(0, 0),
                        List.of(
                                piece("start", box(0, 0, 15, 15), true)
                        )
                );

        BoundingBox effective = compliance.overallPieceBlockBounds();
        assertTrue(
                compliance.effectiveStartBoundsCompliant(effective),
                "bounds inside envelope must be compliant"
        );
    }

    private static void effectiveBounds_oneBlockBeyond_isRejected() {
        DungeonStructureReferenceCompliance compliance =
                DungeonStructureReferenceCompliance.analyze(
                        chunk(0, 0),
                        List.of(
                                piece("start", box(0, 0, 15, 15), true)
                        )
                );

        BoundingBox original = compliance.overallPieceBlockBounds();
        // Expand one block beyond the max envelope boundary
        BoundingBox expanded = new BoundingBox(
                original.minX(),
                original.minY(),
                original.minZ(),
                original.maxX() + (8 * 16 + 1),
                original.maxY(),
                original.maxZ()
        );

        assertFalse(
                compliance.effectiveStartBoundsCompliant(expanded),
                "bounds extending beyond envelope must be rejected"
        );
    }

    private static void effectiveBounds_verticalExpansion_doesNotAffectHorizontal() {
        DungeonStructureReferenceCompliance compliance =
                DungeonStructureReferenceCompliance.analyze(
                        chunk(0, 0),
                        List.of(
                                piece("start", box(0, 0, 15, 15), true)
                        )
                );

        BoundingBox original = compliance.overallPieceBlockBounds();
        // Y-only expansion should not trigger a horizontal containment failure
        BoundingBox taller = new BoundingBox(
                original.minX(),
                original.minY() - 16,
                original.minZ(),
                original.maxX(),
                original.maxY() + 16,
                original.maxZ()
        );

        assertTrue(
                compliance.effectiveStartBoundsCompliant(taller),
                "vertical-only expansion must not trigger horizontal rejection"
        );
    }

    // ── translation ──

    private static void translation_alreadyFitting_returnsZeroOffset() {
        DungeonStructureDistanceReport.ChunkCoordinate start = chunk(0, 0);
        DungeonStructureReferenceEnvelope envelope =
                DungeonStructureReferenceEnvelope.vanilla(start);

        DungeonStructureDistanceReport.ChunkBounds pieceChunks =
                new DungeonStructureDistanceReport.ChunkBounds(0, 0, 1, 1);

        Optional<DungeonStructureReferenceEnvelope.ChunkAlignedBlockOffset> offset =
                envelope.chunkAlignedOffsetToContain(pieceChunks);

        assertTrue(offset.isPresent(), "fitting footprint produces an offset");
        assertTrue(offset.orElseThrow().isZero(), "already-fitting plan gives zero offset");
    }

    private static void translation_translatablePlan_returnsNonzeroOffset() {
        DungeonStructureDistanceReport.ChunkCoordinate start = chunk(0, 0);
        DungeonStructureReferenceEnvelope envelope =
                DungeonStructureReferenceEnvelope.vanilla(start);

        DungeonStructureDistanceReport.ChunkBounds shiftedBounds =
                new DungeonStructureDistanceReport.ChunkBounds(-10, 0, -9, 1);

        Optional<DungeonStructureReferenceEnvelope.ChunkAlignedBlockOffset> offset =
                envelope.chunkAlignedOffsetToContain(shiftedBounds);

        assertTrue(offset.isPresent(), "translatable footprint produces an offset");
        assertEquals(2 * 16, offset.orElseThrow().x(),
                "offset must shift the plan back into the envelope");
        assertEquals(0, offset.orElseThrow().z(), "no z shift needed");
    }

    private static void translation_widerThanEnvelope_returnsNoOffset() {
        DungeonStructureDistanceReport.ChunkCoordinate start = chunk(0, 0);
        DungeonStructureReferenceEnvelope envelope =
                DungeonStructureReferenceEnvelope.vanilla(start);

        // Footprint spans 18 chunks wide, exceeding 17-chunk envelope width
        DungeonStructureDistanceReport.ChunkBounds tooWide =
                new DungeonStructureDistanceReport.ChunkBounds(-9, 0, 8, 1);

        Optional<DungeonStructureReferenceEnvelope.ChunkAlignedBlockOffset> offset =
                envelope.chunkAlignedOffsetToContain(tooWide);

        assertTrue(offset.isEmpty(), "16-chunk-wide footprint cannot be contained");
    }

    private static void translation_afterApplyingOffset_everyPieceInside() {
        DungeonStructureDistanceReport.ChunkCoordinate start = chunk(5, 5);
        DungeonStructureReferenceEnvelope envelope =
                DungeonStructureReferenceEnvelope.vanilla(start);

        // Verify: start chunk (5,5) plus distance 8 gives envelope [-3, -3] .. [13, 13]
        DungeonStructureDistanceReport.ChunkBounds envelopeChunks = envelope.chunkBounds();
        assertEquals(-3, envelopeChunks.minChunkX(), "envelope min chunk x");
        assertEquals(-3, envelopeChunks.minChunkZ(), "envelope min chunk z");
        assertEquals(13, envelopeChunks.maxChunkX(), "envelope max chunk x");
        assertEquals(13, envelopeChunks.maxChunkZ(), "envelope max chunk z");

        // A piece plan shifted to chunks [0,0]..[2,2] fits within [-3,-3]..[13,13]
        DungeonStructureDistanceReport.ChunkBounds shiftedBounds =
                new DungeonStructureDistanceReport.ChunkBounds(0, 0, 2, 2);

        assertTrue(envelope.contains(shiftedBounds),
                "footprint within envelope after applying offset");
    }

    // ── piece-plan integration ──

    private static void piecePlan_basedCompliance_happyPath() {
        DungeonStructureReferenceCompliance compliance =
                DungeonStructureReferenceCompliance.analyze(
                        chunk(0, 0),
                        List.of(
                                piece("start", box(0, 0, 15, 15), true),
                                piece("side", box(16, 0, 31, 15), false)
                        )
                );

        assertTrue(compliance.compliant(), "simple plan is compliant");
        assertEquals(2, compliance.pieceCount(), "two pieces");
        assertEquals(1, compliance.primaryEntryPieceCount(), "one primary");
        assertEquals(1, compliance.maximumPieceDistance(), "max distance 1");
        assertTrue(compliance.outsidePieces().isEmpty(), "no outside pieces");
    }

    private static void pieceCount_reportedCorrectly() {
        DungeonStructureReferenceCompliance compliance =
                DungeonStructureReferenceCompliance.analyze(
                        chunk(0, 0),
                        List.of(
                                piece("a", box(0, 0, 15, 15), true),
                                piece("b", box(32, 0, 47, 15), false),
                                piece("c", box(0, 32, 15, 47), false)
                        )
                );

        assertEquals(3, compliance.pieceCount(), "three pieces counted");
    }

    // ── helpers ──

    private static DungeonStructureDistanceReport.ChunkCoordinate chunk(int x, int z) {
        return new DungeonStructureDistanceReport.ChunkCoordinate(x, z);
    }

    private static DungeonStructureDistanceValidator.PieceBounds piece(
            String label,
            BoundingBox box,
            boolean primaryEntry
    ) {
        return new DungeonStructureDistanceValidator.PieceBounds(label, box, primaryEntry);
    }

    private static BoundingBox box(int minX, int minZ, int maxX, int maxZ) {
        return new BoundingBox(minX, 0, minZ, maxX, 8, maxZ);
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean value, String message) {
        if (value) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(
                    message + ": expected=" + expected + ", actual=" + actual
            );
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(
                    message + ": expected=" + expected + ", actual=" + actual
            );
        }
    }
}
