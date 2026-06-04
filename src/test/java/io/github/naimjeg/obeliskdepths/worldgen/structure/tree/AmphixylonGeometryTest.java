package io.github.naimjeg.obeliskdepths.worldgen.structure.tree;

public final class AmphixylonGeometryTest {
    private AmphixylonGeometryTest() {
    }

    public static void main(String[] args) {
        canonicalHalfIsExactlyReflected();
        trunkSlicesAreExactlyReflected();
        trunkRestoresBroadHourglassEnds();
        pairedPathsHaveIdenticalTopologyAndTaper();
        reflectionConsumesNoRandomSamples();
        restoredArmsRetainDeterministicTaper();
        leafUnitsRestoreBroadCanopyAndRemainReflected();
        symmetryPlaneAndRoundingBoundariesAreStable();
    }

    private static void canonicalHalfIsExactlyReflected() {
        AmphixylonGeometry geometry = representativeGeometry(0x1234ABCDL, 7, -11);
        assertTrue(geometry.isExactlyReflected(), "complete canonical half reflection");
        assertEquals(
                geometry.canonicalHalf().paths().size(),
                geometry.mirroredHalf().paths().size(),
                "reflected path count"
        );
    }

    private static void pairedPathsHaveIdenticalTopologyAndTaper() {
        AmphixylonGeometry geometry = representativeGeometry(0xCAFEF00DL, -3, 19);
        for (int pathIndex = 0; pathIndex < geometry.canonicalHalf().paths().size(); pathIndex++) {
            AmphixylonGeometry.TaperedPath upper = geometry.canonicalHalf().paths().get(pathIndex);
            AmphixylonGeometry.TaperedPath lower = geometry.mirroredHalf().paths().get(pathIndex);
            assertEquals(upper.controlPoints().size(), lower.controlPoints().size(), "control count");
            assertEquals(upper.samples().size(), lower.samples().size(), "sample count");
            assertEquals(upper.radialAngle(), lower.radialAngle(), "radial angle");
            assertEquals(upper.horizontalLength(), lower.horizontalLength(), "horizontal length");
            for (int sampleIndex = 0; sampleIndex < upper.samples().size(); sampleIndex++) {
                AmphixylonGeometry.PathSample first = upper.samples().get(sampleIndex);
                AmphixylonGeometry.PathSample second = lower.samples().get(sampleIndex);
                assertEquals(first.point().x(), second.point().x(), "sample x");
                assertEquals(first.point().y(), -second.point().y(), "sample reflected y");
                assertEquals(first.point().z(), second.point().z(), "sample z");
                assertEquals(first.t(), second.t(), "normalized taper parameter");
                assertEquals(first.radius(), second.radius(), "taper radius");
            }
        }
    }

    private static void trunkSlicesAreExactlyReflected() {
        AmphixylonGeometry geometry = representativeGeometry(0x3141592653589793L, 4, -9);
        int last = geometry.trunkSlices().size() - 1;
        for (int index = 0; index <= last; index++) {
            AmphixylonGeometry.TrunkSlice first = geometry.trunkSlices().get(index);
            AmphixylonGeometry.TrunkSlice second = geometry.trunkSlices().get(last - index);
            assertEquals(first.localY(), -second.localY(), "trunk reflected local y");
            assertEquals(first.centerX(), second.centerX(), "trunk reflected center x");
            assertEquals(first.centerZ(), second.centerZ(), "trunk reflected center z");
            assertEquals(first.radius(), second.radius(), "trunk reflected radius");
            assertEquals(first.irregularityPhase(), second.irregularityPhase(),
                    "trunk reflected irregularity phase");
        }
    }

    private static void trunkRestoresBroadHourglassEnds() {
        AmphixylonGeometry geometry = representativeGeometry(0x6A09E667F3BCC909L, 2, -5);
        AmphixylonGeometry.TrunkSlice waist = geometry.trunkSlices().get(50);
        AmphixylonGeometry.TrunkSlice lowerEnd = geometry.trunkSlices().getFirst();
        AmphixylonGeometry.TrunkSlice upperEnd = geometry.trunkSlices().getLast();
        assertTrue(waist.radius() >= 8.0D && waist.radius() <= 16.0D,
                "restored waist radius");
        assertTrue(lowerEnd.radius() >= 29.0D, "restored broad lower end");
        assertTrue(upperEnd.radius() >= 29.0D, "restored broad upper end");
        assertEquals(lowerEnd.radius(), upperEnd.radius(), "broad ends remain reflected");
    }

    private static void reflectionConsumesNoRandomSamples() {
        AmphixylonGeometry geometry = representativeGeometry(0x5555AAAAL, 0, 0);
        assertTrue(geometry.canonicalRandomSampleCount() > 0, "canonical generation samples randomness");
        assertEquals(0, geometry.mirroredRandomSampleCount(), "mirror random sample count");
    }

    private static void restoredArmsRetainDeterministicTaper() {
        for (int seedIndex = 0; seedIndex < 128; seedIndex++) {
            AmphixylonGeometry geometry = representativeGeometry(
                    0x9E3779B97F4A7C15L * seedIndex,
                    seedIndex - 64,
                    37 - seedIndex
            );
            for (AmphixylonGeometry.TaperedPath path : geometry.mirroredHalf().paths()) {
                double previous = Double.MAX_VALUE;
                for (AmphixylonGeometry.PathSample sample : path.samples()) {
                    assertTrue(sample.radius() <= previous + 1.0E-12D, "monotonic root taper");
                    previous = sample.radius();
                }
                assertTrue(path.tipRadius() >= AmphixylonGeometry.MIN_ROOT_TIP_RADIUS,
                        "root tip lower radius bound");
                assertTrue(path.tipRadius() <= AmphixylonGeometry.MAX_ROOT_TIP_RADIUS,
                        "root tip upper radius bound");
                double penultimate = path.samples().get(path.samples().size() - 2).radius();
                assertTrue(penultimate - path.tipRadius() < 0.15D,
                        "root tip does not end with an abrupt thick cap");
                double depth = -path.samples().getLast().point().y();
                assertTrue(depth >= AmphixylonGeometry.MIN_ROOT_DEPTH, "root minimum depth");
                assertTrue(depth <= AmphixylonGeometry.MAX_ROOT_DEPTH, "root maximum depth");
                assertTrue(path.horizontalLength() <= geometry.site().horizontalReach(),
                        "root horizontal reach budget");
            }
        }
    }

    private static void leafUnitsRestoreBroadCanopyAndRemainReflected() {
        AmphixylonGeometry geometry = representativeGeometry(0xABCDEF01L, 21, 5);
        assertEquals(24, geometry.canonicalHalf().leafUnits().size(), "restored leaf unit count");
        for (int index = 0; index < geometry.canonicalHalf().leafUnits().size(); index++) {
            AmphixylonGeometry.LeafUnit leaf = geometry.canonicalHalf().leafUnits().get(index);
            AmphixylonGeometry.LeafUnit mirror = geometry.mirroredHalf().leafUnits().get(index);
            assertTrue(leaf.radiusX() >= AmphixylonGeometry.MIN_LEAF_HORIZONTAL_RADIUS,
                    "restored leaf x radius minimum");
            assertTrue(leaf.radiusZ() >= AmphixylonGeometry.MIN_LEAF_HORIZONTAL_RADIUS,
                    "restored leaf z radius minimum");
            assertTrue(leaf.radiusY() >= AmphixylonGeometry.MIN_LEAF_VERTICAL_RADIUS,
                    "restored leaf y radius minimum");
            assertTrue(leaf.radiusX() <= AmphixylonGeometry.MAX_LEAF_HORIZONTAL_RADIUS,
                    "leaf x radius");
            assertTrue(leaf.radiusZ() <= AmphixylonGeometry.MAX_LEAF_HORIZONTAL_RADIUS,
                    "leaf z radius");
            assertTrue(leaf.radiusY() <= AmphixylonGeometry.MAX_LEAF_VERTICAL_RADIUS,
                    "leaf y radius");
            assertTrue(leaf.attachmentPathIndex() >= 0
                            && leaf.attachmentPathIndex() < geometry.canonicalHalf().paths().size(),
                    "leaf attachment path");
            AmphixylonGeometry.TaperedPath attachment = geometry.canonicalHalf()
                    .paths().get(leaf.attachmentPathIndex());
            assertTrue(leaf.attachmentSampleIndex() >= 0
                            && leaf.attachmentSampleIndex() < attachment.samples().size(),
                    "leaf attachment sample");
            AmphixylonGeometry.LocalPoint attachmentPoint = attachment.samples()
                    .get(leaf.attachmentSampleIndex()).point();
            double attachmentDistance = Math.sqrt(
                    square(leaf.center().x() - attachmentPoint.x())
                            + square(leaf.center().y() - attachmentPoint.y())
                            + square(leaf.center().z() - attachmentPoint.z())
            );
            assertTrue(attachmentDistance <= 6.5D, "leaf center remains attached to its path");
            assertEquals(leaf.center().x(), mirror.center().x(), "mirrored leaf x");
            assertEquals(leaf.center().y(), -mirror.center().y(), "mirrored leaf y");
            assertEquals(leaf.center().z(), mirror.center().z(), "mirrored leaf z");
            assertEquals(leaf.maskOffsets().size(), mirror.maskOffsets().size(), "mirrored leaf mask size");
        }
    }

    private static void symmetryPlaneAndRoundingBoundariesAreStable() {
        AmphixylonGeometry geometry = representativeGeometry(0x1020304050607080L, -31, 31);
        AmphixylonGeometry.TrunkSlice waist = geometry.trunkSlices().get(50);
        assertEquals(0, waist.localY(), "single canonical trunk slice occupies symmetry plane");
        for (int index = 0; index < geometry.canonicalHalf().paths().size(); index++) {
            AmphixylonGeometry.PathSample upper = geometry.canonicalHalf().paths().get(index).samples().getFirst();
            AmphixylonGeometry.PathSample lower = geometry.mirroredHalf().paths().get(index).samples().getFirst();
            assertTrue(upper.point().y() > 0.0D, "restored upper arm attaches above waist");
            assertEquals(upper.point().y(), -lower.point().y(), "attachment reflected across plane");
            assertEquals(upper.point().x(), lower.point().x(), "attachment x reflection");
            assertEquals(upper.point().z(), lower.point().z(), "attachment z reflection");
        }
    }

    private static AmphixylonGeometry representativeGeometry(long worldSeed, int chunkX, int chunkZ) {
        long treeSeed = mix(worldSeed
                ^ (long) chunkX * 0x632BE59BD9B4E019L
                ^ (long) chunkZ * 0x9E3779B97F4A7C15L
                ^ AmphixylonStructure.TREE_SEED_SALT);
        AmphixylonSite site = new AmphixylonSite(
                chunkX * 16 + 8,
                chunkZ * 16 + 8,
                4,
                123,
                44,
                treeSeed
        );
        return AmphixylonGeometry.create(site);
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }

    private static double square(double value) {
        return value * value;
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(double expected, double actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
