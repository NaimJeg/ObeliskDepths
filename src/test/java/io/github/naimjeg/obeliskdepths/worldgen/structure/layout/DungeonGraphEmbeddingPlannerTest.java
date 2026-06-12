package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraph;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphGenerator;
import io.github.naimjeg.obeliskdepths.worldgen.structure.piece.DungeonPieceMetadata;
import io.github.naimjeg.obeliskdepths.worldgen.structure.piece.DungeonPiecePlan;
import io.github.naimjeg.obeliskdepths.worldgen.structure.piece.DungeonPiecePlanCompiler;
import net.minecraft.core.BlockPos;

public final class DungeonGraphEmbeddingPlannerTest {
    private DungeonGraphEmbeddingPlannerTest() {
    }

    public static void main(String[] args) {
        deterministicEmbedding();
        noRoomOverlap();
        validConnectorDirections();
        startAnchorInsideStartBounds();
    }

    private static void deterministicEmbedding() {
        DungeonGraph graph = DungeonGraphGenerator.generate(12345L, DungeonLayoutGenerationProfile.MEDIUM_TEST);
        BlockPos layoutOrigin = new BlockPos(100, 40, -200);

        DungeonLayoutPlan first = DungeonGraphEmbeddingPlanner.embed(graph, layoutOrigin);
        DungeonLayoutPlan second = DungeonGraphEmbeddingPlanner.embed(graph, layoutOrigin);

        assertEquals(first, second, "embedding should be deterministic");
    }

    private static void noRoomOverlap() {
        DungeonLayoutPlan plan = representativePlan();

        for (int i = 0; i < plan.nodes().size(); i++) {
            for (int j = i + 1; j < plan.nodes().size(); j++) {
                assertFalse(
                        plan.nodes().get(i).intersects(plan.nodes().get(j)),
                        "rooms should not overlap: "
                                + plan.nodes().get(i).roomId()
                                + " and "
                                + plan.nodes().get(j).roomId()
                );
            }
        }
    }

    private static void validConnectorDirections() {
        DungeonSpatialLayoutValidator.validate(representativePlan());
    }

    private static void startAnchorInsideStartBounds() {
        BlockPos layoutOrigin = new BlockPos(0, 32, 0);
        DungeonLayoutPlan plan = DungeonGraphEmbeddingPlanner.embed(
                DungeonGraphGenerator.generate(42L, DungeonLayoutGenerationProfile.SMALL_TEST),
                layoutOrigin
        );
        DungeonPiecePlan piecePlan = DungeonPiecePlanCompiler.compile(layoutOrigin, plan);
        DungeonPieceMetadata start = piecePlan.pieces()
                .stream()
                .filter(piece -> piece.role().roomType() == DungeonRoomType.START)
                .findFirst()
                .orElseThrow();

        assertTrue(start.bounds().isInside(start.anchor()), "start anchor should be inside start bounds");
    }

    private static DungeonLayoutPlan representativePlan() {
        return DungeonGraphEmbeddingPlanner.embed(
                DungeonGraphGenerator.generate(0xCAFEF00DL, DungeonLayoutGenerationProfile.LARGE_TEST),
                new BlockPos(-64, 20, 128)
        );
    }

    private static void assertEquals(
            Object expected,
            Object actual,
            String message
    ) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertTrue(
            boolean value,
            String message
    ) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(
            boolean value,
            String message
    ) {
        if (value) {
            throw new AssertionError(message);
        }
    }
}
