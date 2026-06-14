package io.github.naimjeg.obeliskdepths.menu;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.network.ClientboundTemperingDirectionStatePayload;
import io.github.naimjeg.obeliskdepths.network.TemperingDirectionView;
import io.github.naimjeg.obeliskdepths.recipe.ObeliskTemperingRecipe;
import io.github.naimjeg.obeliskdepths.recipe.ObeliskTemperingRecipeInput;
import io.github.naimjeg.obeliskdepths.recipe.ObeliskTemperingRecipeResolver;
import io.github.naimjeg.obeliskdepths.registry.ModBlocks;
import io.github.naimjeg.obeliskdepths.registry.ModItems;
import io.github.naimjeg.obeliskdepths.registry.ModMenuTypes;
import io.github.naimjeg.obeliskdepths.registry.ModRecipeTypes;
import io.github.naimjeg.obeliskdepths.tempering.AggregatedTemperingDirection;
import io.github.naimjeg.obeliskdepths.tempering.ObeliskTemperingDirectionPoolResolver;
import io.github.naimjeg.obeliskdepths.tempering.ObeliskTemperingPreviewResolver;
import io.github.naimjeg.obeliskdepths.tempering.ObeliskTemperingRoller;
import io.github.naimjeg.obeliskdepths.tempering.TemperingAffixPreview;
import io.github.naimjeg.obeliskdepths.tempering.TemperingTemplateData;
import io.github.naimjeg.obeliskdepths.tempering.TemperingTemplateItems;
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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private List<TemperingDirectionView> clientDirectionViews = List.of();
    private List<TemperingAffixPreview> clientSelectedPreviews = List.of();
    private int clientDirectionStateVersion;

    private @Nullable Identifier selectedDirectionId;
    private @Nullable ClientTemperingState lastSyncedClientState;
    private boolean directionSyncDirty = true;

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
                return ObeliskTemperingMenu.this
                        .isPotentialWeaponStack(stack);
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

    public ObeliskTemperingRecipeInput createRecipeInput() {
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

        Identifier selected = this.selectedDirectionId;

        if (selected == null) {
            this.clearResult();
            this.updateRecipeErrorState();
            return;
        }

        AggregatedTemperingDirection direction =
                this.resolvedState.directions().get(selected);

        if (direction == null || direction.entries().isEmpty()) {
            this.clearResult();
            this.updateRecipeErrorState();
            return;
        }

        ObeliskTemperingRoller.TemperingAvailability availability =
                ObeliskTemperingRoller.checkAvailability(
                        this.inputSlots.getItem(WEAPON_SLOT),
                        selected,
                        this.resolvedState.matchingRecipes()
                );

        if (availability.available()) {
            ItemStack result = this.inputSlots
                    .getItem(WEAPON_SLOT)
                    .copyWithCount(1);
            this.resultSlots.setRecipeUsed(null);
            this.resultSlots.setItem(0, result);
            this.hasValidRecipe.set(1);
        } else {
            this.clearResult();
        }

        this.updateRecipeErrorState();
    }

    private void clearResult() {
        this.resultSlots.setRecipeUsed(null);
        this.resultSlots.setItem(0, ItemStack.EMPTY);
        this.hasValidRecipe.set(0);
    }

    private void updateRecipeErrorState() {
        boolean hasWeapon = !this.inputSlots.getItem(WEAPON_SLOT).isEmpty();
        boolean hasTemplate = !this.inputSlots.getItem(TEMPLATE_SLOT).isEmpty();
        boolean hasResult = !this.resultSlots.getItem(0).isEmpty();

        this.hasRecipeError.set(
                hasWeapon && hasTemplate && !hasResult ? 1 : 0
        );
    }

    private void rebuildResolvedState() {
        if (this.level.isClientSide()) {
            return;
        }

        if (!(this.level.recipeAccess() instanceof RecipeManager recipeManager)) {
            this.replaceResolvedState(ResolvedTemperingState.EMPTY);
            return;
        }

        List<RecipeHolder<?>> loadedTemperingRecipes =
                recipeManager.getRecipes()
                        .stream()
                        .filter(holder ->
                                holder.value().getType()
                                        == ModRecipeTypes.OBELISK_TEMPERING.get()
                        )
                        .toList();

        ObeliskDepths.LOGGER.info(
                "Loaded tempering recipe count={}, ids={}",
                loadedTemperingRecipes.size(),
                loadedTemperingRecipes.stream()
                        .map(holder ->
                                holder.id().identifier().toString()
                        )
                        .sorted()
                        .toList()
        );

        ObeliskTemperingRecipeInput debugInput =
                this.createRecipeInput();

        for (RecipeHolder<?> holder : loadedTemperingRecipes) {
            ObeliskTemperingRecipe recipe =
                    (ObeliskTemperingRecipe) holder.value();

            TemperingTemplateData templateData =
                    TemperingTemplateItems.getOrDefault(
                            debugInput.template()
                    );

            boolean weaponMatches =
                    recipe.weapon().test(debugInput.weapon());

            boolean templateMatches =
                    recipe.template().test(debugInput.template());

            boolean ingredientMatches =
                    recipe.ingredient()
                            .map(ingredient ->
                                    ingredient.test(
                                            debugInput.ingredient()
                                    )
                            )
                            .orElse(debugInput.ingredient().isEmpty());

            boolean tierMatches =
                    templateData.tier() >= recipe.minTier()
                            && templateData.tier() <= recipe.maxTier();

            boolean weaponCanTemper =
                    ObeliskTemperingRoller.canTemper(
                            debugInput.weapon(),
                            recipe.replaceExisting()
                    );

            ObeliskDepths.LOGGER.info(
                    "Tempering candidate {}: weaponMatch={}, "
                            + "templateMatch={}, ingredientMatch={}, "
                            + "templateItem={}, tier={}, tierMatch={}, "
                            + "canTemper={}",
                    holder.id().identifier(),
                    weaponMatches,
                    templateMatches,
                    ingredientMatches,
                    TemperingTemplateItems.isTemperingTemplate(
                            debugInput.template()
                    ),
                    templateData.tier(),
                    tierMatches,
                    weaponCanTemper
            );
        }

        List<RecipeHolder<ObeliskTemperingRecipe>> matchingRecipes =
                ObeliskTemperingRecipeResolver.findBaseMatches(
                        recipeManager,
                        this.createRecipeInput(),
                        this.level
                );
        Map<Identifier, AggregatedTemperingDirection> directions =
                ObeliskTemperingDirectionPoolResolver.resolve(matchingRecipes);

        ObeliskDepths.LOGGER.info(
                "Tempering resolve: weapon={}, template={}, ingredient={}, "
                        + "templateItem={}, templateData={}, matches={}, directions={}",
                this.inputSlots.getItem(WEAPON_SLOT),
                this.inputSlots.getItem(TEMPLATE_SLOT),
                this.inputSlots.getItem(INGREDIENT_SLOT),
                TemperingTemplateItems.isTemperingTemplate(
                        this.inputSlots.getItem(TEMPLATE_SLOT)
                ),
                TemperingTemplateItems.hasTemplateData(
                        this.inputSlots.getItem(TEMPLATE_SLOT)
                ),
                matchingRecipes.stream()
                        .map(holder -> holder.id().identifier().toString())
                        .toList(),
                directions.keySet()
        );

        this.replaceResolvedState(new ResolvedTemperingState(
                matchingRecipes,
                directions,
                List.copyOf(directions.keySet())
        ));
    }

    private void replaceResolvedState(ResolvedTemperingState state) {
        if (this.level.isClientSide()) {
            return;
        }

        List<Identifier> oldAvailable =
                this.resolvedState.orderedDirectionIds();
        Identifier oldSelected = this.selectedDirectionId;

        this.resolvedState = state;

        if (!state.directions().containsKey(this.selectedDirectionId)) {
            this.selectedDirectionId = state.orderedDirectionIds()
                    .stream()
                    .findFirst()
                    .orElse(null);
        }

        if (!Objects.equals(oldSelected, this.selectedDirectionId)
                || !oldAvailable.equals(state.orderedDirectionIds())) {
            this.markDirectionSyncDirty();
        }
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
            this.markDirectionSyncDirty();
            this.syncDirectionState();
            return false;
        }

        if (Objects.equals(this.selectedDirectionId, directionId)) {
            this.markDirectionSyncDirty();
            this.syncDirectionState();
            return true;
        }

        this.selectedDirectionId = directionId;
        this.markDirectionSyncDirty();
        this.createResult();
        return true;
    }

    private ItemStack finalizeTakenResult(Player player) {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return ItemStack.EMPTY;
        }

        Identifier requestedDirection = this.selectedDirectionId;

        if (requestedDirection == null) {
            return ItemStack.EMPTY;
        }

        /*
         * Re-read current inputs and recipes. This protects against stale state,
         * including a datapack reload while the menu is open.
         */
        this.rebuildResolvedState();

        if (!this.resolvedState.directions()
                .containsKey(requestedDirection)) {
            return ItemStack.EMPTY;
        }

        /*
         * rebuildResolvedState may alter the selected direction. Preserve the
         * direction the player actually requested.
         */
        this.selectedDirectionId = requestedDirection;

        ObeliskTemperingRecipeInput input =
                this.createRecipeInput();

        if (!TemperingTemplateItems
                .isTemperingTemplate(input.template())) {
            return ItemStack.EMPTY;
        }

        ObeliskTemperingRoller.TemperingAvailability availability =
                ObeliskTemperingRoller.checkAvailability(
                        input.weapon(),
                        requestedDirection,
                        this.resolvedState.matchingRecipes()
                );

        if (!availability.available()) {
            return ItemStack.EMPTY;
        }

        TemperingTemplateData templateData =
                TemperingTemplateItems.getOrDefault(
                        input.template()
                );

        ObeliskTemperingRoller.TemperingResult result =
                ObeliskTemperingRoller.temper(
                        input.weapon(),
                        templateData,
                        requestedDirection,
                        this.resolvedState.matchingRecipes(),
                        serverLevel.getRandom()
                );

        return result.success()
                ? result.result()
                : ItemStack.EMPTY;
    }

    private void onTake(Player player, ItemStack carried) {
        ItemStack finalResult = this.finalizeTakenResult(player);

        if (finalResult.isEmpty()
                || !this.copyFinalResultIntoTakenStack(carried, finalResult)) {
            carried.setCount(0);
            this.resultSlots.setItem(0, ItemStack.EMPTY);
            this.createResult();
            return;
        }

        this.consumeInputs(player, carried);
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

    private void consumeInputs(Player player, ItemStack crafted) {
        if (!crafted.isEmpty()) {
            crafted.onCraftedBy(player, crafted.getCount());
        }

        this.shrinkStackInSlot(WEAPON_SLOT);
        this.shrinkStackInSlot(TEMPLATE_SLOT);

        if (!this.inputSlots.getItem(INGREDIENT_SLOT).isEmpty()) {
            this.shrinkStackInSlot(INGREDIENT_SLOT);
        }

        this.rebuildResolvedState();
        this.resultSlots.setItem(0, ItemStack.EMPTY);
        this.access.execute((level, pos) -> level.levelEvent(1044, pos, 0));
        this.createResult();
    }

    private void shrinkStackInSlot(int slot) {
        if (!this.inputSlots.getItem(slot).isEmpty()) {
            this.inputSlots.removeItem(slot, 1);
        }
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
            ItemStack finalResult = this.finalizeTakenResult(player);

            if (finalResult.isEmpty()) {
                return ItemStack.EMPTY;
            }

            ItemStack moving = finalResult.copy();

            if (!this.moveItemStackTo(
                    moving,
                    PLAYER_INVENTORY_START,
                    PLAYER_HOTBAR_END,
                    true
            ) || !moving.isEmpty()) {
                return ItemStack.EMPTY;
            }

            slot.setByPlayer(ItemStack.EMPTY);
            this.consumeInputs(player, finalResult);
            return finalResult;
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
        if (this.isPotentialWeaponStack(stack)
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

    private boolean isPotentialWeaponStack(ItemStack stack) {
        return !stack.isEmpty()
                && ObeliskTemperingRoller.canTemper(stack, true);
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
        this.markDirectionSyncDirty();
        this.syncDirectionState();
    }

    public Optional<Identifier> selectedDirectionId() {
        return Optional.ofNullable(this.selectedDirectionId);
    }

    public Optional<AggregatedTemperingDirection> selectedDirection() {
        if (this.level.isClientSide()) {
            return Optional.empty();
        }

        Identifier directionId = this.selectedDirectionId;

        if (directionId == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(this.resolvedState.directions()
                .get(directionId));
    }

    public boolean hasRecipeError() {
        return this.hasRecipeError.get() > 0;
    }

    public boolean hasValidRecipe() {
        return this.hasValidRecipe.get() > 0;
    }

    public boolean isDirectionAvailable(Identifier directionId) {
        if (directionId == null) {
            return false;
        }

        if (this.level.isClientSide()) {
            return this.clientDirectionViews
                    .stream()
                    .anyMatch(view -> view.id().equals(directionId));
        }

        return this.resolvedState.directions().containsKey(directionId);
    }

    public List<Identifier> availableDirectionIds() {
        if (!this.level.isClientSide()) {
            return this.resolvedState.orderedDirectionIds();
        }

        return this.clientDirectionViews
                .stream()
                .map(TemperingDirectionView::id)
                .toList();
    }

    public List<TemperingDirectionView> directionViews() {
        return this.level.isClientSide()
                ? this.clientDirectionViews
                : this.buildDirectionViews();
    }

    public List<TemperingAffixPreview> selectedDirectionPreviews() {
        return this.level.isClientSide()
                ? this.clientSelectedPreviews
                : this.buildSelectedPreviews();
    }

    public int directionStateVersion() {
        return this.clientDirectionStateVersion;
    }

    public void applyDirectionStateFromServer(
            @Nullable Identifier selectedDirectionId,
            List<TemperingDirectionView> directions,
            List<TemperingAffixPreview> selectedPreviews
    ) {
        if (!this.level.isClientSide()) {
            return;
        }

        List<TemperingDirectionView> copiedDirections =
                directions == null ? List.of() : List.copyOf(directions);
        boolean selectedIsAvailable = selectedDirectionId != null
                && copiedDirections.stream()
                .anyMatch(view -> view.id().equals(selectedDirectionId));

        Identifier normalizedSelected =
                selectedIsAvailable ? selectedDirectionId : null;
        List<TemperingAffixPreview> copiedPreviews =
                normalizedSelected == null || selectedPreviews == null
                        ? List.of()
                        : List.copyOf(selectedPreviews);

        boolean changed = !Objects.equals(
                this.selectedDirectionId,
                normalizedSelected
        ) || !this.clientDirectionViews.equals(copiedDirections)
                || !this.clientSelectedPreviews.equals(copiedPreviews);

        this.selectedDirectionId = normalizedSelected;
        this.clientDirectionViews = copiedDirections;
        this.clientSelectedPreviews = copiedPreviews;

        if (changed) {
            this.clientDirectionStateVersion++;
        }
    }

    private List<TemperingDirectionView> buildDirectionViews() {
        return this.resolvedState.orderedDirectionIds()
                .stream()
                .map(this.resolvedState.directions()::get)
                .filter(Objects::nonNull)
                .map(direction -> new TemperingDirectionView(
                        direction.directionId(),
                        direction.definition().displayName(),
                        direction.definition().description()
                ))
                .toList();
    }

    private List<TemperingAffixPreview> buildSelectedPreviews() {
        Identifier selected = this.selectedDirectionId;

        if (selected == null) {
            return List.of();
        }

        AggregatedTemperingDirection direction =
                this.resolvedState.directions().get(selected);

        if (direction == null) {
            return List.of();
        }

        return ObeliskTemperingPreviewResolver.resolveDirectionPreview(
                direction
        );
    }

    private ClientTemperingState buildClientState() {
        return new ClientTemperingState(
                Optional.ofNullable(this.selectedDirectionId),
                this.buildDirectionViews(),
                this.buildSelectedPreviews()
        );
    }

    private void markDirectionSyncDirty() {
        this.directionSyncDirty = true;
    }

    private void syncDirectionState() {
        if (this.level.isClientSide()
                || !(this.owner instanceof ServerPlayer serverPlayer)
                || serverPlayer.containerMenu != this) {
            return;
        }

        ClientTemperingState state = this.buildClientState();

        if (!this.directionSyncDirty
                && Objects.equals(this.lastSyncedClientState, state)) {
            return;
        }

        PacketDistributor.sendToPlayer(
                serverPlayer,
                new ClientboundTemperingDirectionStatePayload(
                        this.containerId,
                        state.selectedDirectionId(),
                        state.directions(),
                        state.selectedPreviews()
                )
        );
        this.lastSyncedClientState = state;
        this.directionSyncDirty = false;
    }

    private record ClientTemperingState(
            Optional<Identifier> selectedDirectionId,
            List<TemperingDirectionView> directions,
            List<TemperingAffixPreview> selectedPreviews
    ) {
        private ClientTemperingState {
            selectedDirectionId = selectedDirectionId == null
                    ? Optional.empty()
                    : selectedDirectionId;
            directions = directions == null
                    ? List.of()
                    : List.copyOf(directions);
            selectedPreviews = selectedPreviews == null
                    ? List.of()
                    : List.copyOf(selectedPreviews);
        }
    }

    private record ResolvedTemperingState(
            List<RecipeHolder<ObeliskTemperingRecipe>> matchingRecipes,
            Map<Identifier, AggregatedTemperingDirection> directions,
            List<Identifier> orderedDirectionIds
    ) {
        private static final ResolvedTemperingState EMPTY =
                new ResolvedTemperingState(List.of(), Map.of(), List.of());

        private ResolvedTemperingState {
            matchingRecipes = matchingRecipes == null
                    ? List.of()
                    : List.copyOf(matchingRecipes);
            directions = directions == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(directions));
            orderedDirectionIds = orderedDirectionIds == null
                    ? List.of()
                    : List.copyOf(orderedDirectionIds);
        }
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
                    && !ObeliskTemperingMenu.this.resultSlots.getItem(0).isEmpty();
        }

        @Override
        public void onTake(Player player, ItemStack carried) {
            ObeliskTemperingMenu.this.onTake(player, carried);
            super.onTake(player, carried);
        }
    }
}
