package io.github.naimjeg.obeliskdepths.equipment;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import net.minecraft.resources.Identifier;

/** The single persistent ID directory for ObeliskDepths equipment content. */
public final class ObeliskEquipmentIds {
    public static final Identifier TEMPERED = template("tempered");
    public static final Identifier BRUTAL = template("brutal");
    public static final Identifier RAZOR_EDGED = template("razor_edged");
    public static final Identifier PIERCING = template("piercing");
    public static final Identifier SUNDERING = template("sundering");
    public static final Identifier DEADLY = template("deadly");
    public static final Identifier CRITICAL_EDGE = template("critical_edge");
    public static final Identifier AMBUSHERS = template("ambushers");
    public static final Identifier EXECUTIONERS = template("executioners");
    public static final Identifier GIANT_SLAYERS = template("giant_slayers");
    public static final Identifier FLAMING = template("flaming");
    public static final Identifier FIRE_EDGE = template("fire_edge");
    public static final Identifier FLAMEFORGED = template("flameforged");
    public static final Identifier SMOLDERING = template("smoldering");
    public static final Identifier FROSTBOUND = template("frostbound");
    public static final Identifier FROSTFORGED = template("frostforged");
    public static final Identifier STORMCHARGED = template("stormcharged");
    public static final Identifier STORMFORGED = template("stormforged");
    public static final Identifier IMPACTING = template("impacting");
    public static final Identifier ARCANE = template("arcane");
    public static final Identifier SPELLBLADE = template("spellblade");
    public static final Identifier VENOMOUS = template("venomous");
    public static final Identifier TOXIC_EDGE = template("toxic_edge");
    public static final Identifier WITHERING = template("withering");

    public static final Identifier GRANDFATHER = unique("grandfather");
    public static final Identifier HARLEQUIN_CREST = unique("harlequin_crest");
    public static final Identifier TYRAELS_MIGHT = unique("tyraels_might");
    public static final Identifier TIBAULTS_WILL = unique("tibaults_will");
    public static final Identifier BLOOD_MOON_BREECHES = unique("blood_moon_breeches");
    public static final Identifier COWL_OF_THE_NAMELESS = unique("cowl_of_the_nameless");

    private ObeliskEquipmentIds() {
    }

    public static Identifier template(String name) {
        return id("tempering/" + name);
    }

    public static Identifier rule(String templateName, String ruleName) {
        return id("tempering/" + templateName + "/" + ruleName);
    }

    public static Identifier stacking(String name) {
        return id("tempering/stacking/" + name);
    }

    public static Identifier unique(String name) {
        return id("unique/" + name);
    }

    public static Identifier uniqueRule(String equipmentName, String ruleName) {
        return id("unique/" + equipmentName + "/" + ruleName);
    }

    public static Identifier uniqueStacking(String equipmentName, String ruleName) {
        return id("unique/stacking/" + equipmentName + "/" + ruleName);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ObeliskDepths.MOD_ID, path);
    }
}
