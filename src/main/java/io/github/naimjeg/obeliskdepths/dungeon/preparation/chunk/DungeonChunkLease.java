package io.github.naimjeg.obeliskdepths.dungeon.preparation.chunk;

import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.world.level.ChunkPos;

public final class DungeonChunkLease implements AutoCloseable {
    private final ChunkPos chunkPos;
    private final long entryToken;
    private final DungeonChunkLeaseManager manager;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    DungeonChunkLease(
            ChunkPos chunkPos,
            long entryToken,
            DungeonChunkLeaseManager manager
    ) {
        this.chunkPos = chunkPos;
        this.entryToken = entryToken;
        this.manager = manager;
    }

    public ChunkPos chunkPos() {
        return this.chunkPos;
    }

    public DungeonChunkLeaseState state() {
        return this.manager.stateFor(this.chunkPos, this.entryToken);
    }

    @Override
    public void close() {
        this.manager.assertOwnerThread();
        if (this.closed.compareAndSet(false, true)) {
            this.manager.release(this.chunkPos, this.entryToken);
        }
    }
}
