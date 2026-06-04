package io.github.naimjeg.obeliskdepths.dungeon.encounter;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum DungeonEncounterMobRole implements StringRepresentable {
    NORMAL("normal"),
    BOSS("boss");

    public static final Codec<DungeonEncounterMobRole> CODEC =
            StringRepresentable.fromEnum(DungeonEncounterMobRole::values);

    private final String serializedName;

    DungeonEncounterMobRole(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }
}
