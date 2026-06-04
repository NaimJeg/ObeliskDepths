package io.github.naimjeg.obeliskdepths.block.entity;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.block.ObeliskChestBlock;
import io.github.naimjeg.obeliskdepths.block.ObeliskChestPart;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;
import io.github.naimjeg.obeliskdepths.dungeon.reward.DungeonRewardId;
import io.github.naimjeg.obeliskdepths.dungeon.reward.DungeonRewardClaim;
import io.github.naimjeg.obeliskdepths.dungeon.reward.DungeonRewardDelivery;
import io.github.naimjeg.obeliskdepths.dungeon.reward.DungeonRewardReconciliation;
import io.github.naimjeg.obeliskdepths.registry.ModBlockEntities;
import io.github.naimjeg.obeliskdepths.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class ObeliskChestBlockEntity extends BlockEntity {
    // Holds every generated scalable roll plus guaranteed non-scalable reward entries without stack merging.
    public static final int REWARD_CAPACITY = 32;
    public static final int OPENING_DELAY_TICKS = 10;
    public static final int SPRAY_INTERVAL_TICKS = 8;
    public static final int POST_SPRAY_CLEANUP_TICKS = 20;

    private static final int NO_POST_SPRAY_CLEANUP = -1;

    private final NonNullList<ItemStack> contents = NonNullList.withSize(REWARD_CAPACITY, ItemStack.EMPTY);
    private Optional<DungeonRewardId> rewardId = Optional.empty();
    private Optional<DungeonInstanceId> instanceId = Optional.empty();
    private Optional<DungeonRoomId> roomId = Optional.empty();
    private long rewardSeed;
    private long createdGameTime;
    private boolean initialized;
    private boolean spraying;
    private int ticksUntilNextSpray;
    private int postSprayCleanupTicks = NO_POST_SPRAY_CLEANUP;
    private int nextSprayOrdinal;

    public ObeliskChestBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OBELISK_CHEST.get(), pos, state);
    }

    public boolean initializeReward(
            DungeonRewardId rewardId,
            DungeonInstanceId instanceId,
            Optional<DungeonRoomId> roomId,
            long rewardSeed,
            long createdGameTime,
            List<ItemStack> contents
    ) {
        if (rewardId == null || instanceId == null || this.initialized || contents == null
                || contents.size() > REWARD_CAPACITY) {
            return false;
        }

        this.clearContents();
        int slot = 0;
        for (ItemStack stack : contents) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            if (slot >= REWARD_CAPACITY) {
                this.clearContents();
                return false;
            }

            this.contents.set(slot++, stack.copy());
        }

        this.rewardId = Optional.of(rewardId);
        this.instanceId = Optional.of(instanceId);
        this.roomId = roomId == null ? Optional.empty() : roomId;
        this.rewardSeed = rewardSeed;
        this.createdGameTime = createdGameTime;
        this.initialized = true;
        this.spraying = false;
        this.ticksUntilNextSpray = 0;
        this.postSprayCleanupTicks = NO_POST_SPRAY_CLEANUP;
        this.nextSprayOrdinal = 0;
        this.setChanged();
        return true;
    }

    public boolean isInitialized() {
        return this.initialized;
    }

    public Optional<DungeonRewardId> rewardId() {
        return this.rewardId;
    }

    public Optional<DungeonInstanceId> instanceId() {
        return this.instanceId;
    }

    public Optional<DungeonRoomId> roomId() {
        return this.roomId;
    }

    public long rewardSeed() {
        return this.rewardSeed;
    }

    public long createdGameTime() {
        return this.createdGameTime;
    }

    public boolean beginSpraying() {
        if (!this.initialized || !this.hasPendingRewards()) {
            return false;
        }

        if (!this.spraying) {
            this.spraying = true;
            this.ticksUntilNextSpray = OPENING_DELAY_TICKS;
            this.postSprayCleanupTicks = NO_POST_SPRAY_CLEANUP;
            this.setChanged();
        }

        return true;
    }

    public boolean ensureSpraying() {
        if (!this.initialized || this.spraying || !this.hasPendingRewards()) {
            return false;
        }

        this.spraying = true;
        if (this.ticksUntilNextSpray <= 0 && this.postSprayCleanupTicks == NO_POST_SPRAY_CLEANUP) {
            this.ticksUntilNextSpray = OPENING_DELAY_TICKS;
        }
        this.postSprayCleanupTicks = NO_POST_SPRAY_CLEANUP;
        this.setChanged();
        return true;
    }

    public boolean isSpraying() {
        return this.spraying;
    }

    public int ticksUntilNextSpray() {
        return this.ticksUntilNextSpray;
    }

    public int postSprayCleanupTicks() {
        return this.postSprayCleanupTicks;
    }

    public int nextSprayOrdinal() {
        return this.nextSprayOrdinal;
    }

    public boolean matchesReward(
            DungeonRewardId rewardId,
            DungeonInstanceId instanceId
    ) {
        return this.initialized
                && this.rewardId.map(rewardId::equals).orElse(false)
                && this.instanceId.map(instanceId::equals).orElse(false);
    }

    public boolean matchesRewardRecord(
            DungeonRewardId rewardId,
            DungeonInstanceId instanceId,
            Optional<DungeonRoomId> roomId,
            long rewardSeed
    ) {
        return this.matchesReward(rewardId, instanceId)
                && this.roomId.equals(roomId == null ? Optional.empty() : roomId)
                && this.rewardSeed == rewardSeed;
    }

    public List<ItemStack> copyContents() {
        List<ItemStack> copy = new ArrayList<>();
        for (ItemStack stack : this.contents) {
            if (!stack.isEmpty()) {
                copy.add(stack.copy());
            }
        }

        return List.copyOf(copy);
    }

    public boolean hasPendingRewards() {
        for (ItemStack stack : this.contents) {
            if (!stack.isEmpty()) {
                return true;
            }
        }

        return false;
    }

    public Optional<ItemStack> peekNextPendingStack() {
        for (ItemStack stack : this.contents) {
            if (!stack.isEmpty()) {
                return Optional.of(stack.copy());
            }
        }

        return Optional.empty();
    }

    public boolean removeNextPendingStack() {
        for (int i = 0; i < this.contents.size(); i++) {
            if (!this.contents.get(i).isEmpty()) {
                this.contents.set(i, ItemStack.EMPTY);
                compactContents();
                this.setChanged();
                return true;
            }
        }

        return false;
    }

    public List<ItemStack> takeAllContents() {
        List<ItemStack> taken = this.copyContents();
        this.clearContents();
        this.setChanged();
        return taken;
    }

    public void restoreContents(List<ItemStack> contents) {
        this.clearContents();
        int slot = 0;
        for (ItemStack stack : contents) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }

            if (slot >= REWARD_CAPACITY) {
                break;
            }

            this.contents.set(slot++, stack.copy());
        }

        this.setChanged();
    }

    public boolean isEmpty() {
        return this.copyContents().isEmpty();
    }

    private void clearContents() {
        for (int i = 0; i < this.contents.size(); i++) {
            this.contents.set(i, ItemStack.EMPTY);
        }
    }

    private void compactContents() {
        int target = 0;
        for (int source = 0; source < this.contents.size(); source++) {
            ItemStack stack = this.contents.get(source);
            if (stack.isEmpty()) {
                continue;
            }

            if (source != target) {
                this.contents.set(target, stack);
                this.contents.set(source, ItemStack.EMPTY);
            }
            target++;
        }
    }

    public static void serverTick(
            Level level,
            BlockPos pos,
            BlockState state,
            ObeliskChestBlockEntity chest
    ) {
        if (!(level instanceof ServerLevel serverLevel)
                || chest == null
                || !state.is(ModBlocks.OBELISK_CHEST.get())
                || !state.hasProperty(ObeliskChestBlock.PART)
                || state.getValue(ObeliskChestBlock.PART) != ObeliskChestPart.BOTTOM_FRONT_LEFT
                || !chest.isInitialized()
                || chest.rewardId.isEmpty()
                || chest.instanceId.isEmpty()) {
            return;
        }

        if (!DungeonRewardClaim.isPhysicalRewardSprayInProgress(
                serverLevel,
                chest.rewardId.get(),
                chest.instanceId.get()
        )) {
            if (chest.spraying) {
                chest.spraying = false;
                chest.setChanged();
            }
            return;
        }

        ObeliskChestBlock chestBlock = ModBlocks.OBELISK_CHEST.get();
        boolean openedState = state.hasProperty(ObeliskChestBlock.OPENED)
                && state.getValue(ObeliskChestBlock.OPENED);
        if (!chestBlock.isCompleteRewardStructure(serverLevel, pos)) {
            if (!chest.hasPendingRewards() && (chest.spraying || openedState)) {
                DungeonRewardClaim.completePhysicalRewardSpray(
                        serverLevel,
                        chest.rewardId.get(),
                        chest.instanceId.get(),
                        pos
                );
            } else if (chest.spraying) {
                chest.spraying = false;
                chest.setChanged();
                ObeliskDepths.LOGGER.warn(
                        "Obelisk Chest spray paused for invalid multipart structure: instance={}, reward={}, pos={}",
                        chest.instanceId.orElse(null),
                        chest.rewardId.orElse(null),
                        pos
                );
                DungeonRewardReconciliation.recoverOpenedPhysicalReward(
                        serverLevel,
                        chest.rewardId.get(),
                        chest.instanceId.get(),
                        pos,
                        "multipart structure invalid during spray tick"
                );
            }
            return;
        }

        if (chest.spraying && state.hasProperty(ObeliskChestBlock.OPENED)
                && !state.getValue(ObeliskChestBlock.OPENED)) {
            chestBlock.setOpened(serverLevel, pos, true);
        }

        if (!chest.spraying) {
            if (state.hasProperty(ObeliskChestBlock.OPENED)
                    && state.getValue(ObeliskChestBlock.OPENED)
                    && chest.hasPendingRewards()) {
                chest.ensureSpraying();
            } else if (state.hasProperty(ObeliskChestBlock.OPENED)
                    && state.getValue(ObeliskChestBlock.OPENED)
                    && !chest.hasPendingRewards()) {
                DungeonRewardClaim.completePhysicalRewardSpray(
                        serverLevel,
                        chest.rewardId.get(),
                        chest.instanceId.get(),
                        pos
                );
            }
            return;
        }

        if (!chest.hasPendingRewards()) {
            tickPostSprayCleanup(serverLevel, pos, chest);
            return;
        }

        if (chest.ticksUntilNextSpray > 0) {
            chest.ticksUntilNextSpray--;
            chest.setChanged();
            return;
        }

        Optional<ItemStack> next = chest.peekNextPendingStack();
        if (next.isEmpty() || next.get().isEmpty()) {
            chest.removeNextPendingStack();
            return;
        }

        boolean spawned = DungeonRewardDelivery.spawnRewardStack(
                serverLevel,
                chest.rewardId.get(),
                chest.instanceId.get(),
                chest.roomId,
                chest.rewardSeed,
                pos,
                chest.nextSprayOrdinal,
                next.get()
        );
        if (!spawned) {
            chest.ticksUntilNextSpray = SPRAY_INTERVAL_TICKS;
            chest.setChanged();
            return;
        }

        chest.removeNextPendingStack();
        chest.nextSprayOrdinal++;
        if (chest.hasPendingRewards()) {
            chest.ticksUntilNextSpray = SPRAY_INTERVAL_TICKS;
        } else {
            chest.ticksUntilNextSpray = 0;
            chest.postSprayCleanupTicks = POST_SPRAY_CLEANUP_TICKS;
        }
        chest.setChanged();
    }

    private static void tickPostSprayCleanup(
            ServerLevel level,
            BlockPos pos,
            ObeliskChestBlockEntity chest
    ) {
        if (chest.postSprayCleanupTicks == NO_POST_SPRAY_CLEANUP) {
            chest.postSprayCleanupTicks = POST_SPRAY_CLEANUP_TICKS;
            chest.setChanged();
            return;
        }

        if (chest.postSprayCleanupTicks > 0) {
            chest.postSprayCleanupTicks--;
            chest.setChanged();
            return;
        }

        if (DungeonRewardClaim.completePhysicalRewardSpray(
                level,
                chest.rewardId.get(),
                chest.instanceId.get(),
                pos
        )) {
            chest.spraying = false;
            chest.setChanged();
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.clearContents();
        ContainerHelper.loadAllItems(input, this.contents);
        this.rewardId = input.read("reward_id", DungeonRewardId.CODEC);
        this.instanceId = input.read("instance_id", DungeonInstanceId.CODEC);
        this.roomId = input.read("room_id", DungeonRoomId.CODEC);
        this.rewardSeed = input.getLongOr("reward_seed", 0L);
        this.createdGameTime = input.getLongOr("created_game_time", 0L);
        this.initialized = input.getBooleanOr("initialized", false)
                && this.rewardId.isPresent()
                && this.instanceId.isPresent();
        this.spraying = input.getBooleanOr("spraying", false);
        this.ticksUntilNextSpray = Math.max(0, input.getIntOr("ticks_until_next_spray", 0));
        this.postSprayCleanupTicks = input.getIntOr("post_spray_cleanup_ticks", NO_POST_SPRAY_CLEANUP);
        this.nextSprayOrdinal = Math.max(0, input.getIntOr("next_spray_ordinal", 0));
        if (!this.initialized) {
            this.rewardId = Optional.empty();
            this.instanceId = Optional.empty();
            this.roomId = Optional.empty();
            this.rewardSeed = 0L;
            this.createdGameTime = 0L;
            this.spraying = false;
            this.ticksUntilNextSpray = 0;
            this.postSprayCleanupTicks = NO_POST_SPRAY_CLEANUP;
            this.nextSprayOrdinal = 0;
            this.clearContents();
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.contents, false);
        this.rewardId.ifPresent(id -> output.store("reward_id", DungeonRewardId.CODEC, id));
        this.instanceId.ifPresent(id -> output.store("instance_id", DungeonInstanceId.CODEC, id));
        this.roomId.ifPresent(id -> output.store("room_id", DungeonRoomId.CODEC, id));
        output.putLong("reward_seed", this.rewardSeed);
        output.putLong("created_game_time", this.createdGameTime);
        output.putBoolean("initialized", this.initialized);
        output.putBoolean("spraying", this.spraying);
        output.putInt("ticks_until_next_spray", this.ticksUntilNextSpray);
        output.putInt("post_spray_cleanup_ticks", this.postSprayCleanupTicks);
        output.putInt("next_spray_ordinal", this.nextSprayOrdinal);
    }
}
