package io.github.naimjeg.obeliskdepths.equipment;

import io.github.naimjeg.damagenexus.api.DamageNexusPreMultiplierBuckets;
import io.github.naimjeg.damagenexus.api.enums.DamageApplicationBucket;
import io.github.naimjeg.damagenexus.api.enums.DamageChannel;
import io.github.naimjeg.damagenexus.api.enums.DamagePhase;
import io.github.naimjeg.damagenexus.api.display.DisplayText;
import io.github.naimjeg.damagenexus.api.rule.DamageNexusConditions;
import io.github.naimjeg.damagenexus.api.rule.DamageNexusOperations;
import io.github.naimjeg.damagenexus.api.rule.DamageRuleCondition;
import io.github.naimjeg.damagenexus.api.rule.DamageRuleDefinition;
import io.github.naimjeg.damagenexus.api.rule.DamageRuleRole;
import io.github.naimjeg.damagenexus.api.rule.DamageRuleStacking;
import io.github.naimjeg.damagenexus.api.rule.entry.DamageEntryDefinition;
import io.github.naimjeg.damagenexus.api.rule.entry.DamageEntryDisplay;
import io.github.naimjeg.damagenexus.api.rule.entry.DamageEntrySlot;
import io.github.naimjeg.damagenexus.api.rule.entry.DamageEntryStacking;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Single construction point for the complete built-in entry definitions. */
final class ObeliskTemperingEntryDefinitions {
    private ObeliskTemperingEntryDefinitions() {
    }

    static List<DamageEntryDefinition> all() {
        return List.of(
                tempered(), brutal(), razorEdged(), piercing(), sundering(),
                deadly(), criticalEdge(), ambushers(), executioners(), giantSlayers(),
                flaming(), fireEdge(), flameforged(), smoldering(),
                frostbound(), frostforged(), stormcharged(), stormforged(), impacting(),
                arcane(), spellblade(), venomous(), toxicEdge(), withering()
        );
    }

    private static DamageEntryDefinition tempered() {
        return flat(ObeliskEquipmentIds.TEMPERED, "tempered", "Tempered", "+3 physical damage", "A steady strike mark.", DamageChannel.PHYSICAL_ID, 3.0F, "flat_physical", "tempered");
    }

    private static DamageEntryDefinition brutal() {
        return global(ObeliskEquipmentIds.BRUTAL, "brutal", "Brutal", "+10% global damage", "A blunt force mark.", 0.10F, DamagePhase.GLOBAL_ADJUSTMENT, DamageNexusPreMultiplierBuckets.GENERIC_DAMAGE, List.of(DamageNexusConditions.always()), "global_damage", "brutal");
    }

    private static DamageEntryDefinition razorEdged() {
        return channel(ObeliskEquipmentIds.RAZOR_EDGED, "razor_edged", "Razor Edged", "+12% physical damage", "A clean edge mark.", DamageChannel.PHYSICAL_ID, DamageNexusPreMultiplierBuckets.PHYSICAL_DAMAGE, 0.12F, DamagePhase.TYPE_SCALING, List.of(DamageNexusConditions.always()), "physical_damage", "razor_edged");
    }

    private static DamageEntryDefinition piercing() {
        return entry(ObeliskEquipmentIds.PIERCING, "piercing", "Piercing", "+1.5 physical true damage", "A narrow point of force.", List.of(rule(ObeliskEquipmentIds.PIERCING, "piercing", "physical_true_damage", DamagePhase.BASE_MODIFICATION, List.of(DamageNexusConditions.always()), List.of(DamageNexusOperations.addTrueDamage(DamageChannel.PHYSICAL_ID, 1.5F)), "piercing")), ObeliskEquipmentIds.stacking("piercing"));
    }

    private static DamageEntryDefinition sundering() {
        return entry(ObeliskEquipmentIds.SUNDERING, "sundering", "Sundering", "Target armor effectiveness -12%", "A breaker mark.", List.of(rule(ObeliskEquipmentIds.SUNDERING, "sundering", "armor_effectiveness", DamagePhase.MITIGATION_SETUP, List.of(DamageNexusConditions.always()), List.of(DamageNexusOperations.multiplyArmorEffectiveness(0.88F)), "sundering")), ObeliskEquipmentIds.stacking("sundering"));
    }

    private static DamageEntryDefinition deadly() {
        return channel(ObeliskEquipmentIds.DEADLY, "deadly", "Deadly", "+20% physical damage when the final critical result is true", "A decisive timing mark.", DamageChannel.PHYSICAL_ID, DamageNexusPreMultiplierBuckets.CRIT_DAMAGE, 0.20F, DamagePhase.CRITICAL_HIT, List.of(DamageNexusConditions.critical()), "critical_physical", "critical_physical");
    }

    private static DamageEntryDefinition criticalEdge() {
        return channel(ObeliskEquipmentIds.CRITICAL_EDGE, "critical_edge", "Critical Edge", "+20% physical damage when the final critical result is true", "A clean decisive strike mark.", DamageChannel.PHYSICAL_ID, DamageNexusPreMultiplierBuckets.CRIT_DAMAGE, 0.20F, DamagePhase.CRITICAL_HIT, List.of(DamageNexusConditions.critical()), "critical_physical", "critical_physical");
    }

    private static DamageEntryDefinition ambushers() {
        return global(ObeliskEquipmentIds.AMBUSHERS, "ambushers", "Ambusher's", "+18% global damage above 80% target health", "An opening-strike mark.", 0.18F, DamagePhase.CONDITIONAL_MULTI, DamageNexusPreMultiplierBuckets.GENERIC_DAMAGE, List.of(DamageNexusConditions.targetHealthAbove(0.80F)), "high_health_target", "ambushers");
    }

    private static DamageEntryDefinition executioners() {
        return channel(ObeliskEquipmentIds.EXECUTIONERS, "executioners", "Executioner's", "+20% physical damage below 35% target health", "A finishing mark.", DamageChannel.PHYSICAL_ID, DamageNexusPreMultiplierBuckets.PHYSICAL_DAMAGE, 0.20F, DamagePhase.CONDITIONAL_MULTI, List.of(DamageNexusConditions.targetHealthBelow(0.35F)), "low_health_physical", "executioners");
    }

    private static DamageEntryDefinition giantSlayers() {
        return global(ObeliskEquipmentIds.GIANT_SLAYERS, "giant_slayers", "Giant Slayer's", "+20% global damage against bosses", "A formal boss-hunting mark.", 0.20F, DamagePhase.CONDITIONAL_MULTI, DamageNexusPreMultiplierBuckets.GENERIC_DAMAGE, List.of(DamageNexusConditions.targetIsBoss()), "boss_damage", "giant_slayers");
    }

    private static DamageEntryDefinition flaming() {
        return flat(ObeliskEquipmentIds.FLAMING, "flaming", "Flaming", "+3 fire damage", "A direct ember mark.", DamageChannel.FIRE_ID, 3.0F, "flat_fire", "flaming");
    }

    private static DamageEntryDefinition fireEdge() {
        return entry(ObeliskEquipmentIds.FIRE_EDGE, "fire_edge", "Fire Edge", List.of("+4 fire damage", "+15% fire damage"), "A tempering mark that burns through the edge.", List.of(
                rule(ObeliskEquipmentIds.FIRE_EDGE, "fire_edge", "base_fire", DamagePhase.BASE_MODIFICATION, List.of(DamageNexusConditions.always()), List.of(DamageNexusOperations.addBaseDamage(DamageChannel.FIRE_ID, DamageApplicationBucket.DN_RULE_BASE, 4.0F)), "fire_edge_base"),
                rule(ObeliskEquipmentIds.FIRE_EDGE, "fire_edge", "fire_scaling", DamagePhase.TYPE_SCALING, List.of(DamageNexusConditions.always()), List.of(DamageNexusOperations.addChannelPreMultiplier(DamageChannel.FIRE_ID, DamageNexusPreMultiplierBuckets.FIRE_DAMAGE, 0.15F)), "fire_edge_scaling")
        ), ObeliskEquipmentIds.stacking("fire_edge"));
    }

    private static DamageEntryDefinition flameforged() {
        return convert(ObeliskEquipmentIds.FLAMEFORGED, "flameforged", "Flameforged", "Converts 20% physical damage to fire", "A furnace-born mark.", DamageChannel.FIRE_ID, 0.20F, "flameforged");
    }

    private static DamageEntryDefinition smoldering() {
        return global(ObeliskEquipmentIds.SMOLDERING, "smoldering", "Smoldering", "+15% global damage against burning targets", "A patient heat mark.", 0.15F, DamagePhase.CONDITIONAL_MULTI, DamageNexusPreMultiplierBuckets.GENERIC_DAMAGE, List.of(DamageNexusConditions.targetOnFire()), "burning_target_damage", "smoldering");
    }

    private static DamageEntryDefinition frostbound() {
        return flat(ObeliskEquipmentIds.FROSTBOUND, "frostbound", "Frostbound", "+3 cold damage", "A winter edge mark.", DamageChannel.COLD_ID, 3.0F, "flat_cold", "frostbound");
    }

    private static DamageEntryDefinition frostforged() {
        return convert(ObeliskEquipmentIds.FROSTFORGED, "frostforged", "Frostforged", "Converts 20% physical damage to cold", "A pale forge mark.", DamageChannel.COLD_ID, 0.20F, "frostforged");
    }

    private static DamageEntryDefinition stormcharged() {
        return flat(ObeliskEquipmentIds.STORMCHARGED, "stormcharged", "Stormcharged", "+3 lightning damage", "A charged strike mark.", DamageChannel.LIGHTNING_ID, 3.0F, "flat_lightning", "stormcharged");
    }

    private static DamageEntryDefinition stormforged() {
        return convert(ObeliskEquipmentIds.STORMFORGED, "stormforged", "Stormforged", "Converts 18% physical damage to lightning", "A storm mark.", DamageChannel.LIGHTNING_ID, 0.18F, "stormforged");
    }

    private static DamageEntryDefinition impacting() {
        return flat(ObeliskEquipmentIds.IMPACTING, "impacting", "Impacting", "+2.5 kinetic damage", "A concussive mark.", DamageChannel.KINETIC_ID, 2.5F, "flat_kinetic", "impacting");
    }

    private static DamageEntryDefinition arcane() {
        return flat(ObeliskEquipmentIds.ARCANE, "arcane", "Arcane", "+3 magic damage", "A focused sigil.", DamageChannel.MAGIC_ID, 3.0F, "flat_magic", "arcane");
    }

    private static DamageEntryDefinition spellblade() {
        return entry(ObeliskEquipmentIds.SPELLBLADE, "spellblade", "Spellblade", "Gain 15% physical damage as additional magic damage", "A blade-and-sigil mark.", List.of(rule(ObeliskEquipmentIds.SPELLBLADE, "spellblade", "physical_to_magic_bonus", DamagePhase.TYPE_SCALING, List.of(DamageNexusConditions.always()), List.of(DamageNexusOperations.gainExtraDamage(DamageChannel.PHYSICAL_ID, DamageChannel.MAGIC_ID, 0.15F)), "spellblade")), ObeliskEquipmentIds.stacking("spellblade"));
    }

    private static DamageEntryDefinition venomous() {
        return flat(ObeliskEquipmentIds.VENOMOUS, "venomous", "Venomous", "+3 poison damage", "A toxin mark.", DamageChannel.POISON_ID, 3.0F, "flat_poison", "venomous");
    }

    private static DamageEntryDefinition toxicEdge() {
        return entry(ObeliskEquipmentIds.TOXIC_EDGE, "toxic_edge", "Toxic Edge", "Gain 15% physical damage as additional poison damage", "A coated-edge mark.", List.of(rule(ObeliskEquipmentIds.TOXIC_EDGE, "toxic_edge", "physical_to_poison_bonus", DamagePhase.TYPE_SCALING, List.of(DamageNexusConditions.always()), List.of(DamageNexusOperations.gainExtraDamage(DamageChannel.PHYSICAL_ID, DamageChannel.POISON_ID, 0.15F)), "toxic_edge")), ObeliskEquipmentIds.stacking("toxic_edge"));
    }

    private static DamageEntryDefinition withering() {
        return flat(ObeliskEquipmentIds.WITHERING, "withering", "Withering", "+2 wither damage", "A ruinous bite.", DamageChannel.WITHER_ID, 2.0F, "flat_wither", "withering");
    }

    private static DamageEntryDefinition flat(Identifier id, String key, String name, String tooltip, String flavor, Identifier channel, float value, String ruleName, String group) {
        return entry(id, key, name, tooltip, flavor, List.of(rule(id, key, ruleName, DamagePhase.BASE_MODIFICATION, List.of(DamageNexusConditions.always()), List.of(DamageNexusOperations.addBaseDamage(channel, DamageApplicationBucket.DN_RULE_BASE, value)), key)), ObeliskEquipmentIds.stacking(group));
    }

    private static DamageEntryDefinition convert(Identifier id, String key, String name, String tooltip, String flavor, Identifier target, float ratio, String group) {
        return entry(id, key, name, tooltip, flavor, List.of(rule(id, key, "physical_to_" + key, DamagePhase.TYPE_SCALING, List.of(DamageNexusConditions.always()), List.of(DamageNexusOperations.convertDamage(DamageChannel.PHYSICAL_ID, target, ratio)), key)), ObeliskEquipmentIds.stacking(group));
    }

    private static DamageEntryDefinition channel(Identifier id, String key, String name, String tooltip, String flavor, Identifier channel, Identifier bucket, float value, DamagePhase phase, List<DamageRuleCondition> conditions, String ruleName, String group) {
        return entry(id, key, name, tooltip, flavor, List.of(rule(id, key, ruleName, phase, conditions, List.of(DamageNexusOperations.addChannelPreMultiplier(channel, bucket, value)), key)), ObeliskEquipmentIds.stacking(group));
    }

    private static DamageEntryDefinition global(Identifier id, String key, String name, String tooltip, String flavor, float value, DamagePhase phase, Identifier bucket, List<DamageRuleCondition> conditions, String ruleName, String group) {
        return entry(id, key, name, tooltip, flavor, List.of(rule(id, key, ruleName, phase, conditions, List.of(DamageNexusOperations.addGlobalPreMultiplier(bucket, value)), key)), ObeliskEquipmentIds.stacking(group));
    }

    private static DamageEntryDefinition entry(Identifier id, String key, String name, String tooltip, String flavor, List<DamageRuleDefinition> rules, Identifier group) {
        return entry(id, key, name, List.of(tooltip), flavor, rules, group);
    }

    private static DamageEntryDefinition entry(Identifier id, String key, String name, List<String> tooltips, String flavor, List<DamageRuleDefinition> rules, Identifier group) {
        return new DamageEntryDefinition(id, display(key, name, tooltips, flavor), DamageEntrySlot.ITEM, rules, DamageEntryStacking.UNIQUE_GROUP, Optional.of(group));
    }

    private static DamageRuleDefinition rule(Identifier id, String key, String ruleName, DamagePhase phase, List<DamageRuleCondition> conditions, List<io.github.naimjeg.damagenexus.api.rule.DamageRuleOperation> operations, String trace) {
        return new DamageRuleDefinition(ObeliskEquipmentIds.rule(key, ruleName), DamageRuleRole.OFFENSIVE, phase, 500, conditions, operations, DamageRuleStacking.STACK, Optional.of(ObeliskEquipmentIds.rule(key, ruleName)), Optional.of(trace));
    }

    private static DamageEntryDisplay display(String key, String name, List<String> tooltips, String flavor) {
        List<DisplayText> tooltipText = new ArrayList<>();
        for (int index = 0; index < tooltips.size(); index++) {
            tooltipText.add(DisplayText.translatableWithFallback(
                    "entry.obeliskdepths." + key + ".tooltip." + index,
                    tooltips.get(index)
            ));
        }
        return new DamageEntryDisplay(
                Optional.of(DisplayText.translatableWithFallback(
                        "entry.obeliskdepths." + key + ".name",
                        name
                )),
                List.copyOf(tooltipText),
                Optional.of(DisplayText.translatableWithFallback("entry.obeliskdepths." + key + ".flavor", flavor)),
                true
        );
    }

}
