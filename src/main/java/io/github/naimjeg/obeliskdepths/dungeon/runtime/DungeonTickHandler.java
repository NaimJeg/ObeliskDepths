package io.github.naimjeg.obeliskdepths.dungeon.runtime;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.artifact.DungeonRuntimeArtifactCleanupService;
import io.github.naimjeg.obeliskdepths.dungeon.correction.DungeonBoundaryCorrectionService;
import io.github.naimjeg.obeliskdepths.dungeon.entity.DungeonEntityCleanupService;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonStatus;
import io.github.naimjeg.obeliskdepths.dungeon.lifecycle.DungeonCleanupService;
import io.github.naimjeg.obeliskdepths.dungeon.portal.DungeonPortalSessionLifecycle;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationRuntime;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationTickBudget;
import io.github.naimjeg.obeliskdepths.dungeon.presence.DungeonPhysicalPresenceService;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidTicker;
import io.github.naimjeg.obeliskdepths.dungeon.reward.DungeonRewardPlacement;
import io.github.naimjeg.obeliskdepths.dungeon.reward.DungeonRewardReconciliation;
import io.github.naimjeg.obeliskdepths.dungeon.reward.DungeonRewardStatus;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSessionCleanup;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSessionProgressBarService;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

@EventBusSubscriber(modid = ObeliskDepths.MOD_ID)
public final class DungeonTickHandler {
    private static final long SESSION_TICK_INTERVAL = 20L;
    private static final long RAID_TICK_INTERVAL = 20L;
    private static final long CLEANUP_TICK_INTERVAL = 200L;
    private static final long ENTITY_RECONCILIATION_INTERVAL = 200L;
    private static final long PORTAL_EXPIRY_MAINTENANCE_INTERVAL = 20L;
    private static final int PORTAL_EXPIRY_MAINTENANCE_BATCH = 4;
    private static final int INACTIVE_PORTAL_MAINTENANCE_BATCH = 4;
    private static final int PREPARED_ENTRY_RECONCILIATION_BATCH = 4;

    private DungeonTickHandler() {
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        if (!level.dimension().equals(ModDimensions.OBELISK_DEPTHS_LEVEL)) {
            return;
        }

        tickPhysicalPresence(level);
        tickRaids(level);
        tickSessions(level);
        tickDelayedCleanup(level);
        tickDungeonEntities(level);
        tickPreparationRuntime(level);
    }

    private static void tickPreparationRuntime(ServerLevel level) {
        DungeonPreparationTickBudget budget =
                DungeonPreparationTickBudget.perLevelTick();
        if (level.getGameTime() % PORTAL_EXPIRY_MAINTENANCE_INTERVAL == 0L
                && budget.hasTimeRemaining()) {
            DungeonPortalSessionLifecycle.purgeExpiredBounded(
                    level,
                    level.getGameTime(),
                    PORTAL_EXPIRY_MAINTENANCE_BATCH,
                    budget::hasTimeRemaining
            );
        }
        DungeonPreparationRuntime runtime = DungeonPreparationRuntime.get(level);
        if (runtime != null) {
            // Fixed-cost lifecycle work cannot be starved by the shared
            // preparation/recovery wall-clock budget.
            runtime.tickPostTeleportHandoffs(level.getGameTime());
        }
        if (runtime != null && budget.hasTimeRemaining()) {
            runtime.reconcilePreparedEntries(
                    DungeonManagerSavedData.get(level),
                    level.getGameTime(),
                    PREPARED_ENTRY_RECONCILIATION_BATCH,
                    budget::hasTimeRemaining
            );
        }
        if (budget.hasTimeRemaining()) {
            DungeonPortalSessionLifecycle.removeForInactiveInstancesBounded(
                    level,
                    INACTIVE_PORTAL_MAINTENANCE_BATCH,
                    budget::hasTimeRemaining
            );
        }
        if (runtime != null && budget.hasTimeRemaining()) {
            runtime.tick(level, budget);
        }
    }

    private static void tickPhysicalPresence(ServerLevel level) {
        for (var player : level.players()) {
            DungeonPhysicalPresenceService.tickPlayerPhysicalPresence(
                    level,
                    player
            );
        }
    }

    private static void tickSessions(ServerLevel level) {
        if (level.getGameTime() % SESSION_TICK_INTERVAL != 0L) {
            return;
        }

        DungeonSessionCleanup.tickSessions(level);
        DungeonSessionProgressBarService.tick(level);
        tickBoundaryCorrection(level);
        tickRewardPlacement(level);
        DungeonRewardReconciliation.reconcileClaimingRewards(level);
    }

    private static void tickDelayedCleanup(ServerLevel level) {
        if (level.getGameTime() % CLEANUP_TICK_INTERVAL != 0L) {
            return;
        }

        DungeonCleanupService.cleanupClosedInstancesReadyForCleanup(level);
        DungeonRuntimeArtifactCleanupService.tickPendingArtifacts(level);
    }

    private static void tickBoundaryCorrection(ServerLevel level) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(level);

        for (var instance : data.instances().all()) {
            if (instance.status() == DungeonStatus.ACTIVE
                    || instance.status() == DungeonStatus.REWARD_PHASE
                    || instance.status() == DungeonStatus.FAILED
                    || instance.status() == DungeonStatus.EXPIRED) {
                DungeonBoundaryCorrectionService.correctDesyncedPlayers(level, instance);
            }
        }
    }

    private static void tickRewardPlacement(ServerLevel level) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(level);

        for (var reward : data.rewards().all()) {
            if (reward.status() == DungeonRewardStatus.BOSS_DEFEATED
                    || reward.status() == DungeonRewardStatus.PLACEMENT_PENDING) {
                DungeonRewardPlacement.tryPlaceReward(level, reward);
            }
        }
    }

    private static void tickRaids(ServerLevel level) {
        if (level.getGameTime() % RAID_TICK_INTERVAL != 0L) {
            return;
        }

        DungeonRaidTicker.tickRaids(level);
    }

    private static void tickDungeonEntities(ServerLevel level) {
        if (level.getGameTime() % ENTITY_RECONCILIATION_INTERVAL != 0L) {
            return;
        }

        for (Entity entity : level.getAllEntities()) {
            DungeonEntityCleanupService.tickEntity(level, entity);
        }
    }
}
