package io.github.naimjeg.obeliskdepths.worldgen.structure;

import com.mojang.serialization.MapCodec;
import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSitePlacement;
import io.github.naimjeg.obeliskdepths.registry.ModWorldgen;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraph;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphGenerator;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphValidator;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonGraphEmbeddingPlanner;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutConstants;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutGenerationProfile;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutNode;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutPlan;
import io.github.naimjeg.obeliskdepths.worldgen.structure.placement.ObeliskDungeonSiteOverlapGuard;
import io.github.naimjeg.obeliskdepths.worldgen.structure.piece.DungeonPiecePlan;
import io.github.naimjeg.obeliskdepths.worldgen.structure.piece.DungeonPiecePlanCompiler;
import io.github.naimjeg.obeliskdepths.worldgen.structure.piece.DungeonPiecePlanEmitter;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
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
        BlockPos layoutOrigin = new BlockPos(
                chunkPos.getMiddleBlockX(),
                DungeonSitePlacement.PROTOTYPE_Y,
                chunkPos.getMiddleBlockZ()
        );

        Holder<Biome> actualBiome = context.chunkGenerator()
                .getBiomeSource()
                .getNoiseBiome(
                        QuartPos.fromBlock(layoutOrigin.getX()),
                        QuartPos.fromBlock(layoutOrigin.getY()),
                        QuartPos.fromBlock(layoutOrigin.getZ()),
                        context.randomState().sampler()
                );

        String actualBiomeId = actualBiome.unwrapKey()
                .map(key -> key.identifier().toString())
                .orElse("<direct/unregistered>");

        boolean valid = context.validBiome().test(actualBiome);

        if (!valid) {
            ObeliskDepths.LOGGER.warn(
                    "[OD structure] invalid biome candidate chunk={} anchor={} actualBiome={}",
                    chunkPos,
                    layoutOrigin,
                    actualBiomeId
            );
        }

        long generationSeed = deriveGenerationSeed(context.seed(), chunkPos, layoutOrigin);
        DungeonGraph graph = DungeonGraphGenerator.generate(
                generationSeed,
                DungeonLayoutGenerationProfile.SMALL_TEST
        );
        DungeonGraphValidator.validate(graph);

        DungeonLayoutPlan layout = DungeonGraphEmbeddingPlanner.embed(graph, layoutOrigin);
        DungeonPiecePlan piecePlan = DungeonPiecePlanCompiler.compile(layoutOrigin, layout);
        BoundingBox plannedBounds = piecePlan.siteBounds();
        BlockPos startRoomAnchor = startRoomAnchor(layout, layoutOrigin);

        Optional<ObeliskDungeonSiteOverlapGuard.Rejection> rejection =
                ObeliskDungeonSiteOverlapGuard.findRejection(
                        context.seed(),
                        chunkPos,
                        plannedBounds
                );

        if (rejection.isPresent()) {
            return Optional.empty();
        }

        ObeliskDepths.LOGGER.debug(
                "[OD structure] accepted dungeon candidate chunk={} layoutOrigin={} startRoomAnchor={} seed={} bounds={} graphNodes={} graphEdges={} rooms={} corridors={} footprintBlocks={}x{} footprintCells={}x{}",
                chunkPos,
                layoutOrigin,
                startRoomAnchor,
                generationSeed,
                plannedBounds,
                graph.nodes().size(),
                graph.edges().size(),
                layout.nodes().size(),
                piecePlan.corridorCount(),
                plannedBounds.getXSpan(),
                plannedBounds.getZSpan(),
                (plannedBounds.getXSpan() + DungeonLayoutConstants.CELL_SIZE_X - 1) / DungeonLayoutConstants.CELL_SIZE_X,
                (plannedBounds.getZSpan() + DungeonLayoutConstants.CELL_SIZE_Z - 1) / DungeonLayoutConstants.CELL_SIZE_Z
        );

        return Optional.of(new GenerationStub(
                startRoomAnchor,
                builder -> DungeonPiecePlanEmitter.emit(builder, piecePlan)
        ));
    }

    @Override
    public StructureType<?> type() {
        return ModWorldgen.OBELISK_DUNGEON.get();
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

    private static BlockPos startRoomAnchor(
            DungeonLayoutPlan layout,
            BlockPos layoutOrigin
    ) {
        DungeonLayoutNode start = layout.nodes()
                .stream()
                .filter(node -> node.type() == io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType.START)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Embedded layout missing START room"));

        return start.blockAnchor(layoutOrigin);
    }
}
