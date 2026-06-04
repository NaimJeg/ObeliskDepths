package io.github.naimjeg.obeliskdepths.dungeon.session;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.artifact.DungeonRuntimeArtifactCleanupService;
import io.github.naimjeg.obeliskdepths.dungeon.encounter.DungeonEncounterDirector;
import io.github.naimjeg.obeliskdepths.dungeon.encounter.DungeonMobResolution;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstanceService;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonStatus;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteUsageStatus;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class DungeonSessionCleanup {
    public static final int ABANDON_GRACE_TICKS = 20 * 90;

    private DungeonSessionCleanup() {
    }

    public static void tickSessions(ServerLevel dungeonLevel) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        long gameTime = dungeonLevel.getGameTime();

        for (DungeonSession session : data.sessions().all()) {
            if (session.state() == DungeonSessionState.WAITING_FOR_ENTRY) {
                tickWaitingForEntry(
                        dungeonLevel,
                        data,
                        session,
                        gameTime
                );
                continue;
            }

            if (!session.state().needsRuntimeTick()) {
                continue;
            }

            ServerPlayer starter =
                    dungeonLevel.getServer()
                            .getPlayerList()
                            .getPlayer(session.starterPlayerId());

            boolean starterInside = starter != null
                    && DungeonSessionPresence.isInsideDungeonTerritory(
                            dungeonLevel,
                            starter,
                            session
                    );

            if (starterInside) {
                data.sessions().markStarterInside(session, gameTime);

                continue;
            }

            /*
             * Only an entered run reaches this branch. Waiting portal sessions are
             * handled above and never consume the abandonment grace period.
             */
            if (gameTime - session.lastStarterInsideGameTime()
                    < ABANDON_GRACE_TICKS) {
                data.sessions().markAbandonPending(session);

                continue;
            }

            abandonAndCleanup(dungeonLevel, session);
        }
    }

    public static int cleanupSessionsForInstance(
            ServerLevel dungeonLevel,
            DungeonInstanceId instanceId
    ) {
        int cleaned = 0;
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);

        for (DungeonSession session : data.sessions().all()) {
            if (!session.instanceId().equals(instanceId)) {
                continue;
            }

            cleanupSession(dungeonLevel, session);
            cleaned++;
        }

        return cleaned;
    }

    public static void abandonAndCleanup(
            ServerLevel dungeonLevel,
            DungeonSession session
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);

        if (!session.state().needsRuntimeTick()) {
            return;
        }

        data.sessions().markAbandoned(session);

        ObeliskDepths.LOGGER.debug(
                "Dungeon session abandoned: session={}, instance={}, starter={}, outsideTicks={}",
                session.id(),
                session.instanceId(),
                session.starterPlayerId(),
                dungeonLevel.getGameTime() - session.lastStarterInsideGameTime()
        );

        cleanupSession(dungeonLevel, session);
        DungeonRuntimeArtifactCleanupService.cleanupInstanceArtifacts(
                dungeonLevel,
                session.instanceId()
        );

        DungeonInstanceService.retireRuntimeInstance(
                dungeonLevel,
                session.instanceId(),
                DungeonSiteUsageStatus.ABANDONED,
                dungeonLevel.getGameTime()
        );
    }

    public static void cleanupSession(
            ServerLevel dungeonLevel,
            DungeonSession session
    ) {
        DungeonEncounterDirector.cleanupInstance(
                dungeonLevel,
                session.instanceId(),
                DungeonMobResolution.CLEANED
        );
        int removedEntities = removeRegisteredEntities(dungeonLevel, session);
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);

        data.sessions().markCleaned(session);

        DungeonSessionProgressBarService.removeSession(session.id());

        /*
         * TODO: When dungeon transport is fully entity-overlap based, evict or
         * return remaining participants through that system during abandonment.
         */
        ObeliskDepths.LOGGER.debug(
                "Dungeon session cleaned: session={}, instance={}, removedEntities={}",
                session.id(),
                session.instanceId(),
                removedEntities
        );
    }

    private static void tickWaitingForEntry(
            ServerLevel dungeonLevel,
            DungeonManagerSavedData data,
            DungeonSession session,
            long gameTime
    ) {
        Optional<DungeonInstance> optionalInstance =
                data.instances().get(session.instanceId());

        if (optionalInstance.isEmpty()) {
            data.sessions().markFailed(session);

            DungeonSessionProgressBarService.removeSession(session.id());
            return;
        }

        DungeonInstance instance = optionalInstance.get();

        if (instance.status() != DungeonStatus.ACTIVE) {
            data.sessions().markCleaned(session);

            DungeonSessionProgressBarService.removeSession(session.id());
            return;
        }

        /*
         * Defensive self-healing: a successfully registered player proves that
         * entry happened even if another caller failed to finalize the session
         * state.
         */
        if (!instance.participants().isEmpty()) {
            data.sessions().setState(session, DungeonSessionState.ACTIVE);

            return;
        }

        if (data.portalSessions().hasValidSessionForInstance(
                session.instanceId(),
                gameTime
        )) {
            return;
        }

        closeUnenteredSession(
                dungeonLevel,
                data,
                session,
                instance,
                gameTime
        );
    }

    private static void closeUnenteredSession(
            ServerLevel dungeonLevel,
            DungeonManagerSavedData data,
            DungeonSession session,
            DungeonInstance instance,
            long gameTime
    ) {
        data.sessions().markAbandoned(session);
        data.instances().markPortalClosed(instance.id(), gameTime);

        cleanupSession(dungeonLevel, session);
        DungeonRuntimeArtifactCleanupService.cleanupInstanceArtifacts(
                dungeonLevel,
                instance.id()
        );

        ObeliskDepths.LOGGER.debug(
                "Closed unentered dungeon session after final portal lease ended: session={}, instance={}",
                session.id(),
                instance.id()
        );
    }

    private static int removeRegisteredEntities(
            ServerLevel dungeonLevel,
            DungeonSession session
    ) {
        /*
         * Session records may still own explicitly registered runtime entities.
         * Encounter-controlled mobs are cleaned by DungeonEncounterDirector
         * before this block runs.
         */
        int removed = 0;

        for (UUID entityId : session.spawnedEntityIds()) {
            Entity entity = dungeonLevel.getEntity(entityId);

            if (entity == null || !entity.isAlive()) {
                continue;
            }

            /*
             * Only entities explicitly spawned and registered by the dungeon
             * session are removed. This prevents cleanup from deleting unrelated
             * mobs that happen to be inside the structure.
             */
            entity.discard();
            removed++;
        }

        DungeonManagerSavedData.get(dungeonLevel)
                .sessions()
                .clearSpawnedEntityIds(session);

        return removed;
    }
}
