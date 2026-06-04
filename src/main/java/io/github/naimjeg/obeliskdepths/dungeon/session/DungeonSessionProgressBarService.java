package io.github.naimjeg.obeliskdepths.dungeon.session;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.encounter.DungeonEncounterPhase;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonStatus;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidInstance;
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
    private static final Map<UUID, DisplaySuppressionReason> LAST_SUPPRESSION_REASON =
            new HashMap<>();

    private DungeonSessionProgressBarService() {
    }

    public static void tick(ServerLevel level) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(level);
        Set<UUID> validActiveSessions = new HashSet<>();

        for (DungeonSession session : data.sessions().all()) {
            if (session.state().needsRuntimeTick()) {
                validActiveSessions.add(session.id());
            }

            updateSession(level, session);
        }

        for (UUID sessionId : List.copyOf(BARS.keySet())) {
            if (!validActiveSessions.contains(sessionId)) {
                removeSession(sessionId);
            }
        }
    }

    public static void updateSession(
            ServerLevel level,
            DungeonSession session
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(level);
        Optional<DungeonInstance> instance =
                data.instances().get(session.instanceId());

        DisplayDecision decision = evaluateDisplay(data, level, session, instance);
        if (decision.reason() != DisplaySuppressionReason.DISPLAY) {
            logSuppressionChange(session, decision.reason());
            removeBar(session.id());
            return;
        }

        List<ServerPlayer> eligiblePlayers =
                DungeonRaidPlayers.findActivePlayersInDungeon(level, instance.get());

        ServerBossEvent bar = BARS.computeIfAbsent(
                session.id(),
                DungeonSessionProgressBarService::createBar
        );

        LAST_SUPPRESSION_REASON.remove(session.id());
        DungeonRaidInstance encounter = decision.encounter().orElseThrow();
        bar.setName(title(encounter));
        bar.setProgress(completedProgress(encounter));
        synchronizePlayers(bar, eligiblePlayers);
        bar.setVisible(true);
    }

    public static void removeSession(UUID sessionId) {
        removeBar(sessionId);
        LAST_SUPPRESSION_REASON.remove(sessionId);
    }

    private static void removeBar(UUID sessionId) {
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

        for (DungeonSession session : DungeonManagerSavedData.get(level).sessions().all()) {
            removeSession(session.id());
        }
    }

    public static void clearAll() {
        for (UUID sessionId : List.copyOf(BARS.keySet())) {
            removeSession(sessionId);
        }
    }

    static boolean shouldDisplayProgress(DungeonRaidInstance encounter) {
        return encounter.normalKillQuota() > 0
                && encounter.encounterPhase() == DungeonEncounterPhase.COMBAT
                && !encounter.normalKillQuotaComplete();
    }

    private static DisplayDecision evaluateDisplay(
            DungeonManagerSavedData data,
            ServerLevel level,
            DungeonSession session,
            Optional<DungeonInstance> instance
    ) {
        if (!session.state().needsRuntimeTick()) {
            return DisplayDecision.suppress(DisplaySuppressionReason.SESSION_NOT_RUNTIME_ACTIVE);
        }

        if (instance.isEmpty()) {
            return DisplayDecision.suppress(DisplaySuppressionReason.INSTANCE_MISSING);
        }

        DungeonInstance value = instance.get();
        if (value.status() != DungeonStatus.ACTIVE) {
            return DisplayDecision.suppress(DisplaySuppressionReason.INSTANCE_NOT_ACTIVE);
        }

        Optional<DungeonRaidInstance> encounter =
                data.raids().findActiveByInstance(value.id());
        if (encounter.isEmpty()) {
            return DisplayDecision.suppress(DisplaySuppressionReason.ENCOUNTER_MISSING);
        }

        DungeonRaidInstance raid = encounter.get();
        if (raid.encounterPhase() != DungeonEncounterPhase.COMBAT) {
            return DisplayDecision.suppress(DisplaySuppressionReason.ENCOUNTER_NOT_COMBAT);
        }

        if (raid.normalKillQuota() <= 0) {
            return DisplayDecision.suppress(DisplaySuppressionReason.ZERO_KILL_QUOTA);
        }

        if (raid.normalKillQuotaComplete()) {
            return DisplayDecision.suppress(DisplaySuppressionReason.QUOTA_COMPLETE);
        }

        if (DungeonRaidPlayers.findActivePlayersInDungeon(level, value).isEmpty()) {
            return DisplayDecision.suppress(DisplaySuppressionReason.NO_PHYSICAL_PLAYERS);
        }

        return DisplayDecision.display(raid);
    }

    private static void logSuppressionChange(
            DungeonSession session,
            DisplaySuppressionReason reason
    ) {
        DisplaySuppressionReason previous =
                LAST_SUPPRESSION_REASON.put(session.id(), reason);

        if (previous == reason) {
            return;
        }

        ObeliskDepths.LOGGER.debug(
                "Dungeon raid progress bar suppressed: session={}, instance={}, reason={}",
                session.id(),
                session.instanceId(),
                reason
        );
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

    private static Component title(DungeonRaidInstance encounter) {
        return Component.translatable(
                TITLE_KEY,
                Math.min(encounter.creditedNormalKills(), encounter.normalKillQuota()),
                encounter.normalKillQuota()
        );
    }

    static float completedProgress(DungeonRaidInstance encounter) {
        int target = encounter.normalKillQuota();

        if (target <= 0) {
            return 0.0F;
        }

        int completed = Math.max(0, Math.min(encounter.creditedNormalKills(), target));
        return Math.max(0.0F, Math.min(1.0F, completed / (float) target));
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

    private record DisplayDecision(
            DisplaySuppressionReason reason,
            Optional<DungeonRaidInstance> encounter
    ) {
        private static DisplayDecision display(DungeonRaidInstance encounter) {
            return new DisplayDecision(
                    DisplaySuppressionReason.DISPLAY,
                    Optional.of(encounter)
            );
        }

        private static DisplayDecision suppress(DisplaySuppressionReason reason) {
            return new DisplayDecision(reason, Optional.empty());
        }
    }

    enum DisplaySuppressionReason {
        DISPLAY,
        SESSION_NOT_RUNTIME_ACTIVE,
        INSTANCE_MISSING,
        INSTANCE_NOT_ACTIVE,
        ENCOUNTER_MISSING,
        ENCOUNTER_NOT_COMBAT,
        ZERO_KILL_QUOTA,
        QUOTA_COMPLETE,
        NO_PHYSICAL_PLAYERS
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
