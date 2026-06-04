package io.github.naimjeg.obeliskdepths.block;

import com.mojang.serialization.MapCodec;
import io.github.naimjeg.obeliskdepths.block.multipart.AbstractMultiPartBlock;
import io.github.naimjeg.obeliskdepths.dungeon.portal.DungeonPortalEntityService;
import io.github.naimjeg.obeliskdepths.menu.ObeliskPortalMenu;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

public class ObeliskBlock extends AbstractMultiPartBlock<ObeliskPart> {
    public static final MapCodec<ObeliskBlock> CODEC = simpleCodec(ObeliskBlock::new);

    public static final EnumProperty<ObeliskPart> PART =
            EnumProperty.create("part", ObeliskPart.class);

    public ObeliskBlock(BlockBehaviour.Properties properties) {
        super(properties, ObeliskPart.values());
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(PART, ObeliskPart.BOTTOM));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected EnumProperty<ObeliskPart> getPartProperty() {
        return PART;
    }

    @Override
    protected BlockState getBasePlacementState(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(PART, ObeliskPart.BOTTOM);
    }

    @Override
    protected boolean hasValidSupport(BlockState mainState, LevelReader level, BlockPos mainPos) {
        BlockPos below = mainPos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (level.dimension().equals(ModDimensions.OBELISK_DEPTHS_LEVEL)) {
            player.sendOverlayMessage(
                    Component.translatable("message.obeliskdepths.obelisk.inside_dungeon_denied")
            );
            return InteractionResult.FAIL;
        }

        BlockPos bottomPos = this.getMainPartPos(pos, state);

        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, opener) -> new ObeliskPortalMenu(
                        containerId,
                        inventory,
                        ContainerLevelAccess.create(level, bottomPos),
                        bottomPos
                ),
                Component.translatable("container.obeliskdepths.obelisk_portal")
        ));

        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    protected void onStructureRemoved(Level level, BlockPos mainPos, BlockState referenceState) {
        if (level instanceof ServerLevel sourceLevel
                && !sourceLevel.dimension().equals(ModDimensions.OBELISK_DEPTHS_LEVEL)) {
            ServerLevel dungeonLevel = sourceLevel.getServer()
                    .getLevel(ModDimensions.OBELISK_DEPTHS_LEVEL);

            if (dungeonLevel != null) {
                DungeonPortalEntityService.closeSessionsForSourceObelisk(
                        sourceLevel,
                        dungeonLevel,
                        sourceLevel.dimension(),
                        mainPos
                );
            }
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }
}
