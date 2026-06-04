package io.github.naimjeg.obeliskdepths.menu;

import io.github.naimjeg.obeliskdepths.block.ObeliskBlock;
import io.github.naimjeg.obeliskdepths.block.ObeliskPart;
import io.github.naimjeg.obeliskdepths.dungeon.interaction.ObeliskInteractionHandler;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.*;
import io.github.naimjeg.obeliskdepths.dungeon.session.SessionAccessPolicy;
import io.github.naimjeg.obeliskdepths.dungeon.tribute.TributeResolver;
import io.github.naimjeg.obeliskdepths.registry.ModBlocks;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import io.github.naimjeg.obeliskdepths.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Optional;

public class ObeliskPortalMenu extends AbstractContainerMenu {
    public static final int TRIBUTE_SLOT = 0;

    public static final int TRIBUTE_SLOT_X = 78;
    public static final int TRIBUTE_SLOT_Y = 35;

    public static final int BUTTON_STARTER_ONLY = 0;
    public static final int BUTTON_OPEN = 1;

    public static final int STATUS_IDLE = 0;
    public static final int STATUS_SUBMITTING = 1;
    public static final int STATUS_FAILED = 2;
    public static final int STATUS_CANCELLED = 3;
    public static final int STATUS_READY = 4;

    private static final int PLAYER_INVENTORY_START = 1;
    private static final int PLAYER_INVENTORY_END = 28;
    private static final int PLAYER_HOTBAR_END = 37;

    private final ContainerLevelAccess access;
    private final Level level;
    private final BlockPos obeliskBottomPos;
    private final ResourceKey<Level> preparationLevelKey;
    private final boolean denyUseInsidePreparationLevel;
    private final PreparationSubmitter preparationSubmitter;
    private final SimpleContainer tributeSlot;
    private final DataSlot status = DataSlot.standalone();
    private final DataSlot synchronizationToken = DataSlot.standalone();
    private final DataSlot stageCode = DataSlot.standalone();
    private final DataSlot terminalReasonCode = DataSlot.standalone();
    private final DataSlot displayCompleted = DataSlot.standalone();
    private final DataSlot displayTotal = DataSlot.standalone();
    private final DataSlot totalCandidateChunks = DataSlot.standalone();
    private final DataSlot submittedCandidateChunks = DataSlot.standalone();
    private final DataSlot completedCandidateChunks = DataSlot.standalone();
    private final DataSlot inFlightCandidateChunks = DataSlot.standalone();
    private final DataSlot totalEntryChunks = DataSlot.standalone();
    private final DataSlot requestedEntryChunks = DataSlot.standalone();
    private final DataSlot readyEntryChunks = DataSlot.standalone();
    private final DataSlot totalSafeSpawnCandidates = DataSlot.standalone();
    private final DataSlot checkedSafeSpawnCandidates = DataSlot.standalone();
    private final DataSlot currentGenerationAttempt = DataSlot.standalone();
    private final DataSlot maximumGenerationAttempts = DataSlot.standalone();
    private DungeonPreparationJobId activeJobId;
    private boolean activationCommitted;

    public ObeliskPortalMenu(int containerId, Inventory inventory) {
        this(
                containerId,
                inventory,
                ContainerLevelAccess.NULL,
                BlockPos.ZERO,
                ModDimensions.OBELISK_DEPTHS_LEVEL,
                true,
                ObeliskInteractionHandler::activate
        );
    }

    public ObeliskPortalMenu(
            int containerId,
            Inventory inventory,
            ContainerLevelAccess access,
            BlockPos obeliskBottomPos
    ) {
        this(
                containerId,
                inventory,
                access,
                obeliskBottomPos,
                ModDimensions.OBELISK_DEPTHS_LEVEL,
                true,
                ObeliskInteractionHandler::activate
        );
    }

    ObeliskPortalMenu(
            int containerId,
            Inventory inventory,
            ContainerLevelAccess access,
            BlockPos obeliskBottomPos,
            ResourceKey<Level> preparationLevelKey,
            boolean denyUseInsidePreparationLevel,
            PreparationSubmitter preparationSubmitter
    ) {
        super(ModMenuTypes.OBELISK_PORTAL.get(), containerId);

        this.access = access;
        this.level = inventory.player.level();
        this.obeliskBottomPos = obeliskBottomPos.immutable();
        this.preparationLevelKey = preparationLevelKey;
        this.denyUseInsidePreparationLevel = denyUseInsidePreparationLevel;
        this.preparationSubmitter = preparationSubmitter;

        this.tributeSlot = new SimpleContainer(1) {
            @Override
            public void setChanged() {
                super.setChanged();
                ObeliskPortalMenu.this.slotsChanged(this);
            }
        };

        this.addSlot(new TributeSlot(
                this.tributeSlot,
                TRIBUTE_SLOT,
                TRIBUTE_SLOT_X,
                TRIBUTE_SLOT_Y
        ));

        this.addStandardInventorySlots(inventory, 8, 84);
        this.addDataSlot(this.status).set(STATUS_IDLE);
        this.addDataSlot(this.synchronizationToken).set(0);
        this.addDataSlot(this.stageCode).set(-1);
        this.addDataSlot(this.terminalReasonCode).set(0);
        this.addDataSlot(this.displayCompleted).set(0);
        this.addDataSlot(this.displayTotal).set(0);
        this.addDataSlot(this.totalCandidateChunks).set(0);
        this.addDataSlot(this.submittedCandidateChunks).set(0);
        this.addDataSlot(this.completedCandidateChunks).set(0);
        this.addDataSlot(this.inFlightCandidateChunks).set(0);
        this.addDataSlot(this.totalEntryChunks).set(0);
        this.addDataSlot(this.requestedEntryChunks).set(0);
        this.addDataSlot(this.readyEntryChunks).set(0);
        this.addDataSlot(this.totalSafeSpawnCandidates).set(0);
        this.addDataSlot(this.checkedSafeSpawnCandidates).set(0);
        this.addDataSlot(this.currentGenerationAttempt).set(0);
        this.addDataSlot(this.maximumGenerationAttempts).set(0);

        /*
         * Vanilla set-data packets are ordered and scoped by containerId, but
         * the slots are not an atomic packet batch. Status is registered first:
         * a terminal update unlocks input before its detail fields arrive, and
         * the screen renders a generic failure/cancellation fallback until its
         * bounded reason arrives. During an active stage transition, old and new
         * progress can coexist only transiently; both are independently clamped,
         * never affect input locking, and retain the same submission identity.
         * The synchronization token is submission identity only.
         */
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);

        if (container == this.tributeSlot
                && (this.status.get() == STATUS_FAILED
                || this.status.get() == STATUS_CANCELLED
                || this.status.get() == STATUS_READY)) {
            this.status.set(STATUS_IDLE);
            this.stageCode.set(-1);
            this.terminalReasonCode.set(0);
            this.clearProgressSlots();
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        SessionAccessPolicy accessPolicy = accessPolicyForButton(buttonId);
        if (accessPolicy == null) {
            return false;
        }

        if (this.status.get() == STATUS_SUBMITTING
                || this.status.get() == STATUS_READY) {
            return true;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }

        if (!this.stillValid(player) || !this.isValidBottomObelisk()) {
            this.fail(
                    serverPlayer,
                    Component.translatable(
                            "message.obeliskdepths.obelisk.invalid_obelisk"
                    ),
                    DungeonPreparationJobFailureReason.SUBMISSION_REJECTED
            );
            return true;
        }

        if (this.denyUseInsidePreparationLevel
                && serverPlayer.level().dimension().equals(this.preparationLevelKey)) {
            this.fail(
                    serverPlayer,
                    Component.translatable(
                            "message.obeliskdepths.obelisk.inside_dungeon_denied"
                    ),
                    DungeonPreparationJobFailureReason.SUBMISSION_REJECTED
            );
            return true;
        }

        ServerLevel dungeonLevel = serverPlayer.level().getServer()
                .getLevel(this.preparationLevelKey);

        if (dungeonLevel == null) {
            this.fail(
                    serverPlayer,
                    Component.translatable(
                            "message.obeliskdepths.obelisk.no_dimension"
                    ),
                    DungeonPreparationJobFailureReason
                            .AUTHORITATIVE_RUNTIME_UNAVAILABLE
            );
            return true;
        }

        /*
         * This stack is the menu slot stack. ObeliskInteractionHandler consumes
         * from this stack only after successful portal creation. Activation
         * success closes this menu; the player must physically enter the
         * spawned portal entity afterward. If portal creation fails, the stack
         * remains in the slot and is returned on close.
         */
        ItemStack tributeStack = this.tributeSlot.getItem(TRIBUTE_SLOT);

        DungeonPreparationSubmission submission =
                this.preparationSubmitter.submit(
                        serverPlayer,
                        dungeonLevel,
                        this.obeliskBottomPos,
                        accessPolicy,
                        this.containerId,
                        tributeStack
                );

        if (submission.accepted()) {
            this.activeJobId = submission.jobId().orElseThrow();
            this.activationCommitted = false;
            this.synchronizationToken.set(nextSynchronizationToken(
                    this.synchronizationToken.get()
            ));
            this.status.set(STATUS_SUBMITTING);
            this.stageCode.set(DungeonPreparationStage.QUEUED.wireCode());
            this.terminalReasonCode.set(0);
            this.applyProgressSnapshot(DungeonPreparationProgressSnapshot.queued());
            this.broadcastChanges();
            return true;
        }

        this.status.set(STATUS_FAILED);
        this.stageCode.set(DungeonPreparationStage.FAILED.wireCode());
        this.terminalReasonCode.set(submission.rejectionReason()
                .map(ObeliskPortalMenu::submissionFailureReason)
                .orElse(DungeonPreparationJobFailureReason.SUBMISSION_REJECTED)
                .wireCode());
        this.activeJobId = null;
        this.clearProgressSlots();
        this.broadcastChanges();
        return true;
    }

    @Override
    public void broadcastChanges() {
        if (!this.level.isClientSide()) {
            this.syncPreparationState();
        }
        super.broadcastChanges();
    }

    private boolean isValidBottomObelisk() {
        return this.access.evaluate((level, pos) -> {
            var state = level.getBlockState(pos);

            return state.is(ModBlocks.OBELISK.get())
                    && state.hasProperty(ObeliskBlock.PART)
                    && state.getValue(ObeliskBlock.PART) == ObeliskPart.BOTTOM;
        }, false);
    }

    private void fail(
            ServerPlayer player,
            Component message,
            DungeonPreparationJobFailureReason reason
    ) {
        terminateSubmission(reason);
        this.broadcastChanges();
        player.sendOverlayMessage(message);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (this.isSubmitting()) {
            return ItemStack.EMPTY;
        }
        if (slotIndex < 0 || slotIndex >= this.slots.size()) {
            return ItemStack.EMPTY;
        }

        Slot slot = this.slots.get(slotIndex);

        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (slotIndex == TRIBUTE_SLOT) {
            if (!this.moveItemStackTo(
                    stack,
                    PLAYER_INVENTORY_START,
                    PLAYER_HOTBAR_END,
                    false
            )) {
                return ItemStack.EMPTY;
            }
        } else if (slotIndex >= PLAYER_INVENTORY_START
                && slotIndex < PLAYER_HOTBAR_END) {
            if (TributeResolver.resolve(stack).valid()) {
                if (!this.moveItemStackTo(
                        stack,
                        TRIBUTE_SLOT,
                        TRIBUTE_SLOT + 1,
                        false
                )) {
                    return ItemStack.EMPTY;
                }
            } else if (slotIndex < PLAYER_INVENTORY_END) {
                if (!this.moveItemStackTo(
                        stack,
                        PLAYER_INVENTORY_END,
                        PLAYER_HOTBAR_END,
                        false
                )) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(
                    stack,
                    PLAYER_INVENTORY_START,
                    PLAYER_INVENTORY_END,
                    false
            )) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        if (stack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }

        slot.onTake(player, stack);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(
                this.access,
                player,
                ModBlocks.OBELISK.get()
        );
    }

    @Override
    public void removed(Player player) {
        super.removed(player);

        if (!this.level.isClientSide()) {
            if (!this.activationCommitted && this.activeJobId != null
                    && player instanceof ServerPlayer serverPlayer) {
                this.cancelActivePreparation(serverPlayer);
            }
            this.access.execute(
                    (level, pos) -> this.clearContainer(player, this.tributeSlot)
            );
        }
    }

    public boolean isSubmitting() {
        return this.status.get() == STATUS_SUBMITTING;
    }

    public boolean isFailed() {
        return this.status.get() == STATUS_FAILED;
    }

    public boolean isCancelled() {
        return this.status.get() == STATUS_CANCELLED;
    }

    public boolean isReady() {
        return this.status.get() == STATUS_READY;
    }

    public int stageCode() {
        return this.stageCode.get();
    }

    public int terminalReasonCode() {
        return this.terminalReasonCode.get();
    }

    public DungeonPreparationMenuState preparationState() {
        return new DungeonPreparationMenuState(
                isSubmitting(),
                this.synchronizationToken.get(),
                this.stageCode.get(),
                this.displayCompleted.get(),
                this.displayTotal.get(),
                this.status.get(),
                this.terminalReasonCode.get()
        );
    }

    public int totalCandidateChunks() {
        return this.totalCandidateChunks.get();
    }

    public int submittedCandidateChunks() {
        return this.submittedCandidateChunks.get();
    }

    public int completedCandidateChunks() {
        return this.completedCandidateChunks.get();
    }

    public int inFlightCandidateChunks() {
        return this.inFlightCandidateChunks.get();
    }

    public int totalEntryChunks() {
        return this.totalEntryChunks.get();
    }

    public int requestedEntryChunks() {
        return this.requestedEntryChunks.get();
    }

    public int readyEntryChunks() {
        return this.readyEntryChunks.get();
    }

    public int totalSafeSpawnCandidates() {
        return this.totalSafeSpawnCandidates.get();
    }

    public int checkedSafeSpawnCandidates() {
        return this.checkedSafeSpawnCandidates.get();
    }

    public int currentGenerationAttempt() {
        return this.currentGenerationAttempt.get();
    }

    public int maximumGenerationAttempts() {
        return this.maximumGenerationAttempts.get();
    }

    public ItemStack tributeStack() {
        return this.tributeSlot.getItem(TRIBUTE_SLOT);
    }

    public BlockPos obeliskBottomPos() {
        return this.obeliskBottomPos;
    }

    public void markActivationCommitted(DungeonPreparationJobId jobId) {
        if (this.activeJobId != null && this.activeJobId.equals(jobId)) {
            this.activationCommitted = true;
            this.activeJobId = null;
            this.status.set(STATUS_READY);
            this.stageCode.set(DungeonPreparationStage.READY.wireCode());
            this.terminalReasonCode.set(0);
            this.clearProgressSlots();
            this.broadcastChanges();
        }
    }

    public boolean matchesActivePreparation(
            DungeonPreparationJobId jobId,
            DungeonPreparationRequest request
    ) {
        return this.activeJobId != null
                && this.activeJobId.equals(jobId)
                && matchesActivePreparationForRuntime(request);
    }

    public boolean matchesActivePreparationForRuntime(
            DungeonPreparationRequest request
    ) {
        return this.containerId == request.sourceContainerId()
                && this.obeliskBottomPos.equals(request.obeliskPos())
                && this.status.get() == STATUS_SUBMITTING;
    }

    private void syncPreparationState() {
        if (this.activeJobId == null) {
            return;
        }
        ServerLevel dungeonLevel = this.level.getServer()
                .getLevel(this.preparationLevelKey);
        if (dungeonLevel == null) {
            terminateSubmission(
                    DungeonPreparationJobFailureReason
                            .AUTHORITATIVE_RUNTIME_UNAVAILABLE
            );
            return;
        }
        DungeonPreparationRuntime runtime = DungeonPreparationRuntime.get(dungeonLevel);
        if (runtime == null) {
            terminateSubmission(
                    DungeonPreparationJobFailureReason
                            .AUTHORITATIVE_RUNTIME_UNAVAILABLE
            );
            return;
        }
        Optional<DungeonPreparationJobSnapshot> snapshot =
                runtime.snapshot(this.activeJobId);
        if (snapshot.isEmpty()) {
            terminateSubmission(
                    DungeonPreparationJobFailureReason.AUTHORITATIVE_JOB_MISSING
            );
            return;
        }
        Optional<DungeonPreparationProgressSnapshot> progress =
                runtime.progressSnapshot(this.activeJobId);
        if (progress.isPresent()) {
            applyProgressSnapshot(progress.get());
        } else {
            clearProgressSlots();
        }

        DungeonPreparationJobSnapshot current = snapshot.get();
        this.stageCode.set(current.stage().wireCode());
        if (current.stage() == DungeonPreparationStage.FAILED) {
            this.status.set(STATUS_FAILED);
            this.terminalReasonCode.set(failureCode(current));
            this.activeJobId = null;
        } else if (current.stage() == DungeonPreparationStage.CANCELLED) {
            this.status.set(STATUS_CANCELLED);
            this.terminalReasonCode.set(cancellationCode(current));
            this.activeJobId = null;
        } else if (current.stage() == DungeonPreparationStage.READY) {
            this.status.set(STATUS_READY);
            this.terminalReasonCode.set(0);
            this.activeJobId = null;
        } else {
            this.status.set(STATUS_SUBMITTING);
            this.terminalReasonCode.set(0);
        }
    }

    private void terminateSubmission(DungeonPreparationJobFailureReason reason) {
        this.activationCommitted = false;
        this.activeJobId = null;
        this.status.set(STATUS_FAILED);
        this.stageCode.set(DungeonPreparationStage.FAILED.wireCode());
        this.terminalReasonCode.set(reason.wireCode());
        this.clearProgressSlots();
    }

    private static DungeonPreparationJobFailureReason submissionFailureReason(
            DungeonPreparationSubmissionRejectionReason reason
    ) {
        return reason == DungeonPreparationSubmissionRejectionReason.RUNTIME_CLEARED
                ? DungeonPreparationJobFailureReason
                        .AUTHORITATIVE_RUNTIME_UNAVAILABLE
                : DungeonPreparationJobFailureReason.SUBMISSION_REJECTED;
    }

    private static int failureCode(DungeonPreparationJobSnapshot snapshot) {
        if (snapshot.terminalCause() instanceof DungeonPreparationFailureCause cause) {
            DungeonPreparationJobFailureReason reason = cause.reason();
            return reason.wireCode();
        }
        return DungeonPreparationJobFailureReason.INTERNAL_ERROR.wireCode();
    }

    private static int cancellationCode(DungeonPreparationJobSnapshot snapshot) {
        if (snapshot.terminalCause() instanceof DungeonPreparationCancellationCause cause) {
            return -cause.reason().wireCode();
        }
        return -DungeonPreparationCancellationReason.USER_CANCELLED.wireCode();
    }

    private void cancelActivePreparation(ServerPlayer player) {
        ServerLevel dungeonLevel = player.level().getServer()
                .getLevel(this.preparationLevelKey);
        if (dungeonLevel == null) {
            return;
        }
        DungeonPreparationRuntime runtime = DungeonPreparationRuntime.get(dungeonLevel);
        if (runtime != null) {
            runtime.cancelJobsForPlayer(
                    player.getUUID(),
                    DungeonPreparationCancellationReason.MENU_CLOSED,
                    "obelisk menu closed",
                    dungeonLevel.getGameTime()
            );
        }
        this.activeJobId = null;
        this.status.set(STATUS_CANCELLED);
        this.stageCode.set(DungeonPreparationStage.CANCELLED.wireCode());
        this.terminalReasonCode.set(
                -DungeonPreparationCancellationReason.MENU_CLOSED.wireCode()
        );
        this.clearProgressSlots();
    }

    private void applyProgressSnapshot(DungeonPreparationProgressSnapshot snapshot) {
        DungeonPreparationMenuProgress.Progress display =
                DungeonPreparationMenuProgress.normalize(snapshot);
        this.displayCompleted.set(display.completed());
        this.displayTotal.set(display.total());
        this.totalCandidateChunks.set(snapshot.totalCandidateChunks());
        this.submittedCandidateChunks.set(snapshot.submittedCandidateChunks());
        this.completedCandidateChunks.set(snapshot.completedCandidateChunks());
        this.inFlightCandidateChunks.set(snapshot.inFlightCandidateChunks());
        this.totalEntryChunks.set(snapshot.totalEntryChunks());
        this.requestedEntryChunks.set(snapshot.requestedEntryChunks());
        this.readyEntryChunks.set(snapshot.readyEntryChunks());
        this.totalSafeSpawnCandidates.set(menuDataValue(
                snapshot.totalSafeSpawnCandidates()
        ));
        this.checkedSafeSpawnCandidates.set(menuDataValue(
                snapshot.checkedSafeSpawnCandidates()
        ));
        this.currentGenerationAttempt.set(snapshot.currentGenerationAttempt());
        this.maximumGenerationAttempts.set(snapshot.maximumGenerationAttempts());
    }

    private void clearProgressSlots() {
        this.displayCompleted.set(0);
        this.displayTotal.set(0);
        this.totalCandidateChunks.set(0);
        this.submittedCandidateChunks.set(0);
        this.completedCandidateChunks.set(0);
        this.inFlightCandidateChunks.set(0);
        this.totalEntryChunks.set(0);
        this.requestedEntryChunks.set(0);
        this.readyEntryChunks.set(0);
        this.totalSafeSpawnCandidates.set(0);
        this.checkedSafeSpawnCandidates.set(0);
        this.currentGenerationAttempt.set(0);
        this.maximumGenerationAttempts.set(0);
    }

    static SessionAccessPolicy accessPolicyForButton(int buttonId) {
        return switch (buttonId) {
            case BUTTON_STARTER_ONLY -> SessionAccessPolicy.STARTER_ONLY;
            case BUTTON_OPEN -> SessionAccessPolicy.OPEN;
            default -> null;
        };
    }

    static int menuDataValue(long value) {
        return (int)Math.min(
                DungeonPreparationProgressSnapshot.MAX_MENU_DATA_VALUE,
                Math.max(0L, value)
        );
    }

    static int nextSynchronizationToken(int current) {
        return current >= Short.MAX_VALUE || current < 0 ? 1 : current + 1;
    }

    private final class TributeSlot extends Slot {
        private TributeSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !ObeliskPortalMenu.this.isSubmitting()
                    && TributeResolver.resolve(stack).valid();
        }

        @Override
        public boolean mayPickup(Player player) {
            return !ObeliskPortalMenu.this.isSubmitting();
        }

        @Override
        public boolean isActive() {
            return !ObeliskPortalMenu.this.isSubmitting();
        }
    }

    @FunctionalInterface
    interface PreparationSubmitter {
        DungeonPreparationSubmission submit(
                ServerPlayer player,
                ServerLevel preparationLevel,
                BlockPos obeliskPos,
                SessionAccessPolicy accessPolicy,
                int sourceContainerId,
                ItemStack tributeStack
        );
    }
}
