package io.github.naimjeg.obeliskdepths.dungeon.identity;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.List;

public final class DungeonMembership {
    private DungeonMembership() {
    }

    public static boolean playerBelongsTo(
            ServerPlayer player,
            DungeonInstance instance
    ) {
        return DungeonPlayerIdentity.belongsToInstance(player, instance.id())
                && instance.isParticipant(player.getUUID());
    }

    public static boolean entityBelongsTo(
            Entity entity,
            DungeonInstanceId instanceId
    ) {
        return DungeonEntityIdentity.belongsToInstance(entity, instanceId);
    }

    public static List<ServerPlayer> activePlayers(
            ServerLevel dungeonLevel,
            DungeonInstance instance
    ) {
        return dungeonLevel.players().stream()
                .filter(player -> playerBelongsTo(player, instance))
                .filter(player -> DungeonPlayerIdentity.isInDungeonDimension(player))
                .filter(ServerPlayer::isAlive)
                .toList();
    }

    /*
     * Future:
     * Add room-level membership:
     *
     * boolean playerBelongsToRoom(ServerPlayer player, DungeonRoomId roomId)
     * boolean entityBelongsToEncounter(Entity entity, DungeonEncounterId encounterId)
     *
     * Do not derive room membership solely from position. Room transitions may
     * use position triggers, but persistent room identity should live in
     * room/player/entity state.
     */
}
