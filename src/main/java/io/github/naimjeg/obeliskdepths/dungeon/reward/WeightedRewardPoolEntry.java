package io.github.naimjeg.obeliskdepths.dungeon.reward;

import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * A concrete {@link DungeonRewardPoolEntry} that combines an item source,
 * optional conditions, optional post-processing functions, and a weight.
 *
 * <p>This is the reusable building block for constructing reward pools
 * without hardcoding selection logic.</p>
 *
 * <p>Conditions are evaluated during {@link #isEligible} before weighted
 * selection, not during {@link #generate}.  This ensures an ineligible
 * entry does not waste a pool roll.</p>
 */
public final class WeightedRewardPoolEntry implements DungeonRewardPoolEntry {
    private final int weight;
    private final DungeonRewardItemSource source;
    private final List<DungeonRewardCondition> conditions;
    private final List<DungeonRewardFunction> functions;

    public WeightedRewardPoolEntry(
            int weight,
            DungeonRewardItemSource source
    ) {
        this(weight, source, List.of(), List.of());
    }

    public WeightedRewardPoolEntry(
            int weight,
            DungeonRewardItemSource source,
            List<DungeonRewardCondition> conditions,
            List<DungeonRewardFunction> functions
    ) {
        this.weight = Math.max(0, weight);
        this.source = Objects.requireNonNull(source, "source");
        this.conditions = List.copyOf(Objects.requireNonNull(conditions, "conditions"));
        this.functions = List.copyOf(Objects.requireNonNull(functions, "functions"));
        for (DungeonRewardCondition condition : this.conditions) {
            Objects.requireNonNull(condition, "condition element");
        }
        for (DungeonRewardFunction function : this.functions) {
            Objects.requireNonNull(function, "function element");
        }
    }

    @Override
    public boolean isEligible(DungeonRewardContext context, Random random) {
        return conditions.isEmpty()
                || RewardEntrySupport.conditionsPass(this.conditions, context, random);
    }

    @Override
    public int weight() {
        return this.weight;
    }

    @Override
    public List<ItemStack> generate(DungeonRewardContext context, Random random) {
        return RewardEntrySupport.applySource(this.source, this.functions, context, random);
    }
}