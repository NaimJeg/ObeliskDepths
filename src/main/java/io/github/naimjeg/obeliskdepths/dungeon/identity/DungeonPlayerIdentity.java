package io.github.naimjeg.obeliskdepths.dungeon.identity;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonStatus;
import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonTracker;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSessionPresence;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public final class DungeonPlayerIdentity {
    private DungeonPlayerIdentity() {
    }

    public static Optional<DungeonInstanceId> currentInstanceId(ServerPlayer player) {
        return PlayerDungeonTracker.currentInstanceId(player);
    }

    public static boolean isInDungeonDimension(ServerPlayer player) {
        return player.level().dimension().equals(ModDimensions.OBELISK_DEPTHS_LEVEL);
    }

    public static boolean belongsToInstance(
            ServerPlayer player,
            DungeonInstanceId instanceId
    ) {
        return PlayerDungeonTracker.isBoundToInstance(player, instanceId);
    }

    public static boolean isBoundToInstance(
            ServerPlayer player,
            DungeonInstanceId instanceId
    ) {
        return belongsToInstance(player, instanceId);
    }

    public static boolean isActiveParticipantOf(
            ServerPlayer player,
            DungeonInstanceId instanceId
    ) {
        return isInDungeonDimension(player)
                && belongsToInstance(player, instanceId)
                && player.isAlive();
    }

    public static boolean isActivePhysicalParticipantOf(
            ServerLevel dungeonLevel,
            ServerPlayer player,
            DungeonInstance instance
    ) {
        /*
         * Bound identity and physical encounter presence are deliberately
         * separate. Portal-entered players may have return bindings; dark-forest
         * walkers may simply be physically inside another active territory.
         * Position is allowed to discover player encounter presence, while mob
         * ownership remains attachment/identity based.
         */
        return isInDungeonDimension(player)
                && player.isAlive()
                && allowsEncounterPresence(instance.status())
                && DungeonSessionPresence.isPhysicallyPresentIn(
                        dungeonLevel,
                        player,
                        instance.id()
                );
    }

    private static boolean allowsEncounterPresence(DungeonStatus status) {
        return status == DungeonStatus.ACTIVE
                || status == DungeonStatus.REWARD_PHASE;
    }
}
