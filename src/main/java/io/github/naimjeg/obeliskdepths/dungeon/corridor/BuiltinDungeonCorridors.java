package io.github.naimjeg.obeliskdepths.dungeon.corridor;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class BuiltinDungeonCorridors {
    public static final Identifier GREAT_SWAMP_STRAIGHT_01 =
            id("great_swamp/straight/straight_01");
    public static final Identifier GREAT_SWAMP_STRAIGHT_02 =
            id("great_swamp/straight/straight_02");
    public static final Identifier GREAT_SWAMP_STRAIGHT_03 =
            id("great_swamp/straight/straight_03");
    public static final Identifier GREAT_SWAMP_STRAIGHT_04 =
            id("great_swamp/straight/straight_04");
    public static final Identifier GREAT_SWAMP_STRAIGHT_05 =
            id("great_swamp/straight/straight_05");
    public static final Identifier GREAT_SWAMP_STRAIGHT_06 =
            id("great_swamp/straight/straight_06");

    public static final Identifier GREAT_SWAMP_CORNER_01 =
            id("great_swamp/corner/corner_01");

    public static final Identifier GREAT_SWAMP_TEE_01 =
            id("great_swamp/tee/tee_01");

    public static final List<Identifier> STRAIGHTS = List.of(
            GREAT_SWAMP_STRAIGHT_01,
            GREAT_SWAMP_STRAIGHT_02,
            GREAT_SWAMP_STRAIGHT_03,
            GREAT_SWAMP_STRAIGHT_04,
            GREAT_SWAMP_STRAIGHT_05,
            GREAT_SWAMP_STRAIGHT_06
    );
    public static final List<Identifier> CORNERS = List.of(
            GREAT_SWAMP_CORNER_01
    );
    public static final List<Identifier> TEES = List.of(
            GREAT_SWAMP_TEE_01
    );
    public static final List<Identifier> ALL = List.of(
            GREAT_SWAMP_STRAIGHT_01,
            GREAT_SWAMP_STRAIGHT_02,
            GREAT_SWAMP_STRAIGHT_03,
            GREAT_SWAMP_STRAIGHT_04,
            GREAT_SWAMP_STRAIGHT_05,
            GREAT_SWAMP_STRAIGHT_06,
            GREAT_SWAMP_CORNER_01,
            GREAT_SWAMP_TEE_01
    );

    private BuiltinDungeonCorridors() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ObeliskDepths.MOD_ID, path);
    }
}
