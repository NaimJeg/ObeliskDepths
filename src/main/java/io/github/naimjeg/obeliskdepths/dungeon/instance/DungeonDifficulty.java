package io.github.naimjeg.obeliskdepths.dungeon.instance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record DungeonDifficulty(
        int tier,
        float amountIntensity,
        float rewardWeightMultiplier,
        int rewardCeilingTier
) {
    public static final Codec<DungeonDifficulty> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("tier").forGetter(DungeonDifficulty::tier),
            Codec.FLOAT.fieldOf("amount_intensity").forGetter(DungeonDifficulty::amountIntensity),
            Codec.FLOAT.fieldOf("reward_weight_multiplier").forGetter(DungeonDifficulty::rewardWeightMultiplier),
            Codec.INT.fieldOf("reward_ceiling_tier").forGetter(DungeonDifficulty::rewardCeilingTier)
    ).apply(instance, DungeonDifficulty::new));

    public float effectiveBaseDifficulty() {
        return this.tier + this.amountIntensity;
    }
}