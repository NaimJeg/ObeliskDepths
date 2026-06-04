package io.github.naimjeg.obeliskdepths.dungeon.reward;

import java.util.List;
import net.minecraft.world.item.ItemStack;

@FunctionalInterface
public interface DungeonRewardGenerator {
    List<ItemStack> generate(DungeonRewardContext context);
}
