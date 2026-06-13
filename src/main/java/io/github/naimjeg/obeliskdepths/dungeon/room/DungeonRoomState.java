package io.github.naimjeg.obeliskdepths.dungeon.room;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;

public final class DungeonRoomState {
    public static final Codec<DungeonRoomState> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    DungeonInstanceId.CODEC.fieldOf("instance_id")
                            .forGetter(DungeonRoomState::instanceId),
                    DungeonRoomId.CODEC.fieldOf("room_id")
                            .forGetter(DungeonRoomState::roomId),
                    DungeonRoomType.CODEC.fieldOf("type")
                            .forGetter(DungeonRoomState::type),
                    DungeonRoomStatus.CODEC
                            .optionalFieldOf("status", DungeonRoomStatus.UNDISCOVERED)
                            .forGetter(DungeonRoomState::status),
                    Codec.BOOL.optionalFieldOf("reward_claimed", false)
                            .forGetter(DungeonRoomState::rewardClaimed)
            ).apply(instance, DungeonRoomState::new));

    private final DungeonInstanceId instanceId;
    private final DungeonRoomId roomId;
    private final DungeonRoomType type;
    private DungeonRoomStatus status;
    private boolean rewardClaimed;

    public DungeonRoomState(
            DungeonInstanceId instanceId,
            DungeonRoomId roomId,
            DungeonRoomType type,
            DungeonRoomStatus status,
            boolean rewardClaimed
    ) {
        this.instanceId = instanceId;
        this.roomId = roomId;
        this.type = type;
        this.status = status;
        this.rewardClaimed = rewardClaimed;
    }

    public static DungeonRoomState initial(
            DungeonInstanceId instanceId,
            DungeonRoomType type,
            DungeonRoomId roomId
    ) {
        DungeonRoomStatus initialStatus = switch (type) {
            case START -> DungeonRoomStatus.DISCOVERED;
            case BOSS -> DungeonRoomStatus.LOCKED;
            default -> DungeonRoomStatus.UNDISCOVERED;
        };

        return new DungeonRoomState(
                instanceId,
                roomId,
                type,
                initialStatus,
                false
        );
    }

    public DungeonInstanceId instanceId() {
        return this.instanceId;
    }

    public DungeonRoomId roomId() {
        return this.roomId;
    }

    public DungeonRoomType type() {
        return this.type;
    }

    public DungeonRoomStatus status() {
        return this.status;
    }

    public boolean rewardClaimed() {
        return this.rewardClaimed;
    }

    public boolean setStatus(DungeonRoomStatus status) {
        if (this.status == status) {
            return false;
        }

        this.status = status;
        return true;
    }

    public boolean markRewardClaimed() {
        if (this.rewardClaimed) {
            return false;
        }

        this.rewardClaimed = true;
        return true;
    }
}
