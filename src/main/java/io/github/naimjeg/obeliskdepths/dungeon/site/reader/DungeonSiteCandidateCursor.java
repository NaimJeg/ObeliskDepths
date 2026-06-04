package io.github.naimjeg.obeliskdepths.dungeon.site.reader;

import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;

import java.util.List;
import java.util.function.Consumer;

public interface DungeonSiteCandidateCursor {
    int advance(int maximumKeys, Consumer<DungeonSiteKey> sink);

    boolean exhausted();

    int producedCount();

    List<DungeonSiteKey> producedKeys();
}
