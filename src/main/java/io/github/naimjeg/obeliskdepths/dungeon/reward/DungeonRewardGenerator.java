package io.github.naimjeg.obeliskdepths.dungeon.reward;

import net.minecraft.world.item.ItemStack;

import java.util.List;

@FunctionalInterface
public interface DungeonRewardGenerator {
    List<ItemStack> generate(DungeonRewardContext context);
}
