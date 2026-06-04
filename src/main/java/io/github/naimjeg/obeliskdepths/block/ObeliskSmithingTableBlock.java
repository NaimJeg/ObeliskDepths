package io.github.naimjeg.obeliskdepths.block;

import io.github.naimjeg.obeliskdepths.menu.ObeliskTemperingMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SmithingTableBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class ObeliskSmithingTableBlock extends SmithingTableBlock {
    private static final Component CONTAINER_TITLE =
            Component.translatable("container.obeliskdepths.obelisk_tempering");

    public ObeliskSmithingTableBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MenuProvider getMenuProvider(
            BlockState state,
            Level level,
            BlockPos pos
    ) {
        return new SimpleMenuProvider(
                (containerId, inventory, player) -> new ObeliskTemperingMenu(
                        containerId,
                        inventory,
                        ContainerLevelAccess.create(level, pos)
                ),
                CONTAINER_TITLE
        );
    }
}
