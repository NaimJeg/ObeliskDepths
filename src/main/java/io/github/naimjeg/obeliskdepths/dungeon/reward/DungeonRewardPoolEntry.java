package io.github.naimjeg.obeliskdepths.dungeon.reward;

import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Random;

/**
 * A single weighted entry inside a {@link DungeonRewardPool}.
 *
 * <p>Semantically similar to vanilla's {@code LootPoolEntryContainer}:
 * it contributes zero or more {@link ItemStack}s when the pool is rolled
 * and the entry is selected by weight.</p>
 *
 * <p>The pool evaluates eligibility before weighted selection so that an entry
 * whose conditions fail does not consume a roll.  Callers that invoke
 * {@link #generate} directly are responsible for checking eligibility first.</p>
 */
@FunctionalInterface
public interface DungeonRewardPoolEntry {
    /**
     * Generates items for this entry.
     *
     * @param context the reward context
     * @param random  the deterministic random source for this roll
     * @return produced item stacks; may be empty but never {@code null}
     */
    List<ItemStack> generate(DungeonRewardContext context, Random random);

    /**
     * Whether this entry is eligible for the current roll.
     *
     * <p>Default returns {@code true}.  Implementations with conditions
     * evaluate them here so that the pool can exclude ineligible entries
     * before weighted selection.  Each entry's eligibility is evaluated
     * exactly once per roll.</p>
     *
     * <p>This method is separate from {@link #generate}:
     * the pool calls {@code isEligible} during selection,
     * then calls {@code generate} only for the chosen entry,
     * without re-evaluating conditions.</p>
     */
    default boolean isEligible(DungeonRewardContext context, Random random) {
        return true;
    }

    /**
     * @return the relative weight used when this entry is drawn from its pool.
     */
    default int weight() {
        return 1;
    }

    /**
     * @return the relative weight for the given context; defaults to
     *         {@link #weight()} for constant-weight entries.
     */
    default int weight(DungeonRewardContext context) {
        return weight();
    }
}