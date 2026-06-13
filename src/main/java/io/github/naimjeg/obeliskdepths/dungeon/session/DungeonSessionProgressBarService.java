package io.github.naimjeg.obeliskdepths.dungeon.session;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonStatus;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidPlayers;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@EventBusSubscriber(modid = ObeliskDepths.MOD_ID)
public final class DungeonSessionProgressBarService {
    private static final String TITLE_KEY = "event.obeliskdepths.dungeon_raid";
    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();

    private DungeonSessionProgressBarService() {
    }

    public static void tick(ServerLevel level) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(level);

        for (DungeonSession session : data.sessions()) {
            updateSession(level, session);
        }
    }

    public static void updateSession(
            ServerLevel level,
            DungeonSession session
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(level);
        Optional<DungeonInstance> instance = data.getInstance(session.instanceId());

        if (instance.isEmpty()
                || !shouldDisplaySession(session, instance.get())) {
            removeSession(session.id());
            return;
        }

        List<ServerPlayer> eligiblePlayers =
                DungeonRaidPlayers.findActivePlayersInDungeon(level, instance.get());

        if (eligiblePlayers.isEmpty()) {
            removeSession(session.id());
            return;
        }

        ServerBossEvent bar = BARS.computeIfAbsent(
                session.id(),
                DungeonSessionProgressBarService::createBar
        );

        DungeonKillProgress progress = session.progress();
        bar.setName(title(progress));
        bar.setProgress(progress.remainingProgress());
        synchronizePlayers(bar, eligiblePlayers);
        bar.setVisible(true);
    }

    public static void removeSession(UUID sessionId) {
        ServerBossEvent bar = BARS.remove(sessionId);

        if (bar == null) {
            return;
        }

        bar.removeAllPlayers();
        bar.setVisible(false);
    }

    public static void clearLevel(ServerLevel level) {
        if (!level.dimension().equals(ModDimensions.OBELISK_DEPTHS_LEVEL)) {
            return;
        }

        for (DungeonSession session : DungeonManagerSavedData.get(level).sessions()) {
            removeSession(session.id());
        }
    }

    public static void clearAll() {
        for (UUID sessionId : List.copyOf(BARS.keySet())) {
            removeSession(sessionId);
        }
    }

    static boolean shouldDisplayProgress(DungeonKillProgress progress) {
        return progress.targetKillScore() > 0 && !progress.isComplete();
    }

    private static boolean shouldDisplaySession(
            DungeonSession session,
            DungeonInstance instance
    ) {
        return session.state().needsRuntimeTick()
                && instance.status() == DungeonStatus.ACTIVE
                && shouldDisplayProgress(session.progress());
    }

    private static ServerBossEvent createBar(UUID sessionId) {
        ServerBossEvent bar = new ServerBossEvent(
                sessionId,
                Component.translatable(TITLE_KEY, 0, 0),
                BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.NOTCHED_10
        );

        bar.setDarkenScreen(false);
        bar.setPlayBossMusic(false);
        bar.setCreateWorldFog(false);
        bar.setVisible(false);
        return bar;
    }

    private static Component title(DungeonKillProgress progress) {
        return Component.translatable(
                TITLE_KEY,
                progress.clampedCurrentKillScore(),
                progress.targetKillScore()
        );
    }

    private static void synchronizePlayers(
            ServerBossEvent bar,
            List<ServerPlayer> eligiblePlayers
    ) {
        Set<ServerPlayer> eligible = new HashSet<>(eligiblePlayers);

        for (ServerPlayer current : List.copyOf(bar.getPlayers())) {
            if (!eligible.contains(current)) {
                bar.removePlayer(current);
            }
        }

        for (ServerPlayer player : eligiblePlayers) {
            if (!bar.getPlayers().contains(player)) {
                bar.addPlayer(player);
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            clearLevel(level);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        clearAll();
    }
}
