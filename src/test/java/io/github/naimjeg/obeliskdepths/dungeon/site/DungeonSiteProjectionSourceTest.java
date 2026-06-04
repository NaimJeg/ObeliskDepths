package io.github.naimjeg.obeliskdepths.dungeon.site;

public final class DungeonSiteProjectionSourceTest {
    private DungeonSiteProjectionSourceTest() {
    }

    public static void main(String[] args) {
        assertTrue(
                DungeonSiteProjectionSource.GENERATED_STRUCTURE_START.authoritative(),
                "generated structure starts must be authoritative"
        );
        assertTrue(
                DungeonSiteProjectionSource.SAVED_SNAPSHOT.authoritative(),
                "saved snapshots must be authoritative"
        );
        assertFalse(
                DungeonSiteProjectionSource.PLANNED_PROTOTYPE.authoritative(),
                "planned prototypes must not be authoritative"
        );
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
}
