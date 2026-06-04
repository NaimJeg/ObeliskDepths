package io.github.naimjeg.obeliskdepths.dungeon.state.store;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.reward.DungeonRewardId;
import io.github.naimjeg.obeliskdepths.dungeon.reward.DungeonRewardRecord;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

public final class DungeonRewardStore {
    private final Map<DungeonRewardId, DungeonRewardRecord> rewards = new HashMap<>();
    private final Map<DungeonInstanceId, DungeonRewardId> rewardIdByInstance =
            new HashMap<>();
    private final Map<DungeonRoomId, Set<DungeonRewardId>> rewardIdsByRoom =
            new HashMap<>();
    private final Map<BlockPos, Set<DungeonRewardId>> rewardIdsByPosition =
            new HashMap<>();
    private final Runnable dirty;

    public DungeonRewardStore(Runnable dirty) {
        this.dirty = dirty;
    }

    public void load(Collection<DungeonRewardRecord> rewards) {
        for (DungeonRewardRecord reward : rewards) {
            this.putLoaded(reward);
        }
    }

    public boolean beginDeliveryPlan(
            DungeonRewardRecord reward,
            List<ItemStack> stacks
    ) {
        DungeonRewardRecord stored = this.requireStored(reward);

        if (!stored.beginDeliveryPlan(stacks)) {
            return false;
        }

        this.dirty.run();
        return true;
    }

    public boolean advanceDeliveryPlan(DungeonRewardRecord reward) {
        DungeonRewardRecord stored = this.requireStored(reward);

        if (!stored.advanceDeliveryPlan()) {
            return false;
        }

        this.dirty.run();
        return true;
    }

    public boolean clearDeliveryPlan(DungeonRewardRecord reward) {
        DungeonRewardRecord stored = this.requireStored(reward);

        if (!stored.clearDeliveryPlan()) {
            return false;
        }

        this.dirty.run();
        return true;
    }

    private DungeonRewardRecord requireStored(DungeonRewardRecord reward) {
        DungeonRewardRecord stored = this.rewards.get(reward.rewardId());
        if (stored == null || stored != reward) {
            throw new IllegalArgumentException(
                    "Reward record is not owned by this store: "
                            + reward.rewardId()
            );
        }

        return stored;
    }

    public Collection<DungeonRewardRecord> all() {
        return List.copyOf(this.rewards.values());
    }

    public DungeonRewardRecord add(DungeonRewardRecord reward) {
        DungeonRewardRecord previous = this.rewards.get(reward.rewardId());
        if (previous == reward) {
            return reward;
        }

        if (previous != null) {
            throw new IllegalStateException(
                    "Duplicate dungeon reward id: " + reward.rewardId()
            );
        }

        this.validateUniqueInstanceReward(reward);
        this.rewards.put(reward.rewardId(), reward);
        this.index(reward);
        this.dirty.run();
        return reward;
    }

    public Optional<DungeonRewardRecord> get(DungeonRewardId rewardId) {
        return Optional.ofNullable(this.rewards.get(rewardId));
    }

    public boolean remove(DungeonRewardId rewardId) {
        DungeonRewardRecord removed = this.rewards.remove(rewardId);

        if (removed == null) {
            return false;
        }

        this.unindex(removed);
        this.dirty.run();
        return true;
    }

    public Optional<DungeonRewardRecord> findByInstance(DungeonInstanceId instanceId) {
        return Optional.ofNullable(this.rewardIdByInstance.get(instanceId))
                .map(this.rewards::get);
    }

    public Optional<DungeonRewardRecord> findAt(
            DungeonInstanceId instanceId,
            BlockPos pos
    ) {
        Set<DungeonRewardId> ids = this.rewardIdsByPosition.get(pos.immutable());

        if (ids == null || ids.isEmpty()) {
            return Optional.empty();
        }

        for (DungeonRewardId id : ids) {
            DungeonRewardRecord reward = this.rewards.get(id);

            if (reward != null && reward.instanceId().equals(instanceId)) {
                return Optional.of(reward);
            }
        }

        return Optional.empty();
    }

    public boolean markPlacementPending(DungeonRewardRecord reward) {
        DungeonRewardRecord stored = this.requireStored(reward);
        return this.mark(stored.markPlacementPending());
    }

    public boolean markAvailable(
            DungeonRewardRecord reward,
            BlockPos pos
    ) {
        DungeonRewardRecord stored = this.requireStored(reward);
        Optional<BlockPos> previousPos = stored.rewardPos();
        boolean changed = stored.markAvailable(pos);
        if (!changed) {
            return false;
        }

        previousPos.ifPresent(previous ->
                this.removeIndexValue(
                        this.rewardIdsByPosition,
                        previous.immutable(),
                        stored.rewardId()
                )
        );
        this.indexPosition(stored);
        this.dirty.run();
        return true;
    }

    public boolean recordPlacementFailure(DungeonRewardRecord reward) {
        DungeonRewardRecord stored = this.requireStored(reward);
        return this.mark(stored.recordPlacementFailure());
    }

    public boolean markPlacementFailed(DungeonRewardRecord reward) {
        DungeonRewardRecord stored = this.requireStored(reward);
        return this.mark(stored.markPlacementFailed());
    }

    public boolean markClaiming(DungeonRewardRecord reward) {
        DungeonRewardRecord stored = this.requireStored(reward);
        return this.mark(stored.markClaiming());
    }

    public boolean markOpened(DungeonRewardRecord reward) {
        DungeonRewardRecord stored = this.requireStored(reward);
        return this.mark(stored.markOpened());
    }

    public boolean recoverOpenedToAvailable(DungeonRewardRecord reward) {
        DungeonRewardRecord stored = this.requireStored(reward);
        return this.mark(stored.recoverOpenedToAvailable());
    }

    public boolean recoverClaimingToAvailable(DungeonRewardRecord reward) {
        DungeonRewardRecord stored = this.requireStored(reward);
        return this.mark(stored.recoverClaimingToAvailable());
    }

    public boolean recoverClaimingToOpened(DungeonRewardRecord reward) {
        DungeonRewardRecord stored = this.requireStored(reward);
        return this.mark(stored.recoverClaimingToOpened());
    }

    public boolean markClaimed(DungeonRewardRecord reward) {
        DungeonRewardRecord stored = this.requireStored(reward);
        return this.mark(stored.markClaimed());
    }

    public boolean markCleaned(DungeonRewardRecord reward) {
        DungeonRewardRecord stored = this.requireStored(reward);
        return this.mark(stored.markCleaned());
    }

    private void putLoaded(DungeonRewardRecord reward) {
        if (this.rewards.containsKey(reward.rewardId())) {
            throw new IllegalStateException(
                    "Duplicate dungeon reward id in saved data: " + reward.rewardId()
            );
        }

        this.validateUniqueInstanceReward(reward);
        this.rewards.put(reward.rewardId(), reward);
        this.index(reward);
    }

    private void validateUniqueInstanceReward(DungeonRewardRecord reward) {
        DungeonRewardId existingRewardId = this.rewardIdByInstance.get(reward.instanceId());
        if (existingRewardId != null && !existingRewardId.equals(reward.rewardId())) {
            throw new IllegalStateException(
                    "Dungeon instance already has a reward: "
                            + reward.instanceId()
                            + " existing="
                            + existingRewardId
                            + " new="
                            + reward.rewardId()
            );
        }
    }

    private boolean mark(boolean changed) {
        if (changed) {
            this.dirty.run();
        }

        return changed;
    }

    private void index(DungeonRewardRecord reward) {
        this.rewardIdByInstance.put(reward.instanceId(), reward.rewardId());
        reward.roomId().ifPresent(roomId ->
                this.addIndexValue(this.rewardIdsByRoom, roomId, reward.rewardId())
        );
        this.indexPosition(reward);
    }

    private void unindex(DungeonRewardRecord reward) {
        this.rewardIdByInstance.remove(reward.instanceId(), reward.rewardId());
        reward.roomId().ifPresent(roomId ->
                this.removeIndexValue(this.rewardIdsByRoom, roomId, reward.rewardId())
        );
        this.unindexPosition(reward);
    }

    private void indexPosition(DungeonRewardRecord reward) {
        reward.rewardPos().ifPresent(pos ->
                this.addIndexValue(this.rewardIdsByPosition, pos.immutable(), reward.rewardId())
        );
    }

    private void unindexPosition(DungeonRewardRecord reward) {
        reward.rewardPos().ifPresent(pos ->
                this.removeIndexValue(this.rewardIdsByPosition, pos.immutable(), reward.rewardId())
        );
    }

    private <K> void addIndexValue(
            Map<K, Set<DungeonRewardId>> index,
            K key,
            DungeonRewardId rewardId
    ) {
        index.computeIfAbsent(key, ignored -> new HashSet<>()).add(rewardId);
    }

    private <K> void removeIndexValue(
            Map<K, Set<DungeonRewardId>> index,
            K key,
            DungeonRewardId rewardId
    ) {
        Set<DungeonRewardId> values = index.get(key);

        if (values == null) {
            return;
        }

        values.remove(rewardId);

        if (values.isEmpty()) {
            index.remove(key);
        }
    }
}
