package io.github.naimjeg.obeliskdepths.dungeon.reward;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Random;
import java.util.stream.Stream;

/**
 * Reward item source that produces a random non-curse enchanted book
 * scaled to the chosen tier.
 */
public final class EnchantedBookRewardItemSource implements DungeonRewardItemSource {
    private final TierProvider tierProvider;

    public EnchantedBookRewardItemSource(TierProvider tierProvider) {
        this.tierProvider = tierProvider;
    }

    @Override
    public Stream<ItemStack> items(DungeonRewardContext context, Random random) {
        int tier = this.tierProvider.tier(context, random);
        EnchantmentRewardFunction function = new EnchantmentRewardFunction(tier);
        ItemStack book = function.apply(new ItemStack(Items.ENCHANTED_BOOK), context, random);
        return book.isEmpty() ? Stream.empty() : Stream.of(book);
    }
}
