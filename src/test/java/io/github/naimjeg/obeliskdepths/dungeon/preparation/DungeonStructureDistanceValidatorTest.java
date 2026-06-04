package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class DungeonStructureDistanceValidatorTest {
    private DungeonStructureDistanceValidatorTest() {
    }

    public static void main(String[] args) {
        pieceWhollyInsideStartChunkIsSafe();
        pieceTouchingDistanceEightIsSafe();
        pieceTouchingDistanceNineIsUnsafe();
        negativeBlockCoordinatesUseFloorChunkMath();
        spanningPieceUsesFarthestTouchedChunk();
        overallBoundsIncludeStartChunkAndAllPieces();
        noPiecesAreNotSafe();
        noPrimaryEntryIsNotSafe();
        exactlyOnePrimaryEntryIsSafeWhenDistanceIsSafe();
        multiplePrimaryEntriesAreNotSafe();
        genericPiecesAreNonPrimary();
        reportListsAreImmutable();
        reportConstructorRejectsInconsistentInputs();
        reportConstructorAcceptsValidDiagnostics();
        nullInputsFailClearly();
        hardValidationThrowsForUnsafeDistance();
    }

    private static void pieceWhollyInsideStartChunkIsSafe() {
        DungeonStructureDistanceReport report = analyze(
                chunk(0, 0),
                piece("start", box(0, 0, 15, 15), true)
        );

        assertEquals(1, report.pieceCount(), "piece count");
        assertEquals(1, report.primaryEntryPieceCount(), "primary count");
        assertEquals(0, report.maximumPieceDistance(), "maximum distance");
        assertEquals(OptionalInt.of(0), report.primaryEntryDistance(),
                "primary entry distance");
        assertTrue(report.safeForEntryOnlyPreparation(), "start chunk piece safe");
    }

    private static void pieceTouchingDistanceEightIsSafe() {
        DungeonStructureDistanceReport report = analyze(
                chunk(4, -3),
                piece(
                        "boundary",
                        box(12 * 16, -11 * 16, 12 * 16 + 15, -11 * 16 + 15),
                        true
                )
        );

        assertEquals(
                DungeonStructureDistanceValidator.VANILLA_REFERENCE_DISTANCE_CHUNKS,
                report.maximumPieceDistance(),
                "distance eight is included"
        );
        assertTrue(report.withinVanillaReferenceDistance(), "distance eight valid");
        assertTrue(report.safeForEntryOnlyPreparation(),
                "distance eight remains entry-preparation safe");
    }

    private static void pieceTouchingDistanceNineIsUnsafe() {
        DungeonStructureDistanceReport report = analyze(
                chunk(0, 0),
                piece("safe", box(0, 0, 15, 15), true),
                piece("far-east", box(9 * 16, 0, 9 * 16 + 15, 15), false)
        );

        assertEquals(9, report.maximumPieceDistance(), "distance nine reported");
        assertFalse(report.withinVanillaReferenceDistance(), "distance nine invalid");
        assertFalse(report.safeForEntryOnlyPreparation(), "distance nine unsafe");
        assertEquals(1, report.piecesOutsideDistance8().size(), "outside count");
        assertEquals("far-east", report.piecesOutsideDistance8().getFirst().label(),
                "outside piece label");
    }

    private static void negativeBlockCoordinatesUseFloorChunkMath() {
        DungeonStructureDistanceReport report = analyze(
                chunk(0, 0),
                piece("minus-one", box(-1, -1, -1, -1), true),
                piece("minus-sixteen", box(-16, -16, -16, -16), false),
                piece("minus-seventeen", box(-17, -17, -17, -17), false)
        );

        assertEquals(
                new DungeonStructureDistanceReport.ChunkBounds(-2, -2, 0, 0),
                report.overallChunkBounds(),
                "negative floor-div chunk bounds"
        );
        assertEquals(2, report.maximumPieceDistance(),
                "-17 belongs to chunk -2");
        assertTrue(report.safeForEntryOnlyPreparation(),
                "negative chunks remain safe inside distance eight");
    }

    private static void spanningPieceUsesFarthestTouchedChunk() {
        DungeonStructureDistanceReport report = analyze(
                chunk(0, 0),
                piece("wide", box(-16, -16, 8 * 16 + 15, 15), true)
        );

        assertEquals(8, report.maximumPieceDistance(),
                "farthest touched chunk determines distance");
        assertEquals(
                new DungeonStructureDistanceReport.ChunkBounds(-1, -1, 8, 0),
                report.pieces().getFirst().chunkBounds(),
                "spanning piece chunk bounds"
        );
        assertEquals(OptionalInt.of(8), report.primaryEntryDistance(),
                "primary entry spanning distance");
    }

    private static void overallBoundsIncludeStartChunkAndAllPieces() {
        DungeonStructureDistanceReport report = analyze(
                chunk(10, 10),
                piece("west", box(8 * 16, 10 * 16, 8 * 16 + 15, 10 * 16 + 15), true),
                piece("south", box(10 * 16, 12 * 16, 10 * 16 + 15, 12 * 16 + 15), false)
        );

        assertEquals(
                new DungeonStructureDistanceReport.ChunkBounds(8, 10, 10, 12),
                report.overallChunkBounds(),
                "overall bounds include start and all pieces"
        );
    }

    private static void noPiecesAreNotSafe() {
        DungeonStructureDistanceReport report =
                DungeonStructureDistanceValidator.analyzePieces(
                        chunk(7, -4),
                        List.of()
                );

        assertEquals(0, report.pieceCount(), "empty piece count");
        assertEquals(0, report.primaryEntryPieceCount(), "empty primary count");
        assertFalse(report.hasPieces(), "empty report has no pieces");
        assertFalse(report.hasExactlyOnePrimaryEntry(), "empty report has no primary");
        assertFalse(report.safeForEntryOnlyPreparation(), "empty report not safe");
        assertEquals(OptionalInt.empty(), report.primaryEntryDistance(),
                "empty primary distance");
        assertContains(report.describeSummary(), "pieceCount=0",
                "summary reports empty piece count");
    }

    private static void noPrimaryEntryIsNotSafe() {
        DungeonStructureDistanceReport report = analyze(
                chunk(0, 0),
                piece("generic", box(0, 0, 15, 15), false)
        );

        assertEquals(1, report.pieceCount(), "piece count");
        assertEquals(0, report.primaryEntryPieceCount(), "primary count");
        assertFalse(report.hasExactlyOnePrimaryEntry(), "missing primary");
        assertFalse(report.safeForEntryOnlyPreparation(), "missing primary unsafe");
    }

    private static void exactlyOnePrimaryEntryIsSafeWhenDistanceIsSafe() {
        DungeonStructureDistanceReport report = analyze(
                chunk(0, 0),
                piece("start", box(0, 0, 15, 15), true),
                piece("generic", box(16, 0, 31, 15), false)
        );

        assertTrue(report.hasExactlyOnePrimaryEntry(), "one primary");
        assertTrue(report.safeForEntryOnlyPreparation(), "one primary safe");
    }

    private static void multiplePrimaryEntriesAreNotSafe() {
        DungeonStructureDistanceReport report = analyze(
                chunk(0, 0),
                piece("start-a", box(0, 0, 15, 15), true),
                piece("start-b", box(16, 0, 31, 15), true)
        );

        assertEquals(2, report.primaryEntryPieceCount(), "duplicate primary count");
        assertFalse(report.hasExactlyOnePrimaryEntry(), "duplicate primary");
        assertFalse(report.safeForEntryOnlyPreparation(), "duplicate primary unsafe");
    }

    private static void genericPiecesAreNonPrimary() {
        DungeonStructureDistanceReport report = analyze(
                chunk(0, 0),
                generic("generic-structure-piece", box(0, 0, 15, 15))
        );

        assertEquals(1, report.pieceCount(), "generic piece count");
        assertEquals(0, report.primaryEntryPieceCount(),
                "generic pieces are non-primary unless marked otherwise");
        assertFalse(report.safeForEntryOnlyPreparation(), "generic-only unsafe");
    }

    private static void reportListsAreImmutable() {
        DungeonStructureDistanceReport report = analyze(
                chunk(0, 0),
                piece("start", box(0, 0, 15, 15), true)
        );

        assertThrows(
                UnsupportedOperationException.class,
                () -> report.pieces().add(new DungeonStructurePieceDistance(
                        "extra",
                        box(16, 0, 31, 15),
                        new DungeonStructureDistanceReport.ChunkBounds(1, 0, 1, 0),
                        1,
                        false
                )),
                "piece list immutable"
        );
        assertThrows(
                UnsupportedOperationException.class,
                () -> report.piecesOutsideDistance8().add(
                        report.pieces().getFirst()
                ),
                "outside list immutable"
        );
    }

    private static void reportConstructorRejectsInconsistentInputs() {
        DungeonStructurePieceDistance primary =
                distance("primary", 0, true, bounds(0, 0, 0, 0));
        DungeonStructurePieceDistance outside =
                distance("outside", 9, false, bounds(9, 0, 9, 0));
        List<DungeonStructurePieceDistance> pieces = List.of(primary, outside);
        List<DungeonStructurePieceDistance> outsidePieces = List.of(outside);
        DungeonStructureDistanceReport.ChunkBounds overall = bounds(0, 0, 9, 0);

        assertThrows(
                IllegalArgumentException.class,
                () -> report(3, 1, 9, outsidePieces, OptionalInt.of(0), overall, pieces),
                "constructor: piece-count mismatch"
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> report(2, 0, 9, outsidePieces, OptionalInt.of(0), overall, pieces),
                "constructor: primary-count mismatch"
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> report(2, 1, 9, List.of(), OptionalInt.of(0), overall, pieces),
                "constructor: outside subset mismatch"
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> report(2, 1, 8, outsidePieces, OptionalInt.of(0), overall, pieces),
                "constructor: maximum-distance mismatch"
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> report(2, 1, 9, outsidePieces, OptionalInt.empty(), overall, pieces),
                "constructor: primary-distance mismatch"
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> report(2, 1, 9, outsidePieces, OptionalInt.of(0),
                        bounds(0, 0, 8, 0), pieces),
                "constructor: overall-bounds mismatch"
        );
    }

    private static void reportConstructorAcceptsValidDiagnostics() {
        DungeonStructureDistanceReport empty = report(
                0,
                0,
                0,
                List.of(),
                OptionalInt.empty(),
                bounds(0, 0, 0, 0),
                List.of()
        );
        assertFalse(empty.hasPieces(), "constructor: valid empty report");

        DungeonStructurePieceDistance primary =
                distance("primary", 2, true, bounds(2, 0, 2, 0));
        DungeonStructureDistanceReport oneEntry = report(
                1,
                1,
                2,
                List.of(),
                OptionalInt.of(2),
                bounds(0, 0, 2, 0),
                List.of(primary)
        );
        assertTrue(oneEntry.safeForEntryOnlyPreparation(),
                "constructor: valid one-entry report");

        DungeonStructurePieceDistance nearPrimary =
                distance("near-primary", 1, true, bounds(1, 0, 1, 0));
        DungeonStructurePieceDistance farPrimary =
                distance("far-primary", 4, true, bounds(4, 0, 4, 0));
        DungeonStructureDistanceReport multiple = report(
                2,
                2,
                4,
                List.of(),
                OptionalInt.of(4),
                bounds(0, 0, 4, 0),
                List.of(nearPrimary, farPrimary)
        );
        assertEquals(2, multiple.primaryEntryPieceCount(),
                "constructor: valid multiple-primary count");
        assertEquals(OptionalInt.of(4), multiple.primaryEntryDistance(),
                "constructor: multiple-primary maximum distance");
    }

    private static void nullInputsFailClearly() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DungeonStructureDistanceValidator.analyzePieces(null, List.of()),
                "null start chunk rejected"
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> DungeonStructureDistanceValidator.analyzePieces(chunk(0, 0), null),
                "null piece list rejected"
        );
        List<DungeonStructureDistanceValidator.PieceBounds> pieces =
                new ArrayList<>();
        pieces.add(null);
        assertThrows(
                IllegalArgumentException.class,
                () -> DungeonStructureDistanceValidator.analyzePieces(chunk(0, 0), pieces),
                "null piece entry rejected"
        );
    }

    private static void hardValidationThrowsForUnsafeDistance() {
        DungeonStructureDistanceReport report = analyze(
                chunk(0, 0),
                piece("too-far", box(0, -9 * 16, 15, -9 * 16 + 15), true)
        );

        assertThrows(
                IllegalStateException.class,
                () -> DungeonStructureDistanceValidator
                        .requireWithinVanillaReferenceDistance(report),
                "hard validation must reject unsafe structures"
        );
    }

    private static DungeonStructureDistanceReport analyze(
            DungeonStructureDistanceReport.ChunkCoordinate startChunk,
            DungeonStructureDistanceValidator.PieceBounds... pieces
    ) {
        return DungeonStructureDistanceValidator.analyzePieces(
                startChunk,
                List.of(pieces)
        );
    }

    private static DungeonStructureDistanceValidator.PieceBounds piece(
            String label,
            BoundingBox box,
            boolean primaryEntry
    ) {
        return new DungeonStructureDistanceValidator.PieceBounds(
                label,
                box,
                primaryEntry
        );
    }

    private static DungeonStructureDistanceValidator.PieceBounds generic(
            String label,
            BoundingBox box
    ) {
        return piece(label, box, false);
    }

    private static DungeonStructureDistanceReport.ChunkCoordinate chunk(
            int x,
            int z
    ) {
        return new DungeonStructureDistanceReport.ChunkCoordinate(x, z);
    }

    private static DungeonStructureDistanceReport report(
            int pieceCount,
            int primaryEntryPieceCount,
            int maximumPieceDistance,
            List<DungeonStructurePieceDistance> outside,
            OptionalInt primaryEntryDistance,
            DungeonStructureDistanceReport.ChunkBounds overall,
            List<DungeonStructurePieceDistance> pieces
    ) {
        return new DungeonStructureDistanceReport(
                chunk(0, 0),
                pieceCount,
                primaryEntryPieceCount,
                maximumPieceDistance,
                outside,
                primaryEntryDistance,
                overall,
                pieces
        );
    }

    private static DungeonStructurePieceDistance distance(
            String label,
            int distance,
            boolean primaryEntry,
            DungeonStructureDistanceReport.ChunkBounds chunkBounds
    ) {
        return new DungeonStructurePieceDistance(
                label,
                box(chunkBounds.minChunkX() * 16, chunkBounds.minChunkZ() * 16,
                        chunkBounds.maxChunkX() * 16 + 15,
                        chunkBounds.maxChunkZ() * 16 + 15),
                chunkBounds,
                distance,
                primaryEntry
        );
    }

    private static DungeonStructureDistanceReport.ChunkBounds bounds(
            int minX,
            int minZ,
            int maxX,
            int maxZ
    ) {
        return new DungeonStructureDistanceReport.ChunkBounds(minX, minZ, maxX, maxZ);
    }

    private static BoundingBox box(
            int minX,
            int minZ,
            int maxX,
            int maxZ
    ) {
        return new BoundingBox(minX, 0, minZ, maxX, 8, maxZ);
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

    private static void assertContains(
            String text,
            String token,
            String message
    ) {
        if (!text.contains(token)) {
            throw new AssertionError(message + ": missing " + token);
        }
    }

    private static void assertThrows(
            Class<? extends RuntimeException> expectedType,
            Runnable action,
            String message
    ) {
        try {
            action.run();
        } catch (RuntimeException expected) {
            if (expectedType.isInstance(expected)) {
                return;
            }
            throw new AssertionError(
                    message
                            + ": expected="
                            + expectedType.getSimpleName()
                            + " actual="
                            + expected.getClass().getSimpleName(),
                    expected
            );
        }
        throw new AssertionError(
                message + ": expected=" + expectedType.getSimpleName()
        );
    }
}
