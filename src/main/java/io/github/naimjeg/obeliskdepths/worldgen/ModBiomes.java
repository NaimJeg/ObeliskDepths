package io.github.naimjeg.obeliskdepths.worldgen;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;

/**
 * Project-owned biome registrations for the Obelisk Depths dimension.
 *
 * <p>Phase 1 intentionally builds the Great Swamp biome from an empty authored definition rather
 * than copying vanilla swamp generation, mobs, or effects. The visual identity is preserved with
 * explicit project-owned colors and attributes.</p>
 */
public final class ModBiomes {
    private ModBiomes() {
    }

    public static final ResourceKey<Biome> GREAT_SWAMP = ResourceKey.create(
            Registries.BIOME,
            Identifier.fromNamespaceAndPath(ObeliskDepths.MOD_ID, "great_swamp")
    );

    public static void bootstrap(BootstrapContext<Biome> context) {
        context.register(GREAT_SWAMP, greatSwamp());
    }

    private static Biome greatSwamp() {
        // Phase 1: no project-specific mob design exists yet, so use an explicit empty spawn table.
        // This documents the decision and prevents accidental inheritance of vanilla swamp spawns.
        MobSpawnSettings spawns = new MobSpawnSettings.Builder()
                .creatureGenerationProbability(0.0f)
                .build();

        BiomeSpecialEffects effects = new BiomeSpecialEffects.Builder()
                .waterColor(0x3a5a52)
                .foliageColorOverride(0x4a6b3d)
                .dryFoliageColorOverride(0x5a4a3a)
                .build();

        EnvironmentAttributeMap attributes = EnvironmentAttributeMap.builder()
                .set(EnvironmentAttributes.FOG_COLOR, 0x0c1410)
                .set(EnvironmentAttributes.SKY_COLOR, 0x050807)
                .set(EnvironmentAttributes.WATER_FOG_COLOR, 0x12221d)
                .set(EnvironmentAttributes.WATER_FOG_END_DISTANCE, 80.0f)
                .build();

        return new Biome.BiomeBuilder()
                .hasPrecipitation(true)
                .temperature(0.8f)
                .downfall(0.9f)
                .specialEffects(effects)
                .mobSpawnSettings(spawns)
                .generationSettings(BiomeGenerationSettings.EMPTY)
                .putAttributes(attributes)
                .build();
    }
}
