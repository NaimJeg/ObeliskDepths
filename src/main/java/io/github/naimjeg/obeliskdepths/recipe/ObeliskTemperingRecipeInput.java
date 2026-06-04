package io.github.naimjeg.obeliskdepths.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

public record ObeliskTemperingRecipeInput(
        ItemStack weapon,
        ItemStack template,
        ItemStack ingredient
) implements RecipeInput {

    @Override
    public ItemStack getItem(int slot) {
        return switch (slot) {
            case 0 -> this.weapon;
            case 1 -> this.template;
            case 2 -> this.ingredient;
            default -> ItemStack.EMPTY;
        };
    }

    @Override
    public int size() {
        return 3;
    }
}
