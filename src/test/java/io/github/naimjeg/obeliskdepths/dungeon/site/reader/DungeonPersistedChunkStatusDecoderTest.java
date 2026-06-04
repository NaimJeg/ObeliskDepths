package io.github.naimjeg.obeliskdepths.dungeon.site.reader;

import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonAsyncTestSupport;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPersistedChunkProbeResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.Optional;

public final class DungeonPersistedChunkStatusDecoderTest {
    private DungeonPersistedChunkStatusDecoderTest() {
    }

    public static void main(String[] args) {
        DungeonAsyncTestSupport.bootstrapMinecraft();

        missingNbt();
        missingStatusField();
        emptyStatus();
        belowRequiredStatus();
        exactlyRequiredStatus();
        afterRequiredStatus();
        malformedStatusName();
        exceptionalScanCompletion();
    }

    private static void missingNbt() {
        DungeonPersistedChunkProbeResult result = decode(
                0,
                null,
                null
        );

        check(result.classification()
                        == DungeonPersistedChunkProbeResult.Classification
                        .NOT_PERSISTED,
                "missing nbt: classification");
    }

    private static void missingStatusField() {
        DungeonPersistedChunkProbeResult result = decode(
                1,
                new CompoundTag(),
                null
        );

        check(result.classification()
                        == DungeonPersistedChunkProbeResult.Classification
                        .NOT_PERSISTED,
                "missing status: classification");
    }

    private static void emptyStatus() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Status", "");

        DungeonPersistedChunkProbeResult result = decode(0, tag, null);

        check(result.classification()
                        == DungeonPersistedChunkProbeResult.Classification
                        .NOT_PERSISTED,
                "empty status: classification");
    }

    private static void belowRequiredStatus() {
        DungeonPersistedChunkProbeResult result = decode(
                0,
                statusTag(ChunkStatus.EMPTY),
                null
        );

        check(result.classification()
                        == DungeonPersistedChunkProbeResult.Classification
                        .BELOW_REQUIRED_STATUS,
                "below: classification");
        check(result.persistedStatus().equals(Optional.of(ChunkStatus.EMPTY)),
                "below: status retained");
    }

    private static void exactlyRequiredStatus() {
        DungeonPersistedChunkProbeResult result =
                DungeonPersistedChunkStatusDecoder.decode(
                        pos(),
                        ChunkStatus.STRUCTURE_STARTS,
                        0,
                        statusTag(ChunkStatus.STRUCTURE_STARTS),
                        null
                );

        check(result.classification()
                        == DungeonPersistedChunkProbeResult.Classification
                        .AVAILABLE_AT_REQUIRED_STATUS,
                "exact: classification");
        check(result.persistedStatus()
                        .equals(Optional.of(ChunkStatus.STRUCTURE_STARTS)),
                "exact: status retained");
    }

    private static void afterRequiredStatus() {
        DungeonPersistedChunkProbeResult result =
                DungeonPersistedChunkStatusDecoder.decode(
                        pos(),
                        ChunkStatus.STRUCTURE_STARTS,
                        0,
                        statusTag(ChunkStatus.FULL),
                        null
                );

        check(result.classification()
                        == DungeonPersistedChunkProbeResult.Classification
                        .AVAILABLE_AT_REQUIRED_STATUS,
                "after: classification");
        check(result.persistedStatus().equals(Optional.of(ChunkStatus.FULL)),
                "after: status retained");
    }

    private static void malformedStatusName() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Status", "obeliskdepths:not_a_status");

        DungeonPersistedChunkProbeResult result = decode(0, tag, null);

        check(result.classification()
                        == DungeonPersistedChunkProbeResult.Classification
                        .MALFORMED_STATUS,
                "malformed: classification");
        check(result.detail().contains("obeliskdepths:not_a_status"),
                "malformed: detail");
    }

    private static void exceptionalScanCompletion() {
        RuntimeException cause = new RuntimeException("disk failed");

        DungeonPersistedChunkProbeResult result = decode(0, null, cause);

        check(result.classification()
                        == DungeonPersistedChunkProbeResult.Classification
                        .SCAN_FAILED,
                "exception: classification");
        check(result.failure().isPresent(), "exception: failure present");
        check(result.failure().get() == cause, "exception: cause preserved");
        check(result.detail().contains("disk failed"),
                "exception: detail");
    }

    private static DungeonPersistedChunkProbeResult decode(
            int missingFieldCount,
            net.minecraft.nbt.Tag scanResult,
            Throwable throwable
    ) {
        return DungeonPersistedChunkStatusDecoder.decode(
                pos(),
                ChunkStatus.FULL,
                missingFieldCount,
                scanResult,
                throwable
        );
    }

    private static CompoundTag statusTag(ChunkStatus status) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Status", status.getName());
        return tag;
    }

    private static void check(boolean condition, String message) {
        DungeonAsyncTestSupport.check(condition, message);
    }

    private static ChunkPos pos() {
        return new ChunkPos(0, 0);
    }
}
