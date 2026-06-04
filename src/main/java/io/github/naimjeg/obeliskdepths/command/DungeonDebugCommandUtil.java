package io.github.naimjeg.obeliskdepths.command;

import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonData;
import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonTracker;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidInstance;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSession;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteProjectionCache;
import io.github.naimjeg.obeliskdepths.dungeon.site.ResolvedDungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

final class DungeonDebugCommandUtil {
    private DungeonDebugCommandUtil() {
    }

    static Optional<ServerPlayer> requirePlayer(CommandSourceStack source) {
        try {
            return Optional.of(source.getPlayerOrException());
        } catch (Exception exception) {
            failure(source, "This command must be run by a player.");
            return Optional.empty();
        }
    }

    static Optional<ServerLevel> requireDungeonLevel(CommandSourceStack source) {
        ServerLevel level = source.getServer().getLevel(ModDimensions.OBELISK_DEPTHS_LEVEL);

        if (level == null) {
            failure(source, "ObeliskDepths dimension is not loaded.");
            return Optional.empty();
        }

        return Optional.of(level);
    }

    static Optional<DungeonInstance> currentInstance(
            ServerLevel level,
            ServerPlayer player
    ) {
        Optional<PlayerDungeonData> playerData = PlayerDungeonTracker.get(player);

        if (playerData.isEmpty() || playerData.get().currentInstanceId().isEmpty()) {
            return Optional.empty();
        }

        return DungeonManagerSavedData.get(level)
                .instances()
                .get(playerData.get().currentInstanceId().get());
    }

    static Optional<DungeonSession> currentSession(
            ServerLevel level,
            DungeonInstance instance
    ) {
        return DungeonManagerSavedData.get(level)
                .sessions()
                .findByInstance(instance.id());
    }

    static Optional<DungeonSite> currentSite(
            ServerLevel level,
            DungeonInstance instance
    ) {
        Optional<DungeonSite> generated = DungeonSiteProjectionCache.read(
                        level,
                        instance.siteKey()
                )
                .map(ResolvedDungeonSite::site);

        if (generated.isPresent()) {
            return generated;
        }

        return DungeonManagerSavedData.get(level)
                .sites()
                .snapshot(instance.siteKey());
    }

    static Optional<DungeonRaidInstance> currentEncounter(
            ServerLevel level,
            DungeonInstance instance
    ) {
        return DungeonManagerSavedData.get(level)
                .raids()
                .findByInstance(instance.id());
    }

    static void success(
            CommandSourceStack source,
            String message
    ) {
        source.sendSuccess(() -> Component.literal(message), false);
    }

    static void info(
            CommandSourceStack source,
            String message
    ) {
        success(source, message);
    }

    static void failure(
            CommandSourceStack source,
            String message
    ) {
        source.sendFailure(Component.literal(message));
    }
}
