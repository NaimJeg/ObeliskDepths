package io.github.naimjeg.obeliskdepths.block;

import java.util.function.Supplier;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import org.jspecify.annotations.Nullable;

public final class StrippableRotatedPillarBlock extends RotatedPillarBlock {
    private final Supplier<? extends Block> strippedBlock;

    public StrippableRotatedPillarBlock(
            Supplier<? extends Block> strippedBlock,
            Properties properties
    ) {
        super(properties);
        this.strippedBlock = strippedBlock;
    }

    @Override
    public @Nullable BlockState getToolModifiedState(
            BlockState state,
            UseOnContext context,
            ItemAbility itemAbility,
            boolean simulate
    ) {
        if (itemAbility == ItemAbilities.AXE_STRIP) {
            return this.strippedBlock.get()
                    .withPropertiesOf(state);
        }

        return super.getToolModifiedState(
                state,
                context,
                itemAbility,
                simulate
        );
    }
}
