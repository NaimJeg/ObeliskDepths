package io.github.naimjeg.obeliskdepths.dungeon.artifact;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum DungeonRuntimeArtifactType implements StringRepresentable {
    REWARD_CHEST("reward_chest"),
    REWARD_ITEM("reward_item"),
    ENCOUNTER_ENTITY("encounter_entity"),
    TEMPORARY_BLOCK("temporary_block");

    public static final Codec<DungeonRuntimeArtifactType> CODEC =
            StringRepresentable.fromEnum(DungeonRuntimeArtifactType::values);

    private final String serializedName;

    DungeonRuntimeArtifactType(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }
}
