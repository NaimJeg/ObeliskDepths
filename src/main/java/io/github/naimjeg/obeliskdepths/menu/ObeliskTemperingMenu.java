package io.github.naimjeg.obeliskdepths.menu;

import io.github.naimjeg.obeliskdepths.network.ClientboundTemperingDirectionStatePayload;
import io.github.naimjeg.obeliskdepths.network.TemperingDirectionView;
import io.github.naimjeg.obeliskdepths.network.TemperingViewState;
import io.github.naimjeg.obeliskdepths.recipe.ObeliskTemperingRecipeInput;
import io.github.naimjeg.obeliskdepths.registry.ModBlocks;
import io.github.naimjeg.obeliskdepths.registry.ModMenuTypes;
import io.github.naimjeg.obeliskdepths.tempering.ResolvedTemperingState;
import io.github.naimjeg.obeliskdepths.tempering.TemperingAffixPreview;
import io.github.naimjeg.obeliskdepths.tempering.TemperingResolver;
import io.github.naimjeg.obeliskdepths.tempering.TemperingResult;
import io.github.naimjeg.obeliskdepths.tempering.TemperingTemplateItems;
import io.github.naimjeg.obeliskdepths.tempering.TemperingTransaction;
import io.github.naimjeg.obeliskdepths.tempering.TemperingViewStateFactory;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ObeliskTemperingMenu extends AbstractContainerMenu {
    public static final int WEAPON_SLOT = 0;
    public static final int TEMPLATE_SLOT = 1;
    public static final int INGREDIENT_SLOT = 2;
    public static final int RESULT_SLOT = 3;

    public static final int WEAPON_SLOT_X = 20;
    public static final int WEAPON_SLOT_Y = 25;
    public static final int TEMPLATE_SLOT_X = 40;
    public static final int TEMPLATE_SLOT_Y = 25;
    public static final int INGREDIENT_SLOT_X = 30;
    public static final int INGREDIENT_SLOT_Y = 45;
    public static final int RESULT_SLOT_X = 148;
    public static final int RESULT_SLOT_Y = 30;

    public static final int AFFIX_BUTTON_OFFSET = 1000;

    private static final int PLAYER_INVENTORY_START = RESULT_SLOT + 1;
    private static final int PLAYER_INVENTORY_END =
            PLAYER_INVENTORY_START + 27;
    private static final int PLAYER_HOTBAR_END =
            PLAYER_INVENTORY_END + 9;

    private final ContainerLevelAccess access;
    private final Level level;
    private final Player owner;
    private final SimpleContainer inputSlots;
    private final ResultContainer resultSlots = new ResultContainer();

    private final DataSlot hasRecipeError = DataSlot.standalone();
    private final DataSlot hasValidRecipe = DataSlot.standalone();

    private ResolvedTemperingState resolvedState =
            ResolvedTemperingState.EMPTY;
    private TemperingViewState clientViewState = TemperingViewState.EMPTY;
    private int clientDirectionStateVersion;

    private @Nullable Identifier selectedDirectionId;
    private @Nullable TemperingViewState lastSyncedClientState;

    public ObeliskTemperingMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    public ObeliskTemperingMenu(
            int containerId,
            Inventory inventory,
            ContainerLevelAccess access
    ) {
        super(ModMenuTypes.OBELISK_TEMPERING.get(), containerId);

        this.access = access;
        this.level = inventory.player.level();
        this.owner = inventory.player;
        this.inputSlots = new SimpleContainer(3) {
            @Override
            public void setChanged() {
                super.setChanged();
                ObeliskTemperingMenu.this.slotsChanged(this);
            }
        };

        this.addSlot(new Slot(
                this.inputSlots,
                WEAPON_SLOT,
                WEAPON_SLOT_X,
                WEAPON_SLOT_Y
        ) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return TemperingResolver.canAcceptWeapon(stack);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        this.addSlot(new Slot(
                this.inputSlots,
                TEMPLATE_SLOT,
                TEMPLATE_SLOT_X,
                TEMPLATE_SLOT_Y
        ) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return TemperingTemplateItems
                        .isTemperingTemplate(stack);
            }
        });

        this.addSlot(new Slot(
                this.inputSlots,
                INGREDIENT_SLOT,
                INGREDIENT_SLOT_X,
                INGREDIENT_SLOT_Y
        ));
        this.addSlot(new ObeliskTemperingResultSlot(
                this.resultSlots,
                0,
                RESULT_SLOT_X,
                RESULT_SLOT_Y
        ));

        this.addStandardInventorySlots(inventory, 8, 84);

        this.addDataSlot(this.hasRecipeError).set(0);
        this.addDataSlot(this.hasValidRecipe).set(0);

        if (!this.level.isClientSide()) {
            this.rebuildResolvedState();
            this.updateResultFromCachedState();
        }
    }

    private ObeliskTemperingRecipeInput currentInput() {
        return new ObeliskTemperingRecipeInput(
                this.inputSlots.getItem(WEAPON_SLOT),
                this.inputSlots.getItem(TEMPLATE_SLOT),
                this.inputSlots.getItem(INGREDIENT_SLOT)
        );
    }

    public void createResult() {
        this.updateResultFromCachedState();
        this.broadcastChanges();
    }

    private void updateResultFromCachedState() {
        if (this.level.isClientSide()) {
            return;
        }

        if (!this.resolvedState.actionable()) {
            this.clearResult();
            this.updateRecipeErrorState();
            return;
        }

        this.resultSlots.setRecipeUsed(null);
        this.resultSlots.setItem(
                0,
                this.resolvedState.previewResult().copy()
        );
        this.hasValidRecipe.set(1);
        this.updateRecipeErrorState();
    }

    private void clearResult() {
        this.resultSlots.setRecipeUsed(null);
        this.resultSlots.setItem(0, ItemStack.EMPTY);
        this.hasValidRecipe.set(0);
    }

    private void updateRecipeErrorState() {
        this.hasRecipeError.set(this.resolvedState.hasRecipeError() ? 1 : 0);
    }

    private void rebuildResolvedState() {
        if (this.level.isClientSide()) {
            return;
        }

        if (!(this.level.recipeAccess() instanceof RecipeManager recipeManager)) {
            this.replaceResolvedState(ResolvedTemperingState.EMPTY);
            return;
        }

        this.replaceResolvedState(TemperingResolver.resolve(
                recipeManager,
                this.level,
                this.currentInput(),
                this.selectedDirectionId
        ));
    }

    private void replaceResolvedState(ResolvedTemperingState state) {
        if (this.level.isClientSide()) {
            return;
        }

        this.resolvedState = state == null
                ? ResolvedTemperingState.EMPTY
                : state;
        this.selectedDirectionId =
                this.resolvedState.selectedDirectionId().orElse(null);
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);

        if (container == this.inputSlots && !this.level.isClientSide()) {
            this.rebuildResolvedState();
            this.createResult();
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        return false;
    }

    public boolean selectDirectionFromClient(Identifier directionId) {
        if (directionId == null) {
            return false;
        }

        if (!this.resolvedState.directions().containsKey(directionId)) {
            this.lastSyncedClientState = null;
            this.syncDirectionState();
            return false;
        }

        if (Objects.equals(this.selectedDirectionId, directionId)) {
            this.lastSyncedClientState = null;
            this.syncDirectionState();
            return true;
        }

        this.selectedDirectionId = directionId;
        this.rebuildResolvedState();
        this.createResult();
        return true;
    }

    private TemperingResult executeCurrentTransaction() {
        ObeliskTemperingRecipeInput input = this.currentInput();

        if (!(this.level instanceof ServerLevel serverLevel)) {
            return TemperingResult.failure(
                    "not_server",
                    input.weapon(),
                    input.template(),
                    input.ingredient()
            );
        }

        if (!(serverLevel.recipeAccess() instanceof RecipeManager recipeManager)) {
            return TemperingResult.failure(
                    "missing_recipe_manager",
                    input.weapon(),
                    input.template(),
                    input.ingredient()
            );
        }

        return TemperingTransaction.execute(
                serverLevel,
                recipeManager,
                input,
                this.selectedDirectionId
        );
    }

    private void onTake(Player player, ItemStack carried) {
        TemperingResult transaction = this.executeCurrentTransaction();

        if (!transaction.success()
                || !this.copyFinalResultIntoTakenStack(
                carried,
                transaction.craftedStack()
        )) {
            carried.setCount(0);
            this.resultSlots.setItem(0, ItemStack.EMPTY);
            this.rebuildResolvedState();
            this.createResult();
            return;
        }

        this.applySuccessfulTransaction(player, carried, transaction);
    }

    private boolean copyFinalResultIntoTakenStack(
            ItemStack carried,
            ItemStack finalResult
    ) {
        if (carried.isEmpty() || finalResult.isEmpty()) {
            return false;
        }

        if (carried.getItem() != finalResult.getItem()) {
            return false;
        }

        carried.setCount(finalResult.getCount());
        carried.applyComponents(finalResult.getComponentsPatch());
        return true;
    }

    private void applySuccessfulTransaction(
            Player player,
            ItemStack crafted,
            TemperingResult transaction
    ) {
        if (!crafted.isEmpty()) {
            crafted.onCraftedBy(player, crafted.getCount());
        }

        this.inputSlots.setItem(WEAPON_SLOT, transaction.remainingWeapon());
        this.inputSlots.setItem(TEMPLATE_SLOT, transaction.remainingTemplate());
        this.inputSlots.setItem(
                INGREDIENT_SLOT,
                transaction.remainingIngredient()
        );

        this.rebuildResolvedState();
        this.resultSlots.setItem(0, ItemStack.EMPTY);
        this.access.execute((level, pos) -> level.levelEvent(1044, pos, 0));
        this.createResult();
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

        if (slotIndex == RESULT_SLOT) {
            TemperingResult transaction = this.executeCurrentTransaction();

            if (!transaction.success()) {
                return ItemStack.EMPTY;
            }

            ItemStack moving = transaction.craftedStack().copy();

            if (!this.moveItemStackTo(
                    moving,
                    PLAYER_INVENTORY_START,
                    PLAYER_HOTBAR_END,
                    true
            ) || !moving.isEmpty()) {
                return ItemStack.EMPTY;
            }

            slot.setByPlayer(ItemStack.EMPTY);
            this.applySuccessfulTransaction(
                    player,
                    transaction.craftedStack(),
                    transaction
            );
            return transaction.craftedStack();
        }

        if (slotIndex >= WEAPON_SLOT && slotIndex <= INGREDIENT_SLOT) {
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
            if (!this.movePlayerStackToInput(stack)) {
                if (slotIndex < PLAYER_INVENTORY_END) {
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

    private boolean movePlayerStackToInput(ItemStack stack) {
        if (TemperingResolver.canAcceptWeapon(stack)
                && this.moveToEmptyInputSlot(stack, WEAPON_SLOT)) {
            return true;
        }

        if (this.isTemplateStack(stack)
                && this.moveToEmptyInputSlot(stack, TEMPLATE_SLOT)) {
            return true;
        }

        return this.moveToEmptyInputSlot(stack, INGREDIENT_SLOT);
    }

    private boolean moveToEmptyInputSlot(ItemStack stack, int slotIndex) {
        Slot target = this.slots.get(slotIndex);

        if (target.hasItem()) {
            return false;
        }

        return this.moveItemStackTo(stack, slotIndex, slotIndex + 1, false);
    }

    private boolean isTemplateStack(ItemStack stack) {
        return TemperingTemplateItems.isTemperingTemplate(stack);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(
                this.access,
                player,
                ModBlocks.OBELISK_SMITHING_TABLE.get()
        );
    }

    @Override
    public void removed(Player player) {
        super.removed(player);

        if (!this.level.isClientSide()) {
            this.access.execute(
                    (level, pos) -> this.clearContainer(player, this.inputSlots)
            );
        }
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        this.syncDirectionState();
    }

    @Override
    public void sendAllDataToRemote() {
        super.sendAllDataToRemote();
        this.lastSyncedClientState = null;
        this.syncDirectionState();
    }

    public Optional<Identifier> selectedDirectionId() {
        return Optional.ofNullable(this.selectedDirectionId);
    }

    public boolean hasRecipeError() {
        return this.hasRecipeError.get() > 0;
    }

    public boolean hasValidRecipe() {
        return this.hasValidRecipe.get() > 0;
    }

    public List<TemperingDirectionView> directionViews() {
        return this.level.isClientSide()
                ? this.clientViewState.directions()
                : this.serverViewState().directions();
    }

    public List<TemperingAffixPreview> selectedDirectionPreviews() {
        return this.level.isClientSide()
                ? this.clientViewState.selectedPreviews()
                : this.serverViewState().selectedPreviews();
    }

    public int directionStateVersion() {
        return this.clientDirectionStateVersion;
    }

    public void applyTemperingViewStateFromServer(
            @Nullable TemperingViewState state
    ) {
        if (!this.level.isClientSide()) {
            return;
        }

        TemperingViewState normalized = state == null
                ? TemperingViewState.EMPTY
                : state;
        Identifier normalizedSelected =
                normalized.selectedDirectionId().orElse(null);
        boolean changed = !Objects.equals(this.clientViewState, normalized)
                || !Objects.equals(
                this.selectedDirectionId,
                normalizedSelected
        );

        this.clientViewState = normalized;
        this.selectedDirectionId = normalizedSelected;

        if (changed) {
            this.clientDirectionStateVersion++;
        }
    }

    private TemperingViewState serverViewState() {
        return TemperingViewStateFactory.create(this.resolvedState);
    }

    private void syncDirectionState() {
        if (this.level.isClientSide()
                || !(this.owner instanceof ServerPlayer serverPlayer)
                || serverPlayer.containerMenu != this) {
            return;
        }

        TemperingViewState state = this.serverViewState();

        if (Objects.equals(this.lastSyncedClientState, state)) {
            return;
        }

        PacketDistributor.sendToPlayer(
                serverPlayer,
                new ClientboundTemperingDirectionStatePayload(
                        this.containerId,
                        state
                )
        );
        this.lastSyncedClientState = state;
    }

    private final class ObeliskTemperingResultSlot extends Slot {
        private ObeliskTemperingResultSlot(
                Container container,
                int slot,
                int x,
                int y
        ) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return ObeliskTemperingMenu.this.hasValidRecipe()
                    && !ObeliskTemperingMenu.this.resultSlots
                    .getItem(0)
                    .isEmpty();
        }

        @Override
        public void onTake(Player player, ItemStack carried) {
            ObeliskTemperingMenu.this.onTake(player, carried);
            super.onTake(player, carried);
        }
    }
}
