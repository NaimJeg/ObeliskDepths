package io.github.naimjeg.obeliskdepths.dungeon.theme;

import io.github.naimjeg.obeliskdepths.dungeon.corridor.DungeonCorridorDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonConnectorShapeType;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DungeonThemeDefinitionValidator {
    private DungeonThemeDefinitionValidator() {
    }

    public static List<String> validate(
            Identifier themeId,
            DungeonThemeDefinition theme,
            Map<Identifier, DungeonRoomDefinition> rooms,
            Map<Identifier, DungeonCorridorDefinition> corridors
    ) {
        List<String> errors = new ArrayList<>();

        if (themeId == null) {
            errors.add("theme id must not be null");
        }

        if (theme == null) {
            errors.add("theme definition must not be null");
            return List.copyOf(errors);
        }

        if (rooms == null) {
            rooms = Map.of();
        }

        if (corridors == null) {
            corridors = Map.of();
        }

        validateRooms(theme, rooms, errors);
        validateCorridors(theme, corridors, errors);

        if (theme.enabled()) {
            requirePool(theme, DungeonRoomType.START, errors);
            requirePool(theme, DungeonRoomType.COMBAT, errors);
            requirePool(theme, DungeonRoomType.TREASURE, errors);
            requirePool(theme, DungeonRoomType.BOSS, errors);
        }

        return List.copyOf(errors);
    }

    private static void validateRooms(
            DungeonThemeDefinition theme,
            Map<Identifier, DungeonRoomDefinition> rooms,
            List<String> errors
    ) {
        for (Map.Entry<DungeonRoomType, List<WeightedDungeonRoom>> pool
                : theme.roomPools().entrySet()) {
            for (WeightedDungeonRoom weightedRoom : pool.getValue()) {
                if (weightedRoom.weight() <= 0) {
                    errors.add("room "
                            + weightedRoom.room()
                            + " has non-positive weight in "
                            + pool.getKey().getSerializedName()
                            + " pool");
                }

                DungeonRoomDefinition room = rooms.get(weightedRoom.room());

                if (room == null) {
                    errors.add("missing room reference "
                            + weightedRoom.room()
                            + " in "
                            + pool.getKey().getSerializedName()
                            + " pool");
                    continue;
                }

                if (room.type() != pool.getKey()) {
                    errors.add("room "
                            + weightedRoom.room()
                            + " has type "
                            + room.type().getSerializedName()
                            + " but is referenced from "
                            + pool.getKey().getSerializedName()
                            + " pool");
                }
            }
        }
    }

    private static void validateCorridors(
            DungeonThemeDefinition theme,
            Map<Identifier, DungeonCorridorDefinition> corridors,
            List<String> errors
    ) {
        for (Map.Entry<DungeonConnectorShapeType, List<WeightedDungeonCorridor>>
                pool : theme.corridorPools().entrySet()) {
            for (WeightedDungeonCorridor weightedCorridor : pool.getValue()) {
                if (weightedCorridor.weight() <= 0) {
                    errors.add("corridor "
                            + weightedCorridor.corridor()
                            + " has non-positive weight in "
                            + pool.getKey().getSerializedName()
                            + " pool");
                }

                DungeonCorridorDefinition corridor =
                        corridors.get(weightedCorridor.corridor());

                if (corridor == null) {
                    errors.add("missing corridor reference "
                            + weightedCorridor.corridor()
                            + " in "
                            + pool.getKey().getSerializedName()
                            + " pool");
                    continue;
                }

                if (corridor.shape() != pool.getKey()) {
                    errors.add("corridor "
                            + weightedCorridor.corridor()
                            + " has shape "
                            + corridor.shape().getSerializedName()
                            + " but is referenced from "
                            + pool.getKey().getSerializedName()
                            + " pool");
                }
            }
        }
    }

    private static void requirePool(
            DungeonThemeDefinition theme,
            DungeonRoomType type,
            List<String> errors
    ) {
        if (theme.roomsFor(type).isEmpty()) {
            errors.add("enabled theme requires at least one "
                    + type.getSerializedName()
                    + " room");
        }
    }
}
