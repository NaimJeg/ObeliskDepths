package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.chunk.DungeonChunkLeaseManager;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@EventBusSubscriber(modid = ObeliskDepths.MOD_ID)
public final class DungeonPreparationRuntime {
    private static final WeakHashMap<ServerLevel, DungeonPreparationRuntime> LEVEL_RUNTIMES =
            new WeakHashMap<>();

    private final ServerLevel level;
    private final DungeonPreparationJobRegistry jobRegistry;
    private final DungeonChunkLeaseManager leaseManager;
    private boolean cleared;

    private DungeonPreparationRuntime(ServerLevel level) {
        this.level = level;
        this.jobRegistry = new DungeonPreparationJobRegistry();
        this.leaseManager = DungeonChunkLeaseManager.createForLevel(level);
        this.cleared = false;
    }

    public static DungeonPreparationRuntime getOrCreate(ServerLevel level) {
        assertServerThread(level);
        DungeonPreparationRuntime runtime =
                LEVEL_RUNTIMES.computeIfAbsent(level, DungeonPreparationRuntime::new);
        runtime.assertUsable();
        return runtime;
    }

    public static DungeonPreparationRuntime get(ServerLevel level) {
        assertServerThread(level);
        DungeonPreparationRuntime runtime = LEVEL_RUNTIMES.get(level);
        if (runtime != null && runtime.cleared) {
            LEVEL_RUNTIMES.remove(level, runtime);
            return null;
        }
        return runtime;
    }

    DungeonPreparationJobRegistry jobRegistry() {
        assertUsable();
        return this.jobRegistry;
    }

    DungeonChunkLeaseManager leaseManager() {
        assertUsable();
        return this.leaseManager;
    }

    public void tick(ServerLevel level) {
        assertOwnerLevel(level);
        assertUsable();
        this.jobRegistry.purgeTerminal(
                level.getGameTime(),
                DungeonPreparationJobRegistry.TERMINAL_RETENTION_TICKS
        );
    }

    public void cancelJobsForPlayer(
            UUID playerId,
            DungeonPreparationCancellationReason reason,
            String detail,
            long gameTime
    ) {
        assertUsable();
        this.jobRegistry.findActiveByPlayer(playerId).ifPresent(job -> {
            this.jobRegistry.cancel(job.id(), reason, detail, gameTime);
        });
    }

    public void cancelJobsForPlayerOutsideSourceDimension(
            UUID playerId,
            ResourceKey<Level> currentDimension,
            DungeonPreparationCancellationReason reason,
            String detail,
            long gameTime
    ) {
        assertUsable();
        this.jobRegistry.findActiveByPlayer(playerId)
                .filter(job -> !job.request().sourceDimension().equals(currentDimension))
                .ifPresent(job -> this.jobRegistry.cancel(job.id(), reason, detail, gameTime));
    }

    public void clear(
            DungeonPreparationCancellationReason reason,
            String detail,
            long gameTime
    ) {
        assertOwnerThread();
        if (this.cleared) {
            return;
        }
        this.cleared = true;
        RuntimeException aggregateFailure = null;

        try {
            this.jobRegistry.clearAllActive(reason, detail, gameTime);
        } catch (RuntimeException exception) {
            aggregateFailure = appendFailure(
                    aggregateFailure,
                    "Failed to cancel preparation jobs",
                    exception
            );
        }

        try {
            this.leaseManager.clear();
        } catch (RuntimeException exception) {
            aggregateFailure = appendFailure(
                    aggregateFailure,
                    "Failed to clear preparation chunk leases",
                    exception
            );
        } finally {
            LEVEL_RUNTIMES.remove(this.level, this);
        }

        if (aggregateFailure != null) {
            throw aggregateFailure;
        }
    }

    public static void clearAllOnServerStopping() {
        Set<DungeonPreparationRuntime> runtimes =
                new LinkedHashSet<>(new ArrayList<>(LEVEL_RUNTIMES.values()));
        LEVEL_RUNTIMES.clear();

        RuntimeException aggregateFailure = null;
        for (DungeonPreparationRuntime runtime : runtimes) {
            if (runtime == null) {
                continue;
            }
            try {
                runtime.clear(
                        DungeonPreparationCancellationReason.SERVER_STOPPING,
                        "server stopping",
                        runtime.level.getGameTime()
                );
            } catch (RuntimeException exception) {
                aggregateFailure = appendFailure(
                        aggregateFailure,
                        "Failed to clear preparation runtime during server stop",
                        exception
                );
            }
        }

        if (aggregateFailure != null) {
            ObeliskDepths.LOGGER.error(
                    "Failed to clear one or more dungeon preparation runtimes during server stop",
                    aggregateFailure
            );
        }
    }

    private static RuntimeException appendFailure(
            RuntimeException aggregateFailure,
            String message,
            RuntimeException exception
    ) {
        RuntimeException result = aggregateFailure;
        if (result == null) {
            result = new IllegalStateException(message);
        }
        result.addSuppressed(exception);
        return result;
    }

    private void assertUsable() {
        assertOwnerThread();
        if (this.cleared) {
            throw new IllegalStateException("DungeonPreparationRuntime has been cleared.");
        }
    }

    private void assertOwnerLevel(ServerLevel level) {
        if (level != this.level) {
            throw new IllegalArgumentException("Runtime used with a non-owning level.");
        }
        assertOwnerThread();
    }

    private void assertOwnerThread() {
        assertServerThread(this.level);
    }

    private static void assertServerThread(ServerLevel level) {
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException(
                    "DungeonPreparationRuntime must be accessed on the server thread."
            );
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level
                && level.dimension().equals(ModDimensions.OBELISK_DEPTHS_LEVEL)) {
            DungeonPreparationRuntime runtime = DungeonPreparationRuntime.get(level);
            if (runtime != null) {
                runtime.clear(
                        DungeonPreparationCancellationReason.LEVEL_UNLOADED,
                        "level unloaded",
                        level.getGameTime()
                );
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        clearAllOnServerStopping();
    }
}
