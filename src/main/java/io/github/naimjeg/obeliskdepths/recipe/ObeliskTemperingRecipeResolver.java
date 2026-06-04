package io.github.naimjeg.obeliskdepths.recipe;

import io.github.naimjeg.obeliskdepths.registry.ModRecipeTypes;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

import java.util.Comparator;
import java.util.List;

/**
 * Finds all recipes whose non-direction inputs match. Direction selection is
 * resolved later by aggregating all matching recipes into virtual direction
 * pools, so gameplay must not use getRecipeFor to choose one recipe.
 */
public final class ObeliskTemperingRecipeResolver {
    private ObeliskTemperingRecipeResolver() {
    }

    public static List<RecipeHolder<ObeliskTemperingRecipe>> findBaseMatches(
            RecipeManager recipeManager,
            ObeliskTemperingRecipeInput input,
            Level level
    ) {
        if (recipeManager == null || input == null || level == null) {
            return List.of();
        }

        return recipeManager
                .getRecipes()
                .stream()
                .filter(holder -> holder.value().getType()
                        == ModRecipeTypes.OBELISK_TEMPERING.get())
                .map(holder -> (RecipeHolder<ObeliskTemperingRecipe>) holder)
                .filter(holder -> holder.value().matchesBase(input, level))
                .sorted(Comparator.comparing(holder ->
                        holder.id().identifier().toString()))
                .toList();
    }
}
