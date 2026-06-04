package io.github.naimjeg.obeliskdepths.dungeon.session;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteProjectionCache;
import io.github.naimjeg.obeliskdepths.dungeon.site.ResolvedDungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.spatial.DungeonSpatialIndex;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class DungeonSessionPresence {
    private DungeonSessionPresence() {
    }

    public static Optional<DungeonInstanceId> findCurrentPhysicalInstance(
            ServerLevel dungeonLevel,
            ServerPlayer player
    ) {
        if (!dungeonLevel.dimension().equals(ModDimensions.OBELISK_DEPTHS_LEVEL)) {
            return Optional.empty();
        }

        if (!player.level().dimension().equals(ModDimensions.OBELISK_DEPTHS_LEVEL)) {
            return Optional.empty();
        }

        /*
         * DungeonSpatialIndex remains a physical world-space lookup only. Player
         * membership and encounter participation are recorded by session runtime
         * state, not owned by the spatial index.
         */
        return DungeonSpatialIndex.findPhysicalOwnerAt(
                dungeonLevel,
                player.blockPosition()
        );
    }

    public static boolean isPhysicallyPresentIn(
            ServerLevel dungeonLevel,
            ServerPlayer player,
            DungeonInstanceId instanceId
    ) {
        return findCurrentPhysicalInstance(dungeonLevel, player)
                .map(instanceId::equals)
                .orElse(false);
    }

    public static boolean isInsideDungeonTerritory(
            ServerPlayer player,
            DungeonSession session
    ) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }

        return isInsideDungeonTerritory(level, player, session);
    }

    public static boolean isInsideDungeonTerritory(
            ServerLevel dungeonLevel,
            ServerPlayer player,
            DungeonSession session
    ) {
        if (dungeonLevel == null || player == null || session == null) {
            return false;
        }

        if (!dungeonLevel.dimension().equals(ModDimensions.OBELISK_DEPTHS_LEVEL)
                || !player.level().dimension().equals(ModDimensions.OBELISK_DEPTHS_LEVEL)) {
            return false;
        }

        /*
         * TODO: Include corridor bounds, entry overlap bounds, boss arena bounds,
         * and any future transport-entity overlap area. For now, this uses the
         * best available site bounds from authoritative DungeonSite metadata.
         */
        return resolveSite(dungeonLevel, session.siteKey())
                .map(site -> site.bounds().contains(player.blockPosition()))
                .orElse(false);
    }

    private static Optional<DungeonSite> resolveSite(
            ServerLevel dungeonLevel,
            DungeonSiteKey siteKey
    ) {
        Optional<DungeonSite> generated =
                DungeonSiteProjectionCache.read(dungeonLevel, siteKey)
                        .map(ResolvedDungeonSite::site);

        if (generated.isPresent()) {
            return generated;
        }

        return DungeonManagerSavedData.get(dungeonLevel)
                .sites()
                .snapshot(siteKey);
    }
}
