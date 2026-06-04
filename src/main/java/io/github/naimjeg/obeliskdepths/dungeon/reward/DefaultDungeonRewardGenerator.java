package io.github.naimjeg.obeliskdepths.dungeon.reward;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.block.entity.ObeliskChestBlockEntity;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonDifficulty;
import io.github.naimjeg.obeliskdepths.registry.ModTags;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
        generateReturnScrolls(context, output);
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
        int ceilingTier = DungeonRewardTiers.clampTier(difficulty.rewardCeilingTier());
        int rolls = Math.min(
                MAX_SCALABLE_ROLLS,
                BASE_SCALABLE_ROLLS + Math.max(0, Math.round(difficulty.amountIntensity()))
        );
        if (random.nextDouble() < Math.max(0.0D, difficulty.rewardWeightMultiplier() - 1.0D) * 0.25D) {
            rolls = Math.min(MAX_SCALABLE_ROLLS, rolls + 1);
        }

        DungeonRewardPool pool = ScalableRewardPoolFactory.create(TierProvider.difficultyBased());
        pool.generate(context, random, rolls, ObeliskChestBlockEntity.REWARD_CAPACITY, output);

        if (output.isEmpty()) {
            int baseTier = Math.min(ceilingTier, Math.max(1, difficulty.tier()));
            output.add(chooseFallbackWeapon(context, baseTier, random));
        }
    }

    public static void generateReturnScrolls(DungeonRewardContext context, List<ItemStack> output) {
        Random random = new Random(mix(context.rewardSeed(), RETURN_SCROLL_SALT));
        output.addAll(new ReturnScrollRewardEntry(
                RETURN_SCROLL_CHANCE,
                RETURN_SCROLL_MIN,
                RETURN_SCROLL_MAX
        ).generate(context, random));
    }

    private static ItemStack chooseFallbackWeapon(
            DungeonRewardContext context,
            int baseTier,
            Random random
    ) {
        TagRewardItemSource source = new TagRewardItemSource(
                ModTags.Items.rewardWeapons(baseTier),
                ScalableRewardPoolFactory.fallbackWeapons(baseTier)
        );
        return source.items(context, random)
                .findFirst()
                .orElse(ItemStack.EMPTY);
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
