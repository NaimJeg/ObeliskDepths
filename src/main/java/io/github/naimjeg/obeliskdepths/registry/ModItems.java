package io.github.naimjeg.obeliskdepths.registry;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.item.ReturnScrollItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.ToolMaterial;
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

    public static final DeferredItem<Item> GRANDFATHER = ITEMS.registerSimpleItem(
            "grandfather",
            properties -> properties.sword(ToolMaterial.NETHERITE, 3.0F, -2.4F)
                    .fireResistant()
    );
    public static final DeferredItem<Item> HARLEQUIN_CREST =
            registerUniqueArmor("harlequin_crest", ArmorType.HELMET);
    public static final DeferredItem<Item> TYRAELS_MIGHT =
            registerUniqueArmor("tyraels_might", ArmorType.CHESTPLATE);
    public static final DeferredItem<Item> TIBAULTS_WILL =
            registerUniqueArmor("tibaults_will", ArmorType.LEGGINGS);
    public static final DeferredItem<Item> BLOOD_MOON_BREECHES =
            registerUniqueArmor("blood_moon_breeches", ArmorType.LEGGINGS);
    public static final DeferredItem<Item> COWL_OF_THE_NAMELESS =
            registerUniqueArmor("cowl_of_the_nameless", ArmorType.HELMET);

    public static final List<DeferredItem<Item>> UNIQUE_EQUIPMENT = List.of(
            GRANDFATHER,
            HARLEQUIN_CREST,
            TYRAELS_MIGHT,
            TIBAULTS_WILL,
            BLOOD_MOON_BREECHES,
            COWL_OF_THE_NAMELESS
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

    private static DeferredItem<Item> registerUniqueArmor(
            String name,
            ArmorType armorType
    ) {
        ResourceKey<EquipmentAsset> asset = ResourceKey.create(
                EquipmentAssets.ROOT_ID,
                Identifier.fromNamespaceAndPath(ObeliskDepths.MOD_ID, name)
        );
        return ITEMS.registerSimpleItem(name, properties -> properties
                .humanoidArmor(ModArmorMaterials.EXILE, armorType)
                .component(
                        DataComponents.EQUIPPABLE,
                        Equippable.builder(armorType.getSlot())
                                .setEquipSound(ModArmorMaterials.EXILE.equipSound())
                                .setAsset(asset)
                                .build()
                ));
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
