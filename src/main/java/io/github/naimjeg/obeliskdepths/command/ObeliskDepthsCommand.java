package io.github.naimjeg.obeliskdepths.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class ObeliskDepthsCommand {
    private ObeliskDepthsCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("obeliskdepths")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(DungeonDebugCommands.dungeon())
        );
    }
}
