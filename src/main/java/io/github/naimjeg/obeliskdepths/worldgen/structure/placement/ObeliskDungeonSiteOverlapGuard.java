package io.github.naimjeg.obeliskdepths.worldgen.structure.placement;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import java.util.Optional;
import java.util.Random;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class ObeliskDungeonSiteOverlapGuard {
    private ObeliskDungeonSiteOverlapGuard() {
    }

    public static Optional<Rejection> findRejection(
            long worldSeed,
            ChunkPos currentChunk,
            BoundingBox currentBounds
    ) {
        int radiusBlocks = conservativeRadiusFor(currentBounds);
        int minimumCenterSeparationBlocks = ObeliskDungeonPlacementSettings.SEPARATION * 16;

        /*
         * This is a diagnostic only.
         *
         * The structure set's minecraft:random_spread placement is the
         * authoritative start-position filter. Runtime allocation discovers
         * already-generated starts; worldgen must not reject every valid start
         * just because the temporary debug layout's conservative bounds exceed
         * the configured separation. Physical overlap prevention for final
         * authored layouts belongs in structure placement/configuration, not in
         * a runtime-style neighboring-layout prediction pass.
         */
        ObeliskDepths.LOGGER.debug(
                "[OD structure] cheap overlap diagnostic chunk={} radiusBlocks={} minimumCenterSeparationBlocks={} placementDecision=vanilla_random_spread",
                currentChunk,
                radiusBlocks,
                minimumCenterSeparationBlocks
        );
        return Optional.empty();
    }

    public static ChunkPos candidateChunk(
            long seed,
            int regionX,
            int regionZ
    ) {
        /*
         * Match minecraft:random_spread placement.
         *
         * Do not use java.util.Random or a manually mixed seed here. The candidate
         * must be identical to the chunk selected by vanilla StructurePlacement.
         *
         * This implementation assumes the structure-set JSON uses:
         *
         *     "spread_type": "linear"
         */
        WorldgenRandom random =
                new WorldgenRandom(new LegacyRandomSource(0L));

        random.setLargeFeatureWithSalt(
                seed,
                regionX,
                regionZ,
                ObeliskDungeonPlacementSettings.SALT
        );

        int spread =
                ObeliskDungeonPlacementSettings.SPACING
                        - ObeliskDungeonPlacementSettings.SEPARATION;

        int offsetX = random.nextInt(spread);
        int offsetZ = random.nextInt(spread);

        return new ChunkPos(
                regionX * ObeliskDungeonPlacementSettings.SPACING + offsetX,
                regionZ * ObeliskDungeonPlacementSettings.SPACING + offsetZ
        );
    }

    private static int conservativeRadiusFor(BoundingBox currentBounds) {
        int footprintBlocks = Math.max(
                currentBounds.maxX() - currentBounds.minX() + 1,
                currentBounds.maxZ() - currentBounds.minZ() + 1
        );
        return Math.max(
                ObeliskDungeonPlacementSettings.MAX_SITE_RADIUS_BLOCKS,
                (footprintBlocks + 1) / 2
        );
    }

    public record Rejection(
            ChunkPos winnerChunk,
            BoundingBox winnerBounds
    ) {
    }
}
