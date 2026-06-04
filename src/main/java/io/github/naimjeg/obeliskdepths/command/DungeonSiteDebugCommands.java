package io.github.naimjeg.obeliskdepths.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.content.DungeonContent;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonLayoutConstants;
import io.github.naimjeg.obeliskdepths.dungeon.site.*;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import io.github.naimjeg.obeliskdepths.worldgen.structure.ObeliskDungeonStructure;
import io.github.naimjeg.obeliskdepths.worldgen.structure.generation.DungeonGenerationCatalog;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraph;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphAnalysis;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphAnalyzer;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphGenerator;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.Optional;

public final class DungeonSiteDebugCommands {
    private DungeonSiteDebugCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> site() {
        return Commands.literal("site")
                .then(Commands.literal("key-here")
                        .executes(context -> keyHere(context.getSource())))
                .then(Commands.literal("check")
                        .then(Commands.argument("startChunkX", IntegerArgumentType.integer())
                                .then(Commands.argument("startChunkZ", IntegerArgumentType.integer())
                                        .executes(context -> check(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "startChunkX"),
                                                IntegerArgumentType.getInteger(context, "startChunkZ")
                                        )))));
    }

    private static int keyHere(CommandSourceStack source) {
        ServerPlayer player;

        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }

        ChunkPos chunkPos = player.chunkPosition();
        DungeonSiteKey key = DungeonSiteKey.fromStartChunk(chunkPos);

        source.sendSuccess(
                () -> Component.literal(
                        "Worldgen dungeon site key here: "
                                + key
                                + ", territory="
                                + key.toTerritoryId()
                ),
                false
        );

        return Command.SINGLE_SUCCESS;
    }

    private static int check(
            CommandSourceStack source,
            int startChunkX,
            int startChunkZ
    ) {
        ServerLevel dungeonLevel = dungeonLevel(source);

        if (dungeonLevel == null) {
            source.sendFailure(Component.literal("ObeliskDepths dimension is not loaded."));
            return 0;
        }

        DungeonSiteKey key = new DungeonSiteKey(startChunkX, startChunkZ);

        Optional<ResolvedDungeonSite> resolved =
                DungeonManagerSavedData.get(dungeonLevel)
                        .sites()
                        .snapshot(key)
                        .map(site -> new ResolvedDungeonSite(
                                site,
                                DungeonSiteProjectionSource.SAVED_SNAPSHOT
                        ));

        if (resolved.isEmpty()) {
            source.sendFailure(Component.literal(
                    "No authoritative dungeon site projection found for key " + key + "."
            ));
            return 0;
        }

        sendSiteInfo(source, dungeonLevel, resolved.get());

        return Command.SINGLE_SUCCESS;
    }

    private static void sendSiteInfo(
            CommandSourceStack source,
            ServerLevel dungeonLevel,
            ResolvedDungeonSite resolved
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        DungeonSite site = resolved.site();
        DungeonSiteKey key = site.key();

        String metadataSource = resolved.source().name().toLowerCase();

        String usageState = data.sites().record(key)
                .map(record -> record.status().getSerializedName()
                        + record.activeInstanceId()
                        .map(instanceId -> ", activeInstance=" + instanceId)
                        .orElse(""))
                .orElse("unreached");

        source.sendSuccess(
                () -> Component.literal(
                        "Worldgen dungeon site "
                                + key
                                + ": authoritative="
                                + resolved.authoritative()
                                + ", source="
                                + metadataSource
                                + ", rooms="
                                + site.rooms().size()
                                + ", usage="
                                + usageState
                                + ", reserved="
                                + data.sites().isReserved(key)
                                + ", unreached="
                                + data.sites().isUnreached(key)
                ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                        "  start="
                                + site.startPos()
                                + ", bounds=["
                                + site.bounds().minX()
                                + ", "
                                + site.bounds().minY()
                                + ", "
                                + site.bounds().minZ()
                                + "] -> ["
                                + site.bounds().maxX()
                                + ", "
                                + site.bounds().maxY()
                                + ", "
                                + site.bounds().maxZ()
                                + "]"
                ),
                false
        );

        for (DungeonGeneratedRoom room : site.rooms()) {
            source.sendSuccess(
                    () -> Component.literal(
                            "  room "
                                    + room.id()
                                    + " type="
                                    + room.type().getSerializedName()
                                    + " anchor="
                                    + room.anchorPos()
                                    + " bounds=["
                                    + room.bounds().minX()
                                    + ", "
                                    + room.bounds().minY()
                                    + ", "
                                    + room.bounds().minZ()
                                    + "] -> ["
                                    + room.bounds().maxX()
                                    + ", "
                                    + room.bounds().maxY()
                                    + ", "
                                    + room.bounds().maxZ()
                                    + "]"
                    ),
                    false
            );
        }

        sendDebugPlanInfo(source, dungeonLevel, site);
    }

    private static void sendDebugPlanInfo(
            CommandSourceStack source,
            ServerLevel dungeonLevel,
            DungeonSite site
    ) {
        ChunkPos chunkPos = site.key().toChunkPos();
        BlockPos layoutOrigin = new BlockPos(
                chunkPos.getMiddleBlockX(),
                DungeonSitePlacement.PREVIEW_Y,
                chunkPos.getMiddleBlockZ()
        );
        long generationSeed = ObeliskDungeonStructure.deriveGenerationSeed(
                dungeonLevel.getSeed(),
                chunkPos,
                layoutOrigin
        );
        DungeonGraph graph = DungeonGraphGenerator.generate(generationSeed);
        DungeonGraphAnalysis analysis = DungeonGraphAnalyzer.analyze(graph);
        DungeonGenerationCatalog catalog = DungeonGenerationCatalog.fromSnapshot(
                ObeliskDungeonStructure.DEFAULT_THEME_ID,
                DungeonContent.active(),
                new DungeonTemplateGeometryResolver(
                        dungeonLevel.getStructureManager()
                )
        );
        DungeonLayoutPlan plan = DungeonGraphEmbeddingPlanner.embed(
                graph,
                layoutOrigin,
                catalog
        );

        source.sendSuccess(
                () -> Component.literal(
                        "  debugGraph seed="
                                + generationSeed
                                + " nodes="
                                + graph.nodes().size()
                                + " treeEdges="
                                + graph.treeEdges().size()
                                + " loopEdges="
                                + graph.loopEdges().size()
                                + " starts="
                                + graph.entryNodeIds().size()
                                + " primaryEntry="
                                + graph.primaryEntryNodeId()
                                + " sectors="
                                + analysis.sectors().size()
                                + " maxBossDistance="
                                + analysis.maxDistanceToBoss()
                                + " (debug reconstruction; runtime reads generated pieces)"
                ),
                false
        );

        source.sendSuccess(
                () -> Component.literal(
                        "  embeddedLayout cellSize="
                                + DungeonLayoutConstants.CELL_SIZE_X
                                + "x"
                                + DungeonLayoutConstants.CELL_SIZE_Y
                                + "x"
                                + DungeonLayoutConstants.CELL_SIZE_Z
                                + ", nodes="
                                + plan.nodes().size()
                                + ", edges="
                                + plan.edges().size()
                ),
                false
        );

        for (DungeonLayoutNode node : plan.nodes()) {
            source.sendSuccess(
                    () -> Component.literal(
                            "    node "
                                    + node.roomId()
                                    + " type="
                                    + node.type().getSerializedName()
                                    + " footprintCells="
                                    + node.footprint().widthCells()
                                    + "x"
                                    + node.footprint().heightCells()
                                    + "x"
                                    + node.footprint().depthCells()
                                    + " connectors="
                                    + node.connectorSides()
                                    + " connectorShape="
                                    + node.connectorShapeType()
                    ),
                    false
            );
        }

        for (DungeonLayoutEdge edge : plan.edges()) {
            source.sendSuccess(
                    () -> Component.literal(
                            "    edge "
                                    + edge.id()
                                    + " "
                                    + edge.fromRoomId()
                                    + "."
                                    + edge.fromSide()
                                    + " -> "
                                    + edge.toRoomId()
                                    + "."
                                    + edge.toSide()
                                    + " widthCells="
                                    + edge.widthCells()
                                    + " kind="
                                    + edge.kind()
                    ),
                    false
            );
        }
    }

    private static ServerLevel dungeonLevel(CommandSourceStack source) {
        return source.getServer().getLevel(ModDimensions.OBELISK_DEPTHS_LEVEL);
    }
}
