package io.github.naimjeg.obeliskdepths.registry;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.tempering.TemperingTemplateData;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(
                    Registries.DATA_COMPONENT_TYPE,
                    ObeliskDepths.MOD_ID
            );

    public static final DeferredHolder<
            DataComponentType<?>,
            DataComponentType<TemperingTemplateData>
            > TEMPERING_TEMPLATE_DATA =
            COMPONENTS.register(
                    "tempering_template_data",
                    () -> DataComponentType
                            .<TemperingTemplateData>builder()
                            .persistent(TemperingTemplateData.CODEC)
                            .networkSynchronized(
                                    TemperingTemplateData.STREAM_CODEC
                            )
                            .cacheEncoding()
                            .build()
            );

    private ModDataComponents() {
    }

    public static void register(IEventBus eventBus) {
        COMPONENTS.register(eventBus);
    }
}
