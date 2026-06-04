package io.github.naimjeg.obeliskdepths.dungeon.theme;

import io.github.naimjeg.obeliskdepths.dungeon.corridor.BuiltinDungeonCorridors;
import io.github.naimjeg.obeliskdepths.dungeon.room.BuiltinDungeonRooms;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonConnectorShapeType;
import net.minecraft.resources.Identifier;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Datagen-only built-in theme definitions. Runtime content loading reads the
 * generated JSON instead of depending on these factories.
 */
public final class BuiltinDungeonThemeDefinitions {
    private BuiltinDungeonThemeDefinitions() {
    }

    public static Map<Identifier, DungeonThemeDefinition> all() {
        Map<Identifier, DungeonThemeDefinition> themes =
                new LinkedHashMap<>();
        themes.put(BuiltinDungeonThemes.GREAT_SWAMP, greatSwamp());
        return Map.copyOf(themes);
    }

    public static DungeonThemeDefinition greatSwamp() {
        EnumMap<DungeonRoomType, List<WeightedDungeonRoom>> roomPools =
                new EnumMap<>(DungeonRoomType.class);
        roomPools.put(DungeonRoomType.START, List.of(
                new WeightedDungeonRoom(
                        BuiltinDungeonRooms.GREAT_SWAMP_START_OPEN_PAVILION,
                        1
                )
        ));
        roomPools.put(DungeonRoomType.COMBAT, List.of(
                new WeightedDungeonRoom(
                        BuiltinDungeonRooms.GREAT_SWAMP_COMBAT_OPEN_PAVILION,
                        1
                )
        ));
        roomPools.put(DungeonRoomType.BOSS, List.of(
                new WeightedDungeonRoom(
                        BuiltinDungeonRooms.GREAT_SWAMP_BOSS_ALTAR,
                        1
                )
        ));
        roomPools.put(DungeonRoomType.TREASURE, List.of(
                new WeightedDungeonRoom(
                        BuiltinDungeonRooms
                                .GREAT_SWAMP_TREASURE_OBELISK_SANCTUM,
                        1
                )
        ));

        EnumMap<DungeonConnectorShapeType, List<WeightedDungeonCorridor>>
                corridorPools = new EnumMap<>(DungeonConnectorShapeType.class);
        corridorPools.put(
                DungeonConnectorShapeType.STRAIGHT,
                weightedCorridors(BuiltinDungeonCorridors.STRAIGHTS)
        );
        corridorPools.put(
                DungeonConnectorShapeType.CORNER,
                weightedCorridors(BuiltinDungeonCorridors.CORNERS)
        );
        corridorPools.put(
                DungeonConnectorShapeType.T,
                weightedCorridors(BuiltinDungeonCorridors.TEES)
        );

        return new DungeonThemeDefinition(
                roomPools,
                corridorPools,
                Optional.empty(),
                true
        );
    }

    private static List<WeightedDungeonCorridor> weightedCorridors(
            List<Identifier> corridors
    ) {
        return corridors.stream()
                .map(id -> new WeightedDungeonCorridor(id, 1))
                .toList();
    }
}
