package io.github.naimjeg.obeliskdepths.client.screen;

import io.github.naimjeg.obeliskdepths.menu.ObeliskPortalMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class ObeliskPortalScreen extends AbstractContainerScreen<ObeliskPortalMenu> {
    private Button soloButton;
    private Button openJoinButton;
    private Button startButton;

    private int selectedButtonId = ObeliskPortalMenu.BUTTON_OPEN_JOIN;
    private boolean localSubmitting;

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

        this.openJoinButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.obeliskdepths.portal.mode.open_join"),
                        button -> this.selectMode(ObeliskPortalMenu.BUTTON_OPEN_JOIN)
                )
                .bounds(this.leftPos + 96, this.topPos + 18, 56, 20)
                .build());

        this.startButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.obeliskdepths.portal.start"),
                        button -> this.start()
                )
                .bounds(this.leftPos + 56, this.topPos + 58, 64, 20)
                .build());

        this.updateButtons();
    }

    @Override
    protected void containerTick() {
        if (this.menu.isFailed()) {
            this.localSubmitting = false;
        }

        this.updateButtons();
    }

    private void selectMode(int buttonId) {
        if (this.isSubmitting()) {
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

        if (this.openJoinButton != null) {
            this.openJoinButton.active = !submitting
                    && this.selectedButtonId != ObeliskPortalMenu.BUTTON_OPEN_JOIN;
        }

        if (this.startButton != null) {
            this.startButton.active = !submitting;
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

        Component selected = this.selectedButtonId == ObeliskPortalMenu.BUTTON_SOLO
                ? Component.translatable("gui.obeliskdepths.portal.selected.solo")
                : Component.translatable("gui.obeliskdepths.portal.selected.open_join");

        graphics.text(
                this.font,
                selected,
                24,
                82,
                0x404040,
                false
        );

        graphics.text(
                this.font,
                Component.translatable("gui.obeliskdepths.portal.note"),
                24,
                94,
                0x606060,
                false
        );

        if (this.isSubmitting()) {
            graphics.text(
                    this.font,
                    Component.translatable("gui.obeliskdepths.portal.loading"),
                    56,
                    70,
                    0x404040,
                    false
            );
        } else if (this.menu.isFailed()) {
            graphics.text(
                    this.font,
                    Component.translatable("gui.obeliskdepths.portal.failed"),
                    56,
                    70,
                    0xA00000,
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
}
