package io.github.naimjeg.obeliskdepths.equipment;

import io.github.naimjeg.damagenexus.api.item.template.DamageAffixTemplateReference;
import io.github.naimjeg.damagenexus.api.item.template.DamageEntryTemplateReference;
import io.github.naimjeg.damagenexus.api.item.template.DamageItemTemplateReferences;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ObeliskEquipmentReferenceRulesTest {
    private static final Identifier FOREIGN_ENTRY =
            Identifier.fromNamespaceAndPath("thirdpartymod", "entry");
    private static final Identifier FOREIGN_AFFIX =
            Identifier.fromNamespaceAndPath("thirdpartymod", "affix");

    @Test
    void addingIsDeduplicatedAndPreservesForeignReferences() {
        DamageItemTemplateReferences initial = foreignOnly();
        ObeliskEquipmentContent tempered = required(ObeliskEquipmentIds.TEMPERED);

        DamageItemTemplateReferences once =
                ObeliskEquipmentRules.withTemplateReference(initial, tempered);
        DamageItemTemplateReferences twice =
                ObeliskEquipmentRules.withTemplateReference(once, tempered);

        assertEquals(once, twice);
        assertEquals(
                List.of(FOREIGN_ENTRY, ObeliskEquipmentIds.TEMPERED),
                ids(twice)
        );
        assertEquals(FOREIGN_AFFIX, twice.affixes().getFirst().id());
    }

    @Test
    void sameStackingGroupReplacesAndDifferentGroupAccumulates() {
        DamageItemTemplateReferences deadly = ObeliskEquipmentRules.withTemplateReference(
                foreignOnly(),
                required(ObeliskEquipmentIds.DEADLY)
        );
        DamageItemTemplateReferences critical = ObeliskEquipmentRules.withTemplateReference(
                deadly,
                required(ObeliskEquipmentIds.CRITICAL_EDGE)
        );
        DamageItemTemplateReferences tempered = ObeliskEquipmentRules.withTemplateReference(
                critical,
                required(ObeliskEquipmentIds.TEMPERED)
        );

        assertEquals(
                List.of(
                        FOREIGN_ENTRY,
                        ObeliskEquipmentIds.CRITICAL_EDGE,
                        ObeliskEquipmentIds.TEMPERED
                ),
                ids(tempered)
        );
    }

    @Test
    void removingManagedReferencesNeverDeletesAnotherNamespaceOrAffixes() {
        DamageItemTemplateReferences withManaged = ObeliskEquipmentRules.withTemplateReference(
                foreignOnly(),
                required(ObeliskEquipmentIds.FLAMEFORGED)
        );
        DamageItemTemplateReferences cleaned =
                ObeliskEquipmentRules.withoutManagedReferences(withManaged);

        assertEquals(foreignOnly(), cleaned);
    }

    private static DamageItemTemplateReferences foreignOnly() {
        return new DamageItemTemplateReferences(
                List.of(new DamageEntryTemplateReference(FOREIGN_ENTRY)),
                List.of(new DamageAffixTemplateReference(FOREIGN_AFFIX))
        );
    }

    private static List<Identifier> ids(DamageItemTemplateReferences references) {
        return references.entries().stream()
                .map(DamageEntryTemplateReference::id)
                .toList();
    }

    private static ObeliskEquipmentContent required(Identifier id) {
        return ObeliskEquipmentTemplateCatalog.find(id).orElseThrow();
    }
}
