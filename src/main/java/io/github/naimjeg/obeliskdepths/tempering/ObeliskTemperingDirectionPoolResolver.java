package io.github.naimjeg.obeliskdepths.tempering;

import io.github.naimjeg.damagenexus.api.rule.entry.DamageEntryDefinition;
import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.recipe.ObeliskTemperingRecipe;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds virtual direction pools by reversing recipe declarations:
 * base-matching recipes contribute their physical pools to every direction id
 * they support, then duplicate affix entries are merged by summed weight.
 */
public final class ObeliskTemperingDirectionPoolResolver {
    private ObeliskTemperingDirectionPoolResolver() {
    }

    public static Map<Identifier, AggregatedTemperingDirection> resolve(
            List<RecipeHolder<ObeliskTemperingRecipe>> matchingRecipes
    ) {
        if (matchingRecipes == null || matchingRecipes.isEmpty()) {
            return Map.of();
        }

        return resolveContributions(matchingRecipes
                .stream()
                .map(holder -> new RecipeContribution(
                        recipeId(holder),
                        holder.value().pool(),
                        holder.value().directions()
                ))
                .toList());
    }

    public static Map<Identifier, AggregatedTemperingDirection> resolveContributions(
            List<RecipeContribution> contributions
    ) {
        if (contributions == null || contributions.isEmpty()) {
            return Map.of();
        }

        Map<Identifier, DirectionAccumulator> accumulators =
                new LinkedHashMap<>();

        List<RecipeContribution> sorted =
                contributions.stream()
                        .sorted(Comparator.comparing(contribution ->
                                contribution.recipeId().toString()))
                        .toList();

        for (RecipeContribution contribution : sorted) {
            List<ObeliskTemperingPoolRegistry.WeightedEntry> poolEntries =
                    ObeliskTemperingPoolRegistry.entries(contribution.poolId());

            if (poolEntries.isEmpty()) {
                ObeliskDepths.LOGGER.warn(
                        "Skipping missing or empty Obelisk tempering pool {} for recipe {}",
                        contribution.poolId(),
                        contribution.recipeId()
                );
                continue;
            }

            for (Identifier directionId : contribution.directions()) {
                ObeliskTemperingDirectionDefinition definition =
                        ObeliskTemperingDirectionRegistry
                                .definition(directionId)
                                .orElse(null);

                if (definition == null) {
                    ObeliskDepths.LOGGER.warn(
                            "Skipping unknown Obelisk tempering direction {} in recipe {}",
                            directionId,
                            contribution.recipeId()
                    );
                    continue;
                }

                DirectionAccumulator accumulator =
                        accumulators.computeIfAbsent(
                                directionId,
                                id -> new DirectionAccumulator(id, definition)
                        );

                for (ObeliskTemperingPoolRegistry.WeightedEntry entry
                        : poolEntries) {
                    accumulator.add(
                            entry
                    );
                }
            }
        }

        Map<Identifier, AggregatedTemperingDirection> resolved =
                new LinkedHashMap<>();

        for (Identifier directionId
                : ObeliskTemperingDirectionRegistry.orderedDirectionIds()) {
            DirectionAccumulator accumulator = accumulators.get(directionId);

            if (accumulator == null) {
                continue;
            }

            AggregatedTemperingDirection direction =
                    accumulator.toDirection();

            if (!direction.entries().isEmpty()) {
                resolved.put(directionId, direction);
            }
        }

        accumulators.entrySet()
                .stream()
                .filter(entry -> !resolved.containsKey(entry.getKey()))
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .forEach(entry -> {
                    AggregatedTemperingDirection direction =
                            entry.getValue().toDirection();

                    if (!direction.entries().isEmpty()) {
                        resolved.put(entry.getKey(), direction);
                    }
                });

        return Collections.unmodifiableMap(resolved);
    }

    private static Identifier recipeId(
            RecipeHolder<ObeliskTemperingRecipe> holder
    ) {
        return holder.id().identifier();
    }

    public record RecipeContribution(
            Identifier recipeId,
            Identifier poolId,
            List<Identifier> directions
    ) {
        public RecipeContribution {
            if (recipeId == null) {
                throw new IllegalArgumentException("recipeId must not be null");
            }

            if (poolId == null) {
                throw new IllegalArgumentException("poolId must not be null");
            }

            directions = directions == null ? List.of() : List.copyOf(
                    new LinkedHashSet<>(directions)
            );
        }
    }

    private static final class DirectionAccumulator {
        private final Identifier directionId;
        private final ObeliskTemperingDirectionDefinition definition;
        private final Map<String, EntryAccumulator> entries = new TreeMap<>();

        private DirectionAccumulator(
                Identifier directionId,
                ObeliskTemperingDirectionDefinition definition
        ) {
            this.directionId = directionId;
            this.definition = definition;
        }

        private void add(
                ObeliskTemperingPoolRegistry.WeightedEntry weightedEntry
        ) {
            DamageEntryDefinition entry = weightedEntry.entry();

            if (entry == null || entry.id() == null
                    || weightedEntry.weight() <= 0) {
                return;
            }

            EntryAccumulator accumulator =
                    this.entries.computeIfAbsent(
                            entry.id().toString(),
                            id -> new EntryAccumulator(entry)
                    );
            accumulator.weight += weightedEntry.weight();
        }

        private AggregatedTemperingDirection toDirection() {
            List<AggregatedTemperingEntry> resolved = new ArrayList<>();

            for (EntryAccumulator accumulator : this.entries.values()) {
                int weight;

                if (accumulator.weight > Integer.MAX_VALUE) {
                    ObeliskDepths.LOGGER.warn(
                            "Clamping aggregated Obelisk tempering weight for entry {} in direction {} from {} to {}",
                            accumulator.entry.id(),
                            this.directionId,
                            accumulator.weight,
                            Integer.MAX_VALUE
                    );
                    weight = Integer.MAX_VALUE;
                } else {
                    weight = (int) accumulator.weight;
                }

                if (weight <= 0) {
                    continue;
                }

                resolved.add(new AggregatedTemperingEntry(
                        accumulator.entry,
                        weight
                ));
            }

            return new AggregatedTemperingDirection(
                    this.directionId,
                    this.definition,
                    resolved
            );
        }
    }

    private static final class EntryAccumulator {
        private final DamageEntryDefinition entry;
        private long weight;

        private EntryAccumulator(DamageEntryDefinition entry) {
            this.entry = entry;
        }
    }
}
