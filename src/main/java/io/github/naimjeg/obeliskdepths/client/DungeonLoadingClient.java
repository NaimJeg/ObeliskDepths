package io.github.naimjeg.obeliskdepths.client;

import io.github.naimjeg.obeliskdepths.client.screen.DungeonLoadingScreen;
import io.github.naimjeg.obeliskdepths.dungeon.interaction.DungeonPortalEntryOperationId;
import io.github.naimjeg.obeliskdepths.dungeon.interaction.DungeonPortalEntryOperationState;
import io.github.naimjeg.obeliskdepths.dungeon.interaction.DungeonPortalEntryResult;
import io.github.naimjeg.obeliskdepths.network.ServerboundDungeonLoadingReadyPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** Client-thread UI adapter. No preparation or teleport business logic lives here. */
//@OnlyIn(Dist.CLIENT)
public final class DungeonLoadingClient {
    private DungeonLoadingClient() {
    }

    public static void open(
            DungeonPortalEntryOperationId operationId,
            DungeonPortalEntryOperationState initialState
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        DungeonLoadingScreen screen;
        if (minecraft.screen instanceof DungeonLoadingScreen existing
                && existing.matches(operationId)) {
            existing.updateState(initialState);
            screen = existing;
        } else {
            screen = new DungeonLoadingScreen(operationId, initialState);
            minecraft.setScreen(screen);
        }

        // The acknowledgement is sent only after setScreen has made this exact
        // operation's screen current on the client game thread.
        if (minecraft.screen == screen) {
            ClientPacketDistributor.sendToServer(
                    new ServerboundDungeonLoadingReadyPayload(operationId)
            );
        }
    }

    public static void update(
            DungeonPortalEntryOperationId operationId,
            DungeonPortalEntryOperationState state
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof DungeonLoadingScreen screen
                && screen.matches(operationId)) {
            screen.updateState(state);
        }
    }

    public static void finish(
            DungeonPortalEntryOperationId operationId,
            DungeonPortalEntryOperationState terminalState,
            DungeonPortalEntryResult result
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof DungeonLoadingScreen screen
                && screen.matches(operationId)) {
            screen.updateState(terminalState);
            minecraft.setScreen(null);
        }
        if (result != DungeonPortalEntryResult.SUCCESS
                && minecraft.player != null) {
            minecraft.player.sendOverlayMessage(
                    Component.translatable(result.translationKey())
            );
        }
    }
}
