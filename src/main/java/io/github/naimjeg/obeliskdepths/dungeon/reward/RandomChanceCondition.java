package io.github.naimjeg.obeliskdepths.dungeon.reward;

import java.util.Random;

/**
 * Vanilla-style random-chance condition for reward entries.
 *
 * <p>The entry only applies when {@code random.nextDouble()} is less than
 * the configured chance. This is a reusable alternative to inlining chance
 * checks inside custom {@link DungeonRewardPoolEntry} implementations.</p>
 */
public final class RandomChanceCondition implements DungeonRewardCondition {
    private final double chance;

    public RandomChanceCondition(double chance) {
        this.chance = Math.max(0.0D, Math.min(1.0D, chance));
    }

    @Override
    public boolean test(DungeonRewardContext context, Random random) {
        return random.nextDouble() < this.chance;
    }
}
