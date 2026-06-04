package io.github.naimjeg.obeliskdepths.dungeon.preparation.chunk;

import net.minecraft.world.level.ChunkPos;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

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

    /**
     * The normalised outcome for this lease.
     *
     * <p>Returns {@code Optional.empty()} unless the lease is in the
     * {@link DungeonChunkLeaseState#FAILED} state.  The outcome carries the
     * stable failure reason, a diagnostic detail string, and an optional
     * cause {@link Throwable}.
     */
    public Optional<DungeonChunkLoadOutcome> outcome() {
        if (state() != DungeonChunkLeaseState.FAILED) {
            return Optional.empty();
        }
        return this.manager.outcomeFor(this.chunkPos, this.entryToken);
    }

    @Override
    public void close() {
        this.manager.assertOwnerThread();
        if (this.closed.compareAndSet(false, true)) {
            this.manager.release(this.chunkPos, this.entryToken);
        }
    }
}
