package io.github.naimjeg.obeliskdepths.worldgen.structure.tree;

import com.mojang.serialization.MapCodec;
import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.registry.ModWorldgen;
import io.github.naimjeg.obeliskdepths.worldgen.debug.DungeonWorldgenTrace;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

public final class GreatSwampHourglassTreeStructure extends Structure {
    public static final MapCodec<GreatSwampHourglassTreeStructure> CODEC =
            Structure.simpleCodec(GreatSwampHourglassTreeStructure::new);

    public static final long TREE_SEED_SALT = 0x4753484754524545L;
    public static final int TREE_VERTICAL_SPAN = GreatSwampTreeTerrainPlacement.TREE_VERTICAL_SPAN;
    public static final ResourceKey<Biome> GREAT_SWAMP_BIOME =
            ResourceKey.create(
                    Registries.BIOME,
                    Identifier.fromNamespaceAndPath(ObeliskDepths.MOD_ID, "great_swamp")
            );

    public GreatSwampHourglassTreeStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        ChunkPos chunkPos = context.chunkPos();
        long treeSeed = deriveTreeSeed(context.seed(), chunkPos);
        int centerX = chunkPos.getMiddleBlockX() + intRange(treeSeed, 1, 17) - 8;
        int centerZ = chunkPos.getMiddleBlockZ() + intRange(treeSeed, 2, 17) - 8;
        int surfaceY = context.chunkGenerator().getFirstOccupiedHeight(
                centerX,
                centerZ,
                Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(),
                context.randomState()
        );
        int minY = terrainRelativeMinY(
                surfaceY,
                context.heightAccessor().getMinY(),
                context.heightAccessor().getMaxY()
        );
        int maxY = minY + TREE_VERTICAL_SPAN;

        if (maxY - minY < 96
                || minY < context.heightAccessor().getMinY()
                || maxY > context.heightAccessor().getMaxY()) {
            ObeliskDepths.LOGGER.warn(
                    "[OD structure] rejected hourglass tree candidate chunk={} reason=height-too-small surfaceY={} minY={} maxY={} dimensionMinY={} dimensionMaxY={}",
                    chunkPos,
                    surfaceY,
                    minY,
                    maxY,
                    context.heightAccessor().getMinY(),
                    context.heightAccessor().getMaxY()
            );
            return Optional.empty();
        }

        int maxRadius = GreatSwampHourglassTreeSite.MIN_RADIUS
                + intRange(
                treeSeed,
                3,
                GreatSwampHourglassTreeSite.MAX_RADIUS - GreatSwampHourglassTreeSite.MIN_RADIUS + 1
        );
        GreatSwampHourglassTreeSite site = new GreatSwampHourglassTreeSite(
                centerX,
                centerZ,
                minY,
                maxY,
                maxRadius,
                treeSeed
        );
        GreatSwampHourglassTreeField field = new GreatSwampHourglassTreeField(site);
        BiomeValidation biomeValidation = validateBiomes(context, site);

        if (!biomeValidation.valid()) {
            ObeliskDepths.LOGGER.warn(
                    "[OD structure] rejected hourglass tree biome candidate chunk={} center={},{} seed={} validation={} samples={}",
                    chunkPos,
                    centerX,
                    centerZ,
                    treeSeed,
                    biomeValidation.reason(),
                    biomeValidation.sampleSummary()
            );
            return Optional.empty();
        }

        if (!field.validatePlan()) {
            ObeliskDepths.LOGGER.warn(
                    "[OD structure] rejected invalid hourglass tree plan chunk={} center={},{} seed={} bounds={}",
                    chunkPos,
                    centerX,
                    centerZ,
                    treeSeed,
                    field.completeBounds()
            );
            return Optional.empty();
        }

        int pieceCount = 1
                + field.lowerRootCount()
                + field.upperRootCount()
                + field.branchCount();
        BoundingBox bounds = field.completeBounds();

        DungeonWorldgenTrace.Context traceContext = DungeonWorldgenTrace.context(chunkPos);
        DungeonWorldgenTrace.debug(
                traceContext,
                () -> "stage=hourglass-tree-start"
                        + " startChunk=" + chunkPos.x() + "," + chunkPos.z()
                        + " center=" + centerX + "," + centerZ
                        + " treeSeed=" + treeSeed
                        + " surfaceY=" + surfaceY
                        + " minY=" + minY
                        + " maxY=" + maxY
                        + " maxRadius=" + maxRadius
                        + " biomeValidation=" + biomeValidation.reason()
                        + " pieceCount=" + pieceCount
                        + " bounds=" + bounds
        );

        ObeliskDepths.LOGGER.debug(
                "[OD structure] accepted hourglass tree startChunk={} center={},{} treeSeed={} surfaceY={} minY={} maxY={} maxRadius={} biomeValidation={} pieceCount={} bounds={}",
                chunkPos,
                centerX,
                centerZ,
                treeSeed,
                surfaceY,
                minY,
                maxY,
                maxRadius,
                biomeValidation.reason(),
                pieceCount,
                bounds
        );

        BlockPos startPos = new BlockPos(centerX, site.waistY(), centerZ);
        return Optional.of(new GenerationStub(
                startPos,
                builder -> {
                    builder.addPiece(new GreatSwampHourglassTreePiece(
                            GreatSwampHourglassTreePiece.Role.TRUNK,
                            -1,
                            site,
                            field.trunkBounds()
                    ));

                    for (int i = 0; i < field.lowerRootCount(); i++) {
                        builder.addPiece(new GreatSwampHourglassTreePiece(
                                GreatSwampHourglassTreePiece.Role.LOWER_ROOT,
                                i,
                                site,
                                field.lowerRootBounds(i)
                        ));
                    }

                    for (int i = 0; i < field.upperRootCount(); i++) {
                        builder.addPiece(new GreatSwampHourglassTreePiece(
                                GreatSwampHourglassTreePiece.Role.UPPER_ROOT,
                                i,
                                site,
                                field.upperRootBounds(i)
                        ));
                    }

                    for (int i = 0; i < field.branchCount(); i++) {
                        builder.addPiece(new GreatSwampHourglassTreePiece(
                                GreatSwampHourglassTreePiece.Role.BRANCH_CANOPY,
                                i,
                                site,
                                field.branchCanopyBounds(i)
                        ));
                    }
                }
        ));
    }

    public static int terrainRelativeMinY(
            int surfaceY,
            int dimensionMinY,
            int dimensionMaxY
    ) {
        return GreatSwampTreeTerrainPlacement.terrainRelativeMinY(surfaceY, dimensionMinY, dimensionMaxY);
    }

    @Override
    public StructureType<?> type() {
        return ModWorldgen.GREAT_SWAMP_HOURGLASS_TREE.get();
    }

    public static long deriveTreeSeed(long worldSeed, ChunkPos chunkPos) {
        long value = worldSeed;
        value ^= (long) chunkPos.x() * 0x632BE59BD9B4E019L;
        value ^= (long) chunkPos.z() * 0x9E3779B97F4A7C15L;
        value ^= TREE_SEED_SALT;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }

    private static BiomeValidation validateBiomes(
            GenerationContext context,
            GreatSwampHourglassTreeSite site
    ) {
        int radius = Math.max(32, site.maxRadius() / 2);
        int y = site.waistY();
        int[][] samples = {
                {0, 0},
                {0, -radius},
                {0, radius},
                {radius, 0},
                {-radius, 0},
                {radius, -radius},
                {-radius, -radius},
                {radius, radius},
                {-radius, radius}
        };

        StringBuilder summary = new StringBuilder();
        for (int i = 0; i < samples.length; i++) {
            int x = site.centerX() + samples[i][0];
            int z = site.centerZ() + samples[i][1];
            Holder<Biome> biome = context.chunkGenerator()
                    .getBiomeSource()
                    .getNoiseBiome(
                            QuartPos.fromBlock(x),
                            QuartPos.fromBlock(y),
                            QuartPos.fromBlock(z),
                            context.randomState().sampler()
                    );
            String biomeId = biome.unwrapKey()
                    .map(key -> key.identifier().toString())
                    .orElse("<direct/unregistered>");
            if (i > 0) {
                summary.append(';');
            }
            summary.append(i).append('=').append(biomeId);

            boolean exactGreatSwamp = biome.unwrapKey()
                    .map(GREAT_SWAMP_BIOME::equals)
                    .orElse(false);
            boolean tagAllowed = context.validBiome().test(biome);
            if (!exactGreatSwamp) {
                return new BiomeValidation(false, "not_great_swamp@" + i, summary.toString());
            }
            if (!tagAllowed) {
                return new BiomeValidation(false, "tag_rejected@" + i, summary.toString());
            }
        }

        return new BiomeValidation(true, "great_swamp_9_of_9", summary.toString());
    }

    private static int intRange(long seed, int salt, int bound) {
        long value = seed ^ (long) salt * 0x9E3779B97F4A7C15L;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return (int) Math.floor((double) (value >>> 11) * 0x1.0p-53D * bound);
    }

    private record BiomeValidation(
            boolean valid,
            String reason,
            String sampleSummary
    ) {
    }
}
