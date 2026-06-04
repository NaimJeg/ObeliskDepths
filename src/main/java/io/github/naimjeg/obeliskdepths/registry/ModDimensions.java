package io.github.naimjeg.obeliskdepths.registry;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;

public final class ModDimensions {
    private ModDimensions() {
    }

    public static final ResourceKey<Level> OBELISK_DEPTHS_LEVEL =
            ResourceKey.create(
                    Registries.DIMENSION,
                    Identifier.fromNamespaceAndPath(ObeliskDepths.MOD_ID, "obelisk_depths")
            );

    public static final ResourceKey<LevelStem> OBELISK_DEPTHS_STEM =
            ResourceKey.create(
                    Registries.LEVEL_STEM,
                    Identifier.fromNamespaceAndPath(ObeliskDepths.MOD_ID, "obelisk_depths")
            );

    public static final ResourceKey<DimensionType> OBELISK_DEPTHS_TYPE =
            ResourceKey.create(
                    Registries.DIMENSION_TYPE,
                    Identifier.fromNamespaceAndPath(ObeliskDepths.MOD_ID, "obelisk_depths")
            );
}