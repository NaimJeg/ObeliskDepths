package io.github.naimjeg.obeliskdepths.dungeon.room;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import net.minecraft.resources.Identifier;

public final class BuiltinDungeonRooms {
    public static final Identifier GREAT_SWAMP_START_OPEN_PAVILION =
            id("great_swamp/start/open_pavilion");
    public static final Identifier GREAT_SWAMP_COMBAT_OPEN_PAVILION =
            id("great_swamp/combat/open_pavilion");
    public static final Identifier GREAT_SWAMP_TREASURE_OBELISK_SANCTUM =
            id("great_swamp/treasure/obelisk_sanctum");
    public static final Identifier GREAT_SWAMP_BOSS_ALTAR =
            id("great_swamp/boss/altar");

    private BuiltinDungeonRooms() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ObeliskDepths.MOD_ID, path);
    }
}
