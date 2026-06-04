package io.github.naimjeg.obeliskdepths.dungeon.reward;

import net.minecraft.world.item.ItemStack;

import java.util.Random;

/**
 * Vanilla-style transformation function for reward items.
 *
 * <p>Mirrors the role of {@code LootItemFunction}: it takes an
 * {@link ItemStack} produced by a source and returns a modified stack.</p>
 */
@FunctionalInterface
public interface DungeonRewardFunction {
    ItemStack apply(ItemStack stack, DungeonRewardContext context, Random random);

    /**
     * Returns a function that applies this function and then the other.
     */
    default DungeonRewardFunction andThen(DungeonRewardFunction other) {
        return (stack, context, random) -> other.apply(
                this.apply(stack, context, random),
                context,
                random
        );
    }
}
