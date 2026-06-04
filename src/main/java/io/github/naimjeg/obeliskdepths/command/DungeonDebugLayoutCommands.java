package io.github.naimjeg.obeliskdepths.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.content.DungeonContent;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSitePlacement;
import io.github.naimjeg.obeliskdepths.worldgen.structure.ObeliskDungeonStructure;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraph;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphAnalyzer;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphGenerator;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphValidator;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonGraphEmbeddingPlanner;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutResolver;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutPlan;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonTemplateGeometryResolver;
import io.github.naimjeg.obeliskdepths.worldgen.structure.generation.DungeonGenerationCatalog;
import io.github.naimjeg.obeliskdepths.worldgen.structure.piece.DungeonPiecePlanCompiler;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;

final class DungeonDebugLayoutCommands {
    private static final int MAX_FAILURE_SUMMARIES = 8;

    private DungeonDebugLayoutCommands() {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("layout-test")
                .then(Commands.argument("count", IntegerArgumentType.integer(1, 1000))
                        .executes(context -> layoutTest(
                                context.getSource(),
                                IntegerArgumentType.getInteger(context, "count")
                        ))));
    }

    private static int layoutTest(
            CommandSourceStack source,
            int count
    ) {
        int successful = 0;
        int failed = 0;
        int deterministicMismatches = 0;
        int maxNodes = 0;
        int maxRooms = 0;
        int maxCorridors = 0;
        List<String> failureSummaries = new ArrayList<>();
        var traceContext =
                io.github.naimjeg.obeliskdepths.worldgen.debug
                        .DungeonWorldgenTrace.Context.disabled(null);
        DungeonGenerationCatalog catalog = DungeonGenerationCatalog.fromSnapshot(
                ObeliskDungeonStructure.DEFAULT_THEME_ID,
                DungeonContent.active(),
                new DungeonTemplateGeometryResolver(
                        source.getLevel().getStructureManager()
                )
        );

        for (int index = 0; index < count; index++) {
            long seed = index * 0x9E3779B97F4A7C15L;
            BlockPos layoutOrigin = new BlockPos(
                    (int) (index * 9973L),
                    DungeonSitePlacement.PREVIEW_Y - 1,
                    (int) (index * -7919L)
            );

            try {
                DungeonGraph graph = DungeonGraphGenerator.generate(seed);
                DungeonGraphValidator.validate(graph);
                DungeonGraphAnalyzer.analyze(graph);

                if (!graph.equals(DungeonGraphGenerator.generate(seed))) {
                    deterministicMismatches++;
                }

                DungeonLayoutPlan plan = DungeonGraphEmbeddingPlanner.embed(
                        graph,
                        layoutOrigin,
                        catalog
                );
                var resolved = DungeonLayoutResolver.resolveProcedural(
                        layoutOrigin,
                        plan,
                        graph.primaryEntryNodeId(),
                        catalog,
                        traceContext
                );
                var piecePlan = DungeonPiecePlanCompiler.compile(
                        resolved,
                        null,
                        traceContext
                );
                successful++;
                maxNodes = Math.max(maxNodes, graph.nodes().size());
                maxRooms = Math.max(maxRooms, plan.nodes().size());
                maxCorridors = Math.max(maxCorridors, (int) piecePlan.corridorCount());
            } catch (RuntimeException exception) {
                failed++;

                if (failureSummaries.size() < MAX_FAILURE_SUMMARIES) {
                    String message = exception.getMessage() == null
                            ? exception.getClass().getSimpleName()
                            : exception.getMessage();
                    failureSummaries.add("seed=" + seed + " reason=" + message);
                }
            }
        }

        DungeonDebugCommandUtil.info(
                source,
                "layout-test complete: pure graph/layout/piece-plan validation only; no runtime dungeon, session, saved data, or mobs were created."
        );
        DungeonDebugCommandUtil.info(
                source,
                "  count="
                        + count
                        + ", successful="
                        + successful
                        + ", failed="
                        + failed
                        + ", deterministicMismatches="
                        + deterministicMismatches
                        + ", maxGraphNodes="
                        + maxNodes
                        + ", maxLayoutRooms="
                        + maxRooms
                        + ", maxCorridors="
                        + maxCorridors
        );

        for (String failure : failureSummaries) {
            DungeonDebugCommandUtil.failure(source, "  " + failure);
        }

        return Command.SINGLE_SUCCESS;
    }
}
