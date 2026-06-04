package io.github.naimjeg.obeliskdepths.data;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.block.ObeliskBlock;
import io.github.naimjeg.obeliskdepths.block.ObeliskChestBlock;
import io.github.naimjeg.obeliskdepths.block.ObeliskChestPart;
import io.github.naimjeg.obeliskdepths.block.ObeliskPart;
import io.github.naimjeg.obeliskdepths.registry.ModBlocks;
import io.github.naimjeg.obeliskdepths.registry.ModItems;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.data.models.model.TexturedModel;

import com.google.gson.JsonArray;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

public final class ModModelProvider extends ModelProvider {
    public ModModelProvider(PackOutput output) {
        super(output, ObeliskDepths.MOD_ID);
    }

    @Override
    protected void registerModels(
            BlockModelGenerators blockModels,
            ItemModelGenerators itemModels
    ) {
        ModBlocks.TRIVIAL_CUBE_BLOCKS.forEach(block ->
                blockModels.createTrivialCube(block.get())
        );
        ModBlocks.STONE_BLOCK_SETS.forEach(set ->
                createStoneBlockSet(blockModels, set)
        );
        ModBlocks.WOOD_BLOCK_SETS.forEach(set ->
                createWoodBlockSet(blockModels, set)
        );

        createObeliskSmithingTable(blockModels, ModBlocks.OBELISK_SMITHING_TABLE.get());
        createObelisk(blockModels, ModBlocks.OBELISK.get());
        createObeliskChest(blockModels, ModBlocks.OBELISK_CHEST.get());
        createReinforcedDungeonStoneBlock(blockModels, ModBlocks.REINFORCED_DUNGEON_STONE.get());
        createModGrassBlock(blockModels, ModBlocks.GREAT_SWAMP_GRASS_BLOCK.get());
        createRootTangle(blockModels, ModBlocks.GREAT_SWAMP_TAXODIUM_ROOT_TANGLE.get());
        createGreatSwampVines(blockModels);

        itemModels.generateFlatItem(
                ModItems.TEMPERING_SMITHING_TEMPLATE.get(),
                ModelTemplates.FLAT_ITEM
        );
        itemModels.generateFlatItem(
                ModItems.RETURN_SCROLL.get(),
                ModelTemplates.FLAT_ITEM
        );
    }

    private static void createReinforcedDungeonStoneBlock(
            BlockModelGenerators blockModels,
            Block block
    ) {
        TextureMapping mapping = new TextureMapping()
                .put(TextureSlot.END, TextureMapping.getBlockTexture(block, "_top"))
                .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(block, "_side"));

        Identifier model = ModelTemplates.CUBE_COLUMN.create(
                block,
                mapping,
                blockModels.modelOutput
        );

        MultiVariant variant = BlockModelGenerators.plainVariant(model);

        blockModels.blockStateOutput.accept(
                BlockModelGenerators.createSimpleBlock(block, variant)
        );

        blockModels.registerSimpleItemModel(block, model);
    }

    private static void createObelisk(
            BlockModelGenerators blockModels,
            Block block
    ) {
        Identifier bottomModel = Identifier.fromNamespaceAndPath(
                ObeliskDepths.MOD_ID,
                "block/obelisk_bottom"
        );

        Identifier midModel = Identifier.fromNamespaceAndPath(
                ObeliskDepths.MOD_ID,
                "block/obelisk_middle"
        );

        Identifier topModel = Identifier.fromNamespaceAndPath(
                ObeliskDepths.MOD_ID,
                "block/obelisk_top"
        );

        MultiVariant bottomVariant =
                BlockModelGenerators.plainVariant(bottomModel);
        MultiVariant midVariant =
                BlockModelGenerators.plainVariant(midModel);
        MultiVariant topVariant =
                BlockModelGenerators.plainVariant(topModel);

        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(block)
                        .with(
                                PropertyDispatch.initial(ObeliskBlock.PART)
                                        .select(
                                                ObeliskPart.BOTTOM,
                                                bottomVariant
                                        )
                                        .select(
                                                ObeliskPart.MIDDLE,
                                                midVariant
                                        )
                                        .select(
                                                ObeliskPart.TOP,
                                                topVariant
                                        )
                        )
        );

        blockModels.registerSimpleItemModel(block, bottomModel);
    }

    private static void createObeliskChest(
            BlockModelGenerators blockModels,
            Block block
    ) {
        Identifier model = Identifier.fromNamespaceAndPath(
                ObeliskDepths.MOD_ID,
                "block/obelisk_chest"
        );

        MultiVariant variant = BlockModelGenerators.plainVariant(model);

        blockModels.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(block)
                        .with(
                                PropertyDispatch.initial(ObeliskChestBlock.PART, ObeliskChestBlock.OPENED)
                                        .generate((part, opened) -> variant)
                        )
                        .with(BlockModelGenerators.ROTATION_HORIZONTAL_FACING)
        );

        blockModels.registerSimpleItemModel(block, model);
    }

    private static void createModGrassBlock(
            BlockModelGenerators blockModels,
            Block block
    ) {
        TextureMapping mapping = new TextureMapping()
                .put(TextureSlot.BOTTOM, TextureMapping.getBlockTexture(ModBlocks.GREAT_SWAMP_DIRT.get()))
                .put(TextureSlot.SIDE, TextureMapping.getBlockTexture(block, "_side"))
                .put(TextureSlot.TOP, TextureMapping.getBlockTexture(block, "_top"));

        Identifier model = ModelTemplates.CUBE_BOTTOM_TOP.create(
                block,
                mapping,
                blockModels.modelOutput
        );

        MultiVariant variant = BlockModelGenerators.plainVariant(model);

        blockModels.blockStateOutput.accept(
                BlockModelGenerators.createSimpleBlock(block, variant)
        );

        blockModels.registerSimpleItemModel(block, model);
    }

    private static void createObeliskSmithingTable(
            BlockModelGenerators blockModels,
            Block block
    ) {
        TextureMapping mapping = new TextureMapping()
                .put(TextureSlot.DOWN, TextureMapping.getBlockTexture(ModBlocks.OBELISK_SMITHING_TABLE.get(), "_bottom"))
                .put(TextureSlot.EAST, TextureMapping.getBlockTexture(ModBlocks.OBELISK_SMITHING_TABLE.get(), "_side"))
                .put(TextureSlot.NORTH, TextureMapping.getBlockTexture(ModBlocks.OBELISK_SMITHING_TABLE.get(), "_front"))
                .put(TextureSlot.PARTICLE, TextureMapping.getBlockTexture(ModBlocks.OBELISK_SMITHING_TABLE.get(), "_front"))
                .put(TextureSlot.SOUTH, TextureMapping.getBlockTexture(ModBlocks.OBELISK_SMITHING_TABLE.get(), "_front"))
                .put(TextureSlot.UP, TextureMapping.getBlockTexture(ModBlocks.OBELISK_SMITHING_TABLE.get(), "_top"))
                .put(TextureSlot.WEST, TextureMapping.getBlockTexture(ModBlocks.OBELISK_SMITHING_TABLE.get(), "_side"));

        Identifier model = ModelTemplates.CUBE.create(
                block,
                mapping,
                blockModels.modelOutput
        );

        MultiVariant variant = BlockModelGenerators.plainVariant(model);

        blockModels.blockStateOutput.accept(
                BlockModelGenerators.createSimpleBlock(block, variant)
        );

        blockModels.registerSimpleItemModel(block, model);
    }

    private static void createGreatSwampVines(BlockModelGenerators blockModels) {
        blockModels.createGrowingPlant(
                ModBlocks.GREAT_SWAMP_VINES.get(),
                ModBlocks.GREAT_SWAMP_VINES_PLANT.get(),
                BlockModelGenerators.PlantType.NOT_TINTED
        );

        blockModels.registerSimpleFlatItemModel(
                ModBlocks.GREAT_SWAMP_VINES.get(),
                "_plant"
        );
    }

    private static void createStoneBlockSet(
            BlockModelGenerators blockModels,
            ModBlocks.StoneBlockSet set
    ) {
        blockModels.familyWithExistingFullBlock(set.base().get())
                .slab(set.slab().get())
                .stairs(set.stairs().get())
                .wall(set.wall().get());
    }

    private static void createWoodBlockSet(
            BlockModelGenerators blockModels,
            ModBlocks.WoodBlockSet set
    ) {
        blockModels.woodProvider(set.log().get())
                .logWithHorizontal(set.log().get())
                .wood(set.wood().get());
        blockModels.woodProvider(set.strippedLog().get())
                .logWithHorizontal(set.strippedLog().get())
                .wood(set.strippedWood().get());

        blockModels.createTrivialCube(set.planks().get());
        var family = blockModels.familyWithExistingFullBlock(set.planks().get())
                .stairs(set.stairs().get())
                .slab(set.slab().get())
                .fence(set.fence().get())
                .fenceGate(set.fenceGate().get())
//                .pressurePlate(set.pressurePlate().get())
//                .button(set.button().get())
                .door(set.door().get());
        family.trapdoor(set.trapdoor().get());

//        createSign(blockModels, set.planks().get(), set.sign().get(), set.wallSign().get());
//        blockModels.createHangingSign(
//                set.strippedLog().get(),
//                set.hangingSign().get(),
//                set.wallHangingSign().get()
//        );
        blockModels.createTintedLeaves(
                set.leaves().get(),
                TexturedModel.LEAVES,
                -12012264
        );
    }

    private static void createSign(
            BlockModelGenerators blockModels,
            Block particleBlock,
            Block sign,
            Block wallSign
    ) {
        Identifier model = ModelTemplates.PARTICLE_ONLY.create(
                sign,
                TextureMapping.particle(particleBlock),
                blockModels.modelOutput
        );
        MultiVariant variant = BlockModelGenerators.plainVariant(model);

        blockModels.blockStateOutput.accept(
                BlockModelGenerators.createSimpleBlock(sign, variant)
        );
        blockModels.blockStateOutput.accept(
                BlockModelGenerators.createSimpleBlock(wallSign, variant)
        );
        blockModels.registerSimpleFlatItemModel(sign.asItem());
    }

    private static void createRootTangle(
            BlockModelGenerators blockModels,
            Block block
    ) {
        Identifier model = ModelTemplates.CUBE_BOTTOM_TOP.create(
                block,
                TextureMapping.cubeBottomTop(block),
                blockModels.modelOutput
        );
        MultiVariant variant = BlockModelGenerators.plainVariant(model);

        blockModels.blockStateOutput.accept(
                BlockModelGenerators.createSimpleBlock(block, variant)
        );
        blockModels.registerSimpleItemModel(block, model);
    }

    private static JsonArray jsonArray(Number... values) {
        JsonArray array = new JsonArray();

        for (Number value : values) {
            array.add(value);
        }

        return array;
    }
}
