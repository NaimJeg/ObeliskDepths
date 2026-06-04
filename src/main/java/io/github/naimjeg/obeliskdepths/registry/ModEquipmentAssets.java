package io.github.naimjeg.obeliskdepths.registry;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/** Equipment asset IDs owned by Obelisk Depths. */
public final class ModEquipmentAssets {
    public static final ResourceKey<EquipmentAsset> EXILE =
            ResourceKey.create(
                    EquipmentAssets.ROOT_ID,
                    Identifier.fromNamespaceAndPath(
                            ObeliskDepths.MOD_ID,
                            "exile"
                    )
            );

    private ModEquipmentAssets() {
    }
}
