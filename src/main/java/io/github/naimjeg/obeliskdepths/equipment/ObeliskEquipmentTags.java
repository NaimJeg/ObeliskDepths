package io.github.naimjeg.obeliskdepths.equipment;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/** Explicit item admission tags used by the server-side tempering gate. */
public final class ObeliskEquipmentTags {
    public static final TagKey<Item> WEAPONS = create("equipment/weapons");
    public static final TagKey<Item> ALL = create("equipment/all");
    public static final TagKey<Item> ARMOR_HEAD = create("equipment/armor_head");
    public static final TagKey<Item> ARMOR_CHEST = create("equipment/armor_chest");
    public static final TagKey<Item> ARMOR_LEGS = create("equipment/armor_legs");
    public static final TagKey<Item> ARMOR_FEET = create("equipment/armor_feet");

    private ObeliskEquipmentTags() {
    }

    public static TagKey<Item> tag(ObeliskEquipmentSlot slot) {
        return switch (slot) {
            case WEAPON -> WEAPONS;
            case ARMOR_HEAD -> ARMOR_HEAD;
            case ARMOR_CHEST -> ARMOR_CHEST;
            case ARMOR_LEGS -> ARMOR_LEGS;
            case ARMOR_FEET -> ARMOR_FEET;
        };
    }

    private static TagKey<Item> create(String path) {
        return TagKey.create(
                Registries.ITEM,
                Identifier.fromNamespaceAndPath(ObeliskDepths.MOD_ID, path)
        );
    }
}
