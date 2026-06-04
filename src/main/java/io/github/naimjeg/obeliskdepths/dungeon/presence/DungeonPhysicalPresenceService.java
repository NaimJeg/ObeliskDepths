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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@EventBusSubscriber(modid = ObeliskDepths.MOD_ID)
public final class DungeonPhysicalPresenceService {
    private static final Map<UUID, DungeonInstanceId> PHYSICAL_INSTANCE_BY_PLAYER =
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

    public static void clearAll() {
        PHYSICAL_INSTANCE_BY_PLAYER.clear();
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
