package io.github.naimjeg.obeliskdepths.equipment;

import io.github.naimjeg.damagenexus.api.item.DamageNexusItemApi;
import io.github.naimjeg.damagenexus.api.item.template.DamageEntryTemplateReference;
import io.github.naimjeg.damagenexus.api.item.template.DamageItemTemplateReferences;
import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** The only construction and recognition boundary for unique ItemStack variants. */
public final class ObeliskUniqueEquipmentStacks {
    private ObeliskUniqueEquipmentStacks() {
    }

    /** Returns a fresh stack with one payload-free unique template reference. */
    public static ItemStack create(ObeliskUniqueEquipmentDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        Item baseItem = Objects.requireNonNull(
                definition.baseItem().get(),
                "unique equipment base item"
        );
        return copyWithIdentity(new ItemStack(baseItem), definition);
    }

    public static ItemStack create(Identifier templateId) {
        return create(ObeliskUniqueEquipmentCatalog.find(templateId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown unique equipment template: " + templateId
                )));
    }

    /**
     * Copies the supplied stack, replaces ObeliskDepths-owned entry references
     * with the declared unique identity, and preserves every foreign entry and
     * affix reference.
     */
    public static ItemStack copyWithIdentity(
            ItemStack base,
            ObeliskUniqueEquipmentDefinition definition
    ) {
        Objects.requireNonNull(definition, "definition");
        if (base == null || base.isEmpty()) {
            throw new IllegalArgumentException("Unique equipment base stack must not be empty");
        }

        ItemStack result = base.copy();
        DamageItemTemplateReferences current =
                DamageNexusItemApi.getTemplateReferences(result);
        List<DamageEntryTemplateReference> entries = new ArrayList<>();
        for (DamageEntryTemplateReference reference : current.entries()) {
            if (!ObeliskDepths.MOD_ID.equals(reference.id().getNamespace())) {
                entries.add(reference);
            }
        }
        entries.add(new DamageEntryTemplateReference(definition.templateId()));
        if (!DamageNexusItemApi.setTemplateReferences(
                result,
                new DamageItemTemplateReferences(entries, current.affixes())
        )) {
            throw new IllegalStateException(
                    "Unable to store unique equipment identity: "
                            + definition.templateId()
            );
        }
        result.set(
                DataComponents.CUSTOM_NAME,
                Component.translatable(definition.displayNameTranslationKey())
        );
        return result;
    }

    /** True for any stack carrying at least one formal unique template ID. */
    public static boolean isUnique(ItemStack stack) {
        return DamageNexusItemApi.getEntryTemplateReferences(stack).stream()
                .anyMatch(reference -> ObeliskUniqueEquipmentCatalog
                        .find(reference.id())
                        .isPresent());
    }

    /** Resolves the identity only when exactly one distinct unique ID is present. */
    public static Optional<ObeliskUniqueEquipmentDefinition> identify(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        List<Identifier> identities = DamageNexusItemApi
                .getEntryTemplateReferences(stack)
                .stream()
                .map(DamageEntryTemplateReference::id)
                .filter(id -> ObeliskUniqueEquipmentCatalog.find(id).isPresent())
                .distinct()
                .toList();
        if (identities.size() != 1) {
            return Optional.empty();
        }
        return ObeliskUniqueEquipmentCatalog.find(identities.getFirst());
    }
}
