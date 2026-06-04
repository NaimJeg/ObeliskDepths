package io.github.naimjeg.obeliskdepths.dungeon.reward;

import io.github.naimjeg.obeliskdepths.registry.ModTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Builds the scalable reward pool used by
 * {@link DefaultDungeonRewardGenerator}.
 *
 * <p>This factory replaces the hardcoded category weights and item
 * selections with reusable {@link DungeonRewardPoolEntry} instances,
 * making the same pool composition available to other generators or
 * data-driven callers.</p>
 */
public final class ScalableRewardPoolFactory {
    private ScalableRewardPoolFactory() {
    }

    public static DungeonRewardPool create(TierProvider tierProvider) {
        return new DungeonRewardPool(List.of(
                new WeightedRewardPoolEntry(
                        30,
                        new TieredTagRewardItemSource(
                                tierProvider,
                                ModTags.Items::rewardWeapons,
                                ScalableRewardPoolFactory::fallbackWeapons
                        )
                ),
                new WeightedRewardPoolEntry(
                        28,
                        new TieredTagRewardItemSource(
                                tierProvider,
                                ModTags.Items::rewardArmor,
                                ScalableRewardPoolFactory::fallbackArmor
                        )
                ),
                new DynamicWeightedRewardPoolEntry(
                        context -> 20 + Math.round(Math.max(0.0F,
                                context.instance().difficulty().rewardWeightMultiplier() - 1.0F) * 8.0F),
                        new EnchantedBookRewardItemSource(tierProvider)
                ),
                new DynamicWeightedRewardPoolEntry(
                        context -> 12 + Math.round(Math.max(0.0F,
                                context.instance().difficulty().rewardWeightMultiplier() - 1.0F) * 10.0F),
                        new TemperingTemplateRewardItemSource(tierProvider)
                )
        ));
    }

    static List<Item> fallbackWeapons(int tier) {
        return switch (DungeonRewardTiers.clampTier(tier)) {
            case 1 -> List.of(Items.IRON_SWORD, Items.IRON_AXE, Items.BOW);
            case 2 -> List.of(Items.IRON_SWORD, Items.IRON_AXE, Items.CROSSBOW, Items.TRIDENT);
            case 3 -> List.of(Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.CROSSBOW, Items.TRIDENT);
            default -> List.of(Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.MACE, Items.TRIDENT);
        };
    }

    static List<Item> fallbackArmor(int tier) {
        return switch (DungeonRewardTiers.clampTier(tier)) {
            case 1 -> List.of(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
            case 2 -> List.of(Items.CHAINMAIL_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.SHIELD);
            case 3 -> List.of(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS);
            default -> List.of(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS, Items.SHIELD);
        };
    }
}
