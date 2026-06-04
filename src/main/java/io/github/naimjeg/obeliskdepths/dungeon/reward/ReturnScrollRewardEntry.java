package io.github.naimjeg.obeliskdepths.dungeon.reward;

import io.github.naimjeg.obeliskdepths.registry.ModItems;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Random;

/**
 * Reusable reward pool entry that produces return scrolls with a configurable
 * chance and count range.
 *
 * <p>Used by {@link DefaultDungeonRewardGenerator#generateReturnScrolls}
 * so the same logic can be shared by other generators or events.</p>
 *
 * <p>The chance is clamped to {@code [0.0, 1.0]} to match the policy of
 * {@link RandomChanceCondition}.</p>
 */
public final class ReturnScrollRewardEntry implements DungeonRewardPoolEntry {
    private final double chance;
    private final int minCount;
    private final int maxCount;

    public ReturnScrollRewardEntry(double chance, int minCount, int maxCount) {
        this.chance = Math.max(0.0D, Math.min(1.0D, chance));
        this.minCount = Math.max(1, minCount);
        this.maxCount = Math.max(this.minCount, maxCount);
    }

    @Override
    public List<ItemStack> generate(DungeonRewardContext context, Random random) {
        if (random.nextDouble() >= this.chance) {
            return List.of();
        }

        int count = this.minCount + random.nextInt(this.maxCount - this.minCount + 1);
        return List.of(new ItemStack(ModItems.RETURN_SCROLL.get(), count));
    }
}