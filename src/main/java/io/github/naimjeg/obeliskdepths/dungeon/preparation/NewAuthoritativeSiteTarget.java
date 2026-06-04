package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.site.ResolvedDungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.tribute.ResolvedTribute;

public record NewAuthoritativeSiteTarget(
        DungeonPreparationRequest request,
        ResolvedDungeonSite resolvedSite,
        ResolvedTribute tribute
) implements DungeonPreparedTarget {
    public NewAuthoritativeSiteTarget {
        if (request == null) {
            throw new IllegalArgumentException("New target request must be present.");
        }
        if (resolvedSite == null) {
            throw new IllegalArgumentException("New target resolved site must be present.");
        }
        if (tribute == null || !tribute.valid()) {
            throw new IllegalArgumentException("New target tribute must be valid.");
        }
    }
}
