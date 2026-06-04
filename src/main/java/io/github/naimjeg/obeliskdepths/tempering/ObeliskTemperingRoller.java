package io.github.naimjeg.obeliskdepths.tempering;

import io.github.naimjeg.damagenexus.api.item.DamageNexusItemApi;
import io.github.naimjeg.damagenexus.api.rule.entry.DamageEntryDefinition;
import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.recipe.ObeliskTemperingRecipe;
import io.github.naimjeg.obeliskdepths.registry.ModDataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ObeliskTemperingRoller {

    private static final String TEMPERING_SOURCE =
            ObeliskDepths.MOD_ID + "/tempering";

    private static final String TEMPERING_ENTRY_PREFIX =
            "tempering/";

    private ObeliskTemperingRoller() {
    }

    public static boolean canTemper(
            ItemStack stack,
            boolean replaceExisting
    ) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        if (stack.getMaxStackSize() != 1) {
            return false;
        }

        if (!stack.isDamageableItem()) {
            return false;
        }

        if (replaceExisting) {
            return true;
        }

        return DamageNexusItemApi.getEntries(stack)
                .stream()
                .noneMatch(ObeliskTemperingRoller::isObeliskTemperingEntry);
    }

    public static TemperingAvailability checkAvailability(
            ItemStack weapon,
            Identifier directionId,
            List<RecipeHolder<ObeliskTemperingRecipe>> matchingRecipes
    ) {
        if (weapon == null || weapon.isEmpty()) {
            return TemperingAvailability.denied("missing_weapon");
        }

        if (directionId == null) {
            return TemperingAvailability.denied("missing_direction");
        }

        List<RecipeHolder<ObeliskTemperingRecipe>> contributors =
                matchingRecipes == null
                        ? List.of()
                        : matchingRecipes.stream()
                        .filter(holder ->
                                holder.value().supportsDirection(directionId))
                        .sorted(Comparator.comparing(holder ->
                                holder.id().identifier().toString()))
                        .toList();

        if (contributors.isEmpty()) {
            return TemperingAvailability.denied(
                    "no_direction_contributors"
            );
        }

        boolean replaceExisting = contributors.stream()
                .anyMatch(holder ->
                        holder.value().replaceExisting());

        if (!canTemper(weapon, replaceExisting)) {
            return TemperingAvailability.denied(
                    "weapon_not_temperable"
            );
        }

        ItemStack probe = weapon.copyWithCount(1);

        if (replaceExisting) {
            DamageNexusItemApi.removeEntries(
                    probe,
                    ObeliskTemperingRoller::isObeliskTemperingEntry
            );
        }

        Set<Identifier> selectedIds = new LinkedHashSet<>();

        for (RecipeHolder<ObeliskTemperingRecipe> holder
                : contributors) {
            ObeliskTemperingRecipe recipe = holder.value();

            List<ObeliskTemperingPoolRegistry.WeightedEntry> entries =
                    ObeliskTemperingPoolRegistry.entries(
                            recipe.pool()
                    );

            if (entries.isEmpty()) {
                return TemperingAvailability.denied(
                        "missing_pool:" + recipe.pool()
                );
            }

            /*
             * Validate maxRolls, not minRolls. This guarantees that every roll
             * count computeRolls(...) can produce has enough compatible entries.
             */
            for (int roll = 0;
                 roll < recipe.maxRolls();
                 roll++) {
                DamageEntryDefinition candidate =
                        findFirstCompatibleEntry(
                                probe,
                                entries,
                                selectedIds
                        );

                if (candidate == null) {
                    return TemperingAvailability.denied(
                            "insufficient_compatible_entries:"
                                    + holder.id().identifier()
                    );
                }

                if (!DamageNexusItemApi.addEntry(
                        probe,
                        candidate,
                        TEMPERING_SOURCE
                )) {
                    return TemperingAvailability.denied(
                            "entry_rejected:" + candidate.id()
                    );
                }

                selectedIds.add(candidate.id());
            }
        }

        return TemperingAvailability.allowed();
    }

    private static DamageEntryDefinition findFirstCompatibleEntry(
            ItemStack current,
            List<ObeliskTemperingPoolRegistry.WeightedEntry> entries,
            Set<Identifier> selectedIds
    ) {
        for (ObeliskTemperingPoolRegistry.WeightedEntry weighted
                : entries) {
            if (weighted == null
                    || weighted.entry() == null
                    || weighted.entry().id() == null
                    || weighted.weight() <= 0
                    || selectedIds.contains(weighted.entry().id())) {
                continue;
            }

            ItemStack candidateProbe = current.copy();

            if (DamageNexusItemApi.addEntry(
                    candidateProbe,
                    weighted.entry(),
                    TEMPERING_SOURCE
            )) {
                return weighted.entry();
            }
        }

        return null;
    }

    public record TemperingAvailability(
            boolean available,
            String reason
    ) {
        public static TemperingAvailability allowed() {
            return new TemperingAvailability(true, "");
        }

        public static TemperingAvailability denied(String reason) {
            return new TemperingAvailability(
                    false,
                    reason == null ? "unknown" : reason
            );
        }
    }

    /**
     * Applies every base-matching recipe that supports the selected direction
     * as its own contribution boundary. Roll-count settings stay attached to
     * their source recipe; the aggregated direction pool is used for discovery
     * and preview, not as an execution shortcut.
     */
    public static TemperingResult temper(
            ItemStack weapon,
            TemperingTemplateData templateData,
            Identifier directionId,
            List<RecipeHolder<ObeliskTemperingRecipe>> matchingRecipes,
            RandomSource random
    ) {
        if (weapon == null || weapon.isEmpty()
                || templateData == null
                || directionId == null
                || random == null) {
            return TemperingResult.failure(ItemStack.EMPTY, "invalid_input");
        }

        List<RecipeHolder<ObeliskTemperingRecipe>> contributors =
                matchingRecipes == null
                        ? List.of()
                        : matchingRecipes.stream()
                        .filter(holder -> holder.value()
                                .supportsDirection(directionId))
                        .sorted(Comparator.comparing(holder ->
                                holder.id().identifier().toString()))
                        .toList();

        if (contributors.isEmpty()) {
            return TemperingResult.failure(
                    weapon.copy(),
                    "no_direction_contributors"
            );
        }

        boolean replaceExisting = contributors
                .stream()
                .anyMatch(holder -> holder.value().replaceExisting());

        if (!canTemper(weapon, replaceExisting)) {
            return TemperingResult.failure(
                    weapon.copy(),
                    "weapon_not_temperable"
            );
        }

        ItemStack result = weapon.copyWithCount(1);

        if (replaceExisting) {
            DamageNexusItemApi.removeEntries(
                    result,
                    ObeliskTemperingRoller::isObeliskTemperingEntry
            );
        }

        List<Identifier> appliedEntryIds = new ArrayList<>();
        Set<Identifier> selectedEntryIds = new LinkedHashSet<>();

        for (RecipeHolder<ObeliskTemperingRecipe> holder : contributors) {
            ObeliskTemperingRecipe recipe = holder.value();
            List<ObeliskTemperingPoolRegistry.WeightedEntry> poolEntries =
                    ObeliskTemperingPoolRegistry.entries(recipe.pool());

            if (poolEntries.isEmpty()) {
                return TemperingResult.failure(
                        weapon.copy(),
                        "missing_pool:" + recipe.pool()
                );
            }

            int rolls = recipe.computeRolls(templateData, random);

            for (int i = 0; i < rolls; i++) {
                DamageEntryDefinition selected = selectDistinctCompatibleEntry(
                        result,
                        poolEntries,
                        selectedEntryIds,
                        random
                );

                if (selected == null) {
                    return TemperingResult.failure(
                            weapon.copy(),
                            "insufficient_distinct_entries:"
                                    + holder.id().identifier()
                    );
                }

                boolean added = DamageNexusItemApi.addEntry(
                        result,
                        selected,
                        TEMPERING_SOURCE
                );

                if (!added) {
                    return TemperingResult.failure(
                            weapon.copy(),
                            "entry_rejected:" + selected.id()
                    );
                }

                selectedEntryIds.add(selected.id());
                appliedEntryIds.add(selected.id());
            }
        }

        return TemperingResult.success(
                result,
                appliedEntryIds
        );
    }


    private static DamageEntryDefinition selectDistinctCompatibleEntry(
            ItemStack result,
            List<ObeliskTemperingPoolRegistry.WeightedEntry> poolEntries,
            Set<Identifier> selectedEntryIds,
            RandomSource random
    ) {
        List<ObeliskTemperingPoolRegistry.WeightedEntry> candidates =
                new ArrayList<>();

        for (ObeliskTemperingPoolRegistry.WeightedEntry entry : poolEntries) {
            if (entry == null || entry.entry() == null
                    || entry.entry().id() == null
                    || entry.weight() <= 0
                    || selectedEntryIds.contains(entry.entry().id())) {
                continue;
            }

            candidates.add(entry);
        }

        while (!candidates.isEmpty()) {
            int totalWeight = 0;

            for (ObeliskTemperingPoolRegistry.WeightedEntry candidate
                    : candidates) {
                totalWeight += candidate.weight();
            }

            if (totalWeight <= 0) {
                return null;
            }

            int selectedWeight = random.nextInt(totalWeight);

            for (int i = 0; i < candidates.size(); i++) {
                ObeliskTemperingPoolRegistry.WeightedEntry candidate =
                        candidates.get(i);
                selectedWeight -= candidate.weight();

                if (selectedWeight >= 0) {
                    continue;
                }

                ItemStack probe = result.copy();

                if (DamageNexusItemApi.addEntry(
                        probe,
                        candidate.entry(),
                        TEMPERING_SOURCE
                )) {
                    return candidate.entry();
                }

                candidates.remove(i);
                break;
            }
        }

        return null;
    }

    private static boolean isObeliskTemperingEntry(
            DamageEntryDefinition entry
    ) {
        if (entry == null || entry.id() == null) {
            return false;
        }

        Identifier id = entry.id();

        return ObeliskDepths.MOD_ID.equals(id.getNamespace())
                && id.getPath().startsWith(TEMPERING_ENTRY_PREFIX);
    }

    public record TemperingResult(
            boolean success,
            ItemStack result,
            List<Identifier> appliedEntryIds,
            String failureReason
    ) {
        private static TemperingResult success(
                ItemStack result,
                List<Identifier> appliedEntryIds
        ) {
            return new TemperingResult(
                    true,
                    result,
                    List.copyOf(appliedEntryIds),
                    ""
            );
        }

        private static TemperingResult failure(
                ItemStack original,
                String failureReason
        ) {
            return new TemperingResult(
                    false,
                    original == null ? ItemStack.EMPTY : original.copy(),
                    List.of(),
                    failureReason
            );
        }
    }
}
