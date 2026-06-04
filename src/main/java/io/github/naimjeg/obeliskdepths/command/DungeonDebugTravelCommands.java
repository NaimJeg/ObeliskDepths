package io.github.naimjeg.obeliskdepths.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonReturnResult;
import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonReturnService;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSitePlacement;
import io.github.naimjeg.obeliskdepths.world.ObeliskDepthsTeleporter;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

final class DungeonDebugTravelCommands {
    private DungeonDebugTravelCommands() {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("return")
                        .executes(context -> returnPlayer(context.getSource())))
                .then(Commands.literal("enter-depths")
                        .executes(context -> enterDepths(context.getSource(), Optional.empty()))
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .executes(context -> enterDepths(
                                                context.getSource(),
                                                Optional.of(new DebugXZ(
                                                        IntegerArgumentType.getInteger(context, "x"),
                                                        IntegerArgumentType.getInteger(context, "z")
                                                ))
                                        )))))
                .then(Commands.literal("enter-depths-here")
                        .executes(context -> enterDepths(context.getSource(), Optional.empty())));
    }

    private static int returnPlayer(CommandSourceStack source) {
        Optional<ServerPlayer> player = DungeonDebugCommandUtil.requirePlayer(source);

        if (player.isEmpty()) {
            return 0;
        }

        PlayerDungeonReturnResult result =
                PlayerDungeonReturnService.returnPlayer(player.get());

        if (result != PlayerDungeonReturnResult.SUCCESS) {
            DungeonDebugCommandUtil.failure(source, "Failed to return from dungeon: " + result);
            return 0;
        }

        DungeonDebugCommandUtil.success(source, "Returned from dungeon.");
        return Command.SINGLE_SUCCESS;
    }

    private static int enterDepths(
            CommandSourceStack source,
            Optional<DebugXZ> requestedXZ
    ) {
        Optional<ServerPlayer> player = DungeonDebugCommandUtil.requirePlayer(source);
        Optional<ServerLevel> level = DungeonDebugCommandUtil.requireDungeonLevel(source);

        if (player.isEmpty() || level.isEmpty()) {
            return 0;
        }

        BlockPos origin = requestedXZ
                .map(xz -> debugPos(xz.x(), xz.z()))
                .orElseGet(() -> debugPos(
                        player.get().blockPosition().getX(),
                        player.get().blockPosition().getZ()
                ));

        BlockPos target = origin;

        if (ObeliskDepthsTeleporter.teleportToLevel(player.get(), level.get(), target).isEmpty()) {
            DungeonDebugCommandUtil.failure(source, "Failed to enter ObeliskDepths dimension.");
            return 0;
        }

        DungeonDebugCommandUtil.success(
                source,
                "Entered ObeliskDepths dimension at "
                        + target
                        + ". Debug entry only: no portal session, no reservation, no runtime instance was created."
        );
        return Command.SINGLE_SUCCESS;
    }

    private static BlockPos debugPos(
            int x,
            int z
    ) {
        return new BlockPos(
                x,
                DungeonSitePlacement.PREVIEW_Y + 2,
                z
        );
    }

    private record DebugXZ(
            int x,
            int z
    ) {
    }
}
