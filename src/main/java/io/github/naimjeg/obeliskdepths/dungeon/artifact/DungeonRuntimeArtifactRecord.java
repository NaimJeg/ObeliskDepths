package io.github.naimjeg.obeliskdepths.dungeon.artifact;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.reward.DungeonRewardId;
import java.util.Optional;
import net.minecraft.core.BlockPos;

public record DungeonRuntimeArtifactRecord(
        DungeonInstanceId instanceId,
        DungeonRuntimeArtifactType type,
        Optional<BlockPos> pos,
        Optional<DungeonRewardId> rewardId,
        boolean pendingCleanup,
        long nextCleanupRetryGameTime,
        int cleanupMismatchCount
) {
    public static final Codec<DungeonRuntimeArtifactRecord> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    DungeonInstanceId.CODEC.fieldOf("instance_id")
                            .forGetter(DungeonRuntimeArtifactRecord::instanceId),
                    DungeonRuntimeArtifactType.CODEC.fieldOf("type")
                            .forGetter(DungeonRuntimeArtifactRecord::type),
                    BlockPos.CODEC.optionalFieldOf("pos")
                            .forGetter(DungeonRuntimeArtifactRecord::pos),
                    DungeonRewardId.CODEC.optionalFieldOf("reward_id")
                            .forGetter(DungeonRuntimeArtifactRecord::rewardId),
                    Codec.BOOL.optionalFieldOf("pending_cleanup", false)
                            .forGetter(DungeonRuntimeArtifactRecord::pendingCleanup),
                    Codec.LONG.optionalFieldOf("next_cleanup_retry_game_time", 0L)
                            .forGetter(DungeonRuntimeArtifactRecord::nextCleanupRetryGameTime),
                    Codec.INT.optionalFieldOf("cleanup_mismatch_count", 0)
                            .forGetter(DungeonRuntimeArtifactRecord::cleanupMismatchCount)
            ).apply(instance, DungeonRuntimeArtifactRecord::new));

    public DungeonRuntimeArtifactRecord(
            DungeonInstanceId instanceId,
            DungeonRuntimeArtifactType type,
            Optional<BlockPos> pos,
            Optional<DungeonRewardId> rewardId,
            boolean pendingCleanup
    ) {
        this(instanceId, type, pos, rewardId, pendingCleanup, 0L, 0);
    }

    public DungeonRuntimeArtifactRecord markPending(long nextRetryGameTime) {
        return new DungeonRuntimeArtifactRecord(
                this.instanceId,
                this.type,
                this.pos,
                this.rewardId,
                true,
                nextRetryGameTime,
                this.cleanupMismatchCount
        );
    }

    public DungeonRuntimeArtifactRecord recordMismatch(long nextRetryGameTime) {
        return new DungeonRuntimeArtifactRecord(
                this.instanceId,
                this.type,
                this.pos,
                this.rewardId,
                true,
                nextRetryGameTime,
                this.cleanupMismatchCount + 1
        );
    }
}
