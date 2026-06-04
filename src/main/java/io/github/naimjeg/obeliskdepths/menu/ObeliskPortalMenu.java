package io.github.naimjeg.obeliskdepths.menu;

import io.github.naimjeg.obeliskdepths.block.ObeliskBlock;
import io.github.naimjeg.obeliskdepths.block.ObeliskPart;
import io.github.naimjeg.obeliskdepths.dungeon.interaction.ObeliskInteractionHandler;
import io.github.naimjeg.obeliskdepths.dungeon.portal.PortalAdmissionMode;
import io.github.naimjeg.obeliskdepths.dungeon.tribute.TributeResolver;
import io.github.naimjeg.obeliskdepths.registry.ModBlocks;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import io.github.naimjeg.obeliskdepths.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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

public class ObeliskPortalMenu extends AbstractContainerMenu {
    public static final int TRIBUTE_SLOT = 0;

    public static final int TRIBUTE_SLOT_X = 80;
    public static final int TRIBUTE_SLOT_Y = 35;

    public static final int BUTTON_SOLO = 0;
    public static final int BUTTON_OPEN_JOIN = 1;

    public static final int STATUS_IDLE = 0;
    public static final int STATUS_SUBMITTING = 1;
    public static final int STATUS_FAILED = 2;

    private static final int PLAYER_INVENTORY_START = 1;
    private static final int PLAYER_INVENTORY_END = 28;
    private static final int PLAYER_HOTBAR_END = 37;

    private final ContainerLevelAccess access;
    private final Level level;
    private final BlockPos obeliskBottomPos;
    private final SimpleContainer tributeSlot;
    private final DataSlot status = DataSlot.standalone();

    public ObeliskPortalMenu(int containerId, Inventory inventory) {
        this(
                containerId,
                inventory,
                ContainerLevelAccess.NULL,
                BlockPos.ZERO
        );
    }

    public ObeliskPortalMenu(
            int containerId,
            Inventory inventory,
            ContainerLevelAccess access,
            BlockPos obeliskBottomPos
    ) {
        super(ModMenuTypes.OBELISK_PORTAL.get(), containerId);

        this.access = access;
        this.level = inventory.player.level();
        this.obeliskBottomPos = obeliskBottomPos.immutable();

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
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);

        if (container == this.tributeSlot && this.status.get() == STATUS_FAILED) {
            this.status.set(STATUS_IDLE);
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (this.status.get() == STATUS_SUBMITTING) {
            return true;
        }

        PortalAdmissionMode requestedMode = this.decodeMode(buttonId);

        if (requestedMode == null) {
            return false;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }

        if (!this.stillValid(player) || !this.isValidBottomObelisk()) {
            this.fail(serverPlayer, Component.translatable(
                    "message.obeliskdepths.obelisk.invalid_obelisk"
            ));
            return true;
        }

        if (serverPlayer.level().dimension().equals(ModDimensions.OBELISK_DEPTHS_LEVEL)) {
            this.fail(serverPlayer, Component.translatable(
                    "message.obeliskdepths.obelisk.inside_dungeon_denied"
            ));
            return true;
        }

        ServerLevel dungeonLevel = serverPlayer.level().getServer()
                .getLevel(ModDimensions.OBELISK_DEPTHS_LEVEL);

        if (dungeonLevel == null) {
            this.fail(serverPlayer, Component.translatable(
                    "message.obeliskdepths.obelisk.no_dimension"
            ));
            return true;
        }

        this.status.set(STATUS_SUBMITTING);
        this.broadcastChanges();

        /*
         * This stack is the menu slot stack. ObeliskInteractionHandler consumes
         * from this stack only after successful portal creation. Activation
         * success closes this menu; the player must physically enter the
         * spawned portal entity afterward. If portal creation fails, the stack
         * remains in the slot and is returned on close.
         */
        ItemStack tributeStack = this.tributeSlot.getItem(TRIBUTE_SLOT);

        boolean success = ObeliskInteractionHandler.activate(
                serverPlayer,
                dungeonLevel,
                this.obeliskBottomPos,
                requestedMode,
                tributeStack
        );

        if (success) {
            serverPlayer.closeContainer();
            return true;
        }

        this.status.set(STATUS_FAILED);
        this.broadcastChanges();
        return true;
    }

    private PortalAdmissionMode decodeMode(int buttonId) {
        return switch (buttonId) {
            case BUTTON_SOLO -> PortalAdmissionMode.SOLO;
            case BUTTON_OPEN_JOIN -> PortalAdmissionMode.OPEN_JOIN;
            default -> null;
        };
    }

    private boolean isValidBottomObelisk() {
        return this.access.evaluate((level, pos) -> {
            var state = level.getBlockState(pos);

            return state.is(ModBlocks.OBELISK.get())
                    && state.hasProperty(ObeliskBlock.PART)
                    && state.getValue(ObeliskBlock.PART) == ObeliskPart.BOTTOM;
        }, false);
    }

    private void fail(ServerPlayer player, Component message) {
        this.status.set(STATUS_FAILED);
        this.broadcastChanges();
        player.sendOverlayMessage(message);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
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
}
