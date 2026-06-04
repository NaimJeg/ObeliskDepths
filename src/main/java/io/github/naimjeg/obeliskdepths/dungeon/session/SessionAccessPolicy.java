package io.github.naimjeg.obeliskdepths.dungeon.session;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum SessionAccessPolicy implements StringRepresentable {
    STARTER_ONLY("starter_only"),
    OPEN("open"),
    ALLOWLIST("allowlist");

    public static final Codec<SessionAccessPolicy> CODEC =
            StringRepresentable.fromEnum(SessionAccessPolicy::values);

    private final String serializedName;

    SessionAccessPolicy(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }
}
