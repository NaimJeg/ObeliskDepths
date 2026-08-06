package io.github.naimjeg.obeliskdepths.equipment;

import com.mojang.serialization.JsonOps;
import io.github.naimjeg.damagenexus.api.rule.entry.DamageEntryDefinition;
import io.github.naimjeg.damagenexus.api.rule.entry.DamageEntrySlot;
import io.github.naimjeg.damagenexus.api.rule.entry.DamageEntryValidator;
import io.github.naimjeg.obeliskdepths.tempering.ObeliskTemperingDirectionRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class ObeliskEquipmentCatalogTest {
    @Test
    void catalogIsSortedImmutableAndUsesUniqueIds() {
        List<ObeliskEquipmentContent> catalog = ObeliskEquipmentTemplateCatalog.all();
        List<String> ids = catalog.stream()
                .map(content -> content.templateId().toString())
                .toList();
        List<String> sorted = new ArrayList<>(ids);
        sorted.sort(String::compareTo);

        assertEquals(sorted, ids);
        assertEquals(ids.size(), new HashSet<>(ids).size());
        assertThrows(UnsupportedOperationException.class, () -> catalog.clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> catalog.getFirst().supportedSlots().clear()
        );
    }

    @Test
    void definitionsMatchRegistrationIdsAndPassPublicValidationAndCodec() {
        for (ObeliskEquipmentContent content : ObeliskEquipmentTemplateCatalog.all()) {
            DamageEntryDefinition definition = content.definition();
            assertEquals(content.templateId(), definition.id());
            assertEquals(content.damageNexusSlot(), definition.slot());
            assertEquals(DamageEntrySlot.ITEM, definition.slot());
            assertEquals(
                    1,
                    DamageEntryValidator.filterValid(
                            List.of(definition),
                            "obeliskdepths/junit"
                    ).size()
            );
            assertTrue(DamageEntryDefinition.CODEC
                    .encodeStart(JsonOps.INSTANCE, definition)
                    .isSuccess());
        }
    }

    @Test
    void ruleIdsAreUniqueAndCriticalEntriesShareOnlyTheirIntendedGroup() {
        Set<Object> ruleIds = new HashSet<>();
        for (ObeliskEquipmentContent content : ObeliskEquipmentTemplateCatalog.all()) {
            content.definition().rules().forEach(rule ->
                    assertTrue(ruleIds.add(rule.id()), "duplicate rule " + rule.id())
            );
        }

        ObeliskEquipmentContent deadly = required(ObeliskEquipmentIds.DEADLY);
        ObeliskEquipmentContent critical = required(ObeliskEquipmentIds.CRITICAL_EDGE);
        assertEquals(deadly.stackingGroup(), critical.stackingGroup());
        assertNotEquals(
                required(ObeliskEquipmentIds.TEMPERED).stackingGroup(),
                deadly.stackingGroup()
        );
    }

    @Test
    void catalogContainsTemperingAndUniqueEntriesWithoutChangingDirectionPools() {
        assertEquals(30, ObeliskEquipmentTemplateCatalog.all().size());
        assertEquals(
                24,
                ObeliskEquipmentTemplateCatalog.all().stream()
                        .filter(ObeliskEquipmentContent::temperingEligible)
                        .count()
        );
        assertEquals(6, ObeliskUniqueEquipmentCatalog.all().size());
        assertEquals(2, required(ObeliskEquipmentIds.FIRE_EDGE).definition().rules().size());
        assertEquals(
                2,
                required(ObeliskEquipmentIds.FIRE_EDGE)
                        .definition().display().authoredSummary().size()
        );

        ObeliskTemperingDirectionRegistry.bootstrapBuiltIns();
        assertFalse(ObeliskTemperingDirectionRegistry.contains(
                ObeliskTemperingDirectionRegistry.GUARD
        ));
        assertFalse(ObeliskTemperingDirectionRegistry.contains(
                ObeliskTemperingDirectionRegistry.ECHO
        ));
        assertTrue(ObeliskEquipmentTemplateCatalog.forDirection(
                ObeliskTemperingDirectionRegistry.GUARD
        ).isEmpty());
        assertTrue(ObeliskEquipmentTemplateCatalog.forDirection(
                ObeliskTemperingDirectionRegistry.ECHO
        ).isEmpty());
    }

    @Test
    void temperingTemplatesRetainTheirExistingEquipmentBoundary() {
        Set<ObeliskEquipmentSlot> expected = Set.of(
                ObeliskEquipmentSlot.WEAPON,
                ObeliskEquipmentSlot.ARMOR_HEAD,
                ObeliskEquipmentSlot.ARMOR_CHEST,
                ObeliskEquipmentSlot.ARMOR_LEGS,
                ObeliskEquipmentSlot.ARMOR_FEET
        );
        for (ObeliskEquipmentContent content : ObeliskEquipmentTemplateCatalog.all()
                .stream()
                .filter(ObeliskEquipmentContent::temperingEligible)
                .toList()) {
            assertEquals(expected, content.supportedSlots());
            assertTrue(content.temperingEligible());
            assertTrue(content.defaultWeight() > 0);
        }
    }

    @Test
    void uniqueTemplatesSupportOnlyTheirSlotAndCannotBeTempered() {
        Set<Object> entryGroups = new HashSet<>();
        Set<Object> ruleGroups = new HashSet<>();
        Set<String> traces = new HashSet<>();
        for (ObeliskUniqueEquipmentDefinition unique
                : ObeliskUniqueEquipmentCatalog.all()) {
            ObeliskEquipmentContent content = unique.content();
            assertEquals(Set.of(unique.slot()), content.supportedSlots());
            assertFalse(content.temperingEligible());
            assertTrue(content.temperingDirections().isEmpty());
            assertSame(content, required(unique.templateId()));
            assertEquals(4, unique.minimumRewardTier());
            assertEquals(3, unique.rewardWeight());
            assertTrue(entryGroups.add(content.stackingGroup()));
            for (var rule : content.definition().rules()) {
                assertTrue(ruleGroups.add(rule.stackingGroup().orElseThrow()));
                assertTrue(traces.add(rule.traceLabel().orElseThrow()));
                assertTrue(rule.id().toString().startsWith("obeliskdepths:unique/"));
                assertEquals(rule.id().toString(), rule.traceLabel().orElseThrow());
            }
        }
    }

    private static ObeliskEquipmentContent required(net.minecraft.resources.Identifier id) {
        return ObeliskEquipmentTemplateCatalog.find(id).orElseThrow();
    }
}
