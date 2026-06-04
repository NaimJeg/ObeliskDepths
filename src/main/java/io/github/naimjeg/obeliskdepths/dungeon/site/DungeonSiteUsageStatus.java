package io.github.naimjeg.obeliskdepths.dungeon.site;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum DungeonSiteUsageStatus implements StringRepresentable {
    RESERVED("reserved"),
    COMPLETED("completed"),
    ABANDONED("abandoned");

    public static final Codec<DungeonSiteUsageStatus> CODEC =
            StringRepresentable.fromEnum(DungeonSiteUsageStatus::values);

    private final String serializedName;

    DungeonSiteUsageStatus(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }

    public boolean isTerminal() {
        return this != RESERVED;
    }
}