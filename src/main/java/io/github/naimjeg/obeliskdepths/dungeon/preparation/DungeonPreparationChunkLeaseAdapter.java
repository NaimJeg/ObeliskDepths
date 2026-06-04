package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.preparation.chunk.DungeonChunkLease;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.chunk.DungeonChunkLeaseState;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.chunk.DungeonChunkLoadOutcome;
import net.minecraft.world.level.ChunkPos;

import java.util.Objects;
import java.util.Optional;

final class DungeonPreparationChunkLeaseAdapter
        implements DungeonPreparationStartChunkLease {
    private final DungeonChunkLease delegate;

    DungeonPreparationChunkLeaseAdapter(DungeonChunkLease delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public ChunkPos chunkPos() {
        return this.delegate.chunkPos();
    }

    @Override
    public DungeonChunkLeaseState state() {
        return this.delegate.state();
    }

    @Override
    public Optional<DungeonChunkLoadOutcome> outcome() {
        return this.delegate.outcome();
    }

    @Override
    public void close() {
        this.delegate.close();
    }
}
