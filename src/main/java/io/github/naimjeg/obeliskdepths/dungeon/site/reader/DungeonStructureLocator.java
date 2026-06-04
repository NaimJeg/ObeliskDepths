package io.github.naimjeg.obeliskdepths.dungeon.site.reader;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import io.github.naimjeg.obeliskdepths.worldgen.structure.placement.ObeliskDungeonPlacementSettings;
import io.github.naimjeg.obeliskdepths.worldgen.structure.placement.ObeliskDungeonSiteOverlapGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

public final class DungeonStructureLocator {
    private static final int MAX_CANDIDATE_ATTEMPTS =
            ObeliskDungeonPlacementSettings.MAX_LOOKUP_CANDIDATES;

    private DungeonStructureLocator() {
    }

    public static Optional<DungeonSiteKey> findNearestStart(
            ServerLevel level,
            BlockPos origin,
            Predicate<DungeonSiteKey> canUseSite
    ) {
        return findNearestStarts(
                level,
                origin,
                canUseSite
        ).stream().findFirst();
    }

    public static List<DungeonSiteKey> findNearestStarts(
            ServerLevel level,
            BlockPos origin,
            Predicate<DungeonSiteKey> canUseSite
    ) {
        return findNearestStartCandidates(
                level,
                origin,
                key -> canUseSite.test(key) ? "candidate_accepted" : "candidate_predicate_rejected"
        );
    }

    public static List<DungeonSiteKey> findNearestStarts(
            ServerLevel level,
            BlockPos origin,
            Function<DungeonSiteKey, String> eligibilityReason
    ) {
        return findNearestStartCandidates(level, origin, eligibilityReason);
    }

    public static List<DungeonSiteKey> findCandidateKeys(
            ServerLevel level,
            BlockPos origin,
            int requestedLimit
    ) {
        int limit = Math.max(
                1,
                Math.min(requestedLimit, MAX_CANDIDATE_ATTEMPTS)
        );

        int originChunkX = SectionPos.blockToSectionCoord(origin.getX());
        int originChunkZ = SectionPos.blockToSectionCoord(origin.getZ());

        int originRegionX = Math.floorDiv(
                originChunkX,
                ObeliskDungeonPlacementSettings.SPACING
        );
        int originRegionZ = Math.floorDiv(
                originChunkZ,
                ObeliskDungeonPlacementSettings.SPACING
        );

        return nearestCandidateChunks(
                level.getSeed(),
                origin,
                originRegionX,
                originRegionZ,
                limit
        ).stream()
                .map(DungeonSiteKey::fromStartChunk)
                .toList();
    }

    private static List<DungeonSiteKey> findNearestStartCandidates(
            ServerLevel level,
            BlockPos origin,
            Function<DungeonSiteKey, String> eligibilityReason
    ) {
        long startNanos = System.nanoTime();
        int originChunkX = SectionPos.blockToSectionCoord(origin.getX());
        int originChunkZ = SectionPos.blockToSectionCoord(origin.getZ());
        int originRegionX = Math.floorDiv(originChunkX, ObeliskDungeonPlacementSettings.SPACING);
        int originRegionZ = Math.floorDiv(originChunkZ, ObeliskDungeonPlacementSettings.SPACING);
        List<ChunkPos> candidates = nearestCandidateChunks(
                level.getSeed(),
                origin,
                originRegionX,
                originRegionZ,
                MAX_CANDIDATE_ATTEMPTS
        );

        List<DungeonSiteKey> found = new ArrayList<>();
        int attempts = 0;
        int loadedCandidates = 0;
        int persistedCandidates = 0;
        int ungeneratedCandidates = 0;
        int lookupFailures = 0;
        int missingStructureStarts = 0;
        int invalidStructureStarts = 0;
        int predicateRejected = 0;
        int reservedRejected = 0;
        int accepted = 0;
        Throwable firstLookupFailure = null;

        for (int index = 0; index < candidates.size(); index++) {
            ChunkPos chunkPos = candidates.get(index);
            DungeonSiteKey key = new DungeonSiteKey(chunkPos.x(), chunkPos.z());
            attempts++;
            long candidateStartNanos = System.nanoTime();

            DungeonStructureStartReader.LookupResult lookup =
                    DungeonStructureStartReader.lookup(level, key);

            if (lookup.currentlyLoaded()) {
                loadedCandidates++;
            }

            if (lookup.persisted()) {
                persistedCandidates++;
            }

            if (lookup.rejectionReason() == DungeonStructureStartReader.LookupRejectionReason.CANDIDATE_NOT_PERSISTED
                    || lookup.rejectionReason() == DungeonStructureStartReader.LookupRejectionReason.CANDIDATE_NOT_PERSISTED_TO_STRUCTURE_STARTS) {
                ungeneratedCandidates++;
            } else if (lookup.rejectionReason() == DungeonStructureStartReader.LookupRejectionReason.EXISTING_CHUNK_LOOKUP_FAILED
                    || lookup.rejectionReason() == DungeonStructureStartReader.LookupRejectionReason.EXISTING_CHUNK_LOOKUP_UNAVAILABLE
                    || lookup.rejectionReason() == DungeonStructureStartReader.LookupRejectionReason.STRUCTURE_KEY_MISSING) {
                lookupFailures++;
                if (firstLookupFailure == null) {
                    firstLookupFailure = lookup.failure().orElse(null);
                }
            } else if (lookup.rejectionReason() == DungeonStructureStartReader.LookupRejectionReason.STRUCTURE_START_MISSING) {
                missingStructureStarts++;
            } else if (lookup.rejectionReason() == DungeonStructureStartReader.LookupRejectionReason.STRUCTURE_START_INVALID) {
                invalidStructureStarts++;
            }

            if (lookup.start().isEmpty()) {
                logCandidate(
                        index,
                        key,
                        chunkPos,
                        lookup,
                        false,
                        null,
                        (System.nanoTime() - candidateStartNanos) / 1_000L
                );
                continue;
            }

            String reason = eligibilityReason.apply(key);
            boolean predicateAccepted = "candidate_accepted".equals(reason);

            if (!predicateAccepted) {
                if ("candidate_reserved".equals(reason)) {
                    reservedRejected++;
                } else {
                    predicateRejected++;
                }
                logCandidate(
                        index,
                        key,
                        chunkPos,
                        lookup,
                        false,
                        reason,
                        (System.nanoTime() - candidateStartNanos) / 1_000L
                );
                continue;
            }

            accepted++;
            StructureStart start = lookup.start().get();
            ObeliskDepths.LOGGER.debug(
                    "[OD locator] candidate index={} key={} chunk={} loaded={} persisted={} mechanism={} persistedStatus={} returnedStatus={} structureStartFound=true structureStartValid=true predicateAccepted=true reason=candidate_accepted bounds={} structureChunk={} elapsedMicros={}",
                    index,
                    key,
                    chunkPos,
                    lookup.currentlyLoaded(),
                    lookup.persisted(),
                    lookup.mechanism(),
                    lookup.persistedStatus().map(Object::toString).orElse("<none>"),
                    lookup.returnedStatus().map(Object::toString).orElse("<none>"),
                    start.getBoundingBox(),
                    start.getChunkPos(),
                    (System.nanoTime() - candidateStartNanos) / 1_000L
            );
            found.add(key);
        }

        if (firstLookupFailure != null) {
            ObeliskDepths.LOGGER.warn(
                    "[OD locator] first generated-site lookup failure origin={} message={}",
                    origin,
                    firstLookupFailure.getMessage()
            );
        }

        ObeliskDepths.LOGGER.debug(
                "[OD locator] candidate lookup origin={} candidateCount={} attempts={} loadedCandidates={} persistedCandidates={} ungeneratedCandidates={} lookupFailures={} missingStructureStarts={} invalidStructureStarts={} predicateRejected={} reservedRejected={} accepted={} elapsedMicros={}",
                origin,
                candidates.size(),
                attempts,
                loadedCandidates,
                persistedCandidates,
                ungeneratedCandidates,
                lookupFailures,
                missingStructureStarts,
                invalidStructureStarts,
                predicateRejected,
                reservedRejected,
                accepted,
                (System.nanoTime() - startNanos) / 1_000L
        );

        return List.copyOf(found);
    }

    private static void logCandidate(
            int index,
            DungeonSiteKey key,
            ChunkPos chunkPos,
            DungeonStructureStartReader.LookupResult lookup,
            boolean predicateAccepted,
            String overrideReason,
            long elapsedMicros
    ) {
        ObeliskDepths.LOGGER.debug(
                "[OD locator] candidate index={} key={} chunk={} loaded={} persisted={} mechanism={} persistedStatus={} returnedStatus={} structureStartFound={} structureStartValid={} predicateAccepted={} reason={} elapsedMicros={}",
                index,
                key,
                chunkPos,
                lookup.currentlyLoaded(),
                lookup.persisted(),
                lookup.mechanism(),
                lookup.persistedStatus().map(Object::toString).orElse("<none>"),
                lookup.returnedStatus().map(Object::toString).orElse("<none>"),
                lookup.start().isPresent(),
                lookup.start().map(StructureStart::isValid).orElse(false),
                predicateAccepted,
                overrideReason != null ? overrideReason : lookup.rejectionReason().name().toLowerCase(Locale.ROOT),
                elapsedMicros
        );
    }

    private static List<ChunkPos> nearestCandidateChunks(
            long worldSeed,
            BlockPos origin,
            int originRegionX,
            int originRegionZ,
            int limit
    ) {
        int regionRadius = 0;
        List<ChunkPos> candidates = new ArrayList<>();

        while (candidates.size() < limit) {
            for (int dx = -regionRadius; dx <= regionRadius; dx++) {
                for (int dz = -regionRadius; dz <= regionRadius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != regionRadius) {
                        continue;
                    }

                    candidates.add(ObeliskDungeonSiteOverlapGuard.candidateChunk(
                            worldSeed,
                            originRegionX + dx,
                            originRegionZ + dz
                    ));
                }
            }

            regionRadius++;
        }

        return candidates.stream()
                .sorted(Comparator
                        .comparingLong((ChunkPos chunk) ->
                                horizontalDistanceSqr(
                                        candidateCenter(chunk, origin),
                                        origin
                                ))
                        .thenComparingInt(ChunkPos::x)
                        .thenComparingInt(ChunkPos::z))
                .limit(limit)
                .toList();
    }

    private static BlockPos candidateCenter(
            ChunkPos chunk,
            BlockPos origin
    ) {
        return new BlockPos(
                chunk.getMiddleBlockX(),
                origin.getY(),
                chunk.getMiddleBlockZ()
        );
    }

    private static long horizontalDistanceSqr(
            BlockPos first,
            BlockPos second
    ) {
        long dx = (long) first.getX() - second.getX();
        long dz = (long) first.getZ() - second.getZ();
        return dx * dx + dz * dz;
    }
}
