package io.github.naimjeg.obeliskdepths.dungeon.state.store;

import io.github.naimjeg.obeliskdepths.dungeon.encounter.DungeonEncounterPhase;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidId;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidInstance;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.Identifier;

public final class DungeonRaidStore {
    private final Map<DungeonRaidId, DungeonRaidInstance> raids = new HashMap<>();
    private final Map<DungeonInstanceId, Set<DungeonRaidId>> raidIdsByInstance =
            new HashMap<>();
    private final Runnable dirty;

    public DungeonRaidStore(Runnable dirty) {
        this.dirty = dirty;
    }

    public void load(Collection<DungeonRaidInstance> raids) {
        for (DungeonRaidInstance raid : raids) {
            this.putLoaded(raid);
        }
    }

    public Collection<DungeonRaidInstance> all() {
        return List.copyOf(this.raids.values());
    }

    public Optional<DungeonRaidInstance> get(DungeonRaidId id) {
        return Optional.ofNullable(this.raids.get(id));
    }

    public Optional<DungeonRaidInstance> findActiveByInstance(
            DungeonInstanceId dungeonInstanceId
    ) {
        Set<DungeonRaidId> raidIds = this.raidIdsByInstance.get(dungeonInstanceId);

        if (raidIds == null || raidIds.isEmpty()) {
            return Optional.empty();
        }

        for (DungeonRaidId raidId : raidIds) {
            DungeonRaidInstance raid = this.raids.get(raidId);

            if (raid != null && !raid.isTerminal()) {
                return Optional.of(raid);
            }
        }

        return Optional.empty();
    }

    public Optional<DungeonRaidInstance> findByInstance(
            DungeonInstanceId dungeonInstanceId
    ) {
        Set<DungeonRaidId> raidIds = this.raidIdsByInstance.get(dungeonInstanceId);

        if (raidIds == null || raidIds.isEmpty()) {
            return Optional.empty();
        }

        for (DungeonRaidId raidId : raidIds) {
            DungeonRaidInstance raid = this.raids.get(raidId);

            if (raid != null && !raid.isTerminal()) {
                return Optional.of(raid);
            }
        }

        for (DungeonRaidId raidId : raidIds) {
            DungeonRaidInstance raid = this.raids.get(raidId);

            if (raid != null) {
                return Optional.of(raid);
            }
        }

        return Optional.empty();
    }

    public List<DungeonRaidInstance> allForInstance(DungeonInstanceId dungeonInstanceId) {
        Set<DungeonRaidId> raidIds = this.raidIdsByInstance.get(dungeonInstanceId);

        if (raidIds == null || raidIds.isEmpty()) {
            return List.of();
        }

        List<DungeonRaidInstance> result = new java.util.ArrayList<>();
        for (DungeonRaidId raidId : raidIds) {
            DungeonRaidInstance raid = this.raids.get(raidId);

            if (raid != null) {
                result.add(raid);
            }
        }

        return List.copyOf(result);
    }

    public DungeonRaidInstance getOrCreateEncounter(
            DungeonInstanceId dungeonInstanceId,
            Identifier raidType,
            int normalKillQuota,
            int desiredLivingMobCount,
            long gameTime
    ) {
        return this.findActiveByInstance(dungeonInstanceId)
                .orElseGet(() -> this.createEncounter(
                        dungeonInstanceId,
                        raidType,
                        normalKillQuota,
                        desiredLivingMobCount,
                        gameTime
                ));
    }

    public DungeonRaidInstance createEncounter(
            DungeonInstanceId dungeonInstanceId,
            Identifier raidType,
            int normalKillQuota,
            int desiredLivingMobCount,
            long gameTime
    ) {
        DungeonRaidInstance encounter =
                DungeonRaidInstance.createInstanceEncounter(
                        dungeonInstanceId,
                        raidType,
                        normalKillQuota,
                        desiredLivingMobCount,
                        gameTime
                );

        this.raids.put(encounter.id(), encounter);
        this.index(encounter);
        this.dirty.run();
        return encounter;
    }

    public boolean remove(DungeonRaidId id) {
        DungeonRaidInstance removed = this.raids.remove(id);

        if (removed == null) {
            return false;
        }

        this.unindex(removed);
        this.dirty.run();
        return true;
    }

    public int removeForInstance(DungeonInstanceId instanceId) {
        return this.removeAllForInstance(instanceId).size();
    }

    public List<DungeonRaidInstance> removeAllForInstance(DungeonInstanceId instanceId) {
        Set<DungeonRaidId> raidIds =
                Set.copyOf(this.raidIdsByInstance.getOrDefault(instanceId, Set.of()));
        List<DungeonRaidInstance> removed = new java.util.ArrayList<>();

        for (DungeonRaidId raidId : raidIds) {
            DungeonRaidInstance raid = this.raids.remove(raidId);

            if (raid != null) {
                this.unindex(raid);
                removed.add(raid);
            }
        }

        if (!removed.isEmpty()) {
            this.dirty.run();
        }

        return List.copyOf(removed);
    }

    public void restoreRemovedRaids(Collection<DungeonRaidInstance> raids) {
        boolean changed = false;
        for (DungeonRaidInstance raid : raids) {
            if (this.raids.containsKey(raid.id())) {
                throw new IllegalStateException(
                        "Dungeon raid already exists: " + raid.id()
                );
            }

            this.raids.put(raid.id(), raid);
            this.index(raid);
            changed = true;
        }

        if (changed) {
            this.dirty.run();
        }
    }

    public boolean initializeEncounterSettings(
            DungeonRaidInstance raid,
            int normalKillQuota,
            int desiredLivingMobCount
    ) {
        return this.mark(raid.initializeEncounterSettings(
                normalKillQuota,
                desiredLivingMobCount
        ));
    }

    public boolean resolveMob(
            DungeonRaidInstance raid,
            UUID entityId
    ) {
        return this.mark(raid.resolveMob(entityId));
    }

    public boolean creditNormalKill(DungeonRaidInstance raid) {
        return this.mark(raid.creditNormalKill());
    }

    public boolean markEncounterExpired(DungeonRaidInstance raid) {
        return this.mark(raid.markEncounterExpired());
    }

    public boolean markEncounterFailed(DungeonRaidInstance raid) {
        return this.mark(raid.markEncounterFailed());
    }

    public boolean setEncounterPhase(
            DungeonRaidInstance raid,
            DungeonEncounterPhase phase
    ) {
        return this.mark(raid.setEncounterPhase(phase));
    }

    public boolean markBossCompleted(DungeonRaidInstance raid) {
        return this.mark(raid.markBossCompleted());
    }

    public boolean markEncounterComplete(DungeonRaidInstance raid) {
        return this.mark(raid.markEncounterComplete());
    }

    public boolean markMobTemporarilyMissing(
            DungeonRaidInstance raid,
            UUID entityId,
            long gameTime
    ) {
        return this.mark(raid.markMobTemporarilyMissing(entityId, gameTime));
    }

    public boolean clearMissingMob(
            DungeonRaidInstance raid,
            UUID entityId
    ) {
        return this.mark(raid.clearMissingMob(entityId));
    }

    public boolean markCleanupPending(
            DungeonRaidInstance raid,
            UUID entityId
    ) {
        return this.mark(raid.markCleanupPending(entityId));
    }

    public boolean clearCleanupPending(
            DungeonRaidInstance raid,
            UUID entityId
    ) {
        return this.mark(raid.clearCleanupPending(entityId));
    }

    public boolean trackMob(
            DungeonRaidInstance raid,
            UUID entityId
    ) {
        return this.mark(raid.trackMob(entityId));
    }

    public boolean migrateCreditedNormalKills(
            DungeonRaidInstance raid,
            int migratedKills
    ) {
        return this.mark(raid.migrateCreditedNormalKills(migratedKills));
    }

    public void setNextSpawnGameTime(
            DungeonRaidInstance raid,
            long gameTime
    ) {
        raid.setNextSpawnGameTime(gameTime);
        this.dirty.run();
    }

    public void clearSpawnFailure(DungeonRaidInstance raid) {
        raid.clearSpawnFailure();
        this.dirty.run();
    }

    public void recordSpawnFailure(
            DungeonRaidInstance raid,
            long nextRetryGameTime
    ) {
        raid.recordSpawnFailure(nextRetryGameTime);
        this.dirty.run();
    }

    private void putLoaded(DungeonRaidInstance raid) {
        DungeonRaidInstance previous = this.raids.put(raid.id(), raid);

        if (previous != null) {
            throw new IllegalStateException(
                    "Duplicate dungeon raid id in saved data: " + raid.id()
            );
        }

        this.index(raid);
    }

    private boolean mark(boolean changed) {
        if (changed) {
            this.dirty.run();
        }

        return changed;
    }

    private void index(DungeonRaidInstance raid) {
        this.raidIdsByInstance
                .computeIfAbsent(raid.dungeonInstanceId(), ignored -> new HashSet<>())
                .add(raid.id());
    }

    private void unindex(DungeonRaidInstance raid) {
        Set<DungeonRaidId> raidIds = this.raidIdsByInstance.get(raid.dungeonInstanceId());

        if (raidIds == null) {
            return;
        }

        raidIds.remove(raid.id());

        if (raidIds.isEmpty()) {
            this.raidIdsByInstance.remove(raid.dungeonInstanceId());
        }
    }
}
