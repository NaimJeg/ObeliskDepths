package io.github.naimjeg.obeliskdepths.tempering;

import io.github.naimjeg.damagenexus.api.item.DamageNexusItemApi;
import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.equipment.ObeliskEquipmentContent;
import io.github.naimjeg.obeliskdepths.equipment.ObeliskEquipmentRules;
import io.github.naimjeg.obeliskdepths.equipment.ObeliskEquipmentSlot;
import io.github.naimjeg.obeliskdepths.equipment.ObeliskEquipmentTemplateCatalog;
import io.github.naimjeg.obeliskdepths.recipe.ObeliskTemperingRecipe;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Server-side, reference-only tempering selection and application. */
public final class ObeliskTemperingRoller {
    private ObeliskTemperingRoller() {
    }

    public static boolean canTemper(ItemStack stack, boolean replaceExisting) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        ObeliskEquipmentSlot slot = equipmentSlot(stack);
        if (slot == null) {
            return false;
        }

        if (replaceExisting) {
            return true;
        }

        return DamageNexusItemApi.getEntryTemplateReferences(stack).stream()
                .map(reference -> ObeliskEquipmentTemplateCatalog.find(reference.id()))
                .flatMap(java.util.Optional::stream)
                .noneMatch(content -> content.temperingEligible() && content.supports(slot));
    }

    public static TemperingAvailability checkAvailability(
            ItemStack equipment,
            Identifier directionId,
            List<RecipeHolder<ObeliskTemperingRecipe>> matchingRecipes
    ) {
        if (equipment == null || equipment.isEmpty()) {
            return TemperingAvailability.denied("missing_equipment");
        }
        if (equipmentSlot(equipment) == null) {
            return TemperingAvailability.denied("equipment_not_tagged");
        }
        if (directionId == null) {
            return TemperingAvailability.denied("missing_direction");
        }

        List<RecipeHolder<ObeliskTemperingRecipe>> contributors = contributors(directionId, matchingRecipes);
        if (contributors.isEmpty()) {
            return TemperingAvailability.denied("no_direction_contributors");
        }

        boolean replaceExisting = contributors.stream().anyMatch(holder -> holder.value().replaceExisting());
        if (!canTemper(equipment, replaceExisting)) {
            return TemperingAvailability.denied("equipment_not_temperable");
        }

        ItemStack probe = equipment.copyWithCount(1);
        Set<Identifier> selected = new LinkedHashSet<>();
        Set<Identifier> selectedGroups = new LinkedHashSet<>();
        Map<Identifier, List<ObeliskTemperingPoolRegistry.WeightedEntry>> pools =
                ObeliskTemperingPoolRegistry.snapshot();
        for (RecipeHolder<ObeliskTemperingRecipe> holder : contributors) {
            List<ObeliskTemperingPoolRegistry.WeightedEntry> pool =
                    pools.getOrDefault(holder.value().pool(), List.of());
            if (pool.isEmpty()) {
                return TemperingAvailability.denied("missing_pool:" + holder.value().pool());
            }
            for (int roll = 0; roll < holder.value().maxRolls(); roll++) {
                ObeliskEquipmentContent candidate = firstCompatible(
                        probe,
                        pool,
                        selected,
                        selectedGroups
                );
                if (candidate == null) {
                    return TemperingAvailability.denied("insufficient_compatible_entries:" + holder.id().identifier());
                }
                selected.add(candidate.templateId());
                selectedGroups.add(candidate.stackingGroup());
                if (!addReference(probe, candidate)) {
                    return TemperingAvailability.denied(
                            "reference_write_rejected:" + candidate.templateId()
                    );
                }
            }
        }
        return TemperingAvailability.allowed();
    }

    public static TemperingResult temper(
            ItemStack equipment,
            TemperingTemplateData templateData,
            Identifier directionId,
            List<RecipeHolder<ObeliskTemperingRecipe>> matchingRecipes,
            RandomSource random
    ) {
        if (equipment == null || equipment.isEmpty() || templateData == null || directionId == null || random == null) {
            return TemperingResult.failure(ItemStack.EMPTY, "invalid_input");
        }

        List<RecipeHolder<ObeliskTemperingRecipe>> contributors = contributors(directionId, matchingRecipes);
        if (contributors.isEmpty()) {
            return TemperingResult.failure(equipment, "no_direction_contributors");
        }
        boolean replaceExisting = contributors.stream().anyMatch(holder -> holder.value().replaceExisting());
        if (!canTemper(equipment, replaceExisting)) {
            return TemperingResult.failure(equipment, "equipment_not_temperable");
        }

        ItemStack result = equipment.copyWithCount(1);
        if (replaceExisting
                && !ObeliskEquipmentRules.removeManagedReferences(result)) {
            return TemperingResult.failure(equipment, "reference_write_rejected");
        }

        List<Identifier> applied = new ArrayList<>();
        Set<Identifier> selectedIds = new HashSet<>();
        Set<Identifier> selectedGroups = new HashSet<>();
        Map<Identifier, List<ObeliskTemperingPoolRegistry.WeightedEntry>> pools =
                ObeliskTemperingPoolRegistry.snapshot();

        for (RecipeHolder<ObeliskTemperingRecipe> holder : contributors) {
            ObeliskTemperingRecipe recipe = holder.value();
            List<ObeliskTemperingPoolRegistry.WeightedEntry> pool =
                    pools.getOrDefault(recipe.pool(), List.of());
            if (pool.isEmpty()) {
                return TemperingResult.failure(equipment, "missing_pool:" + recipe.pool());
            }
            int rolls = recipe.computeRolls(templateData, random);
            for (int i = 0; i < rolls; i++) {
                ObeliskEquipmentContent selected = choose(pool, result, selectedIds, selectedGroups, random);
                if (selected == null) {
                    return TemperingResult.failure(equipment, "insufficient_distinct_entries:" + holder.id().identifier());
                }
                if (!replaceStackingGroup(result, selected)) {
                    return TemperingResult.failure(equipment, "reference_write_rejected:" + selected.templateId());
                }
                selectedIds.add(selected.templateId());
                selectedGroups.add(selected.stackingGroup());
                applied.add(selected.templateId());
            }
        }
        return TemperingResult.success(result, applied);
    }

    private static List<RecipeHolder<ObeliskTemperingRecipe>> contributors(
            Identifier directionId,
            List<RecipeHolder<ObeliskTemperingRecipe>> matchingRecipes
    ) {
        if (matchingRecipes == null) {
            return List.of();
        }
        return matchingRecipes.stream()
                .filter(holder -> holder != null && holder.value().supportsDirection(directionId))
                .sorted(Comparator.comparing(holder -> holder.id().identifier().toString()))
                .toList();
    }

    private static ObeliskEquipmentContent firstCompatible(
            ItemStack stack,
            List<ObeliskTemperingPoolRegistry.WeightedEntry> entries,
            Set<Identifier> selected,
            Set<Identifier> selectedGroups
    ) {
        ObeliskEquipmentSlot slot = equipmentSlot(stack);
        if (slot == null) {
            return null;
        }
        for (ObeliskTemperingPoolRegistry.WeightedEntry weighted : entries) {
            if (weighted == null || weighted.weight() <= 0 || weighted.templateId() == null || selected.contains(weighted.templateId())) {
                continue;
            }
            ObeliskEquipmentContent content = ObeliskEquipmentTemplateCatalog.find(weighted.templateId()).orElse(null);
            if (content != null
                    && content.temperingEligible()
                    && content.supports(slot)
                    && !selectedGroups.contains(content.stackingGroup())) {
                return content;
            }
        }
        return null;
    }

    private static ObeliskEquipmentContent choose(
            List<ObeliskTemperingPoolRegistry.WeightedEntry> entries,
            ItemStack result,
            Set<Identifier> selectedIds,
            Set<Identifier> selectedGroups,
            RandomSource random
    ) {
        ObeliskEquipmentSlot slot = equipmentSlot(result);
        if (slot == null) {
            return null;
        }
        return chooseForSlot(
                entries, slot, selectedIds, selectedGroups, random);
    }

    static ObeliskEquipmentContent chooseForSlot(
            List<ObeliskTemperingPoolRegistry.WeightedEntry> entries,
            ObeliskEquipmentSlot slot,
            Set<Identifier> selectedIds,
            Set<Identifier> selectedGroups,
            RandomSource random
    ) {
        if (entries == null || slot == null || selectedIds == null
                || selectedGroups == null || random == null) {
            return null;
        }
        List<ObeliskTemperingPoolRegistry.WeightedEntry> candidates = new ArrayList<>();
        long total = 0L;
        for (ObeliskTemperingPoolRegistry.WeightedEntry weighted : entries) {
            if (weighted == null || weighted.weight() <= 0 || weighted.templateId() == null || selectedIds.contains(weighted.templateId())) {
                continue;
            }
            ObeliskEquipmentContent content = ObeliskEquipmentTemplateCatalog.find(weighted.templateId()).orElse(null);
            if (content == null || !content.temperingEligible() || !content.supports(slot) || selectedGroups.contains(content.stackingGroup())) {
                continue;
            }
            candidates.add(weighted);
            total = Math.addExact(total, weighted.weight());
        }
        if (candidates.isEmpty() || total <= 0L) {
            return null;
        }
        long draw = nextLong(random, total);
        for (ObeliskTemperingPoolRegistry.WeightedEntry weighted : candidates) {
            draw -= weighted.weight();
            if (draw < 0) {
                return ObeliskEquipmentTemplateCatalog.find(weighted.templateId()).orElse(null);
            }
        }
        return null;
    }

    private static long nextLong(RandomSource random, long bound) {
        long bits;
        long value;
        do {
            bits = random.nextLong() >>> 1;
            value = bits % bound;
        } while (bits - value + (bound - 1L) < 0L);
        return value;
    }

    private static boolean addReference(ItemStack stack, ObeliskEquipmentContent content) {
        return ObeliskEquipmentRules.applyTemplateReference(stack, content);
    }

    private static boolean replaceStackingGroup(ItemStack stack, ObeliskEquipmentContent content) {
        return ObeliskEquipmentRules.applyTemplateReference(stack, content);
    }

    public static ObeliskEquipmentSlot equipmentSlot(ItemStack stack) {
        return ObeliskEquipmentRules.slot(stack).orElse(null);
    }

    public record TemperingAvailability(boolean available, String reason) {
        public static TemperingAvailability allowed() {
            return new TemperingAvailability(true, "");
        }

        public static TemperingAvailability denied(String reason) {
            return new TemperingAvailability(false, reason == null ? "unknown" : reason);
        }
    }

    public record TemperingResult(
            boolean success,
            ItemStack result,
            List<Identifier> appliedEntryIds,
            String failureReason
    ) {
        private static TemperingResult success(ItemStack result, List<Identifier> applied) {
            return new TemperingResult(true, result, List.copyOf(applied), "");
        }

        private static TemperingResult failure(ItemStack original, String reason) {
            return new TemperingResult(false, original == null ? ItemStack.EMPTY : original.copy(), List.of(), reason);
        }
    }
}
