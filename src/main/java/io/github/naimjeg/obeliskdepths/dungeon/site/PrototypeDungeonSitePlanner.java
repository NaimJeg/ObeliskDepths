package io.github.naimjeg.obeliskdepths.dungeon.site;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import io.github.naimjeg.obeliskdepths.worldgen.structure.placement.ObeliskDungeonPlacementSettings;


import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;

public final class PrototypeDungeonSitePlanner {
    private static final int SEARCH_REGIONS = 8;

    /*
     * Prototype-only deterministic site planner.
     *
     * This class does not read vanilla StructurePlacement.
     * This class does not read StructureStart.
     * This class does not prove that terrain or pieces exist.
     *
     * Its output may be useful for early debug previews, but it must not be used
     * as the authoritative source for runtime DungeonInstance reservation.
     *
     * Authoritative runtime site metadata must come from:
     *
     *     StructureStart -> ObeliskDungeonPiece -> GeneratedDungeonSiteProjector
     */
    private PrototypeDungeonSitePlanner() {
    }

    public static Optional<WorldgenDungeonSiteCandidate> findNearestCandidate(
            ServerLevel level,
            BlockPos origin,
            Predicate<DungeonSiteKey> canUseSite
    ) {
        int originChunkX = SectionPos.blockToSectionCoord(origin.getX());
        int originChunkZ = SectionPos.blockToSectionCoord(origin.getZ());

        int originRegionX = Math.floorDiv(originChunkX, ObeliskDungeonPlacementSettings.SPACING);
        int originRegionZ = Math.floorDiv(originChunkZ, ObeliskDungeonPlacementSettings.SPACING);

        WorldgenDungeonSiteCandidate best = null;
        double bestDistanceSqr = Double.MAX_VALUE;

        for (int rx = originRegionX - SEARCH_REGIONS; rx <= originRegionX + SEARCH_REGIONS; rx++) {
            for (int rz = originRegionZ - SEARCH_REGIONS; rz <= originRegionZ + SEARCH_REGIONS; rz++) {
                ChunkPos startChunk = candidateChunk(level.getSeed(), rx, rz);
                DungeonSiteKey key = DungeonSiteKey.fromStartChunk(startChunk);

                if (!canUseSite.test(key)) {
                    continue;
                }

                BlockPos startPos = new BlockPos(
                        startChunk.getMiddleBlockX(),
                        DungeonSitePlacement.PREVIEW_Y,
                        startChunk.getMiddleBlockZ()
                );

                long distanceSqr = horizontalDistanceSqr(startPos, origin);

                if (distanceSqr < bestDistanceSqr) {
                    bestDistanceSqr = distanceSqr;
                    best = new WorldgenDungeonSiteCandidate(
                            key,
                            startPos,
                            saturatedInt(distanceSqr)
                    );
                }
            }
        }

        return Optional.ofNullable(best);
    }

    private static ChunkPos candidateChunk(
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

        int offsetX = random.nextInt(spread);
        int offsetZ = random.nextInt(spread);

        return new ChunkPos(
                regionX * ObeliskDungeonPlacementSettings.SPACING + offsetX,
                regionZ * ObeliskDungeonPlacementSettings.SPACING + offsetZ
        );
    }

    public static long horizontalDistanceSqr(
            BlockPos first,
            BlockPos second
    ) {
        long dx = (long) first.getX() - second.getX();
        long dz = (long) first.getZ() - second.getZ();
        return dx * dx + dz * dz;
    }

    private static int saturatedInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }
}
