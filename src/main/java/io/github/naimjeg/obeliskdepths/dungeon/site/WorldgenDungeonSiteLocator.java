package io.github.naimjeg.obeliskdepths.dungeon.site;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.function.Predicate;

public final class WorldgenDungeonSiteLocator {
    private WorldgenDungeonSiteLocator() {
    }

    /*
     * Debug/preview lookup only.
     *
     * This is allowed to use planned prototype metadata because it is not used
     * to create a real runtime instance.
     */
    public static Optional<ResolvedDungeonSite> findNearestPrototypeSiteForDebug(
            ServerLevel level,
            BlockPos origin,
            Predicate<DungeonSiteKey> canUseSite
    ) {
        return PrototypeDungeonSitePlanner.findNearestCandidate(
                level,
                origin,
                canUseSite
        ).map(candidate -> new ResolvedDungeonSite(
                PlannedDungeonSiteProjector.project(candidate),
                DungeonSiteProjectionSource.PLANNED_PROTOTYPE
        ));
    }

}
