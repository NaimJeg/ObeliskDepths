package io.github.naimjeg.obeliskdepths.equipment;

import io.github.naimjeg.damagenexus.api.rule.entry.DamageEntryDefinition;
import io.github.naimjeg.obeliskdepths.registry.ModItems;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Frozen directory of the six formal unique equipment variants. */
public final class ObeliskUniqueEquipmentCatalog {
    public static final int MINIMUM_REWARD_TIER = 4;
    public static final int REWARD_POOL_WEIGHT = 3;

    private static final List<ObeliskUniqueEquipmentDefinition> DEFINITIONS = build();
    private static final Map<Identifier, ObeliskUniqueEquipmentDefinition> BY_ID =
            index(DEFINITIONS);

    private ObeliskUniqueEquipmentCatalog() {
    }

    public static List<ObeliskUniqueEquipmentDefinition> all() {
        return DEFINITIONS;
    }

    public static Optional<ObeliskUniqueEquipmentDefinition> find(Identifier templateId) {
        return Optional.ofNullable(BY_ID.get(templateId));
    }

    public static List<ObeliskEquipmentContent> contents() {
        return DEFINITIONS.stream()
                .map(ObeliskUniqueEquipmentDefinition::content)
                .toList();
    }

    public static List<ObeliskUniqueEquipmentDefinition> availableAtTier(int rewardTier) {
        return DEFINITIONS.stream()
                .filter(definition -> rewardTier >= definition.minimumRewardTier())
                .toList();
    }

    private static List<ObeliskUniqueEquipmentDefinition> build() {
        Map<Identifier, DamageEntryDefinition> definitions = new LinkedHashMap<>();
        for (DamageEntryDefinition definition : ObeliskUniqueEquipmentDefinitions.all()) {
            if (definitions.put(definition.id(), definition) != null) {
                throw new IllegalStateException(
                        "Duplicate unique equipment definition: " + definition.id()
                );
            }
        }

        List<ObeliskUniqueEquipmentDefinition> values = new ArrayList<>();
        values.add(unique(
                definitions,
                ObeliskEquipmentIds.GRANDFATHER,
                "grandfather",
                ObeliskEquipmentSlot.WEAPON,
                () -> ModItems.GRANDFATHER.get()
        ));
        values.add(unique(
                definitions,
                ObeliskEquipmentIds.HARLEQUIN_CREST,
                "harlequin_crest",
                ObeliskEquipmentSlot.ARMOR_HEAD,
                () -> ModItems.HARLEQUIN_CREST.get()
        ));
        values.add(unique(
                definitions,
                ObeliskEquipmentIds.TYRAELS_MIGHT,
                "tyraels_might",
                ObeliskEquipmentSlot.ARMOR_CHEST,
                () -> ModItems.TYRAELS_MIGHT.get()
        ));
        values.add(unique(
                definitions,
                ObeliskEquipmentIds.TIBAULTS_WILL,
                "tibaults_will",
                ObeliskEquipmentSlot.ARMOR_LEGS,
                () -> ModItems.TIBAULTS_WILL.get()
        ));
        values.add(unique(
                definitions,
                ObeliskEquipmentIds.BLOOD_MOON_BREECHES,
                "blood_moon_breeches",
                ObeliskEquipmentSlot.ARMOR_LEGS,
                () -> ModItems.BLOOD_MOON_BREECHES.get()
        ));
        values.add(unique(
                definitions,
                ObeliskEquipmentIds.COWL_OF_THE_NAMELESS,
                "cowl_of_the_nameless",
                ObeliskEquipmentSlot.ARMOR_HEAD,
                () -> ModItems.COWL_OF_THE_NAMELESS.get()
        ));
        values.sort(Comparator.comparing(definition -> definition.templateId().toString()));
        if (values.size() != definitions.size()) {
            throw new IllegalStateException("Uncatalogued unique equipment definition");
        }
        return List.copyOf(values);
    }

    private static ObeliskUniqueEquipmentDefinition unique(
            Map<Identifier, DamageEntryDefinition> definitions,
            Identifier id,
            String key,
            ObeliskEquipmentSlot slot,
            java.util.function.Supplier<? extends net.minecraft.world.item.Item> baseItem
    ) {
        DamageEntryDefinition definition = Optional.ofNullable(definitions.get(id))
                .orElseThrow(() -> new IllegalStateException(
                        "Missing unique equipment definition: " + id
                ));
        ObeliskEquipmentContent content = new ObeliskEquipmentContent(
                id,
                definition,
                definition.slot(),
                Set.of(slot),
                definition.stackingKey(),
                Set.of(),
                REWARD_POOL_WEIGHT,
                false
        );
        return new ObeliskUniqueEquipmentDefinition(
                id,
                "item.obeliskdepths.unique." + key,
                slot,
                baseItem,
                MINIMUM_REWARD_TIER,
                REWARD_POOL_WEIGHT,
                content
        );
    }

    private static Map<Identifier, ObeliskUniqueEquipmentDefinition> index(
            List<ObeliskUniqueEquipmentDefinition> definitions
    ) {
        Map<Identifier, ObeliskUniqueEquipmentDefinition> result = new LinkedHashMap<>();
        for (ObeliskUniqueEquipmentDefinition definition : definitions) {
            if (result.put(definition.templateId(), definition) != null) {
                throw new IllegalStateException(
                        "Duplicate unique equipment catalog id: " + definition.templateId()
                );
            }
        }
        return Collections.unmodifiableMap(result);
    }
}
