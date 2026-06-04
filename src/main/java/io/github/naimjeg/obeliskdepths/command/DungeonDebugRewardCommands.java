package io.github.naimjeg.obeliskdepths.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.reward.DungeonRewardClaim;
import io.github.naimjeg.obeliskdepths.dungeon.reward.DungeonRewardClaimResult;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomState;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

final class DungeonDebugRewardCommands {
    private DungeonDebugRewardCommands() {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("claim-room-reward-debug")
                        .executes(context -> claimRoomRewardDebug(context.getSource())))
                .then(Commands.literal("reward")
                        .then(Commands.literal("status")
                                .executes(context -> status(context.getSource())))
                        .then(Commands.literal("claim-current-room")
                                .executes(context -> claimRoomRewardDebug(context.getSource()))));
    }

    private static int status(CommandSourceStack source) {
        DungeonDebugCommandUtil.info(
                source,
                "Reward chests use persisted Obelisk Chest block entities and deterministic claim validation."
        );
        DungeonDebugCommandUtil.info(
                source,
                "Runtime reward artifact cleanup validates saved reward and instance ownership before removing chests."
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int claimRoomRewardDebug(CommandSourceStack source) {
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

        Optional<DungeonRoomState> state =
                io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData
                        .get(level.get())
                        .roomStates()
                        .get(instance.get().id(), room.get().id());

        if (state.isEmpty()) {
            DungeonDebugCommandUtil.failure(source, "Current room state is missing.");
            return 0;
        }

        DungeonRewardClaimResult result = DungeonRewardClaim.validateRewardClaimable(
                level.get(),
                instance.get().id(),
                room.get().id()
        );

        if (result != DungeonRewardClaimResult.SUCCESS) {
            DungeonDebugCommandUtil.failure(source, "Room reward debug claim failed: " + result);
            return 0;
        }

        DungeonDebugCommandUtil.success(
                source,
                "Validated room reward claimability for "
                        + room.get().id()
                        + ". Physical claims must use the reward chest."
        );
        return Command.SINGLE_SUCCESS;
    }
}
