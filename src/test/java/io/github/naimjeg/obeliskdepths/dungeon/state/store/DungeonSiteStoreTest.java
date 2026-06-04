//package io.github.naimjeg.obeliskdepths.dungeon.state.store;
//import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
//import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
//import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
//import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteRecord;
//import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteUsageStatus;
//
//import java.util.List;
//import java.util.Objects;
//import java.util.UUID;
//
///**
// * Tests for {@link DungeonSiteStore} null boundaries, loaded-snapshot
// * validation, and reservation lifecycle.
// */
//public final class DungeonSiteStoreTest {
//    private DungeonSiteStoreTest() {
//    }
//
//    public static void main(String[] args) {
//        nullLoadedSnapshot_failsWithParameterDiagnostic();
//        duplicateLoadedSnapshot_fails();
//        putLoadedSnapshot_isNotPublic();
//        reservedState_afterReserve_isPresent();
//        snapshotDisappears_onRelease();
//        snapshotDisappears_onRetirement();
//        nullBuildSite_rejected();
//    }
//
//    private static void nullLoadedSnapshot_failsWithParameterDiagnostic() {
//        DungeonSiteStore store = new DungeonSiteStore(noop());
//
//        try {
//            store.loadSnapshots(List.of((DungeonSite) null));
//            throw new AssertionError("null snapshot: should have thrown");
//        } catch (NullPointerException expected) {
//            assertContains(expected.getMessage(), "snapshot",
//                    "null snapshot: diagnostic must reference parameter name");
//        }
//    }
//
//    private static void duplicateLoadedSnapshot_fails() {
//        DungeonSiteStore store = new DungeonSiteStore(noop());
//        DungeonSite site = buildSite(siteKey("duplicate-test"), 0, 0);
//        store.loadSnapshots(List.of(site));
//
//        try {
//            store.loadSnapshots(List.of(site));
//            throw new AssertionError("duplicate snapshot: should have thrown");
//        } catch (IllegalStateException expected) {
//            assertContains(expected.getMessage(), "Duplicate",
//                    "duplicate snapshot: diagnostic must mention duplicate");
//        }
//    }
//
//    @SuppressWarnings({"ResultOfMethodCallIgnored", "PMD.AvoidAccessibilityAlteration"})
//    private static void putLoadedSnapshot_isNotPublic() {
//        try {
//            DungeonSiteStore.class.getDeclaredMethod(
//                    "putLoadedSnapshot", DungeonSite.class
//            );
//            throw new AssertionError(
//                    "putLoadedSnapshot must not be a public method"
//            );
//        } catch (NoSuchMethodException expected) {
//            // Expected: the method (private or absent) is not accessible.
//        }
//
//        try {
//            DungeonSiteStore.class.getMethod(
//                    "requireReservedSnapshot", DungeonInstanceId.class, DungeonSiteKey.class
//            );
//            throw new AssertionError(
//                    "requireReservedSnapshot must not exist"
//            );
//        } catch (NoSuchMethodException expected) {
//            // Expected.
//        }
//    }
//
//    private static void reservedState_afterReserve_isPresent() {
//        DungeonSiteStore store = new DungeonSiteStore(noop());
//        DungeonSiteKey siteKey = siteKey("reserve-test");
//        DungeonSite site = buildSite(siteKey, 0, 0);
//        DungeonInstanceId instanceId = instanceId("reserve-instance");
//
//        store.reserve(site, instanceId, 1000L);
//
//        DungeonSiteStore.ReservedSiteState state =
//                store.reservedState(instanceId, siteKey).orElseThrow(
//                        () -> new AssertionError("reserved state: must be present after reserve")
//                );
//
//        assertTrue(
//                state.record().isReservedFor(instanceId),
//                "reserved state: record must belong to instance"
//        );
//        assertEquals(siteKey, state.snapshot().key(),
//                "reserved state: snapshot key must match");
//    }
//
//    private static void snapshotDisappears_onRelease() {
//        DungeonSiteStore store = new DungeonSiteStore(noop());
//        DungeonSiteKey siteKey = siteKey("release-test");
//        DungeonSite site = buildSite(siteKey, 10, 20);
//        DungeonInstanceId instanceId = instanceId("release-instance");
//
//        store.reserve(site, instanceId, 2000L);
//        boolean released = store.releaseReservation(instanceId, siteKey);
//
//        assertTrue(released, "release: must return true");
//        assertTrue(store.reservedState(instanceId, siteKey).isEmpty(),
//                "release: reserved state must be absent");
//        assertEquals(0, store.snapshots().size(),
//                "release: snapshot collection must be empty");
//    }
//
//    private static void snapshotDisappears_onRetirement() {
//        DungeonSiteStore store = new DungeonSiteStore(noop());
//        DungeonSiteKey siteKey = siteKey("retire-test");
//        DungeonSite site = buildSite(siteKey, 30, 40);
//        DungeonInstanceId instanceId = instanceId("retire-instance");
//
//        store.reserve(site, instanceId, 3000L);
//        boolean retired = store.retireReservation(
//                instanceId,
//                siteKey,
//                DungeonSiteUsageStatus.COMPLETED,
//                5000L
//        );
//
//        assertTrue(retired, "retire: must return true");
//        assertEquals(0, store.snapshots().size(),
//                "retire: snapshot collection must be empty");
//        assertTrue(store.record(siteKey).isPresent(),
//                "retire: record must persist with terminal status");
//    }
//
//    private static void nullBuildSite_rejected() {
//        DungeonSiteStore store = new DungeonSiteStore(noop());
//
//        assertThrows(
//                NullPointerException.class,
//                () -> store.loadSnapshots(List.of((DungeonSite) null)),
//                "null loaded snapshot: must throw NullPointerException"
//        );
//    }
//
//    // ── helpers ──
//
//    private static Runnable noop() {
//        return () -> {};
//    }
//
//    private static DungeonSiteKey siteKey(String id) {
//        Objects.requireNonNull(id);
//        int chunkX = Math.abs(id.hashCode() % 1000);
//        int chunkZ = Math.abs((id + "z").hashCode() % 1000);
//        return new DungeonSiteKey(chunkX, chunkZ);
//    }
//
//    private static DungeonInstanceId instanceId(String id) {
//        return new DungeonInstanceId(UUID.randomUUID());
//    }
//
//    private static DungeonSite buildSite(DungeonSiteKey key, int startX, int startZ) {
//        Objects.requireNonNull(key);
//        return new DungeonSite(
//                key,
//                new net.minecraft.core.BlockPos(startX, 64, startZ),
//                new net.minecraft.world.level.levelgen.structure.BoundingBox(
//                        startX, 32, startZ, startX + 15, 96, startZ + 15
//                ),
//                List.of(),
//                1,
//                io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonDifficulty.NORMAL,
//                java.util.Optional.empty()
//        );
//    }
//
//    private static void assertTrue(boolean value, String message) {
//        if (!value) {
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
//    private static void assertEquals(int expected, int actual, String message) {
//        if (expected != actual) {
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
//
//    private static void assertThrows(
//            Class<? extends RuntimeException> expectedType,
//            Runnable action,
//            String message
//    ) {
//        try {
//            action.run();
//        } catch (RuntimeException expected) {
//            if (expectedType.isInstance(expected)) {
//                return;
//            }
//            throw new AssertionError(
//                    message + ": expected=" + expectedType.getSimpleName()
//                            + " actual=" + expected.getClass().getSimpleName(),
//                    expected
//            );
//        }
//        throw new AssertionError(
//                message + ": expected=" + expectedType.getSimpleName()
//        );
//    }
//}
