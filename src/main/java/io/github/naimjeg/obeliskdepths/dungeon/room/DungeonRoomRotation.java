package io.github.naimjeg.obeliskdepths.dungeon.room;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Rotation;

public enum DungeonRoomRotation implements StringRepresentable {
    NONE("none", Rotation.NONE),
    CLOCKWISE_90("clockwise_90", Rotation.CLOCKWISE_90),
    CLOCKWISE_180("clockwise_180", Rotation.CLOCKWISE_180),
    COUNTERCLOCKWISE_90("counterclockwise_90", Rotation.COUNTERCLOCKWISE_90);

    public static final Codec<DungeonRoomRotation> CODEC =
            StringRepresentable.fromEnum(DungeonRoomRotation::values);

    private final String serializedName;
    private final Rotation minecraftRotation;

    DungeonRoomRotation(
            String serializedName,
            Rotation minecraftRotation
    ) {
        this.serializedName = serializedName;
        this.minecraftRotation = minecraftRotation;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }

    public Rotation toMinecraftRotation() {
        return this.minecraftRotation;
    }
}
