package io.github.naimjeg.obeliskdepths.dungeon.reward;

import net.minecraft.world.item.ItemStack;

import java.util.Random;

/**
 * Vanilla-style function that sets the count of the produced stack.
 *
 * <p>If the source produced a stack of arbitrary size, this function replaces
 * the count with a fixed value while respecting the item's maximum stack size.</p>
 */
public final class SetItemCountFunction implements DungeonRewardFunction {
    private final int count;

    public SetItemCountFunction(int count) {
        this.count = Math.max(1, count);
    }

    @Override
    public ItemStack apply(ItemStack stack, DungeonRewardContext context, Random random) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack result = stack.copy();
        result.setCount(Math.min(this.count, result.getMaxStackSize()));
        return result;
    }
}
