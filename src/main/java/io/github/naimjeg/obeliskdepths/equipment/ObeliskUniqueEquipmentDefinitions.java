package io.github.naimjeg.obeliskdepths.equipment;

import io.github.naimjeg.damagenexus.api.DamageNexusPreMultiplierBuckets;
import io.github.naimjeg.damagenexus.api.display.DisplayText;
import io.github.naimjeg.damagenexus.api.enums.DamageApplicationBucket;
import io.github.naimjeg.damagenexus.api.enums.DamageChannel;
import io.github.naimjeg.damagenexus.api.enums.DamagePhase;
import io.github.naimjeg.damagenexus.api.rule.DamageNexusConditions;
import io.github.naimjeg.damagenexus.api.rule.DamageNexusOperations;
import io.github.naimjeg.damagenexus.api.rule.DamageRuleCondition;
import io.github.naimjeg.damagenexus.api.rule.DamageRuleDefinition;
import io.github.naimjeg.damagenexus.api.rule.DamageRuleOperation;
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

/** Complete static DamageNexus definitions for the formal unique equipment set. */
public final class ObeliskUniqueEquipmentDefinitions {
    private static final int RULE_PRIORITY = 500;

    private ObeliskUniqueEquipmentDefinitions() {
    }

    public static List<DamageEntryDefinition> all() {
        return List.of(
                grandfather(),
                harlequinCrest(),
                tyraelsMight(),
                tibaultsWill(),
                bloodMoonBreeches(),
                cowlOfTheNameless()
        );
    }

    private static DamageEntryDefinition grandfather() {
        return entry(
                ObeliskEquipmentIds.GRANDFATHER,
                "grandfather",
                "The Grandfather",
                List.of("+50% damage when the final critical result is true"),
                "An ancestral blade whose verdict outlives every wielder.",
                List.of(rule(
                        "grandfather",
                        "critical_damage",
                        DamageRuleRole.OFFENSIVE,
                        DamagePhase.CRITICAL_HIT,
                        List.of(DamageNexusConditions.critical()),
                        List.of(DamageNexusOperations.addGlobalPreMultiplier(
                                DamageNexusPreMultiplierBuckets.CRIT_DAMAGE,
                                0.50F
                        ))
                ))
        );
    }

    private static DamageEntryDefinition harlequinCrest() {
        return entry(
                ObeliskEquipmentIds.HARLEQUIN_CREST,
                "harlequin_crest",
                "Harlequin Crest",
                List.of("10% damage reduction"),
                "A mocking crown that turns mortal blows into hollow threats.",
                List.of(rule(
                        "harlequin_crest",
                        "damage_reduction",
                        DamageRuleRole.DEFENSIVE,
                        DamagePhase.MITIGATION_SETUP,
                        List.of(DamageNexusConditions.always()),
                        List.of(DamageNexusOperations.addGlobalMitigation(0.10F))
                ))
        );
    }

    private static DamageEntryDefinition tyraelsMight() {
        List<DamageRuleOperation> resistances = List.of(
                DamageNexusOperations.addTemporaryResistance(DamageChannel.PHYSICAL_ID, 10.0F),
                DamageNexusOperations.addTemporaryResistance(DamageChannel.FIRE_ID, 10.0F),
                DamageNexusOperations.addTemporaryResistance(DamageChannel.COLD_ID, 10.0F),
                DamageNexusOperations.addTemporaryResistance(DamageChannel.LIGHTNING_ID, 10.0F),
                DamageNexusOperations.addTemporaryResistance(DamageChannel.MAGIC_ID, 10.0F),
                DamageNexusOperations.addTemporaryResistance(DamageChannel.POISON_ID, 10.0F),
                DamageNexusOperations.addTemporaryResistance(DamageChannel.WITHER_ID, 10.0F),
                DamageNexusOperations.addTemporaryResistance(DamageChannel.KINETIC_ID, 10.0F)
        );
        return entry(
                ObeliskEquipmentIds.TYRAELS_MIGHT,
                "tyraels_might",
                "Tyrael's Might",
                List.of(
                        "+10 resistance rating to all supported damage channels",
                        "+4 magic damage while above 99% health"
                ),
                "Justice is heaviest when carried without fear.",
                List.of(
                        rule(
                                "tyraels_might",
                                "channel_resistances",
                                DamageRuleRole.DEFENSIVE,
                                DamagePhase.MITIGATION_SETUP,
                                List.of(DamageNexusConditions.always()),
                                resistances
                        ),
                        rule(
                                "tyraels_might",
                                "high_health_magic_damage",
                                DamageRuleRole.OFFENSIVE,
                                DamagePhase.BASE_MODIFICATION,
                                List.of(DamageNexusConditions.attackerHealthAbove(0.99F)),
                                List.of(DamageNexusOperations.addBaseDamage(
                                        DamageChannel.MAGIC_ID,
                                        DamageApplicationBucket.DN_RULE_BASE,
                                        4.0F
                                ))
                        )
                )
        );
    }

    private static DamageEntryDefinition tibaultsWill() {
        return entry(
                ObeliskEquipmentIds.TIBAULTS_WILL,
                "tibaults_will",
                "Tibault's Will",
                List.of("20% more damage while Unstoppable"),
                "Defiance becomes momentum when restraint falls away.",
                List.of(rule(
                        "tibaults_will",
                        "unstoppable_damage",
                        DamageRuleRole.OFFENSIVE,
                        DamagePhase.CONDITIONAL_MULTI,
                        List.of(DamageNexusConditions.attackerEffectTag(
                                ObeliskEquipmentEffectTags.UNSTOPPABLE_EFFECTS
                        )),
                        List.of(DamageNexusOperations.addGlobalPostMultiplier(0.20F))
                ))
        );
    }

    private static DamageEntryDefinition bloodMoonBreeches() {
        return entry(
                ObeliskEquipmentIds.BLOOD_MOON_BREECHES,
                "blood_moon_breeches",
                "Blood Moon Breeches",
                List.of("20% more damage against Cursed targets"),
                "Beneath a red moon, every curse opens a deeper wound.",
                List.of(rule(
                        "blood_moon_breeches",
                        "cursed_target_damage",
                        DamageRuleRole.OFFENSIVE,
                        DamagePhase.CONDITIONAL_MULTI,
                        List.of(DamageNexusConditions.targetEffectTag(
                                ObeliskEquipmentEffectTags.CURSE_EFFECTS
                        )),
                        List.of(DamageNexusOperations.addGlobalPostMultiplier(0.20F))
                ))
        );
    }

    private static DamageEntryDefinition cowlOfTheNameless() {
        return entry(
                ObeliskEquipmentIds.COWL_OF_THE_NAMELESS,
                "cowl_of_the_nameless",
                "Cowl of the Nameless",
                List.of("15% more damage against Crowd-Controlled targets"),
                "Those stripped of motion soon surrender their names as well.",
                List.of(rule(
                        "cowl_of_the_nameless",
                        "controlled_target_damage",
                        DamageRuleRole.OFFENSIVE,
                        DamagePhase.CONDITIONAL_MULTI,
                        List.of(DamageNexusConditions.targetEffectTag(
                                ObeliskEquipmentEffectTags.CROWD_CONTROLLED_EFFECTS
                        )),
                        List.of(DamageNexusOperations.addGlobalPostMultiplier(0.15F))
                ))
        );
    }

    private static DamageEntryDefinition entry(
            Identifier id,
            String key,
            String name,
            List<String> tooltips,
            String flavor,
            List<DamageRuleDefinition> rules
    ) {
        return new DamageEntryDefinition(
                id,
                display(key, name, tooltips, flavor),
                DamageEntrySlot.ITEM,
                rules,
                DamageEntryStacking.UNIQUE_GROUP,
                Optional.of(ObeliskEquipmentIds.uniqueStacking(key, "entry"))
        );
    }

    private static DamageRuleDefinition rule(
            String equipmentName,
            String ruleName,
            DamageRuleRole role,
            DamagePhase phase,
            List<DamageRuleCondition> conditions,
            List<DamageRuleOperation> operations
    ) {
        Identifier ruleId = ObeliskEquipmentIds.uniqueRule(equipmentName, ruleName);
        return new DamageRuleDefinition(
                ruleId,
                role,
                phase,
                RULE_PRIORITY,
                conditions,
                operations,
                DamageRuleStacking.STACK,
                Optional.of(ObeliskEquipmentIds.uniqueStacking(
                        equipmentName,
                        ruleName
                )),
                Optional.of(ruleId.toString())
        );
    }

    private static DamageEntryDisplay display(
            String key,
            String name,
            List<String> tooltips,
            String flavor
    ) {
        List<DisplayText> tooltipText = new ArrayList<>();
        for (int index = 0; index < tooltips.size(); index++) {
            tooltipText.add(DisplayText.translatableWithFallback(
                    "entry.obeliskdepths.unique." + key + ".tooltip." + index,
                    tooltips.get(index)
            ));
        }
        return new DamageEntryDisplay(
                Optional.of(DisplayText.translatableWithFallback(
                        "entry.obeliskdepths.unique." + key + ".name",
                        name
                )),
                List.copyOf(tooltipText),
                Optional.of(DisplayText.translatableWithFallback(
                        "entry.obeliskdepths.unique." + key + ".flavor",
                        flavor
                )),
                true
        );
    }
}
