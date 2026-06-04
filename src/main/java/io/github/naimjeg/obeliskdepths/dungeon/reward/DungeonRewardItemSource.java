package io.github.naimjeg.obeliskdepths.dungeon.reward;

import net.minecraft.world.item.ItemStack;

import java.util.Random;
import java.util.stream.Stream;

/**
 * Produces a stream of candidate {@link ItemStack}s for a reward entry.
 *
 * <p>Similar to vanilla's {@code LootPoolSingletonEntry} or tag-based entries:
 * it abstracts where candidate items come from (a fixed item, a tag, etc.).</p>
 */
@FunctionalInterface
public interface DungeonRewardItemSource {
    Stream<ItemStack> items(DungeonRewardContext context, Random random);
}
