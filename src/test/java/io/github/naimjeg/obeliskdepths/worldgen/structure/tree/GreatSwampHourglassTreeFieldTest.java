package io.github.naimjeg.obeliskdepths.worldgen.structure.tree;

import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class GreatSwampHourglassTreeFieldTest {
    private GreatSwampHourglassTreeFieldTest() {
    }

    public static void main(String[] args) {
        sameSeedAndChunkProduceSamePlan();
        differentSeedsVaryPlan();
        waistIsNarrowerThanUpperAndLowerTrunk();
        pieceBoundsStayInsideCompleteBounds();
        basinCandidatesAreAvailable();
        bulkPlansValidate();
    }

    private static void sameSeedAndChunkProduceSamePlan() {
        GreatSwampHourglassTreeField first = representativeField(0x1234ABCDL, 7, -11);
        GreatSwampHourglassTreeField second = representativeField(0x1234ABCDL, 7, -11);

        assertEquals(first.completeBounds(), second.completeBounds(), "same seed/chunk bounds");
        assertEquals(first.lowerRootCount(), second.lowerRootCount(), "same lower root count");
        assertEquals(first.upperRootCount(), second.upperRootCount(), "same upper root count");
        assertEquals(first.branchCount(), second.branchCount(), "same branch count");
        assertTrue(first.validatePlan(), "representative plan validates");
        assertTrue(second.validatePlan(), "recreated plan validates");
    }

    private static void differentSeedsVaryPlan() {
        GreatSwampHourglassTreeField first = representativeField(0x1234ABCDL, 7, -11);
        GreatSwampHourglassTreeField second = representativeField(0x5678EF90L, 7, -11);

        boolean varied = !first.completeBounds().equals(second.completeBounds())
                || first.lowerRootCount() != second.lowerRootCount()
                || first.upperRootCount() != second.upperRootCount()
                || first.branchCount() != second.branchCount();

        assertTrue(varied, "different world seeds should vary the tree plan");
    }

    private static void waistIsNarrowerThanUpperAndLowerTrunk() {
        GreatSwampHourglassTreeField field = representativeField(0xCAFEF00DL, -3, 19);
        GreatSwampHourglassTreeSite site = field.site();
        double waist = field.radiusAtY(site.waistY());
        double lower = field.radiusAtY(site.minY() + site.height() / 5);
        double upper = field.radiusAtY(site.minY() + site.height() * 4 / 5);

        assertTrue(waist < lower, "waist smaller than lower trunk");
        assertTrue(waist < upper, "waist smaller than upper trunk");
    }

    private static void pieceBoundsStayInsideCompleteBounds() {
        GreatSwampHourglassTreeField field = representativeField(0xABCDEF01L, 21, 5);
        BoundingBox complete = field.completeBounds();

        assertContains(complete, field.trunkBounds(), "trunk bounds");
        for (int i = 0; i < field.lowerRootCount(); i++) {
            assertContains(complete, field.lowerRootBounds(i), "lower root " + i);
        }
        for (int i = 0; i < field.upperRootCount(); i++) {
            assertContains(complete, field.upperRootBounds(i), "upper root " + i);
        }
        for (int i = 0; i < field.branchCount(); i++) {
            assertContains(complete, field.branchCanopyBounds(i), "branch canopy " + i);
        }
    }

    private static void basinCandidatesAreAvailable() {
        GreatSwampHourglassTreeField field = representativeField(0x5555AAAAL, 0, 0);

        assertTrue(!field.candidateDungeonBasinCenters().isEmpty(), "candidate basin centers exist");
        assertEquals(
                field.candidateDungeonBasinCenters().getFirst(),
                field.lowerRootBasinCenter(),
                "lower root basin center is first candidate"
        );
    }

    private static void bulkPlansValidate() {
        for (int index = 0; index < 128; index++) {
            GreatSwampHourglassTreeField field = representativeField(
                    0x9E3779B97F4A7C15L * index,
                    index - 64,
                    37 - index
            );
            assertTrue(field.validatePlan(), "bulk tree plan validates for index " + index);
            assertTrue(
                    field.completeBounds().minY() >= field.site().minY()
                            && field.completeBounds().maxY() <= field.site().maxY(),
                    "bulk tree bounds stay inside build height for index " + index
            );
        }
    }

    private static GreatSwampHourglassTreeField representativeField(long worldSeed, int chunkX, int chunkZ) {
        long treeSeed = mix(worldSeed
                ^ (long) chunkX * 0x632BE59BD9B4E019L
                ^ (long) chunkZ * 0x9E3779B97F4A7C15L
                ^ GreatSwampHourglassTreeStructure.TREE_SEED_SALT);
        GreatSwampHourglassTreeSite site = new GreatSwampHourglassTreeSite(
                chunkX * 16 + 8,
                chunkZ * 16 + 8,
                4,
                123,
                88,
                treeSeed
        );
        return new GreatSwampHourglassTreeField(site);
    }

    private static void assertContains(BoundingBox outer, BoundingBox inner, String message) {
        assertTrue(
                inner.minX() >= outer.minX()
                        && inner.maxX() <= outer.maxX()
                        && inner.minY() >= outer.minY()
                        && inner.maxY() <= outer.maxY()
                        && inner.minZ() >= outer.minZ()
                        && inner.maxZ() <= outer.maxZ(),
                message + " should be inside complete bounds"
        );
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }
}
