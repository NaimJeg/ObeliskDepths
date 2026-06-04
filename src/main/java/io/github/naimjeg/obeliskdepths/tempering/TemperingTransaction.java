package io.github.naimjeg.obeliskdepths.tempering;

import io.github.naimjeg.obeliskdepths.recipe.ObeliskTemperingRecipeInput;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import org.jspecify.annotations.Nullable;

public final class TemperingTransaction {
    private TemperingTransaction() {
    }

    public static TemperingResult execute(
            ServerLevel level,
            RecipeManager recipeManager,
            ObeliskTemperingRecipeInput input,
            @Nullable Identifier selectedDirectionId
    ) {
        if (level == null || recipeManager == null || input == null) {
            return TemperingResult.failure(
                    "invalid_context",
                    ItemStack.EMPTY,
                    ItemStack.EMPTY,
                    ItemStack.EMPTY
            );
        }

        if (!TemperingTemplateItems.isTemperingTemplate(input.template())) {
            return failure("invalid_template", input);
        }

        ResolvedTemperingState resolved =
                TemperingResolver.resolve(
                        recipeManager,
                        level,
                        input,
                        selectedDirectionId
                );

        if (selectedDirectionId == null
                || resolved.selectedDirectionId().filter(selectedDirectionId::equals).isEmpty()
                || resolved.failure().failed()) {
            return failure(
                    resolved.failure().failed()
                            ? resolved.failure().reason()
                            : "invalid_direction",
                    input
            );
        }

        TemperingTemplateData templateData =
                TemperingTemplateItems.getOrDefault(input.template());
        ObeliskTemperingRoller.TemperingResult rolled =
                ObeliskTemperingRoller.temper(
                        input.weapon(),
                        templateData,
                        selectedDirectionId,
                        resolved.matchingRecipes(),
                        level.getRandom()
                );

        if (!rolled.success()) {
            return failure(rolled.failureReason(), input);
        }

        return TemperingResult.success(
                rolled.result(),
                removeOne(input.weapon()),
                removeOne(input.template()),
                input.ingredient().isEmpty()
                        ? ItemStack.EMPTY
                        : removeOne(input.ingredient())
        );
    }

    private static TemperingResult failure(
            String reason,
            ObeliskTemperingRecipeInput input
    ) {
        return TemperingResult.failure(
                reason,
                input.weapon(),
                input.template(),
                input.ingredient()
        );
    }

    private static ItemStack removeOne(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack remaining = stack.copy();
        remaining.shrink(1);
        return remaining.isEmpty() ? ItemStack.EMPTY : remaining;
    }
}
