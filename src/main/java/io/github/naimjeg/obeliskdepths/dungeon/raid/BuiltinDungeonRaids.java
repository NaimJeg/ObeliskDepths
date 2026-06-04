package io.github.naimjeg.obeliskdepths.dungeon.raid;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import net.minecraft.resources.Identifier;

public final class BuiltinDungeonRaids {
    public static final Identifier COMBAT_ROOM =
            Identifier.parse(ObeliskDepths.MOD_ID + ":combat_room");

    public static final Identifier BOSS_ROOM =
            Identifier.parse(ObeliskDepths.MOD_ID + ":boss_room");

    private BuiltinDungeonRaids() {
    }
}