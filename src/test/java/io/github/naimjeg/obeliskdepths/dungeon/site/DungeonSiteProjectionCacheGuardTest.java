package io.github.naimjeg.obeliskdepths.dungeon.site;

public final class DungeonSiteProjectionCacheGuardTest {
    private DungeonSiteProjectionCacheGuardTest() {
    }

    public static void main(String[] args) {
        assertSame(
                DungeonSiteProjectionSource.GENERATED_STRUCTURE_START,
                DungeonSiteProjectionCache.requireAuthoritativeSource(
                        DungeonSiteProjectionSource.GENERATED_STRUCTURE_START
                ),
                "generated source should pass cache guard"
        );
        assertSame(
                DungeonSiteProjectionSource.SAVED_SNAPSHOT,
                DungeonSiteProjectionCache.requireAuthoritativeSource(
                        DungeonSiteProjectionSource.SAVED_SNAPSHOT
                ),
                "saved source should pass cache guard"
        );
        assertThrows(
                () -> DungeonSiteProjectionCache.requireAuthoritativeSource(
                        DungeonSiteProjectionSource.PLANNED_PROTOTYPE
                ),
                "planned prototype source should fail cache guard"
        );
        assertThrows(
                () -> DungeonSiteProjectionCache.requireAuthoritativeSource(null),
                "null source should fail cache guard"
        );
    }

    private static void assertSame(
            Object expected,
            Object actual,
            String message
    ) {
        if (expected != actual) {
            throw new AssertionError(message);
        }
    }

    private static void assertThrows(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }

        throw new AssertionError(message);
    }
}
