package io.github.naimjeg.obeliskdepths.data;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.worldgen.ModBiomes;
import io.github.naimjeg.obeliskdepths.worldgen.ModNoiseSettings;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.List;
import java.util.Set;

@EventBusSubscriber(modid = ObeliskDepths.MOD_ID)
public final class ModDataGenerators {
    private ModDataGenerators() {
    }

    @SubscribeEvent
    public static void gatherClientData(GatherDataEvent.Client event) {
        event.createProvider(ModModelProvider::new);
        event.createProvider(LangEnUsProvider::new);
        event.createProvider(LangZhCnProvider::new);
    }

    @SubscribeEvent
    public static void gatherServerData(GatherDataEvent.Server event) {
        // The server-data entry point constructs GatherDataEvent.Server, while
        // the client-data entry point constructs GatherDataEvent.Client.
        // Datapack registry entries must live here so runData emits runtime
        // biome and noise-settings JSON through the active registry codecs.
        event.createProvider((output, lookupProvider) -> new DatapackBuiltinEntriesProvider(
                output,
                lookupProvider,
                new RegistrySetBuilder()
                        .add(Registries.BIOME, ModBiomes::bootstrap)
                        .add(Registries.NOISE_SETTINGS, ModNoiseSettings::bootstrap),
                Set.of(ObeliskDepths.MOD_ID)
        ));

        event.createProvider(ModBlockTagProvider::new);
        event.createProvider(ModItemTagProvider::new);
        event.createProvider(ModMobEffectTagProvider::new);
        event.createProvider(ModRecipeProvider.Runner::new);
        event.createProvider(DungeonRoomDefinitionProvider::new);
        event.createProvider(DungeonCorridorDefinitionProvider::new);
        event.createProvider(DungeonThemeDefinitionProvider::new);

        event.createProvider((output, lookupProvider) -> new LootTableProvider(
                output,
                Set.of(),
                List.of(
                        new LootTableProvider.SubProviderEntry(
                                ModBlockLootProvider::new,
                                LootContextParamSets.BLOCK
                        )
                ),
                lookupProvider
        ));
    }
}
