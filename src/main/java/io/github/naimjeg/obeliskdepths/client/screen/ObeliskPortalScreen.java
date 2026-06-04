package io.github.naimjeg.obeliskdepths.client.screen;

import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationCancellationReason;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationJobFailureReason;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationStage;
import io.github.naimjeg.obeliskdepths.menu.DungeonPreparationMenuState;
import io.github.naimjeg.obeliskdepths.menu.ObeliskPortalMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class ObeliskPortalScreen extends AbstractContainerScreen<ObeliskPortalMenu> {
    private Button soloButton;
    private Button startButton;

    private int selectedButtonId = ObeliskPortalMenu.BUTTON_SOLO;
    private boolean localSubmitting;
    private DungeonPreparationMenuState displayedState;
    private Component displayedStatus = Component.empty();

    public ObeliskPortalScreen(
            ObeliskPortalMenu menu,
            Inventory inventory,
            Component title
    ) {
        super(menu, inventory, title, 176, 166);
    }

    @Override
    protected void init() {
        super.init();

        this.localSubmitting = false;

        this.soloButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.obeliskdepths.portal.mode.solo"),
                        button -> this.selectMode(ObeliskPortalMenu.BUTTON_SOLO)
                )
                .bounds(this.leftPos + 24, this.topPos + 18, 56, 20)
                .build());

        this.startButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.obeliskdepths.portal.start"),
                        button -> this.start()
                )
                .bounds(this.leftPos + 56, this.topPos + 58, 64, 20)
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
        if (this.isSubmitting()) {
            return;
        }
        if (buttonId != ObeliskPortalMenu.BUTTON_SOLO) {
            return;
        }

        this.selectedButtonId = buttonId;
        this.updateButtons();
    }

    private void start() {
        if (this.isSubmitting()) {
            return;
        }

        this.localSubmitting = true;
        this.updateButtons();

        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(
                    this.menu.containerId,
                    this.selectedButtonId
            );
        }
    }

    private boolean isSubmitting() {
        return this.localSubmitting || this.menu.isSubmitting();
    }

    private void updateButtons() {
        boolean submitting = this.isSubmitting();

        if (this.soloButton != null) {
            this.soloButton.active = !submitting
                    && this.selectedButtonId != ObeliskPortalMenu.BUTTON_SOLO;
        }

        if (this.startButton != null) {
            this.startButton.active = !submitting;
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

        int left = this.leftPos;
        int top = this.topPos;

        graphics.fill(
                left,
                top,
                left + this.imageWidth,
                top + this.imageHeight,
                0xFFC6C6C6
        );

        graphics.fill(
                left + 7,
                top + 7,
                left + this.imageWidth - 7,
                top + this.imageHeight - 7,
                0xFFE0E0E0
        );

        int slotX = left + ObeliskPortalMenu.TRIBUTE_SLOT_X;
        int slotY = top + ObeliskPortalMenu.TRIBUTE_SLOT_Y;

        graphics.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, 0xFF8B8B8B);
        graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0xFF373737);
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
                58,
                47,
                0x404040,
                false
        );

        Component selected = Component.translatable(
                "gui.obeliskdepths.portal.selected.solo"
        );

        graphics.text(
                this.font,
                selected,
                24,
                82,
                0x404040,
                false
        );

        if (this.isSubmitting()) {
            graphics.text(
                    this.font,
                    this.displayedStatus,
                    56,
                    70,
                    0x404040,
                    false
            );
        } else if (this.menu.isFailed()) {
            graphics.text(
                    this.font,
                    this.displayedStatus,
                    56,
                    70,
                    0xA00000,
                    false
            );
        } else if (this.menu.isCancelled()) {
            graphics.text(
                    this.font,
                    this.displayedStatus,
                    56,
                    70,
                    0xA06000,
                    false
            );
        }

        graphics.text(
                this.font,
                this.playerInventoryTitle,
                this.inventoryLabelX,
                this.inventoryLabelY,
                0x404040,
                false
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
