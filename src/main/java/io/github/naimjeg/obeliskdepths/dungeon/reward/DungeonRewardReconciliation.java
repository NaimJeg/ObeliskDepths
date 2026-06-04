package io.github.naimjeg.obeliskdepths.dungeon.reward;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.block.entity.ObeliskChestBlockEntity;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import io.github.naimjeg.obeliskdepths.registry.ModBlocks;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class DungeonRewardReconciliation {
    private DungeonRewardReconciliation() {
    }

    public static void recoverOpenedPhysicalReward(
            ServerLevel level,
            DungeonRewardId rewardId,
            DungeonInstanceId instanceId,
            BlockPos mainPos,
            String reason
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(level);
        Optional<DungeonRewardRecord> record = data.rewards().get(rewardId);
        if (record.isEmpty() || !record.get().instanceId().equals(instanceId)) {
            return;
        }

        DungeonRewardRecord reward = record.get();
        if (reward.status() != DungeonRewardStatus.OPENED) {
            return;
        }

        Optional<ObeliskChestBlockEntity> chest = DungeonRewardPlacement.resolveOwnedChest(
                level,
                reward,
                mainPos
        );
        if (chest.isPresent()) {
            ModBlocks.OBELISK_CHEST.get().setOpened(level, mainPos, true);
            if (chest.get().hasPendingRewards()) {
                chest.get().ensureSpraying();
            } else {
                DungeonRewardClaim.completePhysicalRewardSpray(
                        level,
                        rewardId,
                        instanceId,
                        mainPos
                );
            }
            return;
        }

        ObeliskDepths.LOGGER.warn(
                "Unable to recover opened dungeon reward conservatively: instance={}, reward={}, pos={}, reason={}",
                instanceId,
                rewardId,
                mainPos,
                reason
        );
    }

    public static void reconcileClaimingRewards(ServerLevel level) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(level);
        for (DungeonRewardRecord reward : data.rewards().all()) {
            if (reward.status() == DungeonRewardStatus.OPENED) {
                if (reward.placedMainPos().isPresent()) {
                    recoverOpenedPhysicalReward(
                            level,
                            reward.rewardId(),
                            reward.instanceId(),
                            reward.placedMainPos().get(),
                            "startup opened reward reconciliation"
                    );
                } else {
                    ObeliskDepths.LOGGER.warn(
                            "Opened dungeon reward has no physical position for reconciliation: instance={}, reward={}",
                            reward.instanceId(),
                            reward.rewardId()
                    );
                }
                continue;
            }

            if (reward.status() != DungeonRewardStatus.CLAIMING) {
                continue;
            }

            if (reward.hasDeliveryPlan()) {
                reconcileDeliveryPlan(level, data, reward);
                continue;
            }

            ObeliskDepths.LOGGER.warn(
                    "Claiming dungeon reward has no delivery plan and will remain in claiming state: instance={}, reward={}",
                    reward.instanceId(),
                    reward.rewardId()
            );
        }
    }

    private static void reconcileDeliveryPlan(
            ServerLevel level,
            DungeonManagerSavedData data,
            DungeonRewardRecord reward
    ) {
        BlockPos center = deliveryCenter(data, reward);
        while (true) {
            Optional<RewardDeliveryPlan> currentPlan = reward.deliveryPlan();
            if (currentPlan.isEmpty()) {
                return;
            }

            RewardDeliveryPlan plan = currentPlan.get();
            if (plan.complete()) {
                data.rewards().clearDeliveryPlan(reward);
                if (data.rewards().markClaimed(reward)
                        || reward.status() == DungeonRewardStatus.CLAIMED) {
                    DungeonRewardClaim.markRoomRewardClaimed(data, reward);
                }
                ObeliskDepths.LOGGER.warn(
                        "Completed dungeon reward claim reconciliation after all ordinals advanced: instance={}, reward={}",
                        reward.instanceId(),
                        reward.rewardId()
                );
                return;
            }

            int ordinal = plan.nextOrdinal();
            if (!DungeonRewardDelivery.hasSpawnedOrdinal(level, reward, ordinal, center)) {
                ObeliskDepths.LOGGER.warn(
                        "Dungeon reward claim reconciliation paused at missing ordinal: instance={}, reward={}, ordinal={}",
                        reward.instanceId(),
                        reward.rewardId(),
                        ordinal
                );
                return;
            }

            data.rewards().advanceDeliveryPlan(reward);
        }
    }

    private static BlockPos deliveryCenter(
            DungeonManagerSavedData data,
            DungeonRewardRecord reward
    ) {
        return reward.placedMainPos()
                .or(() -> reward.preferredPlacementOrigin())
                .or(() -> data.instances().get(reward.instanceId()).map(instance -> instance.startPos()))
                .orElse(BlockPos.ZERO);
    }
}
