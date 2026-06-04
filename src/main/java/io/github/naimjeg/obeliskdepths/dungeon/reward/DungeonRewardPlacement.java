package io.github.naimjeg.obeliskdepths.dungeon.reward;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.block.ObeliskChestBlock;
import io.github.naimjeg.obeliskdepths.block.entity.ObeliskChestBlockEntity;
import io.github.naimjeg.obeliskdepths.dungeon.artifact.DungeonRuntimeArtifactRecord;
import io.github.naimjeg.obeliskdepths.dungeon.artifact.DungeonRuntimeArtifactType;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonStatus;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonGeneratedRoom;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteProjectionCache;
import io.github.naimjeg.obeliskdepths.dungeon.site.ResolvedDungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import io.github.naimjeg.obeliskdepths.dungeon.territory.DungeonBounds;
import io.github.naimjeg.obeliskdepths.registry.ModBlocks;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class DungeonRewardPlacement {
    public static final int MAX_PLACEMENT_FAILURES = 5;
    private static final int PLACEMENT_SEARCH_NEGATIVE_OFFSET = -8;
    private static final int PLACEMENT_SEARCH_POSITIVE_OFFSET_EXCLUSIVE = 8;
    private static final Direction[] HORIZONTAL_FACINGS = {
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST
    };

    private DungeonRewardPlacement() {
    }

    public static boolean tryPlaceReward(
            ServerLevel level,
            DungeonRewardRecord reward
    ) {
        if (!level.dimension().equals(ModDimensions.OBELISK_DEPTHS_LEVEL)) {
            return false;
        }

        if (reward.status().terminal()
                || reward.status() == DungeonRewardStatus.CLAIMING
                || reward.status() == DungeonRewardStatus.PLACEMENT_FAILED
                || reward.status() == DungeonRewardStatus.OPENED) {
            return false;
        }

        DungeonManagerSavedData data = DungeonManagerSavedData.get(level);
        Optional<DungeonInstance> instance = data.instances().get(reward.instanceId());
        if (instance.isEmpty()) {
            recordPlacementFailure(level, data, reward, "missing instance", Optional.empty());
            return false;
        }

        if (instance.get().status() != DungeonStatus.REWARD_PHASE) {
            recordPlacementFailure(level, data, reward, "instance not in reward phase", Optional.empty());
            return false;
        }

        if (reward.placedMainPos().isPresent()) {
            Optional<ObeliskChestBlockEntity> existing = resolveOwnedChest(
                    level,
                    reward,
                    reward.placedMainPos().get()
            );
            if (existing.isPresent()) {
                if (reward.status() != DungeonRewardStatus.AVAILABLE) {
                    finishSuccessfulPlacement(
                            data,
                            reward,
                            reward.placedMainPos().get(),
                            facingAt(level, reward.placedMainPos().get())
                    );
                }
                return true;
            }

            ObeliskDepths.LOGGER.warn(
                    "Saved reward chest position is not owned by reward; searching replacement: instance={}, reward={}, savedPos={}",
                    reward.instanceId(),
                    reward.rewardId(),
                    reward.placedMainPos().get()
            );
        }

        data.rewards().markPlacementPending(reward);
        Optional<BlockPos> origin = resolvePlacementOrigin(level, reward);
        if (origin.isEmpty()) {
            recordPlacementFailure(level, data, reward, "missing boss death or authored fallback origin", Optional.empty());
            return false;
        }

        List<ItemStack> contents = DungeonRewardDelivery.generate(level, instance.get(), reward);
        if (contents.isEmpty() || contents.size() > ObeliskChestBlockEntity.REWARD_CAPACITY) {
            recordPlacementFailure(level, data, reward, "reward inventory generation failed", origin);
            return false;
        }

        PlacementSearchStats stats = new PlacementSearchStats(origin.get());
        List<BlockPos> candidates = orderedPlacementCandidates(origin.get());
        ObeliskChestBlock chestBlock = ModBlocks.OBELISK_CHEST.get();
        for (BlockPos candidate : candidates) {
            stats.positionsTested++;
            for (Direction facing : HORIZONTAL_FACINGS) {
                stats.orientationsTested++;
                Optional<PlacedRewardChest> placed = tryPlaceAt(
                        level,
                        data,
                        reward,
                        chestBlock,
                        candidate,
                        facing,
                        contents,
                        stats
                );

                if (placed.isPresent()) {
                    finishSuccessfulPlacement(data, reward, placed.get().mainPos(), placed.get().facing());
                    ObeliskDepths.LOGGER.debug(
                            "Dungeon reward placement search succeeded: instance={}, reward={}, origin={}, positionsTested={}, orientationsTested={}, actualPos={}, actualFacing={}",
                            reward.instanceId(),
                            reward.rewardId(),
                            origin.get(),
                            stats.positionsTested,
                            stats.orientationsTested,
                            placed.get().mainPos(),
                            placed.get().facing()
                    );
                    return true;
                }
            }
        }

        recordPlacementFailure(level, data, reward, stats.summary(), origin);
        return false;
    }

    public static List<BlockPos> orderedPlacementCandidates(BlockPos origin) {
        List<BlockPos> candidates = new ArrayList<>(4096);
        for (int dy = PLACEMENT_SEARCH_NEGATIVE_OFFSET; dy < PLACEMENT_SEARCH_POSITIVE_OFFSET_EXCLUSIVE; dy++) {
            for (int dz = PLACEMENT_SEARCH_NEGATIVE_OFFSET; dz < PLACEMENT_SEARCH_POSITIVE_OFFSET_EXCLUSIVE; dz++) {
                for (int dx = PLACEMENT_SEARCH_NEGATIVE_OFFSET; dx < PLACEMENT_SEARCH_POSITIVE_OFFSET_EXCLUSIVE; dx++) {
                    candidates.add(origin.offset(dx, dy, dz).immutable());
                }
            }
        }

        candidates.sort(Comparator
                .comparingLong((BlockPos pos) -> squaredDistance(origin, pos))
                .thenComparingInt(pos -> Math.abs(pos.getY() - origin.getY()))
                .thenComparingInt(pos -> pos.getY() < origin.getY() ? 1 : 0)
                .thenComparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getZ));
        return List.copyOf(candidates);
    }

    public static long squaredDistance(BlockPos origin, BlockPos pos) {
        long dx = pos.getX() - origin.getX();
        long dy = pos.getY() - origin.getY();
        long dz = pos.getZ() - origin.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    static Optional<ObeliskChestBlockEntity> resolveOwnedChest(
            ServerLevel level,
            DungeonRewardRecord reward,
            BlockPos mainPos
    ) {
        ObeliskChestBlock chestBlock = ModBlocks.OBELISK_CHEST.get();
        if (!level.hasChunkAt(mainPos)
                || !chestBlock.isCompleteRewardStructure(level, mainPos)
                || !(level.getBlockEntity(mainPos) instanceof ObeliskChestBlockEntity chest)
                || !chest.matchesRewardRecord(
                reward.rewardId(),
                reward.instanceId(),
                reward.roomId(),
                reward.rewardSeed()
        )) {
            return Optional.empty();
        }

        return Optional.of(chest);
    }

    private static void finishSuccessfulPlacement(
            DungeonManagerSavedData data,
            DungeonRewardRecord reward,
            BlockPos mainPos,
            Direction facing
    ) {
        data.rewards().markAvailable(reward, mainPos);
        data.runtimeArtifacts().add(new DungeonRuntimeArtifactRecord(
                reward.instanceId(),
                DungeonRuntimeArtifactType.REWARD_CHEST,
                Optional.of(mainPos.immutable()),
                Optional.of(reward.rewardId()),
                false
        ));
        ObeliskDepths.LOGGER.debug(
                "Dungeon reward available: instance={}, reward={}, pos={}, facing={}",
                reward.instanceId(),
                reward.rewardId(),
                mainPos,
                facing
        );
    }

    private static Optional<PlacedRewardChest> tryPlaceAt(
            ServerLevel level,
            DungeonManagerSavedData data,
            DungeonRewardRecord reward,
            ObeliskChestBlock chestBlock,
            BlockPos mainPos,
            Direction facing,
            List<ItemStack> contents,
            PlacementSearchStats stats
    ) {
        PlacementRejection rejection = validateCandidate(level, data, chestBlock, mainPos, facing);
        if (rejection != PlacementRejection.NONE) {
            stats.record(rejection);
            return Optional.empty();
        }

        Optional<ObeliskChestBlockEntity> chest = chestBlock.placeRewardStructure(level, mainPos, facing);
        if (chest.isEmpty()) {
            stats.record(PlacementRejection.PLACEMENT_FAILED);
            return Optional.empty();
        }

        if (!chest.get().initializeReward(
                reward.rewardId(),
                reward.instanceId(),
                reward.roomId(),
                reward.rewardSeed(),
                reward.createdGameTime(),
                contents
        ) || !chest.get().matchesRewardRecord(
                reward.rewardId(),
                reward.instanceId(),
                reward.roomId(),
                reward.rewardSeed()
        ) || !chestBlock.isCompleteRewardStructure(level, mainPos)) {
            chestBlock.removeRewardStructure(level, mainPos);
            stats.record(PlacementRejection.INITIALIZATION_FAILED);
            return Optional.empty();
        }

        return Optional.of(new PlacedRewardChest(mainPos.immutable(), facing));
    }

    private static PlacementRejection validateCandidate(
            ServerLevel level,
            DungeonManagerSavedData data,
            ObeliskChestBlock chestBlock,
            BlockPos mainPos,
            Direction facing
    ) {
        List<BlockPos> footprint = chestBlock.rewardPartPositions(mainPos, facing);
        if (footprint.size() != chestBlock.getParts().length) {
            return PlacementRejection.DUPLICATE_PART_POSITION;
        }

        BlockPos supportPos = mainPos.below();
        if (!level.hasChunkAt(supportPos)
                || !level.getBlockState(supportPos).isFaceSturdy(level, supportPos, Direction.UP)) {
            return PlacementRejection.INVALID_SUPPORT;
        }

        Set<BlockPos> footprintSet = new HashSet<>(footprint);
        if (data.runtimeArtifacts().all().stream()
                .flatMap(artifact -> artifact.pos().stream())
                .anyMatch(footprintSet::contains)) {
            return PlacementRejection.RUNTIME_ARTIFACT;
        }

        for (BlockPos partPos : footprint) {
            if (partPos.getY() < level.getMinY() || partPos.getY() >= level.getMaxY()) {
                return PlacementRejection.OUT_OF_BUILD_HEIGHT;
            }

            if (!level.hasChunkAt(partPos)) {
                return PlacementRejection.UNLOADED_CHUNK;
            }

            if (level.getBlockEntity(partPos) != null) {
                return PlacementRejection.BLOCK_ENTITY;
            }

            BlockState current = level.getBlockState(partPos);
            if (current.is(chestBlock) || current.is(Blocks.CHEST)) {
                return PlacementRejection.CHEST_COLLISION;
            }

            if (!current.isAir() && !current.canBeReplaced()) {
                return PlacementRejection.NOT_REPLACEABLE;
            }
        }

        return PlacementRejection.NONE;
    }

    private static Optional<BlockPos> resolvePlacementOrigin(
            ServerLevel level,
            DungeonRewardRecord reward
    ) {
        return reward.preferredPlacementOrigin()
                .or(() -> resolveRewardMarker(level, reward).map(DungeonRewardMarker::mainPos))
                .map(BlockPos::immutable);
    }

    private static Optional<DungeonRewardMarker> resolveRewardMarker(
            ServerLevel level,
            DungeonRewardRecord reward
    ) {
        Optional<DungeonGeneratedRoom> room = resolveRewardRoom(level, reward);
        if (room.isEmpty()) {
            ObeliskDepths.LOGGER.warn(
                    "Dungeon reward marker resolution failed with missing room: instance={}, reward={}, room={}",
                    reward.instanceId(),
                    reward.rewardId(),
                    reward.roomId()
            );
            return Optional.empty();
        }

        DungeonBounds bounds = room.get().bounds();
        List<DungeonRewardMarker> markers = new ArrayList<>();
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.hasChunkAt(pos)) {
                        return Optional.empty();
                    }
                    BlockState state = level.getBlockState(pos);
                    if (state.is(Blocks.CHEST)) {
                        markers.add(new DungeonRewardMarker(pos, facingFromChest(state)));
                    }
                }
            }
        }

        if (markers.size() != 1) {
            ObeliskDepths.LOGGER.warn(
                    "Dungeon reward marker count invalid: instance={}, reward={}, room={}, bounds={}, markers={}",
                    reward.instanceId(),
                    reward.rewardId(),
                    room.get().id(),
                    bounds,
                    markers.size()
            );
            return Optional.empty();
        }

        ObeliskDepths.LOGGER.debug(
                "Dungeon reward marker resolved: instance={}, reward={}, room={}, pos={}, facing={}",
                reward.instanceId(),
                reward.rewardId(),
                room.get().id(),
                markers.getFirst().mainPos(),
                markers.getFirst().facing()
        );
        return Optional.of(markers.getFirst());
    }

    private static Optional<DungeonGeneratedRoom> resolveRewardRoom(
            ServerLevel level,
            DungeonRewardRecord reward
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(level);
        Optional<DungeonSite> site = data.instances().get(reward.instanceId())
                .flatMap(instance -> DungeonSiteProjectionCache.read(level, instance.siteKey())
                        .map(ResolvedDungeonSite::site)
                        .or(() -> data.sites().snapshot(instance.siteKey())));
        return site.flatMap(value -> value.rooms()
                .stream()
                .filter(room -> reward.roomId().map(room.id()::equals).orElse(room.type() == DungeonRoomType.BOSS))
                .findFirst());
    }

    private static void recordPlacementFailure(
            ServerLevel level,
            DungeonManagerSavedData data,
            DungeonRewardRecord reward,
            String reason,
            Optional<BlockPos> fallbackDeliveryPos
    ) {
        data.rewards().recordPlacementFailure(reward);
        boolean becameTerminal = false;
        if (reward.placementFailures() >= MAX_PLACEMENT_FAILURES) {
            becameTerminal = data.rewards().markPlacementFailed(reward);
        }
        ObeliskDepths.LOGGER.warn(
                "Dungeon reward placement {}: instance={}, reward={}, failures={}, reason={}",
                reward.status() == DungeonRewardStatus.PLACEMENT_FAILED ? "failed" : "pending",
                reward.instanceId(),
                reward.rewardId(),
                reward.placementFailures(),
                reason
        );
        if (becameTerminal) {
            notifyPlacementFallbackAvailable(level, data, reward);
            DungeonRewardClaim.claimPlacementFailureFallback(
                    level,
                    data,
                    reward,
                    fallbackDeliveryPos
            );
        }
    }

    private static void notifyPlacementFallbackAvailable(
            ServerLevel level,
            DungeonManagerSavedData data,
            DungeonRewardRecord reward
    ) {
        data.sessions().findByInstance(reward.instanceId()).ifPresent(session ->
                session.participants().stream()
                        .map(playerId -> level.getServer().getPlayerList().getPlayer(playerId))
                        .filter(player -> player != null && player.isAlive())
                        .forEach(player -> player.sendSystemMessage(Component.translatable(
                                "message.obeliskdepths.dungeon.reward_placement_failed"
                        )))
        );
    }

    private static Direction facingAt(ServerLevel level, BlockPos pos) {
        return facingFromChest(level.getBlockState(pos));
    }

    private static Direction facingFromChest(BlockState state) {
        return state.hasProperty(ChestBlock.FACING) ? state.getValue(ChestBlock.FACING) : Direction.NORTH;
    }

    private record PlacedRewardChest(BlockPos mainPos, Direction facing) {
    }

    private enum PlacementRejection {
        NONE,
        OUT_OF_BUILD_HEIGHT,
        UNLOADED_CHUNK,
        NOT_REPLACEABLE,
        BLOCK_ENTITY,
        RUNTIME_ARTIFACT,
        INVALID_SUPPORT,
        CHEST_COLLISION,
        DUPLICATE_PART_POSITION,
        PLACEMENT_FAILED,
        INITIALIZATION_FAILED
    }

    private static final class PlacementSearchStats {
        private final BlockPos origin;
        private int positionsTested;
        private int orientationsTested;
        private int outOfBuildHeight;
        private int unloadedChunk;
        private int notReplaceable;
        private int blockEntity;
        private int runtimeArtifact;
        private int invalidSupport;
        private int chestCollision;
        private int duplicatePartPosition;
        private int placementFailed;
        private int initializationFailed;

        private PlacementSearchStats(BlockPos origin) {
            this.origin = origin.immutable();
        }

        private void record(PlacementRejection rejection) {
            switch (rejection) {
                case OUT_OF_BUILD_HEIGHT -> this.outOfBuildHeight++;
                case UNLOADED_CHUNK -> this.unloadedChunk++;
                case NOT_REPLACEABLE -> this.notReplaceable++;
                case BLOCK_ENTITY -> this.blockEntity++;
                case RUNTIME_ARTIFACT -> this.runtimeArtifact++;
                case INVALID_SUPPORT -> this.invalidSupport++;
                case CHEST_COLLISION -> this.chestCollision++;
                case DUPLICATE_PART_POSITION -> this.duplicatePartPosition++;
                case PLACEMENT_FAILED -> this.placementFailed++;
                case INITIALIZATION_FAILED -> this.initializationFailed++;
                case NONE -> {
                }
            }
        }

        private String summary() {
            return "full placement search failed"
                    + ", origin=" + this.origin
                    + ", positionsTested=" + this.positionsTested
                    + ", orientationsTested=" + this.orientationsTested
                    + ", outOfBuildHeight=" + this.outOfBuildHeight
                    + ", unloadedChunk=" + this.unloadedChunk
                    + ", notReplaceable=" + this.notReplaceable
                    + ", blockEntity=" + this.blockEntity
                    + ", runtimeArtifact=" + this.runtimeArtifact
                    + ", invalidSupport=" + this.invalidSupport
                    + ", chestCollision=" + this.chestCollision
                    + ", duplicatePartPosition=" + this.duplicatePartPosition
                    + ", placementFailed=" + this.placementFailed
                    + ", initializationFailed=" + this.initializationFailed;
        }
    }
}
