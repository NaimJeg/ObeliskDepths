package io.github.naimjeg.obeliskdepths.dungeon.reward;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Reward item source that always returns a single stack of a fixed item.
 */
public final class SingletonRewardItemSource implements DungeonRewardItemSource {
    private final Supplier<? extends ItemLike> item;
    private final int count;

    public SingletonRewardItemSource(Supplier<? extends ItemLike> item) {
        this(item, 1);
    }

    public SingletonRewardItemSource(Supplier<? extends ItemLike> item, int count) {
        this.item = item;
        this.count = Math.max(1, count);
    }

    @Override
    public Stream<ItemStack> items(DungeonRewardContext context, Random random) {
        return Stream.of(new ItemStack(this.item.get(), this.count));
    }
}
