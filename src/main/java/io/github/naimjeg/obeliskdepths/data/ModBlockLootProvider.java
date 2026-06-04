package io.github.naimjeg.obeliskdepths.data;

import io.github.naimjeg.obeliskdepths.registry.ModBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;

import java.util.Set;
import java.util.stream.Stream;

public final class ModBlockLootProvider extends BlockLootSubProvider {
    public ModBlockLootProvider(HolderLookup.Provider lookupProvider) {
        super(Set.of(), FeatureFlags.DEFAULT_FLAGS, lookupProvider);
    }

    @Override
    protected void generate() {
        ModBlocks.SELF_DROPPING_BLOCKS.forEach(block -> {
            Block value = block.get();

            if (value instanceof SlabBlock) {
                this.add(value, this::createSlabItemTable);
                return;
            }

            if (value instanceof DoorBlock) {
                this.add(value, this::createDoorTable);
                return;
            }

            this.dropSelf(value);
        });

        ModBlocks.WOOD_BLOCK_SETS.forEach(this::addWoodBlockSetLoot);

        this.add(
                ModBlocks.GREAT_SWAMP_VINES.get(),
                block -> this.createShearsOnlyDrop(ModBlocks.GREAT_SWAMP_VINES.get())
        );

        this.add(
                ModBlocks.GREAT_SWAMP_VINES_PLANT.get(),
                block -> this.createShearsOnlyDrop(ModBlocks.GREAT_SWAMP_VINES.get())
        );
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return Stream.concat(
                Stream.concat(
                        ModBlocks.SELF_DROPPING_BLOCKS.stream().map(block -> (Block) block.get()),
                        ModBlocks.WOOD_SET_BLOCKS.stream().map(block -> (Block) block.get())
                ),
                Stream.of(
                        ModBlocks.GREAT_SWAMP_VINES.get(),
                        ModBlocks.GREAT_SWAMP_VINES_PLANT.get()
                )
        ).toList();
    }

    private void addWoodBlockSetLoot(ModBlocks.WoodBlockSet set) {
        this.dropSelf(set.log().get());
        this.dropSelf(set.wood().get());
        this.dropSelf(set.strippedLog().get());
        this.dropSelf(set.strippedWood().get());
        this.dropSelf(set.planks().get());
        this.dropSelf(set.stairs().get());
        this.add(set.slab().get(), this::createSlabItemTable);
        this.dropSelf(set.fence().get());
        this.dropSelf(set.fenceGate().get());
        this.add(set.door().get(), this::createDoorTable);
        this.dropSelf(set.trapdoor().get());
//        this.dropSelf(set.pressurePlate().get());
//        this.dropSelf(set.button().get());
//        this.dropSelf(set.sign().get());
//        this.dropOther(set.wallSign().get(), set.sign().get());
//        this.dropSelf(set.hangingSign().get());
//        this.dropOther(set.wallHangingSign().get(), set.hangingSign().get());
        this.add(
                set.leaves().get(),
                block -> this.createShearsOrSilkTouchOnlyDrop(set.leaves().get())
        );
    }
}
