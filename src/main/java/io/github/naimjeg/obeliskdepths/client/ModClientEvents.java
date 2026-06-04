package io.github.naimjeg.obeliskdepths.client;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.client.render.ReturnScrollFirstPersonRenderer;
import io.github.naimjeg.obeliskdepths.client.render.ObeliskChestRenderer;
import io.github.naimjeg.obeliskdepths.client.screen.ObeliskPortalScreen;
import io.github.naimjeg.obeliskdepths.client.screen.ObeliskTemperingScreen;
import io.github.naimjeg.obeliskdepths.registry.ModEntityTypes;
import io.github.naimjeg.obeliskdepths.registry.ModBlockEntities;
import io.github.naimjeg.obeliskdepths.registry.ModMenuTypes;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

@EventBusSubscriber(
        modid = ObeliskDepths.MOD_ID,
        value = Dist.CLIENT
)
public final class ModClientEvents {
    private ModClientEvents() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(
                ModMenuTypes.OBELISK_TEMPERING.get(),
                ObeliskTemperingScreen::new
        );

        event.register(
                ModMenuTypes.OBELISK_PORTAL.get(),
                ObeliskPortalScreen::new
        );
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                ModBlockEntities.OBELISK_CHEST.get(),
                ObeliskChestRenderer::new
        );
        event.registerEntityRenderer(
                ModEntityTypes.DUNGEON_PORTAL.get(),
                NoopRenderer::new
        );
    }

    @SubscribeEvent
    public static void registerStandaloneModels(ModelEvent.RegisterStandalone event) {
        event.register(
                ObeliskChestRenderer.BOTTOM_MODEL,
                SimpleUnbakedStandaloneModel.simpleModelWrapper(ObeliskChestRenderer.BOTTOM_MODEL_ID)
        );
        event.register(
                ObeliskChestRenderer.LID_MODEL,
                SimpleUnbakedStandaloneModel.simpleModelWrapper(ObeliskChestRenderer.LID_MODEL_ID)
        );
        event.register(
                ObeliskChestRenderer.LOCK_MODEL,
                SimpleUnbakedStandaloneModel.simpleModelWrapper(ObeliskChestRenderer.LOCK_MODEL_ID)
        );
    }

    @SubscribeEvent
    public static void renderHand(RenderHandEvent event) {
        if (ReturnScrollFirstPersonRenderer.render(
                event.getHand(),
                event.getPoseStack(),
                event.getSubmitNodeCollector(),
                event.getPackedLight(),
                event.getPartialTick(),
                event.getInterpolatedPitch(),
                event.getSwingProgress(),
                event.getEquipProgress(),
                event.getItemStack()
        )) {
            event.setCanceled(true);
        }
    }
}
