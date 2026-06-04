package io.github.naimjeg.obeliskdepths.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public final class ObeliskDepthsCommand {
    private ObeliskDepthsCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("obeliskdepths")
                        .requires(ObeliskDepthsCommand::canUseDebugCommands)
                        .then(DungeonDebugCommands.dungeon())
        );
    }

    private static boolean canUseDebugCommands(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();

        /*
         * Non-player sources:
         * - dedicated server console
         * - command execution context without player entity
         *
         * Allow them for debug/admin commands.
         */
        if (player == null) {
            return true;
        }

        return source.getServer()
                .getPlayerList()
                .isOp(player.nameAndId());
    }
}