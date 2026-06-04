package io.github.naimjeg.obeliskdepths.dungeon.preparation.chunk;

import java.util.concurrent.CompletableFuture;

/**
 * Immutable ownership result of installing one physical ticket.
 *
 * <p>The completion carries only a normalized immutable outcome; it never
 * exposes a mutable {@code ChunkAccess} or vanilla result collection. The
 * future may complete on any thread. {@code ticketInstalled} records whether
 * later owner-thread compensation must remove the physical ticket.</p>
 */
record DungeonChunkLoadRequest(
        CompletableFuture<DungeonChunkLoadOutcome> completion,
        boolean ticketInstalled
) {
}
