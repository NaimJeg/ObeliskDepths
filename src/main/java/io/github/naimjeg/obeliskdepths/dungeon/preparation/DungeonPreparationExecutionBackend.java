package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSafeSpawnScan;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import io.github.naimjeg.obeliskdepths.dungeon.site.reader.DungeonSiteCandidateCursor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.List;

interface DungeonPreparationExecutionBackend {
    void assertOwnerThread();

    long gameTime();

    int maxCandidateCount();

    DungeonSiteCandidateCursor createCandidateCursor(
            DungeonPreparationRequest request,
            int requestedLimit
    );

    AsyncDungeonSiteProbe createSiteProbe(
            List<DungeonSiteKey> candidates,
            ChunkStatus requiredStatus,
            int maxConcurrentProbes
    );

    DungeonPreparationStartChunkLease acquireStartChunk(DungeonSiteKey key);

    DungeonPreparationStartChunkLease acquireEntryChunk(ChunkPos chunkPos);

    DungeonPreparationLoadedSiteResult readLoadedSite(DungeonSiteKey key);

    boolean isChunkLoaded(ChunkPos chunkPos);

    DungeonSafeSpawnScan createSafeEntryScan(DungeonSite site);

    String generatedReservationRejectionReason(DungeonSiteKey key);

    void logJobRuntimeFailure(
            DungeonPreparationJob job,
            RuntimeException exception
    );
}
