package io.github.naimjeg.obeliskdepths.data;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.registry.ModBlocks;
import io.github.naimjeg.obeliskdepths.registry.ModItems;
import io.github.naimjeg.obeliskdepths.registry.ModTags;
import io.github.naimjeg.obeliskdepths.equipment.ObeliskEquipmentTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.IntrinsicHolderTagsProvider;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.concurrent.CompletableFuture;

public final class ModItemTagProvider extends IntrinsicHolderTagsProvider<Item> {
    public ModItemTagProvider(
            PackOutput output,
            CompletableFuture<HolderLookup.Provider> lookupProvider
    ) {
        super(
                output,
                Registries.ITEM,
                lookupProvider,
                item -> BuiltInRegistries.ITEM
                        .getResourceKey(item)
                        .orElseThrow(),
                ObeliskDepths.MOD_ID
        );
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        this.addWoodTags();
        this.addArmorTags();
        this.addTemperingTags();
        this.addRewardTags();
    }

    private void addWoodTags() {
        for (ModBlocks.WoodBlockSet set : ModBlocks.WOOD_BLOCK_SETS) {
            this.tag(ModTags.Items.AMPHIXYLON_LOGS)
                    .add(
                            set.logItem().get(),
                            set.woodItem().get(),
                            set.strippedLogItem().get(),
                            set.strippedWoodItem().get()
                    );
            this.tag(ItemTags.LOGS)
                    .addTag(ModTags.Items.AMPHIXYLON_LOGS);
            this.tag(ItemTags.LOGS_THAT_BURN)
                    .addTag(ModTags.Items.AMPHIXYLON_LOGS);
            this.tag(ItemTags.PLANKS)
                    .add(set.planksItem().get());
            this.tag(ItemTags.WOODEN_STAIRS)
                    .add(set.stairsItem().get());
            this.tag(ItemTags.STAIRS)
                    .add(set.stairsItem().get());
            this.tag(ItemTags.WOODEN_SLABS)
                    .add(set.slabItem().get());
            this.tag(ItemTags.SLABS)
                    .add(set.slabItem().get());
            this.tag(ItemTags.WOODEN_FENCES)
                    .add(set.fenceItem().get());
            this.tag(ItemTags.FENCES)
                    .add(set.fenceItem().get());
            this.tag(ItemTags.FENCE_GATES)
                    .add(set.fenceGateItem().get());
            this.tag(ItemTags.WOODEN_DOORS)
                    .add(set.doorItem().get());
            this.tag(ItemTags.DOORS)
                    .add(set.doorItem().get());
            this.tag(ItemTags.WOODEN_TRAPDOORS)
                    .add(set.trapdoorItem().get());
            this.tag(ItemTags.TRAPDOORS)
                    .add(set.trapdoorItem().get());
//            this.tag(ItemTags.WOODEN_PRESSURE_PLATES)
//                    .add(set.pressurePlateItem().get());
//            this.tag(ItemTags.WOODEN_BUTTONS)
//                    .add(set.buttonItem().get());
//            this.tag(ItemTags.BUTTONS)
//                    .add(set.buttonItem().get());
            this.tag(ItemTags.LEAVES)
                    .add(set.leavesItem().get());
//            this.tag(ItemTags.SIGNS)
//                    .add(set.signItem().get());
//            this.tag(ItemTags.HANGING_SIGNS)
//                    .add(set.hangingSignItem().get());
        }
    }

    private void addArmorTags() {
        this.tag(ItemTags.HEAD_ARMOR)
                .add(ModItems.EXILE_HELMET.get());
        this.tag(ItemTags.CHEST_ARMOR)
                .add(ModItems.EXILE_CHESTPLATE.get());
        this.tag(ItemTags.LEG_ARMOR)
                .add(ModItems.EXILE_LEGGINGS.get());
        this.tag(ItemTags.FOOT_ARMOR)
                .add(ModItems.EXILE_BOOTS.get());
    }

    private void addTemperingTags() {
        this.tag(ObeliskEquipmentTags.WEAPONS)
                .add(
                        Items.WOODEN_SWORD,
                        Items.STONE_SWORD,
                        Items.IRON_SWORD,
                        Items.GOLDEN_SWORD,
                        Items.DIAMOND_SWORD,
                        Items.NETHERITE_SWORD,
                        Items.WOODEN_AXE,
                        Items.STONE_AXE,
                        Items.IRON_AXE,
                        Items.GOLDEN_AXE,
                        Items.DIAMOND_AXE,
                        Items.NETHERITE_AXE,
                        Items.BOW,
                        Items.CROSSBOW,
                        Items.TRIDENT,
                        Items.MACE
                );
        this.tag(ObeliskEquipmentTags.ARMOR_HEAD)
                .addTag(ItemTags.HEAD_ARMOR);
        this.tag(ObeliskEquipmentTags.ARMOR_CHEST)
                .addTag(ItemTags.CHEST_ARMOR);
        this.tag(ObeliskEquipmentTags.ARMOR_LEGS)
                .addTag(ItemTags.LEG_ARMOR);
        this.tag(ObeliskEquipmentTags.ARMOR_FEET)
                .addTag(ItemTags.FOOT_ARMOR);
        this.tag(ObeliskEquipmentTags.ALL)
                .addTag(ObeliskEquipmentTags.WEAPONS)
                .addTag(ObeliskEquipmentTags.ARMOR_HEAD)
                .addTag(ObeliskEquipmentTags.ARMOR_CHEST)
                .addTag(ObeliskEquipmentTags.ARMOR_LEGS)
                .addTag(ObeliskEquipmentTags.ARMOR_FEET);
    }

    private void addRewardTags() {
        this.tag(ModTags.Items.REWARD_WEAPONS_TIER_1)
                .add(
                        Items.IRON_SWORD,
                        Items.IRON_AXE,
                        Items.BOW
                );
        this.tag(ModTags.Items.REWARD_WEAPONS_TIER_2)
                .add(
                        Items.IRON_SWORD,
                        Items.IRON_AXE,
                        Items.CROSSBOW,
                        Items.TRIDENT
                );
        this.tag(ModTags.Items.REWARD_WEAPONS_TIER_3)
                .add(
                        Items.DIAMOND_SWORD,
                        Items.DIAMOND_AXE,
                        Items.CROSSBOW,
                        Items.TRIDENT
                );
        this.tag(ModTags.Items.REWARD_WEAPONS_TIER_4)
                .add(
                        Items.DIAMOND_SWORD,
                        Items.DIAMOND_AXE,
                        Items.MACE,
                        Items.TRIDENT
                );
        this.tag(ModTags.Items.REWARD_ARMOR_TIER_1)
                .add(
                        Items.IRON_HELMET,
                        Items.IRON_CHESTPLATE,
                        Items.IRON_LEGGINGS,
                        Items.IRON_BOOTS
                );
        this.tag(ModTags.Items.REWARD_ARMOR_TIER_2)
                .add(
                        Items.CHAINMAIL_HELMET,
                        Items.IRON_CHESTPLATE,
                        Items.IRON_LEGGINGS,
                        Items.SHIELD
                );
        this.tag(ModTags.Items.REWARD_ARMOR_TIER_3)
                .add(
                        Items.DIAMOND_HELMET,
                        Items.DIAMOND_CHESTPLATE,
                        Items.DIAMOND_LEGGINGS,
                        Items.DIAMOND_BOOTS
                );
        this.tag(ModTags.Items.REWARD_ARMOR_TIER_4)
                .add(
                        Items.DIAMOND_HELMET,
                        Items.DIAMOND_CHESTPLATE,
                        Items.DIAMOND_LEGGINGS,
                        Items.DIAMOND_BOOTS,
                        Items.SHIELD
                );
    }
}
