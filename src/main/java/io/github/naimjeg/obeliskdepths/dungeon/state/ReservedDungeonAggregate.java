package io.github.naimjeg.obeliskdepths.dungeon.state;

import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomState;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteRecord;
import io.github.naimjeg.obeliskdepths.dungeon.territory.DungeonTerritory;

import java.util.List;
import java.util.Objects;

public record ReservedDungeonAggregate(
        DungeonInstance instance,
        DungeonSiteRecord siteRecord,
        DungeonSite site,
        DungeonTerritory territory,
        List<DungeonRoomState> roomStates
) {
    public ReservedDungeonAggregate {
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(siteRecord, "siteRecord");
        Objects.requireNonNull(site, "site");
        Objects.requireNonNull(territory, "territory");
        roomStates = List.copyOf(Objects.requireNonNull(roomStates, "roomStates"));
    }
}
