package io.github.naimjeg.obeliskdepths.dungeon.artifact;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.block.ObeliskChestBlock;
import io.github.naimjeg.obeliskdepths.block.entity.ObeliskChestBlockEntity;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonStatus;
import io.github.naimjeg.obeliskdepths.dungeon.reward.DungeonRewardRecord;
import io.github.naimjeg.obeliskdepths.dungeon.reward.DungeonRewardId;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import io.github.naimjeg.obeliskdepths.registry.ModBlocks;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;

public final class DungeonRuntimeArtifactCleanupService {
    private static final long PENDING_RETRY_INTERVAL_TICKS = 20L * 10L;

    private DungeonRuntimeArtifactCleanupService() {
    }

    public static void cleanupInstanceArtifacts(
            ServerLevel dungeonLevel,
            DungeonInstanceId instanceId
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        CleanupCounts counts = new CleanupCounts();

        for (DungeonRuntimeArtifactRecord artifact : data.runtimeArtifacts().forInstance(instanceId)) {
            if (artifact.type() != DungeonRuntimeArtifactType.REWARD_CHEST) {
                continue;
            }

            cleanupArtifact(dungeonLevel, data, artifact, counts);
        }

        ObeliskDepths.LOGGER.debug(
                "Dungeon runtime artifact cleanup: level={}, instance={}, rewardChestsRemoved={}, rewardChestsPending={}, rewardChestsMissing={}, rewardChestsMismatched={}",
                dungeonLevel.dimension().identifier(),
                instanceId,
                counts.removed,
                counts.pending,
                counts.missing,
                counts.mismatched
        );
    }

    public static void cleanupRewardArtifact(
            ServerLevel dungeonLevel,
            DungeonInstanceId instanceId,
            DungeonRewardId rewardId
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        CleanupCounts counts = new CleanupCounts();

        data.runtimeArtifacts().all()
                .stream()
                .filter(artifact -> artifact.type() == DungeonRuntimeArtifactType.REWARD_CHEST)
                .filter(artifact -> artifact.instanceId().equals(instanceId))
                .filter(artifact -> artifact.rewardId().filter(rewardId::equals).isPresent())
                .findFirst()
                .ifPresent(artifact -> cleanupArtifact(dungeonLevel, data, artifact, counts));
    }

    public static void tickPendingArtifacts(ServerLevel dungeonLevel) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        long gameTime = dungeonLevel.getGameTime();
        CleanupCounts counts = new CleanupCounts();

        for (DungeonRuntimeArtifactRecord artifact : data.runtimeArtifacts().pending()) {
            if (artifact.nextCleanupRetryGameTime() > gameTime) {
                continue;
            }

            cleanupArtifact(dungeonLevel, data, artifact, counts);
        }

        if (counts.hasWork()) {
            ObeliskDepths.LOGGER.debug(
                    "Dungeon pending artifact cleanup: level={}, removed={}, pending={}, missing={}, mismatched={}",
                    dungeonLevel.dimension().identifier(),
                    counts.removed,
                    counts.pending,
                    counts.missing,
                    counts.mismatched
            );
        }
    }

    public static void cleanupPendingArtifactsInChunk(
            ServerLevel level,
            ChunkPos loadedChunk
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(level);
        CleanupCounts counts = new CleanupCounts();

        for (DungeonRuntimeArtifactRecord artifact : data.runtimeArtifacts().pending()) {
            if (artifact.type() != DungeonRuntimeArtifactType.REWARD_CHEST
                    || artifact.pos().isEmpty()
                    || !artifactIntersectsChunk(level, artifact, loadedChunk)) {
                continue;
            }

            cleanupArtifact(level, data, artifact, counts);
        }

        if (counts.hasWork()) {
            ObeliskDepths.LOGGER.debug(
                    "Dungeon chunk-load pending artifact cleanup: level={}, chunk={}, removed={}, pending={}, missing={}, mismatched={}",
                    level.dimension().identifier(),
                    loadedChunk,
                    counts.removed,
                    counts.pending,
                    counts.missing,
                    counts.mismatched
            );
        }
    }

    public static void reconcileStaleRewardArtifactsForSite(
            ServerLevel level,
            DungeonSite site
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(level);
        CleanupCounts counts = new CleanupCounts();

        for (DungeonRuntimeArtifactRecord artifact : data.runtimeArtifacts().all()) {
            if (artifact.type() != DungeonRuntimeArtifactType.REWARD_CHEST
                    || artifact.pos().isEmpty()
                    || !site.bounds().contains(artifact.pos().get())
                    || !isStaleOwner(data, artifact.instanceId())) {
                continue;
            }

            cleanupArtifact(level, data, artifact, counts);
        }

        if (counts.hasWork()) {
            ObeliskDepths.LOGGER.debug(
                    "Dungeon stale reward artifact reconciliation: site={}, removed={}, pending={}, missing={}, mismatched={}",
                    site.key(),
                    counts.removed,
                    counts.pending,
                    counts.missing,
                    counts.mismatched
            );
        }
    }

    private static void cleanupArtifact(
            ServerLevel level,
            DungeonManagerSavedData data,
            DungeonRuntimeArtifactRecord artifact,
            CleanupCounts counts
    ) {
        if (artifact.type() != DungeonRuntimeArtifactType.REWARD_CHEST || artifact.pos().isEmpty()) {
            data.runtimeArtifacts().remove(artifact);
            return;
        }

        BlockPos savedPos = artifact.pos().get();
        long nextRetry = level.getGameTime() + PENDING_RETRY_INTERVAL_TICKS;
        if (!level.hasChunkAt(savedPos)) {
            counts.pending++;
            data.runtimeArtifacts().replace(artifact, artifact.markPending(nextRetry));
            return;
        }

        BlockState state = level.getBlockState(savedPos);
        if (state.isAir()) {
            counts.missing++;
            finishArtifactCleanup(data, artifact);
            return;
        }

        ObeliskChestBlock chestBlock = ModBlocks.OBELISK_CHEST.get();
        if (!state.is(chestBlock)) {
            counts.mismatched++;
            data.runtimeArtifacts().replace(artifact, artifact.recordMismatch(nextRetry));
            ObeliskDepths.LOGGER.warn(
                    "Reward artifact cleanup ownership mismatch: instance={}, reward={}, pos={}, block={}",
                    artifact.instanceId(),
                    artifact.rewardId(),
                    savedPos,
                    state.getBlock()
            );
            return;
        }

        BlockPos mainPos = chestBlock.resolveMainPos(savedPos, state);
        if (!mainPos.equals(savedPos)) {
            counts.mismatched++;
            data.runtimeArtifacts().replace(artifact, artifact.recordMismatch(nextRetry));
            ObeliskDepths.LOGGER.warn(
                    "Reward artifact cleanup saved position is not the chest main part: instance={}, reward={}, savedPos={}, resolvedMain={}",
                    artifact.instanceId(),
                    artifact.rewardId(),
                    savedPos,
                    mainPos
            );
            return;
        }

        List<BlockPos> expectedParts =
                chestBlock.getRewardStructurePartPositions(mainPos, state);
        if (!chestBlock.areRewardStructureChunksLoaded(level, expectedParts)) {
            counts.pending++;
            data.runtimeArtifacts().replace(artifact, artifact.markPending(nextRetry));
            return;
        }

        if (!chestBlock.isCompleteRewardStructure(level, mainPos)) {
            counts.mismatched++;
            data.runtimeArtifacts().replace(artifact, artifact.recordMismatch(nextRetry));
            ObeliskDepths.LOGGER.warn(
                    "Reward artifact cleanup found incomplete chest structure: instance={}, reward={}, pos={}",
                    artifact.instanceId(),
                    artifact.rewardId(),
                    savedPos
            );
            return;
        }

        if (!(level.getBlockEntity(mainPos) instanceof ObeliskChestBlockEntity chest)
                || artifact.rewardId().isEmpty()
                || chest.rewardId().filter(artifact.rewardId().get()::equals).isEmpty()
                || chest.instanceId().filter(artifact.instanceId()::equals).isEmpty()) {
            counts.mismatched++;
            data.runtimeArtifacts().replace(artifact, artifact.recordMismatch(nextRetry));
            ObeliskDepths.LOGGER.warn(
                    "Reward artifact cleanup block entity identity mismatch: instance={}, reward={}, pos={}",
                    artifact.instanceId(),
                    artifact.rewardId(),
                    savedPos
            );
            return;
        }

        if (!chestBlock.removeRewardStructure(level, mainPos)) {
            counts.pending++;
            data.runtimeArtifacts().replace(artifact, artifact.markPending(nextRetry));
            return;
        }

        counts.removed++;
        finishArtifactCleanup(data, artifact);
    }

    private static boolean artifactIntersectsChunk(
            ServerLevel level,
            DungeonRuntimeArtifactRecord artifact,
            ChunkPos loadedChunk
    ) {
        BlockPos savedPos = artifact.pos().orElseThrow();
        if (chunkPos(savedPos).equals(loadedChunk)) {
            return true;
        }

        if (!level.hasChunkAt(savedPos)) {
            return false;
        }

        ObeliskChestBlock chestBlock = ModBlocks.OBELISK_CHEST.get();
        BlockState state = level.getBlockState(savedPos);
        if (!state.is(chestBlock)) {
            return false;
        }

        return chestBlock.getRewardStructurePartPositions(savedPos, state)
                .stream()
                .map(DungeonRuntimeArtifactCleanupService::chunkPos)
                .anyMatch(loadedChunk::equals);
    }

    private static ChunkPos chunkPos(BlockPos pos) {
        return new ChunkPos(
                SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ())
        );
    }

    private static boolean isStaleOwner(
            DungeonManagerSavedData data,
            DungeonInstanceId instanceId
    ) {
        Optional<DungeonInstance> instance = data.instances().get(instanceId);
        if (instance.isEmpty()) {
            return true;
        }

        DungeonStatus status = instance.get().status();
        return status == DungeonStatus.PORTAL_CLOSED
                || status == DungeonStatus.CLEARED
                || status == DungeonStatus.FAILED
                || status == DungeonStatus.EXPIRED;
    }

    private static void finishArtifactCleanup(
            DungeonManagerSavedData data,
            DungeonRuntimeArtifactRecord artifact
    ) {
        data.runtimeArtifacts().remove(artifact);
        Optional<DungeonRewardRecord> reward = artifact.rewardId().flatMap(data.rewards()::get);
        reward.ifPresent(data.rewards()::markCleaned);
    }

    private static final class CleanupCounts {
        private int removed;
        private int pending;
        private int missing;
        private int mismatched;

        private boolean hasWork() {
            return this.removed > 0 || this.pending > 0 || this.missing > 0 || this.mismatched > 0;
        }
    }
}
