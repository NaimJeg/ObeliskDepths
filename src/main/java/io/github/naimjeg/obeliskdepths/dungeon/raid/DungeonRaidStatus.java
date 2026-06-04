package io.github.naimjeg.obeliskdepths.dungeon.raid;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum DungeonRaidStatus implements StringRepresentable {
    PREPARING("preparing"),
    ACTIVE("active"),
    WON("won"),
    FAILED("failed"),
    EXPIRED("expired");

    public static final Codec<DungeonRaidStatus> CODEC =
            StringRepresentable.fromEnum(DungeonRaidStatus::values);

    private final String serializedName;

    DungeonRaidStatus(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }
}