package io.github.naimjeg.obeliskdepths.tempering;

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
import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.resources.Identifier;

public final class ObeliskTemperingEntryFactory {
    public static final Identifier TEMPERED = id("tempering/tempered");
    public static final Identifier BRUTAL = id("tempering/brutal");
    public static final Identifier RAZOR_EDGED = id("tempering/razor_edged");
    public static final Identifier PIERCING = id("tempering/piercing");
    public static final Identifier SUNDERING = id("tempering/sundering");
    public static final Identifier EXECUTIONERS = id("tempering/executioners");
    public static final Identifier FLAMING = id("tempering/flaming");
    public static final Identifier FLAMEFORGED = id("tempering/flameforged");
    public static final Identifier SMOLDERING = id("tempering/smoldering");
    public static final Identifier FROSTBOUND = id("tempering/frostbound");
    public static final Identifier FROSTFORGED = id("tempering/frostforged");
    public static final Identifier STORMCHARGED = id("tempering/stormcharged");
    public static final Identifier STORMFORGED = id("tempering/stormforged");
    public static final Identifier IMPACTING = id("tempering/impacting");
    public static final Identifier ARCANE = id("tempering/arcane");
    public static final Identifier SPELLBLADE = id("tempering/spellblade");
    public static final Identifier VENOMOUS = id("tempering/venomous");
    public static final Identifier TOXIC_EDGE = id("tempering/toxic_edge");
    public static final Identifier WITHERING = id("tempering/withering");
    public static final Identifier DEADLY = id("tempering/deadly");
    public static final Identifier AMBUSHERS = id("tempering/ambushers");
    public static final Identifier GIANT_SLAYERS = id("tempering/giant_slayers");

    public static final Identifier FIRE_TEMPERING_ENTRY =
            id("tempering/fire_edge");
    public static final Identifier CRIT_TEMPERING_ENTRY =
            id("tempering/critical_edge");

    private static final Identifier DN_GENERIC_DAMAGE_BUCKET =
            dn("generic_damage");
    private static final Identifier DN_PHYSICAL_DAMAGE_BUCKET =
            dn("physical_damage");
    private static final Identifier DN_FIRE_DAMAGE_BUCKET =
            dn("fire_damage");
    private static final Identifier DN_CRIT_DAMAGE_BUCKET =
            dn("crit_damage");

    private static final Map<Identifier, Supplier<DamageEntryDefinition>>
            BUILT_IN_FACTORIES = builtInFactories();

    private ObeliskTemperingEntryFactory() {
    }

    public static DamageEntryDefinition createFireTemperingEntry() {
        return createById(FIRE_TEMPERING_ENTRY).orElseThrow();
    }

    public static DamageEntryDefinition createCritTemperingEntry() {
        return createById(CRIT_TEMPERING_ENTRY).orElseThrow();
    }

    public static List<Identifier> builtInEntryIds() {
        return List.copyOf(BUILT_IN_FACTORIES.keySet());
    }

    public static List<DamageEntryDefinition> createBuiltInEntries() {
        List<DamageEntryDefinition> entries = new ArrayList<>();

        for (Identifier id : BUILT_IN_FACTORIES.keySet()) {
            entries.add(createById(id).orElseThrow());
        }

        return List.copyOf(entries);
    }

    public static Optional<DamageEntryDefinition> createById(
            Identifier entryId
    ) {
        Supplier<DamageEntryDefinition> factory =
                BUILT_IN_FACTORIES.get(entryId);

        if (factory == null) {
            return Optional.empty();
        }

        return Optional.of(factory.get());
    }

    private static Map<Identifier, Supplier<DamageEntryDefinition>>
    builtInFactories() {
        Map<Identifier, Supplier<DamageEntryDefinition>> result =
                new LinkedHashMap<>();

        register(result, TEMPERED, ObeliskTemperingEntryFactory::tempered);
        register(result, BRUTAL, ObeliskTemperingEntryFactory::brutal);
        register(result, RAZOR_EDGED, ObeliskTemperingEntryFactory::razorEdged);
        register(result, PIERCING, ObeliskTemperingEntryFactory::piercing);
        register(result, SUNDERING, ObeliskTemperingEntryFactory::sundering);
        register(result, EXECUTIONERS, ObeliskTemperingEntryFactory::executioners);
        register(result, FLAMING, ObeliskTemperingEntryFactory::flaming);
        register(result, FLAMEFORGED, ObeliskTemperingEntryFactory::flameforged);
        register(result, SMOLDERING, ObeliskTemperingEntryFactory::smoldering);
        register(result, FROSTBOUND, ObeliskTemperingEntryFactory::frostbound);
        register(result, FROSTFORGED, ObeliskTemperingEntryFactory::frostforged);
        register(result, STORMCHARGED, ObeliskTemperingEntryFactory::stormcharged);
        register(result, STORMFORGED, ObeliskTemperingEntryFactory::stormforged);
        register(result, IMPACTING, ObeliskTemperingEntryFactory::impacting);
        register(result, ARCANE, ObeliskTemperingEntryFactory::arcane);
        register(result, SPELLBLADE, ObeliskTemperingEntryFactory::spellblade);
        register(result, VENOMOUS, ObeliskTemperingEntryFactory::venomous);
        register(result, TOXIC_EDGE, ObeliskTemperingEntryFactory::toxicEdge);
        register(result, WITHERING, ObeliskTemperingEntryFactory::withering);
        register(result, DEADLY, ObeliskTemperingEntryFactory::deadly);
        register(result, AMBUSHERS, ObeliskTemperingEntryFactory::ambushers);
        register(result, GIANT_SLAYERS, ObeliskTemperingEntryFactory::giantSlayers);

        register(result, FIRE_TEMPERING_ENTRY, ObeliskTemperingEntryFactory::legacyFireEdge);
        register(result, CRIT_TEMPERING_ENTRY, ObeliskTemperingEntryFactory::legacyCriticalEdge);

        return Collections.unmodifiableMap(result);
    }

    private static void register(
            Map<Identifier, Supplier<DamageEntryDefinition>> factories,
            Identifier id,
            Supplier<DamageEntryDefinition> factory
    ) {
        if (factories.put(id, factory) != null) {
            throw new IllegalStateException(
                    "Duplicate built-in Obelisk tempering entry id: " + id
            );
        }
    }

    private static DamageEntryDefinition tempered() {
        return entry(
                TEMPERED,
                "tempered",
                "Tempered",
                List.of("+3 physical damage"),
                "A steady strike mark with no fragile condition.",
                List.of(flatDamage(
                        "tempered",
                        "flat_physical",
                        DamageChannel.PHYSICAL_ID,
                        3.0F,
                        "Obelisk Tempering: +3 Physical"
                )),
                "tempered"
        );
    }

    private static DamageEntryDefinition brutal() {
        return entry(
                BRUTAL,
                "brutal",
                "Brutal",
                List.of("+10% global damage"),
                "A blunt force mark that pushes every hit harder.",
                List.of(globalMultiplier(
                        "brutal",
                        "global_damage",
                        DamagePhase.GLOBAL_ADJUSTMENT,
                        always(),
                        DN_GENERIC_DAMAGE_BUCKET,
                        0.10F,
                        "Obelisk Tempering: +10% Global Damage"
                )),
                "brutal"
        );
    }

    private static DamageEntryDefinition razorEdged() {
        return entry(
                RAZOR_EDGED,
                "razor_edged",
                "Razor Edged",
                List.of("+12% physical damage"),
                "A clean edge mark for weapons that solve problems directly.",
                List.of(channelMultiplier(
                        "razor_edged",
                        "physical_damage",
                        DamageChannel.PHYSICAL_ID,
                        DN_PHYSICAL_DAMAGE_BUCKET,
                        0.12F,
                        DamagePhase.TYPE_SCALING,
                        always(),
                        "Obelisk Tempering: +12% Physical"
                )),
                "razor_edged"
        );
    }

    private static DamageEntryDefinition piercing() {
        return entry(
                PIERCING,
                "piercing",
                "Piercing",
                List.of("+1.5 physical true damage"),
                "A narrow point of force that slips past mitigation.",
                List.of(trueDamage(
                        "piercing",
                        "physical_true_damage",
                        DamageChannel.PHYSICAL_ID,
                        1.5F,
                        "Obelisk Tempering: +1.5 Physical True Damage"
                )),
                "piercing"
        );
    }

    private static DamageEntryDefinition sundering() {
        return entry(
                SUNDERING,
                "sundering",
                "Sundering",
                List.of("-12% target armor effectiveness"),
                "A breaker mark that makes armor answer less loudly.",
                List.of(armorEffectiveness(
                        "sundering",
                        "armor_effectiveness",
                        0.88F,
                        "Obelisk Tempering: 88% Armor Effectiveness"
                )),
                "sundering"
        );
    }

    private static DamageEntryDefinition executioners() {
        return entry(
                EXECUTIONERS,
                "executioners",
                "Executioner's",
                List.of("+20% physical damage below 35% target health"),
                "A finishing mark that leans into a weakened enemy.",
                List.of(channelMultiplier(
                        "executioners",
                        "low_health_physical",
                        DamageChannel.PHYSICAL_ID,
                        DN_PHYSICAL_DAMAGE_BUCKET,
                        0.20F,
                        DamagePhase.CONDITIONAL_MULTI,
                        List.of(DamageNexusConditions.targetHealthBelow(0.35F)),
                        "Obelisk Tempering: +20% Physical vs Low Health"
                )),
                "executioners"
        );
    }

    private static DamageEntryDefinition flaming() {
        return entry(
                FLAMING,
                "flaming",
                "Flaming",
                List.of("+3 fire damage"),
                "A direct ember mark that adds reliable fire damage.",
                List.of(flatDamage(
                        "flaming",
                        "flat_fire",
                        DamageChannel.FIRE_ID,
                        3.0F,
                        "Obelisk Tempering: +3 Fire"
                )),
                "flaming"
        );
    }

    private static DamageEntryDefinition flameforged() {
        return entry(
                FLAMEFORGED,
                "flameforged",
                "Flameforged",
                List.of("Converts 20% physical damage to fire"),
                "A furnace-born mark that changes part of the blade's bite.",
                List.of(convertDamage(
                        "flameforged",
                        "physical_to_fire",
                        DamageChannel.PHYSICAL_ID,
                        DamageChannel.FIRE_ID,
                        0.20F,
                        "Obelisk Tempering: 20% Physical to Fire"
                )),
                "flameforged"
        );
    }

    private static DamageEntryDefinition smoldering() {
        return entry(
                SMOLDERING,
                "smoldering",
                "Smoldering",
                List.of("+15% global damage against burning targets"),
                "A patient heat mark that rewards keeping enemies burning.",
                List.of(globalMultiplier(
                        "smoldering",
                        "burning_target_damage",
                        DamagePhase.CONDITIONAL_MULTI,
                        List.of(DamageNexusConditions.targetOnFire()),
                        DN_GENERIC_DAMAGE_BUCKET,
                        0.15F,
                        "Obelisk Tempering: +15% vs Burning"
                )),
                "smoldering"
        );
    }

    private static DamageEntryDefinition frostbound() {
        return entry(
                FROSTBOUND,
                "frostbound",
                "Frostbound",
                List.of("+3 cold damage"),
                "A cold mark that lays winter into the weapon's edge.",
                List.of(flatDamage(
                        "frostbound",
                        "flat_cold",
                        DamageChannel.COLD_ID,
                        3.0F,
                        "Obelisk Tempering: +3 Cold"
                )),
                "frostbound"
        );
    }

    private static DamageEntryDefinition frostforged() {
        return entry(
                FROSTFORGED,
                "frostforged",
                "Frostforged",
                List.of("Converts 20% physical damage to cold"),
                "A pale forge mark that turns impact into chill.",
                List.of(convertDamage(
                        "frostforged",
                        "physical_to_cold",
                        DamageChannel.PHYSICAL_ID,
                        DamageChannel.COLD_ID,
                        0.20F,
                        "Obelisk Tempering: 20% Physical to Cold"
                )),
                "frostforged"
        );
    }

    private static DamageEntryDefinition stormcharged() {
        return entry(
                STORMCHARGED,
                "stormcharged",
                "Stormcharged",
                List.of("+3 lightning damage"),
                "A charged mark that snaps through the strike.",
                List.of(flatDamage(
                        "stormcharged",
                        "flat_lightning",
                        DamageChannel.LIGHTNING_ID,
                        3.0F,
                        "Obelisk Tempering: +3 Lightning"
                )),
                "stormcharged"
        );
    }

    private static DamageEntryDefinition stormforged() {
        return entry(
                STORMFORGED,
                "stormforged",
                "Stormforged",
                List.of("Converts 18% physical damage to lightning"),
                "A storm mark that turns force into a hard flash.",
                List.of(convertDamage(
                        "stormforged",
                        "physical_to_lightning",
                        DamageChannel.PHYSICAL_ID,
                        DamageChannel.LIGHTNING_ID,
                        0.18F,
                        "Obelisk Tempering: 18% Physical to Lightning"
                )),
                "stormforged"
        );
    }

    private static DamageEntryDefinition impacting() {
        return entry(
                IMPACTING,
                "impacting",
                "Impacting",
                List.of("+2.5 kinetic damage"),
                "A concussive mark that adds blunt momentum.",
                List.of(flatDamage(
                        "impacting",
                        "flat_kinetic",
                        DamageChannel.KINETIC_ID,
                        2.5F,
                        "Obelisk Tempering: +2.5 Kinetic"
                )),
                "impacting"
        );
    }

    private static DamageEntryDefinition arcane() {
        return entry(
                ARCANE,
                "arcane",
                "Arcane",
                List.of("+3 magic damage"),
                "A focused sigil that threads magic through the hit.",
                List.of(flatDamage(
                        "arcane",
                        "flat_magic",
                        DamageChannel.MAGIC_ID,
                        3.0F,
                        "Obelisk Tempering: +3 Magic"
                )),
                "arcane"
        );
    }

    private static DamageEntryDefinition spellblade() {
        return entry(
                SPELLBLADE,
                "spellblade",
                "Spellblade",
                List.of("Gain 15% physical damage as magic"),
                "A blade-and-sigil mark that echoes force as magic.",
                List.of(gainExtraDamage(
                        "spellblade",
                        "physical_to_magic_bonus",
                        DamageChannel.PHYSICAL_ID,
                        DamageChannel.MAGIC_ID,
                        0.15F,
                        "Obelisk Tempering: 15% Physical as Magic"
                )),
                "spellblade"
        );
    }

    private static DamageEntryDefinition venomous() {
        return entry(
                VENOMOUS,
                "venomous",
                "Venomous",
                List.of("+3 poison damage"),
                "A toxin mark that leaves a bitter cut.",
                List.of(flatDamage(
                        "venomous",
                        "flat_poison",
                        DamageChannel.POISON_ID,
                        3.0F,
                        "Obelisk Tempering: +3 Poison"
                )),
                "venomous"
        );
    }

    private static DamageEntryDefinition toxicEdge() {
        return entry(
                TOXIC_EDGE,
                "toxic_edge",
                "Toxic Edge",
                List.of("Gain 15% physical damage as poison"),
                "A coated-edge mark that makes clean cuts turn toxic.",
                List.of(gainExtraDamage(
                        "toxic_edge",
                        "physical_to_poison_bonus",
                        DamageChannel.PHYSICAL_ID,
                        DamageChannel.POISON_ID,
                        0.15F,
                        "Obelisk Tempering: 15% Physical as Poison"
                )),
                "toxic_edge"
        );
    }

    private static DamageEntryDefinition withering() {
        return entry(
                WITHERING,
                "withering",
                "Withering",
                List.of("+2 wither damage"),
                "A fading mark that carries a dry, ruinous bite.",
                List.of(flatDamage(
                        "withering",
                        "flat_wither",
                        DamageChannel.WITHER_ID,
                        2.0F,
                        "Obelisk Tempering: +2 Wither"
                )),
                "withering"
        );
    }

    private static DamageEntryDefinition deadly() {
        return entry(
                DEADLY,
                "deadly",
                "Deadly",
                List.of("+20% physical damage on critical hits"),
                "A precise mark that rewards decisive timing.",
                List.of(channelMultiplier(
                        "deadly",
                        "critical_physical",
                        DamageChannel.PHYSICAL_ID,
                        DN_CRIT_DAMAGE_BUCKET,
                        0.20F,
                        DamagePhase.CRITICAL_HIT,
                        List.of(DamageNexusConditions.critical()),
                        "Obelisk Tempering: +20% Physical Critical Damage"
                )),
                "deadly"
        );
    }

    private static DamageEntryDefinition ambushers() {
        return entry(
                AMBUSHERS,
                "ambushers",
                "Ambusher's",
                List.of("+18% global damage above 80% target health"),
                "An opening-strike mark made for the first clean hit.",
                List.of(globalMultiplier(
                        "ambushers",
                        "high_health_target",
                        DamagePhase.CONDITIONAL_MULTI,
                        List.of(DamageNexusConditions.targetHealthAbove(0.80F)),
                        DN_GENERIC_DAMAGE_BUCKET,
                        0.18F,
                        "Obelisk Tempering: +18% vs High Health"
                )),
                "ambushers"
        );
    }

    private static DamageEntryDefinition giantSlayers() {
        return entry(
                GIANT_SLAYERS,
                "giant_slayers",
                "Giant Slayer's",
                List.of("+20% global damage against bosses"),
                "A hunter's mark built for targets that should not stand.",
                List.of(globalMultiplier(
                        "giant_slayers",
                        "boss_damage",
                        DamagePhase.CONDITIONAL_MULTI,
                        List.of(DamageNexusConditions.targetIsBoss()),
                        DN_GENERIC_DAMAGE_BUCKET,
                        0.20F,
                        "Obelisk Tempering: +20% vs Bosses"
                )),
                "giant_slayers"
        );
    }

    private static DamageEntryDefinition legacyFireEdge() {
        return entry(
                FIRE_TEMPERING_ENTRY,
                "fire_edge",
                "Fire Edge",
                List.of("+4 fire damage", "+15% fire damage"),
                "A tempering mark that burns through the weapon edge.",
                List.of(
                        flatDamage(
                                "fire_edge",
                                "base_fire",
                                DamageChannel.FIRE_ID,
                                4.0F,
                                "Obelisk Tempering: +4 Fire"
                        ),
                        channelMultiplier(
                                "fire_edge",
                                "fire_scaling",
                                DamageChannel.FIRE_ID,
                                DN_FIRE_DAMAGE_BUCKET,
                                0.15F,
                                DamagePhase.TYPE_SCALING,
                                always(),
                                "Obelisk Tempering: +15% Fire"
                        )
                ),
                "fire"
        );
    }

    private static DamageEntryDefinition legacyCriticalEdge() {
        return entry(
                CRIT_TEMPERING_ENTRY,
                "critical_edge",
                "Critical Edge",
                List.of("+20% physical damage on critical hits"),
                "A tempering mark that rewards clean decisive strikes.",
                List.of(channelMultiplier(
                        "critical_edge",
                        "crit_physical_scaling",
                        DamageChannel.PHYSICAL_ID,
                        DN_CRIT_DAMAGE_BUCKET,
                        0.20F,
                        DamagePhase.CRITICAL_HIT,
                        List.of(DamageNexusConditions.critical()),
                        "Obelisk Tempering: +20% Physical Critical Damage"
                )),
                "critical"
        );
    }

    private static DamageEntryDefinition entry(
            Identifier id,
            String displayKey,
            String fallbackName,
            List<String> fallbackTooltip,
            String fallbackFlavor,
            List<DamageRuleDefinition> rules,
            String stackingGroup
    ) {
        return new DamageEntryDefinition(
                id,
                display(displayKey, fallbackName, fallbackTooltip, fallbackFlavor),
                DamageEntrySlot.WEAPON,
                rules,
                DamageEntryStacking.UNIQUE_GROUP,
                Optional.of(id("tempering/stacking/" + stackingGroup))
        );
    }

    private static DamageRuleDefinition flatDamage(
            String entryKey,
            String ruleKey,
            Identifier channel,
            float value,
            String traceLabel
    ) {
        return rule(
                entryKey,
                ruleKey,
                DamagePhase.BASE_MODIFICATION,
                520,
                always(),
                List.of(DamageNexusOperations.addBaseDamage(
                        channel,
                        DamageApplicationBucket.DN_RULE_BASE,
                        value
                )),
                traceLabel
        );
    }

    private static DamageRuleDefinition trueDamage(
            String entryKey,
            String ruleKey,
            Identifier channel,
            float value,
            String traceLabel
    ) {
        return rule(
                entryKey,
                ruleKey,
                DamagePhase.BASE_MODIFICATION,
                515,
                always(),
                List.of(DamageNexusOperations.addTrueDamage(channel, value)),
                traceLabel
        );
    }

    private static DamageRuleDefinition convertDamage(
            String entryKey,
            String ruleKey,
            Identifier from,
            Identifier to,
            float ratio,
            String traceLabel
    ) {
        return rule(
                entryKey,
                ruleKey,
                DamagePhase.TYPE_SCALING,
                500,
                always(),
                List.of(DamageNexusOperations.convertDamage(from, to, ratio)),
                traceLabel
        );
    }

    private static DamageRuleDefinition gainExtraDamage(
            String entryKey,
            String ruleKey,
            Identifier basedOn,
            Identifier to,
            float ratio,
            String traceLabel
    ) {
        return rule(
                entryKey,
                ruleKey,
                DamagePhase.TYPE_SCALING,
                500,
                always(),
                List.of(DamageNexusOperations.gainExtraDamage(
                        basedOn,
                        to,
                        ratio
                )),
                traceLabel
        );
    }

    private static DamageRuleDefinition channelMultiplier(
            String entryKey,
            String ruleKey,
            Identifier channel,
            Identifier bucket,
            float value,
            DamagePhase phase,
            List<DamageRuleCondition> conditions,
            String traceLabel
    ) {
        return rule(
                entryKey,
                ruleKey,
                phase,
                510,
                conditions,
                List.of(DamageNexusOperations.addChannelPreMultiplier(
                        channel,
                        bucket,
                        value
                )),
                traceLabel
        );
    }

    private static DamageRuleDefinition globalMultiplier(
            String entryKey,
            String ruleKey,
            DamagePhase phase,
            List<DamageRuleCondition> conditions,
            Identifier bucket,
            float value,
            String traceLabel
    ) {
        return rule(
                entryKey,
                ruleKey,
                phase,
                505,
                conditions,
                List.of(DamageNexusOperations.addGlobalPreMultiplier(
                        bucket,
                        value
                )),
                traceLabel
        );
    }

    private static DamageRuleDefinition armorEffectiveness(
            String entryKey,
            String ruleKey,
            float multiplier,
            String traceLabel
    ) {
        return rule(
                entryKey,
                ruleKey,
                DamagePhase.MITIGATION_SETUP,
                500,
                always(),
                List.of(DamageNexusOperations.multiplyArmorEffectiveness(
                        multiplier
                )),
                traceLabel
        );
    }

    private static DamageRuleDefinition rule(
            String entryKey,
            String ruleKey,
            DamagePhase phase,
            int priority,
            List<DamageRuleCondition> conditions,
            List<DamageRuleOperation> operations,
            String traceLabel
    ) {
        return new DamageRuleDefinition(
                id("tempering/" + entryKey + "/" + ruleKey),
                DamageRuleRole.OFFENSIVE,
                phase,
                priority,
                conditions,
                operations,
                DamageRuleStacking.STACK,
                Optional.of(id("tempering/rule/" + entryKey + "/" + ruleKey)),
                Optional.of(traceLabel)
        );
    }

    private static DamageEntryDisplay display(
            String key,
            String fallbackName,
            List<String> fallbackTooltip,
            String fallbackFlavor
    ) {
        List<DisplayText> tooltip = new ArrayList<>();

        for (int i = 0; i < fallbackTooltip.size(); i++) {
            tooltip.add(DisplayText.translatableWithFallback(
                    langKey(key, "tooltip." + i),
                    fallbackTooltip.get(i)
            ));
        }

        return new DamageEntryDisplay(
                DisplayText.translatableWithFallback(
                        langKey(key, "name"),
                        fallbackName
                ),
                tooltip,
                Optional.of(DisplayText.translatableWithFallback(
                        langKey(key, "flavor"),
                        fallbackFlavor
                )),
                true
        );
    }

    private static List<DamageRuleCondition> always() {
        return List.of(DamageNexusConditions.always());
    }

    private static String langKey(String key, String suffix) {
        return "entry."
                + ObeliskDepths.MOD_ID
                + "."
                + key
                + "."
                + suffix;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ObeliskDepths.MOD_ID, path);
    }

    private static Identifier dn(String path) {
        return Identifier.fromNamespaceAndPath("damagenexus", path);
    }
}
