package io.github.naimjeg.obeliskdepths.dungeon.site;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.site.reader.DungeonStructureLocator;
import io.github.naimjeg.obeliskdepths.dungeon.site.reader.GeneratedDungeonSiteReader;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import io.github.naimjeg.obeliskdepths.dungeon.territory.DungeonBounds;
import io.github.naimjeg.obeliskdepths.worldgen.structure.placement.ObeliskDungeonPlacementSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Finds an already-generated dungeon site, or provisions one by asking
 * vanilla world generation to generate a valid random-spread candidate.
 *
 * This class never manually places dungeon blocks or fabricates DungeonSite
 * metadata. GeneratedDungeonSiteReader remains the authoritative metadata
 * source.
 */
public final class WorldgenDungeonSiteProvisioner {
    private static final int MAX_GENERATION_ATTEMPTS = 4;

    /*
     * Generate the start chunk plus one neighboring chunk in every direction
     * before metadata is available. Once a generated site is accepted, the
     * complete bounded site area is generated below.
     */
    private static final int GENERATION_RADIUS_CHUNKS = 1;

    private static final int SITE_BOUNDS_SAFETY_MARGIN_CHUNKS = 1;
    private static final int MAX_SITE_BOUNDS_CHUNKS = 512;

    private static final long RANDOM_SELECTION_SALT =
            0x6A09E667F3BCC909L;

    private WorldgenDungeonSiteProvisioner() {
    }

    public static Optional<ResolvedDungeonSite> findOrGenerateReservableSite(
            ServerLevel level,
            BlockPos origin,
            DungeonManagerSavedData data
    ) {
        /*
         * Preserve the existing fast path by reusing a valid persisted site
         * instead of selecting a new structure candidate.
         */
        Optional<ResolvedDungeonSite> existing =
                WorldgenDungeonSiteLocator.findNearestReservableSite(
                        level,
                        origin,
                        data
                );

        if (existing.isPresent()) {
            return Optional.of(
                    prepareExistingSiteForEntry(
                            existing.get(),
                            bounds -> generateSiteBounds(level, bounds)
                    )
            );
        }

        List<DungeonSiteKey> candidates = new ArrayList<>(
                DungeonStructureLocator.findCandidateKeys(
                        level,
                        origin,
                        ObeliskDungeonPlacementSettings.MAX_LOOKUP_CANDIDATES
                )
        );

        /*
         * Remove reserved, previously reached, and otherwise ineligible keys
         * before selecting a generation target.
         */
        candidates.removeIf(key -> !canReserve(data, key));

        if (candidates.isEmpty()) {
            ObeliskDepths.LOGGER.warn(
                    "[OD provision] no eligible placement candidates origin={}",
                    origin
            );
            return Optional.empty();
        }

        /*
         * Game time is included so a later retry does not always select exactly
         * the same failed candidates.
         */
        long randomSeed =
                level.getSeed()
                        ^ origin.asLong()
                        ^ level.getGameTime()
                        ^ RANDOM_SELECTION_SALT;

        RandomSource random = RandomSource.create(randomSeed);

        int attemptLimit = Math.min(
                MAX_GENERATION_ATTEMPTS,
                candidates.size()
        );

        for (int attempt = 1; attempt <= attemptLimit; attempt++) {
            /*
             * Removing the selected element ensures that one request never
             * attempts the same candidate twice.
             */
            int selectedIndex = random.nextInt(candidates.size());
            DungeonSiteKey key = candidates.remove(selectedIndex);
            ChunkPos startChunk = key.toChunkPos();

            /*
             * Recheck immediately before generation. Another portal request may
             * have reserved the candidate earlier in the same server session.
             */
            if (!canReserve(data, key)) {
                ObeliskDepths.LOGGER.debug(
                        "[OD provision] skipped candidate attempt={}/{} key={} reason={}",
                        attempt,
                        attemptLimit,
                        key,
                        data.sites().generatedReservationRejectionReason(key)
                );
                continue;
            }

            long generationStartNanos = System.nanoTime();

            try {
                ObeliskDepths.LOGGER.info(
                        "[OD provision] generating candidate attempt={}/{} key={} chunk={}",
                        attempt,
                        attemptLimit,
                        key,
                        startChunk
                );

                generateCandidateArea(level, startChunk);

                /*
                 * Do not fabricate a DungeonSite from the selected coordinates.
                 * Read it back from the generated StructureStart.
                 */
                Optional<DungeonSite> generatedSite =
                        GeneratedDungeonSiteReader.readGeneratedSite(
                                level,
                                key
                        );

                if (generatedSite.isEmpty()) {
                    ObeliskDepths.LOGGER.warn(
                            "[OD provision] candidate generated without dungeon structure "
                                    + "attempt={}/{} key={} chunk={} elapsedMicros={}",
                            attempt,
                            attemptLimit,
                            key,
                            startChunk,
                            elapsedMicros(generationStartNanos)
                    );
                    continue;
                }

                DungeonSite site = generatedSite.get();

                if (!GeneratedDungeonSiteReader.isValidGeneratedSite(site)) {
                    ObeliskDepths.LOGGER.warn(
                            "[OD provision] generated site has invalid metadata "
                                    + "attempt={}/{} key={} rooms={} start={} elapsedMicros={}",
                            attempt,
                            attemptLimit,
                            key,
                            site.rooms().size(),
                            site.startPos(),
                            elapsedMicros(generationStartNanos)
                    );
                    continue;
                }

                /*
                 * Recheck after generation and before returning. The caller
                 * performs the actual reservation immediately afterward.
                 */
                if (!canReserve(data, key)) {
                    ObeliskDepths.LOGGER.warn(
                            "[OD provision] generated candidate became unavailable "
                                    + "key={} reason={}",
                            key,
                            data.sites().generatedReservationRejectionReason(key)
                    );
                    continue;
                }

                ObeliskDepths.LOGGER.info(
                        "[OD provision] generated candidate accepted "
                                + "key={} chunk={} rooms={} start={} elapsedMicros={}",
                        key,
                        startChunk,
                        site.rooms().size(),
                        site.startPos(),
                        elapsedMicros(generationStartNanos)
                );

                ResolvedDungeonSite resolved = new ResolvedDungeonSite(
                        site,
                        DungeonSiteProjectionSource.GENERATED_STRUCTURE_START
                );

                return Optional.of(
                        prepareGeneratedSiteForEntry(
                                resolved,
                                bounds -> generateSiteBounds(level, bounds)
                        )
                );
            } catch (RuntimeException exception) {
                ObeliskDepths.LOGGER.error(
                        "[OD provision] candidate generation failed "
                                + "attempt={}/{} key={} chunk={} elapsedMicros={}",
                        attempt,
                        attemptLimit,
                        key,
                        startChunk,
                        elapsedMicros(generationStartNanos),
                        exception
                );
            }
        }

        ObeliskDepths.LOGGER.warn(
                "[OD provision] generation attempts exhausted origin={} "
                        + "attempts={} remainingCandidates={}",
                origin,
                attemptLimit,
                candidates.size()
        );

        return Optional.empty();
    }

    private static void generateCandidateArea(
            ServerLevel level,
            ChunkPos center
    ) {
        /*
         * Generate the authoritative structure-start chunk first.
         */
        generateChunk(level, center.x(), center.z());

        /*
         * Then generate the surrounding chunks required for initial entry and
         * nearby structure-piece placement.
         */
        for (int dx = -GENERATION_RADIUS_CHUNKS;
             dx <= GENERATION_RADIUS_CHUNKS;
             dx++) {
            for (int dz = -GENERATION_RADIUS_CHUNKS;
                 dz <= GENERATION_RADIUS_CHUNKS;
                 dz++) {

                if (dx == 0 && dz == 0) {
                    continue;
                }

                generateChunk(
                        level,
                        center.x() + dx,
                        center.z() + dz
                );
            }
        }
    }

    private static void generateChunk(
            ServerLevel level,
            int chunkX,
            int chunkZ
    ) {
        level.getChunk(
                chunkX,
                chunkZ,
                ChunkStatus.FULL,
                true
        );
    }

    private static void generateSiteBounds(
            ServerLevel level,
            DungeonBounds bounds
    ) {
        ChunkGenerationPlan plan = planChunksForBounds(
                bounds,
                SITE_BOUNDS_SAFETY_MARGIN_CHUNKS,
                MAX_SITE_BOUNDS_CHUNKS
        );

        for (SiteChunk chunk : plan.chunks()) {
            generateChunk(level, chunk.x(), chunk.z());
        }

        ObeliskDepths.LOGGER.info(
                "[OD provision] generated site bounds chunks count={} chunkBounds={},{}..{},{} blockBounds={} margin={}",
                plan.chunks().size(),
                plan.minChunkX(),
                plan.minChunkZ(),
                plan.maxChunkX(),
                plan.maxChunkZ(),
                bounds,
                SITE_BOUNDS_SAFETY_MARGIN_CHUNKS
        );
    }

    static ChunkGenerationPlan planChunksForBounds(
            DungeonBounds bounds,
            int safetyMarginChunks,
            int maximumChunks
    ) {
        if (bounds.maxX() < bounds.minX()
                || bounds.maxZ() < bounds.minZ()) {
            throw new IllegalArgumentException(
                    "Dungeon site bounds are inverted: " + bounds
            );
        }
        if (safetyMarginChunks < 0 || safetyMarginChunks > 1) {
            throw new IllegalArgumentException(
                    "Dungeon site chunk safety margin must be 0 or 1: "
                            + safetyMarginChunks
            );
        }

        int minChunkX = Math.floorDiv(bounds.minX(), 16) - safetyMarginChunks;
        int maxChunkX = Math.floorDiv(bounds.maxX(), 16) + safetyMarginChunks;
        int minChunkZ = Math.floorDiv(bounds.minZ(), 16) - safetyMarginChunks;
        int maxChunkZ = Math.floorDiv(bounds.maxZ(), 16) + safetyMarginChunks;
        int chunkCount = Math.multiplyExact(
                maxChunkX - minChunkX + 1,
                maxChunkZ - minChunkZ + 1
        );
        if (chunkCount > maximumChunks) {
            throw new IllegalArgumentException(
                    "Dungeon site bounds request too many chunks: count="
                            + chunkCount
                            + " max="
                            + maximumChunks
                            + " bounds="
                            + bounds
                            + " chunkBounds="
                            + minChunkX
                            + ","
                            + minChunkZ
                            + ".."
                            + maxChunkX
                            + ","
                            + maxChunkZ
            );
        }

        List<SiteChunk> chunks = new ArrayList<>(chunkCount);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                chunks.add(new SiteChunk(chunkX, chunkZ));
            }
        }
        return new ChunkGenerationPlan(
                minChunkX,
                minChunkZ,
                maxChunkX,
                maxChunkZ,
                chunks
        );
    }

    private static boolean canReserve(
            DungeonManagerSavedData data,
            DungeonSiteKey key
    ) {
        return "candidate_accepted".equals(
                data.sites().generatedReservationRejectionReason(key)
        );
    }

    private static long elapsedMicros(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000L;
    }

    static ResolvedDungeonSite prepareExistingSiteForEntry(
            ResolvedDungeonSite resolved,
            Consumer<DungeonBounds> boundsGenerator
    ) {
        boundsGenerator.accept(resolved.site().bounds());
        return resolved;
    }

    static ResolvedDungeonSite prepareGeneratedSiteForEntry(
            ResolvedDungeonSite resolved,
            Consumer<DungeonBounds> boundsGenerator
    ) {
        boundsGenerator.accept(resolved.site().bounds());
        return resolved;
    }

    record ChunkGenerationPlan(
            int minChunkX,
            int minChunkZ,
            int maxChunkX,
            int maxChunkZ,
            List<SiteChunk> chunks
    ) {
        ChunkGenerationPlan {
            chunks = List.copyOf(chunks);
        }
    }

    record SiteChunk(int x, int z) {
    }

}
