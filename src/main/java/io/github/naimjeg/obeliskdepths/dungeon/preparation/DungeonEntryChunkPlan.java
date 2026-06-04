
package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable entry-chunk planning result produced by
 * {@link DungeonEntryChunkPlanner}.
 *
 * <p>Contains the room chunk footprint, the expanded requested footprint,
 * and every intersecting chunk in deterministic row-major order.</p>
 */
public final class DungeonEntryChunkPlan {
    private final ChunkPos roomMinChunk;
    private final ChunkPos roomMaxChunk;
    private final ChunkPos requestedMinChunk;
    private final ChunkPos requestedMaxChunk;
    private final List<ChunkPos> chunks;

    DungeonEntryChunkPlan(
            ChunkPos roomMinChunk,
            ChunkPos roomMaxChunk,
            ChunkPos requestedMinChunk,
            ChunkPos requestedMaxChunk,
            List<ChunkPos> chunks
    ) {
        this.roomMinChunk = Objects.requireNonNull(roomMinChunk, "roomMinChunk");
        this.roomMaxChunk = Objects.requireNonNull(roomMaxChunk, "roomMaxChunk");
        this.requestedMinChunk = Objects.requireNonNull(requestedMinChunk, "requestedMinChunk");
        this.requestedMaxChunk = Objects.requireNonNull(requestedMaxChunk, "requestedMaxChunk");
        Objects.requireNonNull(chunks, "chunks");
        this.chunks = List.copyOf(new ArrayList<>(chunks));

        if (roomMinChunk.x() > roomMaxChunk.x()
                || roomMinChunk.z() > roomMaxChunk.z()) {
            throw new IllegalArgumentException(
                    "room chunk bounds must be valid: "
                            + roomMinChunk + " -> " + roomMaxChunk
            );
        }
        if (requestedMinChunk.x() > requestedMaxChunk.x()
                || requestedMinChunk.z() > requestedMaxChunk.z()) {
            throw new IllegalArgumentException(
                    "requested chunk bounds must be valid: "
                            + requestedMinChunk + " -> " + requestedMaxChunk
            );
        }
    }

    public ChunkPos roomMinChunk() {
        return this.roomMinChunk;
    }

    public ChunkPos roomMaxChunk() {
        return this.roomMaxChunk;
    }

    public ChunkPos requestedMinChunk() {
        return this.requestedMinChunk;
    }

    public ChunkPos requestedMaxChunk() {
        return this.requestedMaxChunk;
    }

    public List<ChunkPos> chunks() {
        return this.chunks;
    }
}
