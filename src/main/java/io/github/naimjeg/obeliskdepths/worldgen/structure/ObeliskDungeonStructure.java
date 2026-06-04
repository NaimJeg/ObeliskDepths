package io.github.naimjeg.obeliskdepths.worldgen.structure;

import com.mojang.serialization.MapCodec;
import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.content.DungeonContent;
import io.github.naimjeg.obeliskdepths.dungeon.content.DungeonContentException;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSitePlacement;
import io.github.naimjeg.obeliskdepths.registry.ModWorldgen;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraph;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphAnalysis;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphAnalyzer;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphEdge;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonGraphEdgeKind;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphGenerator;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphValidator;
import io.github.naimjeg.obeliskdepths.worldgen.debug.DungeonWorldgenTrace;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonNodeAnalysis;
import io.github.naimjeg.obeliskdepths.worldgen.structure.generation.DungeonGenerationPlan;
import io.github.naimjeg.obeliskdepths.worldgen.structure.generation.DungeonGenerationPlanEmitter;
import io.github.naimjeg.obeliskdepths.worldgen.structure.generation.DungeonGenerationPlanner;
import io.github.naimjeg.obeliskdepths.worldgen.structure.generation.DungeonGenerationCatalog;
import io.github.naimjeg.obeliskdepths.worldgen.structure.generation.PlacedDungeonRoom;
import io.github.naimjeg.obeliskdepths.worldgen.structure.generation.RoutedDungeonConnection;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonGraphEmbeddingPlanner;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonLayoutConstants;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutGenerationException;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonResolvedTopologyValidator;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonTemplateGeometryResolver;
import io.github.naimjeg.obeliskdepths.worldgen.structure.placement.ObeliskDungeonSiteOverlapGuard;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/*
 * Vanilla worldgen entry point for Obelisk dungeon sites.
 *
 * Current behavior:
 * - lets vanilla StructurePlacement decide start chunks
 * - creates a valid StructureStart with serialized ObeliskDungeonPiece metadata
 * - delegates block placement and piece metadata ownership to ObeliskDungeonPiece
 *
 * Later:
 * - choose Y from dimension terrain/noise
 * - choose theme/layout from seed + start chunk
 * - generate authored room/corridor pieces
 * - expose projection metadata through GeneratedDungeonSiteReader
 */
public final class ObeliskDungeonStructure extends Structure {
    private static final int MAX_SPATIAL_LAYOUT_ATTEMPTS = 3;
    public static final Identifier DEFAULT_THEME_ID =
            Identifier.fromNamespaceAndPath(ObeliskDepths.MOD_ID, "great_swamp");

    public static final MapCodec<ObeliskDungeonStructure> CODEC =
            Structure.simpleCodec(ObeliskDungeonStructure::new);

    public ObeliskDungeonStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(
            GenerationContext context
    ) {
        ChunkPos chunkPos = context.chunkPos();

        /*
         * Use the center of the structure start chunk.
         *
         * Later:
         * - read placement/config from JSON
         * - use layout generator
         * - avoid unsuitable terrain pockets if this dimension becomes noisy
         */
        BlockPos previewLayoutOrigin = new BlockPos(
                chunkPos.getMiddleBlockX(),
                DungeonSitePlacement.PREVIEW_Y,
                chunkPos.getMiddleBlockZ()
        );

        Holder<Biome> actualBiome = context.chunkGenerator()
                .getBiomeSource()
                .getNoiseBiome(
                        QuartPos.fromBlock(previewLayoutOrigin.getX()),
                        QuartPos.fromBlock(previewLayoutOrigin.getY()),
                        QuartPos.fromBlock(previewLayoutOrigin.getZ()),
                        context.randomState().sampler()
                );

        String actualBiomeId = actualBiome.unwrapKey()
                .map(key -> key.identifier().toString())
                .orElse("<direct/unregistered>");

        boolean valid = context.validBiome().test(actualBiome);

        if (!valid) {
            ObeliskDepths.LOGGER.warn(
                    "[OD structure] rejected invalid biome candidate "
                            + "chunk={} anchor={} actualBiome={}",
                    chunkPos,
                    previewLayoutOrigin,
                    actualBiomeId
            );
            return Optional.empty();
        }

        long generationStart = System.nanoTime();
        long generationSeed = deriveGenerationSeed(
                context.seed(),
                chunkPos,
                previewLayoutOrigin
        );
        DungeonWorldgenTrace.Context traceContext = DungeonWorldgenTrace.context(chunkPos);
        DungeonGraph graph = DungeonGraphGenerator.generate(generationSeed);
        long graphGenerated = System.nanoTime();
        DungeonGraphValidator.validate(graph);
        long graphValidated = System.nanoTime();
        DungeonGraphAnalysis analysis = DungeonGraphAnalyzer.analyze(graph);
        long graphAnalyzed = System.nanoTime();

        DungeonTemplateGeometryResolver geometryResolver =
                new DungeonTemplateGeometryResolver(
                        context.structureTemplateManager()
                );
        DungeonGenerationCatalog catalog;
        try {
            catalog = DungeonGenerationCatalog.fromSnapshot(
                    DEFAULT_THEME_ID,
                    DungeonContent.active(),
                    geometryResolver
            );
        } catch (DungeonContentException exception) {
            ObeliskDepths.LOGGER.error(
                    "[OD structure] rejected dungeon candidate due to content error chunk={} seed={} resource={} reason={}",
                    chunkPos,
                    generationSeed,
                    exception.resourceId(),
                    exception.reason()
            );
            return Optional.empty();
        }
        DungeonLayoutGenerationException lastFailure = null;
        for (int attemptIndex = 0;
             attemptIndex < MAX_SPATIAL_LAYOUT_ATTEMPTS;
             attemptIndex++) {
            int layoutAttempt = attemptIndex + 1;
            long attemptStart = System.nanoTime();
            long attemptSalt = deriveLayoutAttemptSalt(
                    generationSeed,
                    attemptIndex
            );
            DungeonGenerationPlan generationPlan = null;

            try {
                generationPlan = DungeonGenerationPlanner.plan(
                        graph,
                        analysis,
                        previewLayoutOrigin,
                        catalog,
                        attemptSalt,
                        layoutAttempt,
                        traceContext
                );
                long planned = System.nanoTime();
                tracePlanRooms(
                        traceContext,
                        graph,
                        analysis,
                        generationPlan
                );
                BoundingBox plannedBounds = generationPlan.siteBounds();
                DungeonTerrainHeightSampler.Result terrainSample =
                        DungeonTerrainHeightSampler.sample(
                                context,
                                new BlockPos(
                                        chunkPos.getMiddleBlockX(),
                                        previewLayoutOrigin.getY(),
                                        chunkPos.getMiddleBlockZ()
                                ),
                                plannedBounds
                        );
                DungeonGenerationPlan translatedPlan =
                        generationPlan.translatedY(
                                terrainSample.verticalOffset()
                        );
                BoundingBox translatedBounds = translatedPlan.siteBounds();
                BlockPos startRoomAnchor = translatedPlan.primaryEntryAnchor();
                traceTerrainSample(
                        traceContext,
                        chunkPos,
                        layoutAttempt,
                        terrainSample,
                        generationPlan.rooms().size()
                                + (DungeonGenerationPlanEmitter
                                .corridorCells(generationPlan)
                                .isEmpty()
                                ? 1
                                : 2)
                );

                Optional<ObeliskDungeonSiteOverlapGuard.Rejection> rejection =
                        ObeliskDungeonSiteOverlapGuard.findRejection(
                                context.seed(),
                                chunkPos,
                                translatedBounds
                        );
                long overlapChecked = System.nanoTime();

                if (rejection.isPresent()) {
                    tracePlanAttemptSummary(
                            traceContext,
                            chunkPos,
                            generationSeed,
                            layoutAttempt,
                            graph,
                            analysis,
                            translatedPlan,
                            "overlap:" + rejection.get()
                    );
                    return Optional.empty();
                }

                tracePlanAttemptSummary(
                        traceContext,
                        chunkPos,
                        generationSeed,
                        layoutAttempt,
                        graph,
                        analysis,
                        translatedPlan,
                        "accepted"
                );
                traceAcceptedTiming(
                        traceContext,
                        chunkPos,
                        layoutAttempt,
                        graphGenerated - generationStart,
                        graphValidated - graphGenerated,
                        graphAnalyzed - graphValidated,
                        planned - attemptStart,
                        overlapChecked - planned,
                        overlapChecked - generationStart
                );

                int corridorCells = DungeonGenerationPlanEmitter
                        .corridorCells(translatedPlan)
                        .size();
                ObeliskDepths.LOGGER.debug(
                        "[OD structure] accepted dungeon candidate chunk={} previewLayoutOrigin={} translatedLayoutOrigin={} primaryEntryAnchor={} seed={} layoutAttempt={} terrainHeights={} terrainMedian={} requestedBaseY={} finalBaseY={} verticalOffset={} plannedBounds={} translatedBounds={} graphNodes={} treeEdges={} loopEdges={} starts={} sectors={} maxBossDistance={} rooms={} corridors={} corridorCells={} footprintBlocks={}x{} footprintCells={}x{}",
                        chunkPos,
                        previewLayoutOrigin,
                        translatedPlan.origin(),
                        startRoomAnchor,
                        generationSeed,
                        layoutAttempt,
                        DungeonTerrainHeightSampler.describeSamples(
                                terrainSample.samples()
                        ),
                        terrainSample.medianHeight(),
                        terrainSample.requestedBaseY(),
                        terrainSample.baseY(),
                        terrainSample.verticalOffset(),
                        plannedBounds,
                        translatedBounds,
                        graph.nodes().size(),
                        graph.treeEdges().size(),
                        graph.loopEdges().size(),
                        graph.entryNodeIds().size(),
                        analysis.sectors().size(),
                        analysis.maxDistanceToBoss(),
                        translatedPlan.rooms().size(),
                        translatedPlan.connections().size(),
                        corridorCells,
                        translatedBounds.getXSpan(),
                        translatedBounds.getZSpan(),
                        (translatedBounds.getXSpan() + DungeonLayoutConstants.CELL_SIZE_X - 1) / DungeonLayoutConstants.CELL_SIZE_X,
                        (translatedBounds.getZSpan() + DungeonLayoutConstants.CELL_SIZE_Z - 1) / DungeonLayoutConstants.CELL_SIZE_Z
                );
                ObeliskDepths.LOGGER.debug(
                        "[OD timing] structure chunk={} layoutAttempt={} graphGenerateMicros={} graphValidateMicros={} graphAnalyzeMicros={} planMicros={} overlapMicros={} totalMicros={}",
                        chunkPos,
                        layoutAttempt,
                        (graphGenerated - generationStart) / 1_000L,
                        (graphValidated - graphGenerated) / 1_000L,
                        (graphAnalyzed - graphValidated) / 1_000L,
                        (planned - attemptStart) / 1_000L,
                        (overlapChecked - planned) / 1_000L,
                        (overlapChecked - generationStart) / 1_000L
                );

                DungeonGenerationPlan acceptedPlan = translatedPlan;
                return Optional.of(new GenerationStub(
                        startRoomAnchor,
                        builder -> DungeonGenerationPlanEmitter.emit(
                                builder,
                                acceptedPlan
                        )
                ));
            } catch (DungeonContentException exception) {
                String reason = exception.getClass().getSimpleName()
                        + ":"
                        + exception.getMessage();
                tracePlanAttemptSummary(
                        traceContext,
                        chunkPos,
                        generationSeed,
                        layoutAttempt,
                        graph,
                        analysis,
                        generationPlan,
                        "content:" + DungeonWorldgenTrace.bounded(reason)
                );
                ObeliskDepths.LOGGER.error(
                        "[OD structure] rejected dungeon candidate due to content error chunk={} layoutAttempt={}/{} seed={} resource={} reason={}",
                        chunkPos,
                        layoutAttempt,
                        MAX_SPATIAL_LAYOUT_ATTEMPTS,
                        generationSeed,
                        exception.resourceId(),
                        exception.reason()
                );
                return Optional.empty();
            } catch (DungeonLayoutGenerationException exception) {
                lastFailure = exception;
                String reason = exception.getClass().getSimpleName()
                        + ":"
                        + exception.getMessage();
                tracePlanAttemptSummary(
                        traceContext,
                        chunkPos,
                        generationSeed,
                        layoutAttempt,
                        graph,
                        analysis,
                        generationPlan,
                        "retry:" + DungeonWorldgenTrace.bounded(reason)
                );
                ObeliskDepths.LOGGER.warn(
                        "[OD structure] rejected spatial dungeon layout attempt chunk={} layoutAttempt={}/{} seed={} reason={}",
                        chunkPos,
                        layoutAttempt,
                        MAX_SPATIAL_LAYOUT_ATTEMPTS,
                        generationSeed,
                        DungeonWorldgenTrace.bounded(reason)
                );
            } catch (RuntimeException exception) {
                ObeliskDepths.LOGGER.error(
                        "[OD structure] hard dungeon generation failure chunk={} layoutAttempt={}/{} seed={}",
                        chunkPos,
                        layoutAttempt,
                        MAX_SPATIAL_LAYOUT_ATTEMPTS,
                        generationSeed,
                        exception
                );
                throw exception;
            }
        }

        ObeliskDepths.LOGGER.warn(
                "[OD structure] rejected dungeon candidate after spatial retries chunk={} seed={} attempts={} lastReason={}",
                chunkPos,
                generationSeed,
                MAX_SPATIAL_LAYOUT_ATTEMPTS,
                lastFailure == null
                        ? "<none>"
                        : DungeonWorldgenTrace.bounded(lastFailure.getMessage())
        );
        return Optional.empty();
    }

    @Override
    public StructureType<?> type() {
        return ModWorldgen.OBELISK_DUNGEON.get();
    }

    private static void tracePlanAttemptSummary(
            DungeonWorldgenTrace.Context traceContext,
            ChunkPos startChunk,
            long generationSeed,
            int layoutAttempt,
            DungeonGraph graph,
            DungeonGraphAnalysis analysis,
            DungeonGenerationPlan plan,
            String reason
    ) {
        Map<DungeonGraphEdgeKind, Long> graphCounts =
                graphEdgeCounts(graph);
        Map<DungeonGraphEdgeKind, Long> resolvedCounts =
                planConnectionCounts(plan);
        BoundingBox siteBounds = plan == null ? null : plan.siteBounds();
        int corridorCells = plan == null
                ? 0
                : DungeonGenerationPlanEmitter.corridorCells(plan).size();
        DungeonWorldgenTrace.debug(
                traceContext,
                () -> "stage=structure-attempt-summary startChunk="
                        + startChunk.x()
                        + ","
                        + startChunk.z()
                        + " generationSeed="
                        + generationSeed
                        + " layoutAttempt="
                        + layoutAttempt
                        + " graphNodes="
                        + graph.nodes().size()
                        + " graphTreeEdges="
                        + graphCounts.get(DungeonGraphEdgeKind.TREE)
                        + " graphLoopEdges="
                        + graphCounts.get(DungeonGraphEdgeKind.LOOP)
                        + " graphSecretEdges="
                        + graphCounts.get(DungeonGraphEdgeKind.SECRET)
                        + " graphCycleRank="
                        + DungeonResolvedTopologyValidator.cycleRank(
                        graph.nodes().size(),
                        graph.edges().size(),
                        1
                )
                        + " primaryEntry="
                        + graph.primaryEntryNodeId()
                        + " sectorRootSides="
                        + sectorRootSides(graph, analysis, plan)
                        + " layoutRooms="
                        + (plan == null ? 0 : plan.rooms().size())
                        + " layoutEdges="
                        + (plan == null ? 0 : plan.connections().size())
                        + " resolvedConnections="
                        + (plan == null ? 0 : plan.connections().size())
                        + " resolvedTreeEdges="
                        + resolvedCounts.get(DungeonGraphEdgeKind.TREE)
                        + " resolvedLoopEdges="
                        + resolvedCounts.get(DungeonGraphEdgeKind.LOOP)
                        + " resolvedSecretEdges="
                        + resolvedCounts.get(DungeonGraphEdgeKind.SECRET)
                        + " resolvedCycleRank="
                        + planCycleRank(plan)
                        + " routedCells="
                        + (plan == null ? 0 : plan.routedCellCount())
                        + " roomPieces="
                        + (plan == null ? 0 : plan.rooms().size())
                        + " corridorPieces="
                        + (corridorCells == 0 ? 0 : 1)
                        + " physicalPieces="
                        + (plan == null ? 0 : plan.rooms().size()
                        + (corridorCells == 0 ? 1 : 2))
                        + " roomTemplatePlacements="
                        + (plan == null ? 0 : plan.rooms().size())
                        + " corridorTemplatePlacements=0"
                        + " proceduralCorridorCells="
                        + corridorCells
                        + " intersectedChunks="
                        + (siteBounds == null ? 0 : intersectedChunkCount(siteBounds))
                        + " siteBounds="
                        + siteBounds
                        + " siteXSpan="
                        + (siteBounds == null ? 0 : siteBounds.getXSpan())
                        + " siteZSpan="
                        + (siteBounds == null ? 0 : siteBounds.getZSpan())
                        + " reason="
                        + reason
        );
    }

    private static void tracePlanRooms(
            DungeonWorldgenTrace.Context traceContext,
            DungeonGraph graph,
            DungeonGraphAnalysis analysis,
            DungeonGenerationPlan plan
    ) {
        Map<String, String> radialSides =
                radialSidesByRoom(graph, analysis, plan);
        for (PlacedDungeonRoom room : plan.rooms()) {
            DungeonNodeAnalysis nodeAnalysis = analysis.requireNode(room.id());
            DungeonWorldgenTrace.debug(
                    traceContext,
                    () -> "stage=layout-room room="
                            + room.id()
                            + " type="
                            + room.type()
                            + " sector="
                            + (nodeAnalysis.sectorIndex().isPresent()
                            ? nodeAnalysis.sectorIndex().getAsInt()
                            : "none")
                            + " treeDepth="
                            + nodeAnalysis.treeDepth()
                            + " cellOrigin="
                            + room.cellOrigin()
                            + " bounds="
                            + room.bounds()
                            + " radialSide="
                            + radialSides.getOrDefault(room.id(), "none")
            );
        }
    }

    private static void traceAcceptedTiming(
            DungeonWorldgenTrace.Context traceContext,
            ChunkPos startChunk,
            int layoutAttempt,
            long graphGenerateNanos,
            long graphValidateNanos,
            long graphAnalyzeNanos,
            long planNanos,
            long overlapNanos,
            long totalNanos
    ) {
        DungeonWorldgenTrace.debug(
                traceContext,
                () -> "stage=structure-timing startChunk="
                        + startChunk.x()
                        + ","
                        + startChunk.z()
                        + " layoutAttempt="
                        + layoutAttempt
                        + " graphGenerateMicros="
                        + graphGenerateNanos / 1_000L
                        + " graphValidateMicros="
                        + graphValidateNanos / 1_000L
                        + " graphAnalyzeMicros="
                        + graphAnalyzeNanos / 1_000L
                        + " planMicros="
                        + planNanos / 1_000L
                        + " overlapMicros="
                        + overlapNanos / 1_000L
                        + " totalMicros="
                        + totalNanos / 1_000L
        );
    }

    private static void traceTerrainSample(
            DungeonWorldgenTrace.Context traceContext,
            ChunkPos startChunk,
            int layoutAttempt,
            DungeonTerrainHeightSampler.Result sample,
            int pieceCount
    ) {
        DungeonWorldgenTrace.debug(
                traceContext,
                () -> "stage=terrain-height-sample startChunk="
                        + startChunk.x()
                        + ","
                        + startChunk.z()
                        + " layoutAttempt="
                        + layoutAttempt
                        + " heights="
                        + DungeonTerrainHeightSampler.describeSamples(
                                sample.samples()
                        )
                        + " medianHeight="
                        + sample.medianHeight()
                        + " requestedBaseY="
                        + sample.requestedBaseY()
                        + " finalBaseY="
                        + sample.baseY()
                        + " verticalOffset="
                        + sample.verticalOffset()
                        + " plannedBounds="
                        + sample.plannedBounds()
                        + " translatedBounds="
                        + sample.translatedBounds()
                        + " terrainAdaptation=none"
                        + " pieceCount="
                        + pieceCount
        );
    }

    private static int intersectedChunkCount(BoundingBox bounds) {
        int minChunkX = Math.floorDiv(bounds.minX(), 16);
        int maxChunkX = Math.floorDiv(bounds.maxX(), 16);
        int minChunkZ = Math.floorDiv(bounds.minZ(), 16);
        int maxChunkZ = Math.floorDiv(bounds.maxZ(), 16);
        return (maxChunkX - minChunkX + 1)
                * (maxChunkZ - minChunkZ + 1);
    }

    private static Map<DungeonGraphEdgeKind, Long> graphEdgeCounts(
            DungeonGraph graph
    ) {
        Map<DungeonGraphEdgeKind, Long> counts =
                new EnumMap<>(DungeonGraphEdgeKind.class);
        for (DungeonGraphEdgeKind kind : DungeonGraphEdgeKind.values()) {
            counts.put(kind, graph.edges()
                    .stream()
                    .filter(edge -> edge.kind() == kind)
                    .count());
        }
        return counts;
    }

    private static Map<DungeonGraphEdgeKind, Long> planConnectionCounts(
            DungeonGenerationPlan plan
    ) {
        Map<DungeonGraphEdgeKind, Long> counts =
                new EnumMap<>(DungeonGraphEdgeKind.class);
        for (DungeonGraphEdgeKind kind : DungeonGraphEdgeKind.values()) {
            long count = 0L;
            if (plan != null) {
                for (RoutedDungeonConnection connection : plan.connections()) {
                    if (connection.kind() == kind) {
                        count++;
                    }
                }
            }
            counts.put(kind, count);
        }
        return counts;
    }

    private static int planCycleRank(DungeonGenerationPlan plan) {
        if (plan == null) {
            return 0;
        }
        return DungeonResolvedTopologyValidator.cycleRank(
                plan.rooms().size(),
                plan.connections().size(),
                1
        );
    }

    private static List<String> sectorRootSides(
            DungeonGraph graph,
            DungeonGraphAnalysis analysis,
            DungeonGenerationPlan plan
    ) {
        if (plan == null) {
            return List.of();
        }
        Map<String, String> sides = radialSidesByRoom(graph, analysis, plan);
        return sectorRoots(graph, analysis)
                .stream()
                .map(root -> root + ":" + sides.getOrDefault(root, "none"))
                .toList();
    }

    private static Map<String, String> radialSidesByRoom(
            DungeonGraph graph,
            DungeonGraphAnalysis analysis,
            DungeonGenerationPlan plan
    ) {
        Map<String, PlacedDungeonRoom> roomsById = new LinkedHashMap<>();
        for (PlacedDungeonRoom room : plan.rooms()) {
            roomsById.put(room.id(), room);
        }
        Map<String, RoutedDungeonConnection> connectionsById = new LinkedHashMap<>();
        for (RoutedDungeonConnection connection : plan.connections()) {
            connectionsById.put(connection.id(), connection);
        }

        Map<String, String> radialBySector = new LinkedHashMap<>();
        for (String root : sectorRoots(graph, analysis)) {
            DungeonGraphEdge rootEdge = graph.treeEdges()
                    .stream()
                    .filter(edge -> edge.targetNodeId().equals(root))
                    .findFirst()
                    .orElse(null);
            if (rootEdge == null) {
                continue;
            }
            RoutedDungeonConnection connection = connectionsById.get(
                    DungeonGraphEmbeddingPlanner.layoutEdgeIdFor(rootEdge)
            );
            if (connection == null) {
                continue;
            }
            String parentPort = connection.from().roomId()
                    .equals(rootEdge.sourceNodeId())
                    ? connection.from().portId()
                    : connection.to().portId();
            PlacedDungeonRoom parentRoom = roomsById.get(rootEdge.sourceNodeId());
            String side = parentRoom == null
                    ? "none"
                    : parentRoom.ports()
                    .stream()
                    .filter(port -> port.id().equals(parentPort))
                    .findFirst()
                    .map(port -> port.facing().getSerializedName())
                    .orElse("none");
            DungeonNodeAnalysis rootAnalysis = analysis.requireNode(root);
            if (rootAnalysis.sectorIndex().isPresent()) {
                radialBySector.put(
                        String.valueOf(rootAnalysis.sectorIndex().getAsInt()),
                        side
                );
            }
        }

        Map<String, String> result = new LinkedHashMap<>();
        for (PlacedDungeonRoom room : plan.rooms()) {
            DungeonNodeAnalysis nodeAnalysis = analysis.requireNode(room.id());
            if (nodeAnalysis.sectorIndex().isPresent()) {
                result.put(
                        room.id(),
                        radialBySector.getOrDefault(
                                String.valueOf(nodeAnalysis.sectorIndex().getAsInt()),
                                "none"
                        )
                );
            }
        }
        return Map.copyOf(result);
    }

    private static List<String> sectorRoots(
            DungeonGraph graph,
            DungeonGraphAnalysis analysis
    ) {
        return graph.treeEdges()
                .stream()
                .filter(edge -> isSectorRoot(
                        analysis,
                        edge.sourceNodeId(),
                        edge.targetNodeId()
                ))
                .map(DungeonGraphEdge::targetNodeId)
                .toList();
    }

    private static boolean isSectorRoot(
            DungeonGraphAnalysis analysis,
            String parentId,
            String childId
    ) {
        DungeonNodeAnalysis child = analysis.requireNode(childId);
        if (child.sectorIndex().isEmpty()
                || child.sectorIndex().getAsInt() < 0) {
            return false;
        }
        DungeonNodeAnalysis parent = analysis.requireNode(parentId);
        return parent.sectorIndex().isEmpty()
                || parent.sectorIndex().getAsInt() < 0
                || parent.sectorIndex().getAsInt() != child.sectorIndex().getAsInt();
    }

    public static long deriveGenerationSeed(
            long worldSeed,
            ChunkPos chunkPos,
            BlockPos layoutOrigin
    ) {
        long value = worldSeed;
        value ^= (long) chunkPos.x() * 0x632BE59BD9B4E019L;
        value ^= (long) chunkPos.z() * 0x9E3779B97F4A7C15L;
        value ^= layoutOrigin.asLong();
        value ^= 0x4F42444C47524150L;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }

    private static long deriveLayoutAttemptSalt(
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

}
