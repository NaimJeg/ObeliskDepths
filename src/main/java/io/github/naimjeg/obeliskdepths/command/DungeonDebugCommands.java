package io.github.naimjeg.obeliskdepths.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class DungeonDebugCommands {
    private DungeonDebugCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> dungeon() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("dungeon");

        DungeonDebugInfoCommands.register(root);
        DungeonDebugTravelCommands.register(root);
        DungeonDebugLifecycleCommands.register(root);
        DungeonDebugEncounterCommands.register(root);
        DungeonDebugLayoutCommands.register(root);
        DungeonDebugRewardCommands.register(root);
        root.then(DungeonSiteDebugCommands.site());

        return root;
    }
}
