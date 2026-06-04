package io.github.naimjeg.obeliskdepths.dungeon.instance;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.territory.DungeonTerritory;

public final class DungeonInstanceFactory {
    private DungeonInstanceFactory() {
    }

    public static DungeonInstanceCreation create(
            DungeonDifficulty difficulty,
            DungeonSite site,
            long gameTime
    ) {
        DungeonInstanceId instanceId = DungeonInstanceId.create();

        DungeonTerritory territory = new DungeonTerritory(
                site.key().toTerritoryId(),
                instanceId,
                site.bounds(),
                site.startPos()
        );

        DungeonInstance instance = new DungeonInstance(
                instanceId,
                site.key(),
                difficulty,
                territory.id(),
                territory.startPos(),
                gameTime
        );

        return new DungeonInstanceCreation(
                instance,
                territory,
                site
        );
    }
}