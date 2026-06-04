package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.id.PortalSessionId;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import net.minecraft.world.level.ChunkPos;

import java.util.Objects;
import java.util.function.Predicate;

/** Pure validation of immutable prepared-entry state for the portal fast path. */
public final class DungeonPreparedEntryValidator {
    private DungeonPreparedEntryValidator() {
    }

    public static DungeonPreparedEntryValidation validate(
            PortalSessionId portalSessionId,
            DungeonInstanceId instanceId,
            DungeonSiteKey siteKey,
            DungeonPreparedPortalEntry prepared,
            long gameTime,
            Predicate<ChunkPos> loadedChunkLookup
    ) {
        Objects.requireNonNull(portalSessionId, "portalSessionId");
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(siteKey, "siteKey");
        Objects.requireNonNull(prepared, "prepared");
        Objects.requireNonNull(loadedChunkLookup, "loadedChunkLookup");

        if (!prepared.portalSessionId().equals(portalSessionId)
                || !prepared.instanceId().equals(instanceId)
                || !prepared.siteKey().equals(siteKey)) {
            return DungeonPreparedEntryValidation.IDENTITY_MISMATCH;
        }
        if (prepared.isClosed() || prepared.isStale(gameTime)) {
            return DungeonPreparedEntryValidation.CLOSED_OR_STALE;
        }
        for (ChunkPos chunkPos : prepared.entryChunks()) {
            if (!loadedChunkLookup.test(chunkPos)) {
                return DungeonPreparedEntryValidation.CHUNK_UNAVAILABLE;
            }
        }
        return DungeonPreparedEntryValidation.VALID;
    }
}
