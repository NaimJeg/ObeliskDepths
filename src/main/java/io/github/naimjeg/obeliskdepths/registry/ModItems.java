package io.github.naimjeg.obeliskdepths.registry;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.item.ReturnScrollItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.ArmorType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public final class ModItems {
    private ModItems() {
    }

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ObeliskDepths.MOD_ID);

    public static final DeferredItem<Item> TEMPERING_SMITHING_TEMPLATE =
            ITEMS.register(
                    "tempering_smithing_template",
                    registryName -> new Item(new Item.Properties()
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

    public static final DeferredItem<Item> EXILE_HELMET =
            registerExileArmor("exile_helmet", ArmorType.HELMET);
    public static final DeferredItem<Item> EXILE_CHESTPLATE =
            registerExileArmor("exile_chestplate", ArmorType.CHESTPLATE);
    public static final DeferredItem<Item> EXILE_LEGGINGS =
            registerExileArmor("exile_leggings", ArmorType.LEGGINGS);
    public static final DeferredItem<Item> EXILE_BOOTS =
            registerExileArmor("exile_boots", ArmorType.BOOTS);

    public static final List<DeferredItem<Item>> EXILE_ARMOR = List.of(
            EXILE_HELMET,
            EXILE_CHESTPLATE,
            EXILE_LEGGINGS,
            EXILE_BOOTS
    );

    private static DeferredItem<Item> registerExileArmor(
            String name,
            ArmorType armorType
    ) {
        return ITEMS.registerSimpleItem(
                name,
                properties -> properties.humanoidArmor(
                        ModArmorMaterials.EXILE,
                        armorType
                )
        );
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
