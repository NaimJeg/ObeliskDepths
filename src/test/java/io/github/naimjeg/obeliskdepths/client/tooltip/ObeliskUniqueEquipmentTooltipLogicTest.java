package io.github.naimjeg.obeliskdepths.client.tooltip;

import io.github.naimjeg.damagenexus.api.item.template.DamageNexusTemplates;
import io.github.naimjeg.damagenexus.api.rule.entry.DamageEntryDefinition;
import io.github.naimjeg.obeliskdepths.equipment.ObeliskUniqueEquipmentCatalog;
import io.github.naimjeg.obeliskdepths.equipment.ObeliskUniqueEquipmentDefinition;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ObeliskUniqueEquipmentTooltipLogicTest {
    private ObeliskUniqueEquipmentTooltipLogicTest() {
    }

    public static void main(String[] args) {
        uniqueCatalogEntriesResolveThroughRegistryOrCatalogFallback();
        presentationCopyKeepsAllAuthoringExceptName();
        presentationCopyDoesNotMutateCatalogDefinitions();
        materializedSameIdSuppressesSupplement();
        unknownIdentityDoesNotSupplement();
        templateMissUsesReadOnlyCatalogFallback();
        repeatedPureResolutionIsStable();
    }

    @Test
    static void uniqueCatalogEntriesResolveThroughRegistryOrCatalogFallback() {
        for (ObeliskUniqueEquipmentDefinition unique
                : ObeliskUniqueEquipmentCatalog.all()) {
            Identifier id = unique.templateId();
            Optional<DamageEntryDefinition> registered =
                    DamageNexusTemplates.entry(id);
            Optional<DamageEntryDefinition> fallback =
                    ObeliskUniqueEquipmentTooltipLogic.presentationEntry(
                            Optional.of(unique),
                            List.of(),
                            ignored -> Optional.empty()
                    );

            DamageEntryDefinition resolved = registered.or(() -> fallback)
                    .orElseThrow(() -> new AssertionError(
                            "No template or catalog definition for " + id
                    ));
            assertEquals(id, resolved.id());
        }
    }

    @Test
    static void presentationCopyKeepsAllAuthoringExceptName() {
        for (ObeliskUniqueEquipmentDefinition unique
                : ObeliskUniqueEquipmentCatalog.all()) {
            DamageEntryDefinition source = unique.content().definition();
            DamageEntryDefinition presentation =
                    ObeliskUniqueEquipmentTooltipLogic.withoutDisplayName(source);

            assertTrue(presentation.display().name().isEmpty());
            assertEquals(source.id(), presentation.id());
            assertEquals(source.slot(), presentation.slot());
            assertEquals(source.stacking(), presentation.stacking());
            assertEquals(source.stackingGroup(), presentation.stackingGroup());
            assertEquals(
                    source.display().tooltip(),
                    presentation.display().tooltip()
            );
            assertEquals(
                    source.display().flavorText(),
                    presentation.display().flavorText()
            );
            assertEquals(
                    source.display().showRuleBreakdown(),
                    presentation.display().showRuleBreakdown()
            );
            assertEquals(source.rules(), presentation.rules());
            assertNotSame(source.display(), presentation.display());
        }
    }

    @Test
    static void presentationCopyDoesNotMutateCatalogDefinitions() {
        for (ObeliskUniqueEquipmentDefinition unique
                : ObeliskUniqueEquipmentCatalog.all()) {
            DamageEntryDefinition before = unique.content().definition();
            ObeliskUniqueEquipmentTooltipLogic.withoutDisplayName(before);
            assertSame(before, unique.content().definition());
        }
    }

    @Test
    static void materializedSameIdSuppressesSupplement() {
        for (ObeliskUniqueEquipmentDefinition unique
                : ObeliskUniqueEquipmentCatalog.all()) {
            Optional<DamageEntryDefinition> result =
                    ObeliskUniqueEquipmentTooltipLogic.presentationEntry(
                            Optional.of(unique),
                            List.of(unique.content().definition()),
                            ignored -> Optional.empty()
                    );
            assertTrue(result.isEmpty());
        }
    }

    @Test
    static void unknownIdentityDoesNotSupplement() {
        assertTrue(
                ObeliskUniqueEquipmentTooltipLogic.presentationEntry(
                        Optional.empty(),
                        List.of(),
                        ignored -> Optional.empty()
                ).isEmpty()
        );
    }

    @Test
    static void templateMissUsesReadOnlyCatalogFallback() {
        for (ObeliskUniqueEquipmentDefinition unique
                : ObeliskUniqueEquipmentCatalog.all()) {
            DamageEntryDefinition presentation =
                    ObeliskUniqueEquipmentTooltipLogic.presentationEntry(
                            Optional.of(unique),
                            List.of(),
                            ignored -> Optional.empty()
                    ).orElseThrow();
            assertEquals(unique.templateId(), presentation.id());
            assertFalse(presentation.display().name().isPresent());
            assertEquals(
                    unique.content().definition().display().tooltip(),
                    presentation.display().tooltip()
            );
        }
    }

    @Test
    static void repeatedPureResolutionIsStable() {
        for (ObeliskUniqueEquipmentDefinition unique
                : ObeliskUniqueEquipmentCatalog.all()) {
            DamageEntryDefinition catalog = unique.content().definition();
            DamageEntryDefinition first =
                    ObeliskUniqueEquipmentTooltipLogic.presentationEntry(
                            Optional.of(unique),
                            List.of(),
                            ignored -> Optional.empty()
                    ).orElseThrow();
            DamageEntryDefinition second =
                    ObeliskUniqueEquipmentTooltipLogic.presentationEntry(
                            Optional.of(unique),
                            List.of(),
                            ignored -> Optional.empty()
                    ).orElseThrow();
            assertEquals(first, second);
            assertSame(catalog, unique.content().definition());
        }
    }
}
