package io.github.naimjeg.obeliskdepths.equipment;

import com.mojang.serialization.JsonOps;
import io.github.naimjeg.damagenexus.api.rule.DamageRuleValidator;
import io.github.naimjeg.damagenexus.api.rule.entry.DamageEntryDefinition;
import io.github.naimjeg.damagenexus.api.rule.entry.DamageEntryValidator;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Frozen, deterministic ObeliskDepths equipment directory. It is the source
 * for both client previews and the server registration callback; it never
 * reads DamageNexus' internal registry.
 */
public final class ObeliskEquipmentTemplateCatalog {
    private static final Comparator<ObeliskEquipmentContent> ORDER =
            Comparator.comparing(content -> content.templateId().toString());

    private static final List<ObeliskEquipmentContent> CONTENT = build();
    private static final Map<Identifier, ObeliskEquipmentContent> BY_ID = index(CONTENT);

    private ObeliskEquipmentTemplateCatalog() {
    }

    public static List<ObeliskEquipmentContent> all() {
        return CONTENT;
    }

    public static Optional<ObeliskEquipmentContent> find(Identifier id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    public static List<ObeliskEquipmentContent> forDirection(Identifier direction) {
        return CONTENT.stream()
                .filter(content -> content.temperingEligible()
                        && content.temperingDirections().contains(direction))
                .toList();
    }

    public static List<DamageEntryDefinition> definitions() {
        return CONTENT.stream().map(ObeliskEquipmentContent::definition).toList();
    }

    private static List<ObeliskEquipmentContent> build() {
        List<ObeliskEquipmentContent> values = new ArrayList<>();
        for (DamageEntryDefinition definition : ObeliskTemperingEntryDefinitions.all()) {
            values.add(new ObeliskEquipmentContent(
                    definition.id(),
                    definition,
                    definition.slot(),
                    EnumSet.allOf(ObeliskEquipmentSlot.class),
                    definition.stackingKey(),
                    directionsFor(definition.id()),
                    defaultWeight(definition.id()),
                    true
            ));
        }
        values.addAll(ObeliskUniqueEquipmentCatalog.contents());
        values.sort(ORDER);
        validate(values);
        return List.copyOf(values);
    }

    private static Set<Identifier> directionsFor(Identifier id) {
        String path = id.getPath();
        String name = path.substring(path.lastIndexOf('/') + 1);
        Set<Identifier> result = new java.util.LinkedHashSet<>();
        if (Set.of("tempered", "brutal", "razor_edged", "deadly").contains(name)) {
            result.add(io.github.naimjeg.obeliskdepths.tempering.ObeliskTemperingDirectionRegistry.BALANCE);
        }
        if (Set.of("razor_edged", "piercing", "sundering", "executioners").contains(name)) {
            result.add(io.github.naimjeg.obeliskdepths.tempering.ObeliskTemperingDirectionRegistry.EDGE);
        }
        if (Set.of("flaming", "fire_edge", "flameforged", "smoldering").contains(name)) {
            result.add(io.github.naimjeg.obeliskdepths.tempering.ObeliskTemperingDirectionRegistry.FLAME);
        }
        if (Set.of("frostbound", "frostforged").contains(name)) {
            result.add(io.github.naimjeg.obeliskdepths.tempering.ObeliskTemperingDirectionRegistry.FROST);
        }
        if (Set.of("stormcharged", "stormforged", "impacting").contains(name)) {
            result.add(io.github.naimjeg.obeliskdepths.tempering.ObeliskTemperingDirectionRegistry.STORM);
        }
        if (Set.of("arcane", "spellblade").contains(name)) {
            result.add(io.github.naimjeg.obeliskdepths.tempering.ObeliskTemperingDirectionRegistry.ARCANE);
        }
        if (Set.of("venomous", "toxic_edge", "withering").contains(name)) {
            result.add(io.github.naimjeg.obeliskdepths.tempering.ObeliskTemperingDirectionRegistry.VENOM);
        }
        if (Set.of("deadly", "critical_edge", "ambushers", "razor_edged").contains(name)) {
            result.add(io.github.naimjeg.obeliskdepths.tempering.ObeliskTemperingDirectionRegistry.PRECISION);
        }
        if (Set.of("giant_slayers", "executioners", "ambushers", "tempered").contains(name)) {
            result.add(io.github.naimjeg.obeliskdepths.tempering.ObeliskTemperingDirectionRegistry.HUNT);
        }
        return Set.copyOf(result);
    }

    private static int defaultWeight(Identifier id) {
        String name = id.getPath().substring(id.getPath().lastIndexOf('/') + 1);
        return switch (name) {
            case "tempered" -> 10;
            case "brutal", "flameforged", "frostforged", "spellblade", "toxic_edge" -> 6;
            case "razor_edged", "flaming", "frostbound", "arcane", "venomous" -> 10;
            default -> 5;
        };
    }

    private static Map<Identifier, ObeliskEquipmentContent> index(List<ObeliskEquipmentContent> values) {
        Map<Identifier, ObeliskEquipmentContent> result = new LinkedHashMap<>();
        for (ObeliskEquipmentContent content : values) {
            if (result.put(content.templateId(), content) != null) {
                throw new IllegalStateException("Duplicate Obelisk equipment template id: " + content.templateId());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static void validate(List<ObeliskEquipmentContent> values) {
        Map<Identifier, Identifier> rules = new LinkedHashMap<>();
        Map<Identifier, Identifier> groups = new LinkedHashMap<>();
        for (ObeliskEquipmentContent content : values) {
            if (content.definition().rules().isEmpty()) {
                throw new IllegalStateException("Template has no rules: " + content.templateId());
            }
            if (DamageEntryValidator.filterValid(
                    List.of(content.definition()),
                    "obeliskdepths/equipment_catalog"
            ).size() != 1) {
                throw new IllegalStateException(
                        "DamageNexus rejected equipment template: " + content.templateId()
                );
            }
            DamageEntryDefinition.CODEC.encodeStart(JsonOps.INSTANCE, content.definition())
                    .getOrThrow(message -> new IllegalStateException(
                            "DamageNexus codec rejected equipment template "
                                    + content.templateId() + ": " + message
                    ));
            if (groups.putIfAbsent(content.stackingGroup(), content.templateId()) != null
                    && !content.stackingGroup().equals(ObeliskEquipmentIds.stacking("critical_physical"))) {
                throw new IllegalStateException("Unexpected stacking group collision: " + content.stackingGroup());
            }
            for (var rule : content.definition().rules()) {
                if (rules.put(rule.id(), content.templateId()) != null) {
                    throw new IllegalStateException("Duplicate Obelisk equipment rule id: " + rule.id());
                }
                DamageRuleValidator.requireValid(rule, "obeliskdepths/equipment_catalog");
            }
        }
    }
}
