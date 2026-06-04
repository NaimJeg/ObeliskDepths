package io.github.naimjeg.obeliskdepths.dungeon.raid;

import io.github.naimjeg.obeliskdepths.dungeon.identity.DungeonPlayerIdentity;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonTracker;
import io.github.naimjeg.obeliskdepths.dungeon.spatial.DungeonSpatialValidation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public final class DungeonRaidPlayers {
    private DungeonRaidPlayers() {
    }

    public static List<ServerPlayer> findActivePlayersInDungeon(
            ServerLevel dungeonLevel,
            DungeonInstance instance
    ) {
        return dungeonLevel.players().stream()
                .filter(player -> DungeonPlayerIdentity.isActivePhysicalParticipantOf(
                        dungeonLevel,
                        player,
                        instance
                ))
                .toList();
    }

    public static List<ServerPlayer> findPhysicallyDesyncedPlayers(
            ServerLevel dungeonLevel,
            DungeonInstance instance
    ) {
        return instance.participants().stream()
                .map(playerId -> dungeonLevel.getServer().getPlayerList().getPlayer(playerId))
                .filter(player -> player != null && player.isAlive())
                .filter(player -> PlayerDungeonTracker.isBoundToInstance(player, instance.id()))
                .filter(player -> !DungeonSpatialValidation.playerIsPhysicallyInsideInstance(
                        dungeonLevel,
                        player,
                        instance.id()
                ))
                .toList();
    }

    /*
     * Temporary implementation note:
     *
     * Player encounter participation is physical-presence based. The dark-forest
     * design allows players to walk into another active dungeon territory inside
     * the dungeon dimension without using that player's portal.
     *
     * This does not change portal access, return bindings, or difficulty.
     * DungeonSpatialIndex remains a lookup service; DungeonSession records the
     * runtime presence state used by encounter systems.
     */
}
