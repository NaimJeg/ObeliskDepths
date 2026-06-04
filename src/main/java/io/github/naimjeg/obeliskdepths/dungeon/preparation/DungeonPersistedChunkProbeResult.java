package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.Optional;

/**
 * Immutable result of a single non-blocking persisted-chunk status probe.
 *
 * <p>The scanner does not load or generate the chunk; it inspects the
 * serialized NBT stored on disk.
 */
public record DungeonPersistedChunkProbeResult(
        ChunkPos chunkPos,
        Classification classification,
        Optional<ChunkStatus> persistedStatus,
        String detail,
        Optional<Throwable> failure
) {
    public DungeonPersistedChunkProbeResult {
        if (chunkPos == null) {
            throw new IllegalArgumentException("probe chunkPos must be present.");
        }
        if (classification == null) {
            throw new IllegalArgumentException("probe classification must be present.");
        }
        persistedStatus = persistedStatus == null
                ? Optional.empty() : persistedStatus;
        detail = detail == null ? "" : detail;
        failure = failure == null ? Optional.empty() : failure;
    }

    /** Classification of the probe outcome. */
    public enum Classification {
        /**
         * The persisted chunk status is equal to or after the required status.
         */
        AVAILABLE_AT_REQUIRED_STATUS,
        /**
         * No usable chunk NBT or no {@code Status} field exists on disk.
         */
        NOT_PERSISTED,
        /**
         * A valid persisted status exists but is before the required status.
         */
        BELOW_REQUIRED_STATUS,
        /**
         * The asynchronous I/O future completed exceptionally.
         */
        SCAN_FAILED,
        /**
         * A {@code Status} value exists but cannot be resolved to a known
         * {@link ChunkStatus}.
         */
        MALFORMED_STATUS,
        /**
         * The aggregate scan session was cancelled before this candidate
         * could be processed.
         */
        CANCELLED
    }
}
