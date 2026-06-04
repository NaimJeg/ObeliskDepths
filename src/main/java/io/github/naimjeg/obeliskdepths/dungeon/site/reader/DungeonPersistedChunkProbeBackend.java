package io.github.naimjeg.obeliskdepths.dungeon.site.reader;

import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPersistedChunkProbeResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Abstraction for asynchronous persisted-chunk status probing.
 *
 * <p>Allows deterministic testing with fake backends.  The production
 * implementation uses {@code level.getChunkSource().chunkScanner().scanChunk()}
 * without blocking, loading, or generating chunks.
 *
 * <p>{@link #probe(ChunkPos, ChunkStatus)} must be invoked on the owner thread
 * because production submission reaches level-owned storage. The concrete
 * I/O worker may complete on any thread. Normal results are immutable and are
 * completed on the owner thread after extraction and decoding; if owner
 * executor submission itself is rejected, the future completes exceptionally
 * on the rejecting completion thread so the scanner's thread-safe mailbox can
 * retain the terminal signal. No callback may access world, registry, or
 * mutable scanner state.</p>
 */
public interface DungeonPersistedChunkProbeBackend {
    static DungeonPersistedChunkProbeBackend forLevel(ServerLevel level) {
        return new ServerPersistedChunkProbeBackend(
                Objects.requireNonNull(level, "level")
        );
    }

    /**
     * Probes the persisted storage for the chunk at {@code chunkPos}.
     *
     * <p>Must not load, generate, or add tickets for the chunk.
     * The returned future completes with an immutable result. Production
     * extraction and registry decoding occur after physical scan completion
     * and only on the owner thread.
     */
    CompletableFuture<DungeonPersistedChunkProbeResult> probe(
            ChunkPos chunkPos,
            ChunkStatus requiredStatus
    );

    /**
     * Non-generating fast-path probe: returns a completed result if the chunk
     * is already loaded in memory at or after the required status.
     *
     * <p>Must be called on the owner thread.  Default returns empty.
     */
    default Optional<DungeonPersistedChunkProbeResult> probeLoadedChunk(
            ChunkPos chunkPos,
            ChunkStatus requiredStatus
    ) {
        return Optional.empty();
    }

    /** Returns true when called on the owning server thread. */
    boolean isOwnerThread();
}
