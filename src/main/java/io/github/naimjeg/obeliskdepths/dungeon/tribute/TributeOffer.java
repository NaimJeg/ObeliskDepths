package io.github.naimjeg.obeliskdepths.dungeon.tribute;

import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public record TributeOffer(
        UUID offeringPlayer,
        ItemStack stack
) {
}