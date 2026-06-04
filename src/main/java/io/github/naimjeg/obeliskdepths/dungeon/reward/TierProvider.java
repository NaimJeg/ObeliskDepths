package io.github.naimjeg.obeliskdepths.dungeon.reward;

import java.util.Random;

/**
 * Decides which reward tier to use for a given roll.
 *
 * <p>Abstracting tier selection makes it reusable across different reward
 * categories and generators instead of hardcoding the algorithm inside
 * {@link DefaultDungeonRewardGenerator}.</p>
 */
@FunctionalInterface
public interface TierProvider {
    int tier(DungeonRewardContext context, Random random);

    /**
     * Returns a provider backed by the dungeon difficulty, using the same
     * tier-climbing algorithm the default generator historically used.
     */
    static TierProvider difficultyBased() {
        return (context, random) -> {
            int ceiling = DungeonRewardTiers.clampTier(
                    context.instance().difficulty().rewardCeilingTier()
            );
            int base = Math.min(
                    ceiling,
                    Math.max(1, context.instance().difficulty().tier())
            );
            return DungeonRewardTiers.chooseTier(
                    random,
                    base,
                    ceiling,
                    context.instance().difficulty().rewardWeightMultiplier()
            );
        };
    }
}
