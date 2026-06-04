package io.github.naimjeg.obeliskdepths.dungeon.preparation.chunk;

import net.minecraft.world.level.ChunkPos;

/**
 * Owner-thread boundary for installing and removing preparation tickets.
 *
 * <p>In the resolved 26.1.2 implementation, acquisition synchronously
 * validates the ticket type, registers the radius ticket, runs distance-map
 * updates, obtains the visible holder, and schedules the holder's FULL-status
 * work before returning {@code CompletableFuture<?>}. Those steps can be
 * substantial but do not directly perform the region-file read on the caller
 * thread. The radius-zero implementation later produces a runtime
 * {@code ChunkResult<List<ChunkAccess>>}; the wildcard signature does not
 * prove that shape, so normalization must validate it at runtime.</p>
 *
 * <p>The raw future can complete on whichever owner or chunk-work executor
 * completes the scheduled status chain. Completion callbacks may normalize
 * the runtime value into immutable data, but mutable lease state changes and
 * both {@link #acquire(ChunkPos)} and {@link #release(ChunkPos)} are confined
 * to the owning server thread.</p>
 */
interface DungeonChunkTicketBackend {
    DungeonChunkLoadRequest acquire(ChunkPos chunkPos);

    void release(ChunkPos chunkPos);

    boolean isOwnerThread();

    void executeOnOwnerThread(Runnable task);
}
