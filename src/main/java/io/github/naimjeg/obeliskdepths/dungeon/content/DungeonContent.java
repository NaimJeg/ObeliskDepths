package io.github.naimjeg.obeliskdepths.dungeon.content;

import io.github.naimjeg.obeliskdepths.worldgen.structure.generation.DungeonGenerationCatalog;
import java.util.Objects;

public final class DungeonContent {
    private static volatile DungeonContentSnapshot active =
            DungeonContentSnapshot.empty();

    private DungeonContent() {
    }

    public static DungeonContentSnapshot active() {
        return active;
    }

    static void install(DungeonContentSnapshot snapshot) {
        active = Objects.requireNonNull(snapshot, "snapshot");
        DungeonGenerationCatalog.clearCache();
    }
}
