package io.github.naimjeg.obeliskdepths.block;

import com.mojang.serialization.MapCodec;
import io.github.naimjeg.obeliskdepths.registry.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GrowingPlantBodyBlock;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.VoxelShape;

public class GreatSwampVinePlantBlock extends GrowingPlantBodyBlock {
    public static final MapCodec<GreatSwampVinePlantBlock> CODEC =
            simpleCodec(GreatSwampVinePlantBlock::new);

    private static final VoxelShape SHAPE =
            Block.box(4.0D, 0.0D, 4.0D, 12.0D, 16.0D, 12.0D);

    public GreatSwampVinePlantBlock(BlockBehaviour.Properties properties) {
        super(
                properties,
                Direction.DOWN,
                SHAPE,
                false
        );
    }

    @Override
    protected MapCodec<GreatSwampVinePlantBlock> codec() {
        return CODEC;
    }

    @Override
    protected GrowingPlantHeadBlock getHeadBlock() {
        return ModBlocks.GREAT_SWAMP_VINES.get();
    }
}