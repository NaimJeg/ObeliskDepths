package io.github.naimjeg.obeliskdepths.tempering;

import io.github.naimjeg.obeliskdepths.recipe.ObeliskTemperingRecipe;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

public record ResolvedTemperingState(
        List<RecipeHolder<ObeliskTemperingRecipe>> matchingRecipes,
        Map<Identifier, AggregatedTemperingDirection> directions,
        List<Identifier> orderedDirectionIds,
        Optional<Identifier> selectedDirectionId,
        List<TemperingAffixPreview> selectedPreviews,
        ItemStack previewResult,
        TemperingFailure failure,
        boolean hasRecipeError
) {
    public static final ResolvedTemperingState EMPTY = new ResolvedTemperingState(
            List.of(),
            Map.of(),
            List.of(),
            Optional.empty(),
            List.of(),
            ItemStack.EMPTY,
            TemperingFailure.NONE,
            false
    );

    public ResolvedTemperingState {
        matchingRecipes = matchingRecipes == null ? List.of() : List.copyOf(matchingRecipes);
        directions = directions == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(directions));
        orderedDirectionIds = orderedDirectionIds == null ? List.of() : List.copyOf(orderedDirectionIds);
        selectedDirectionId = selectedDirectionId == null ? Optional.empty() : selectedDirectionId;
        selectedPreviews = selectedPreviews == null ? List.of() : List.copyOf(selectedPreviews);
        previewResult = previewResult == null ? ItemStack.EMPTY : previewResult.copy();
        failure = failure == null ? TemperingFailure.NONE : failure;
    }

    public boolean actionable() {
        return !this.previewResult.isEmpty();
    }
}
