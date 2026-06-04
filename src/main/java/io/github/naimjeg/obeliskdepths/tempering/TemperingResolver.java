package io.github.naimjeg.obeliskdepths.tempering;

import io.github.naimjeg.obeliskdepths.recipe.ObeliskTemperingRecipe;
import io.github.naimjeg.obeliskdepths.recipe.ObeliskTemperingRecipeInput;
import io.github.naimjeg.obeliskdepths.recipe.ObeliskTemperingRecipeResolver;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public final class TemperingResolver {
    private TemperingResolver() {
    }

    public static ResolvedTemperingState resolve(
            RecipeManager recipeManager,
            Level level,
            ObeliskTemperingRecipeInput input,
            @Nullable Identifier requestedDirectionId
    ) {
        if (recipeManager == null || level == null || input == null) {
            return ResolvedTemperingState.EMPTY;
        }

        List<RecipeHolder<ObeliskTemperingRecipe>> matchingRecipes =
                ObeliskTemperingRecipeResolver.findBaseMatches(
                        recipeManager,
                        input,
                        level
                );
        Map<Identifier, AggregatedTemperingDirection> directions =
                ObeliskTemperingDirectionPoolResolver.resolve(matchingRecipes);
        List<Identifier> orderedDirectionIds = List.copyOf(directions.keySet());
        Optional<Identifier> selectedDirectionId = selectDirection(
                directions,
                orderedDirectionIds,
                requestedDirectionId
        );
        List<TemperingAffixPreview> selectedPreviews = selectedDirectionId
                .map(directions::get)
                .map(ObeliskTemperingPreviewResolver::resolveDirectionPreview)
                .orElse(List.of());
        TemperingFailure failure = determineFailure(
                input,
                selectedDirectionId.orElse(null),
                matchingRecipes
        );
        ItemStack previewResult = failure.failed()
                ? ItemStack.EMPTY
                : input.weapon().copyWithCount(1);
        boolean hasRecipeError = hasInputPair(input) && previewResult.isEmpty();

        return new ResolvedTemperingState(
                matchingRecipes,
                directions,
                orderedDirectionIds,
                selectedDirectionId,
                selectedPreviews,
                previewResult,
                failure,
                hasRecipeError
        );
    }

    public static boolean canAcceptWeapon(ItemStack stack) {
        return !stack.isEmpty() && ObeliskTemperingRoller.canTemper(stack, true);
    }

    private static Optional<Identifier> selectDirection(
            Map<Identifier, AggregatedTemperingDirection> directions,
            List<Identifier> orderedDirectionIds,
            @Nullable Identifier requestedDirectionId
    ) {
        if (requestedDirectionId != null && directions.containsKey(requestedDirectionId)) {
            return Optional.of(requestedDirectionId);
        }

        return orderedDirectionIds.stream().findFirst();
    }

    private static TemperingFailure determineFailure(
            ObeliskTemperingRecipeInput input,
            @Nullable Identifier selectedDirectionId,
            List<RecipeHolder<ObeliskTemperingRecipe>> matchingRecipes
    ) {
        if (!hasInputPair(input)) {
            return TemperingFailure.NONE;
        }

        if (!TemperingTemplateItems.isTemperingTemplate(input.template())) {
            return new TemperingFailure("invalid_template");
        }

        if (selectedDirectionId == null) {
            return new TemperingFailure("missing_direction");
        }

        ObeliskTemperingRoller.TemperingAvailability availability =
                ObeliskTemperingRoller.checkAvailability(
                        input.weapon(),
                        selectedDirectionId,
                        matchingRecipes
                );

        return availability.available()
                ? TemperingFailure.NONE
                : new TemperingFailure(availability.reason());
    }

    private static boolean hasInputPair(ObeliskTemperingRecipeInput input) {
        return !input.weapon().isEmpty() && !input.template().isEmpty();
    }
}
