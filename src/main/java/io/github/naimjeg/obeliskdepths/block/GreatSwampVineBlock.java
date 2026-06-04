package io.github.naimjeg.obeliskdepths.block;

import com.mojang.serialization.MapCodec;
import io.github.naimjeg.obeliskdepths.registry.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

public class GreatSwampVineBlock extends GrowingPlantHeadBlock {
    public static final MapCodec<GreatSwampVineBlock> CODEC =
            simpleCodec(GreatSwampVineBlock::new);

    private static final VoxelShape SHAPE =
            Block.box(4.0D, 0.0D, 4.0D, 12.0D, 16.0D, 12.0D);

    public GreatSwampVineBlock(BlockBehaviour.Properties properties) {
        super(
                properties,
                Direction.DOWN,
                SHAPE,
                false,
                0.1D
        );
    }

    @Override
    protected MapCodec<GreatSwampVineBlock> codec() {
        return CODEC;
    }

    @Override
    protected int getBlocksToGrowWhenBonemealed(RandomSource random) {
        return 1 + random.nextInt(3);
    }

    @Override
    protected boolean canGrowInto(BlockState state) {
        return state.isAir();
    }

    @Override
    protected Block getBodyBlock() {
        return ModBlocks.GREAT_SWAMP_VINES_PLANT.get();
    }
}