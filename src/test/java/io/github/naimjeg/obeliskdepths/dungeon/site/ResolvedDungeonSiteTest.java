package io.github.naimjeg.obeliskdepths.dungeon.site;

public final class ResolvedDungeonSiteTest {
    private ResolvedDungeonSiteTest() {
    }

    public static void main(String[] args) {
        assertTrue(
                new ResolvedDungeonSite(
                        null,
                        DungeonSiteProjectionSource.GENERATED_STRUCTURE_START
                ).authoritative(),
                "resolved generated site should delegate authoritative=true"
        );
        assertTrue(
                new ResolvedDungeonSite(
                        null,
                        DungeonSiteProjectionSource.SAVED_SNAPSHOT
                ).authoritative(),
                "resolved saved site should delegate authoritative=true"
        );
        assertFalse(
                new ResolvedDungeonSite(
                        null,
                        DungeonSiteProjectionSource.PLANNED_PROTOTYPE
                ).authoritative(),
                "resolved planned prototype should delegate authoritative=false"
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
