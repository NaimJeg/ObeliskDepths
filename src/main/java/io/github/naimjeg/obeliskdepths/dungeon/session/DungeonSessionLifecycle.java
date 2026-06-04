package io.github.naimjeg.obeliskdepths.dungeon.session;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonStatus;
import io.github.naimjeg.obeliskdepths.dungeon.portal.PortalAdmissionMode;
import io.github.naimjeg.obeliskdepths.dungeon.portal.PortalSession;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

public final class DungeonSessionLifecycle {
    private DungeonSessionLifecycle() {
    }

    public static DungeonSession getOrCreateForPortal(
            ServerLevel dungeonLevel,
            DungeonInstance instance,
            PortalSession portalSession,
            boolean tributeBonusActive
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);

        Optional<DungeonSession> existing =
                data.sessions().findByInstance(instance.id());
        SessionAccessPolicy requestedAccessPolicy =
                accessPolicyFor(portalSession.admissionMode());

        if (existing.isPresent()) {
            DungeonSession session = existing.get();
            if (session.accessPolicy() != requestedAccessPolicy) {
                ObeliskDepths.LOGGER.error(
                        "Dungeon session access policy invariant violation: session={}, instance={}, existing={}, requested={}",
                        session.id(),
                        session.instanceId(),
                        session.accessPolicy(),
                        requestedAccessPolicy
                );
                throw new IllegalStateException(
                        "Existing dungeon session access policy mismatch: "
                                + session.instanceId()
                );
            }

            if (tributeBonusActive && !session.tributeBonusActive()) {
                /*
                 * This branch is intentionally not toggled yet. Tribute bonuses
                 * should be fixed at run creation; joining an existing run should
                 * not reactivate a consumed/expired bonus.
                 */
                ObeliskDepths.LOGGER.debug(
                        "Ignoring late tribute bonus activation for existing dungeon session {}",
                        session.id()
                );
            }

            return session;
        }

        DungeonSession created = DungeonSession.createForPortal(
                instance,
                portalSession.opener(),
                requestedAccessPolicy,
                tributeBonusActive,
                dungeonLevel.getGameTime()
        );

        data.sessions().add(created);

        ObeliskDepths.LOGGER.debug(
                "Created dungeon session: session={}, instance={}, starter={}, site={}, access={}, tributeBonus={}",
                created.id(),
                created.instanceId(),
                created.starterPlayerId(),
                created.siteKey(),
                created.accessPolicy(),
                created.tributeBonusActive()
        );

        return created;
    }

    public static DungeonSession getOrCreateDebugSession(
            ServerLevel dungeonLevel,
            DungeonInstance instance,
            UUID starterPlayerId
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        Optional<DungeonSession> existing =
                data.sessions().findByInstance(instance.id());

        if (existing.isPresent()) {
            return existing.get();
        }

        /*
         * Debug/dev entry is not a portal session and must not change portal
         * semantics. It creates the minimum runtime session needed by encounter
         * cleanup/progress systems after a real authoritative site is reserved.
         */
        DungeonSession created = DungeonSession.createActive(
                instance,
                starterPlayerId,
                SessionAccessPolicy.OPEN,
                false,
                dungeonLevel.getGameTime()
        );

        data.sessions().add(created);
        return created;
    }

    public static Optional<DungeonSession> recoverMissingSessionForPhysicalEntry(
            ServerLevel dungeonLevel,
            DungeonInstance instance,
            UUID enteringPlayerId,
            String reason
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        Optional<DungeonSession> existing =
                data.sessions().findByInstance(instance.id());

        if (existing.isPresent()) {
            return existing;
        }

        if (data.sites().reservedSite(instance.id()).filter(instance.siteKey()::equals).isEmpty()) {
            ObeliskDepths.LOGGER.warn(
                    "Refusing physical-entry session recovery for unreserved dungeon instance: instance={}, site={}, player={}, reason={}",
                    instance.id(),
                    instance.siteKey(),
                    enteringPlayerId,
                    reason
            );
            return Optional.empty();
        }

        if (instance.status() != DungeonStatus.ACTIVE
                && instance.status() != DungeonStatus.REWARD_PHASE) {
            return Optional.empty();
        }

        Optional<PortalSession> portalSession =
                data.portalSessions().findByInstance(instance.id(), dungeonLevel.getGameTime());
        if (portalSession.isEmpty()) {
            ObeliskDepths.LOGGER.warn(
                    "Refusing physical-entry session recovery without authoritative portal session: instance={}, site={}, player={}, reason={}",
                    instance.id(),
                    instance.siteKey(),
                    enteringPlayerId,
                    reason
            );
            return Optional.empty();
        }

        DungeonSession recovered = DungeonSession.createActive(
                instance,
                portalSession.get().opener(),
                accessPolicyFor(portalSession.get().admissionMode()),
                false,
                dungeonLevel.getGameTime()
        );

        data.sessions().add(recovered);
        ObeliskDepths.LOGGER.warn(
                "Recovered missing dungeon session for physical entry: instance={}, site={}, player={}, reason={}",
                instance.id(),
                instance.siteKey(),
                enteringPlayerId,
                reason
        );
        return Optional.of(recovered);
    }

    public static boolean removeSession(
            ServerLevel dungeonLevel,
            UUID sessionId
    ) {
        DungeonSessionProgressBarService.removeSession(sessionId);
        return DungeonManagerSavedData.get(dungeonLevel).sessions().remove(sessionId);
    }

    public static boolean registerParticipant(
            ServerLevel dungeonLevel,
            DungeonInstanceId instanceId,
            UUID playerId
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        Optional<DungeonSession> session =
                data.sessions().findByInstance(instanceId);

        if (session.isEmpty()) {
            return false;
        }

        return data.sessions().registerParticipant(session.get(), playerId);
    }

    public static boolean removeParticipant(
            ServerLevel dungeonLevel,
            DungeonInstanceId instanceId,
            UUID playerId
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        Optional<DungeonSession> session =
                data.sessions().findByInstance(instanceId);

        if (session.isEmpty()) {
            return false;
        }

        return data.sessions().removeParticipant(session.get(), playerId);
    }

    public static boolean registerPhysicalParticipant(
            ServerLevel dungeonLevel,
            DungeonInstanceId instanceId,
            UUID playerId
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        Optional<DungeonSession> session =
                data.sessions().findByInstance(instanceId);

        if (session.isEmpty()) {
            return false;
        }

        return data.sessions().registerPhysicalParticipant(session.get(), playerId);
    }

    public static boolean unregisterPhysicalParticipant(
            ServerLevel dungeonLevel,
            DungeonInstanceId instanceId,
            UUID playerId
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        Optional<DungeonSession> session =
                data.sessions().findByInstance(instanceId);

        if (session.isEmpty()) {
            return false;
        }

        return data.sessions().unregisterPhysicalParticipant(session.get(), playerId);
    }

    public static int unregisterPhysicalParticipantFromAll(
            ServerLevel dungeonLevel,
            UUID playerId
    ) {
        return DungeonManagerSavedData.get(dungeonLevel)
                .sessions()
                .unregisterPhysicalParticipantFromAll(playerId);
    }

    public static void markPortalEntrySucceeded(
            ServerLevel dungeonLevel,
            DungeonSession session,
            UUID playerId,
            long gameTime
    ) {
        DungeonManagerSavedData.get(dungeonLevel)
                .sessions()
                .markPortalEntrySucceeded(session, playerId, gameTime);
    }

    private static SessionAccessPolicy accessPolicyFor(
            PortalAdmissionMode admissionMode
    ) {
        return switch (admissionMode) {
            case SOLO -> SessionAccessPolicy.STARTER_ONLY;
            case OPEN_JOIN -> SessionAccessPolicy.OPEN;
        };
    }
}
