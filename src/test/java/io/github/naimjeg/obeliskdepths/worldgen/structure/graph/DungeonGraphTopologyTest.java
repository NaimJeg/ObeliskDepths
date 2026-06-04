package io.github.naimjeg.obeliskdepths.worldgen.structure.graph;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Queue;
import java.util.Set;
import java.util.stream.IntStream;

public final class DungeonGraphTopologyTest {
    private static final DungeonGraphGenerationConfig CONFIG =
            DungeonGraphGenerationConfig.DEFAULT;

    private DungeonGraphTopologyTest() {
    }

    public static void main(String[] args) {
        compactSideBranchTopologyAcrossSeeds();
    }

    private static void compactSideBranchTopologyAcrossSeeds() {
        IntStream.range(0, 256)
                .parallel()
                .forEach(DungeonGraphTopologyTest::verifySeed);
    }

    private static void verifySeed(int seed) {
        DungeonGraph graph = DungeonGraphGenerator.generate(seed);
        DungeonGraphValidator.validate(graph);
        DungeonGraphAnalysis analysis = DungeonGraphAnalyzer.analyze(graph);

        int expectedNodes = 2
                + CONFIG.minSectorCount() * CONFIG.maxArmDepth()
                + CONFIG.minSideBranches() * CONFIG.minSideBranchLength();
        assertEquals(expectedNodes, graph.nodes().size(), "seed " + seed + " node count");

        int sideBranchCount = 0;
        for (DungeonGraphNode node : graph.nodes()) {
            if (!node.id().startsWith("side_")) {
                continue;
            }
            sideBranchCount++;
            assertEquals(
                    DungeonRoomType.TREASURE,
                    node.type(),
                    "seed " + seed + " side terminal type"
            );
            int degree = analysis.requireNode(node.id()).totalDegree();
            assertEquals(1, degree, "seed " + seed + " side terminal degree");

            String parent = analysis.treeParentByNode().get(node.id());
            assertTrue(
                    parent != null && parent.startsWith("sector_"),
                    "seed " + seed + " side branch attaches to a radial node"
            );
            assertTrue(
                    parent != null && radialDepth(parent) == 1,
                    "seed " + seed + " side branch attaches to compact inner ring"
            );
        }
        assertEquals(
                CONFIG.minSideBranches(),
                sideBranchCount,
                "seed " + seed + " side branch count"
        );

        assertEquals(1, graph.loopEdges().size(), "seed " + seed + " loop count");

        for (DungeonGraphNode node : graph.nodes()) {
            if (node.id().equals(DungeonGraphGenerator.BOSS_ID)
                    || node.id().equals(DungeonGraphGenerator.BOSS_HUB_ID)) {
                continue;
            }
            int degree = analysis.requireNode(node.id()).totalDegree();
            assertTrue(
                    degree <= CONFIG.maxOrdinaryDegree(),
                    "seed " + seed + " ordinary degree for " + node.id()
            );
        }

        for (String entry : graph.entryNodeIds()) {
            assertTrue(
                    reaches(graph, entry, graph.rootNodeId()),
                    "seed " + seed + " entry reaches boss"
            );
        }
        for (DungeonGraphNode node : graph.nodes()) {
            assertTrue(
                    reaches(graph, node.id(), graph.rootNodeId()),
                    "seed " + seed + " graph is connected"
            );
        }
    }

    private static int radialDepth(String nodeId) {
        int marker = nodeId.lastIndexOf("_depth_");
        return marker < 0 ? -1 : Integer.parseInt(nodeId.substring(marker + "_depth_".length()));
    }

    private static boolean reaches(DungeonGraph graph, String source, String target) {
        Set<String> visited = new LinkedHashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(source);
        while (!queue.isEmpty()) {
            String current = queue.remove();
            if (!visited.add(current)) {
                continue;
            }
            if (current.equals(target)) {
                return true;
            }
            queue.addAll(graph.neighbors(current));
        }
        return false;
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
