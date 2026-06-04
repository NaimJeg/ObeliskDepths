package io.github.naimjeg.obeliskdepths.dungeon.reward;

import net.minecraft.world.item.ItemStack;

import java.util.Random;

/**
 * Vanilla-style function that clamps the produced stack count to an inclusive
 * range.
 *
 * <p>Unlike {@link SetItemCountFunction}, this preserves the original count
 * as long as it lies within {@code [min, max]}.</p>
 */
public final class LimitCountFunction implements DungeonRewardFunction {
    private final int min;
    private final int max;

    public LimitCountFunction(int min, int max) {
        this.min = Math.max(1, min);
        this.max = Math.max(this.min, max);
    }

    @Override
    public ItemStack apply(ItemStack stack, DungeonRewardContext context, Random random) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack result = stack.copy();
        int clamped = Math.max(this.min, Math.min(this.max, result.getCount()));
        result.setCount(Math.min(clamped, result.getMaxStackSize()));
        return result;
    }
}
