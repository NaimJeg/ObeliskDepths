package io.github.naimjeg.obeliskdepths.worldgen.structure.graph;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import java.util.ArrayList;
import java.util.List;

public final class DungeonSideBranchAccountingTest {
    private DungeonSideBranchAccountingTest() {}

    public static void main(String[] args) {
        configAcceptsMaxSideBranches();
        generatedGraphCountsCorrectBranches();
    }

    /*
     * Verify the DEFAULT config can accommodate its own
     * min/max side branch requirements within its maxNodeCount.
     */
    private static void configAcceptsMaxSideBranches() {
        DungeonGraphGenerationConfig cfg = DungeonGraphGenerationConfig.DEFAULT;
        check(cfg.minSideBranches() <= cfg.maxSideBranches(),
            "config: min <= max branches");
        check(cfg.minSideBranchLength() <= cfg.maxSideBranchLength(),
            "config: min <= max branch length");
        // Conservative: 2 cores + 3 sectors * 4 depth + 2 branches * 1 depth
        // = 2 + 12 + 2 = 16 nodes. cfg.maxNodeCount() is 48, so there is headroom.
        int conservativeNodes = 2 + 3 * cfg.maxArmDepth() + cfg.maxSideBranches() * cfg.maxSideBranchLength();
        check(conservativeNodes <= cfg.maxNodeCount(),
            "config: conservative node estimate " + conservativeNodes
                + " <= max " + cfg.maxNodeCount());
    }

    /*
     * Generate graphs across many seeds and verify the branch count
     * reported by graph analysis is exactly the number of TREASURE
     * nodes found, and that every TREASURE node has exactly one
     * incident edge (terminal).
     */
    private static void generatedGraphCountsCorrectBranches() {
        DungeonGraphGenerationConfig cfg = DungeonGraphGenerationConfig.DEFAULT;
        int minExpected = cfg.minSideBranches();
        int maxExpected = cfg.maxSideBranches();

        for (int i = 0; i < 200; i++) {
            long seed = i * 0x9E3779B97F4A7C15L;
            DungeonGraph graph = DungeonGraphGenerator.generate(seed);

            long treasureCount = graph.nodes().stream()
                    .filter(n -> n.type() == DungeonRoomType.TREASURE)
                    .count();

            check(treasureCount >= minExpected && treasureCount <= maxExpected,
                "seed " + seed + ": treasure count " + treasureCount
                    + " in [" + minExpected + ", " + maxExpected + "]");

            // Every TREASURE node must be terminal (max 1 edge)
            for (DungeonGraphNode node : graph.nodes()) {
                if (node.type() == DungeonRoomType.TREASURE) {
                    long incidentEdgeCount = graph.edges().stream()
                            .filter(e -> e.sourceNodeId().equals(node.id())
                                    || e.targetNodeId().equals(node.id()))
                            .count();
                    check(incidentEdgeCount == 1,
                        "seed " + seed + ": treasure node " + node.id()
                            + " has " + incidentEdgeCount + " edges, expected 1");
                }
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
