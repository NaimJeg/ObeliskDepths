package io.github.naimjeg.obeliskdepths.dungeon.preparation.chunk;

import net.minecraft.world.level.ChunkPos;

interface DungeonChunkTicketBackend {
    DungeonChunkLoadRequest acquire(ChunkPos chunkPos);

    void release(ChunkPos chunkPos);

    boolean isOwnerThread();

    void executeOnOwnerThread(Runnable task);
}
