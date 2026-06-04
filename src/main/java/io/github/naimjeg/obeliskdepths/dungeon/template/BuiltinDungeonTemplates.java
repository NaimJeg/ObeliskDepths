package io.github.naimjeg.obeliskdepths.dungeon.template;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class BuiltinDungeonTemplates {
    public static final Identifier GREAT_SWAMP_ROOM_START_OPEN_PAVILION_01 =
            id("dungeon/great_swamp/room/start/open_pavilion_01");
    public static final Identifier GREAT_SWAMP_ROOM_COMBAT_OPEN_PAVILION_01 =
            id("dungeon/great_swamp/room/combat/open_pavilion_01");
    public static final Identifier GREAT_SWAMP_ROOM_TREASURE_OBELISK_SANCTUM_01 =
            id("dungeon/great_swamp/room/treasure/obelisk_sanctum_01");
    public static final Identifier GREAT_SWAMP_ROOM_BOSS_ALTAR_01 =
            id("dungeon/great_swamp/room/boss/altar_01");

    public static final Identifier GREAT_SWAMP_CORRIDOR_STRAIGHT_01 =
            corridor("straight/straight_01");
    public static final Identifier GREAT_SWAMP_CORRIDOR_STRAIGHT_02 =
            corridor("straight/straight_02");
    public static final Identifier GREAT_SWAMP_CORRIDOR_STRAIGHT_03 =
            corridor("straight/straight_03");
    public static final Identifier GREAT_SWAMP_CORRIDOR_STRAIGHT_04 =
            corridor("straight/straight_04");
    public static final Identifier GREAT_SWAMP_CORRIDOR_STRAIGHT_05 =
            corridor("straight/straight_05");
    public static final Identifier GREAT_SWAMP_CORRIDOR_STRAIGHT_06 =
            corridor("straight/straight_06");
    public static final Identifier GREAT_SWAMP_CORRIDOR_CORNER_01 =
            corridor("corner/corner_01");
    public static final Identifier GREAT_SWAMP_CORRIDOR_TEE_01 =
            corridor("tee/tee_01");

    public static final List<Identifier> ALL_SUPPLIED_TEMPLATES = List.of(
            GREAT_SWAMP_ROOM_START_OPEN_PAVILION_01,
            GREAT_SWAMP_ROOM_COMBAT_OPEN_PAVILION_01,
            GREAT_SWAMP_ROOM_TREASURE_OBELISK_SANCTUM_01,
            GREAT_SWAMP_ROOM_BOSS_ALTAR_01,
            GREAT_SWAMP_CORRIDOR_STRAIGHT_01,
            GREAT_SWAMP_CORRIDOR_STRAIGHT_02,
            GREAT_SWAMP_CORRIDOR_STRAIGHT_03,
            GREAT_SWAMP_CORRIDOR_STRAIGHT_04,
            GREAT_SWAMP_CORRIDOR_STRAIGHT_05,
            GREAT_SWAMP_CORRIDOR_STRAIGHT_06,
            GREAT_SWAMP_CORRIDOR_CORNER_01,
            GREAT_SWAMP_CORRIDOR_TEE_01
    );

    private BuiltinDungeonTemplates() {
    }

    private static Identifier corridor(String name) {
        return id("dungeon/great_swamp/corridor/" + name);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ObeliskDepths.MOD_ID, path);
    }
}
