package io.github.naimjeg.obeliskdepths.dungeon.site.reader;

import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import io.github.naimjeg.obeliskdepths.worldgen.structure.placement.ObeliskDungeonPlacementSettings;
import io.github.naimjeg.obeliskdepths.worldgen.structure.placement.ObeliskDungeonSiteOverlapGuard;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class DungeonStructureLocator {
    private static final int MAX_CANDIDATE_ATTEMPTS =
            ObeliskDungeonPlacementSettings.MAX_LOOKUP_CANDIDATES;

    private DungeonStructureLocator() {
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

    public static DungeonSiteCandidateCursor candidateCursor(
            long worldSeed,
            BlockPos origin,
            int requestedLimit
    ) {
        Objects.requireNonNull(origin, "origin");
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
        return new Cursor(worldSeed, origin, originRegionX, originRegionZ, limit);
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

    private static final class Cursor implements DungeonSiteCandidateCursor {
        private final long worldSeed;
        private final BlockPos origin;
        private final int originRegionX;
        private final int originRegionZ;
        private final int limit;
        private final List<ChunkPos> rawCandidates = new ArrayList<>();
        private List<DungeonSiteKey> orderedCandidates;
        private int regionRadius;
        private int ringDx;
        private int ringDz;
        private int producedCount;
        private boolean generationComplete;

        Cursor(
                long worldSeed,
                BlockPos origin,
                int originRegionX,
                int originRegionZ,
                int limit
        ) {
            this.worldSeed = worldSeed;
            this.origin = origin;
            this.originRegionX = originRegionX;
            this.originRegionZ = originRegionZ;
            this.limit = limit;
            resetRingCursor();
        }

        @Override
        public int advance(int maximumKeys, Consumer<DungeonSiteKey> sink) {
            Objects.requireNonNull(sink, "sink");
            if (maximumKeys < 0) {
                throw new IllegalArgumentException(
                        "maximumKeys must be non-negative"
                );
            }
            if (maximumKeys == 0 || exhausted()) {
                return 0;
            }

            int allowance = maximumKeys;
            while (allowance > 0 && !this.generationComplete) {
                generateOneCandidate();
                allowance--;
            }

            if (this.generationComplete && this.orderedCandidates == null) {
                this.orderedCandidates = this.rawCandidates.stream()
                        .sorted(Comparator
                                .comparingLong((ChunkPos chunk) ->
                                        horizontalDistanceSqr(
                                                candidateCenter(chunk, this.origin),
                                                this.origin
                                        ))
                                .thenComparingInt(ChunkPos::x)
                                .thenComparingInt(ChunkPos::z))
                        .limit(this.limit)
                        .map(DungeonSiteKey::fromStartChunk)
                        .toList();
            }

            int emitted = 0;
            while (allowance > 0
                    && this.orderedCandidates != null
                    && this.producedCount < this.orderedCandidates.size()) {
                sink.accept(this.orderedCandidates.get(this.producedCount));
                this.producedCount++;
                allowance--;
                emitted++;
            }
            return emitted;
        }

        @Override
        public boolean exhausted() {
            return this.orderedCandidates != null
                    && this.producedCount >= this.orderedCandidates.size();
        }

        @Override
        public int producedCount() {
            return this.producedCount;
        }

        @Override
        public List<DungeonSiteKey> producedKeys() {
            if (this.orderedCandidates == null) {
                return List.of();
            }
            return List.copyOf(this.orderedCandidates.subList(
                    0,
                    this.producedCount
            ));
        }

        private void generateOneCandidate() {
            int generatedRing = this.regionRadius;
            this.rawCandidates.add(ObeliskDungeonSiteOverlapGuard.candidateChunk(
                    this.worldSeed,
                    this.originRegionX + this.ringDx,
                    this.originRegionZ + this.ringDz
            ));
            advanceRingCursor();

            /*
             * The legacy lookup always finished the current region ring before
             * sorting and truncating to the requested limit. Complete the same
             * ring here so incremental enumeration preserves the exact legacy
             * candidate set and deterministic ordering.
             */
            boolean completedRing = this.regionRadius != generatedRing;
            if (completedRing && this.rawCandidates.size() >= this.limit) {
                this.generationComplete = true;
            }
        }

        private void advanceRingCursor() {
            if (this.regionRadius == 0) {
                this.regionRadius = 1;
                resetRingCursor();
                return;
            }
            do {
                if (this.ringDz < this.regionRadius) {
                    this.ringDz++;
                } else if (this.ringDx < this.regionRadius) {
                    this.ringDx++;
                    this.ringDz = -this.regionRadius;
                } else {
                    this.regionRadius++;
                    resetRingCursor();
                    return;
                }
            } while (Math.max(Math.abs(this.ringDx), Math.abs(this.ringDz))
                    != this.regionRadius);
        }

        private void resetRingCursor() {
            this.ringDx = -this.regionRadius;
            this.ringDz = -this.regionRadius;
        }
    }
}
