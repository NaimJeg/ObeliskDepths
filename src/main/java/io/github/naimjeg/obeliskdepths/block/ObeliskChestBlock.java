package io.github.naimjeg.obeliskdepths.block;

import com.mojang.serialization.MapCodec;
import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.block.entity.ObeliskChestBlockEntity;
import io.github.naimjeg.obeliskdepths.block.multipart.AbstractMultiPartEntityBlock;
import io.github.naimjeg.obeliskdepths.dungeon.reward.DungeonRewardClaimResult;
import io.github.naimjeg.obeliskdepths.dungeon.reward.DungeonRewardClaim;
import io.github.naimjeg.obeliskdepths.registry.ModBlockEntities;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public class ObeliskChestBlock extends AbstractMultiPartEntityBlock<ObeliskChestPart> {
    public static final MapCodec<ObeliskChestBlock> CODEC = simpleCodec(ObeliskChestBlock::new);

    public static final EnumProperty<ObeliskChestPart> PART =
            EnumProperty.create("part", ObeliskChestPart.class);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty OPENED = BooleanProperty.create("opened");

    public ObeliskChestBlock(BlockBehaviour.Properties properties) {
        super(properties, ObeliskChestPart.values());
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(PART, ObeliskChestPart.BOTTOM_FRONT_LEFT)
                .setValue(FACING, Direction.NORTH)
                .setValue(OPENED, false));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return state.getValue(PART) == ObeliskChestPart.BOTTOM_FRONT_CENTER
                ? RenderShape.MODEL
                : RenderShape.INVISIBLE;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected EnumProperty<ObeliskChestPart> getPartProperty() {
        return PART;
    }

    @Override
    protected BlockState getBasePlacementState(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(PART, ObeliskChestPart.BOTTOM_FRONT_LEFT)
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(OPENED, false);
    }

    @Override
    protected Vec3i transformOffset(BlockState state, Vec3i canonicalOffset) {
        return ObeliskChestPart.transformCanonicalOffset(state.getValue(FACING), canonicalOffset);
    }

    @Override
    protected BlockEntity createMainBlockEntity(BlockPos pos, BlockState state) {
        return new ObeliskChestBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    ) {
        if (level.isClientSide()
                || type != ModBlockEntities.OBELISK_CHEST.get()
                || !state.hasProperty(PART)
                || state.getValue(PART) != ObeliskChestPart.BOTTOM_FRONT_LEFT) {
            return null;
        }

        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof ObeliskChestBlockEntity chest) {
                ObeliskChestBlockEntity.serverTick(tickLevel, pos, tickState, chest);
            }
        };
    }

    public Optional<ObeliskChestBlockEntity> placeRewardStructure(
            ServerLevel level,
            BlockPos mainPos,
            Direction facing
    ) {
        BlockState sharedState = this.defaultBlockState()
                .setValue(PART, ObeliskChestPart.BOTTOM_FRONT_LEFT)
                .setValue(FACING, facing)
                .setValue(OPENED, false);

        Map<BlockPos, SavedBlock> originals = new HashMap<>();
        for (ObeliskChestPart part : this.getParts()) {
            BlockPos partPos = this.getPartPos(mainPos, sharedState, part);
            if (partPos.getY() < level.getMinY()
                    || partPos.getY() >= level.getMaxY()
                    || !level.hasChunkAt(partPos)) {
                return Optional.empty();
            }

            BlockState current = level.getBlockState(partPos);
            if ((!current.isAir() && !current.canBeReplaced())
                    || level.getBlockEntity(partPos) != null) {
                return Optional.empty();
            }

            BlockEntity originalEntity = level.getBlockEntity(partPos);
            originals.put(
                    partPos.immutable(),
                    new SavedBlock(
                            current,
                            originalEntity == null ? null : originalEntity.saveWithFullMetadata(level.registryAccess())
                    )
            );
        }

        if (!this.hasValidSupport(sharedState, level, mainPos)) {
            return Optional.empty();
        }

        BlockPos supportPos = mainPos.below();
        if (!level.hasChunkAt(supportPos)
                || !level.getBlockState(supportPos).isFaceSturdy(level, supportPos, Direction.UP)) {
            return Optional.empty();
        }

        try {
            for (ObeliskChestPart part : this.getParts()) {
                BlockPos partPos = this.getPartPos(mainPos, sharedState, part);
                BlockState partState = this.createPartState(sharedState, part);
                if (!level.setBlock(partPos, partState, Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS)) {
                    rollbackRewardStructure(level, originals);
                    return Optional.empty();
                }
            }

            BlockState placedMain = level.getBlockState(mainPos);
            if (!this.isCompleteStructure(level, mainPos, placedMain)) {
                rollbackRewardStructure(level, originals);
                return Optional.empty();
            }

            BlockEntity blockEntity = level.getBlockEntity(mainPos);
            if (blockEntity instanceof ObeliskChestBlockEntity chest) {
                return Optional.of(chest);
            }

            rollbackRewardStructure(level, originals);
            return Optional.empty();
        } catch (RuntimeException exception) {
            rollbackRewardStructure(level, originals);
            ObeliskDepths.LOGGER.warn(
                    "Obelisk Chest reward placement failed and was rolled back at {}",
                    mainPos,
                    exception
            );
            return Optional.empty();
        }
    }

    public boolean removeRewardStructure(ServerLevel level, BlockPos mainPos) {
        BlockState state = level.getBlockState(mainPos);
        if (!state.is(this) || !state.hasProperty(PART)) {
            return false;
        }

        BlockPos resolvedMain = this.getMainPartPos(mainPos, state);
        List<BlockPos> positions = this.getRewardStructurePartPositions(resolvedMain, state);
        if (positions.size() != this.getParts().length || !areRewardStructureChunksLoaded(level, positions)) {
            return false;
        }

        boolean removedAny = false;
        for (BlockPos partPos : positions) {
            if (level.getBlockState(partPos).is(this)
                    && level.getBlockState(partPos).hasProperty(PART)) {
                removedAny |= level.removeBlock(partPos, false);
            }
        }

        return removedAny;
    }

    public boolean isCompleteRewardStructure(Level level, BlockPos mainPos) {
        BlockState state = level.getBlockState(mainPos);
        return state.is(this)
                && state.hasProperty(PART)
                && state.getValue(PART) == ObeliskChestPart.BOTTOM_FRONT_LEFT
                && this.isCompleteStructure(level, mainPos, state);
    }

    public Optional<ObeliskChestBlockEntity> getCompleteRewardBlockEntity(ServerLevel level, BlockPos mainPos) {
        return this.isCompleteRewardStructure(level, mainPos)
                && level.getBlockEntity(mainPos) instanceof ObeliskChestBlockEntity chest
                ? Optional.of(chest)
                : Optional.empty();
    }

    public List<BlockPos> rewardPartPositions(BlockPos mainPos, Direction facing) {
        BlockState sharedState = this.defaultBlockState()
                .setValue(PART, ObeliskChestPart.BOTTOM_FRONT_LEFT)
                .setValue(FACING, facing)
                .setValue(OPENED, false);
        return getRewardStructurePartPositions(mainPos, sharedState);
    }

    public List<BlockPos> getRewardStructurePartPositions(
            BlockPos mainPos,
            BlockState sharedState
    ) {
        Set<BlockPos> positions = new HashSet<>();
        for (ObeliskChestPart part : this.getParts()) {
            BlockPos partPos = this.getPartPos(mainPos, sharedState, part).immutable();
            if (!positions.add(partPos)) {
                return List.of();
            }
        }

        return List.copyOf(positions);
    }

    public boolean areRewardStructureChunksLoaded(
            ServerLevel level,
            BlockPos mainPos,
            BlockState sharedState
    ) {
        return areRewardStructureChunksLoaded(
                level,
                getRewardStructurePartPositions(mainPos, sharedState)
        );
    }

    public boolean areRewardStructureChunksLoaded(
            ServerLevel level,
            List<BlockPos> positions
    ) {
        if (positions.size() != this.getParts().length) {
            return false;
        }

        for (BlockPos pos : positions) {
            if (!level.hasChunkAt(pos)) {
                return false;
            }
        }

        return true;
    }

    public BlockPos resolveMainPos(BlockPos pos, BlockState state) {
        return this.getMainPartPos(pos, state);
    }

    public boolean setOpened(ServerLevel level, BlockPos mainPos, boolean opened) {
        BlockState mainState = level.getBlockState(mainPos);
        if (mainState.is(this) && mainState.hasProperty(OPENED)) {
            this.updateSharedProperty(level, mainPos, mainState, OPENED, opened, Block.UPDATE_ALL);
            BlockState updated = level.getBlockState(mainPos);
            return updated.is(this)
                    && updated.hasProperty(OPENED)
                    && updated.getValue(OPENED) == opened;
        }

        return false;
    }

    private static void rollbackRewardStructure(ServerLevel level, Map<BlockPos, SavedBlock> originals) {
        for (Map.Entry<BlockPos, SavedBlock> entry : originals.entrySet()) {
            if (level.hasChunkAt(entry.getKey())) {
                level.setBlock(entry.getKey(), entry.getValue().state(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                if (entry.getValue().blockEntityTag() != null) {
                    BlockEntity restored = BlockEntity.loadStatic(
                            entry.getKey(),
                            entry.getValue().state(),
                            entry.getValue().blockEntityTag(),
                            level.registryAccess()
                    );
                    if (restored != null) {
                        level.setBlockEntity(restored);
                    }
                }
            }
        }
    }

    private record SavedBlock(
            BlockState state,
            CompoundTag blockEntityTag
    ) {
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

        BlockPos mainPos = this.getMainPartPos(pos, state);
        BlockState mainState = level.getBlockState(mainPos);
        if (!mainState.is(this)
                || !mainState.hasProperty(PART)
                || mainState.getValue(PART) != ObeliskChestPart.BOTTOM_FRONT_LEFT
                || !this.isCompleteStructure(level, mainPos, mainState)) {
            return InteractionResult.PASS;
        }

        if (mainState.getValue(OPENED)) {
            return InteractionResult.SUCCESS_SERVER;
        }

        BlockEntity blockEntity = level.getBlockEntity(mainPos);
        if (!(player instanceof ServerPlayer serverPlayer)
                || !(blockEntity instanceof ObeliskChestBlockEntity chest)) {
            return InteractionResult.PASS;
        }

        if (chest.rewardId().isEmpty() || chest.instanceId().isEmpty()) {
            return InteractionResult.PASS;
        }

        DungeonRewardClaimResult result = DungeonRewardClaim.tryClaimReward(
                serverPlayer,
                chest.rewardId().get(),
                chest.instanceId().get(),
                mainPos
        );
        if (result == DungeonRewardClaimResult.SUCCESS) {
            return InteractionResult.SUCCESS_SERVER;
        }

        return result == DungeonRewardClaimResult.ALREADY_OPENED
                || result == DungeonRewardClaimResult.ALREADY_CLAIMED
                ? InteractionResult.SUCCESS_SERVER
                : InteractionResult.PASS;
    }

    @Override
    protected void onStructureRemoved(Level level, BlockPos mainPos, BlockState referenceState) {
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, FACING, OPENED);
    }
}
