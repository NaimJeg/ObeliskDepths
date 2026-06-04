package io.github.naimjeg.obeliskdepths.dungeon.presence;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonStatus;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSession;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSessionLifecycle;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSessionPresence;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@EventBusSubscriber(modid = ObeliskDepths.MOD_ID)
public final class DungeonPhysicalPresenceService {
    private static final long UNACTIVATED_SITE_DEBUG_INTERVAL_TICKS = 20L * 30L;
    private static final Map<UUID, DungeonInstanceId> PHYSICAL_INSTANCE_BY_PLAYER =
            new HashMap<>();
    private static final Map<String, Long> UNACTIVATED_SITE_DEBUG_TIME_BY_KEY =
            new HashMap<>();

    private DungeonPhysicalPresenceService() {
    }

    public static void tickPlayerPhysicalPresence(
            ServerLevel dungeonLevel,
            ServerPlayer player
    ) {
        if (!isValidPhysicalParticipant(player)) {
            clearPlayerPhysicalPresence(dungeonLevel, player.getUUID());
            return;
        }

        Optional<DungeonInstanceId> current =
                DungeonSessionPresence.findCurrentPhysicalInstance(
                        dungeonLevel,
                        player
                );

        if (current.isEmpty()) {
            logResolvableUnactivatedSite(dungeonLevel, player);
            clearPlayerPhysicalPresence(dungeonLevel, player.getUUID());
            return;
        }

        UUID playerId = player.getUUID();
        Optional<DungeonInstanceId> previous =
                Optional.ofNullable(PHYSICAL_INSTANCE_BY_PLAYER.get(playerId))
                        .or(() -> DungeonManagerSavedData.get(dungeonLevel)
                                .sessions()
                                .findPhysicalInstanceByPlayer(playerId));

        DungeonInstanceId currentId = current.get();
        if (previous.isPresent() && !previous.get().equals(currentId)) {
            DungeonSessionLifecycle.unregisterPhysicalParticipant(
                    dungeonLevel,
                    previous.get(),
                    playerId
            );
        }

        if (recordPhysicalPresence(dungeonLevel, player, currentId)) {
            PHYSICAL_INSTANCE_BY_PLAYER.put(playerId, currentId);
        } else {
            PHYSICAL_INSTANCE_BY_PLAYER.remove(playerId);
        }
    }

    public static void clearPlayerPhysicalPresence(
            ServerLevel dungeonLevel,
            UUID playerId
    ) {
        DungeonInstanceId cached = PHYSICAL_INSTANCE_BY_PLAYER.remove(playerId);

        if (cached != null) {
            DungeonSessionLifecycle.unregisterPhysicalParticipant(
                    dungeonLevel,
                    cached,
                    playerId
            );
        }

        DungeonSessionLifecycle.unregisterPhysicalParticipantFromAll(
                dungeonLevel,
                playerId
        );
    }

    private static boolean recordPhysicalPresence(
            ServerLevel dungeonLevel,
            ServerPlayer player,
            DungeonInstanceId instanceId
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        Optional<DungeonInstance> instance =
                data.instances().get(instanceId);

        if (instance.isEmpty() || !allowsPhysicalPresence(instance.get().status())) {
            return false;
        }

        if (data.sessions().findByInstance(instanceId).isEmpty()) {
            Optional<DungeonSession> recovered = DungeonSessionLifecycle.recoverMissingSessionForPhysicalEntry(
                    dungeonLevel,
                    instance.get(),
                    player.getUUID(),
                    "physical_entry_missing_session"
            );

            if (recovered.isEmpty()) {
                return false;
            }
        }

        /*
         * Physical entry through the dungeon dimension is not portal entry. Do
         * not apply portal access rules here, do not overwrite PlayerDungeonData
         * return information, and do not refresh/recalculate difficulty. The
         * difficulty was fixed at original instance reservation time.
         */
        DungeonSessionLifecycle.registerPhysicalParticipant(
                dungeonLevel,
                instanceId,
                player.getUUID()
        );
        return true;
    }

    private static boolean allowsPhysicalPresence(DungeonStatus status) {
        return status == DungeonStatus.ACTIVE
                || status == DungeonStatus.REWARD_PHASE;
    }

    private static boolean isValidPhysicalParticipant(ServerPlayer player) {
        return player.isAlive()
                && !player.isSpectator()
                && player.level().dimension().equals(ModDimensions.OBELISK_DEPTHS_LEVEL);
    }

    private static void logResolvableUnactivatedSite(
            ServerLevel dungeonLevel,
            ServerPlayer player
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);

        data.sites().snapshots().stream()
                .filter(site -> site.bounds().contains(player.blockPosition()))
                .findFirst()
                .ifPresent(site -> {
                    String key = site.key() + "/" + player.getUUID();
                    long gameTime = dungeonLevel.getGameTime();
                    long previous = UNACTIVATED_SITE_DEBUG_TIME_BY_KEY.getOrDefault(
                            key,
                            Long.MIN_VALUE
                    );

                    if (gameTime - previous < UNACTIVATED_SITE_DEBUG_INTERVAL_TICKS) {
                        return;
                    }

                    UNACTIVATED_SITE_DEBUG_TIME_BY_KEY.put(key, gameTime);
                    ObeliskDepths.LOGGER.debug(
                            "Player is inside a generated but unactivated dungeon site; no runtime instance/session will be created: player={}, site={}, pos={}",
                            player.getUUID(),
                            site.key(),
                            player.blockPosition()
                    );
                });
    }

    public static void clearAll() {
        PHYSICAL_INSTANCE_BY_PLAYER.clear();
        UNACTIVATED_SITE_DEBUG_TIME_BY_KEY.clear();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level
                && level.dimension().equals(ModDimensions.OBELISK_DEPTHS_LEVEL)) {
            clearAll();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        clearAll();
    }
}
