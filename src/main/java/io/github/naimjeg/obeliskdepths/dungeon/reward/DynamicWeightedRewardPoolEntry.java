package io.github.naimjeg.obeliskdepths.dungeon.reward;

import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.ToIntFunction;

/**
 * A {@link DungeonRewardPoolEntry} whose weight is computed from the
 * {@link DungeonRewardContext} rather than being a constant.
 *
 * <p>This mirrors how vanilla loot pools can use dynamic providers while
 * still participating in weighted selection. Like
 * {@link WeightedRewardPoolEntry}, it supports optional conditions and
 * post-processing functions.</p>
 *
 * <p>Conditions are evaluated during {@link #isEligible} before weighted
 * selection, not during {@link #generate}.  This ensures an ineligible
 * entry does not waste a pool roll.</p>
 */
public final class DynamicWeightedRewardPoolEntry implements DungeonRewardPoolEntry {
    private final ToIntFunction<DungeonRewardContext> weightFunction;
    private final DungeonRewardItemSource source;
    private final List<DungeonRewardCondition> conditions;
    private final List<DungeonRewardFunction> functions;

    public DynamicWeightedRewardPoolEntry(
            ToIntFunction<DungeonRewardContext> weightFunction,
            DungeonRewardItemSource source
    ) {
        this(weightFunction, source, List.of(), List.of());
    }

    public DynamicWeightedRewardPoolEntry(
            ToIntFunction<DungeonRewardContext> weightFunction,
            DungeonRewardItemSource source,
            List<DungeonRewardCondition> conditions,
            List<DungeonRewardFunction> functions
    ) {
        this.weightFunction = Objects.requireNonNull(weightFunction, "weightFunction");
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
    public int weight(DungeonRewardContext context) {
        return Math.max(0, this.weightFunction.applyAsInt(context));
    }

    @Override
    public int weight() {
        return 1;
    }

    @Override
    public List<ItemStack> generate(DungeonRewardContext context, Random random) {
        return RewardEntrySupport.applySource(this.source, this.functions, context, random);
    }
}