package io.github.naimjeg.obeliskdepths.dungeon.presence;

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

import java.util.Optional;
import java.util.UUID;

public final class DungeonPhysicalPresenceService {
    private DungeonPhysicalPresenceService() {
    }

    public static void tickPlayerPhysicalPresence(
            ServerLevel dungeonLevel,
            ServerPlayer player
    ) {
        UUID playerId = player.getUUID();
        resolveCurrentPhysicalPresence(
                isValidPhysicalParticipant(player),
                DungeonSessionPresence.findCurrentPhysicalInstance(
                        dungeonLevel,
                        player
                ),
                instanceId -> recordPhysicalPresence(
                        dungeonLevel,
                        player,
                        instanceId
                ),
                () -> clearPlayerPhysicalPresence(
                        dungeonLevel,
                        playerId
                )
        );
    }

    public static void clearPlayerPhysicalPresence(
            ServerLevel dungeonLevel,
            UUID playerId
    ) {
        DungeonSessionLifecycle.unregisterPhysicalParticipantFromAll(
                dungeonLevel,
                playerId
        );
    }

    static void resolveCurrentPhysicalPresence(
            boolean validParticipant,
            Optional<DungeonInstanceId> currentInstance,
            PhysicalPresenceRecorder recorder,
            Runnable clearPhysicalPresence
    ) {
        if (!validParticipant || currentInstance.isEmpty()) {
            clearPhysicalPresence.run();
            return;
        }

        if (!recorder.record(currentInstance.get())) {
            clearPhysicalPresence.run();
        }
    }

    @FunctionalInterface
    interface PhysicalPresenceRecorder {
        boolean record(DungeonInstanceId instanceId);
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
        return DungeonSessionLifecycle.reconcilePhysicalParticipant(
                dungeonLevel,
                instanceId,
                player.getUUID()
        );
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
}
