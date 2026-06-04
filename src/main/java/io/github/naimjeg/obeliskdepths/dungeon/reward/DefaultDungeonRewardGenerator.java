package io.github.naimjeg.obeliskdepths.dungeon.reward;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.block.entity.ObeliskChestBlockEntity;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonDifficulty;
import io.github.naimjeg.obeliskdepths.registry.ModItems;
import io.github.naimjeg.obeliskdepths.registry.ModTags;
import io.github.naimjeg.obeliskdepths.tempering.TemperingTemplateItems;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public final class DefaultDungeonRewardGenerator implements DungeonRewardGenerator {
    public static final DefaultDungeonRewardGenerator INSTANCE = new DefaultDungeonRewardGenerator();
    public static final long SCALABLE_REWARD_SALT = 0x41D3_9B78_7A15_2C6DL;
    public static final long RETURN_SCROLL_SALT = 0x63A9_D14B_31E2_8F05L;
    public static final long SPRAY_SALT = 0x2468_ACE1_1357_9BDFL;

    private static final int BASE_SCALABLE_ROLLS = 10;
    private static final int MAX_SCALABLE_ROLLS = 20;
    private static final double RETURN_SCROLL_CHANCE = 1D;
    private static final int RETURN_SCROLL_MIN = 1;
    private static final int RETURN_SCROLL_MAX = 2;

    private DefaultDungeonRewardGenerator() {
    }

    @Override
    public List<ItemStack> generate(DungeonRewardContext context) {
        List<ItemStack> output = new ArrayList<>();
        generateScalableRewards(context, output);
        generateReturnScrolls(context.rewardSeed(), output);
        ObeliskDepths.LOGGER.debug(
                "Generated dungeon reward contents: instance={}, room={}, stacks={}",
                context.instanceId(),
                context.roomId(),
                output.size()
        );
        return List.copyOf(output);
    }

    private static void generateScalableRewards(
            DungeonRewardContext context,
            List<ItemStack> output
    ) {
        DungeonDifficulty difficulty = context.instance().difficulty();
        Random random = new Random(mix(context.rewardSeed(), SCALABLE_REWARD_SALT));
        int ceilingTier = clampTier(difficulty.rewardCeilingTier());
        int baseTier = Math.min(ceilingTier, Math.max(1, difficulty.tier()));
        int rolls = Math.min(
                MAX_SCALABLE_ROLLS,
                BASE_SCALABLE_ROLLS + Math.max(0, Math.round(difficulty.amountIntensity()))
        );
        if (random.nextDouble() < Math.max(0.0D, difficulty.rewardWeightMultiplier() - 1.0D) * 0.25D) {
            rolls = Math.min(MAX_SCALABLE_ROLLS, rolls + 1);
        }

        for (int roll = 0; roll < rolls && output.size() < ObeliskChestBlockEntity.REWARD_CAPACITY; roll++) {
            DungeonRewardCategory category = chooseScalableCategory(random, difficulty.rewardWeightMultiplier());
            int tier = chooseTier(random, baseTier, ceilingTier, difficulty.rewardWeightMultiplier());
            ItemStack stack = switch (category) {
                case WEAPON -> chooseTaggedItem(context, ModTags.Items.rewardWeapons(tier), fallbackWeapons(tier), random);
                case ARMOR -> chooseTaggedItem(context, ModTags.Items.rewardArmor(tier), fallbackArmor(tier), random);
                case ENCHANTED_BOOK -> createEnchantedBook(context, tier, random);
                case TEMPERING_TEMPLATE -> TemperingTemplateItems.createTemplate(
                        tier,
                        Math.max(0.0F, difficulty.rewardWeightMultiplier() - 1.0F)
                );
                case RETURN_SCROLL -> ItemStack.EMPTY;
            };

            if (!stack.isEmpty()) {
                output.add(stack);
            }
        }

        if (output.isEmpty()) {
            output.add(chooseTaggedItem(context, ModTags.Items.rewardWeapons(baseTier), fallbackWeapons(baseTier), random));
        }
    }

    public static void generateReturnScrolls(long rewardSeed, List<ItemStack> output) {
        Random random = new Random(mix(rewardSeed, RETURN_SCROLL_SALT));
        if (random.nextDouble() >= RETURN_SCROLL_CHANCE) {
            return;
        }

        int count = RETURN_SCROLL_MIN + random.nextInt(RETURN_SCROLL_MAX - RETURN_SCROLL_MIN + 1);
        output.add(new ItemStack(ModItems.RETURN_SCROLL.get(), count));
    }

    private static DungeonRewardCategory chooseScalableCategory(Random random, float multiplier) {
        int weapon = 30;
        int armor = 28;
        int book = 20 + Math.round(Math.max(0.0F, multiplier - 1.0F) * 8.0F);
        int template = 12 + Math.round(Math.max(0.0F, multiplier - 1.0F) * 10.0F);
        int total = weapon + armor + book + template;
        int choice = random.nextInt(total);
        if ((choice -= weapon) < 0) {
            return DungeonRewardCategory.WEAPON;
        }
        if ((choice -= armor) < 0) {
            return DungeonRewardCategory.ARMOR;
        }
        if ((choice -= book) < 0) {
            return DungeonRewardCategory.ENCHANTED_BOOK;
        }
        return DungeonRewardCategory.TEMPERING_TEMPLATE;
    }

    private static int chooseTier(Random random, int baseTier, int ceilingTier, float multiplier) {
        int tier = Math.min(baseTier, ceilingTier);
        while (tier < ceilingTier) {
            double chance = 0.20D + Math.max(0.0F, multiplier - 1.0F) * 0.10D;
            if (random.nextDouble() >= Math.min(0.65D, chance)) {
                break;
            }
            tier++;
        }
        return tier;
    }

    private static ItemStack chooseTaggedItem(
            DungeonRewardContext context,
            TagKey<Item> tag,
            List<Item> fallback,
            Random random
    ) {
        Stream<Holder<Item>> fallbackStream = fallback.stream()
                .map(BuiltInRegistries.ITEM::wrapAsHolder);
        List<Item> candidates = context.level()
                .registryAccess()
                .lookupOrThrow(Registries.ITEM)
                .get(tag)
                .map(HolderSet::stream)
                .orElseGet(() -> fallbackStream)
                .map(Holder::value)
                .sorted(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()))
                .toList();
        if (candidates.isEmpty()) {
            return ItemStack.EMPTY;
        }

        return new ItemStack(candidates.get(random.nextInt(candidates.size())));
    }

    private static ItemStack createEnchantedBook(
            DungeonRewardContext context,
            int tier,
            Random random
    ) {
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
        int maxLevel = Math.max(enchantment.value().getMinLevel(), enchantment.value().getMaxLevel());
        int targetLevel = Math.min(maxLevel, Math.max(enchantment.value().getMinLevel(), tier));
        int level = enchantment.value().getMinLevel()
                + random.nextInt(targetLevel - enchantment.value().getMinLevel() + 1);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        mutable.set(enchantment, level);
        ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
        stack.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
        return stack;
    }

    private static List<Item> fallbackWeapons(int tier) {
        return switch (clampTier(tier)) {
            case 1 -> List.of(Items.IRON_SWORD, Items.IRON_AXE, Items.BOW);
            case 2 -> List.of(Items.IRON_SWORD, Items.IRON_AXE, Items.CROSSBOW, Items.TRIDENT);
            case 3 -> List.of(Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.CROSSBOW, Items.TRIDENT);
            default -> List.of(Items.DIAMOND_SWORD, Items.DIAMOND_AXE, Items.MACE, Items.TRIDENT);
        };
    }

    private static List<Item> fallbackArmor(int tier) {
        return switch (clampTier(tier)) {
            case 1 -> List.of(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
            case 2 -> List.of(Items.CHAINMAIL_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.SHIELD);
            case 3 -> List.of(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS);
            default -> List.of(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS, Items.SHIELD);
        };
    }

    private static int clampTier(int tier) {
        return Math.max(1, Math.min(4, tier));
    }

    public static long mix(long seed, long salt) {
        long value = seed ^ salt;
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }
}
