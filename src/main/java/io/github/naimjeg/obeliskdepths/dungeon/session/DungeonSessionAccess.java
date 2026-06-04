package io.github.naimjeg.obeliskdepths.dungeon.session;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

public final class DungeonSessionAccess {
    private DungeonSessionAccess() {
    }

    public static boolean canAccessSession(
            ServerPlayer player,
            DungeonSession session
    ) {
        if (player == null) {
            return false;
        }

        return canAccessSession(player.getUUID(), session);
    }

    public static boolean canAccessSession(
            UUID playerId,
            DungeonSession session
    ) {
        if (session == null || playerId == null) {
            return false;
        }

        /*
         * The starter is implicitly authorized for every session policy,
         * including future external allowlist-backed sessions.
         */
        if (session.starterPlayerId().equals(playerId)) {
            return true;
        }

        return switch (session.accessPolicy()) {
            case OPEN -> true;
            case STARTER_ONLY -> false;
            case ALLOWLIST -> session.isParticipant(playerId);
        };
    }
}
