package io.github.naimjeg.obeliskdepths.worldgen;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.SurfaceRuleData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.*;

import java.util.List;

/**
 * Project-owned noise settings for the Obelisk Depths dimension.
 *
 * <p>Phase 1 uses a minimal bounded box router: a flat floor at the nominal cavern floor and a
 * flat ceiling at the nominal cavern ceiling. The result is an enclosed cavern with no dependency
 * on Overworld continents, erosion, ridges, cave entrances, noodle/spaghetti caves, aquifers, or
 * ore veins. This router is intentionally simple so it can be cleanly replaced by the later custom
 * cavern density router.</p>
 */
public final class ModNoiseSettings {
    private ModNoiseSettings() {
    }

    public static final ResourceKey<NoiseGeneratorSettings> OBLISK_DEPTHS = ResourceKey.create(
            Registries.NOISE_SETTINGS,
            Identifier.fromNamespaceAndPath(ObeliskDepths.MOD_ID, "obelisk_depths")
    );

    public static void bootstrap(BootstrapContext<NoiseGeneratorSettings> context) {
        context.register(OBLISK_DEPTHS, obeliskDepths());
    }

    private static NoiseGeneratorSettings obeliskDepths() {
        DensityFunction zero = DensityFunctions.zero();

        // Floor: solid below NOMINAL_FLOOR_Y, air above.
        DensityFunction floor = DensityFunctions.yClampedGradient(
                GreatSwampCavernProfile.NOMINAL_FLOOR_Y,
                GreatSwampCavernProfile.NOMINAL_FLOOR_Y + 1,
                1.0,
                -1.0
        );

        // Ceiling: air below NOMINAL_CEILING_Y, solid above.
        DensityFunction ceiling = DensityFunctions.yClampedGradient(
                GreatSwampCavernProfile.NOMINAL_CEILING_Y - 1,
                GreatSwampCavernProfile.NOMINAL_CEILING_Y,
                -1.0,
                1.0
        );

        // Positive final density is solid; negative is air. This gives a cavern band between the
        // floor and ceiling with stone above and below.
        DensityFunction finalDensity = DensityFunctions.max(floor, ceiling);

        NoiseRouter router = new NoiseRouter(
                zero, zero, zero, zero, zero, zero, zero, zero, zero, zero, zero,
                finalDensity,
                zero, zero, zero
        );

        return new NoiseGeneratorSettings(
                NoiseSettings.create(
                        GreatSwampCavernProfile.MIN_Y,
                        GreatSwampCavernProfile.HEIGHT,
                        1,
                        2
                ),
                Blocks.STONE.defaultBlockState(),
                Blocks.WATER.defaultBlockState(),
                router,
                SurfaceRuleData.air(),
                List.of(),
                0,
                true,
                false,
                false,
                false
        );
    }
}
