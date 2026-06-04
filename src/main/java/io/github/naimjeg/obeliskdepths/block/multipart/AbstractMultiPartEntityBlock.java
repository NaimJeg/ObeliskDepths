package io.github.naimjeg.obeliskdepths.block.multipart;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public abstract class AbstractMultiPartEntityBlock<P extends Enum<P> & MultiPartPart>
        extends AbstractMultiPartBlock<P>
        implements EntityBlock {
    protected AbstractMultiPartEntityBlock(BlockBehaviour.Properties properties, P[] parts) {
        super(properties, parts);
    }

    @Override
    public final @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (!state.hasProperty(this.getPartProperty()) || !this.isMainPart(state)) {
            return null;
        }

        return this.createMainBlockEntity(pos, state);
    }

    protected abstract BlockEntity createMainBlockEntity(BlockPos pos, BlockState state);
}
