//package io.github.naimjeg.obeliskdepths.dungeon.site.reader;
//
//import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonStructureDistanceReport;
//import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
//import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
//
//import java.util.List;
//import java.util.Optional;
//
///**
// * Tests for {@link LoadedDungeonSiteProjectionResult} factory methods and
// * invariant enforcement.
// */
//public final class LoadedDungeonSiteProjectionResultTest {
//    private LoadedDungeonSiteProjectionResultTest() {
//    }
//
//    public static void main(String[] args) {
//        accepted_hasSiteAndNoFailure();
//        rejected_hasFailureAndNoSite();
//        sitePlusFailure_isRejected();
//        neitherSiteNorFailure_isRejected();
//        nullDistanceReport_isRejected();
//        nullFailure_isRejectedByFactory();
//        acceptedResult_reportsCorrectState();
//    }
//
//    private static void accepted_hasSiteAndNoFailure() {
//        DungeonSite site = site(chunk(0, 0));
//        DungeonStructureDistanceReport report = distanceReport(1, 1, 0);
//
//        LoadedDungeonSiteProjectionResult result =
//                LoadedDungeonSiteProjectionResult.accepted(site, report);
//
//        assertTrue(result.accepted(), "accepted: must report accepted");
//        assertEquals(site, result.site().orElseThrow(),
//                "accepted: site must be present");
//        assertTrue(result.failure() == null,
//                "accepted: failure must be null");
//        assertEquals(report, result.distanceReport(),
//                "accepted: distance report must match");
//    }
//
//    private static void rejected_hasFailureAndNoSite() {
//        DungeonStructureDistanceReport report = distanceReport(0, 0, 0);
//
//        LoadedDungeonSiteProjectionResult result =
//                LoadedDungeonSiteProjectionResult.rejected(
//                        LoadedDungeonSiteProjectionFailure.NO_PIECES,
//                        report
//                );
//
//        assertFalse(result.accepted(), "rejected: must not report accepted");
//        assertTrue(result.site().isEmpty(), "rejected: site must be absent");
//        assertEquals(LoadedDungeonSiteProjectionFailure.NO_PIECES, result.failure(),
//                "rejected: failure must match");
//        assertEquals(report, result.distanceReport(),
//                "rejected: distance report must match");
//    }
//
//    private static void sitePlusFailure_isRejected() {
//        try {
//            new LoadedDungeonSiteProjectionResult(
//                    Optional.of(site(chunk(0, 0))),
//                    LoadedDungeonSiteProjectionFailure.NO_PIECES,
//                    distanceReport(1, 0, 0)
//            );
//            throw new AssertionError("site+failure: should have rejected");
//        } catch (IllegalArgumentException expected) {
//            assertContains(expected.getMessage(), "not both",
//                    "site+failure: diagnostic must indicate mutual exclusion");
//        }
//    }
//
//    private static void neitherSiteNorFailure_isRejected() {
//        try {
//            new LoadedDungeonSiteProjectionResult(
//                    Optional.empty(),
//                    null,
//                    distanceReport(0, 0, 0)
//            );
//            throw new AssertionError("neither: should have rejected");
//        } catch (IllegalArgumentException expected) {
//            assertContains(expected.getMessage(), "not both",
//                    "neither: diagnostic must indicate mutual exclusion");
//        }
//    }
//
//    private static void nullDistanceReport_isRejected() {
//        try {
//            new LoadedDungeonSiteProjectionResult(
//                    Optional.empty(),
//                    LoadedDungeonSiteProjectionFailure.INVALID_PRIMARY_ENTRY_COUNT,
//                    null
//            );
//            throw new AssertionError("null distance report: should have thrown");
//        } catch (NullPointerException expected) {
//            assertContains(expected.getMessage(), "distanceReport",
//                    "null distance report: diagnostic must mention field name");
//        }
//    }
//
//    private static void nullFailure_isRejectedByFactory() {
//        try {
//            LoadedDungeonSiteProjectionResult.rejected(
//                    null,
//                    distanceReport(0, 0, 0)
//            );
//            throw new AssertionError("null failure in rejected factory: should have thrown");
//        } catch (NullPointerException expected) {
//            // The rejected() factory requires non-null arguments.
//        }
//
//        // Null failure is also rejected by the static factory call
//        // because it matches the 'neither site nor failure' pattern above.
//    }
//
//    private static void acceptedResult_reportsCorrectState() {
//        DungeonSite site = site(chunk(1, 2));
//        DungeonStructureDistanceReport report = distanceReport(3, 1, 2);
//
//        LoadedDungeonSiteProjectionResult result =
//                LoadedDungeonSiteProjectionResult.accepted(site, report);
//
//        assertTrue(result.site().isPresent(), "site must be present");
//        assertTrue(result.failure() == null, "failure must be null");
//        assertEquals(new DungeonSiteKey(1, 2), result.site().orElseThrow().key(),
//                "site key must match");
//    }
//
//    // ── helpers ──
//
//    private static DungeonSiteKey chunk(int x, int z) {
//        return new DungeonSiteKey(x, z);
//    }
//
//    private static DungeonSite site(DungeonSiteKey key) {
//        return new DungeonSite(
//                key,
//                new net.minecraft.core.BlockPos(0, 64, 0),
//                new net.minecraft.world.level.levelgen.structure.BoundingBox(
//                        0, 32, 0, 15, 96, 15
//                ),
//                List.of(),
//                1,
//                io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonDifficulty.NORMAL,
//                Optional.empty()
//        );
//    }
//
//    private static DungeonStructureDistanceReport distanceReport(
//            int pieceCount,
//            int primaryCount,
//            int maxDistance
//    ) {
//        return new DungeonStructureDistanceReport(
//                new DungeonStructureDistanceReport.ChunkCoordinate(0, 0),
//                pieceCount,
//                primaryCount,
//                maxDistance,
//                List.of(),
//                primaryCount > 0
//                        ? java.util.OptionalInt.of(maxDistance)
//                        : java.util.OptionalInt.empty(),
//                new DungeonStructureDistanceReport.ChunkBounds(0, 0, 0, 0),
//                List.of()
//        );
//    }
//
//    private static void assertTrue(boolean value, String message) {
//        if (!value) {
//            throw new AssertionError(message);
//        }
//    }
//
//    private static void assertFalse(boolean value, String message) {
//        if (value) {
//            throw new AssertionError(message);
//        }
//    }
//
//    private static void assertEquals(Object expected, Object actual, String message) {
//        if (!expected.equals(actual)) {
//            throw new AssertionError(
//                    message + ": expected=" + expected + ", actual=" + actual
//            );
//        }
//    }
//
//    private static void assertContains(String text, String token, String message) {
//        if (!text.contains(token)) {
//            throw new AssertionError(message + ": missing '" + token + "' in: " + text);
//        }
//    }
//}
