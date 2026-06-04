package io.github.naimjeg.obeliskdepths.dungeon.reward;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.IntFunction;
import java.util.stream.Stream;

/**
 * Reward item source that resolves a tag based on the chosen tier and then
 * selects a random item from that tag (with fallback items if the tag is
 * empty).
 */
public final class TieredTagRewardItemSource implements DungeonRewardItemSource {
    private final TierProvider tierProvider;
    private final IntFunction<TagKey<Item>> tagResolver;
    private final IntFunction<List<Item>> fallbackResolver;

    public TieredTagRewardItemSource(
            TierProvider tierProvider,
            IntFunction<TagKey<Item>> tagResolver,
            IntFunction<List<Item>> fallbackResolver
    ) {
        this.tierProvider = Objects.requireNonNull(tierProvider, "tierProvider");
        this.tagResolver = Objects.requireNonNull(tagResolver, "tagResolver");
        this.fallbackResolver = Objects.requireNonNull(fallbackResolver, "fallbackResolver");
    }

    @Override
    public Stream<ItemStack> items(DungeonRewardContext context, Random random) {
        int tier = this.tierProvider.tier(context, random);
        TagKey<Item> tag = this.tagResolver.apply(tier);
        List<Item> fallback = this.fallbackResolver.apply(tier);
        return new TagRewardItemSource(tag, fallback).items(context, random);
    }
}