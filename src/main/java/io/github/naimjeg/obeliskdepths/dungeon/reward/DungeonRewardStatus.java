package io.github.naimjeg.obeliskdepths.dungeon.reward;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum DungeonRewardStatus implements StringRepresentable {
    NOT_READY("not_ready"),
    BOSS_DEFEATED("boss_defeated"),
    PLACEMENT_PENDING("placement_pending"),
    PLACEMENT_FAILED("placement_failed"),
    AVAILABLE("available"),
    CLAIMING("claiming"),
    OPENED("opened"),
    CLAIMED("claimed"),
    EXPIRED("expired"),
    CLEANED("cleaned");

    public static final Codec<DungeonRewardStatus> CODEC =
            StringRepresentable.fromEnum(DungeonRewardStatus::values);

    private final String serializedName;

    DungeonRewardStatus(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }

    public boolean terminal() {
        return this == CLAIMED || this == EXPIRED || this == CLEANED;
    }

    public boolean claimable() {
        return this == AVAILABLE || this == PLACEMENT_FAILED;
    }
}
