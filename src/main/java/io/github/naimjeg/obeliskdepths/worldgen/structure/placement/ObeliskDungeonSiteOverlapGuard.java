package io.github.naimjeg.obeliskdepths.worldgen.structure.placement;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSitePlacement;
import io.github.naimjeg.obeliskdepths.worldgen.structure.ObeliskDungeonStructure;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraph;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphGenerator;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonGraphEmbeddingPlanner;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutGenerationProfile;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutPlan;
import io.github.naimjeg.obeliskdepths.worldgen.structure.piece.DungeonPiecePlan;
import io.github.naimjeg.obeliskdepths.worldgen.structure.piece.DungeonPiecePlanCompiler;
import java.util.Optional;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class ObeliskDungeonSiteOverlapGuard {
    private ObeliskDungeonSiteOverlapGuard() {
    }

    public static Optional<Rejection> findRejection(
            long worldSeed,
            ChunkPos currentChunk,
            BoundingBox currentBounds
    ) {
        int regionX = Math.floorDiv(currentChunk.x(), ObeliskDungeonPlacementSettings.SPACING);
        int regionZ = Math.floorDiv(currentChunk.z(), ObeliskDungeonPlacementSettings.SPACING);
        int searchRadius = overlapSearchRadius(currentBounds);
        long currentPriority = priority(worldSeed, currentChunk);

        for (int rx = regionX - searchRadius; rx <= regionX + searchRadius; rx++) {
            for (int rz = regionZ - searchRadius; rz <= regionZ + searchRadius; rz++) {
                ChunkPos neighborChunk = candidateChunk(worldSeed, rx, rz);

                if (neighborChunk.equals(currentChunk)) {
                    continue;
                }

                BoundingBox neighborBounds = plannedBoundsForChunk(worldSeed, neighborChunk);

                if (!intersects(currentBounds, neighborBounds)) {
                    continue;
                }

                long neighborPriority = priority(worldSeed, neighborChunk);

                if (neighborPriority < currentPriority
                        || (neighborPriority == currentPriority
                        && compareChunk(neighborChunk, currentChunk) < 0)) {
                    Rejection rejection = new Rejection(
                            neighborChunk,
                            neighborBounds
                    );
                    ObeliskDepths.LOGGER.debug(
                            "[OD structure] rejected overlapping dungeon candidate chunk={} winnerChunk={} currentBounds={} winnerBounds={}",
                            currentChunk,
                            neighborChunk,
                            currentBounds,
                            neighborBounds
                    );
                    return Optional.of(rejection);
                }
            }
        }

        return Optional.empty();
    }

    public static BoundingBox plannedBoundsForChunk(
            long worldSeed,
            ChunkPos chunkPos
    ) {
        BlockPos layoutOrigin = new BlockPos(
                chunkPos.getMiddleBlockX(),
                DungeonSitePlacement.PROTOTYPE_Y,
                chunkPos.getMiddleBlockZ()
        );
        long generationSeed = ObeliskDungeonStructure.deriveGenerationSeed(
                worldSeed,
                chunkPos,
                layoutOrigin
        );
        DungeonGraph graph = DungeonGraphGenerator.generate(
                generationSeed,
                DungeonLayoutGenerationProfile.SMALL_TEST
        );
        DungeonLayoutPlan layout = DungeonGraphEmbeddingPlanner.embed(graph, layoutOrigin);
        DungeonPiecePlan terrainPlan = DungeonPiecePlanCompiler.compile(layoutOrigin, layout);

        return terrainPlan.siteBounds();
    }

    public static ChunkPos candidateChunk(
            long seed,
            int regionX,
            int regionZ
    ) {
        long mixedSeed = seed;
        mixedSeed ^= (long) regionX * 341873128712L;
        mixedSeed ^= (long) regionZ * 132897987541L;
        mixedSeed ^= ObeliskDungeonPlacementSettings.SALT;

        Random random = new Random(mixedSeed);
        int spread = ObeliskDungeonPlacementSettings.SPACING
                - ObeliskDungeonPlacementSettings.SEPARATION;

        return new ChunkPos(
                regionX * ObeliskDungeonPlacementSettings.SPACING + random.nextInt(spread),
                regionZ * ObeliskDungeonPlacementSettings.SPACING + random.nextInt(spread)
        );
    }

    private static int overlapSearchRadius(BoundingBox currentBounds) {
        int footprintBlocks = Math.max(
                currentBounds.maxX() - currentBounds.minX() + 1,
                currentBounds.maxZ() - currentBounds.minZ() + 1
        );
        int footprintChunks = Math.max(1, (footprintBlocks + 15) / 16);
        int spacing = Math.max(1, ObeliskDungeonPlacementSettings.SPACING);

        return Math.max(2, footprintChunks / spacing + 2);
    }

    private static long priority(
            long worldSeed,
            ChunkPos chunkPos
    ) {
        long value = worldSeed;
        value ^= (long) chunkPos.x() * 341873128712L;
        value ^= (long) chunkPos.z() * 132897987541L;
        value ^= ObeliskDungeonPlacementSettings.SALT;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }

    private static int compareChunk(
            ChunkPos first,
            ChunkPos second
    ) {
        int xCompare = Integer.compare(first.x(), second.x());

        if (xCompare != 0) {
            return xCompare;
        }

        return Integer.compare(first.z(), second.z());
    }

    private static boolean intersects(
            BoundingBox first,
            BoundingBox second
    ) {
        return first.minX() <= second.maxX()
                && first.maxX() >= second.minX()
                && first.minY() <= second.maxY()
                && first.maxY() >= second.minY()
                && first.minZ() <= second.maxZ()
                && first.maxZ() >= second.minZ();
    }

    public record Rejection(
            ChunkPos winnerChunk,
            BoundingBox winnerBounds
    ) {
    }
}
