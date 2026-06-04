package io.github.naimjeg.obeliskdepths.client.screen;

import io.github.naimjeg.obeliskdepths.dungeon.interaction.DungeonPortalEntryOperationId;
import io.github.naimjeg.obeliskdepths.dungeon.interaction.DungeonPortalEntryOperationState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Objects;

/** Presentation-only view of the latest authoritative server operation state. */
public final class DungeonLoadingScreen extends Screen {
    private final DungeonPortalEntryOperationId operationId;
    private DungeonPortalEntryOperationState state;

    public DungeonLoadingScreen(
            DungeonPortalEntryOperationId operationId,
            DungeonPortalEntryOperationState initialState
    ) {
        super(Component.translatable(
                "gui.obeliskdepths.dungeon_loading.title"
        ));
        this.operationId = Objects.requireNonNull(operationId, "operationId");
        this.state = Objects.requireNonNull(initialState, "initialState");
    }

    public boolean matches(DungeonPortalEntryOperationId operationId) {
        return this.operationId.equals(operationId);
    }

    public void updateState(DungeonPortalEntryOperationState state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected boolean shouldNarrateNavigation() {
        return false;
    }

    @Override
    public void extractBackground(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        graphics.fill(0, 0, this.width, this.height, 0xF0101018);
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(
                this.font,
                this.title,
                this.width / 2,
                this.height / 2 - 24,
                0xFFFFFF
        );
        graphics.centeredText(
                this.font,
                statusLabel(this.state),
                this.width / 2,
                this.height / 2,
                0xC8D8FF
        );
    }

    static Component statusLabel(DungeonPortalEntryOperationState state) {
        String key = switch (state) {
            case AWAITING_CLIENT_READY ->
                    "gui.obeliskdepths.dungeon_loading.awaiting_client";
            case PREPARING ->
                    "gui.obeliskdepths.dungeon_loading.preparing";
            case READY_TO_TELEPORT ->
                    "gui.obeliskdepths.dungeon_loading.ready";
            case TELEPORTING ->
                    "gui.obeliskdepths.dungeon_loading.teleporting";
            case FINALIZING ->
                    "gui.obeliskdepths.dungeon_loading.finalizing";
            case COMPLETED ->
                    "gui.obeliskdepths.dungeon_loading.completed";
            case FAILED ->
                    "gui.obeliskdepths.dungeon_loading.failed";
            case CANCELLED ->
                    "gui.obeliskdepths.dungeon_loading.cancelled";
        };
        return Component.translatable(key);
    }
}
