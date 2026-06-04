package io.github.naimjeg.obeliskdepths.dungeon.preparation.chunk;

import java.util.concurrent.CompletableFuture;

record DungeonChunkLoadRequest(
        CompletableFuture<?> completion,
        boolean ticketInstalled
) {
    DungeonChunkLoadRequest {
        if (completion == null) {
            throw new IllegalArgumentException("Chunk load completion must be present.");
        }
    }
}
