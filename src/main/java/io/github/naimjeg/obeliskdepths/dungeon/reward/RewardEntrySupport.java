package io.github.naimjeg.obeliskdepths.dungeon.reward;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

/**
 * Shared logic for applying conditions and functions to reward entries.
 *
 * <p>Package-private helper used by {@link WeightedRewardPoolEntry} and
 * {@link DynamicWeightedRewardPoolEntry} so both entry types behave
 * consistently when filtering (conditions) and transforming (functions)
 * produced items.</p>
 */
final class RewardEntrySupport {
    private RewardEntrySupport() {
    }

    static boolean conditionsPass(
            List<DungeonRewardCondition> conditions,
            DungeonRewardContext context,
            Random random
    ) {
        for (DungeonRewardCondition condition : conditions) {
            if (!condition.test(context, random)) {
                return false;
            }
        }
        return true;
    }

    static List<ItemStack> applySource(
            DungeonRewardItemSource source,
            List<DungeonRewardFunction> functions,
            DungeonRewardContext context,
            Random random
    ) {
        List<ItemStack> result = new ArrayList<>();
        try (Stream<ItemStack> stream = source.items(context, random)) {
            stream.forEach(stack -> {
                ItemStack processed = applyFunctions(stack, functions, context, random);
                if (!processed.isEmpty()) {
                    result.add(processed);
                }
            });
        }
        return List.copyOf(result);
    }

    static ItemStack applyFunctions(
            ItemStack stack,
            List<DungeonRewardFunction> functions,
            DungeonRewardContext context,
            Random random
    ) {
        ItemStack processed = stack.copy();
        for (DungeonRewardFunction function : functions) {
            processed = function.apply(processed, context, random);
            if (processed.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        return processed;
    }
}
