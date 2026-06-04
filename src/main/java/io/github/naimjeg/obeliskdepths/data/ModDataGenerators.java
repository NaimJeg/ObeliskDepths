package io.github.naimjeg.obeliskdepths.data;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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
        event.createProvider(ModBlockTagProvider::new);
        event.createProvider(ModItemTagProvider::new);
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

    @SubscribeEvent
    public static void gatherServerData(GatherDataEvent.Server event) {
        /*  NEVER NEVER NEVER NEVER USE THIS EVENT
        *   Neoforge API separated the event
        *   but the separated firing will delete resources generated from the other event
        *   Only use one event could collect all the resource
        * */
    }
}
