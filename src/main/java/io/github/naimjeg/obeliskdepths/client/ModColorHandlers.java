package io.github.naimjeg.obeliskdepths.client;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.registry.ModBlocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

import java.util.List;

@EventBusSubscriber(
        modid = ObeliskDepths.MOD_ID,
        value = Dist.CLIENT
)
public final class ModColorHandlers {
    private ModColorHandlers() {
    }

    @SubscribeEvent
    public static void registerBlockTintSources(RegisterColorHandlersEvent.BlockTintSources event) {
        event.register(
                List.of(net.minecraft.client.color.block.BlockTintSources.grassBlock()),
                ModBlocks.GREAT_SWAMP_GRASS_BLOCK.get()
        );

        event.register(
                List.of(net.minecraft.client.color.block.BlockTintSources.foliage()),
                ModBlocks.GREAT_SWAMP_VINES.get()
        );
    }
}