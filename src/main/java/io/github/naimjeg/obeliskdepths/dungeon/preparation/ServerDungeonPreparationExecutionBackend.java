package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.chunk.DungeonChunkLeaseManager;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSafeSpawnResolver;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSafeSpawnScan;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import io.github.naimjeg.obeliskdepths.dungeon.site.reader.*;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import io.github.naimjeg.obeliskdepths.worldgen.structure.placement.ObeliskDungeonPlacementSettings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.List;
import java.util.Objects;

final class ServerDungeonPreparationExecutionBackend
        implements DungeonPreparationExecutionBackend {
    private final ServerLevel level;
    private final DungeonChunkLeaseManager leaseManager;

    ServerDungeonPreparationExecutionBackend(
            ServerLevel level,
            DungeonChunkLeaseManager leaseManager
    ) {
        this.level = Objects.requireNonNull(level, "level");
        this.leaseManager = Objects.requireNonNull(leaseManager, "leaseManager");
    }

    @Override
    public void assertOwnerThread() {
        if (!this.level.getServer().isSameThread()) {
            throw new IllegalStateException(
                    "Dungeon preparation execution must run on the server thread."
            );
        }
    }

    @Override
    public long gameTime() {
        assertOwnerThread();
        return this.level.getGameTime();
    }

    @Override
    public int maxCandidateCount() {
        return ObeliskDungeonPlacementSettings.MAX_LOOKUP_CANDIDATES;
    }

    @Override
    public DungeonSiteCandidateCursor createCandidateCursor(
            DungeonPreparationRequest request,
            int requestedLimit
    ) {
        assertOwnerThread();
        DungeonPreparationProfiler profiler = DungeonPreparationProfiler.global();
        if (profiler.enabled()) {
            long startNanos = profiler.start();
            try {
                return DungeonStructureLocator.candidateCursor(
                        this.level.getSeed(),
                        request.obeliskPos(),
                        requestedLimit
                );
            } finally {
                profiler.record(
                        DungeonPreparationProfiler.Operation.CREATE_CANDIDATE_CURSOR,
                        startNanos,
                        true
                );
            }
        }
        return DungeonStructureLocator.candidateCursor(
                this.level.getSeed(),
                request.obeliskPos(),
                requestedLimit
        );
    }

    @Override
    public AsyncDungeonSiteProbe createSiteProbe(
            List<DungeonSiteKey> candidates,
            ChunkStatus requiredStatus,
            int maxConcurrentProbes
    ) {
        assertOwnerThread();
        return AsyncDungeonSiteProbe.createForLevel(
                this.level,
                candidates,
                requiredStatus,
                maxConcurrentProbes
        );
    }

    @Override
    public DungeonPreparationStartChunkLease acquireStartChunk(DungeonSiteKey key) {
        assertOwnerThread();
        return new DungeonPreparationChunkLeaseAdapter(
                this.leaseManager.acquire(key.toChunkPos())
        );
    }

    @Override
    public DungeonPreparationStartChunkLease acquireEntryChunk(ChunkPos chunkPos) {
        assertOwnerThread();
        return new DungeonPreparationChunkLeaseAdapter(
                this.leaseManager.acquire(chunkPos)
        );
    }

    @Override
    public boolean isChunkLoaded(ChunkPos chunkPos) {
        assertOwnerThread();
        return this.level.getChunkSource()
                .getChunkNow(chunkPos.x(), chunkPos.z()) != null;
    }

    @Override
    public DungeonSafeSpawnScan createSafeEntryScan(DungeonSite site) {
        assertOwnerThread();
        return DungeonSafeSpawnResolver.createPrimaryEntryScan(this.level, site);
    }

    @Override
    public DungeonPreparationLoadedSiteResult readLoadedSite(DungeonSiteKey key) {
        assertOwnerThread();
        Objects.requireNonNull(key, "key");

        DungeonStructureStartReader.LoadedStructureStartResult lookup =
                DungeonStructureStartReader.lookupLoaded(this.level, key);

        if (lookup.start().isEmpty()) {
            return DungeonPreparationLoadedSiteResult.rejected(
                    failureReasonFor(lookup.failure()),
                    "loaded structure-start lookup rejected: "
                            + lookup.failure().name()
            );
        }

        try {
            DungeonPreparationProfiler profiler = DungeonPreparationProfiler.global();
            LoadedDungeonSiteProjectionResult projection;
            if (profiler.enabled()) {
                long startNanos = profiler.start();
                try {
                    projection = LoadedDungeonSiteReader.projectValidatedStart(
                            key,
                            lookup.start().orElseThrow()
                    );
                } finally {
                    profiler.record(
                            DungeonPreparationProfiler.Operation.STRUCTURE_SITE_PROJECTION,
                            startNanos,
                            true
                    );
                }
            } else {
                projection = LoadedDungeonSiteReader.projectValidatedStart(
                        key,
                        lookup.start().orElseThrow()
                );
            }
            if (projection.accepted()) {
                return DungeonPreparationLoadedSiteResult.accepted(
                        projection.site().orElseThrow()
                );
            }

            LoadedDungeonSiteProjectionFailure failure =
                    Objects.requireNonNull(
                            projection.failure(),
                            "rejected projection failure"
                    );

            return DungeonPreparationLoadedSiteResult.rejected(
                    projectionFailureReason(failure),
                    failure.name()
                            + ": "
                            + projection.distanceReport().describeSummary()
            );
        } catch (RuntimeException exception) {
            return DungeonPreparationLoadedSiteResult.rejected(
                    DungeonPreparationJobFailureReason.INTERNAL_ERROR,
                    "loaded dungeon-site projection failed: "
                            + diagnosticDetail(exception),
                    exception
            );
        }
    }

    static DungeonPreparationJobFailureReason failureReasonFor(
            DungeonStructureStartReader.LoadedStructureStartResult.Failure failure
    ) {
        return switch (failure) {
            case NONE -> DungeonPreparationJobFailureReason.INTERNAL_ERROR;
            case CHUNK_NOT_LOADED -> DungeonPreparationJobFailureReason.CHUNK_LOAD_FAILED;
            case STRUCTURE_TYPE_MISSING -> DungeonPreparationJobFailureReason.INTERNAL_ERROR;
            case STRUCTURE_START_MISSING -> DungeonPreparationJobFailureReason.STRUCTURE_START_MISSING;
            case STRUCTURE_START_INVALID -> DungeonPreparationJobFailureReason.STRUCTURE_START_INVALID;
        };
    }

    static DungeonPreparationJobFailureReason projectionFailureReason(
            LoadedDungeonSiteProjectionFailure failure
    ) {
        return switch (failure) {
            case NO_PIECES -> DungeonPreparationJobFailureReason.STRUCTURE_START_INVALID;
            case INVALID_PRIMARY_ENTRY_COUNT -> DungeonPreparationJobFailureReason.STRUCTURE_START_INVALID;
            case OUTSIDE_VANILLA_REFERENCE_DISTANCE -> DungeonPreparationJobFailureReason.NON_AUTHORITATIVE_SITE;
            case INCOMPLETE_PROJECTED_METADATA -> DungeonPreparationJobFailureReason.NON_AUTHORITATIVE_SITE;
        };
    }

    @Override
    public String generatedReservationRejectionReason(DungeonSiteKey key) {
        assertOwnerThread();
        Objects.requireNonNull(key, "key");

        return DungeonManagerSavedData.get(this.level)
                .sites()
                .generatedReservationRejectionReason(key);
    }

    @Override
    public void logJobRuntimeFailure(
            DungeonPreparationJob job,
            RuntimeException exception
    ) {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(exception, "exception");

        ObeliskDepths.LOGGER.error(
                "Unexpected dungeon preparation job runtime failure: job={}",
                job,
                exception
        );
    }

    private static String diagnosticDetail(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getName();
        }
        return throwable.getClass().getName() + ": " + message;
    }
}
