package io.github.naimjeg.obeliskdepths.client.tooltip;

import io.github.naimjeg.damagenexus.api.item.DamageNexusItemApi;
import io.github.naimjeg.damagenexus.api.item.template.DamageNexusTemplates;
import io.github.naimjeg.damagenexus.api.rule.entry.DamageEntryDefinition;
import io.github.naimjeg.damagenexus.api.rule.entry.DamageEntryDisplay;
import io.github.naimjeg.obeliskdepths.equipment.ObeliskUniqueEquipmentDefinition;
import io.github.naimjeg.obeliskdepths.equipment.ObeliskUniqueEquipmentStacks;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Pure resolution boundary for unique-equipment tooltip supplements.
 *
 * <p>The class intentionally has no Minecraft client renderer dependency so
 * its fallback and presentation-copy rules can be tested without a GUI.</p>
 */
public final class ObeliskUniqueEquipmentTooltipLogic {
    private ObeliskUniqueEquipmentTooltipLogic() {
    }

    /**
     * Resolves the unique identity, suppresses already-materialized entries,
     * and returns a presentation copy without the duplicate display name.
     */
    public static Optional<DamageEntryDefinition> resolveForPresentation(
            ItemStack stack
    ) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        Optional<ObeliskUniqueEquipmentDefinition> identity =
                ObeliskUniqueEquipmentStacks.identify(stack);
        return presentationEntry(
                identity,
                DamageNexusItemApi.getMaterializedEntries(stack),
                DamageNexusTemplates::entry
        );
    }

    /**
     * Pure fallback for tests and callers that already resolved identity.
     * The lookup receives the formal template ID; a miss falls back to the
     * read-only catalog definition without touching the ItemStack.
     */
    public static Optional<DamageEntryDefinition> presentationEntry(
            Optional<ObeliskUniqueEquipmentDefinition> identity,
            List<DamageEntryDefinition> materializedEntries,
            Function<Identifier, Optional<DamageEntryDefinition>> templateLookup
    ) {
        if (identity == null || identity.isEmpty()) {
            return Optional.empty();
        }
        ObeliskUniqueEquipmentDefinition unique = identity.get();
        Identifier templateId = unique.templateId();
        if (containsId(materializedEntries, templateId)) {
            return Optional.empty();
        }

        Objects.requireNonNull(templateLookup, "templateLookup");
        DamageEntryDefinition source = templateLookup.apply(templateId)
                .filter(definition -> templateId.equals(definition.id()))
                .orElseGet(() -> unique.content().definition());
        return Optional.of(withoutDisplayName(source));
    }

    /** Returns true when the definition ID is already materialized. */
    public static boolean containsId(
            List<DamageEntryDefinition> materializedEntries,
            Identifier id
    ) {
        if (materializedEntries == null || materializedEntries.isEmpty()) {
            return false;
        }
        Objects.requireNonNull(id, "id");
        return materializedEntries.stream()
                .filter(Objects::nonNull)
                .anyMatch(entry -> id.equals(entry.id()));
    }

    /**
     * Builds a tooltip-only copy that preserves every authoring field and
     * drops the DamageNexus display name, preventing a duplicate item title.
     */
    public static DamageEntryDefinition withoutDisplayName(
            DamageEntryDefinition source
    ) {
        Objects.requireNonNull(source, "source");
        DamageEntryDisplay display = source.display();
        DamageEntryDisplay presentationDisplay = new DamageEntryDisplay(
                Optional.empty(),
                display.tooltip(),
                display.flavorText(),
                display.showRuleBreakdown()
        );
        return new DamageEntryDefinition(
                source.id(),
                presentationDisplay,
                source.slot(),
                source.rules(),
                source.stacking(),
                source.stackingGroup()
        );
    }
}
