package io.github.naimjeg.obeliskdepths.dungeon.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

public final class DungeonRewardRecord {
    public static final Codec<DungeonRewardRecord> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    DungeonRewardId.CODEC.fieldOf("reward_id")
                            .forGetter(DungeonRewardRecord::rewardId),
                    DungeonInstanceId.CODEC.fieldOf("instance_id")
                            .forGetter(DungeonRewardRecord::instanceId),
                    DungeonRoomId.CODEC.optionalFieldOf("room_id")
                            .forGetter(DungeonRewardRecord::roomId),
                    DungeonRewardStatus.CODEC
                            .optionalFieldOf("status", DungeonRewardStatus.NOT_READY)
                            .forGetter(DungeonRewardRecord::status),
                    BlockPos.CODEC.optionalFieldOf("preferred_placement_origin")
                            .forGetter(DungeonRewardRecord::preferredPlacementOrigin),
                    BlockPos.CODEC.optionalFieldOf("placed_main_pos")
                            .forGetter(DungeonRewardRecord::placedMainPos),
                    Codec.LONG.optionalFieldOf("reward_seed", 0L)
                            .forGetter(DungeonRewardRecord::rewardSeed),
                    Codec.INT.optionalFieldOf("placement_failures", 0)
                            .forGetter(DungeonRewardRecord::placementFailures),
                    Codec.LONG.optionalFieldOf("created_game_time", 0L)
                            .forGetter(DungeonRewardRecord::createdGameTime),
                    RewardDeliveryPlan.CODEC
                            .optionalFieldOf("delivery_plan")
                            .forGetter(DungeonRewardRecord::deliveryPlan)
            ).apply(instance, DungeonRewardRecord::new));

    private final DungeonRewardId rewardId;
    private final DungeonInstanceId instanceId;
    private final Optional<DungeonRoomId> roomId;
    private DungeonRewardStatus status;
    private final Optional<BlockPos> preferredPlacementOrigin;
    private Optional<BlockPos> placedMainPos;
    private final long rewardSeed;
    private int placementFailures;
    private final long createdGameTime;
    private RewardDeliveryPlan deliveryPlan;

    public DungeonRewardRecord(
            DungeonRewardId rewardId,
            DungeonInstanceId instanceId,
            Optional<DungeonRoomId> roomId,
            DungeonRewardStatus status,
            Optional<BlockPos> preferredPlacementOrigin,
            Optional<BlockPos> placedMainPos,
            long rewardSeed,
            int placementFailures,
            long createdGameTime,
            Optional<RewardDeliveryPlan> deliveryPlan
    ) {
        this.rewardId = rewardId;
        this.instanceId = instanceId;
        this.roomId = roomId == null ? Optional.empty() : roomId;
        this.status = status == null ? DungeonRewardStatus.NOT_READY : status;
        this.preferredPlacementOrigin = immutable(preferredPlacementOrigin);
        this.placedMainPos = immutable(placedMainPos);
        this.rewardSeed = rewardSeed;
        this.placementFailures = Math.max(0, placementFailures);
        this.createdGameTime = createdGameTime;
        this.deliveryPlan = deliveryPlan == null ? null : deliveryPlan.orElse(null);
    }

    public DungeonRewardRecord(
            DungeonRewardId rewardId,
            DungeonInstanceId instanceId,
            Optional<DungeonRoomId> roomId,
            DungeonRewardStatus status,
            Optional<BlockPos> rewardPos,
            long rewardSeed,
            int placementFailures,
            long createdGameTime,
            Optional<RewardDeliveryPlan> deliveryPlan
    ) {
        this(
                rewardId,
                instanceId,
                roomId,
                status,
                Optional.empty(),
                rewardPos,
                rewardSeed,
                placementFailures,
                createdGameTime,
                deliveryPlan
        );
    }

    public static DungeonRewardRecord bossDefeated(
            DungeonInstanceId instanceId,
            Optional<DungeonRoomId> roomId,
            long gameTime
    ) {
        return bossDefeated(instanceId, roomId, Optional.empty(), gameTime);
    }

    public static DungeonRewardRecord bossDefeated(
            DungeonInstanceId instanceId,
            Optional<DungeonRoomId> roomId,
            Optional<BlockPos> preferredPlacementOrigin,
            long gameTime
    ) {
        long seed = UUID.randomUUID().getMostSignificantBits()
                ^ UUID.randomUUID().getLeastSignificantBits();
        return new DungeonRewardRecord(
                DungeonRewardId.create(),
                instanceId,
                roomId,
                DungeonRewardStatus.BOSS_DEFEATED,
                immutable(preferredPlacementOrigin),
                Optional.empty(),
                seed,
                0,
                gameTime,
                Optional.empty()
        );
    }

    public DungeonRewardId rewardId() {
        return this.rewardId;
    }

    public DungeonInstanceId instanceId() {
        return this.instanceId;
    }

    public Optional<DungeonRoomId> roomId() {
        return this.roomId;
    }

    public DungeonRewardStatus status() {
        return this.status;
    }

    public Optional<BlockPos> rewardPos() {
        return this.placedMainPos;
    }

    public Optional<BlockPos> preferredPlacementOrigin() {
        return this.preferredPlacementOrigin;
    }

    public Optional<BlockPos> placedMainPos() {
        return this.placedMainPos;
    }

    public long rewardSeed() {
        return this.rewardSeed;
    }

    public int placementFailures() {
        return this.placementFailures;
    }

    public long createdGameTime() {
        return this.createdGameTime;
    }

    public boolean markPlacementPending() {
        if (this.status == DungeonRewardStatus.AVAILABLE
                || this.status == DungeonRewardStatus.CLAIMING
                || this.status == DungeonRewardStatus.OPENED
                || this.status == DungeonRewardStatus.CLAIMED
                || this.status == DungeonRewardStatus.CLEANED
                || this.status == DungeonRewardStatus.EXPIRED
                || this.status == DungeonRewardStatus.PLACEMENT_FAILED) {
            return false;
        }

        return setStatus(DungeonRewardStatus.PLACEMENT_PENDING);
    }

    public boolean markAvailable(BlockPos pos) {
        if (this.status == DungeonRewardStatus.CLAIMING
                || this.status == DungeonRewardStatus.OPENED
                || this.status == DungeonRewardStatus.CLAIMED
                || this.status == DungeonRewardStatus.CLEANED
                || this.status == DungeonRewardStatus.EXPIRED) {
            return false;
        }

        boolean changed = setStatus(DungeonRewardStatus.AVAILABLE);
        Optional<BlockPos> nextPos = Optional.of(pos.immutable());

        if (!nextPos.equals(this.placedMainPos)) {
            this.placedMainPos = nextPos;
            changed = true;
        }

        return changed;
    }

    public boolean recordPlacementFailure() {
        this.placementFailures++;
        if (this.status != DungeonRewardStatus.AVAILABLE
                && this.status != DungeonRewardStatus.CLAIMING
                && this.status != DungeonRewardStatus.OPENED
                && this.status != DungeonRewardStatus.CLAIMED
                && this.status != DungeonRewardStatus.CLEANED
                && this.status != DungeonRewardStatus.EXPIRED
                && this.status != DungeonRewardStatus.PLACEMENT_FAILED) {
            setStatus(DungeonRewardStatus.PLACEMENT_PENDING);
        }

        return true;
    }

    public boolean markPlacementFailed() {
        if (this.status == DungeonRewardStatus.CLAIMING
                || this.status == DungeonRewardStatus.OPENED
                || this.status == DungeonRewardStatus.CLAIMED
                || this.status == DungeonRewardStatus.CLEANED
                || this.status == DungeonRewardStatus.EXPIRED) {
            return false;
        }

        return setStatus(DungeonRewardStatus.PLACEMENT_FAILED);
    }

    public Optional<RewardDeliveryPlan> deliveryPlan() {
        return Optional.ofNullable(this.deliveryPlan);
    }

    public boolean hasDeliveryPlan() {
        return this.deliveryPlan != null;
    }

    public boolean beginDeliveryPlan(List<ItemStack> stacks) {
        if (this.status != DungeonRewardStatus.CLAIMING) {
            return false;
        }

        if (this.deliveryPlan != null) {
            return false;
        }

        this.deliveryPlan = RewardDeliveryPlan.start(stacks);
        return true;
    }

    public boolean advanceDeliveryPlan() {
        if (this.deliveryPlan == null || this.deliveryPlan.complete()) {
            return false;
        }

        this.deliveryPlan = this.deliveryPlan.advance();
        return true;
    }

    public boolean clearDeliveryPlan() {
        if (this.deliveryPlan == null) {
            return false;
        }

        this.deliveryPlan = null;
        return true;
    }

    public boolean markClaiming() {
        if (!this.status.claimable()) {
            return false;
        }

        return setStatus(DungeonRewardStatus.CLAIMING);
    }

    public boolean markOpened() {
        if (this.status != DungeonRewardStatus.AVAILABLE) {
            return false;
        }

        return setStatus(DungeonRewardStatus.OPENED);
    }

    public boolean recoverClaimingToAvailable() {
        if (this.status != DungeonRewardStatus.CLAIMING || this.deliveryPlan != null) {
            return false;
        }

        return setStatus(this.placedMainPos.isPresent()
                ? DungeonRewardStatus.AVAILABLE
                : DungeonRewardStatus.PLACEMENT_FAILED);
    }

    public boolean recoverOpenedToAvailable() {
        if (this.status != DungeonRewardStatus.OPENED) {
            return false;
        }

        return setStatus(this.placedMainPos.isPresent()
                ? DungeonRewardStatus.AVAILABLE
                : DungeonRewardStatus.PLACEMENT_FAILED);
    }

    public boolean recoverClaimingToOpened() {
        if (this.status != DungeonRewardStatus.CLAIMING || this.deliveryPlan != null) {
            return false;
        }

        return setStatus(DungeonRewardStatus.OPENED);
    }

    public boolean markClaimed() {
        if (this.status == DungeonRewardStatus.CLEANED || this.status == DungeonRewardStatus.EXPIRED) {
            return false;
        }

        if (this.deliveryPlan != null) {
            return false;
        }

        if (this.status != DungeonRewardStatus.OPENED
                && this.status != DungeonRewardStatus.CLAIMING
                && this.status != DungeonRewardStatus.CLAIMED) {
            return false;
        }

        return setStatus(DungeonRewardStatus.CLAIMED);
    }

    public boolean markCleaned() {
        if (this.status == DungeonRewardStatus.EXPIRED) {
            return false;
        }

        return setStatus(DungeonRewardStatus.CLEANED);
    }

    private boolean setStatus(DungeonRewardStatus status) {
        if (this.status == status) {
            return false;
        }

        this.status = status;
        return true;
    }

    private static Optional<BlockPos> immutable(Optional<BlockPos> pos) {
        return pos == null ? Optional.empty() : pos.map(BlockPos::immutable);
    }
}
