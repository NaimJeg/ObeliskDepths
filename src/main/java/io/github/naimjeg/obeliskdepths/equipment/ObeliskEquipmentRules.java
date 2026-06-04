package io.github.naimjeg.obeliskdepths.equipment;

import io.github.naimjeg.damagenexus.api.item.DamageNexusItemApi;
import io.github.naimjeg.damagenexus.api.item.template.DamageEntryTemplateReference;
import io.github.naimjeg.damagenexus.api.item.template.DamageItemTemplateReferences;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Shared explicit-tag and actual-equipment-slot admission rules. */
public final class ObeliskEquipmentRules {
    private ObeliskEquipmentRules() {
    }

    public static boolean accepts(ItemStack stack) {
        return slot(stack).isPresent();
    }

    public static Optional<ObeliskEquipmentSlot> slot(ItemStack stack) {
        if (stack == null || stack.isEmpty()
                || ObeliskUniqueEquipmentStacks.isUnique(stack)
                || !stack.is(ObeliskEquipmentTags.ALL)) {
            return Optional.empty();
        }

        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (stack.is(ObeliskEquipmentTags.WEAPONS)
                && (equippable == null
                || equippable.slot() == EquipmentSlot.MAINHAND
                || equippable.slot() == EquipmentSlot.OFFHAND)) {
            return Optional.of(ObeliskEquipmentSlot.WEAPON);
        }
        if (matchesArmor(stack, equippable, ObeliskEquipmentTags.ARMOR_HEAD, EquipmentSlot.HEAD)) {
            return Optional.of(ObeliskEquipmentSlot.ARMOR_HEAD);
        }
        if (matchesArmor(stack, equippable, ObeliskEquipmentTags.ARMOR_CHEST, EquipmentSlot.CHEST)) {
            return Optional.of(ObeliskEquipmentSlot.ARMOR_CHEST);
        }
        if (matchesArmor(stack, equippable, ObeliskEquipmentTags.ARMOR_LEGS, EquipmentSlot.LEGS)) {
            return Optional.of(ObeliskEquipmentSlot.ARMOR_LEGS);
        }
        if (matchesArmor(stack, equippable, ObeliskEquipmentTags.ARMOR_FEET, EquipmentSlot.FEET)) {
            return Optional.of(ObeliskEquipmentSlot.ARMOR_FEET);
        }
        return Optional.empty();
    }

    public static boolean hasManagedReference(ItemStack stack) {
        return DamageNexusItemApi.getEntryTemplateReferences(stack).stream()
                .anyMatch(reference -> ObeliskEquipmentTemplateCatalog
                        .find(reference.id())
                        .isPresent());
    }

    public static boolean applyTemplateReference(
            ItemStack stack,
            ObeliskEquipmentContent content
    ) {
        if (stack == null || stack.isEmpty() || content == null) {
            return false;
        }
        if (content.temperingEligible()
                && ObeliskUniqueEquipmentStacks.isUnique(stack)) {
            return false;
        }

        DamageItemTemplateReferences current =
                DamageNexusItemApi.getTemplateReferences(stack);
        return DamageNexusItemApi.setTemplateReferences(
                stack,
                withTemplateReference(current, content)
        );
    }

    static DamageItemTemplateReferences withTemplateReference(
            DamageItemTemplateReferences current,
            ObeliskEquipmentContent content
    ) {
        if (current == null || content == null) {
            throw new IllegalArgumentException("current and content must not be null");
        }
        List<DamageEntryTemplateReference> entries = new ArrayList<>();
        for (DamageEntryTemplateReference reference : current.entries()) {
            ObeliskEquipmentContent existing = ObeliskEquipmentTemplateCatalog
                    .find(reference.id())
                    .orElse(null);
            if (existing == null
                    || !existing.stackingGroup().equals(content.stackingGroup())) {
                entries.add(reference);
            }
        }
        if (entries.stream().noneMatch(reference ->
                reference.id().equals(content.templateId()))) {
            entries.add(new DamageEntryTemplateReference(content.templateId()));
        }
        return new DamageItemTemplateReferences(entries, current.affixes());
    }

    public static boolean removeManagedReferences(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        DamageItemTemplateReferences current =
                DamageNexusItemApi.getTemplateReferences(stack);
        return DamageNexusItemApi.setTemplateReferences(
                stack,
                withoutManagedReferences(current)
        );
    }

    static DamageItemTemplateReferences withoutManagedReferences(
            DamageItemTemplateReferences current
    ) {
        if (current == null) {
            throw new IllegalArgumentException("current must not be null");
        }
        List<DamageEntryTemplateReference> kept = current.entries().stream()
                .filter(reference -> ObeliskEquipmentTemplateCatalog
                        .find(reference.id())
                        .map(content -> !content.temperingEligible())
                        .orElse(true))
                .toList();
        return new DamageItemTemplateReferences(kept, current.affixes());
    }

    private static boolean matchesArmor(
            ItemStack stack,
            Equippable equippable,
            TagKey<Item> tag,
            EquipmentSlot expectedSlot
    ) {
        return stack.is(tag)
                && equippable != null
                && equippable.slot() == expectedSlot;
    }
}
