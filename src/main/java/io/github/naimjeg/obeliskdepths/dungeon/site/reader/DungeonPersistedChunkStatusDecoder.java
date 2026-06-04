package io.github.naimjeg.obeliskdepths.dungeon.site.reader;

import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPersistedChunkProbeResult;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.Objects;
import java.util.Optional;

final class DungeonPersistedChunkStatusDecoder {
    private DungeonPersistedChunkStatusDecoder() {
    }

    static DungeonPersistedChunkProbeResult decode(
            RawPersistedChunkProbeResult raw
    ) {
        Objects.requireNonNull(raw, "raw");
        return decode(
                raw.chunkPos(),
                raw.requiredStatus(),
                raw.missingFieldCount(),
                raw.scanResult(),
                raw.failure()
        );
    }

    static DungeonPersistedChunkProbeResult decode(
            ChunkPos chunkPos,
            ChunkStatus requiredStatus,
            int missingFieldCount,
            Tag scanResult,
            Throwable completionFailure
    ) {
        Objects.requireNonNull(chunkPos, "chunkPos");
        Objects.requireNonNull(requiredStatus, "requiredStatus");
        if (missingFieldCount < 0) {
            throw new IllegalArgumentException(
                    "missingFieldCount must be non-negative"
            );
        }

        if (completionFailure != null) {
            return new DungeonPersistedChunkProbeResult(
                    chunkPos,
                    DungeonPersistedChunkProbeResult.Classification.SCAN_FAILED,
                    Optional.empty(),
                    failureDetail("scanChunk", completionFailure),
                    Optional.of(completionFailure)
            );
        }

        if (scanResult == null) {
            return notPersisted(chunkPos, "Chunk NBT was not present");
        }

        if (missingFieldCount > 0) {
            return notPersisted(chunkPos, "Status field missing from chunk NBT");
        }

        if (!(scanResult instanceof CompoundTag tag)) {
            return notPersisted(chunkPos, "Chunk NBT root is not a compound tag");
        }

        Optional<String> statusName = tag.getString("Status");
        if (statusName.isEmpty() || statusName.get().isBlank()) {
            return notPersisted(chunkPos, "Status field is missing or empty");
        }

        Identifier statusId = Identifier.tryParse(statusName.get());
        if (statusId == null
                || !BuiltInRegistries.CHUNK_STATUS.containsKey(statusId)) {
            return new DungeonPersistedChunkProbeResult(
                    chunkPos,
                    DungeonPersistedChunkProbeResult.Classification.MALFORMED_STATUS,
                    Optional.empty(),
                    "Unknown persisted chunk status: " + statusName.get(),
                    Optional.empty()
            );
        }

        ChunkStatus status = BuiltInRegistries.CHUNK_STATUS.getValue(statusId);

        if (status.isOrAfter(requiredStatus)) {
            return new DungeonPersistedChunkProbeResult(
                    chunkPos,
                    DungeonPersistedChunkProbeResult.Classification
                            .AVAILABLE_AT_REQUIRED_STATUS,
                    Optional.of(status),
                    "",
                    Optional.empty()
            );
        }

        return new DungeonPersistedChunkProbeResult(
                chunkPos,
                DungeonPersistedChunkProbeResult.Classification.BELOW_REQUIRED_STATUS,
                Optional.of(status),
                "Persisted status " + status.getName()
                        + " is below required " + requiredStatus.getName(),
                Optional.empty()
        );
    }

    static String failureDetail(String operation, Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return operation + " failed: " + throwable.getClass().getName();
        }
        return operation + " failed: "
                + throwable.getClass().getName()
                + ": "
                + message;
    }

    private static DungeonPersistedChunkProbeResult notPersisted(
            ChunkPos chunkPos,
            String detail
    ) {
        return new DungeonPersistedChunkProbeResult(
                chunkPos,
                DungeonPersistedChunkProbeResult.Classification.NOT_PERSISTED,
                Optional.empty(),
                detail,
                Optional.empty()
        );
    }
}
