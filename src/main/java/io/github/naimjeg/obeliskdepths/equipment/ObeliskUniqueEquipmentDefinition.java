package io.github.naimjeg.obeliskdepths.equipment;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

import java.util.Objects;
import java.util.function.Supplier;

/** Immutable reward and presentation metadata for one unique ItemStack variant. */
public record ObeliskUniqueEquipmentDefinition(
        Identifier templateId,
        String displayNameTranslationKey,
        ObeliskEquipmentSlot slot,
        Supplier<? extends Item> baseItem,
        int minimumRewardTier,
        int rewardWeight,
        ObeliskEquipmentContent content
) {
    public ObeliskUniqueEquipmentDefinition {
        Objects.requireNonNull(templateId, "templateId");
        Objects.requireNonNull(displayNameTranslationKey, "displayNameTranslationKey");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(baseItem, "baseItem");
        Objects.requireNonNull(content, "content");
        if (!templateId.equals(content.templateId())) {
            throw new IllegalArgumentException(
                    "Unique equipment template/content mismatch: " + templateId
            );
        }
        if (!content.supportedSlots().equals(java.util.Set.of(slot))) {
            throw new IllegalArgumentException(
                    "Unique equipment must support only its declared slot: " + templateId
            );
        }
        if (content.temperingEligible() || !content.temperingDirections().isEmpty()) {
            throw new IllegalArgumentException(
                    "Unique equipment cannot enter tempering pools: " + templateId
            );
        }
        if (displayNameTranslationKey.isBlank()) {
            throw new IllegalArgumentException("Unique display-name key must not be blank");
        }
        if (minimumRewardTier < 1 || rewardWeight <= 0) {
            throw new IllegalArgumentException(
                    "Invalid unique reward metadata: " + templateId
            );
        }
    }

    /** Client-facing item/equipment asset ID, derived from the gameplay ID. */
    public Identifier assetId() {
        String path = templateId.getPath();
        int separator = path.lastIndexOf('/');
        return Identifier.fromNamespaceAndPath(
                templateId.getNamespace(),
                separator < 0 ? path : path.substring(separator + 1)
        );
    }

    /** Vanilla equipment asset key for armor; weapons have no equipment asset. */
    public ResourceKey<EquipmentAsset> equipmentAsset() {
        if (slot == ObeliskEquipmentSlot.WEAPON) {
            throw new IllegalStateException("Weapons do not have equipment assets: " + templateId);
        }
        return ResourceKey.create(EquipmentAssets.ROOT_ID, assetId());
    }
}
