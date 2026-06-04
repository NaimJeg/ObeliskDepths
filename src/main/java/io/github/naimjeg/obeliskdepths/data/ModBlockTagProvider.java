package io.github.naimjeg.obeliskdepths.data;

import io.github.naimjeg.obeliskdepths.registry.ModBlocks;
import io.github.naimjeg.obeliskdepths.registry.ModTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.IntrinsicHolderTagsProvider;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.concurrent.CompletableFuture;

public final class ModBlockTagProvider extends IntrinsicHolderTagsProvider<Block> {
    public ModBlockTagProvider(
            PackOutput output,
            CompletableFuture<HolderLookup.Provider> lookupProvider
    ) {
        super(
                output,
                Registries.BLOCK,
                lookupProvider,
                block -> block.builtInRegistryHolder().key()
        );
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        addDungeonStoneToolTags();
        addStoneVariantShapeTags();
        addWoodTags();
        addVineTags();
    }

    private void addDungeonStoneToolTags() {
        this.tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(
                        ModBlocks.OBELISK.get(),
                        ModBlocks.REINFORCED_DUNGEON_STONE.get(),
                        ModBlocks.DUNGEON_TILES.get(),
                        ModBlocks.DUNGEON_CRACKED_TILES.get(),
                        ModBlocks.DUNGEON_CRACKED_BRICKS.get()
                )
                .add(ModBlocks.STONE_SET_BLOCKS.stream()
                        .map(DeferredBlock::get)
                        .toArray(Block[]::new));

        this.tag(BlockTags.NEEDS_DIAMOND_TOOL)
                .add(
                        ModBlocks.REINFORCED_DUNGEON_STONE.get(),
                        ModBlocks.DUNGEON_TILES.get(),
                        ModBlocks.DUNGEON_CRACKED_TILES.get(),
                        ModBlocks.DUNGEON_CRACKED_BRICKS.get()
                )
                .add(ModBlocks.STONE_SET_BLOCKS.stream()
                        .map(DeferredBlock::get)
                        .toArray(Block[]::new));
    }

    private void addStoneVariantShapeTags() {
        this.tag(BlockTags.SLABS)
                .add(ModBlocks.STONE_BLOCK_SETS.stream()
                        .map(set -> set.slab().get())
                        .toArray(Block[]::new));

        this.tag(BlockTags.STAIRS)
                .add(ModBlocks.STONE_BLOCK_SETS.stream()
                        .map(set -> set.stairs().get())
                        .toArray(Block[]::new));

        this.tag(BlockTags.WALLS)
                .add(ModBlocks.STONE_BLOCK_SETS.stream()
                        .map(set -> set.wall().get())
                        .toArray(Block[]::new));
    }

    private void addWoodTags() {
        for (ModBlocks.WoodBlockSet set : ModBlocks.WOOD_BLOCK_SETS) {
            Block[] logs = set.logBlocks()
                    .stream()
                    .map(DeferredBlock::get)
                    .toArray(Block[]::new);

            this.tag(ModTags.Blocks.GREAT_SWAMP_TAXODIUM_LOGS)
                    .add(logs);
            this.tag(BlockTags.LOGS)
                    .addTag(ModTags.Blocks.GREAT_SWAMP_TAXODIUM_LOGS);
            this.tag(BlockTags.LOGS_THAT_BURN)
                    .addTag(ModTags.Blocks.GREAT_SWAMP_TAXODIUM_LOGS);

            this.tag(BlockTags.PLANKS)
                    .add(set.planks().get());
            this.tag(BlockTags.WOODEN_STAIRS)
                    .add(set.stairs().get());
            this.tag(BlockTags.STAIRS)
                    .add(set.stairs().get());
            this.tag(BlockTags.WOODEN_SLABS)
                    .add(set.slab().get());
            this.tag(BlockTags.SLABS)
                    .add(set.slab().get());
            this.tag(BlockTags.WOODEN_FENCES)
                    .add(set.fence().get());
            this.tag(BlockTags.FENCES)
                    .add(set.fence().get());
            this.tag(BlockTags.FENCE_GATES)
                    .add(set.fenceGate().get());
            this.tag(BlockTags.WOODEN_DOORS)
                    .add(set.door().get());
            this.tag(BlockTags.DOORS)
                    .add(set.door().get());
            this.tag(BlockTags.WOODEN_TRAPDOORS)
                    .add(set.trapdoor().get());
            this.tag(BlockTags.TRAPDOORS)
                    .add(set.trapdoor().get());
//            this.tag(BlockTags.WOODEN_PRESSURE_PLATES)
//                    .add(set.pressurePlate().get());
//            this.tag(BlockTags.PRESSURE_PLATES)
//                    .add(set.pressurePlate().get());
//            this.tag(BlockTags.WOODEN_BUTTONS)
//                    .add(set.button().get());
//            this.tag(BlockTags.BUTTONS)
//                    .add(set.button().get());
//            this.tag(BlockTags.STANDING_SIGNS)
//                    .add(set.sign().get());
//            this.tag(BlockTags.WALL_SIGNS)
//                    .add(set.wallSign().get());
//            this.tag(BlockTags.SIGNS)
//                    .add(set.sign().get(), set.wallSign().get());
//            this.tag(BlockTags.CEILING_HANGING_SIGNS)
//                    .add(set.hangingSign().get());
//            this.tag(BlockTags.WALL_HANGING_SIGNS)
//                    .add(set.wallHangingSign().get());
//            this.tag(BlockTags.ALL_HANGING_SIGNS)
//                    .add(set.hangingSign().get(), set.wallHangingSign().get());
//            this.tag(BlockTags.ALL_SIGNS)
//                    .add(
//                            set.sign().get(),
//                            set.wallSign().get(),
//                            set.hangingSign().get(),
//                            set.wallHangingSign().get()
//                    );
            this.tag(BlockTags.LEAVES)
                    .add(set.leaves().get());

            this.tag(BlockTags.MINEABLE_WITH_AXE)
                    .add(
                            set.log().get(),
                            set.wood().get(),
                            set.strippedLog().get(),
                            set.strippedWood().get(),
                            set.planks().get(),
                            set.stairs().get(),
                            set.slab().get(),
                            set.fence().get(),
                            set.fenceGate().get(),
                            set.door().get(),
                            set.trapdoor().get()
//                            set.pressurePlate().get(),
//                            set.button().get(),
//                            set.sign().get(),
//                            set.wallSign().get(),
//                            set.hangingSign().get(),
//                            set.wallHangingSign().get()
                    );
            this.tag(BlockTags.MINEABLE_WITH_HOE)
                    .add(set.leaves().get());
        }

        this.tag(BlockTags.MINEABLE_WITH_AXE)
                .add(
                        ModBlocks.GREAT_SWAMP_TAXODIUM_ROOT_TANGLE.get()
                );
    }

    private void addVineTags() {
        this.tag(BlockTags.CLIMBABLE)
                .add(
                        ModBlocks.GREAT_SWAMP_VINES.get(),
                        ModBlocks.GREAT_SWAMP_VINES_PLANT.get()
                );

        this.tag(BlockTags.FALL_DAMAGE_RESETTING)
                .add(
                        ModBlocks.GREAT_SWAMP_VINES.get(),
                        ModBlocks.GREAT_SWAMP_VINES_PLANT.get()
                );

        this.tag(BlockTags.CAN_GLIDE_THROUGH)
                .add(
                        ModBlocks.GREAT_SWAMP_VINES.get(),
                        ModBlocks.GREAT_SWAMP_VINES_PLANT.get()
                );

        this.tag(BlockTags.SWORD_EFFICIENT)
                .add(
                        ModBlocks.GREAT_SWAMP_VINES.get(),
                        ModBlocks.GREAT_SWAMP_VINES_PLANT.get()
                );
    }
}
