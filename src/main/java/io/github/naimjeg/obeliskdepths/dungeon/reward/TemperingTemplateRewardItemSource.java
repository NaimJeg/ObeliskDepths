package io.github.naimjeg.obeliskdepths.dungeon.reward;

import io.github.naimjeg.obeliskdepths.tempering.TemperingTemplateItems;
import net.minecraft.world.item.ItemStack;

import java.util.Random;
import java.util.stream.Stream;

/**
 * Reward item source that creates a tempering template for the chosen tier.
 */
public final class TemperingTemplateRewardItemSource implements DungeonRewardItemSource {
    private final TierProvider tierProvider;

    public TemperingTemplateRewardItemSource(TierProvider tierProvider) {
        this.tierProvider = tierProvider;
    }

    @Override
    public Stream<ItemStack> items(DungeonRewardContext context, Random random) {
        int tier = this.tierProvider.tier(context, random);
        float weight = Math.max(0.0F, context.instance().difficulty().rewardWeightMultiplier() - 1.0F);
        return Stream.of(TemperingTemplateItems.createTemplate(tier, weight));
    }
}
