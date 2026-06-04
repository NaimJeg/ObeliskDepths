package io.github.naimjeg.obeliskdepths.worldgen.structure.test;

import io.github.naimjeg.obeliskdepths.worldgen.debug.DungeonWorldgenTrace;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraph;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphAnalysis;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphAnalyzer;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphGenerator;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphValidator;
import io.github.naimjeg.obeliskdepths.worldgen.structure.generation.DungeonGenerationPlan;
import io.github.naimjeg.obeliskdepths.worldgen.structure.generation.DungeonGenerationCatalog;
import io.github.naimjeg.obeliskdepths.worldgen.structure.generation.DungeonGenerationPlanner;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonGraphEmbeddingPlanner;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutGenerationException;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutPlan;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutResolver;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonResolvedTopologyValidator;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.ResolvedDungeonLayout;
import io.github.naimjeg.obeliskdepths.worldgen.structure.piece.DungeonPiecePlan;
import io.github.naimjeg.obeliskdepths.worldgen.structure.piece.DungeonPiecePlanCompiler;
import net.minecraft.core.BlockPos;

public final class DungeonProceduralTestSupport {
    public static final int PRODUCTION_SPATIAL_LAYOUT_ATTEMPTS = 3;
    public static final DungeonWorldgenTrace.Context TRACE =
            DungeonWorldgenTrace.Context.disabled(null);
    public static final DungeonGenerationCatalog CATALOG =
            DungeonGenerationCatalogTestFixtures.catalog();

    private DungeonProceduralTestSupport() {
    }

    public static AcceptedProceduralLayout firstAcceptedProceduralLayout(
            DungeonGraph graph,
            BlockPos origin,
            long generationSeed,
            String scenario
    ) {
        DungeonLayoutGenerationException lastFailure = null;
        for (int attempt = 0;
             attempt < PRODUCTION_SPATIAL_LAYOUT_ATTEMPTS;
             attempt++) {
            try {
                return acceptedProceduralLayout(
                        graph,
                        origin,
                        generationSeed,
                        attempt
                );
            } catch (DungeonLayoutGenerationException exception) {
                lastFailure = exception;
            }
        }

        throw new AssertionError(
                scenario
                        + " expected a valid procedural layout within production retry budget"
                        + " seed="
                        + generationSeed
                        + " attempts="
                        + PRODUCTION_SPATIAL_LAYOUT_ATTEMPTS
                        + " graphNodes="
                        + graph.nodes().size()
                        + " treeEdges="
                        + graph.treeEdges().size()
                        + " loopEdges="
                        + graph.loopEdges().size()
                        + " lastFailure="
                        + (lastFailure == null ? "<none>" : lastFailure.getMessage()),
                lastFailure
        );
    }

    public static AcceptedProceduralLayout acceptedProceduralLayout(
            DungeonGraph graph,
            BlockPos origin,
            long generationSeed,
            int attemptIndex
    ) {
        DungeonLayoutPlan layout = DungeonGraphEmbeddingPlanner.embed(
                graph,
                origin,
                CATALOG,
                attemptSalt(generationSeed, attemptIndex)
        );
        ResolvedDungeonLayout resolved = DungeonLayoutResolver.resolveProcedural(
                origin,
                layout,
                graph.primaryEntryNodeId(),
                CATALOG,
                TRACE
        );
        DungeonResolvedTopologyValidator.validateProcedural(
                graph,
                layout,
                resolved,
                attemptIndex + 1
        );
        DungeonPiecePlan pieces = DungeonPiecePlanCompiler.compile(
                resolved,
                null,
                TRACE
        );
        return new AcceptedProceduralLayout(
                attemptIndex,
                layout,
                resolved,
                pieces
        );
    }

    public static AcceptedGenerationPlan firstAcceptedGenerationPlan(
            long firstSeed,
            int seedCount,
            BlockPos origin,
            String scenario
    ) {
        DungeonLayoutGenerationException lastFailure = null;
        long lastSeed = firstSeed;
        int lastAttempt = -1;
        for (long seed = firstSeed; seed < firstSeed + seedCount; seed++) {
            DungeonGraph graph = DungeonGraphGenerator.generate(seed);
            DungeonGraphValidator.validate(graph);
            DungeonGraphAnalysis analysis = DungeonGraphAnalyzer.analyze(graph);
            for (int attempt = 0;
                 attempt < PRODUCTION_SPATIAL_LAYOUT_ATTEMPTS;
                 attempt++) {
                try {
                    DungeonGenerationPlan plan = generationPlan(
                            graph,
                            analysis,
                            origin,
                            seed,
                            attempt
                    );
                    return new AcceptedGenerationPlan(
                            seed,
                            attempt,
                            graph,
                            analysis,
                            plan
                    );
                } catch (DungeonLayoutGenerationException exception) {
                    lastFailure = exception;
                    lastSeed = seed;
                    lastAttempt = attempt;
                }
            }
        }

        throw new AssertionError(
                scenario
                        + " expected an accepted generation plan"
                        + " seedWindowStart="
                        + firstSeed
                        + " seedCount="
                        + seedCount
                        + " productionAttempts="
                        + PRODUCTION_SPATIAL_LAYOUT_ATTEMPTS
                        + " lastSeed="
                        + lastSeed
                        + " lastAttempt="
                        + (lastAttempt + 1)
                        + " lastFailure="
                        + (lastFailure == null ? "<none>" : lastFailure.getMessage()),
                lastFailure
        );
    }

    public static DungeonGenerationPlan generationPlan(
            DungeonGraph graph,
            DungeonGraphAnalysis analysis,
            BlockPos origin,
            long generationSeed,
            int attemptIndex
    ) {
        return DungeonGenerationPlanner.plan(
                graph,
                analysis,
                origin,
                CATALOG,
                attemptSalt(generationSeed, attemptIndex),
                attemptIndex + 1,
                TRACE
        );
    }

    public static long attemptSalt(
            long generationSeed,
            int attemptIndex
    ) {
        long value = generationSeed
                ^ ((long) attemptIndex * 0xD1B54A32D192ED03L)
                ^ 0x5350415449414C30L;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }

    public record AcceptedProceduralLayout(
            int attemptIndex,
            DungeonLayoutPlan layout,
            ResolvedDungeonLayout resolved,
            DungeonPiecePlan pieces
    ) {
    }

    public record AcceptedGenerationPlan(
            long seed,
            int attemptIndex,
            DungeonGraph graph,
            DungeonGraphAnalysis analysis,
            DungeonGenerationPlan plan
    ) {
    }
}
