package io.github.naimjeg.obeliskdepths.dungeon.player;

import io.github.naimjeg.obeliskdepths.dungeon.presence.DungeonPhysicalPresenceService;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class PlayerDungeonLifecycleService {
    private PlayerDungeonLifecycleService() {
    }

    public static void onLogout(ServerPlayer player) {
        detachFromRuntimeInstance(player);
    }

    public static void onDeath(ServerPlayer player) {
        detachFromRuntimeInstance(player);
    }

    public static void onChangedDimension(ServerPlayer player) {
        if (!player.level().dimension().equals(ModDimensions.OBELISK_DEPTHS_LEVEL)) {
            detachFromRuntimeInstance(player);
        }
    }

    private static void detachFromRuntimeInstance(ServerPlayer player) {
        var optionalInstanceId = PlayerDungeonTracker.currentInstanceId(player);
        ServerLevel dungeonLevel = player.level().getServer().getLevel(ModDimensions.OBELISK_DEPTHS_LEVEL);

        if (dungeonLevel != null) {
            DungeonPhysicalPresenceService.clearPlayerPhysicalPresence(
                    dungeonLevel,
                    player.getUUID()
            );
        }

        if (optionalInstanceId.isEmpty()) {
            PlayerDungeonTracker.clear(player);
            return;
        }

        if (dungeonLevel != null) {
            var instanceId = optionalInstanceId.get();
            DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);

            data.instances().removeParticipant(
                    instanceId,
                    player.getUUID()
            );

            data.portalSessions().removeParticipantFromInstanceSessions(
                    instanceId,
                    player.getUUID()
            );
        }

        PlayerDungeonTracker.clear(player);
    }
}
