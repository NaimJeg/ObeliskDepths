package io.github.naimjeg.obeliskdepths.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.debug.DungeonDebugChunkWarmupService;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonDifficulty;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstanceService;
import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonReturnResult;
import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonReturnService;
import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonTracker;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSessionLifecycle;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSitePlacement;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import io.github.naimjeg.obeliskdepths.world.ObeliskDepthsTeleporter;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

final class DungeonDebugTravelCommands {
    private static final int DEFAULT_DEV_WARMUP_RADIUS_CHUNKS = 6;

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
                        .executes(context -> enterDepths(context.getSource(), Optional.empty())))
                .then(Commands.literal("dev-start-nearest")
                        .executes(context -> devStartNearest(
                                context.getSource(),
                                DEFAULT_DEV_WARMUP_RADIUS_CHUNKS
                        ))
                        .then(Commands.argument(
                                        "warmupRadiusChunks",
                                        IntegerArgumentType.integer(
                                                0,
                                                DungeonDebugChunkWarmupService.MAX_RADIUS_CHUNKS
                                        )
                                )
                                .executes(context -> devStartNearest(
                                        context.getSource(),
                                        IntegerArgumentType.getInteger(context, "warmupRadiusChunks")
                                ))));
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

        if (requestedXZ.isEmpty()) {
            DungeonManagerSavedData data = DungeonManagerSavedData.get(level.get());
            target = DungeonSiteDebugCommands.findNearestGeneratedReservable(
                            level.get(),
                            origin,
                            data
                    )
                    .map(resolved -> DungeonSiteDebugCommands.safeEntryPos(resolved.site()))
                    .orElse(origin);
        }

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

    private static int devStartNearest(
            CommandSourceStack source,
            int warmupRadiusChunks
    ) {
        Optional<ServerPlayer> player = DungeonDebugCommandUtil.requirePlayer(source);
        Optional<ServerLevel> level = DungeonDebugCommandUtil.requireDungeonLevel(source);

        if (player.isEmpty() || level.isEmpty()) {
            return 0;
        }

        BlockPos origin = DungeonSiteDebugCommands.originInDungeonLevel(source);
        int warmedChunks = DungeonDebugChunkWarmupService.warmupChunks(
                level.get(),
                origin,
                warmupRadiusChunks
        );

        DungeonDifficulty debugDifficulty = new DungeonDifficulty(
                1,
                0.0F,
                1.0F,
                1
        );

        Optional<DungeonInstance> created =
                DungeonInstanceService.reserveNearestUnreachedWorldgenSite(
                        level.get(),
                        origin,
                        debugDifficulty
                );

        if (created.isEmpty()) {
            DungeonSiteDebugCommands.sendNoGeneratedSiteDiagnostic(
                    source,
                    level.get(),
                    origin,
                    true,
                    warmedChunks
            );
            DungeonDebugCommandUtil.failure(
                    source,
                    "dev-start-nearest did not create an instance. Check generated-site locator and projection logs."
            );
            return 0;
        }

        DungeonInstance instance = created.get();
        var returnDimension = player.get().level().dimension();
        BlockPos returnPos = player.get().blockPosition();
        Optional<ServerPlayer> teleported =
                ObeliskDepthsTeleporter.teleportToInstanceStart(player.get(), instance);

        if (teleported.isEmpty()) {
            DungeonInstanceService.releaseFailedReservation(level.get(), instance.id());
            DungeonDebugCommandUtil.failure(
                    source,
                    "Failed to teleport to reserved dungeon instance start."
            );
            return 0;
        }

        ServerPlayer enteredPlayer = teleported.get();
        DungeonManagerSavedData.get(level.get()).instances().addParticipant(
                instance.id(),
                enteredPlayer.getUUID(),
                level.get().getGameTime()
        );
        PlayerDungeonTracker.bindPlayerToInstance(
                enteredPlayer,
                instance.id(),
                returnDimension,
                returnPos
        );
        DungeonSessionLifecycle.getOrCreateDebugSession(
                level.get(),
                instance,
                enteredPlayer.getUUID()
        );
        DungeonSessionLifecycle.registerParticipant(
                level.get(),
                instance.id(),
                enteredPlayer.getUUID()
        );
        DungeonSessionLifecycle.registerPhysicalParticipant(
                level.get(),
                instance.id(),
                enteredPlayer.getUUID()
        );

        DungeonDebugCommandUtil.success(
                source,
                "Dev-started dungeon instance "
                        + instance.id()
                        + " from authoritative generated site "
                        + instance.siteKey()
                        + " start="
                        + instance.startPos()
                        + ", warmedChunks="
                        + warmedChunks
                        + ". This is a debug runtime start, not portal gameplay."
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
