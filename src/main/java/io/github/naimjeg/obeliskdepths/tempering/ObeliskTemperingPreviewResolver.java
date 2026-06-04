package io.github.naimjeg.obeliskdepths.tempering;

import io.github.naimjeg.damagenexus.api.display.DisplayText;
import io.github.naimjeg.damagenexus.api.rule.entry.DamageEntryDefinition;
import io.github.naimjeg.damagenexus.api.rule.entry.DamageEntryDisplay;
import io.github.naimjeg.obeliskdepths.recipe.ObeliskTemperingRecipe;
import io.github.naimjeg.obeliskdepths.recipe.ObeliskTemperingRecipeInput;
import io.github.naimjeg.obeliskdepths.recipe.ObeliskTemperingRecipeResolver;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Map;

public final class ObeliskTemperingPreviewResolver {

    private ObeliskTemperingPreviewResolver() {
    }

    public static List<TemperingAffixPreview> resolveServerPreview(
            ServerLevel level,
            ObeliskTemperingRecipeInput input,
            Identifier directionId
    ) {
        List<RecipeHolder<ObeliskTemperingRecipe>> matchingRecipes =
                ObeliskTemperingRecipeResolver.findBaseMatches(
                        level.recipeAccess(),
                        input,
                        level
                );

        return resolveAggregatedPreview(matchingRecipes, directionId);
    }

    public static List<TemperingAffixPreview> resolveClientPreview(
            Level level,
            ObeliskTemperingRecipeInput input,
            Identifier directionId
    ) {
        if (level == null
                || !(level.recipeAccess() instanceof RecipeManager recipeManager)) {
            return List.of();
        }

        List<RecipeHolder<ObeliskTemperingRecipe>> matchingRecipes =
                ObeliskTemperingRecipeResolver.findBaseMatches(
                        recipeManager,
                        input,
                        level
                );

        return resolveAggregatedPreview(matchingRecipes, directionId);
    }

    public static List<TemperingAffixPreview> resolvePoolPreview(
            Identifier poolId
    ) {
        return ObeliskTemperingPoolRegistry.entries(poolId)
                .stream()
                .map(ObeliskTemperingPreviewResolver::toPreview)
                .toList();
    }

    private static List<TemperingAffixPreview> resolveAggregatedPreview(
            List<RecipeHolder<ObeliskTemperingRecipe>> matchingRecipes,
            Identifier directionId
    ) {
        if (directionId == null) {
            return List.of();
        }

        Map<Identifier, AggregatedTemperingDirection> directions =
                ObeliskTemperingDirectionPoolResolver.resolve(matchingRecipes);
        AggregatedTemperingDirection direction = directions.get(directionId);

        if (direction == null) {
            return List.of();
        }

        return resolveDirectionPreview(direction);
    }

    public static List<TemperingAffixPreview> resolveDirectionPreview(
            AggregatedTemperingDirection direction
    ) {
        if (direction == null) {
            return List.of();
        }

        return direction.entries()
                .stream()
                .map(entry -> toPreview(new ObeliskTemperingPoolRegistry
                        .WeightedEntry(entry.entry(), entry.weight())))
                .toList();
    }

    private static TemperingAffixPreview toPreview(
            ObeliskTemperingPoolRegistry.WeightedEntry weightedEntry
    ) {
        DamageEntryDefinition entry = weightedEntry.entry();
        DamageEntryDisplay display = entry.display();
        Component fallbackName = Component.literal(entry.id().toString());
        Component displayName = resolveDisplayText(display.name(), fallbackName);
        Component description = display.flavorText()
                .map(text -> resolveDisplayText(text, Component.empty()))
                .orElseGet(() -> display.tooltip()
                        .stream()
                        .filter(text -> !text.isBlank())
                        .findFirst()
                        .map(text -> resolveDisplayText(text, Component.empty()))
                        .orElse(Component.empty()));

        return new TemperingAffixPreview(
                entry.id(),
                displayName,
                description,
                weightedEntry.weight()
        );
    }

    private static Component resolveDisplayText(
            DisplayText text,
            Component fallback
    ) {
        if (text == null || text.isBlank()) {
            return fallback;
        }

        if (text.translate().isPresent()) {
            String key = text.translate().get();
            Object[] args = text.args()
                    .stream()
                    .map(Component::literal)
                    .toArray(Object[]::new);

            return text.fallback()
                    .map(value -> Component.translatableWithFallback(
                            key,
                            value,
                            args
                    ))
                    .orElseGet(() -> Component.translatable(key, args));
        }

        return Component.literal(
                text.text()
                        .or(text::fallback)
                        .orElse("")
        );
    }
}
