package io.github.naimjeg.obeliskdepths.dungeon.reward;

import java.util.Random;

/**
 * Vanilla-style condition predicate for reward generation.
 *
 * <p>Mirrors the role of {@code LootItemCondition}: it inspects the
 * {@link DungeonRewardContext} and random source to decide whether an
 * entry, source, or function should apply.</p>
 */
@FunctionalInterface
public interface DungeonRewardCondition {
    boolean test(DungeonRewardContext context, Random random);

    /**
     * Returns a condition that passes only when both this and the other pass.
     */
    default DungeonRewardCondition and(DungeonRewardCondition other) {
        return (context, random) -> this.test(context, random) && other.test(context, random);
    }

    /**
     * Returns a condition that inverts this result.
     */
    default DungeonRewardCondition negate() {
        return (context, random) -> !this.test(context, random);
    }
}
