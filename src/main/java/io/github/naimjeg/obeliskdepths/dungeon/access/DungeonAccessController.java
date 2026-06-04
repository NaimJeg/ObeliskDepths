package io.github.naimjeg.obeliskdepths.dungeon.access;

import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonStatus;
import io.github.naimjeg.obeliskdepths.dungeon.portal.PortalAdmissionMode;
import io.github.naimjeg.obeliskdepths.dungeon.portal.PortalSession;

import java.util.UUID;

public final class DungeonAccessController {
    private DungeonAccessController() {
    }

    /*
     * Portal-only access controller.
     *
     * This answers whether a player may enter through this portal session. It
     * must not decide whether a player can physically enter a dungeon territory
     * from inside the dungeon dimension. The dark-forest/open-presence rule is
     * handled by DungeonPhysicalPresenceService and does not recalculate
     * difficulty or reinterpret portal admission modes.
     */
    public static DungeonAccessResult canEnter(
            UUID playerId,
            PortalSession session,
            DungeonInstance instance,
            long gameTime
    ) {
        if (session.isExpired(gameTime)) {
            return DungeonAccessResult.DENY_PORTAL_EXPIRED;
        }

        if (instance.status() != DungeonStatus.ACTIVE) {
            return DungeonAccessResult.DENY_INSTANCE_CLOSED;
        }

        if (session.admissionMode() == PortalAdmissionMode.SOLO
                && !session.opener().equals(playerId)) {
            return DungeonAccessResult.DENY_SOLO_NOT_OPENER;
        }

        int maxParticipants = session.admissionMode() == PortalAdmissionMode.SOLO ? 1 : 4;

        if (!session.isParticipant(playerId)
                && session.participants().size() >= maxParticipants) {
            return DungeonAccessResult.DENY_MAX_PARTICIPANTS;
        }

        return DungeonAccessResult.ALLOW;
    }
}
