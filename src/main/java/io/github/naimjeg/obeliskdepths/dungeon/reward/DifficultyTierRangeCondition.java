package io.github.naimjeg.obeliskdepths.dungeon.reward;

import java.util.Objects;
import java.util.Random;

/**
 * Condition that passes only when the dungeon's configured difficulty tier
 * falls within an inclusive range.
 */
public final class DifficultyTierRangeCondition implements DungeonRewardCondition {
    private final int minTier;
    private final int maxTier;

    public DifficultyTierRangeCondition(int minTier, int maxTier) {
        this.minTier = Math.max(1, minTier);
        this.maxTier = Math.max(this.minTier, maxTier);
    }

    @Override
    public boolean test(DungeonRewardContext context, Random random) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(random, "random");
        int tier = DungeonRewardTiers.clampTier(context.instance().difficulty().tier());
        return tier >= this.minTier && tier <= this.maxTier;
    }
}