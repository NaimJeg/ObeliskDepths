package io.github.naimjeg.obeliskdepths.dungeon.reward;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Vanilla-style reward function that transforms an empty or placeholder
 * stack into a random enchanted book.
 *
 * <p>Replaces the hardcoded curse filtering and level randomisation from the
 * default dungeon reward generator with a reusable component.</p>
 */
public final class EnchantmentRewardFunction implements DungeonRewardFunction {
    private final int tier;

    public EnchantmentRewardFunction(int tier) {
        this.tier = Math.max(1, tier);
    }

    @Override
    public ItemStack apply(ItemStack stack, DungeonRewardContext context, Random random) {
        List<Holder.Reference<Enchantment>> candidates = context.level()
                .registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .listElements()
                .filter(holder -> holder.unwrapKey()
                        .map(key -> !key.identifier().getPath().contains("curse"))
                        .orElse(true))
                .sorted(Comparator.comparing(holder -> holder.key().identifier().toString()))
                .toList();

        if (candidates.isEmpty()) {
            return ItemStack.EMPTY;
        }

        Holder<Enchantment> enchantment = candidates.get(random.nextInt(candidates.size()));
        Enchantment value = enchantment.value();
        int maxLevel = Math.max(value.getMinLevel(), value.getMaxLevel());
        int targetLevel = Math.min(maxLevel, Math.max(value.getMinLevel(), this.tier));
        int level = value.getMinLevel()
                + random.nextInt(targetLevel - value.getMinLevel() + 1);

        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        mutable.set(enchantment, level);
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        book.set(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
        return book;
    }
}
