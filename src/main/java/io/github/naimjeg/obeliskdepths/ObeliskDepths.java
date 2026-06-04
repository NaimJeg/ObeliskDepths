package io.github.naimjeg.obeliskdepths;

import com.mojang.logging.LogUtils;
import io.github.naimjeg.obeliskdepths.command.ExportStructureCommand;
import io.github.naimjeg.obeliskdepths.dungeon.content.DungeonContentReloadListener;
import io.github.naimjeg.obeliskdepths.equipment.ObeliskEquipmentGameTests;
import io.github.naimjeg.obeliskdepths.equipment.ObeliskEquipmentTemplateRegistration;
import io.github.naimjeg.obeliskdepths.network.*;
import io.github.naimjeg.obeliskdepths.registry.*;
import io.github.naimjeg.obeliskdepths.tempering.ObeliskTemperingDirectionReloadListener;
import io.github.naimjeg.obeliskdepths.tempering.ObeliskTemperingPoolReloadListener;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.resource.ListenerKey;
import org.slf4j.Logger;

@Mod(ObeliskDepths.MOD_ID)
public final class ObeliskDepths {

    public static final String MOD_ID = "obeliskdepths";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final ListenerKey<ObeliskTemperingPoolReloadListener>
            TEMPERING_POOL_RELOAD_LISTENER =
            ListenerKey.create(Identifier.fromNamespaceAndPath(
                    ObeliskDepths.MOD_ID,
                    "obelisk_tempering_pools"
            ));
    private static final ListenerKey<ObeliskTemperingDirectionReloadListener>
            TEMPERING_DIRECTION_RELOAD_LISTENER =
            ListenerKey.create(Identifier.fromNamespaceAndPath(
                    ObeliskDepths.MOD_ID,
                    "obelisk_tempering_directions"
            ));
    private static final ListenerKey<DungeonContentReloadListener>
            DUNGEON_CONTENT_RELOAD_LISTENER =
            ListenerKey.create(Identifier.fromNamespaceAndPath(
                    ObeliskDepths.MOD_ID,
                    "dungeon_content"
            ));

    public ObeliskDepths(
            IEventBus modEventBus,
            ModContainer modContainer
    ) {
        ModTicketTypes.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModAttachments.register(modEventBus);
        ModDataComponents.register(modEventBus);
        ModMenuTypes.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModEntityTypes.register(modEventBus);
        ModRecipeTypes.register(modEventBus);
        ModRecipeSerializers.register(modEventBus);
        ModWorldgen.register(modEventBus);

        NeoForge.EVENT_BUS.addListener(
                ExportStructureCommand::onRegisterCommands
        );
        // DamageNexus owns this GAME-bus lifecycle. The registrar is valid
        // only inside this callback and is never retained by ObeliskDepths.
        NeoForge.EVENT_BUS.addListener(this::registerDamageNexusContent);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloadHandlers);
        modEventBus.addListener(ObeliskEquipmentGameTests::registerTestFunctions);
        modEventBus.addListener(ObeliskEquipmentGameTests::registerGameTests);
        NeoForge.EVENT_BUS.addListener(this::addReloadListeners);
    }

    private void registerDamageNexusContent(
            io.github.naimjeg.damagenexus.api.event.DamageNexusRegisterEvent event
    ) {
        ObeliskEquipmentTemplateRegistration.register(event);
        ObeliskEquipmentGameTests.registerDamageNexusExtensions(event);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ObeliskDepthsCommonSetup::bootstrap);
    }

    private void addReloadListeners(AddServerReloadListenersEvent event) {
        event.addRetainedListener(
                TEMPERING_POOL_RELOAD_LISTENER,
                new ObeliskTemperingPoolReloadListener()
        );
        event.addRetainedListener(
                TEMPERING_DIRECTION_RELOAD_LISTENER,
                new ObeliskTemperingDirectionReloadListener()
        );
        event.addRetainedListener(
                DUNGEON_CONTENT_RELOAD_LISTENER,
                new DungeonContentReloadListener()
        );
    }

    private void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToServer(
                        SelectTemperingDirectionPayload.TYPE,
                        SelectTemperingDirectionPayload.STREAM_CODEC,
                        SelectTemperingDirectionPayload::handle
                )
                .playToClient(
                        ClientboundTemperingDirectionStatePayload.TYPE,
                        ClientboundTemperingDirectionStatePayload.STREAM_CODEC,
                        ClientboundTemperingDirectionStatePayload::handle
                )
                .playToClient(
                        ClientboundOpenDungeonLoadingPayload.TYPE,
                        ClientboundOpenDungeonLoadingPayload.STREAM_CODEC,
                        ClientboundOpenDungeonLoadingPayload::handle
                )
                .playToServer(
                        ServerboundDungeonLoadingReadyPayload.TYPE,
                        ServerboundDungeonLoadingReadyPayload.STREAM_CODEC,
                        ServerboundDungeonLoadingReadyPayload::handle
                )
                .playToClient(
                        ClientboundDungeonLoadingStatePayload.TYPE,
                        ClientboundDungeonLoadingStatePayload.STREAM_CODEC,
                        ClientboundDungeonLoadingStatePayload::handle
                )
                .playToClient(
                        ClientboundDungeonLoadingFinishedPayload.TYPE,
                        ClientboundDungeonLoadingFinishedPayload.STREAM_CODEC,
                        ClientboundDungeonLoadingFinishedPayload::handle
                );
    }
}
