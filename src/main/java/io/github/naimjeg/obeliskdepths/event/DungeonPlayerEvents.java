package io.github.naimjeg.obeliskdepths.event;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.interaction.DungeonPortalEntryService;
import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonLifecycleService;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationCancellationReason;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationRuntime;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = ObeliskDepths.MOD_ID)
public final class DungeonPlayerEvents {
    private DungeonPlayerEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerDungeonLifecycleService.onLogout(player);
            cancelPreparationJobOnLogout(player);
            DungeonPortalEntryService.onPlayerLoggedOut(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerDungeonLifecycleService.onChangedDimension(player);
            cancelPreparationJobOnDimensionChange(player, player.level().dimension());
            DungeonPortalEntryService.onPlayerChangedDimension(player);
        }
    }

    private static void cancelPreparationJobOnLogout(ServerPlayer player) {
        ServerLevel dungeonLevel = obeliskDepthsLevel(player);
        if (dungeonLevel == null) {
            return;
        }
        DungeonPreparationRuntime runtime = DungeonPreparationRuntime.get(dungeonLevel);
        if (runtime != null) {
            runtime.cancelJobsForPlayer(
                    player.getUUID(),
                    DungeonPreparationCancellationReason.PLAYER_DISCONNECTED,
                    "player disconnected",
                    dungeonLevel.getGameTime()
            );
        }
    }

    private static void cancelPreparationJobOnDimensionChange(
            ServerPlayer player,
            ResourceKey<Level> currentDimension
    ) {
        ServerLevel dungeonLevel = obeliskDepthsLevel(player);
        if (dungeonLevel == null) {
            return;
        }
        DungeonPreparationRuntime runtime = DungeonPreparationRuntime.get(dungeonLevel);
        if (runtime != null) {
            runtime.cancelJobsForPlayerOutsideSourceDimension(
                    player.getUUID(),
                    currentDimension,
                    DungeonPreparationCancellationReason.PLAYER_DIMENSION_CHANGED,
                    "player changed dimension",
                    dungeonLevel.getGameTime()
            );
        }
    }

    private static void cancelPreparationJobOnDeath(ServerPlayer player) {
        ServerLevel dungeonLevel = obeliskDepthsLevel(player);
        if (dungeonLevel == null) {
            return;
        }
        DungeonPreparationRuntime runtime = DungeonPreparationRuntime.get(dungeonLevel);
        if (runtime != null) {
            runtime.cancelJobsForPlayer(
                    player.getUUID(),
                    DungeonPreparationCancellationReason.PLAYER_DIED,
                    "player died",
                    dungeonLevel.getGameTime()
            );
        }
    }

    private static ServerLevel obeliskDepthsLevel(ServerPlayer player) {
        return player.level().getServer().getLevel(ModDimensions.OBELISK_DEPTHS_LEVEL);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerDungeonLifecycleService.onDeath(player);
            cancelPreparationJobOnDeath(player);
            DungeonPortalEntryService.onPlayerDied(player);
        }
    }
}
