package io.github.naimjeg.obeliskdepths.dungeon.site.reader;

import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.Objects;

public record RawPersistedChunkProbeResult(
        ChunkPos chunkPos,
        ChunkStatus requiredStatus,
        int missingFieldCount,
        Tag scanResult,
        Throwable failure
) {
    public RawPersistedChunkProbeResult {
        chunkPos = Objects.requireNonNull(chunkPos, "chunkPos");
        requiredStatus = Objects.requireNonNull(requiredStatus, "requiredStatus");
        if (missingFieldCount < 0) {
            throw new IllegalArgumentException(
                    "missingFieldCount must be non-negative"
            );
        }
        scanResult = scanResult == null ? null : scanResult.copy();
    }
}
