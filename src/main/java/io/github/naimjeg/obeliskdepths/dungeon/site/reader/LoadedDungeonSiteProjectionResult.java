package io.github.naimjeg.obeliskdepths.dungeon.site.reader;

import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonStructureDistanceReport;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;

import java.util.Objects;
import java.util.Optional;

/**
 * Structured result from projecting a loaded structure start into a
 * dungeon site. Either contains an accepted site or a rejection with
 * a specific failure reason and distance diagnostic.
 */
public record LoadedDungeonSiteProjectionResult(
        Optional<DungeonSite> site,
        LoadedDungeonSiteProjectionFailure failure,
        DungeonStructureDistanceReport distanceReport
) {
    public LoadedDungeonSiteProjectionResult {
        Objects.requireNonNull(site, "site");
        Objects.requireNonNull(distanceReport, "distanceReport");
        if (site.isPresent() == (failure != null)) {
            throw new IllegalArgumentException(
                    "Projection result must have either a site or a failure, not both."
            );
        }
    }

    public boolean accepted() {
        return site.isPresent();
    }

    static LoadedDungeonSiteProjectionResult accepted(
            DungeonSite site,
            DungeonStructureDistanceReport report
    ) {
        return new LoadedDungeonSiteProjectionResult(
                Optional.of(site), null, report
        );
    }

    static LoadedDungeonSiteProjectionResult rejected(
            LoadedDungeonSiteProjectionFailure failure,
            DungeonStructureDistanceReport report
    ) {
        return new LoadedDungeonSiteProjectionResult(
                Optional.empty(), failure, report
        );
    }
}