package io.github.naimjeg.obeliskdepths.dungeon.portal;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.id.PortalSessionId;
import io.github.naimjeg.obeliskdepths.entity.DungeonPortalEntity;
import io.github.naimjeg.obeliskdepths.registry.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

public final class DungeonPortalEntityService {
    private static final double SEARCH_RADIUS = 4.0D;

    private DungeonPortalEntityService() {
    }

    public static Optional<DungeonPortalEntity> ensurePortal(
            ServerLevel sourceLevel,
            PortalSession session
    ) {
        return ensurePortalWithResult(sourceLevel, session)
                .map(PortalEnsureResult::entity);
    }

    public static Optional<PortalEnsureResult> ensurePortalWithResult(
            ServerLevel sourceLevel,
            PortalSession session
    ) {
        if (!sourceLevel.dimension().equals(session.sourceDimension())) {
            return Optional.empty();
        }

        Optional<DungeonPortalEntity> existing =
                findPortal(sourceLevel, session.id(), session.portalAnchorPos());

        if (existing.isPresent()) {
            ObeliskDepths.LOGGER.debug(
                    "Dungeon portal reused: session={}, anchor={}",
                    session.id(),
                    session.portalAnchorPos()
            );
            return Optional.of(new PortalEnsureResult(existing.get(), false));
        }

        DungeonPortalEntity entity = ModEntityTypes.DUNGEON_PORTAL.get().create(
                sourceLevel,
                EntitySpawnReason.TRIGGERED
        );

        if (entity == null) {
            return Optional.empty();
        }

        entity.initialize(session.id(), session.portalAnchorPos());

        if (!sourceLevel.addFreshEntity(entity)) {
            return Optional.empty();
        }

        ObeliskDepths.LOGGER.debug(
                "Dungeon portal created: session={}, anchor={}",
                session.id(),
                session.portalAnchorPos()
        );
        return Optional.of(new PortalEnsureResult(entity, true));
    }

    public static void removeExactCreatedPortal(
            ServerLevel sourceLevel,
            DungeonPortalEntity entity
    ) {
        if (!sourceLevel.getServer().isSameThread()) {
            throw new IllegalStateException("Portal removal must run on server thread");
        }
        if (entity.isAlive() && entity.level() == sourceLevel) {
            entity.discard();
        }
    }

    public record PortalEnsureResult(
            DungeonPortalEntity entity,
            boolean created
    ) {
    }

    public static Optional<DungeonPortalEntity> findPortal(
            ServerLevel sourceLevel,
            PortalSessionId sessionId,
            BlockPos anchor
    ) {
        List<DungeonPortalEntity> portals = nearbyPortals(sourceLevel, anchor)
                .stream()
                .filter(entity -> entity.portalSessionId()
                        .map(sessionId::equals)
                        .orElse(false))
                .toList();

        DungeonPortalEntity keep = null;

        for (DungeonPortalEntity portal : portals) {
            if (keep == null && portal.isCloseToAnchor(anchor)) {
                keep = portal;
                continue;
            }

            portal.discard();
            ObeliskDepths.LOGGER.debug(
                    "Duplicate dungeon portal removed: session={}, anchor={}, entity={}",
                    sessionId,
                    anchor,
                    portal.getUUID()
            );
        }

        return Optional.ofNullable(keep);
    }

    /**
     * Finds a portal entity without mutating or loading chunks.
     *
     * <p>This is the read-only counterpart of {@link #findPortal}, suitable
     * for reconciliation analysis passes.  It inspects only already-loaded
     * entities within the anchor search box, does not discard duplicates,
     * and never forces chunk loads.  Returns empty when no matching entity
     * is present in loaded chunks.</p>
     *
     * @param sourceLevel the source level, must be non-null and on the server thread
     * @param sessionId   the portal session id to match
     * @param anchor      the portal anchor position
     * @return the first alive portal matching {@code sessionId} within the search
     *         radius, or empty if none present in loaded chunks
     */
    public static Optional<DungeonPortalEntity> findLoadedPortalReadOnly(
            ServerLevel sourceLevel,
            PortalSessionId sessionId,
            BlockPos anchor
    ) {
        AABB searchBox = AABB.ofSize(
                Vec3.atCenterOf(anchor),
                SEARCH_RADIUS * 2.0D,
                SEARCH_RADIUS * 2.0D,
                SEARCH_RADIUS * 2.0D
        );

        return sourceLevel.getEntities(
                EntityTypeTest.forClass(DungeonPortalEntity.class),
                searchBox,
                entity -> entity.isAlive()
                        && entity.portalSessionId().map(sessionId::equals).orElse(false)
        ).stream().findFirst();
    }

    public static int removePortalsForSession(
            ServerLevel sourceLevel,
            PortalSessionId sessionId,
            BlockPos anchor
    ) {
        int removed = 0;

        for (DungeonPortalEntity portal : nearbyPortals(sourceLevel, anchor)) {
            if (portal.portalSessionId().map(sessionId::equals).orElse(false)) {
                portal.discard();
                removed++;
            }
        }

        return removed;
    }

    public static int closeSessionsForSourceObelisk(
            ServerLevel sourceLevel,
            ServerLevel dungeonLevel,
            ResourceKey<Level> sourceDimension,
            BlockPos obeliskBottomPos
    ) {
        int removedSessions = DungeonPortalSessionLifecycle.removeForSourceObelisk(
                sourceLevel,
                dungeonLevel,
                sourceDimension,
                obeliskBottomPos,
                PortalSessionRemovalReason.SOURCE_OBELISK_CLOSED
        );

        if (removedSessions > 0) {
            ObeliskDepths.LOGGER.debug(
                    "Closed source obelisk portal sessions: sourceDimension={}, obelisk={}, sessions={}",
                    sourceDimension.identifier(),
                    obeliskBottomPos,
                    removedSessions
            );
        }

        return removedSessions;
    }

    private static List<DungeonPortalEntity> nearbyPortals(
            ServerLevel sourceLevel,
            BlockPos anchor
    ) {
        AABB searchBox = AABB.ofSize(
                Vec3.atCenterOf(anchor),
                SEARCH_RADIUS * 2.0D,
                SEARCH_RADIUS * 2.0D,
                SEARCH_RADIUS * 2.0D
        );

        return sourceLevel.getEntities(
                EntityTypeTest.forClass(DungeonPortalEntity.class),
                searchBox,
                entity -> entity.isAlive()
        );
    }
}
