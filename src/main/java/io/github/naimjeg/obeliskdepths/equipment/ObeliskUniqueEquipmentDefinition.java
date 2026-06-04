package io.github.naimjeg.obeliskdepths.equipment;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

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
}
