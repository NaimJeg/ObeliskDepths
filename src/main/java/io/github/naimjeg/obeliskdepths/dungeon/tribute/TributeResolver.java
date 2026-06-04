package io.github.naimjeg.obeliskdepths.dungeon.tribute;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class TributeResolver {
    private TributeResolver() {
    }

    public static ResolvedTribute resolve(ItemStack stack) {
        if (stack.isEmpty()) {
            return ResolvedTribute.invalid();
        }

        int tier;

        if (stack.is(Items.IRON_INGOT)) {
            tier = 1;
        } else if (stack.is(Items.DIAMOND)) {
            tier = 2;
        } else if (stack.is(Items.NETHERITE_INGOT)) {
            tier = 3;
        } else if (stack.is(Items.ECHO_SHARD)) {
            tier = 4;
        } else {
            return ResolvedTribute.invalid();
        }

        int amount = Math.max(1, stack.getCount());

        float amountIntensity = calculateAmountIntensity(amount);
        float rewardWeightMultiplier = 1.0F + amountIntensity;

        return new ResolvedTribute(
                true,
                tier,
                amount,
                amountIntensity,
                rewardWeightMultiplier,
                tier
        );
    }

    private static float calculateAmountIntensity(int amount) {
        float normalized = (float) Math.log(amount) / (float) Math.log(64);
        return Math.min(0.8F, Math.max(0.0F, normalized * 0.8F));
    }
}