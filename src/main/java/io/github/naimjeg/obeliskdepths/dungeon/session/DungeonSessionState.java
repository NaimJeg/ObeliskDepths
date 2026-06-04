package io.github.naimjeg.obeliskdepths.dungeon.session;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum DungeonSessionState implements StringRepresentable {
    WAITING_FOR_ENTRY("waiting_for_entry"),
    ACTIVE("active"),
    COMPLETED("completed"),
    ABANDON_PENDING("abandon_pending"),
    ABANDONED("abandoned"),
    FAILED("failed"),
    CLEANED("cleaned");

    public static final Codec<DungeonSessionState> CODEC =
            StringRepresentable.fromEnum(DungeonSessionState::values);

    private final String serializedName;

    DungeonSessionState(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }

    public boolean needsRuntimeTick() {
        return this == ACTIVE || this == ABANDON_PENDING;
    }

    public boolean acceptsPortalEntry() {
        return this == WAITING_FOR_ENTRY
                || this == ACTIVE
                || this == ABANDON_PENDING;
    }

    public boolean keepsRuntimeInstanceAlive() {
        return this == WAITING_FOR_ENTRY || needsRuntimeTick();
    }
}