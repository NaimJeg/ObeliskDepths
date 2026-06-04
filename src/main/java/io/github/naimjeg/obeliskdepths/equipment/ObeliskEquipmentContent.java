package io.github.naimjeg.obeliskdepths.equipment;

import io.github.naimjeg.damagenexus.api.rule.entry.DamageEntryDefinition;
import io.github.naimjeg.damagenexus.api.rule.entry.DamageEntrySlot;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable catalog metadata plus one complete DamageNexus entry template. */
public record ObeliskEquipmentContent(
        Identifier templateId,
        DamageEntryDefinition definition,
        DamageEntrySlot damageNexusSlot,
        Set<ObeliskEquipmentSlot> supportedSlots,
        Identifier stackingGroup,
        Set<Identifier> temperingDirections,
        int defaultWeight,
        boolean temperingEligible
) {
    public ObeliskEquipmentContent {
        Objects.requireNonNull(templateId, "templateId");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(damageNexusSlot, "damageNexusSlot");
        Objects.requireNonNull(stackingGroup, "stackingGroup");
        supportedSlots = supportedSlots == null
                ? Set.of()
                : Set.copyOf(supportedSlots);
        temperingDirections = temperingDirections == null
                ? Set.of()
                : Set.copyOf(temperingDirections);
        if (!templateId.equals(definition.id())) {
            throw new IllegalArgumentException(
                    "Template id does not match definition id: " + templateId
            );
        }
        if (!stackingGroup.equals(definition.stackingKey())) {
            throw new IllegalArgumentException(
                    "Catalog stacking group does not match definition: " + templateId
            );
        }
        if (supportedSlots.isEmpty()) {
            throw new IllegalArgumentException(
                    "Equipment template must support at least one slot: " + templateId
            );
        }
        if (defaultWeight <= 0) {
            throw new IllegalArgumentException(
                    "Equipment template weight must be positive: " + templateId
            );
        }
        if (!temperingEligible && !temperingDirections.isEmpty()) {
            throw new IllegalArgumentException(
                    "Non-tempering template cannot have directions: " + templateId
            );
        }
    }

    public boolean supports(ObeliskEquipmentSlot slot) {
        return slot != null && supportedSlots.contains(slot);
    }

    public List<Identifier> orderedDirections() {
        return temperingDirections.stream()
                .sorted(java.util.Comparator.comparing(Identifier::toString))
                .toList();
    }
}
