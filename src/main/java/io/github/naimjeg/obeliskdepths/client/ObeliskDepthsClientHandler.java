package io.github.naimjeg.obeliskdepths.client;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.registry.ModWoodTypes;
import net.minecraft.client.renderer.Sheets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(
        modid = ObeliskDepths.MOD_ID,
        value = Dist.CLIENT
)
public final class ObeliskDepthsClientHandler {
    private ObeliskDepthsClientHandler() {
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() ->
                Sheets.addWoodType(ModWoodTypes.GREAT_SWAMP_TAXODIUM)
        );
    }
}
