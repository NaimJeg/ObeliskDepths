package io.github.naimjeg.obeliskdepths.dungeon.correction;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonStatus;
import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonReturnResult;
import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonReturnService;
import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonTracker;
import io.github.naimjeg.obeliskdepths.dungeon.spatial.DungeonSpatialValidation;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class DungeonBoundaryCorrectionService {
    private static final long GRACE_TICKS = 20L * 10L;
    private static final long RETURN_RETRY_COOLDOWN_TICKS = 20L * 5L;
    private static final String WARNING_KEY =
            "message.obeliskdepths.dungeon.boundary_warning";

    private static final Map<Key, CorrectionState> DESYNCED_PLAYERS = new HashMap<>();

    private DungeonBoundaryCorrectionService() {
    }

    public static void correctDesyncedPlayers(
            ServerLevel dungeonLevel,
            DungeonInstance instance
    ) {
        long gameTime = dungeonLevel.getGameTime();

        boolean terminalInstance = instance.status() != DungeonStatus.ACTIVE
                && instance.status() != DungeonStatus.REWARD_PHASE;

        Iterable<ServerPlayer> players = boundOnlinePlayers(dungeonLevel, instance);

        for (ServerPlayer player : players) {
            Key key = new Key(instance.id(), player.getUUID());
            if (!PlayerDungeonTracker.isBoundToInstance(player, instance.id())) {
                DESYNCED_PLAYERS.remove(key);
                continue;
            }

            boolean outside = terminalInstance
                    || !DungeonSpatialValidation.playerIsPhysicallyInsideInstance(
                    dungeonLevel,
                    player,
                    instance.id()
            );

            boolean physicallyInside =
                    DungeonSpatialValidation.playerIsPhysicallyInsideInstance(
                            dungeonLevel,
                            player,
                            instance.id()
                    );

            /*
             * Never teleport a player merely because they are outside the dungeon.
             *
             * Only players physically inside a terminal dungeon instance should be
             * evacuated.
             */
            if (!physicallyInside || !terminalInstance) {
                DESYNCED_PLAYERS.remove(key);
                continue;
            }

            CorrectionState state = DESYNCED_PLAYERS.computeIfAbsent(
                    key,
                    ignored -> new CorrectionState(gameTime, 0L, false)
            );

            if (!state.warned) {
                player.sendOverlayMessage(Component.translatable(WARNING_KEY));
                state.warned = true;
            }

            if (!terminalInstance && gameTime - state.firstDetectedGameTime < GRACE_TICKS) {
                continue;
            }

            if (gameTime - state.lastReturnAttemptGameTime < RETURN_RETRY_COOLDOWN_TICKS) {
                continue;
            }

            state.lastReturnAttemptGameTime = gameTime;
            PlayerDungeonReturnResult result = PlayerDungeonReturnService.returnPlayer(player);

            if (result == PlayerDungeonReturnResult.SUCCESS
                    || result == PlayerDungeonReturnResult.NO_DUNGEON_BINDING) {
                DESYNCED_PLAYERS.remove(key);
            } else {
                ObeliskDepths.LOGGER.warn(
                        "Dungeon boundary correction return failed: player={}, instance={}, pos={}, terminal={}, result={}",
                        player.getGameProfile().name(),
                        instance.id(),
                        player.blockPosition(),
                        terminalInstance,
                        result
                );
            }
        }

        pruneStale(dungeonLevel, instance, gameTime);
    }

    private static Iterable<ServerPlayer> boundOnlinePlayers(
            ServerLevel dungeonLevel,
            DungeonInstance instance
    ) {
        return instance.participants()
                .stream()
                .map(playerId -> dungeonLevel.getServer().getPlayerList().getPlayer(playerId))
                .filter(player -> player != null && player.isAlive())
                .toList();
    }

    private static void pruneStale(
            ServerLevel dungeonLevel,
            DungeonInstance instance,
            long gameTime
    ) {
        Iterator<Map.Entry<Key, CorrectionState>> iterator =
                DESYNCED_PLAYERS.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Key, CorrectionState> entry = iterator.next();

            if (!entry.getKey().instanceId.equals(instance.id())) {
                continue;
            }

            ServerPlayer player = dungeonLevel.getServer().getPlayerList().getPlayer(entry.getKey().playerId);
            if (player == null
                    || !PlayerDungeonTracker.isBoundToInstance(player, instance.id())
                    || DungeonSpatialValidation.playerIsPhysicallyInsideInstance(dungeonLevel, player, instance.id())
                    || gameTime - entry.getValue().firstDetectedGameTime > 20L * 60L * 5L) {
                iterator.remove();
            }
        }
    }

    private record Key(DungeonInstanceId instanceId, UUID playerId) {
        private Key {
            Objects.requireNonNull(instanceId, "instance id");
            Objects.requireNonNull(playerId, "player id");
        }
    }

    private static final class CorrectionState {
        private final long firstDetectedGameTime;
        private long lastReturnAttemptGameTime;
        private boolean warned;

        private CorrectionState(
                long firstDetectedGameTime,
                long lastReturnAttemptGameTime,
                boolean warned
        ) {
            this.firstDetectedGameTime = firstDetectedGameTime;
            this.lastReturnAttemptGameTime = lastReturnAttemptGameTime;
            this.warned = warned;
        }
    }
}
