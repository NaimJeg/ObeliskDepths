package io.github.naimjeg.obeliskdepths.registry;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;

public final class ModWoodTypes {
    private ModWoodTypes() {
    }

    public static final BlockSetType GREAT_SWAMP_TAXODIUM_SET =
            BlockSetType.register(new BlockSetType(ObeliskDepths.MOD_ID + ":great_swamp_taxodium"));

    public static final WoodType GREAT_SWAMP_TAXODIUM =
            WoodType.register(new WoodType(
                    ObeliskDepths.MOD_ID + ":great_swamp_taxodium",
                    GREAT_SWAMP_TAXODIUM_SET
            ));
}
