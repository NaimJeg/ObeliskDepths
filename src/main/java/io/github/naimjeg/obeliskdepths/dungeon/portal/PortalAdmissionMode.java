package io.github.naimjeg.obeliskdepths.dungeon.portal;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum PortalAdmissionMode implements StringRepresentable {
    SOLO("solo"),
    OPEN_JOIN("open_join");

    public static final Codec<PortalAdmissionMode> CODEC =
            StringRepresentable.fromEnum(PortalAdmissionMode::values);

    private final String serializedName;

    PortalAdmissionMode(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }
}
