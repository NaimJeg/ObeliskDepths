package io.github.naimjeg.obeliskdepths.worldgen.structure.perf;

import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraph;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphAnalysis;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonGraphEdgeKind;
import io.github.naimjeg.obeliskdepths.worldgen.structure.generation.DungeonGenerationPlan;
import io.github.naimjeg.obeliskdepths.worldgen.structure.generation.DungeonGenerationPlanEmitter;
import io.github.naimjeg.obeliskdepths.worldgen.structure.test.DungeonProceduralTestSupport;
import io.github.naimjeg.obeliskdepths.worldgen.structure.test.DungeonProceduralTestSupport.AcceptedGenerationPlan;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class DungeonWorldgenPerformanceMetricsTest {
    private static final long SEED_WINDOW_START = 0x5EC70A11D00DFEEDL;
    private static final int SEED_WINDOW_SIZE = 128;
    private static final BlockPos ORIGIN = new BlockPos(304, 32, 1344);

    private DungeonWorldgenPerformanceMetricsTest() {
    }

    public static void main(String[] args) {
        Metrics metrics = measure(SEED_WINDOW_START, SEED_WINDOW_SIZE, ORIGIN);
        assertTrue(
                metrics.corridorTemplatePlacementCount() == 0,
                "normal procedural corridors should not use corridor NBT template placements"
        );
        assertTrue(
                metrics.corridorPieceCount() <= 1,
                "normal procedural corridors should emit at most one corridor grid piece"
        );
        assertTrue(
                metrics.pieceCount() < metrics.routedCells() + metrics.roomCount(),
                "total piece count should be lower than the old one-piece-per-cell design"
        );
        System.out.println(metrics.toReportLine());
    }

    private static Metrics measure(
            long firstSeed,
            int seedCount,
            BlockPos origin
    ) {
        long graphStart = System.nanoTime();
        AcceptedGenerationPlan accepted =
                DungeonProceduralTestSupport.firstAcceptedGenerationPlan(
                        firstSeed,
                        seedCount,
                        origin,
                        "performance metrics sample"
                );
        DungeonGraph graph = accepted.graph();
        DungeonGraphAnalysis analysis = accepted.analysis();
        long graphNanos = System.nanoTime() - graphStart;

        long planStart = System.nanoTime();
        DungeonGenerationPlan plan = DungeonProceduralTestSupport.generationPlan(
                graph,
                analysis,
                origin,
                accepted.seed(),
                accepted.attemptIndex()
        );
        long planNanos = System.nanoTime() - planStart;

        long emissionStart = System.nanoTime();
        int corridorCellCount = DungeonGenerationPlanEmitter
                .corridorCells(plan)
                .size();
        long emissionNanos = System.nanoTime() - emissionStart;
        int corridorPieces = corridorCellCount == 0 ? 0 : 1;

        return new Metrics(
                accepted.seed(),
                accepted.attemptIndex() + 1,
                graph.nodes().size(),
                graph.edges().size(),
                graph.edges().stream()
                        .filter(edge -> edge.kind() == DungeonGraphEdgeKind.TREE)
                        .count(),
                graph.edges().stream()
                        .filter(edge -> edge.kind() == DungeonGraphEdgeKind.LOOP)
                        .count(),
                graph.edges().stream()
                        .filter(edge -> edge.kind() == DungeonGraphEdgeKind.SECRET)
                        .count(),
                plan.routedCellCount(),
                plan.rooms().size() + corridorPieces + 1,
                corridorPieces,
                0,
                plan.rooms().size(),
                corridorCellCount,
                chunkCount(plan.siteBounds()),
                spanX(plan.siteBounds()),
                spanZ(plan.siteBounds()),
                graphNanos,
                planNanos,
                emissionNanos
        );
    }

    private static int chunkCount(BoundingBox bounds) {
        int minChunkX = Math.floorDiv(bounds.minX(), 16);
        int maxChunkX = Math.floorDiv(bounds.maxX(), 16);
        int minChunkZ = Math.floorDiv(bounds.minZ(), 16);
        int maxChunkZ = Math.floorDiv(bounds.maxZ(), 16);
        return (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
    }

    private static int spanX(BoundingBox bounds) {
        return bounds.maxX() - bounds.minX() + 1;
    }

    private static int spanZ(BoundingBox bounds) {
        return bounds.maxZ() - bounds.minZ() + 1;
    }

    private static void assertTrue(
            boolean value,
            String message
    ) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private record Metrics(
            long seed,
            int attempt,
            int roomCount,
            int graphEdgeCount,
            long treeEdges,
            long loopEdges,
            long secretEdges,
            int routedCells,
            int pieceCount,
            long corridorPieceCount,
            long corridorTemplatePlacementCount,
            long roomTemplatePlacementCount,
            int canonicalCorridorCells,
            int intersectedChunks,
            int spanX,
            int spanZ,
            long graphNanos,
            long planNanos,
            long emissionNanos
    ) {
        private String toReportLine() {
            return "dungeon-worldgen-metrics"
                    + " seed=" + seed
                    + " attempt=" + attempt
                    + " rooms=" + roomCount
                    + " graphEdges=" + graphEdgeCount
                    + " treeEdges=" + treeEdges
                    + " loopEdges=" + loopEdges
                    + " secretEdges=" + secretEdges
                    + " routedCells=" + routedCells
                    + " pieces=" + pieceCount
                    + " corridorPieces=" + corridorPieceCount
                    + " corridorTemplatePlacements=" + corridorTemplatePlacementCount
                    + " roomTemplatePlacements=" + roomTemplatePlacementCount
                    + " canonicalCorridorCells=" + canonicalCorridorCells
                    + " intersectedChunks=" + intersectedChunks
                    + " spanX=" + spanX
                    + " spanZ=" + spanZ
                    + " graphMs=" + nanosToMillis(graphNanos)
                    + " planMs=" + nanosToMillis(planNanos)
                    + " emissionMs=" + nanosToMillis(emissionNanos);
        }

        private static String nanosToMillis(long nanos) {
            return String.format(java.util.Locale.ROOT, "%.3f", nanos / 1_000_000.0D);
        }
    }
}
