package io.github.naimjeg.obeliskdepths.dungeon.reward;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomState;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomStatus;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class DungeonRewardLifecycle {
    private DungeonRewardLifecycle() {
    }

    public static boolean isRewardEligible(DungeonRoomState room) {
        return canCreateReward(room);
    }

    public static boolean canCreateReward(DungeonRoomState room) {
        return room.status() == DungeonRoomStatus.CLEARED
                && !room.rewardClaimed()
                && isRewardRoomType(room.type());
    }

    public static DungeonRewardRecord onBossDefeated(
            ServerLevel level,
            DungeonInstanceId instanceId
    ) {
        return onBossDefeated(level, instanceId, Optional.empty());
    }

    public static DungeonRewardRecord onBossDefeated(
            ServerLevel level,
            DungeonInstanceId instanceId,
            Optional<BlockPos> preferredPlacementOrigin
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(level);
        Optional<DungeonRewardRecord> existing = data.rewards().findByInstance(instanceId);
        if (existing.isPresent()) {
            ObeliskDepths.LOGGER.debug(
                    "Reusing dungeon reward record: instance={}, reward={}, status={}",
                    instanceId,
                    existing.get().rewardId(),
                    existing.get().status()
            );
            return existing.get();
        }

        Optional<DungeonRoomId> bossRoomId = data.roomStates().allForInstance(instanceId)
                .stream()
                .filter(room -> room.type() == DungeonRoomType.BOSS)
                .map(DungeonRoomState::roomId)
                .findFirst();
        DungeonRewardRecord reward = DungeonRewardRecord.bossDefeated(
                instanceId,
                bossRoomId,
                preferredPlacementOrigin,
                level.getGameTime()
        );
        data.rewards().add(reward);
        ObeliskDepths.LOGGER.debug(
                "Created dungeon reward record: instance={}, reward={}, room={}",
                instanceId,
                reward.rewardId(),
                reward.roomId()
        );
        return reward;
    }

    private static boolean isRewardRoomType(DungeonRoomType type) {
        return type == DungeonRoomType.BOSS
                || type == DungeonRoomType.TREASURE;
    }
}
