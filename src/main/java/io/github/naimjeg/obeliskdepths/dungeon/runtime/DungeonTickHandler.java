package io.github.naimjeg.obeliskdepths.dungeon.runtime;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.artifact.DungeonRuntimeArtifactCleanupService;
import io.github.naimjeg.obeliskdepths.dungeon.correction.DungeonBoundaryCorrectionService;
import io.github.naimjeg.obeliskdepths.dungeon.entity.DungeonEntityCleanupService;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonStatus;
import io.github.naimjeg.obeliskdepths.dungeon.lifecycle.DungeonCleanupService;
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

        tickDungeonState(level);
        tickPhysicalPresence(level);
        tickRaids(level);
        tickSessions(level);
        tickDelayedCleanup(level);
        tickDungeonEntities(level);
    }

    private static void tickDungeonState(ServerLevel level) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(level);
        long gameTime = level.getGameTime();

        data.portalSessions().purgeExpired(gameTime);
        data.portalSessions().removeForInactiveInstances(instanceId ->
                data.instances()
                        .get(instanceId)
                        .map(instance -> instance.status() == DungeonStatus.ACTIVE)
                        .orElse(false)
        );
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
