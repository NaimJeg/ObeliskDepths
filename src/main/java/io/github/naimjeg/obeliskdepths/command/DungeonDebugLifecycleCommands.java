package io.github.naimjeg.obeliskdepths.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.completion.DungeonCompletionResult;
import io.github.naimjeg.obeliskdepths.dungeon.completion.DungeonCompletionService;
import io.github.naimjeg.obeliskdepths.dungeon.lifecycle.DungeonCleanupService;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomRuntimeService;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSessionCleanup;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

final class DungeonDebugLifecycleCommands {
    private DungeonDebugLifecycleCommands() {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("close-empty")
                        .executes(context -> closeEmpty(context.getSource())))
                .then(Commands.literal("purge-expired-sessions")
                        .executes(context -> purgeExpiredSessions(context.getSource())))
                .then(Commands.literal("cleanup-closed")
                        .executes(context -> cleanupClosed(context.getSource())))
                .then(Commands.literal("clear-room-here")
                        .executes(context -> clearRoomHere(context.getSource())))
                .then(Commands.literal("complete-debug")
                        .executes(context -> completeDebug(context.getSource())))
                .then(Commands.literal("close")
                        .executes(context -> closeEmpty(context.getSource())))
                .then(Commands.literal("cleanup")
                        .executes(context -> cleanupClosed(context.getSource())))
                .then(Commands.literal("abandon")
                        .executes(context -> abandonCurrent(context.getSource())));
    }

    private static int closeEmpty(CommandSourceStack source) {
        Optional<ServerLevel> level = DungeonDebugCommandUtil.requireDungeonLevel(source);

        if (level.isEmpty()) {
            return 0;
        }

        DungeonManagerSavedData data = DungeonManagerSavedData.get(level.get());
        int removedSessions =
                data.portalSessions().removeForInactiveInstances(instanceId ->
                        data.instances()
                                .get(instanceId)
                                .filter(instance -> instance.status()
                                        == io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonStatus.ACTIVE)
                                .isPresent()
                );
        DungeonDebugCommandUtil.success(
                source,
                "Close-empty instance cleanup is not implemented for the current runtime model; removed stale portal sessions: "
                        + removedSessions
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int purgeExpiredSessions(CommandSourceStack source) {
        Optional<ServerLevel> level = DungeonDebugCommandUtil.requireDungeonLevel(source);

        if (level.isEmpty()) {
            return 0;
        }

        int purged = DungeonManagerSavedData.get(level.get())
                .portalSessions()
                .purgeExpired(level.get().getGameTime());
        DungeonDebugCommandUtil.success(source, "Purged expired portal sessions: " + purged);
        return Command.SINGLE_SUCCESS;
    }

    private static int cleanupClosed(CommandSourceStack source) {
        Optional<ServerLevel> level = DungeonDebugCommandUtil.requireDungeonLevel(source);

        if (level.isEmpty()) {
            return 0;
        }

        int cleaned = DungeonCleanupService.cleanupPortalClosedInstances(level.get());
        DungeonDebugCommandUtil.success(
                source,
                "Cleaned portal-closed dungeon instances: " + cleaned
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int clearRoomHere(CommandSourceStack source) {
        Optional<ServerPlayer> player = DungeonDebugCommandUtil.requirePlayer(source);
        Optional<ServerLevel> level = DungeonDebugCommandUtil.requireDungeonLevel(source);

        if (player.isEmpty() || level.isEmpty()) {
            return 0;
        }

        var instance = DungeonDebugCommandUtil.currentInstance(level.get(), player.get());

        if (instance.isEmpty()) {
            DungeonDebugCommandUtil.failure(source, "You are not bound to a dungeon instance.");
            return 0;
        }

        var site = DungeonDebugCommandUtil.currentSite(level.get(), instance.get());

        if (site.isEmpty()) {
            DungeonDebugCommandUtil.failure(source, "Current dungeon site metadata is missing.");
            return 0;
        }

        var room = site.get().roomAt(player.get().blockPosition());

        if (room.isEmpty()) {
            DungeonDebugCommandUtil.failure(source, "You are not inside a generated dungeon room.");
            return 0;
        }

        boolean changed = DungeonRoomRuntimeService.markRoomClearedForDebug(
                level.get(),
                instance.get().id(),
                room.get().id()
        );

        DungeonDebugCommandUtil.success(
                source,
                changed
                        ? "Cleared room " + room.get().id()
                        : "Room was already clear or has no runtime state: " + room.get().id()
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int completeDebug(CommandSourceStack source) {
        Optional<ServerPlayer> player = DungeonDebugCommandUtil.requirePlayer(source);

        if (player.isEmpty()) {
            return 0;
        }

        DungeonCompletionResult result =
                DungeonCompletionService.enterRewardPhase(player.get());

        if (result != DungeonCompletionResult.SUCCESS) {
            DungeonDebugCommandUtil.failure(source, "Dungeon completion failed: " + result);
            return 0;
        }

        DungeonDebugCommandUtil.success(source, "Dungeon completed; reward phase started.");
        return Command.SINGLE_SUCCESS;
    }

    private static int abandonCurrent(CommandSourceStack source) {
        Optional<ServerPlayer> player = DungeonDebugCommandUtil.requirePlayer(source);
        Optional<ServerLevel> level = DungeonDebugCommandUtil.requireDungeonLevel(source);

        if (player.isEmpty() || level.isEmpty()) {
            return 0;
        }

        var instance = DungeonDebugCommandUtil.currentInstance(level.get(), player.get());

        if (instance.isEmpty()) {
            DungeonDebugCommandUtil.failure(source, "You are not bound to a dungeon instance.");
            return 0;
        }

        var session = DungeonDebugCommandUtil.currentSession(level.get(), instance.get());

        if (session.isEmpty()) {
            DungeonDebugCommandUtil.failure(source, "Current dungeon session is missing.");
            return 0;
        }

        DungeonSessionCleanup.abandonAndCleanup(level.get(), session.get());
        DungeonDebugCommandUtil.success(
                source,
                "Abandoned and cleaned current dungeon instance " + instance.get().id() + "."
        );
        return Command.SINGLE_SUCCESS;
    }
}
