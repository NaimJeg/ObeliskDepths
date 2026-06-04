package io.github.naimjeg.obeliskdepths.tempering;

import net.minecraft.world.item.ItemStack;

public record TemperingResult(
        boolean success,
        ItemStack craftedStack,
        ItemStack remainingWeapon,
        ItemStack remainingTemplate,
        ItemStack remainingIngredient,
        TemperingFailure failure
) {
    public TemperingResult {
        craftedStack = copyOrEmpty(craftedStack);
        remainingWeapon = copyOrEmpty(remainingWeapon);
        remainingTemplate = copyOrEmpty(remainingTemplate);
        remainingIngredient = copyOrEmpty(remainingIngredient);
        failure = failure == null ? TemperingFailure.NONE : failure;
    }

    public static TemperingResult success(
            ItemStack craftedStack,
            ItemStack remainingWeapon,
            ItemStack remainingTemplate,
            ItemStack remainingIngredient
    ) {
        return new TemperingResult(
                true,
                craftedStack,
                remainingWeapon,
                remainingTemplate,
                remainingIngredient,
                TemperingFailure.NONE
        );
    }

    public static TemperingResult failure(
            String reason,
            ItemStack weapon,
            ItemStack template,
            ItemStack ingredient
    ) {
        return new TemperingResult(
                false,
                ItemStack.EMPTY,
                weapon,
                template,
                ingredient,
                new TemperingFailure(reason)
        );
    }

    private static ItemStack copyOrEmpty(ItemStack stack) {
        return stack == null ? ItemStack.EMPTY : stack.copy();
    }
}
