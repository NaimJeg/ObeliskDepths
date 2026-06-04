package io.github.naimjeg.obeliskdepths.block.multipart;

import com.mojang.serialization.MapCodec;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.jspecify.annotations.Nullable;

public abstract class AbstractMultiPartBlock<P extends Enum<P> & MultiPartPart> extends Block {
    private static final int SILENT_PART_REMOVAL_FLAGS = Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS;

    private final P[] parts;
    private final P mainPart;

    protected AbstractMultiPartBlock(BlockBehaviour.Properties properties, P[] parts) {
        super(properties);
        this.parts = parts.clone();
        this.mainPart = validateParts(this.parts);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(ignored -> this);
    }

    protected abstract EnumProperty<P> getPartProperty();

    protected abstract BlockState getBasePlacementState(BlockPlaceContext context);

    protected Vec3i transformOffset(BlockState state, Vec3i canonicalOffset) {
        return canonicalOffset;
    }

    protected boolean hasValidSupport(BlockState mainState, LevelReader level, BlockPos mainPos) {
        return true;
    }

    protected void onStructureRemoved(Level level, BlockPos mainPos, BlockState referenceState) {
    }

    public final P[] getParts() {
        return this.parts.clone();
    }

    protected final P getMainPart() {
        return this.mainPart;
    }

    protected final boolean isMainPart(BlockState state) {
        return state.getValue(this.getPartProperty()).isMainPart();
    }

    protected final Vec3i getPartOffset(BlockState state, P part) {
        return this.transformOffset(state, part.canonicalOffset());
    }

    protected final BlockPos getMainPartPos(BlockPos partPos, BlockState partState) {
        P part = partState.getValue(this.getPartProperty());
        Vec3i offset = this.getPartOffset(partState, part);
        return partPos.offset(-offset.getX(), -offset.getY(), -offset.getZ());
    }

    protected final BlockPos getPartPos(BlockPos mainPos, BlockState sharedState, P part) {
        Vec3i offset = this.getPartOffset(sharedState, part);
        return mainPos.offset(offset.getX(), offset.getY(), offset.getZ());
    }

    protected final BlockState createPartState(BlockState sharedState, P part) {
        return sharedState.setValue(this.getPartProperty(), part);
    }

    protected final boolean isCompleteStructure(LevelReader level, BlockPos partPos, BlockState partState) {
        if (!partState.is(this) || !partState.hasProperty(this.getPartProperty())) {
            return false;
        }

        BlockPos mainPos = this.getMainPartPos(partPos, partState);
        for (P part : this.parts) {
            BlockPos currentPos = this.getPartPos(mainPos, partState, part);
            if (isOutsideBuildHeight(level, currentPos)) {
                return false;
            }

            BlockState currentState = level.getBlockState(currentPos);
            if (!this.isExpectedStructurePart(level, mainPos, currentPos, currentState, part)) {
                return false;
            }
        }

        return this.hasValidSupport(this.createPartState(partState, this.mainPart), level, mainPos);
    }

    protected final void forEachPart(BlockPos mainPos, BlockState sharedState, BiConsumer<P, BlockPos> consumer) {
        for (P part : this.parts) {
            consumer.accept(part, this.getPartPos(mainPos, sharedState, part));
        }
    }

    protected final void removeOtherParts(Level level, BlockPos alreadyRemovingPos, BlockState referenceState) {
        BlockPos mainPos = this.getMainPartPos(alreadyRemovingPos, referenceState);
        this.removeOtherParts(level, mainPos, alreadyRemovingPos, referenceState);
    }

    protected final <T extends Comparable<T>> void updateSharedProperty(
            Level level,
            BlockPos anyPartPos,
            BlockState anyPartState,
            Property<T> property,
            T value,
            int flags
    ) {
        if (!anyPartState.is(this) || !anyPartState.hasProperty(this.getPartProperty())) {
            return;
        }

        BlockPos mainPos = this.getMainPartPos(anyPartPos, anyPartState);
        for (P part : this.parts) {
            BlockPos partPos = this.getPartPos(mainPos, anyPartState, part);
            if (!level.hasChunkAt(partPos)) {
                return;
            }

            BlockState currentState = level.getBlockState(partPos);
            if (!currentState.is(this)
                    || !currentState.hasProperty(this.getPartProperty())
                    || !currentState.hasProperty(property)
                    || currentState.getValue(this.getPartProperty()) != part
                    || !this.getMainPartPos(partPos, currentState).equals(mainPos)) {
                return;
            }
        }

        for (P part : this.parts) {
            BlockPos partPos = this.getPartPos(mainPos, anyPartState, part);
            BlockState currentState = level.getBlockState(partPos);

            level.setBlock(partPos, currentState.setValue(property, value), flags);
        }
    }

    @Override
    public final @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState sharedState = this.getBasePlacementState(context);
        if (sharedState == null) {
            return null;
        }

        sharedState = this.createPartState(sharedState, this.mainPart);
        Level level = context.getLevel();
        BlockPos mainPos = context.getClickedPos();

        for (P part : this.parts) {
            BlockPos partPos = this.getPartPos(mainPos, sharedState, part);
            if (isOutsideBuildHeight(level, partPos) || !level.hasChunkAt(partPos)) {
                return null;
            }

            if (!level.getBlockState(partPos).canBeReplaced(context)) {
                return null;
            }
        }

        return this.hasValidSupport(sharedState, level, mainPos) ? sharedState : null;
    }

    @Override
    public final void setPlacedBy(
            Level level,
            BlockPos pos,
            BlockState state,
            @Nullable LivingEntity placer,
            ItemStack stack
    ) {
        Set<BlockPos> placed = new HashSet<>();

        for (P part : this.parts) {
            if (part == this.mainPart) {
                continue;
            }

            BlockPos partPos = this.getPartPos(pos, state, part);
            if (isOutsideBuildHeight(level, partPos) || !level.hasChunkAt(partPos)) {
                rollbackPlacement(level, pos, state, placed);
                return;
            }

            if (!level.setBlock(partPos, this.createPartState(state, part), Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE)) {
                rollbackPlacement(level, pos, state, placed);
                return;
            }

            placed.add(partPos);
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (state.is(this) && state.hasProperty(this.getPartProperty())) {
            BlockPos mainPos = this.getMainPartPos(pos, state);
            MultipartRemovalGuards.MultipartStructureKey key =
                    this.structureKey(level, mainPos);
            boolean markPreRemoved = !level.isClientSide()
                    && !MultipartRemovalGuards.isCurrentlyRemoving(key);

            MultipartRemovalGuards.runRemoving(
                    key,
                    () -> this.removeStructureFromInitiatingPart(
                            level,
                            mainPos,
                            pos,
                            state,
                            key,
                            markPreRemoved
                    )
            );
        }

        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void destroy(LevelAccessor level, BlockPos pos, BlockState state) {
        if (level instanceof Level realLevel
                && state.hasProperty(this.getPartProperty())
                && realLevel.isClientSide()
                && state.is(this)) {
            BlockPos mainPos = this.getMainPartPos(pos, state);
            MultipartRemovalGuards.MultipartStructureKey key =
                    this.structureKey(realLevel, mainPos);
            if (!MultipartRemovalGuards.isCurrentlyRemoving(key)) {
                MultipartRemovalGuards.runRemoving(key, () ->
                        this.removeOtherParts(realLevel, mainPos, pos, state)
                );
            }
        }

        super.destroy(level, pos, state);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (state.hasProperty(this.getPartProperty()) && state.is(this)) {
            BlockPos mainPos = this.getMainPartPos(pos, state);
            MultipartRemovalGuards.MultipartStructureKey key =
                    this.structureKey(level, mainPos);

            if (!MultipartRemovalGuards.isCurrentlyRemoving(key)
                    && !MultipartRemovalGuards.consumePreRemoved(key)) {
                MultipartRemovalGuards.runRemoving(key, () -> {
                    MultipartRemovalGuards.runHookOnce(
                            key,
                            () -> this.onStructureRemoved(level, mainPos, state)
                    );
                    this.removeOtherParts(level, mainPos, pos, state);
                });
            }
        }

        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            LevelReader level,
            ScheduledTickAccess ticks,
            BlockPos pos,
            Direction directionToNeighbor,
            BlockPos neighborPos,
            BlockState neighborState,
            RandomSource random
    ) {
        if (state.hasProperty(this.getPartProperty())) {
            BlockPos mainPos = this.getMainPartPos(pos, state);

            if (level instanceof Level realLevel
                    && state.is(this)
                    && MultipartRemovalGuards.isCurrentlyRemoving(
                    this.structureKey(realLevel, mainPos)
            )) {
                return state;
            }

            if (!this.hasValidSupport(
                    this.createPartState(state, this.mainPart),
                    level,
                    mainPos
            )) {
                return Blocks.AIR.defaultBlockState();
            }

            for (P part : this.parts) {
                BlockPos expectedPos = this.getPartPos(mainPos, state, part);
                if (!expectedPos.equals(neighborPos)) {
                    continue;
                }

                if (!this.isExpectedStructurePart(
                        level,
                        mainPos,
                        expectedPos,
                        neighborState,
                        part
                )) {
                    return Blocks.AIR.defaultBlockState();
                }
            }
        }

        return super.updateShape(
                state,
                level,
                ticks,
                pos,
                directionToNeighbor,
                neighborPos,
                neighborState,
                random
        );
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (!state.hasProperty(this.getPartProperty())) {
            return super.canSurvive(state, level, pos);
        }

        BlockPos mainPos = this.getMainPartPos(pos, state);
        return this.hasValidSupport(this.createPartState(state, this.mainPart), level, mainPos);
    }

    @Override
    protected void onExplosionHit(
            BlockState state,
            ServerLevel level,
            BlockPos pos,
            Explosion explosion,
            BiConsumer<ItemStack, BlockPos> onHit
    ) {
        if (!state.hasProperty(this.getPartProperty())) {
            super.onExplosionHit(state, level, pos, explosion, onHit);
            return;
        }

        BlockPos mainPos = this.getMainPartPos(pos, state);
        MultipartRemovalGuards.MultipartStructureKey key =
                this.structureKey(level, mainPos);
        boolean markPreRemoved =
                !MultipartRemovalGuards.isCurrentlyRemoving(key);

        MultipartRemovalGuards.runRemoving(
                key,
                () -> this.removeStructureFromInitiatingPart(
                        level,
                        mainPos,
                        pos,
                        state,
                        key,
                        markPreRemoved
                )
        );

        super.onExplosionHit(state, level, pos, explosion, onHit);
    }

    private void removeOtherParts(Level level, BlockPos mainPos, BlockPos alreadyRemovingPos, BlockState referenceState) {
        for (P part : this.parts) {
            BlockPos partPos = this.getPartPos(mainPos, referenceState, part);
            if (partPos.equals(alreadyRemovingPos) || !level.hasChunkAt(partPos)) {
                continue;
            }

            BlockState currentState = level.getBlockState(partPos);
            if (!this.isExpectedStructurePart(level, mainPos, partPos, currentState, part)) {
                continue;
            }

            BlockState replacement = currentState.getFluidState().createLegacyBlock();
            level.setBlock(partPos, replacement, SILENT_PART_REMOVAL_FLAGS);
        }
    }

    private boolean isExpectedStructurePart(
            LevelReader level,
            BlockPos mainPos,
            BlockPos partPos,
            BlockState state,
            P expectedPart
    ) {
        return !isOutsideBuildHeight(level, partPos)
                && state.is(this)
                && state.hasProperty(this.getPartProperty())
                && state.getValue(this.getPartProperty()) == expectedPart
                && this.getMainPartPos(partPos, state).equals(mainPos);
    }

    private void rollbackPlacement(Level level, BlockPos mainPos, BlockState state, Set<BlockPos> placed) {
        MultipartRemovalGuards.MultipartStructureKey key =
                this.structureKey(level, mainPos);
        MultipartRemovalGuards.runRemoving(key, () -> {
            for (BlockPos placedPos : placed) {
                if (!level.hasChunkAt(placedPos)) {
                    continue;
                }

                BlockState placedState = level.getBlockState(placedPos);
                if (placedState.is(this)) {
                    level.setBlock(placedPos, placedState.getFluidState().createLegacyBlock(), SILENT_PART_REMOVAL_FLAGS);
                }
            }

            if (level.hasChunkAt(mainPos) && level.getBlockState(mainPos).is(this)) {
                level.setBlock(mainPos, state.getFluidState().createLegacyBlock(), SILENT_PART_REMOVAL_FLAGS);
            }
        });
    }

    private P validateParts(P[] parts) {
        if (parts.length == 0) {
            throw new IllegalArgumentException("Multipart layout must contain at least one part.");
        }

        P main = null;
        Set<Vec3i> offsets = new HashSet<>();

        for (P part : parts) {
            if (!offsets.add(part.canonicalOffset())) {
                throw new IllegalArgumentException("Duplicate multipart offset: " + part.canonicalOffset());
            }

            if (part.isMainPart()) {
                if (main != null) {
                    throw new IllegalArgumentException("Multipart layout must contain exactly one main part.");
                }

                main = part;
            }
        }

        if (main == null) {
            throw new IllegalArgumentException("Multipart layout must contain exactly one main part.");
        }

        return main;
    }

    private void removeStructureFromInitiatingPart(
            Level level,
            BlockPos mainPos,
            BlockPos alreadyRemovingPos,
            BlockState referenceState,
            MultipartRemovalGuards.MultipartStructureKey key,
            boolean markPreRemoved
    ) {
        boolean preRemovedMarked = false;
        try {
            if (!level.isClientSide()) {
                MultipartRemovalGuards.runHookOnce(
                        key,
                        () -> this.onStructureRemoved(level, mainPos, referenceState)
                );

                if (markPreRemoved) {
                    MultipartRemovalGuards.markPreRemoved(key);
                    preRemovedMarked = true;
                }
            }

            this.removeOtherParts(
                    level,
                    mainPos,
                    alreadyRemovingPos,
                    referenceState
            );
        } catch (RuntimeException | Error exception) {
            if (preRemovedMarked) {
                MultipartRemovalGuards.unmarkPreRemoved(key);
            }
            throw exception;
        }
    }

    private MultipartRemovalGuards.MultipartStructureKey structureKey(
            Level level,
            BlockPos mainPos
    ) {
        return MultipartRemovalGuards.MultipartStructureKey.of(
                level,
                this,
                mainPos
        );
    }

    private static boolean isOutsideBuildHeight(LevelReader level, BlockPos pos) {
        return pos.getY() < level.getMinY() || pos.getY() >= level.getMaxY();
    }
}
