package io.github.naimjeg.obeliskdepths.worldgen.structure.tree;

import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

public final class AmphixylonPlacementPlanTest {
    private static final Comparator<AmphixylonGeometry.LocalBlock> LOCAL_ORDER = Comparator
            .comparingInt(AmphixylonGeometry.LocalBlock::y)
            .thenComparingInt(AmphixylonGeometry.LocalBlock::x)
            .thenComparingInt(AmphixylonGeometry.LocalBlock::z);
    private static final Comparator<AmphixylonPlacementPlan.BlockPlacement> WORLD_ORDER = Comparator
            .comparingInt(AmphixylonPlacementPlan.BlockPlacement::y)
            .thenComparingInt(AmphixylonPlacementPlan.BlockPlacement::x)
            .thenComparingInt(AmphixylonPlacementPlan.BlockPlacement::z);

    private AmphixylonPlacementPlanTest() {
    }

    public static void main(String[] args) {
        mirroredStructuralVoxelMasksAreExact();
        voxelReflectionHandlesThicknessAndRoundingBoundaries();
        fixedSeedCorpusStaysInsideStrictBounds();
        everyPlacementFitsExactPieceBounds();
        eachRootVoxelMaskIsConnected();
        leafMassRestoresBroadCanopyAndStaysBounded();
        duplicateResolutionAndPrecedenceAreDeterministic();
        repeatedPlansHaveIdenticalSortedPlacementsAndHash();
        buildHeightAndMaximumReachBoundariesValidate();
    }

    private static void mirroredStructuralVoxelMasksAreExact() {
        AmphixylonPlacementPlan plan = representativePlan(0x1234ABCDL, 7, -11, 44);
        int pathCount = plan.geometry().canonicalHalf().paths().size();
        for (int index = 0; index < pathCount; index++) {
            TreeSet<AmphixylonGeometry.LocalBlock> reflected = new TreeSet<>(LOCAL_ORDER);
            for (AmphixylonPlacementPlan.PathVoxel voxel : AmphixylonPlacementPlan.voxelizePath(
                    plan.geometry().canonicalHalf().paths().get(index))) {
                AmphixylonGeometry.LocalBlock block = voxel.block();
                reflected.add(new AmphixylonGeometry.LocalBlock(block.x(), -block.y(), block.z()));
            }
            TreeSet<AmphixylonGeometry.LocalBlock> lower = new TreeSet<>(LOCAL_ORDER);
            for (AmphixylonPlacementPlan.PathVoxel voxel : AmphixylonPlacementPlan.voxelizePath(
                    plan.geometry().mirroredHalf().paths().get(index))) {
                lower.add(voxel.block());
            }
            assertEquals(reflected, lower, "mirrored voxel mask path " + index);
        }
    }

    private static void voxelReflectionHandlesThicknessAndRoundingBoundaries() {
        List<AmphixylonGeometry.PathSample> samples = List.of(
                new AmphixylonGeometry.PathSample(
                        new AmphixylonGeometry.LocalPoint(0.5D, 0.0D, -0.5D),
                        0.0D,
                        AmphixylonGeometry.MAX_ROOT_TIP_RADIUS
                ),
                new AmphixylonGeometry.PathSample(
                        new AmphixylonGeometry.LocalPoint(1.5D, 0.5D, -1.5D),
                        0.5D,
                        1.65D
                ),
                new AmphixylonGeometry.PathSample(
                        new AmphixylonGeometry.LocalPoint(2.5D, 2.5D, -2.5D),
                        1.0D,
                        2.65D
                )
        );
        AmphixylonGeometry.TaperedPath boundaryPath = new AmphixylonGeometry.TaperedPath(
                0,
                0.0D,
                AmphixylonSite.MAX_HORIZONTAL_REACH,
                2.65D,
                AmphixylonGeometry.MAX_ROOT_TIP_RADIUS,
                List.of(samples.getFirst().point(), samples.getLast().point()),
                samples
        );
        List<AmphixylonPlacementPlan.PathVoxel> canonical =
                AmphixylonPlacementPlan.voxelizePath(boundaryPath);
        List<AmphixylonPlacementPlan.PathVoxel> mirrored =
                AmphixylonPlacementPlan.mirrorPathVoxels(canonical);
        TreeSet<AmphixylonGeometry.LocalBlock> expected = new TreeSet<>(LOCAL_ORDER);
        TreeSet<AmphixylonGeometry.LocalBlock> actual = new TreeSet<>(LOCAL_ORDER);
        for (AmphixylonPlacementPlan.PathVoxel voxel : canonical) {
            expected.add(voxel.block().mirror());
        }
        for (AmphixylonPlacementPlan.PathVoxel voxel : mirrored) {
            actual.add(voxel.block());
        }
        assertTrue(!canonical.isEmpty(), "odd/even thickness boundary mask is nonempty");
        assertEquals(expected, actual, "floor/rounding boundary mask reflects exactly");
    }

    private static void fixedSeedCorpusStaysInsideStrictBounds() {
        int maximumXSpan = 0;
        int maximumZSpan = 0;
        for (int index = 0; index < 512; index++) {
            int reach = AmphixylonSite.MIN_HORIZONTAL_REACH
                    + index % (AmphixylonSite.MAX_HORIZONTAL_REACH
                    - AmphixylonSite.MIN_HORIZONTAL_REACH + 1);
            AmphixylonPlacementPlan plan = representativePlan(
                    0xD1B54A32D192ED03L * index,
                    index - 256,
                    193 - index,
                    reach
            );
            maximumXSpan = Math.max(maximumXSpan, plan.xSpan());
            maximumZSpan = Math.max(maximumZSpan, plan.zSpan());
            assertTrue(plan.xSpan() <= 127, "strict X chunk bound " + index);
            assertTrue(plan.zSpan() <= 127, "strict Z chunk bound " + index);
            assertTrue(plan.xSpan() <= AmphixylonGeometry.MAX_HORIZONTAL_SPAN,
                    "internal X design budget " + index);
            assertTrue(plan.zSpan() <= AmphixylonGeometry.MAX_HORIZONTAL_SPAN,
                    "internal Z design budget " + index);
        }
        System.out.println("amphixylon-corpus-bounds maxXSpan=" + maximumXSpan
                + " maxZSpan=" + maximumZSpan
                + " theoreticalMaxSpan=" + AmphixylonGeometry.MAX_HORIZONTAL_SPAN);
    }

    private static void everyPlacementFitsExactPieceBounds() {
        AmphixylonPlacementPlan plan = representativePlan(0xABCDEF01L, 21, 5, 44);
        BoundingBox bounds = plan.completeBounds();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (AmphixylonPlacementPlan.BlockPlacement placement : plan.placements()) {
            assertTrue(contains(bounds, placement), "placement inside piece bounds");
            minX = Math.min(minX, placement.x());
            minY = Math.min(minY, placement.y());
            minZ = Math.min(minZ, placement.z());
            maxX = Math.max(maxX, placement.x());
            maxY = Math.max(maxY, placement.y());
            maxZ = Math.max(maxZ, placement.z());
        }
        assertEquals(new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ), bounds,
                "piece bounds are exact occupied bounds");
        for (AmphixylonPlacementPlan.VineColumn vine : plan.vines()) {
            assertTrue(contains(bounds, vine.x(), vine.supportY(), vine.z()), "vine support in piece bounds");
            assertTrue(contains(bounds, vine.x(), vine.topY(), vine.z()), "vine top in piece bounds");
            assertTrue(contains(bounds, vine.x(), vine.bottomY(), vine.z()), "vine bottom in piece bounds");
        }
    }

    private static void eachRootVoxelMaskIsConnected() {
        int maximumRootRadius = 0;
        int maximumRootDepth = 0;
        int maximumRootBlockCount = 0;
        for (int seedIndex = 0; seedIndex < 64; seedIndex++) {
            AmphixylonPlacementPlan plan = representativePlan(
                    0xCAFEF00DL * (seedIndex + 1L),
                    seedIndex - 32,
                    19 - seedIndex,
                    AmphixylonSite.MAX_HORIZONTAL_REACH
            );
            TreeSet<AmphixylonGeometry.LocalBlock> allRootBlocks = new TreeSet<>(LOCAL_ORDER);
            for (int index = 0; index < plan.geometry().mirroredHalf().paths().size(); index++) {
                List<AmphixylonGeometry.LocalBlock> blocks = plan.lowerRootBlocks(index);
                assertConnected(blocks, "connected root path seed=" + seedIndex + " path=" + index);
                allRootBlocks.addAll(blocks);
            }
            maximumRootBlockCount = Math.max(maximumRootBlockCount, allRootBlocks.size());
            for (AmphixylonGeometry.LocalBlock block : allRootBlocks) {
                maximumRootRadius = Math.max(
                        maximumRootRadius,
                        (int) Math.ceil(Math.sqrt(block.x() * block.x() + block.z() * block.z()))
                );
                maximumRootDepth = Math.max(maximumRootDepth, -block.y());
            }
            assertTrue(allRootBlocks.size() <= AmphixylonGeometry.MAX_ROOT_BLOCK_BUDGET,
                    "root block-count budget seed=" + seedIndex);
        }
        assertTrue(maximumRootRadius <= AmphixylonGeometry.MAX_ROOT_OCCUPIED_RADIUS,
                "root occupied radius budget");
        assertTrue(maximumRootDepth <= AmphixylonGeometry.MAX_ROOT_OCCUPIED_DEPTH,
                "root occupied depth budget including tip voxelization");
        System.out.println("amphixylon-root-budget maxRadius=" + maximumRootRadius
                + " maxDepth=" + maximumRootDepth
                + " maxBlocks=" + maximumRootBlockCount);
    }

    private static void assertConnected(
            List<AmphixylonGeometry.LocalBlock> blocks,
            String message
    ) {
        TreeSet<AmphixylonGeometry.LocalBlock> remaining = new TreeSet<>(LOCAL_ORDER);
        remaining.addAll(blocks);
        ArrayDeque<AmphixylonGeometry.LocalBlock> queue = new ArrayDeque<>();
        AmphixylonGeometry.LocalBlock first = remaining.pollFirst();
        assertTrue(first != null, message + " is not empty");
        queue.add(first);
        int visited = 0;
        while (!queue.isEmpty()) {
            AmphixylonGeometry.LocalBlock block = queue.removeFirst();
            visited++;
            visitNeighbor(remaining, queue, block.x() + 1, block.y(), block.z());
            visitNeighbor(remaining, queue, block.x() - 1, block.y(), block.z());
            visitNeighbor(remaining, queue, block.x(), block.y() + 1, block.z());
            visitNeighbor(remaining, queue, block.x(), block.y() - 1, block.z());
            visitNeighbor(remaining, queue, block.x(), block.y(), block.z() + 1);
            visitNeighbor(remaining, queue, block.x(), block.y(), block.z() - 1);
        }
        assertEquals(blocks.size(), visited, message);
    }

    private static void duplicateResolutionAndPrecedenceAreDeterministic() {
        AmphixylonPlacementPlan plan = representativePlan(0x1020304050607080L, 0, 0, 44);
        assertTrue(plan.duplicateResolutionCount() > 0, "symmetry-plane duplicates are resolved");
        TreeSet<AmphixylonPlacementPlan.BlockPlacement> coordinates = new TreeSet<>(WORLD_ORDER);
        for (AmphixylonPlacementPlan.BlockPlacement placement : plan.placements()) {
            assertTrue(coordinates.add(placement), "one resolved placement per coordinate");
            if (placement.kind() == AmphixylonPlacementPlan.PlacementKind.LEAF) {
                assertTrue(!placement.kind().isStructural(), "leaf does not overwrite structure");
            }
        }
    }

    private static void leafMassRestoresBroadCanopyAndStaysBounded() {
        for (int seedIndex = 0; seedIndex < 16; seedIndex++) {
            AmphixylonPlacementPlan plan = representativePlan(
                    0xA0761D6478BD642FL * (seedIndex + 1L),
                    seedIndex - 8,
                    8 - seedIndex,
                    AmphixylonSite.MAX_HORIZONTAL_REACH
            );
            int leafBlockCount = 0;
            for (AmphixylonPlacementPlan.BlockPlacement placement : plan.placements()) {
                if (placement.kind() == AmphixylonPlacementPlan.PlacementKind.LEAF) {
                    assertTrue(contains(plan.completeBounds(), placement.x(), placement.y(), placement.z()),
                            "leaf in global bounds");
                    leafBlockCount++;
                }
            }
            assertTrue(leafBlockCount >= 8_000,
                    "restored broad foliage mass seed=" + seedIndex + " blocks=" + leafBlockCount);
        }
    }

    private static void repeatedPlansHaveIdenticalSortedPlacementsAndHash() {
        AmphixylonPlacementPlan first = representativePlan(0x5678EF90L, -17, 23, 42);
        AmphixylonPlacementPlan second = representativePlan(0x5678EF90L, -17, 23, 42);
        assertEquals(first.placements(), second.placements(), "stable sorted placement list");
        assertEquals(first.vines(), second.vines(), "stable vine list");
        assertEquals(first.stableHash(), second.stableHash(), "stable geometry hash");
    }

    private static void buildHeightAndMaximumReachBoundariesValidate() {
        AmphixylonSite site = new AmphixylonSite(
                -511,
                513,
                -60,
                59,
                AmphixylonSite.MAX_HORIZONTAL_REACH,
                0x7FFFFFFFFFFFFFFFL
        );
        AmphixylonPlacementPlan plan = AmphixylonPlacementPlan.create(site, AmphixylonGeometry.create(site));
        assertTrue(plan.isValid(), "maximum reach boundary plan");
        assertTrue(plan.completeBounds().minY() >= site.minY(), "lower build boundary");
        assertTrue(plan.completeBounds().maxY() <= site.maxY(), "upper build boundary");
    }

    private static AmphixylonPlacementPlan representativePlan(
            long worldSeed,
            int chunkX,
            int chunkZ,
            int reach
    ) {
        AmphixylonSite site = AmphixylonFieldTest
                .representativeField(worldSeed, chunkX, chunkZ, reach)
                .site();
        return AmphixylonPlacementPlan.create(site, AmphixylonGeometry.create(site));
    }

    private static void visitNeighbor(
            TreeSet<AmphixylonGeometry.LocalBlock> remaining,
            ArrayDeque<AmphixylonGeometry.LocalBlock> queue,
            int x,
            int y,
            int z
    ) {
        AmphixylonGeometry.LocalBlock neighbor = new AmphixylonGeometry.LocalBlock(x, y, z);
        if (remaining.remove(neighbor)) {
            queue.addLast(neighbor);
        }
    }

    private static boolean contains(
            BoundingBox bounds,
            AmphixylonPlacementPlan.BlockPlacement placement
    ) {
        return contains(bounds, placement.x(), placement.y(), placement.z());
    }

    private static boolean contains(BoundingBox bounds, int x, int y, int z) {
        return x >= bounds.minX() && x <= bounds.maxX()
                && y >= bounds.minY() && y <= bounds.maxY()
                && z >= bounds.minZ() && z <= bounds.maxZ();
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

    private static void assertEquals(long expected, long actual, String message) {
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
