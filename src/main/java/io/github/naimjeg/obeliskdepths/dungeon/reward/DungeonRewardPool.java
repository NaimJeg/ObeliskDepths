package io.github.naimjeg.obeliskdepths.dungeon.reward;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A weighted pool of {@link DungeonRewardPoolEntry}s.
 *
 * <p>Modeled after vanilla's {@code LootPool}: each roll picks one entry
 * by weight, and the entry contributes items. Rolls are bounded by the
 * requested count and by the caller-supplied capacity.</p>
 *
 * <p>Entries are filtered by {@link DungeonRewardPoolEntry#isEligible}
 * before weighted selection so that an ineligible entry does not consume
 * a roll.  Contextual weights are computed once per entry per roll and
 * summed with {@code long} to avoid overflow.</p>
 */
public final class DungeonRewardPool {
    private final List<DungeonRewardPoolEntry> entries;

    public DungeonRewardPool(List<DungeonRewardPoolEntry> entries) {
        this.entries = List.copyOf(entries);
    }

    /**
     * Rolls this pool the given number of times, appending produced stacks
     * to {@code output} until the requested rolls are exhausted or the
     * capacity is reached.
     *
     * @param context  reward context
     * @param random   random source
     * @param rolls    number of rolls to perform
     * @param capacity maximum stacks to append
     * @param output   destination list
     */
    public void generate(
            DungeonRewardContext context,
            Random random,
            int rolls,
            int capacity,
            List<ItemStack> output
    ) {
        for (int roll = 0; roll < rolls && output.size() < capacity; roll++) {
            DungeonRewardPoolEntry entry = choose(context, random);
            if (entry == null) {
                continue;
            }

            List<ItemStack> stacks = entry.generate(context, random);
            for (ItemStack stack : stacks) {
                if (stack != null && !stack.isEmpty()) {
                    output.add(stack);
                    if (output.size() >= capacity) {
                        return;
                    }
                }
            }
        }
    }

    /**
     * Convenience overload that returns a new list with up to
     * {@code capacity} stacks.
     */
    public List<ItemStack> generate(
            DungeonRewardContext context,
            Random random,
            int rolls,
            int capacity
    ) {
        List<ItemStack> output = new ArrayList<>(Math.min(rolls, capacity));
        generate(context, random, rolls, capacity, output);
        return List.copyOf(output);
    }

    /**
     * Selects an eligible entry by weight.
     *
     * <p>Eligibility is evaluated first.  Contextual weights are computed
     * once per entry, clamped to zero, and stored.  The total is computed
     * with {@code long} to avoid overflow.</p>
     *
     * <p>Selection uses bounded {@code long} without biased modulo.
     * If the random {@code long} lands on a weight boundary the
     * entry at that boundary is returned; entries with zero weight
     * are skipped.</p>
     *
     * @return the chosen entry, or {@code null} if no entry is eligible
     *         or all eligible weights sum to zero
     */
    private DungeonRewardPoolEntry choose(DungeonRewardContext context, Random random) {
        int n = this.entries.size();

        // Phase 1: evaluate eligibility and store per-entry weights
        long[] weights = new long[n];
        long total = 0L;
        for (int i = 0; i < n; i++) {
            DungeonRewardPoolEntry entry = this.entries.get(i);
            if (!entry.isEligible(context, random)) {
                continue; // ineligible: contributes zero weight
            }
            long w = Math.max(0L, (long) entry.weight(context));
            weights[i] = w;
            total += w;
        }

        if (total <= 0L) {
            return null;
        }

        // Phase 2: select by bounded long (no biased modulo)
        long choice = boundLong(random, total);
        long cumulative = 0L;
        for (int i = 0; i < n; i++) {
            if (weights[i] <= 0L) {
                continue;
            }
            cumulative += weights[i];
            if (choice < cumulative) {
                return this.entries.get(i);
            }
        }

        // fallback: return the first entry with non-zero weight
        for (int i = 0; i < n; i++) {
            if (weights[i] > 0L) {
                return this.entries.get(i);
            }
        }
        return null;
    }

    /**
     * Returns a uniformly distributed long value in {@code [0, bound)}.
     *
     * <p>Uses the same rejection-sampling strategy as
     * {@code Random.nextLong(long)} in Java 21 so the RNG sequence
     * is compatible with code that uses that method directly.</p>
     */
    static long boundLong(Random random, long bound) {
        long bits;
        long val;
        do {
            bits = random.nextLong() >>> 1;
            val = bits % bound;
        } while (bits - val + (bound - 1L) < 0L);
        return val;
    }
}