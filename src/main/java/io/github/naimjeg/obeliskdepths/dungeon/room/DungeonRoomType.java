package io.github.naimjeg.obeliskdepths.dungeon.room;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum DungeonRoomType implements StringRepresentable {
    START("start"),
    COMBAT("combat"),
    TREASURE("treasure"),
    BOSS("boss");

    public static final Codec<DungeonRoomType> CODEC =
            StringRepresentable.fromEnum(DungeonRoomType::values);

    private final String serializedName;

    DungeonRoomType(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }
}
