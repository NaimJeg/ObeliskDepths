package io.github.naimjeg.obeliskdepths.dungeon.encounter;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.entity.DungeonEntityData;
import io.github.naimjeg.obeliskdepths.dungeon.entity.DungeonEntityTracker;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonDifficulty;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonStatus;
import io.github.naimjeg.obeliskdepths.dungeon.lifecycle.DungeonEncounterFailureService;
import io.github.naimjeg.obeliskdepths.dungeon.raid.BuiltinDungeonRaids;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidId;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidInstance;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidPlayers;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomStatus;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.reward.DungeonRewardLifecycle;
import io.github.naimjeg.obeliskdepths.dungeon.reward.DungeonRewardPlacement;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSession;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSessionCompletion;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSessionProgressBarService;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonGeneratedRoom;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteProjectionCache;
import io.github.naimjeg.obeliskdepths.dungeon.site.ResolvedDungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/*
 * Instance-level dungeon encounter controller.
 *
 * Rooms are spatial metadata only. This director owns encounter phase,
 * controlled mob population, kill credit, boss transition, and encounter
 * cleanup for each active dungeon instance.
 */
public final class DungeonEncounterDirector {
    private static final int NORMAL_MOB_KILL_SCORE = 1;
    private static final long RECONCILE_INTERVAL_TICKS = 40L;
    private static final int MAX_SPAWN_ATTEMPTS_PER_TICK = 8;
    private static final long BASE_RETRY_BACKOFF_TICKS = 40L;
    private static final long MAX_RETRY_BACKOFF_TICKS = 20L * 15L;
    private static final int MAX_CONSECUTIVE_SPAWN_FAILURES = 10;
    public static final long MISSING_MOB_RECONCILE_TIMEOUT_TICKS = 20L * 60L;

    private DungeonEncounterDirector() {
    }

    public static void tick(ServerLevel level) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(level);

        data.instances().forEachActive(instance ->
                tickInstance(level, data, instance)
        );
    }

    public static boolean resolveControlledMob(
            ServerLevel level,
            DungeonInstanceId instanceId,
            UUID entityId,
            DungeonMobResolution resolution
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(level);
        Optional<DungeonRaidInstance> encounter =
                data.raids().findActiveByInstance(instanceId);

        if (encounter.isEmpty()) {
            return false;
        }

        return resolveControlledMob(level, data, encounter.get(), entityId, resolution);
    }

    public static boolean resolveControlledMob(
            ServerLevel level,
            DungeonRaidId encounterId,
            UUID entityId,
            DungeonMobResolution resolution
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(level);
        Optional<DungeonRaidInstance> encounter = data.raids().get(encounterId);

        if (encounter.isEmpty()) {
            return false;
        }

        return resolveControlledMob(level, data, encounter.get(), entityId, resolution);
    }

    public static void cleanupInstance(
            ServerLevel level,
            DungeonInstanceId instanceId,
            DungeonMobResolution resolution
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(level);
        data.raids().findActiveByInstance(instanceId)
                .ifPresent(encounter -> {
                    removeTrackedMobs(level, data, encounter, resolution);
                    data.raids().markEncounterExpired(encounter);
                    data.sessions().findByInstance(instanceId)
                            .ifPresent(session ->
                                    DungeonSessionProgressBarService.removeSession(session.id())
                            );
                });
    }

    public static boolean failInstance(
            ServerLevel level,
            DungeonInstanceId instanceId,
            DungeonMobResolution resolution
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(level);
        Optional<DungeonRaidInstance> encounter = data.raids().findByInstance(instanceId);

        if (encounter.isEmpty() || encounter.get().isTerminal()) {
            return false;
        }

        failEncounter(level, data, encounter.get(), resolution);
        return true;
    }

    public static boolean expireInstance(
            ServerLevel level,
            DungeonInstanceId instanceId,
            DungeonMobResolution resolution
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(level);
        Optional<DungeonRaidInstance> encounter = data.raids().findByInstance(instanceId);

        if (encounter.isEmpty() || encounter.get().isTerminal()) {
            return false;
        }

        expireEncounter(level, data, encounter.get(), resolution);
        return true;
    }

    public static boolean reconcileInstanceNow(
            ServerLevel level,
            DungeonInstanceId instanceId
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(level);
        Optional<DungeonInstance> instance = data.instances().get(instanceId);

        if (instance.isEmpty() || instance.get().status() != DungeonStatus.ACTIVE) {
            return false;
        }

        Optional<DungeonSite> site = resolveSite(level, data, instance.get());
        Optional<DungeonRaidInstance> encounter =
                data.raids().findActiveByInstance(instanceId);

        if (site.isEmpty() || encounter.isEmpty() || !encounter.get().encounterPhase().active()) {
            return false;
        }

        reconcilePopulation(level, data, instance.get(), site.get(), encounter.get());
        return true;
    }

    private static void tickInstance(
            ServerLevel level,
            DungeonManagerSavedData data,
            DungeonInstance instance
    ) {
        if (instance.status() != DungeonStatus.ACTIVE) {
            cleanupInstance(level, instance.id(), DungeonMobResolution.CLEANED);
            return;
        }

        Optional<DungeonSite> site = resolveSite(level, data, instance);

        if (site.isEmpty()) {
            return;
        }

        Optional<DungeonSession> session =
                data.sessions().findByInstance(instance.id());

        if (session.isEmpty() || !session.get().state().needsRuntimeTick()) {
            return;
        }

        Optional<DungeonRaidInstance> existingEncounter =
                data.raids().findByInstance(instance.id());

        if (existingEncounter.isPresent() && existingEncounter.get().isTerminal()) {
            return;
        }

        DungeonEncounterSettings encounterSettings =
                DungeonEncounterSettingsResolver.resolve(instance.difficulty());

        DungeonRaidInstance encounter = data.raids().getOrCreateEncounter(
                instance.id(),
                BuiltinDungeonRaids.COMBAT_ROOM,
                encounterSettings.normalKillQuota(),
                encounterSettings.desiredLivingMobCount(),
                level.getGameTime()
        );
        data.raids().initializeEncounterSettings(
                encounter,
                encounterSettings.normalKillQuota(),
                encounterSettings.desiredLivingMobCount()
        );

        if (encounter.encounterPhase() == DungeonEncounterPhase.COMBAT
                && encounter.normalKillQuotaComplete()) {
            transitionToBoss(level, data, instance, encounter);
            return;
        }

        if (!encounter.encounterPhase().active()) {
            return;
        }

        if (DungeonRaidPlayers.findActivePlayersInDungeon(level, instance).isEmpty()) {
            suspendForNoPlayers(level, data, encounter);
            return;
        }

        if (level.getGameTime() < encounter.nextSpawnGameTime()) {
            return;
        }

        reconcilePopulation(level, data, instance, site.get(), encounter);
    }

    private static boolean resolveControlledMob(
            ServerLevel level,
            DungeonManagerSavedData data,
            DungeonRaidInstance encounter,
            UUID entityId,
            DungeonMobResolution resolution
    ) {
        Entity entity = level.getEntity(entityId);
        Optional<BlockPos> deathPos = entity == null
                ? Optional.empty()
                : Optional.of(entity.blockPosition().immutable());
        Optional<DungeonEntityData> entityData =
                entity == null ? Optional.empty() : DungeonEntityTracker.get(entity);
        DungeonEncounterMobRole role = entityData
                .flatMap(DungeonEntityData::mobRole)
                .orElse(encounter.currentMobRole());

        if (!data.raids().resolveMob(encounter, entityId)) {
            return false;
        }

        if (entity != null) {
            DungeonEntityTracker.clear(entity);
        }

        if (resolution == DungeonMobResolution.KILLED) {
            if (role == DungeonEncounterMobRole.NORMAL
                    && encounter.encounterPhase() == DungeonEncounterPhase.COMBAT) {
                data.raids().creditNormalKill(encounter);
                data.sessions().findByInstance(encounter.dungeonInstanceId())
                        .ifPresent(session -> DungeonSessionProgressBarService.updateSession(level, session));
                Optional.of(encounter)
                        .filter(DungeonRaidInstance::normalKillQuotaComplete)
                        .flatMap(ignored -> data.instances().get(encounter.dungeonInstanceId()))
                        .ifPresent(instance ->
                                transitionToBoss(level, data, instance, encounter)
                        );
            } else if (role == DungeonEncounterMobRole.BOSS
                    && encounter.encounterPhase() == DungeonEncounterPhase.BOSS) {
                completeBoss(level, data, encounter, deathPos);
            }
        }

        return true;
    }

    private static void reconcilePopulation(
            ServerLevel level,
            DungeonManagerSavedData data,
            DungeonInstance instance,
            DungeonSite site,
            DungeonRaidInstance encounter
    ) {
        int living = pruneInvalidTrackedMobs(level, data, encounter);

        if (!encounter.encounterPhase().active()) {
            return;
        }

        int desired = encounter.encounterPhase() == DungeonEncounterPhase.BOSS
                ? 1
                : encounter.desiredLivingMobCount();
        int deficit = Math.max(0, desired - living);

        if (deficit <= 0) {
            data.raids().setNextSpawnGameTime(
                    encounter,
                    level.getGameTime() + RECONCILE_INTERVAL_TICKS
            );
            return;
        }

        int spawned = spawnDeficit(level, data, instance, site, encounter, deficit);

        if (spawned > 0) {
            data.raids().clearSpawnFailure(encounter);
            data.raids().setNextSpawnGameTime(
                    encounter,
                    level.getGameTime() + RECONCILE_INTERVAL_TICKS
            );
        } else {
            long backoff = Math.min(
                    MAX_RETRY_BACKOFF_TICKS,
                    BASE_RETRY_BACKOFF_TICKS * (encounter.spawnFailureCount() + 1L)
            );
            data.raids().recordSpawnFailure(encounter, level.getGameTime() + backoff);
            if (encounter.spawnFailureCount() >= MAX_CONSECUTIVE_SPAWN_FAILURES) {
                ObeliskDepths.LOGGER.warn(
                        "Dungeon encounter spawn failed terminally: instance={}, encounter={}, room={}, phase={}, failureCount={}, nextAction=failEncounterAndCleanup",
                        instance.id(),
                        encounter.id(),
                        encounter.roomId().map(Object::toString).orElse("<instance>"),
                        encounter.encounterPhase().getSerializedName(),
                        encounter.spawnFailureCount()
                );
                failEncounter(level, data, encounter, DungeonMobResolution.INVALIDATED);
                return;
            }

            ObeliskDepths.LOGGER.debug(
                    "Dungeon encounter spawn deferred: instance={}, encounter={}, phase={}, failureCount={}, nextRetry={}",
                    instance.id(),
                    encounter.id(),
                    encounter.encounterPhase().getSerializedName(),
                    encounter.spawnFailureCount(),
                    encounter.nextSpawnGameTime()
            );
        }

    }

    private static int pruneInvalidTrackedMobs(
            ServerLevel level,
            DungeonManagerSavedData data,
            DungeonRaidInstance encounter
    ) {
        int living = 0;

        for (UUID entityId : List.copyOf(encounter.trackedMobIds())) {
            Entity entity = level.getEntity(entityId);

            if (entity == null) {
                Optional<Long> missingSince = encounter.missingSinceGameTime(entityId);

                if (missingSince.isEmpty()) {
                    data.raids().markMobTemporarilyMissing(
                            encounter,
                            entityId,
                            level.getGameTime()
                    );
                    living++;
                    continue;
                }

                if (level.getGameTime() - missingSince.get()
                        < MISSING_MOB_RECONCILE_TIMEOUT_TICKS) {
                    living++;
                    continue;
                }

                data.raids().resolveMob(encounter, entityId);
                continue;
            }

            if (!(entity instanceof Mob) || !entity.isAlive()) {
                data.raids().resolveMob(encounter, entityId);
                continue;
            }

            Optional<DungeonEntityData> entityData = DungeonEntityTracker.get(entity);

            if (entityData.isEmpty()
                    || entityData.get().instanceId().isEmpty()
                    || !entityData.get().instanceId().get().equals(encounter.dungeonInstanceId())
                    || entityData.get().raidId().isEmpty()
                    || !entityData.get().raidId().get().equals(encounter.id())) {
                DungeonEntityTracker.clear(entity);
                entity.discard();
                data.raids().resolveMob(encounter, entityId);
                continue;
            }

            data.raids().clearMissingMob(encounter, entityId);
            living++;
        }

        return living;
    }

    private static int spawnDeficit(
            ServerLevel level,
            DungeonManagerSavedData data,
            DungeonInstance instance,
            DungeonSite site,
            DungeonRaidInstance encounter,
            int deficit
    ) {
        List<DungeonGeneratedRoom> rooms = spawnRooms(site, encounter.encounterPhase());

        if (rooms.isEmpty()) {
            return 0;
        }

        int spawned = 0;
        int attempts = 0;

        while (spawned < deficit && attempts < MAX_SPAWN_ATTEMPTS_PER_TICK) {
            DungeonGeneratedRoom room = rooms.get(attempts % rooms.size());
            Optional<BlockPos> spawnPos =
                    DungeonSpawnPositionResolver.findSpawnPos(
                            room,
                            level,
                            attempts + encounter.trackedMobIds().size()
                    );

            attempts++;

            if (spawnPos.isEmpty()) {
                continue;
            }

            Mob mob = entityTypeFor(encounter.currentMobRole(), attempts)
                    .spawn(level, spawnPos.get(), EntitySpawnReason.TRIGGERED);

            if (mob == null) {
                continue;
            }

            mob.setPersistenceRequired();
            DungeonEntityTracker.bindControlledMob(
                    mob,
                    instance.id(),
                    encounter.id(),
                    encounter.currentMobRole()
            );
            data.raids().trackMob(encounter, mob.getUUID());
            spawned++;
        }

        return spawned;
    }

    private static List<DungeonGeneratedRoom> spawnRooms(
            DungeonSite site,
            DungeonEncounterPhase phase
    ) {
        if (phase == DungeonEncounterPhase.BOSS) {
            return site.roomsOfType(DungeonRoomType.BOSS);
        }

        return site.rooms()
                .stream()
                .filter(room -> room.type() != DungeonRoomType.START)
                .filter(room -> room.type() != DungeonRoomType.BOSS)
                .sorted(Comparator.comparing(room -> room.id().value()))
                .toList();
    }

    private static EntityType<? extends Mob> entityTypeFor(
            DungeonEncounterMobRole role,
            int sequence
    ) {
        if (role == DungeonEncounterMobRole.BOSS) {
            return EntityType.ZOMBIE;
        }

        return sequence % 2 == 0 ? EntityType.ZOMBIE : EntityType.SKELETON;
    }

    private static void transitionToBoss(
            ServerLevel level,
            DungeonManagerSavedData data,
            DungeonInstance instance,
            DungeonRaidInstance encounter
    ) {
        if (!data.raids().setEncounterPhase(encounter, DungeonEncounterPhase.BOSS)) {
            return;
        }

        removeTrackedMobs(level, data, encounter, DungeonMobResolution.CLEANED);
        data.roomStates().unlockBossRooms(instance.id());

        for (var roomState : data.roomStates().allForInstance(instance.id())) {
            if (roomState.type() == DungeonRoomType.COMBAT) {
                data.roomStates().setStatus(
                        instance.id(),
                        roomState.roomId(),
                        DungeonRoomStatus.CLEARED
                );
            }
        }

        data.sessions().findByInstance(instance.id())
                .ifPresent(session ->
                        DungeonSessionProgressBarService.removeSession(session.id())
                );
        data.raids().setNextSpawnGameTime(encounter, level.getGameTime());

        ObeliskDepths.LOGGER.debug(
                "Dungeon encounter transitioned to boss: instance={}, encounter={}",
                instance.id(),
                encounter.id()
        );
    }

    private static void completeBoss(
            ServerLevel level,
            DungeonManagerSavedData data,
            DungeonRaidInstance encounter,
            Optional<BlockPos> deathPos
    ) {
        if (!data.raids().markBossCompleted(encounter)) {
            return;
        }

        data.raids().markEncounterComplete(encounter);
        removeTrackedMobs(level, data, encounter, DungeonMobResolution.CLEANED);

        for (var roomState : data.roomStates().allForInstance(encounter.dungeonInstanceId())) {
            if (roomState.type() == DungeonRoomType.BOSS) {
                data.roomStates().setStatus(
                        encounter.dungeonInstanceId(),
                        roomState.roomId(),
                        DungeonRoomStatus.CLEARED
                );
            }
        }

        data.instances().setStatus(
                encounter.dungeonInstanceId(),
                DungeonStatus.REWARD_PHASE
        );
        var reward = DungeonRewardLifecycle.onBossDefeated(
                level,
                encounter.dungeonInstanceId(),
                deathPos
        );
        DungeonRewardPlacement.tryPlaceReward(level, reward);
        DungeonSessionCompletion.completeSession(level, encounter.dungeonInstanceId());
        ObeliskDepths.LOGGER.debug(
                "Dungeon encounter completed: instance={}, encounter={}",
                encounter.dungeonInstanceId(),
                encounter.id()
        );
    }

    private static void suspendForNoPlayers(
            ServerLevel level,
            DungeonManagerSavedData data,
            DungeonRaidInstance encounter
    ) {
        if (encounter.trackedMobIds().isEmpty()) {
            return;
        }

        removeTrackedMobs(level, data, encounter, DungeonMobResolution.CLEANED);
        data.raids().setNextSpawnGameTime(
                encounter,
                level.getGameTime() + RECONCILE_INTERVAL_TICKS
        );
    }

    private static void expireEncounter(
            ServerLevel level,
            DungeonManagerSavedData data,
            DungeonRaidInstance encounter,
            DungeonMobResolution resolution
    ) {
        removeTrackedMobs(level, data, encounter, resolution);
        data.raids().markEncounterExpired(encounter);
        data.sessions().findByInstance(encounter.dungeonInstanceId())
                .ifPresent(session ->
                        DungeonSessionProgressBarService.removeSession(session.id())
                );
    }

    private static void failEncounter(
            ServerLevel level,
            DungeonManagerSavedData data,
            DungeonRaidInstance encounter,
            DungeonMobResolution resolution
    ) {
        removeTrackedMobs(level, data, encounter, resolution);
        DungeonEncounterFailureService.failInstanceForEncounter(
                level,
                data,
                encounter,
                "encounter_spawn_or_reconciliation_failure"
        );
    }

    private static void removeTrackedMobs(
            ServerLevel level,
            DungeonManagerSavedData data,
            DungeonRaidInstance encounter,
            DungeonMobResolution resolution
    ) {
        int removed = 0;
        int resolved = 0;
        int deferred = 0;

        for (UUID entityId : List.copyOf(encounter.trackedMobIds())) {
            Entity entity = level.getEntity(entityId);

            if (entity != null) {
                DungeonEntityTracker.clear(entity);
                entity.discard();
                if (data.raids().resolveMob(encounter, entityId)) {
                    resolved++;
                }
                removed++;
            } else {
                if (data.raids().markCleanupPending(encounter, entityId)) {
                    deferred++;
                }
            }
        }

        if (removed > 0 || resolved > 0 || deferred > 0) {
            ObeliskDepths.LOGGER.debug(
                    "Dungeon encounter removed tracked mobs: instance={}, encounter={}, resolution={}, removed={}, resolved={}, deferred={}",
                    encounter.dungeonInstanceId(),
                    encounter.id(),
                    resolution,
                    removed,
                    resolved,
                    deferred
            );
        }

    }

    private static Optional<DungeonSite> resolveSite(
            ServerLevel level,
            DungeonManagerSavedData data,
            DungeonInstance instance
    ) {
        Optional<DungeonSite> generated =
                DungeonSiteProjectionCache.read(level, instance.siteKey())
                        .map(ResolvedDungeonSite::site);

        if (generated.isPresent()) {
            return generated;
        }

        return data.sites().snapshot(instance.siteKey());
    }

    public static int fixedNormalKillQuota(DungeonDifficulty difficulty) {
        return DungeonEncounterSettingsResolver.fixedNormalKillQuota(difficulty);
    }

    public static int desiredLivingMobCount(DungeonDifficulty difficulty) {
        return DungeonEncounterSettingsResolver.desiredLivingMobCount(difficulty);
    }
}
