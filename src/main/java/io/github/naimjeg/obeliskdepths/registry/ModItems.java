package io.github.naimjeg.obeliskdepths.registry;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.item.ReturnScrollItem;
import io.github.naimjeg.obeliskdepths.item.TemperingSmithingTemplateItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    private ModItems() {
    }

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ObeliskDepths.MOD_ID);

    public static final DeferredItem<Item> TEMPERING_SMITHING_TEMPLATE =
            ITEMS.register(
                    "tempering_smithing_template",
                    registryName -> new TemperingSmithingTemplateItem(new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, registryName))
                            .stacksTo(64))
            );

    public static final DeferredItem<ReturnScrollItem> RETURN_SCROLL =
            ITEMS.register(
                    "return_scroll",
                    registryName -> new ReturnScrollItem(new Item.Properties()
                            .setId(ResourceKey.create(Registries.ITEM, registryName))
                            .stacksTo(16))
            );

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
