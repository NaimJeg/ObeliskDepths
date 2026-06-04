package io.github.naimjeg.obeliskdepths.dungeon.tribute;

import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonDifficulty;

public record ResolvedTribute(
        boolean valid,
        int tier,
        int amount,
        float amountIntensity,
        float rewardWeightMultiplier,
        int rewardCeilingTier
) {
    public static ResolvedTribute invalid() {
        return new ResolvedTribute(
                false,
                0,
                0,
                0.0F,
                0.0F,
                0
        );
    }

    public DungeonDifficulty toDifficulty() {
        if (!this.valid) {
            throw new IllegalStateException("Cannot create dungeon difficulty from invalid tribute.");
        }

        return new DungeonDifficulty(
                this.tier,
                this.amountIntensity,
                this.rewardWeightMultiplier,
                this.rewardCeilingTier
        );
    }
}