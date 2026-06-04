package io.github.naimjeg.obeliskdepths.registry;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;

import java.util.Map;

/**
 * Armor material definitions owned by Obelisk Depths.
 */
public final class ModArmorMaterials {
    /**
     * A placeholder tier between vanilla iron and diamond armor.
     *
     * <p>The iron equipment asset is intentional until the Exile texture set
     * exists. Replacing {@link EquipmentAssets#IRON} with a mod equipment asset
     * later will not require changing the registered items.</p>
     */
    public static final ArmorMaterial EXILE = new ArmorMaterial(
            24,
            Map.of(
                    ArmorType.BOOTS, 2,
                    ArmorType.LEGGINGS, 6,
                    ArmorType.CHESTPLATE, 7,
                    ArmorType.HELMET, 3,
                    ArmorType.BODY, 8
            ),
            10,
            SoundEvents.ARMOR_EQUIP_IRON,
            1.0F,
            0.0F,
            ItemTags.REPAIRS_IRON_ARMOR,
            EquipmentAssets.IRON
    );

    private ModArmorMaterials() {
    }
}
