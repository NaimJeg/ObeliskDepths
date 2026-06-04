package io.github.naimjeg.obeliskdepths.client.screen;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationCancellationReason;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationJobFailureReason;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationStage;
import io.github.naimjeg.obeliskdepths.menu.DungeonPreparationMenuState;
import io.github.naimjeg.obeliskdepths.menu.ObeliskPortalMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ObeliskPortalScreen extends AbstractContainerScreen<ObeliskPortalMenu> {
    /*
     * Texture contract:
     * visible container region is 176x166 at UV 0,0 in a 256x256 image.
     * Real Slot positions remain authoritative in ObeliskPortalMenu.
     */
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(
                    ObeliskDepths.MOD_ID,
                    "textures/gui/container/obelisk_portal.png"
            );

    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 166;
    private static final int TEXTURE_WIDTH = 256;
    private static final int TEXTURE_HEIGHT = 256;
    private static final int VANILLA_SLOT_SIZE = 16;

    static final float TRIBUTE_SLOT_SCALE = 1.25F;
    static final int TRIBUTE_SLOT_RENDER_SIZE = 20;
    static final int TRIBUTE_SLOT_RENDER_OFFSET =
            (VANILLA_SLOT_SIZE - TRIBUTE_SLOT_RENDER_SIZE) / 2;

    static final int MODE_BUTTON_Y = 17;
    static final int MODE_BUTTON_WIDTH = 68;
    static final int MODE_BUTTON_HEIGHT = 20;
    static final int STARTER_BUTTON_X = 8;
    static final int OPEN_BUTTON_X = 100;
    static final int START_BUTTON_X = 104;
    static final int START_BUTTON_Y = 42;
    static final int START_BUTTON_WIDTH = 64;
    static final int START_BUTTON_HEIGHT = 20;
    static final int TRIBUTE_LABEL_X = 8;
    static final int TRIBUTE_LABEL_Y = 43;
    static final int STATUS_X = 8;
    static final int STATUS_Y = 68;
    static final int STATUS_MAX_WIDTH = 160;
    static final int INVENTORY_SLOT_TOP = 84;

    private Button starterOnlyButton;
    private Button openButton;
    private Button startButton;

    private int selectedButtonId = ObeliskPortalMenu.BUTTON_STARTER_ONLY;
    private boolean localSubmitting;
    private DungeonPreparationMenuState displayedState;
    private Component displayedStatus = Component.empty();

    public ObeliskPortalScreen(
            ObeliskPortalMenu menu,
            Inventory inventory,
            Component title
    ) {
        super(menu, inventory, title, GUI_WIDTH, GUI_HEIGHT);
    }

    @Override
    protected void init() {
        super.init();

        this.localSubmitting = false;

        this.starterOnlyButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.obeliskdepths.portal.mode.starter_only"),
                        button -> this.selectMode(ObeliskPortalMenu.BUTTON_STARTER_ONLY)
                )
                .bounds(
                        this.leftPos + STARTER_BUTTON_X,
                        this.topPos + MODE_BUTTON_Y,
                        MODE_BUTTON_WIDTH,
                        MODE_BUTTON_HEIGHT
                )
                .build());
        this.starterOnlyButton.setTooltip(Tooltip.create(Component.translatable(
                "gui.obeliskdepths.portal.mode.starter_only.tooltip"
        )));

        this.openButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.obeliskdepths.portal.mode.open"),
                        button -> this.selectMode(ObeliskPortalMenu.BUTTON_OPEN)
                )
                .bounds(
                        this.leftPos + OPEN_BUTTON_X,
                        this.topPos + MODE_BUTTON_Y,
                        MODE_BUTTON_WIDTH,
                        MODE_BUTTON_HEIGHT
                )
                .build());
        this.openButton.setTooltip(Tooltip.create(Component.translatable(
                "gui.obeliskdepths.portal.mode.open.tooltip"
        )));

        this.startButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.obeliskdepths.portal.start"),
                        button -> this.start()
                )
                .bounds(
                        this.leftPos + START_BUTTON_X,
                        this.topPos + START_BUTTON_Y,
                        START_BUTTON_WIDTH,
                        START_BUTTON_HEIGHT
                )
                .build());

        this.updateButtons();
        this.updateDisplayedStatus();
    }

    @Override
    protected void containerTick() {
        if (this.menu.isFailed() || this.menu.isCancelled() || this.menu.isReady()) {
            this.localSubmitting = false;
        }

        this.updateButtons();
        this.updateDisplayedStatus();
    }

    private void selectMode(int buttonId) {
        if (this.isInputLocked()) {
            return;
        }
        if (buttonId != ObeliskPortalMenu.BUTTON_STARTER_ONLY
                && buttonId != ObeliskPortalMenu.BUTTON_OPEN) {
            return;
        }

        this.selectedButtonId = buttonId;
        this.updateButtons();
    }

    private void start() {
        if (this.isInputLocked()) {
            return;
        }

        if (this.minecraft == null
                || this.minecraft.gameMode == null) {
            return;
        }

        this.localSubmitting = true;
        this.updateButtons();

        this.minecraft.gameMode.handleInventoryButtonClick(
                this.menu.containerId,
                this.selectedButtonId
        );
    }

    private boolean isSubmitting() {
        return this.localSubmitting || this.menu.isSubmitting();
    }

    private boolean isInputLocked() {
        return this.isSubmitting() || this.menu.isReady();
    }

    private void updateButtons() {
        boolean inputLocked = this.isInputLocked();

        if (this.starterOnlyButton != null) {
            this.starterOnlyButton.active = !inputLocked
                    && this.selectedButtonId != ObeliskPortalMenu.BUTTON_STARTER_ONLY;
        }

        if (this.openButton != null) {
            this.openButton.active = !inputLocked
                    && this.selectedButtonId != ObeliskPortalMenu.BUTTON_OPEN;
        }

        if (this.startButton != null) {
            this.startButton.active = !inputLocked;
        }
    }

    private void updateDisplayedStatus() {
        DungeonPreparationMenuState state = this.menu.preparationState();
        if (state.equals(this.displayedState)) {
            return;
        }
        this.displayedState = state;
        this.displayedStatus = statusLabel(state);
        if (this.startButton != null) {
            this.startButton.setTooltip(state.active()
                    || state.terminalStatus() != ObeliskPortalMenu.STATUS_IDLE
                    ? Tooltip.create(this.displayedStatus)
                    : null);
        }
    }

    @Override
    public void extractBackground(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                TEXTURE,
                this.leftPos,
                this.topPos,
                0.0F,
                0.0F,
                GUI_WIDTH,
                GUI_HEIGHT,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT
        );
    }

    @Override
    protected void extractLabels(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY
    ) {
        graphics.text(
                this.font,
                this.title,
                this.titleLabelX,
                this.titleLabelY,
                0x404040,
                false
        );

        graphics.text(
                this.font,
                Component.translatable("gui.obeliskdepths.portal.tribute"),
                TRIBUTE_LABEL_X,
                TRIBUTE_LABEL_Y,
                0x404040,
                false
        );

        Component footer = footerLabel();
        String clippedFooter = this.clippedFooterText(footer);
        graphics.text(
                this.font,
                Component.literal(clippedFooter),
                STATUS_X,
                STATUS_Y,
                footerColor(),
                false
        );
    }

    @Override
    protected void renderSlotContents(
            GuiGraphicsExtractor graphics,
            ItemStack itemStack,
            Slot slot,
            String itemCount
    ) {
        if (slot.index != ObeliskPortalMenu.TRIBUTE_SLOT) {
            super.renderSlotContents(graphics, itemStack, slot, itemCount);
            return;
        }

        float centerX = slot.x + VANILLA_SLOT_SIZE / 2.0F;
        float centerY = slot.y + VANILLA_SLOT_SIZE / 2.0F;
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);
        graphics.pose().scale(TRIBUTE_SLOT_SCALE, TRIBUTE_SLOT_SCALE);
        graphics.pose().translate(-centerX, -centerY);
        super.renderSlotContents(graphics, itemStack, slot, itemCount);
        graphics.pose().popMatrix();
    }

    @Override
    protected boolean isHovering(
            int left,
            int top,
            int width,
            int height,
            double mouseX,
            double mouseY
    ) {
        if (left == ObeliskPortalMenu.TRIBUTE_SLOT_X
                && top == ObeliskPortalMenu.TRIBUTE_SLOT_Y
                && width == VANILLA_SLOT_SIZE
                && height == VANILLA_SLOT_SIZE) {
            return super.isHovering(
                    left + TRIBUTE_SLOT_RENDER_OFFSET,
                    top + TRIBUTE_SLOT_RENDER_OFFSET,
                    TRIBUTE_SLOT_RENDER_SIZE,
                    TRIBUTE_SLOT_RENDER_SIZE,
                    mouseX,
                    mouseY
            );
        }
        return super.isHovering(
                left,
                top,
                width,
                height,
                mouseX,
                mouseY
        );
    }

    private String clippedFooterText(Component footer) {
        String text = footer.getString();

        if (this.font.width(text) <= STATUS_MAX_WIDTH) {
            return text;
        }

        String ellipsis = "...";
        int availableWidth = Math.max(
                0,
                STATUS_MAX_WIDTH - this.font.width(ellipsis)
        );

        return this.font.plainSubstrByWidth(
                text,
                availableWidth
        ) + ellipsis;
    }

    private Component footerLabel() {
        if (this.isSubmitting()
                || this.menu.isFailed()
                || this.menu.isCancelled()
                || this.menu.isReady()) {
            return this.displayedStatus;
        }
        return selectedModeLabel();
    }

    private int footerColor() {
        if (this.menu.isFailed()) {
            return 0xA00000;
        }
        if (this.menu.isCancelled()) {
            return 0xA06000;
        }
        return 0x404040;
    }

    private Component selectedModeLabel() {
        return Component.translatable(
                switch (this.selectedButtonId) {
                    case ObeliskPortalMenu.BUTTON_OPEN ->
                            "gui.obeliskdepths.portal.selected.open";
                    case ObeliskPortalMenu.BUTTON_STARTER_ONLY ->
                            "gui.obeliskdepths.portal.selected.starter_only";
                    default ->
                            "gui.obeliskdepths.portal.selected.starter_only";
                }
        );
    }

    static Component statusLabel(DungeonPreparationMenuState state) {
        if (state.terminalStatus() == ObeliskPortalMenu.STATUS_FAILED) {
            return Component.translatable(failureTranslationKey(
                    state.terminalReasonWireCode()
            ));
        }
        if (state.terminalStatus() == ObeliskPortalMenu.STATUS_CANCELLED) {
            return Component.translatable(cancellationTranslationKey(
                    -state.terminalReasonWireCode()
            ));
        }
        DungeonPreparationStage stage = state.stage().orElse(null);
        if (stage == null) {
            return Component.translatable("gui.obeliskdepths.portal.stage.unknown");
        }
        if (state.determinate()) {
            String progressKey = switch (stage) {
                case SCANNING_EXISTING_SITES ->
                        "gui.obeliskdepths.portal.stage.scanning.progress";
                case REQUESTING_ENTRY_CHUNKS, WAITING_FOR_ENTRY_CHUNKS,
                        VALIDATING_ENTRY_CHUNKS ->
                        "gui.obeliskdepths.portal.stage.entry_chunks.progress";
                case VALIDATING_ENTRY ->
                        "gui.obeliskdepths.portal.stage.validating_entry.progress";
                case SELECTING_CANDIDATE, REQUESTING_START_CHUNK,
                        WAITING_FOR_START_CHUNK, READING_STRUCTURE_START ->
                        "gui.obeliskdepths.portal.stage.generation.progress";
                default -> null;
            };
            if (progressKey != null) {
                return Component.translatable(
                        progressKey,
                        state.completed(),
                        state.total()
                );
            }
        }
        return Component.translatable(stageTranslationKey(stage));
    }

    private static String stageTranslationKey(DungeonPreparationStage stage) {
        return switch (stage) {
            case QUEUED -> "gui.obeliskdepths.portal.stage.queued";
            case VALIDATING -> "gui.obeliskdepths.portal.stage.validating";
            case SCANNING_EXISTING_SITES -> "gui.obeliskdepths.portal.stage.scanning";
            case SELECTING_CANDIDATE -> "gui.obeliskdepths.portal.stage.selecting";
            case REQUESTING_START_CHUNK -> "gui.obeliskdepths.portal.stage.requesting_start";
            case WAITING_FOR_START_CHUNK -> "gui.obeliskdepths.portal.stage.waiting_start";
            case READING_STRUCTURE_START -> "gui.obeliskdepths.portal.stage.reading_start";
            case PLANNING_ENTRY_CHUNKS -> "gui.obeliskdepths.portal.stage.planning_entry";
            case REQUESTING_ENTRY_CHUNKS -> "gui.obeliskdepths.portal.stage.requesting_entry";
            case WAITING_FOR_ENTRY_CHUNKS -> "gui.obeliskdepths.portal.stage.waiting_entry";
            case VALIDATING_ENTRY_CHUNKS -> "gui.obeliskdepths.portal.stage.validating_chunks";
            case VALIDATING_ENTRY -> "gui.obeliskdepths.portal.stage.validating_entry";
            case READY_TO_COMMIT -> "gui.obeliskdepths.portal.stage.ready_to_commit";
            case COMMITTING -> "gui.obeliskdepths.portal.stage.committing";
            case READY -> "gui.obeliskdepths.portal.stage.ready";
            case FAILED -> "gui.obeliskdepths.portal.failed";
            case CANCELLED -> "gui.obeliskdepths.portal.cancelled";
        };
    }

    private static String failureTranslationKey(int wireCode) {
        return DungeonPreparationJobFailureReason.fromWireCode(wireCode)
                .map(reason -> switch (reason) {
                    case INVALID_TRIBUTE -> "gui.obeliskdepths.portal.failed.invalid_tribute";
                    case NO_SITE_AVAILABLE -> "gui.obeliskdepths.portal.failed.no_site";
                    case NON_AUTHORITATIVE_SITE -> "gui.obeliskdepths.portal.failed.non_authoritative_site";
                    case SITE_CONFLICT -> "gui.obeliskdepths.portal.failed.site_conflict";
                    case CHUNK_LOAD_FAILED -> "gui.obeliskdepths.portal.failed.chunk_load";
                    case STRUCTURE_START_MISSING -> "gui.obeliskdepths.portal.failed.structure_missing";
                    case STRUCTURE_START_INVALID -> "gui.obeliskdepths.portal.failed.structure_invalid";
                    case ENTRY_VALIDATION_FAILED -> "gui.obeliskdepths.portal.failed.entry_validation";
                    case COMMIT_VALIDATION_FAILED -> "gui.obeliskdepths.portal.failed.commit_validation";
                    case PORTAL_SPAWN_FAILED -> "gui.obeliskdepths.portal.failed.portal_spawn";
                    case PREPARED_ENTRY_REGISTRATION_FAILED -> "gui.obeliskdepths.portal.failed.prepared_entry";
                    case SITE_CLAIM_LOST -> "gui.obeliskdepths.portal.failed.site_claim_lost";
                    case INTERNAL_ERROR -> "gui.obeliskdepths.portal.failed.internal";
                    case AUTHORITATIVE_RUNTIME_UNAVAILABLE -> "gui.obeliskdepths.portal.failed.runtime_unavailable";
                    case AUTHORITATIVE_JOB_MISSING -> "gui.obeliskdepths.portal.failed.job_missing";
                    case SUBMISSION_REJECTED -> "gui.obeliskdepths.portal.failed.submission_rejected";
                })
                .orElse("gui.obeliskdepths.portal.failed");
    }

    private static String cancellationTranslationKey(int wireCode) {
        return DungeonPreparationCancellationReason.fromWireCode(wireCode)
                .map(reason -> switch (reason) {
                    case USER_CANCELLED -> "gui.obeliskdepths.portal.cancelled.user";
                    case PLAYER_DISCONNECTED -> "gui.obeliskdepths.portal.cancelled.player_disconnected";
                    case PLAYER_DIMENSION_CHANGED -> "gui.obeliskdepths.portal.cancelled.dimension_changed";
                    case PLAYER_MOVED_TOO_FAR -> "gui.obeliskdepths.portal.cancelled.moved_too_far";
                    case PLAYER_DIED -> "gui.obeliskdepths.portal.cancelled.player_died";
                    case OBELISK_INVALID -> "gui.obeliskdepths.portal.cancelled.obelisk_invalid";
                    case MENU_CLOSED -> "gui.obeliskdepths.portal.cancelled.menu_closed";
                    case LEVEL_UNLOADED -> "gui.obeliskdepths.portal.cancelled.level_unloaded";
                    case SERVER_STOPPING -> "gui.obeliskdepths.portal.cancelled.server_stopping";
                    case TIMEOUT -> "gui.obeliskdepths.portal.cancelled.timeout";
                })
                .orElse("gui.obeliskdepths.portal.cancelled");
    }
}
