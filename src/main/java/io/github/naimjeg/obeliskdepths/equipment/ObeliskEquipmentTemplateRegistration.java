package io.github.naimjeg.obeliskdepths.equipment;

import io.github.naimjeg.damagenexus.api.event.DamageNexusRegisterEvent;
import net.minecraft.resources.Identifier;

/** Registers every immutable catalog definition during the DN GAME-bus event. */
public final class ObeliskEquipmentTemplateRegistration {
    private ObeliskEquipmentTemplateRegistration() {
    }

    public static void register(DamageNexusRegisterEvent event) {
        for (ObeliskEquipmentContent content : ObeliskEquipmentTemplateCatalog.all()) {
            Identifier id = content.templateId();
            if (!id.equals(content.definition().id())) {
                throw new IllegalStateException("Template registration id mismatch: " + id);
            }
            event.registerEntryTemplate(id, content.definition());
        }
    }
}
