package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.preparation.chunk.DungeonChunkLeaseState;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.chunk.DungeonChunkLoadOutcome;
import net.minecraft.world.level.ChunkPos;

import java.util.Optional;

interface DungeonPreparationStartChunkLease extends AutoCloseable {
    ChunkPos chunkPos();

    DungeonChunkLeaseState state();

    Optional<DungeonChunkLoadOutcome> outcome();

    @Override
    void close();
}
