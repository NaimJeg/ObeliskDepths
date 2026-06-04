package io.github.naimjeg.obeliskdepths.dungeon.room;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum DungeonRoomStatus implements StringRepresentable {
    LOCKED("locked"),
    UNDISCOVERED("undiscovered"),
    DISCOVERED("discovered"),
    ACTIVE("active"),
    CLEARED("cleared"),
    FAILED("failed");

    public static final Codec<DungeonRoomStatus> CODEC =
            StringRepresentable.fromEnum(DungeonRoomStatus::values);

    private final String serializedName;

    DungeonRoomStatus(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }
}