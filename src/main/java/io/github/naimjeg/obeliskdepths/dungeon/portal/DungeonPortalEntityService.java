package io.github.naimjeg.obeliskdepths.dungeon.portal;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.entity.DungeonPortalEntity;
import io.github.naimjeg.obeliskdepths.dungeon.id.PortalSessionId;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import io.github.naimjeg.obeliskdepths.registry.ModEntityTypes;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class DungeonPortalEntityService {
    private static final double SEARCH_RADIUS = 4.0D;

    private DungeonPortalEntityService() {
    }

    public static Optional<DungeonPortalEntity> ensurePortal(
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
            return existing;
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
        return Optional.of(entity);
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
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        List<PortalSession> sessions = data.portalSessions().all()
                .stream()
                .filter(session -> session.sourceDimension().equals(sourceDimension))
                .filter(session -> session.obeliskPos().equals(obeliskBottomPos))
                .toList();

        int removedPortals = 0;

        for (PortalSession session : sessions) {
            removedPortals += removePortalsForSession(
                    sourceLevel,
                    session.id(),
                    session.portalAnchorPos()
            );
        }

        int removedSessions = data.portalSessions().removeForSourceObelisk(
                sourceDimension,
                obeliskBottomPos
        );

        if (removedSessions > 0 || removedPortals > 0) {
            ObeliskDepths.LOGGER.debug(
                    "Closed source obelisk portal sessions: sourceDimension={}, obelisk={}, sessions={}, portals={}",
                    sourceDimension.identifier(),
                    obeliskBottomPos,
                    removedSessions,
                    removedPortals
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
