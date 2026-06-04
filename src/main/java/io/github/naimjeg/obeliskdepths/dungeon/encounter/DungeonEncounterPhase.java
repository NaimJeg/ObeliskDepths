package io.github.naimjeg.obeliskdepths.dungeon.encounter;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum DungeonEncounterPhase implements StringRepresentable {
    COMBAT("combat"),
    BOSS("boss"),
    COMPLETE("complete"),
    EXPIRED("expired"),
    FAILED("failed");

    public static final Codec<DungeonEncounterPhase> CODEC =
            StringRepresentable.fromEnum(DungeonEncounterPhase::values);

    private final String serializedName;

    DungeonEncounterPhase(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }

    public boolean active() {
        return this == COMBAT || this == BOSS;
    }
}
