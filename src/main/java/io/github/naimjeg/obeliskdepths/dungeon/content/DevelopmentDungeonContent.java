package io.github.naimjeg.obeliskdepths.dungeon.content;

import io.github.naimjeg.obeliskdepths.dungeon.corridor.BuiltinDungeonCorridorDefinitions;
import io.github.naimjeg.obeliskdepths.dungeon.room.BuiltinDungeonRoomDefinitions;
import io.github.naimjeg.obeliskdepths.dungeon.theme.BuiltinDungeonThemeDefinitions;
import java.util.Map;

/**
 * Explicit development fixture for datagen and tests. Production worldgen must
 * use the runtime JSON snapshot installed by {@link DungeonContent}.
 */
public final class DevelopmentDungeonContent {
    private DevelopmentDungeonContent() {
    }

    public static DungeonContentSnapshot createBuiltinSnapshot() {
        return new DungeonContentSnapshot(
                BuiltinDungeonRoomDefinitions.all(),
                BuiltinDungeonCorridorDefinitions.all(),
                BuiltinDungeonThemeDefinitions.all(),
                Map.of()
        );
    }
}
