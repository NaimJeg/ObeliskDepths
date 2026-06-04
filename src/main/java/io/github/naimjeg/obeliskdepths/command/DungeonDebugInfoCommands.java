package io.github.naimjeg.obeliskdepths.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonTracker;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonGeneratedRoom;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

final class DungeonDebugInfoCommands {
    private DungeonDebugInfoCommands() {
    }

    static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("where")
                        .executes(context -> where(context.getSource())))
                .then(Commands.literal("stats")
                        .executes(context -> stats(context.getSource())))
                .then(Commands.literal("list")
                        .executes(context -> list(context.getSource())))
                .then(Commands.literal("site")
                        .then(Commands.literal("current")
                                .executes(context -> currentSite(context.getSource()))))
                .then(Commands.literal("rooms")
                        .executes(context -> rooms(context.getSource()))
                        .then(Commands.literal("current")
                                .executes(context -> rooms(context.getSource()))))
                .then(Commands.literal("room")
                        .then(Commands.literal("current")
                                .executes(context -> room(context.getSource()))))
                .then(Commands.literal("room-here-debug")
                        .executes(context -> room(context.getSource())));
    }

    private static int where(CommandSourceStack source) {
        Optional<ServerPlayer> player = DungeonDebugCommandUtil.requirePlayer(source);

        if (player.isEmpty()) {
            return 0;
        }

        PlayerDungeonTracker.get(player.get())
                .ifPresentOrElse(
                        data -> DungeonDebugCommandUtil.info(
                                source,
                                "Dungeon binding: instance="
                                        + data.currentInstanceId().map(Object::toString).orElse("<missing>")
                                        + ", returnDimension="
                                        + data.returnDimension().map(Object::toString).orElse("<missing>")
                                        + ", returnPos="
                                        + data.returnPos().map(Object::toString).orElse("<missing>")
                        ),
                        () -> DungeonDebugCommandUtil.info(
                                source,
                                "You are not bound to a dungeon instance."
                        )
                );

        return Command.SINGLE_SUCCESS;
    }

    private static int stats(CommandSourceStack source) {
        Optional<ServerLevel> level = DungeonDebugCommandUtil.requireDungeonLevel(source);

        if (level.isEmpty()) {
            return 0;
        }

        DungeonManagerSavedData data = DungeonManagerSavedData.get(level.get());
        DungeonDebugCommandUtil.info(
                source,
                "Dungeon stats: instances="
                        + data.instances().size()
                        + ", territories="
                        + data.territories().size()
                        + ", portalSessions="
                        + data.portalSessions().size()
                        + ", siteRecords="
                        + data.sites().recordCount()
                        + ", reservedSites="
                        + data.sites().reservedCount()
                        + ", retiredSites="
                        + data.sites().retiredCount()
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int list(CommandSourceStack source) {
        Optional<ServerLevel> level = DungeonDebugCommandUtil.requireDungeonLevel(source);

        if (level.isEmpty()) {
            return 0;
        }

        DungeonManagerSavedData data = DungeonManagerSavedData.get(level.get());

        if (data.instances().isEmpty()) {
            DungeonDebugCommandUtil.info(source, "No dungeon instances.");
            return Command.SINGLE_SUCCESS;
        }

        for (var instance : data.instances().all()) {
            DungeonDebugCommandUtil.info(
                    source,
                    "Instance "
                            + instance.id()
                            + " status="
                            + instance.status().getSerializedName()
                            + " participants="
                            + instance.participants().size()
                            + " start="
                            + instance.startPos()
                            + " territory="
                            + instance.territoryId()
                            + " site="
                            + instance.siteKey()
            );
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int currentSite(CommandSourceStack source) {
        Optional<ResolvedCurrentSite> resolved = resolveCurrentSite(source);

        if (resolved.isEmpty()) {
            return 0;
        }

        DungeonSite site = resolved.get().site();
        Map<DungeonRoomType, Integer> roomCounts = new EnumMap<>(DungeonRoomType.class);

        for (DungeonGeneratedRoom room : site.rooms()) {
            roomCounts.merge(room.type(), 1, Integer::sum);
        }

        DungeonDebugCommandUtil.info(
                source,
                "Current site: key="
                        + site.key()
                        + ", instance="
                        + resolved.get().instance().id()
                        + ", bounds="
                        + site.bounds()
                        + ", primaryEntry="
                        + site.primaryEntryRoomId()
                        + ", roomCounts="
                        + roomCounts
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int rooms(CommandSourceStack source) {
        Optional<ResolvedCurrentSite> resolved = resolveCurrentSite(source);

        if (resolved.isEmpty()) {
            return 0;
        }

        for (DungeonGeneratedRoom room : resolved.get().site().rooms()) {
            DungeonDebugCommandUtil.info(
                    source,
                    "Room "
                            + room.id()
                            + " type="
                            + room.type().getSerializedName()
                            + " bounds="
                            + room.bounds()
                            + " anchor="
                            + room.anchorPos()
            );
        }

        return Command.SINGLE_SUCCESS;
    }

    private static int room(CommandSourceStack source) {
        Optional<ResolvedCurrentSite> resolved = resolveCurrentSite(source);

        if (resolved.isEmpty()) {
            return 0;
        }

        BlockPos playerPos = resolved.get().player().blockPosition();
        Optional<DungeonGeneratedRoom> room = resolved.get().site().roomAt(playerPos);

        if (room.isEmpty()) {
            DungeonDebugCommandUtil.failure(
                    source,
                    "You are not inside a generated dungeon room."
            );
            return 0;
        }

        DungeonGeneratedRoom value = room.get();
        DungeonDebugCommandUtil.info(
                source,
                "Current room: id="
                        + value.id()
                        + ", type="
                        + value.type().getSerializedName()
                        + ", bounds="
                        + value.bounds()
                        + ", anchor="
                        + value.anchorPos()
        );
        return Command.SINGLE_SUCCESS;
    }

    private static Optional<ResolvedCurrentSite> resolveCurrentSite(CommandSourceStack source) {
        Optional<ServerPlayer> player = DungeonDebugCommandUtil.requirePlayer(source);
        Optional<ServerLevel> level = DungeonDebugCommandUtil.requireDungeonLevel(source);

        if (player.isEmpty() || level.isEmpty()) {
            return Optional.empty();
        }

        var instance = DungeonDebugCommandUtil.currentInstance(level.get(), player.get());

        if (instance.isEmpty()) {
            DungeonDebugCommandUtil.failure(source, "You are not bound to a dungeon instance.");
            return Optional.empty();
        }

        var site = DungeonDebugCommandUtil.currentSite(level.get(), instance.get());

        if (site.isEmpty()) {
            DungeonDebugCommandUtil.failure(source, "Current dungeon site metadata is missing.");
            return Optional.empty();
        }

        return Optional.of(new ResolvedCurrentSite(
                player.get(),
                instance.get(),
                site.get()
        ));
    }

    private record ResolvedCurrentSite(
            ServerPlayer player,
            io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance instance,
            DungeonSite site
    ) {
    }
}
