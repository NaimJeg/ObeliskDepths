package io.github.naimjeg.obeliskdepths.dungeon.runtime;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.entity.DungeonEntityCleanupService;
import io.github.naimjeg.obeliskdepths.dungeon.lifecycle.DungeonCleanupService;
import io.github.naimjeg.obeliskdepths.dungeon.presence.DungeonPhysicalPresenceService;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidTicker;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSessionManager;
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
        tickSessions(level);
        tickDelayedCleanup(level);
        tickRaids(level);
        tickDungeonEntities(level);
    }

    private static void tickDungeonState(ServerLevel level) {
        DungeonManagerSavedData.get(level).tick(level);
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

        DungeonSessionManager.tickSessions(level);
        DungeonSessionProgressBarService.tick(level);
    }

    private static void tickDelayedCleanup(ServerLevel level) {
        if (level.getGameTime() % CLEANUP_TICK_INTERVAL != 0L) {
            return;
        }

        DungeonCleanupService.cleanupClosedInstancesReadyForCleanup(level);
    }

    private static void tickRaids(ServerLevel level) {
        if (level.getGameTime() % RAID_TICK_INTERVAL != 0L) {
            return;
        }

        DungeonRaidTicker.tickRaids(level);
    }

    private static void tickDungeonEntities(ServerLevel level) {
        for (Entity entity : level.getAllEntities()) {
            DungeonEntityCleanupService.tickEntity(level, entity);
        }
    }
}
