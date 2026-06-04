package io.github.naimjeg.obeliskdepths.dungeon.reward;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.block.ObeliskChestBlock;
import io.github.naimjeg.obeliskdepths.block.entity.ObeliskChestBlockEntity;
import io.github.naimjeg.obeliskdepths.dungeon.artifact.DungeonRuntimeArtifactCleanupService;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonStatus;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSession;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSessionAccess;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import io.github.naimjeg.obeliskdepths.registry.ModBlocks;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class DungeonRewardClaim {
    private DungeonRewardClaim() {
    }

    public static DungeonRewardClaimResult tryClaimReward(
            ServerPlayer player,
            DungeonRewardId rewardId,
            DungeonInstanceId instanceId,
            BlockPos interactedMainPos
    ) {
        if (!(player.level() instanceof ServerLevel level)) {
            return DungeonRewardClaimResult.NOT_SERVER_PLAYER;
        }

        if (rewardId == null || instanceId == null || interactedMainPos == null) {
            return DungeonRewardClaimResult.ID_MISMATCH;
        }

        DungeonManagerSavedData data = DungeonManagerSavedData.get(level);
        Optional<DungeonRewardRecord> record = data.rewards().get(rewardId);
        if (record.isEmpty()) {
            return DungeonRewardClaimResult.REWARD_NOT_FOUND;
        }

        DungeonRewardRecord reward = record.get();
        if (!reward.instanceId().equals(instanceId)) {
            return DungeonRewardClaimResult.ID_MISMATCH;
        }

        if (reward.rewardPos().isPresent()
                && reward.rewardPos().filter(interactedMainPos::equals).isEmpty()) {
            return DungeonRewardClaimResult.POSITION_MISMATCH;
        }

        Optional<DungeonInstance> instance = data.instances().get(instanceId);
        if (instance.isEmpty()) {
            return DungeonRewardClaimResult.INSTANCE_NOT_FOUND;
        }

        Optional<DungeonSession> session = data.sessions().findByInstance(instanceId);
        if (session.isEmpty()) {
            return DungeonRewardClaimResult.SESSION_NOT_FOUND;
        }

        if (!DungeonSessionAccess.canAccessSession(player, session.get())) {
            ObeliskDepths.LOGGER.debug(
                    "Dungeon reward access denied: player={}, instance={}, reward={}",
                    player.getUUID(),
                    instanceId,
                    rewardId
            );
            return DungeonRewardClaimResult.ACCESS_DENIED;
        }

        if (instance.get().status() != DungeonStatus.REWARD_PHASE) {
            return DungeonRewardClaimResult.INSTANCE_NOT_IN_REWARD_PHASE;
        }

        DungeonRewardClaimResult stateResult = validateClaimableState(reward);
        if (stateResult != DungeonRewardClaimResult.SUCCESS) {
            return stateResult;
        }

        if (reward.status() == DungeonRewardStatus.PLACEMENT_FAILED
                || (reward.status() == DungeonRewardStatus.CLAIMING
                && reward.hasDeliveryPlan())) {
            return tryClaimVirtualReward(level, data, instance.get(), reward, player.blockPosition());
        }

        Optional<ObeliskChestBlockEntity> chest = DungeonRewardPlacement.resolveOwnedChest(
                level,
                reward,
                interactedMainPos
        );
        if (chest.isEmpty()) {
            return DungeonRewardClaimResult.INVALID_STRUCTURE;
        }

        if (!chest.get().hasPendingRewards()) {
            return DungeonRewardClaimResult.EMPTY_REWARD;
        }

        return startPhysicalRewardSpray(level, data, reward, chest.get(), interactedMainPos, player.getUUID());
    }

    public static boolean completePhysicalRewardSpray(
            ServerLevel level,
            DungeonRewardId rewardId,
            DungeonInstanceId instanceId,
            BlockPos mainPos
    ) {
        if (rewardId == null || instanceId == null || mainPos == null) {
            return false;
        }

        DungeonManagerSavedData data = DungeonManagerSavedData.get(level);
        Optional<DungeonRewardRecord> record = data.rewards().get(rewardId);
        if (record.isEmpty() || !record.get().instanceId().equals(instanceId)) {
            return false;
        }

        DungeonRewardRecord reward = record.get();
        if (reward.status() == DungeonRewardStatus.CLEANED || reward.status() == DungeonRewardStatus.CLAIMED) {
            return true;
        }

        if (reward.rewardPos().isPresent() && reward.rewardPos().filter(mainPos::equals).isEmpty()) {
            return false;
        }

        if (reward.status() != DungeonRewardStatus.OPENED) {
            return false;
        }

        Optional<ObeliskChestBlockEntity> chest = DungeonRewardPlacement.resolveOwnedChest(
                level,
                reward,
                mainPos
        );
        if (chest.isPresent() && chest.get().hasPendingRewards()) {
            return false;
        }

        if (!data.rewards().markClaimed(reward)) {
            return reward.status() == DungeonRewardStatus.CLAIMED
                    || reward.status() == DungeonRewardStatus.CLEANED;
        }
        markRoomRewardClaimed(data, reward);
        cleanupClaimedPhysicalRewardArtifact(level, reward);
        ObeliskDepths.LOGGER.debug(
                "Dungeon reward spray completed: instance={}, reward={}, pos={}",
                reward.instanceId(),
                reward.rewardId(),
                mainPos
        );
        return true;
    }

    public static boolean isPhysicalRewardSprayInProgress(
            ServerLevel level,
            DungeonRewardId rewardId,
            DungeonInstanceId instanceId
    ) {
        if (level == null || rewardId == null || instanceId == null) {
            return false;
        }

        return DungeonManagerSavedData.get(level)
                .rewards()
                .get(rewardId)
                .filter(reward -> reward.instanceId().equals(instanceId))
                .map(reward -> reward.status() == DungeonRewardStatus.OPENED)
                .orElse(false);
    }

    public static DungeonRewardClaimResult tryOpenReward(
            ServerPlayer player,
            BlockPos rewardPos,
            ObeliskChestBlockEntity chest
    ) {
        if (chest == null || !chest.isInitialized()) {
            return DungeonRewardClaimResult.CHEST_NOT_INITIALIZED;
        }

        Optional<DungeonRewardId> rewardId = chest.rewardId();
        Optional<DungeonInstanceId> instanceId = chest.instanceId();
        if (rewardId.isEmpty() || instanceId.isEmpty()) {
            return DungeonRewardClaimResult.ID_MISMATCH;
        }

        return tryClaimReward(player, rewardId.get(), instanceId.get(), rewardPos);
    }

    public static DungeonRewardClaimResult tryOpenReward(
            ServerPlayer player,
            DungeonInstanceId instanceId,
            BlockPos rewardPos
    ) {
        if (!(player.level() instanceof ServerLevel level)) {
            return DungeonRewardClaimResult.NOT_SERVER_PLAYER;
        }

        if (level.getBlockEntity(rewardPos) instanceof ObeliskChestBlockEntity chest
                && chest.instanceId().filter(instanceId::equals).isPresent()) {
            return tryOpenReward(player, rewardPos, chest);
        }

        return DungeonRewardClaimResult.CHEST_NOT_INITIALIZED;
    }

    public static DungeonRewardClaimResult validateRewardClaimable(
            ServerLevel level,
            DungeonInstanceId instanceId,
            DungeonRoomId roomId
    ) {
        Optional<DungeonRewardRecord> reward = DungeonManagerSavedData.get(level)
                .rewards()
                .findByInstance(instanceId)
                .filter(record -> record.roomId().map(roomId::equals).orElse(false));
        return reward.map(DungeonRewardClaim::validateClaimableState)
                .orElse(DungeonRewardClaimResult.REWARD_NOT_FOUND);
    }

    static void claimPlacementFailureFallback(
            ServerLevel level,
            DungeonManagerSavedData data,
            DungeonRewardRecord reward,
            Optional<BlockPos> fallbackDeliveryPos
    ) {
        Optional<DungeonInstance> instance = data.instances().get(reward.instanceId());
        if (instance.isEmpty()) {
            return;
        }

        DungeonRewardClaimResult result = tryClaimVirtualReward(
                level,
                data,
                instance.get(),
                reward,
                fallbackDeliveryPos
                        .or(() -> reward.preferredPlacementOrigin())
                        .orElse(instance.get().startPos())
        );
        if (result == DungeonRewardClaimResult.SUCCESS) {
            ObeliskDepths.LOGGER.warn(
                    "Delivered dungeon reward directly after bounded placement failure: instance={}, reward={}, status={}",
                    reward.instanceId(),
                    reward.rewardId(),
                    reward.status()
            );
        } else {
            ObeliskDepths.LOGGER.warn(
                    "Direct reward delivery after placement failure did not complete: instance={}, reward={}, result={}",
                    reward.instanceId(),
                    reward.rewardId(),
                    result
            );
        }
    }

    private static DungeonRewardClaimResult startPhysicalRewardSpray(
            ServerLevel level,
            DungeonManagerSavedData data,
            DungeonRewardRecord reward,
            ObeliskChestBlockEntity chest,
            BlockPos mainPos,
            UUID playerId
    ) {
        /*
         * Physical claim order:
         * 1. Persist OPENED to block duplicate claim starts.
         * 2. Open the multipart chest and start its persisted spray state.
         * 3. If world mutation fails, close/recover back to AVAILABLE.
         * 4. The block entity completes the claim after all persisted stacks
         *    have been spawned.
         */
        if (!data.rewards().markOpened(reward)) {
            return reward.status() == DungeonRewardStatus.OPENED
                    ? DungeonRewardClaimResult.ALREADY_OPENED
                    : DungeonRewardClaimResult.ROOM_NOT_CLEARED;
        }

        ObeliskChestBlock chestBlock = ModBlocks.OBELISK_CHEST.get();
        try {
            if (!chestBlock.setOpened(level, mainPos, true) || !chest.beginSpraying()) {
                chestBlock.setOpened(level, mainPos, false);
                data.rewards().recoverOpenedToAvailable(reward);
                return DungeonRewardClaimResult.INVALID_STRUCTURE;
            }

            ObeliskDepths.LOGGER.debug(
                    "Dungeon reward spray started: player={}, instance={}, reward={}, pendingStacks={}",
                    playerId,
                    reward.instanceId(),
                    reward.rewardId(),
                    chest.copyContents().size()
            );
            return DungeonRewardClaimResult.SUCCESS;
        } catch (RuntimeException exception) {
            chestBlock.setOpened(level, mainPos, false);
            data.rewards().recoverOpenedToAvailable(reward);
            ObeliskDepths.LOGGER.warn(
                    "Dungeon reward spray failed to start: instance={}, reward={}",
                    reward.instanceId(),
                    reward.rewardId(),
                    exception
            );
            return DungeonRewardClaimResult.INVALID_STRUCTURE;
        }
    }

    private static DungeonRewardClaimResult tryClaimVirtualReward(
            ServerLevel level,
            DungeonManagerSavedData data,
            DungeonInstance instance,
            DungeonRewardRecord reward,
            BlockPos deliveryPos
    ) {
        /*
         * Virtual claim order:
         * 1. Persist CLAIMING to block duplicate direct delivery.
         * 2. Persist an ordinal delivery plan.
         * 3. Spawn only the current missing ordinal, then advance the plan.
         * 4. Commit CLAIMED only after every ordinal has been advanced.
         */
        if (reward.status() != DungeonRewardStatus.CLAIMING
                && !data.rewards().markClaiming(reward)) {
            return DungeonRewardClaimResult.ROOM_NOT_CLEARED;
        }

        boolean deliveryPlanStarted = reward.hasDeliveryPlan();
        try {
            if (!reward.hasDeliveryPlan()) {
                List<ItemStack> stacks = nonEmptyCopies(DungeonRewardDelivery.generate(
                        level,
                        instance,
                        reward
                ));
                if (stacks.isEmpty()) {
                    data.rewards().recoverClaimingToAvailable(reward);
                    return DungeonRewardClaimResult.EMPTY_REWARD;
                }

                if (!data.rewards().beginDeliveryPlan(reward, stacks)) {
                    return DungeonRewardClaimResult.ROOM_NOT_CLEARED;
                }
                deliveryPlanStarted = true;
            }

            BlockPos resolvedDeliveryPos = resolveVirtualDeliveryPos(
                    instance,
                    reward,
                    deliveryPos
            );
            deliverVirtualRewardPlan(level, data, reward, resolvedDeliveryPos);
            ObeliskDepths.LOGGER.debug(
                    "Dungeon virtual reward claimed: instance={}, reward={}, status={}",
                    reward.instanceId(),
                    reward.rewardId(),
                    reward.status()
            );
            return DungeonRewardClaimResult.SUCCESS;
        } catch (RuntimeException exception) {
            if (!deliveryPlanStarted) {
                data.rewards().recoverClaimingToAvailable(reward);
            }
            ObeliskDepths.LOGGER.warn(
                    "Dungeon virtual reward claim failed before completion: instance={}, reward={}, status={}",
                    reward.instanceId(),
                    reward.rewardId(),
                    reward.status(),
                    exception
            );
            return DungeonRewardClaimResult.INVALID_STRUCTURE;
        }
    }

    private static DungeonRewardClaimResult validateClaimableState(DungeonRewardRecord reward) {
        if (reward.status() == DungeonRewardStatus.CLAIMED
                || reward.status() == DungeonRewardStatus.CLEANED) {
            return DungeonRewardClaimResult.ALREADY_CLAIMED;
        }

        if (reward.status() == DungeonRewardStatus.CLAIMING && reward.hasDeliveryPlan()) {
            return DungeonRewardClaimResult.SUCCESS;
        }

        if (reward.status() == DungeonRewardStatus.OPENED
                || reward.status() == DungeonRewardStatus.CLAIMING) {
            return DungeonRewardClaimResult.ALREADY_OPENED;
        }

        if (reward.status() == DungeonRewardStatus.EXPIRED) {
            return DungeonRewardClaimResult.ROOM_NOT_CLEARED;
        }

        return reward.status().claimable()
                ? DungeonRewardClaimResult.SUCCESS
                : DungeonRewardClaimResult.ROOM_NOT_CLEARED;
    }

    private static List<ItemStack> nonEmptyCopies(List<ItemStack> stacks) {
        return stacks.stream()
                .filter(stack -> stack != null && !stack.isEmpty())
                .map(ItemStack::copy)
                .toList();
    }

    private static BlockPos resolveVirtualDeliveryPos(
            DungeonInstance instance,
            DungeonRewardRecord reward,
            BlockPos requestedDeliveryPos
    ) {
        return reward.placedMainPos()
                .or(() -> reward.preferredPlacementOrigin())
                .orElse(requestedDeliveryPos == null ? instance.startPos() : requestedDeliveryPos)
                .immutable();
    }

    private static void deliverVirtualRewardPlan(
            ServerLevel level,
            DungeonManagerSavedData data,
            DungeonRewardRecord reward,
            BlockPos deliveryPos
    ) {
        while (true) {
            RewardDeliveryPlan plan = reward.deliveryPlan()
                    .orElseThrow(() -> new IllegalStateException(
                            "Missing reward delivery plan: " + reward.rewardId()
                    ));
            if (plan.complete()) {
                finishVirtualRewardDelivery(data, reward);
                return;
            }

            int ordinal = plan.nextOrdinal();
            if (!DungeonRewardDelivery.hasSpawnedOrdinal(level, reward, ordinal, deliveryPos)) {
                DungeonRewardDelivery.spawnRewardOrdinalOrThrow(
                        level,
                        reward,
                        deliveryPos,
                        plan.currentStack(),
                        ordinal
                );
            }

            if (!data.rewards().advanceDeliveryPlan(reward)) {
                throw new IllegalStateException(
                        "Failed to advance reward delivery plan: "
                                + reward.rewardId()
                                + " ordinal="
                                + ordinal
                );
            }
        }
    }

    private static void finishVirtualRewardDelivery(
            DungeonManagerSavedData data,
            DungeonRewardRecord reward
    ) {
        if (!data.rewards().clearDeliveryPlan(reward) && reward.hasDeliveryPlan()) {
            throw new IllegalStateException(
                    "Failed to clear reward delivery plan: " + reward.rewardId()
            );
        }

        if (!data.rewards().markClaimed(reward)
                && reward.status() != DungeonRewardStatus.CLAIMED) {
            throw new IllegalStateException(
                    "Failed to mark reward claimed after delivery: " + reward.rewardId()
            );
        }

        markRoomRewardClaimed(data, reward);
    }

    static void markRoomRewardClaimed(
            DungeonManagerSavedData data,
            DungeonRewardRecord reward
    ) {
        reward.roomId().ifPresent(roomId ->
                data.roomStates().markRewardClaimed(reward.instanceId(), roomId)
        );
    }

    private static void cleanupClaimedPhysicalRewardArtifact(
            ServerLevel level,
            DungeonRewardRecord reward
    ) {
        try {
            DungeonRuntimeArtifactCleanupService.cleanupRewardArtifact(
                    level,
                    reward.instanceId(),
                    reward.rewardId()
            );
        } catch (RuntimeException exception) {
            ObeliskDepths.LOGGER.warn(
                    "Dungeon reward claimed but chest artifact cleanup failed: instance={}, reward={}",
                    reward.instanceId(),
                    reward.rewardId(),
                    exception
            );
        }
    }
}
