package io.github.naimjeg.obeliskdepths.dungeon.raid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.encounter.DungeonEncounterPhase;
import io.github.naimjeg.obeliskdepths.dungeon.encounter.DungeonEncounterMobRole;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;
import io.github.naimjeg.obeliskdepths.dungeon.serialization.DungeonCodecs;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class DungeonRaidInstance {
    public static final Codec<DungeonRaidInstance> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    DungeonRaidId.CODEC.fieldOf("id")
                            .forGetter(DungeonRaidInstance::id),

                    DungeonInstanceId.CODEC.fieldOf("dungeon_instance_id")
                            .forGetter(DungeonRaidInstance::dungeonInstanceId),

                    DungeonRoomId.CODEC.optionalFieldOf("room_id")
                            .forGetter(DungeonRaidInstance::roomId),

                    Identifier.CODEC.fieldOf("raid_type")
                            .forGetter(DungeonRaidInstance::raidType),

                    DungeonRaidStatus.CODEC
                            .optionalFieldOf("status", DungeonRaidStatus.PREPARING)
                            .forGetter(DungeonRaidInstance::status),

                    DungeonEncounterPhase.CODEC
                            .optionalFieldOf("encounter_phase", DungeonEncounterPhase.COMBAT)
                            .forGetter(DungeonRaidInstance::encounterPhase),

                    Codec.INT.optionalFieldOf("normal_kill_quota", 0)
                            .forGetter(DungeonRaidInstance::normalKillQuota),

                    Codec.INT.optionalFieldOf("credited_normal_kills", 0)
                            .forGetter(DungeonRaidInstance::creditedNormalKills),

                    Codec.INT.optionalFieldOf("desired_living_mob_count", 0)
                            .forGetter(DungeonRaidInstance::desiredLivingMobCount),

                    DungeonCodecs.UUID_CODEC.listOf()
                            .optionalFieldOf("tracked_mob_ids", List.of())
                            .forGetter(raid -> List.copyOf(raid.trackedMobIds)),

                    DungeonCodecs.UUID_CODEC.listOf()
                            .optionalFieldOf("resolved_mob_ids", List.of())
                            .forGetter(raid -> List.of()),

                    DungeonMissingMobRecord.CODEC.listOf()
                            .optionalFieldOf("missing_mobs", List.of())
                            .forGetter(DungeonRaidInstance::missingMobRecords),

                    DungeonCodecs.UUID_CODEC.listOf()
                            .optionalFieldOf("cleanup_pending_mob_ids", List.of())
                            .forGetter(raid -> List.copyOf(raid.cleanupPendingMobIds)),

                    Codec.LONG.optionalFieldOf("next_spawn_game_time", 0L)
                            .forGetter(DungeonRaidInstance::nextSpawnGameTime),

                    Codec.INT.optionalFieldOf("spawn_failure_count", 0)
                            .forGetter(DungeonRaidInstance::spawnFailureCount),

                    Codec.BOOL.optionalFieldOf("boss_completed", false)
                            .forGetter(DungeonRaidInstance::bossCompleted)
            ).apply(instance, DungeonRaidInstance::fromCodec));

    private final DungeonRaidId id;
    private final DungeonInstanceId dungeonInstanceId;
    private final Optional<DungeonRoomId> roomId;
    private final Identifier raidType;

    private DungeonRaidStatus status;
    private int currentWave;
    private long nextWaveGameTime;
    private int spawnedMobCount;
    private int killedMobCount;
    private DungeonEncounterPhase encounterPhase;
    private int normalKillQuota;
    private int creditedNormalKills;
    private int desiredLivingMobCount;
    private final Set<UUID> trackedMobIds = new LinkedHashSet<>();
    private final Map<UUID, Long> missingMobSinceGameTime = new HashMap<>();
    private final Set<UUID> cleanupPendingMobIds = new LinkedHashSet<>();
    private long nextSpawnGameTime;
    private int spawnFailureCount;
    private boolean bossCompleted;

    public DungeonRaidInstance(
            DungeonRaidId id,
            DungeonInstanceId dungeonInstanceId,
            Optional<DungeonRoomId> roomId,
            Identifier raidType,
            DungeonRaidStatus status,
            int currentWave,
            long nextWaveGameTime,
            int spawnedMobCount,
            int killedMobCount,
            DungeonEncounterPhase encounterPhase,
            int normalKillQuota,
            int creditedNormalKills,
            int desiredLivingMobCount,
            List<UUID> trackedMobIds,
            List<UUID> resolvedMobIds,
            List<DungeonMissingMobRecord> missingMobs,
            List<UUID> cleanupPendingMobIds,
            long nextSpawnGameTime,
            int spawnFailureCount,
            boolean bossCompleted
    ) {
        this.id = id;
        this.dungeonInstanceId = dungeonInstanceId;
        this.roomId = roomId;
        this.raidType = raidType;
        this.status = status;
        this.currentWave = currentWave;
        this.nextWaveGameTime = nextWaveGameTime;
        this.spawnedMobCount = spawnedMobCount;
        this.killedMobCount = killedMobCount;
        this.encounterPhase = encounterPhase == null
                ? DungeonEncounterPhase.COMBAT
                : encounterPhase;
        this.normalKillQuota = Math.max(0, normalKillQuota);
        this.creditedNormalKills = clampCreditedKills(creditedNormalKills, this.normalKillQuota);
        this.desiredLivingMobCount = Math.max(0, desiredLivingMobCount);
        this.trackedMobIds.addAll(trackedMobIds == null ? List.of() : trackedMobIds);
        if (missingMobs != null) {
            for (DungeonMissingMobRecord missingMob : missingMobs) {
                if (missingMob != null && this.trackedMobIds.contains(missingMob.entityId())) {
                    this.missingMobSinceGameTime.put(
                            missingMob.entityId(),
                            Math.max(0L, missingMob.missingSinceGameTime())
                    );
                }
            }
        }
        this.cleanupPendingMobIds.addAll(cleanupPendingMobIds == null ? List.of() : cleanupPendingMobIds);
        this.nextSpawnGameTime = nextSpawnGameTime;
        this.spawnFailureCount = Math.max(0, spawnFailureCount);
        this.bossCompleted = bossCompleted;
    }

    private static DungeonRaidInstance fromCodec(
            DungeonRaidId id,
            DungeonInstanceId dungeonInstanceId,
            Optional<DungeonRoomId> roomId,
            Identifier raidType,
            DungeonRaidStatus status,
            DungeonEncounterPhase encounterPhase,
            int normalKillQuota,
            int creditedNormalKills,
            int desiredLivingMobCount,
            List<UUID> trackedMobIds,
            List<UUID> resolvedMobIds,
            List<DungeonMissingMobRecord> missingMobs,
            List<UUID> cleanupPendingMobIds,
            long nextSpawnGameTime,
            int spawnFailureCount,
            boolean bossCompleted
    ) {
        return new DungeonRaidInstance(
                id,
                dungeonInstanceId,
                roomId,
                raidType,
                status,
                0,
                nextSpawnGameTime,
                0,
                0,
                encounterPhase,
                normalKillQuota,
                creditedNormalKills,
                desiredLivingMobCount,
                trackedMobIds,
                resolvedMobIds,
                missingMobs,
                cleanupPendingMobIds,
                nextSpawnGameTime,
                spawnFailureCount,
                bossCompleted
        );
    }

    public static DungeonRaidInstance createInstanceEncounter(
            DungeonInstanceId dungeonInstanceId,
            Identifier raidType,
            int normalKillQuota,
            int desiredLivingMobCount,
            long gameTime
    ) {
        return new DungeonRaidInstance(
                DungeonRaidId.create(),
                dungeonInstanceId,
                Optional.empty(),
                raidType,
                DungeonRaidStatus.ACTIVE,
                0,
                gameTime,
                0,
                0,
                DungeonEncounterPhase.COMBAT,
                normalKillQuota,
                0,
                desiredLivingMobCount,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                gameTime,
                0,
                false
        );
    }

    public DungeonRaidId id() {
        return this.id;
    }

    public DungeonInstanceId dungeonInstanceId() {
        return this.dungeonInstanceId;
    }

    public Optional<DungeonRoomId> roomId() {
        return this.roomId;
    }

    public Identifier raidType() {
        return this.raidType;
    }

    public DungeonRaidStatus status() {
        return this.status;
    }

    public int currentWave() {
        return this.currentWave;
    }

    public long nextWaveGameTime() {
        return this.nextWaveGameTime;
    }

    public int spawnedMobCount() {
        return this.spawnedMobCount;
    }

    public int killedMobCount() {
        return this.killedMobCount;
    }

    public DungeonEncounterPhase encounterPhase() {
        return this.encounterPhase;
    }

    public int normalKillQuota() {
        return this.normalKillQuota;
    }

    public int creditedNormalKills() {
        return this.creditedNormalKills;
    }

    public int desiredLivingMobCount() {
        return this.desiredLivingMobCount;
    }

    public Set<UUID> trackedMobIds() {
        return Set.copyOf(this.trackedMobIds);
    }

    public Set<UUID> resolvedMobIds() {
        return Set.of();
    }

    public Set<UUID> cleanupPendingMobIds() {
        return Set.copyOf(this.cleanupPendingMobIds);
    }

    public Optional<Long> missingSinceGameTime(UUID entityId) {
        return Optional.ofNullable(this.missingMobSinceGameTime.get(entityId));
    }

    public List<DungeonMissingMobRecord> missingMobRecords() {
        return this.missingMobSinceGameTime.entrySet()
                .stream()
                .filter(entry -> this.trackedMobIds.contains(entry.getKey()))
                .map(entry -> new DungeonMissingMobRecord(entry.getKey(), entry.getValue()))
                .toList();
    }

    public long nextSpawnGameTime() {
        return this.nextSpawnGameTime;
    }

    public int spawnFailureCount() {
        return this.spawnFailureCount;
    }

    public boolean bossCompleted() {
        return this.bossCompleted;
    }

    public boolean setStatus(DungeonRaidStatus status) {
        if (this.status == status) {
            return false;
        }

        this.status = status;
        return true;
    }

    public void advanceWave(long nextWaveGameTime) {
        this.currentWave++;
        this.nextWaveGameTime = nextWaveGameTime;
    }

    public void markMobSpawned() {
        this.spawnedMobCount++;
    }

    public void markMobKilled() {
        this.killedMobCount++;
    }

    public boolean setEncounterPhase(DungeonEncounterPhase phase) {
        if (this.encounterPhase == phase) {
            return false;
        }

        this.encounterPhase = phase;
        return true;
    }

    public boolean markEncounterComplete() {
        boolean changed = false;

        if (this.encounterPhase != DungeonEncounterPhase.COMPLETE) {
            this.encounterPhase = DungeonEncounterPhase.COMPLETE;
            changed = true;
        }

        if (this.status != DungeonRaidStatus.WON) {
            this.status = DungeonRaidStatus.WON;
            changed = true;
        }

        return changed;
    }

    public boolean markEncounterExpired() {
        boolean changed = false;

        if (this.encounterPhase != DungeonEncounterPhase.EXPIRED) {
            this.encounterPhase = DungeonEncounterPhase.EXPIRED;
            changed = true;
        }

        if (this.status != DungeonRaidStatus.EXPIRED) {
            this.status = DungeonRaidStatus.EXPIRED;
            changed = true;
        }

        return changed;
    }

    public boolean markEncounterFailed() {
        boolean changed = false;

        if (this.encounterPhase != DungeonEncounterPhase.FAILED) {
            this.encounterPhase = DungeonEncounterPhase.FAILED;
            changed = true;
        }

        if (this.status != DungeonRaidStatus.FAILED) {
            this.status = DungeonRaidStatus.FAILED;
            changed = true;
        }

        return changed;
    }

    public boolean trackMob(UUID entityId) {
        this.cleanupPendingMobIds.remove(entityId);
        this.missingMobSinceGameTime.remove(entityId);
        return this.trackedMobIds.add(entityId);
    }

    public boolean untrackMob(UUID entityId) {
        this.missingMobSinceGameTime.remove(entityId);
        return this.trackedMobIds.remove(entityId);
    }

    public boolean resolveMob(UUID entityId) {
        if (entityId == null || !this.trackedMobIds.remove(entityId)) {
            return false;
        }

        this.missingMobSinceGameTime.remove(entityId);
        return true;
    }

    public boolean creditNormalKill() {
        if (this.normalKillQuota > 0 && this.creditedNormalKills >= this.normalKillQuota) {
            return false;
        }

        int next = this.creditedNormalKills + 1;

        if (next == this.creditedNormalKills) {
            return false;
        }

        this.creditedNormalKills = next;
        return true;
    }

    public boolean migrateCreditedNormalKills(int migratedKills) {
        int clamped = clampCreditedKills(migratedKills, this.normalKillQuota);

        if (clamped <= this.creditedNormalKills) {
            return false;
        }

        this.creditedNormalKills = clamped;
        return true;
    }

    public boolean normalKillQuotaComplete() {
        return this.normalKillQuota > 0
                && this.creditedNormalKills >= this.normalKillQuota;
    }

    public boolean markMobTemporarilyMissing(UUID entityId, long gameTime) {
        if (!this.trackedMobIds.contains(entityId)) {
            return false;
        }

        return this.missingMobSinceGameTime.putIfAbsent(entityId, gameTime) == null;
    }

    public boolean clearMissingMob(UUID entityId) {
        return this.missingMobSinceGameTime.remove(entityId) != null;
    }

    public boolean markCleanupPending(UUID entityId) {
        boolean changed = this.trackedMobIds.remove(entityId);
        this.missingMobSinceGameTime.remove(entityId);
        return this.cleanupPendingMobIds.add(entityId) || changed;
    }

    public boolean clearCleanupPending(UUID entityId) {
        return this.cleanupPendingMobIds.remove(entityId);
    }

    public boolean markBossCompleted() {
        if (this.bossCompleted) {
            return false;
        }

        this.bossCompleted = true;
        return true;
    }

    public boolean initializeEncounterSettings(
            int normalKillQuota,
            int desiredLivingMobCount
    ) {
        boolean changed = false;

        if (this.normalKillQuota <= 0 && normalKillQuota > 0) {
            this.normalKillQuota = normalKillQuota;
            this.creditedNormalKills = clampCreditedKills(
                    this.creditedNormalKills,
                    this.normalKillQuota
            );
            changed = true;
        }

        if (this.desiredLivingMobCount <= 0 && desiredLivingMobCount > 0) {
            this.desiredLivingMobCount = desiredLivingMobCount;
            changed = true;
        }

        return changed;
    }

    public void setNextSpawnGameTime(long gameTime) {
        this.nextSpawnGameTime = gameTime;
    }

    public void clearSpawnFailure() {
        this.spawnFailureCount = 0;
    }

    public void recordSpawnFailure(long nextRetryGameTime) {
        this.spawnFailureCount++;
        this.nextSpawnGameTime = nextRetryGameTime;
    }

    public DungeonEncounterMobRole currentMobRole() {
        return this.encounterPhase == DungeonEncounterPhase.BOSS
                ? DungeonEncounterMobRole.BOSS
                : DungeonEncounterMobRole.NORMAL;
    }

    public boolean isTerminal() {
        return this.encounterPhase == DungeonEncounterPhase.COMPLETE
                || this.encounterPhase == DungeonEncounterPhase.EXPIRED
                || this.encounterPhase == DungeonEncounterPhase.FAILED
                || this.status == DungeonRaidStatus.WON
                || this.status == DungeonRaidStatus.FAILED
                || this.status == DungeonRaidStatus.EXPIRED;
    }

    private static int clampCreditedKills(int value, int quota) {
        int nonNegative = Math.max(0, value);
        return quota > 0 ? Math.min(nonNegative, quota) : nonNegative;
    }
}
