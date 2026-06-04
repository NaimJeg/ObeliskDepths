package io.github.naimjeg.obeliskdepths.dungeon.reward;

import java.util.Random;

/**
 * Shared tier utilities used by reward generation.
 */
final class DungeonRewardTiers {
    private DungeonRewardTiers() {
    }

    static int clampTier(int tier) {
        return Math.max(1, Math.min(4, tier));
    }

    static int chooseTier(Random random, int baseTier, int ceilingTier, float multiplier) {
        int tier = Math.min(baseTier, ceilingTier);
        while (tier < ceilingTier) {
            double chance = 0.20D + Math.max(0.0F, multiplier - 1.0F) * 0.10D;
            if (random.nextDouble() >= Math.min(0.65D, chance)) {
                break;
            }
            tier++;
        }
        return tier;
    }
}
