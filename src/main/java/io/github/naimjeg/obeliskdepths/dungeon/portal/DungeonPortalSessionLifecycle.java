package io.github.naimjeg.obeliskdepths.dungeon.portal;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.id.PortalSessionId;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonStatus;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationRuntime;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class DungeonPortalSessionLifecycle {
    private DungeonPortalSessionLifecycle() {
    }

    /**
     * Removes a portal session and its associated prepared entry.
     *
     * <p>Persistent session removal is guaranteed before source-portal
     * entity cleanup so that an entity-discard failure cannot orphan
     * the session in SavedData.  All independent cleanup steps are
     * attempted; failures are logged.</p>
     *
     * @param dungeonLevel the dungeon level
     * @param id           the portal session id
     * @param reason       the removal reason
     * @return {@code true} if a persistent session existed and was removed
     */
    public static boolean remove(
            ServerLevel dungeonLevel,
            PortalSessionId id,
            PortalSessionRemovalReason reason
    ) {
        Objects.requireNonNull(dungeonLevel, "dungeonLevel");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(reason, "reason");
        assertServerThread(dungeonLevel);

        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        PortalSession session = data.portalSessions().get(id).orElse(null);

        return removeInternal(dungeonLevel, data, id, session, reason)
                .sessionRemoved();
    }

    /**
     * Removes a portal session with a structured result.
     *
     * <p>Identical ordering guarantees as {@link #remove}, but returns
     * detailed information about what was actually removed and any
     * failures encountered during cleanup.</p>
     *
     * @param dungeonLevel the dungeon level
     * @param id           the portal session id
     * @param reason       the removal reason
     * @return structured result describing what was actually removed
     */
    public static PortalSessionRemovalResult removeWithResult(
            ServerLevel dungeonLevel,
            PortalSessionId id,
            PortalSessionRemovalReason reason
    ) {
        Objects.requireNonNull(dungeonLevel, "dungeonLevel");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(reason, "reason");
        assertServerThread(dungeonLevel);

        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        PortalSession session = data.portalSessions().get(id).orElse(null);

        return removeInternal(dungeonLevel, data, id, session, reason);
    }

    private static PortalSessionRemovalResult removeInternal(
            ServerLevel dungeonLevel,
            DungeonManagerSavedData data,
            PortalSessionId id,
            PortalSession session,
            PortalSessionRemovalReason reason
    ) {
        boolean sessionExisted = session != null;
        boolean preparedEntryRemoved = false;
        RuntimeException closeFailure = null;

        try {
            preparedEntryRemoved = closePreparedEntryAndReport(
                    dungeonLevel, id, session, reason);
        } catch (RuntimeException exception) {
            closeFailure = exception;
            ObeliskDepths.LOGGER.error(
                    "Prepared entry close failed during portal session removal: session={}, reason={}",
                    id, reason, exception
            );
        }

        boolean sessionRemoved = false;
        if (sessionExisted) {
            sessionRemoved = data.portalSessions().remove(id);
            if (sessionRemoved) {
                ObeliskDepths.LOGGER.debug(
                        "Portal session removed: session={}, instance={}, reason={}",
                        id, session.instanceId(), reason
                );
            }
        }

        int sourcePortalsRemoved = 0;
        RuntimeException sourceCleanupFailure = null;
        if (sessionExisted) {
            try {
                sourcePortalsRemoved = removeSourcePortalAndCount(
                        dungeonLevel, session);
            } catch (RuntimeException exception) {
                sourceCleanupFailure = exception;
                ObeliskDepths.LOGGER.error(
                        "Source portal cleanup failed: session={}",
                        id, exception
                );
            }
        }

        List<Throwable> failures = new ArrayList<>();
        if (closeFailure != null) {
            failures.add(closeFailure);
        }
        if (sourceCleanupFailure != null) {
            failures.add(sourceCleanupFailure);
        }

        return new PortalSessionRemovalResult(
                sessionExisted,
                sessionRemoved,
                preparedEntryRemoved,
                sourcePortalsRemoved,
                failures
        );
    }

    public static int purgeExpired(
            ServerLevel dungeonLevel,
            long gameTime
    ) {
        assertServerThread(dungeonLevel);
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        List<PortalSession> expired = data.portalSessions()
                .all()
                .stream()
                .filter(session -> session.isExpired(gameTime))
                .toList();

        int removed = 0;
        for (PortalSession session : expired) {
            if (remove(dungeonLevel, session.id(),
                    PortalSessionRemovalReason.EXPIRED)) {
                removed++;
            }
        }
        return removed;
    }

    /** Performs a fair, bounded owner-thread maintenance batch. */
    public static int purgeExpiredBounded(
            ServerLevel dungeonLevel,
            long gameTime,
            int maximumSessions,
            BooleanSupplier hasTimeRemaining
    ) {
        assertServerThread(dungeonLevel);
        Objects.requireNonNull(hasTimeRemaining, "hasTimeRemaining");
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        List<PortalSession> batch = data.portalSessions()
                .nextMaintenanceBatch(maximumSessions);
        int removed = 0;
        for (PortalSession session : batch) {
            if (!hasTimeRemaining.getAsBoolean()) {
                break;
            }
            if (session.isExpired(gameTime)
                    && remove(dungeonLevel, session.id(),
                    PortalSessionRemovalReason.EXPIRED)) {
                removed++;
            }
        }
        return removed;
    }

    /** Removes only a transaction-created session record; dependents are compensated separately. */
    public static boolean removeCreatedSessionRecord(
            ServerLevel dungeonLevel,
            PortalSessionId id
    ) {
        assertServerThread(dungeonLevel);
        return DungeonManagerSavedData.get(dungeonLevel).portalSessions().remove(id);
    }

    public static int removeForInactiveInstances(ServerLevel dungeonLevel) {
        assertServerThread(dungeonLevel);
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        List<PortalSession> inactive = data.portalSessions()
                .all()
                .stream()
                .filter(session -> data.instances()
                        .get(session.instanceId())
                        .map(instance -> instance.status() == DungeonStatus.ACTIVE)
                        .orElse(false) == false)
                .toList();

        int removed = 0;
        for (PortalSession session : inactive) {
            if (remove(
                    dungeonLevel,
                    session.id(),
                    PortalSessionRemovalReason.INACTIVE_INSTANCE_CLEANUP
            )) {
                removed++;
            }
        }
        return removed;
    }

    /** Performs bounded fair inactive-instance cleanup for the regular tick. */
    public static int removeForInactiveInstancesBounded(
            ServerLevel dungeonLevel,
            int maximumSessions,
            BooleanSupplier hasTimeRemaining
    ) {
        assertServerThread(dungeonLevel);
        Objects.requireNonNull(hasTimeRemaining, "hasTimeRemaining");
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        List<PortalSession> batch = data.portalSessions()
                .nextMaintenanceBatch(maximumSessions);
        int removed = 0;
        for (PortalSession session : batch) {
            if (!hasTimeRemaining.getAsBoolean()) {
                break;
            }
            boolean active = data.instances()
                    .get(session.instanceId())
                    .map(instance -> instance.status() == DungeonStatus.ACTIVE)
                    .orElse(false);
            if (!active && remove(
                    dungeonLevel,
                    session.id(),
                    PortalSessionRemovalReason.INACTIVE_INSTANCE_CLEANUP
            )) {
                removed++;
            }
        }
        return removed;
    }

    public static int removeForSourceObelisk(
            ServerLevel sourceLevel,
            ServerLevel dungeonLevel,
            ResourceKey<Level> sourceDimension,
            BlockPos obeliskBottomPos,
            PortalSessionRemovalReason reason
    ) {
        assertServerThread(dungeonLevel);
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        List<PortalSession> sessions = data.portalSessions()
                .all()
                .stream()
                .filter(session -> session.sourceDimension()
                        .equals(sourceDimension))
                .filter(session -> session.obeliskPos()
                        .equals(obeliskBottomPos))
                .toList();

        int removed = 0;
        for (PortalSession session : sessions) {
            if (sourceLevel != null) {
                DungeonPortalEntityService.removePortalsForSession(
                        sourceLevel,
                        session.id(),
                        session.portalAnchorPos()
                );
            }
            if (remove(dungeonLevel, session.id(), reason)) {
                removed++;
            }
        }
        return removed;
    }

    private static boolean closePreparedEntryAndReport(
            ServerLevel dungeonLevel,
            PortalSessionId id,
            PortalSession session,
            PortalSessionRemovalReason reason
    ) {
        DungeonPreparationRuntime runtime =
                DungeonPreparationRuntime.get(dungeonLevel);
        if (runtime == null) {
            return false;
        }
        var removedOpt = runtime.removeAndClosePreparedEntry(id);
        if (removedOpt.isPresent()) {
            var entry = removedOpt.get();
            ObeliskDepths.LOGGER.debug(
                    "Prepared entry removed with portal session: session={}, instance={}, site={}, chunks={}, reason={}",
                    entry.portalSessionId(),
                    entry.instanceId(),
                    entry.siteKey(),
                    entry.entryChunks().size(),
                    reason
            );
            return true;
        }
        return false;
    }

    private static int removeSourcePortalAndCount(
            ServerLevel dungeonLevel,
            PortalSession session
    ) {
        ServerLevel sourceLevel = dungeonLevel.getServer()
                .getLevel(session.sourceDimension());
        if (sourceLevel == null) {
            return 0;
        }
        return DungeonPortalEntityService.removePortalsForSession(
                sourceLevel,
                session.id(),
                session.portalAnchorPos()
        );
    }

    private static void assertServerThread(ServerLevel level) {
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException(
                    "Portal session lifecycle must run on the server thread."
            );
        }
    }
}
