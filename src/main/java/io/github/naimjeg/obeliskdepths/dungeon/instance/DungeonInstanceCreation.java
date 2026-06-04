package io.github.naimjeg.obeliskdepths.dungeon.instance;

import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.territory.DungeonTerritory;

public record DungeonInstanceCreation(
        DungeonInstance instance,
        DungeonTerritory territory,
        DungeonSite site
) {
}