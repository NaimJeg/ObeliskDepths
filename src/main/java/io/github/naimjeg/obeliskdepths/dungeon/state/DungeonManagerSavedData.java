package io.github.naimjeg.obeliskdepths.dungeon.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonTerritoryId;
import io.github.naimjeg.obeliskdepths.dungeon.id.PortalSessionId;
import io.github.naimjeg.obeliskdepths.dungeon.instance.*;
import io.github.naimjeg.obeliskdepths.dungeon.portal.DungeonAccessMode;
import io.github.naimjeg.obeliskdepths.dungeon.portal.PortalSession;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidId;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidInstance;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidStatus;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomState;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomStatus;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSession;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteRecord;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteUsageStatus;
import io.github.naimjeg.obeliskdepths.dungeon.state.store.RoomStateStore;
import io.github.naimjeg.obeliskdepths.dungeon.territory.DungeonBounds;
import io.github.naimjeg.obeliskdepths.dungeon.territory.DungeonTerritory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;
import java.util.function.Consumer;

public final class DungeonManagerSavedData extends SavedData {

    private static final long CLOSED_INSTANCE_CLEANUP_DELAY_TICKS = 20L * 60L * 5L;
    private final RoomStateStore roomStates = new RoomStateStore(this::setDirty);

    private final Map<DungeonInstanceId, Set<DungeonRaidId>> raidsByInstance =
            new HashMap<>();

    private final Map<Long, Set<DungeonTerritoryId>> territoriesByChunk =
            new HashMap<>();

    private final Map<DungeonSiteKey, DungeonSiteRecord> siteRecords =
            new HashMap<>();

    private final Map<DungeonSiteKey, DungeonSite> siteSnapshots =
            new HashMap<>();

    private final Map<DungeonInstanceId, DungeonSiteKey> reservedSiteByInstance =
            new HashMap<>();


    private static final Identifier FILE_ID =
            Identifier.fromNamespaceAndPath(ObeliskDepths.MOD_ID, "dungeons");

    private final Map<PortalSessionId, PortalSession> portalSessions = new HashMap<>();

    public static final Codec<DungeonManagerSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            DungeonInstance.CODEC.listOf()
                    .optionalFieldOf("instances", List.of())
                    .forGetter(data -> List.copyOf(data.instances.values())),
            DungeonTerritory.CODEC.listOf()
                    .optionalFieldOf("territories", List.of())
                    .forGetter(data -> List.copyOf(data.territories.values())),
            PortalSession.CODEC.listOf()
                    .optionalFieldOf("portal_sessions", List.of())
                    .forGetter(data -> List.copyOf(data.portalSessions.values())),
            DungeonRaidInstance.CODEC.listOf()
                    .optionalFieldOf("raids", List.of())
                    .forGetter(data -> List.copyOf(data.raids.values())),
            DungeonSession.CODEC.listOf()
                    .optionalFieldOf("sessions", List.of())
                    .forGetter(data -> List.copyOf(data.sessions.values())),
            DungeonRoomState.CODEC.listOf()
                    .optionalFieldOf("room_states", List.of())
                    .forGetter(data -> data.roomStates.flatten()),
            DungeonSiteRecord.CODEC.listOf()
                    .optionalFieldOf("site_records", List.of())
                    .forGetter(data -> List.copyOf(data.siteRecords.values())),
            DungeonSite.CODEC.listOf()
                    .optionalFieldOf("site_snapshots", List.of())
                    .forGetter(data -> List.copyOf(data.siteSnapshots.values()))
    ).apply(instance, DungeonManagerSavedData::new));

    public static final SavedDataType<DungeonManagerSavedData> TYPE =
            new SavedDataType<>(
                    FILE_ID,
                    DungeonManagerSavedData::new,
                    CODEC,
                    DataFixTypes.SAVED_DATA_MAP_DATA
            );

    private final Map<DungeonInstanceId, DungeonInstance> instances = new HashMap<>();
    private final Map<DungeonTerritoryId, DungeonTerritory> territories = new HashMap<>();
    private final Map<DungeonRaidId, DungeonRaidInstance> raids = new HashMap<>();
    private final Map<UUID, DungeonSession> sessions = new HashMap<>();

    public DungeonManagerSavedData() {
    }

    private DungeonManagerSavedData(
            List<DungeonInstance> instances,
            List<DungeonTerritory> territories,
            List<PortalSession> portalSessions,
            List<DungeonRaidInstance> raids,
            List<DungeonSession> sessions,
            List<DungeonRoomState> roomStates,
            List<DungeonSiteRecord> siteRecords,
            List<DungeonSite> siteSnapshots
    ) {
        for (DungeonInstance instance : instances) {
            this.instances.put(instance.id(), instance);
        }

        for (DungeonTerritory territory : territories) {
            this.territories.put(territory.id(), territory);
            this.indexTerritory(territory);
        }

        for (PortalSession session : portalSessions) {
            this.portalSessions.put(session.id(), session);
        }

        for (DungeonRaidInstance raid : raids) {
            this.raids.put(raid.id(), raid);
            this.indexRaid(raid);
        }

        for (DungeonSession session : sessions) {
            this.sessions.put(session.id(), session);
        }

        for (DungeonSiteRecord record : siteRecords) {
            this.siteRecords.put(record.siteKey(), record);

            if (record.status() == DungeonSiteUsageStatus.RESERVED) {
                record.activeInstanceId().ifPresent(instanceId ->
                        this.reservedSiteByInstance.put(instanceId, record.siteKey())
                );
            }
        }

        for (DungeonSite siteSnapshot : siteSnapshots) {
            this.siteSnapshots.put(siteSnapshot.key(), siteSnapshot);
        }

        this.roomStates.load(roomStates);
    }

    public static DungeonManagerSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public DungeonSession addSession(DungeonSession session) {
        DungeonSession previous = this.sessions.put(session.id(), session);

        if (!session.equals(previous)) {
            this.setDirty();
        }

        return session;
    }

    public Optional<DungeonSession> getSession(UUID id) {
        return Optional.ofNullable(this.sessions.get(id));
    }

    public Optional<DungeonSession> findSessionByInstance(DungeonInstanceId instanceId) {
        return this.sessions.values()
                .stream()
                .filter(session -> session.instanceId().equals(instanceId))
                .findFirst();
    }

    public Optional<DungeonSession> findSessionBySite(DungeonSiteKey siteKey) {
        return this.sessions.values()
                .stream()
                .filter(session -> session.siteKey().equals(siteKey))
                .findFirst();
    }

    public Optional<DungeonSession> findSessionByPlayer(UUID playerId) {
        return this.sessions.values()
                .stream()
                .filter(session -> session.isParticipant(playerId))
                .findFirst();
    }

    public Collection<DungeonSession> sessions() {
        return List.copyOf(this.sessions.values());
    }

    public void markSessionsDirty() {
        this.setDirty();
    }

    @Deprecated(forRemoval = false)
    public DungeonInstance createInstance(
            DungeonDifficulty difficulty,
            long gameTime
    ) {
        throw new UnsupportedOperationException(
                "Runtime dungeon creation is disabled. "
                        + "Use reserveSiteForNewInstance(...) after locating an unreached worldgen site."
        );
    }

    public Optional<DungeonInstance> getInstance(DungeonInstanceId id) {
        return Optional.ofNullable(this.instances.get(id));
    }

    public boolean releaseFailedReservation(DungeonInstanceId id) {
        Optional<DungeonInstance> removed = this.removeRuntimeState(id);

        if (removed.isEmpty()) {
            return false;
        }

        DungeonSiteKey siteKey = this.reservedSiteByInstance.remove(id);

        if (siteKey == null) {
            siteKey = removed.get().siteKey();
        }

        this.siteSnapshots.remove(siteKey);

        DungeonSiteRecord record = this.siteRecords.get(siteKey);

        if (record != null && record.isReservedFor(id)) {
            this.siteRecords.remove(siteKey);
        }

        this.setDirty();

        return true;
    }

    public Optional<DungeonTerritory> getTerritory(DungeonTerritoryId id) {
        return Optional.ofNullable(this.territories.get(id));
    }

    public Optional<DungeonTerritory> findTerritory(BlockPos pos) {
        long chunkKey = ChunkPos.pack(
                SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ())
        );

        Set<DungeonTerritoryId> candidates =
                this.territoriesByChunk.get(chunkKey);

        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }

        for (DungeonTerritoryId territoryId : candidates) {
            DungeonTerritory territory = this.territories.get(territoryId);

            if (territory != null && territory.bounds().contains(pos)) {
                return Optional.of(territory);
            }
        }

        return Optional.empty();
    }

    public Optional<DungeonInstanceId> findOwner(BlockPos pos) {
        return this.findTerritory(pos).map(DungeonTerritory::instanceId);
    }

    public boolean addParticipant(
            DungeonInstanceId id,
            UUID playerId,
            long gameTime
    ) {
        DungeonInstance instance = this.instances.get(id);

        if (instance == null) {
            return false;
        }

        boolean changed = instance.addParticipant(playerId);

        if (changed) {
            instance.markActiveAt(gameTime);
            this.setDirty();
        }

        return changed;
    }

    public boolean removeParticipant(DungeonInstanceId id, UUID playerId) {
        DungeonInstance instance = this.instances.get(id);

        if (instance == null) {
            return false;
        }

        boolean changed = instance.removeParticipant(playerId);

        if (changed) {
            this.setDirty();
        }

        return changed;
    }

    public boolean setInstanceStatus(
            DungeonInstanceId id,
            DungeonStatus status
    ) {
        DungeonInstance instance = this.instances.get(id);

        if (instance == null) {
            return false;
        }

        boolean changed = instance.setStatus(status);

        if (changed) {
            this.setDirty();
        }

        return changed;
    }

    public void tick(ServerLevel level) {
        long gameTime = level.getGameTime();

        this.purgeExpiredPortalSessions(gameTime);
        this.removePortalSessionsForInactiveInstances();
        this.closeEmptyActiveInstances(gameTime);

        // Future lifecycle updates:
        // - expire old portal_closed instances
        // - remove terminal raids
        // - room-triggered encounter state updates
    }

    public PortalSession createPortalSession(
            DungeonInstanceId instanceId,
            UUID opener,
            BlockPos obeliskPos,
            DungeonAccessMode accessMode,
            long currentGameTime
    ) {
        this.purgeExpiredPortalSessions(currentGameTime);

        long durationTicks = 20L * 60L;

        PortalSession session = new PortalSession(
                PortalSessionId.create(),
                instanceId,
                opener,
                obeliskPos,
                accessMode,
                currentGameTime + durationTicks
        );

        this.portalSessions.put(session.id(), session);
        this.setDirty();

        return session;
    }

    public Optional<PortalSession> getPortalSession(PortalSessionId id) {
        return Optional.ofNullable(this.portalSessions.get(id));
    }

    public Optional<PortalSession> findActivePartyOpenSession(
            BlockPos obeliskPos,
            long gameTime
    ) {
        this.purgeExpiredPortalSessions(gameTime);
        this.removePortalSessionsForInactiveInstances();

        return this.portalSessions.values().stream()
                .filter(session -> session.accessMode() == DungeonAccessMode.PARTY_OPEN)
                .filter(session -> !session.isExpired(gameTime))
                .filter(session -> session.obeliskPos().equals(obeliskPos))
                .filter(session -> {
                    DungeonInstance instance = this.instances.get(session.instanceId());

                    return instance != null
                            && instance.status() == DungeonStatus.ACTIVE;
                })
                .findFirst();
    }

    public int removePortalSessionsForInactiveInstances() {
        int removedCount = 0;

        Iterator<PortalSession> iterator = this.portalSessions.values().iterator();

        while (iterator.hasNext()) {
            PortalSession session = iterator.next();
            DungeonInstance instance = this.instances.get(session.instanceId());

            if (instance == null || instance.status() != DungeonStatus.ACTIVE) {
                iterator.remove();
                removedCount++;
            }
        }

        if (removedCount > 0) {
            this.setDirty();
        }

        return removedCount;
    }

    public boolean removePortalSession(PortalSessionId id) {
        boolean changed = this.portalSessions.remove(id) != null;

        if (changed) {
            this.setDirty();
        }

        return changed;
    }

    public boolean addPortalSessionParticipant(
            PortalSessionId id,
            UUID playerId
    ) {
        PortalSession session = this.portalSessions.get(id);

        if (session == null) {
            return false;
        }

        boolean changed = session.addParticipant(playerId);

        if (changed) {
            this.setDirty();
        }

        return changed;
    }

    public boolean removePortalSessionParticipant(
            PortalSessionId id,
            UUID playerId
    ) {
        PortalSession session = this.portalSessions.get(id);

        if (session == null) {
            return false;
        }

        boolean changed = session.removeParticipant(playerId);

        if (changed) {
            this.setDirty();
        }

        return changed;
    }

    public int removePortalSessionParticipantFromInstanceSessions(
            DungeonInstanceId instanceId,
            UUID playerId
    ) {
        int changedCount = 0;

        for (PortalSession session : this.portalSessions.values()) {
            if (!session.instanceId().equals(instanceId)) {
                continue;
            }

            if (session.removeParticipant(playerId)) {
                changedCount++;
            }
        }

        if (changedCount > 0) {
            this.setDirty();
        }

        return changedCount;
    }

    public int purgeExpiredPortalSessions(long gameTime) {
        int removed = 0;

        Iterator<PortalSession> iterator = this.portalSessions.values().iterator();

        while (iterator.hasNext()) {
            PortalSession session = iterator.next();

            if (session.isExpired(gameTime)) {
                iterator.remove();
                removed++;
            }
        }

        if (removed > 0) {
            this.setDirty();
        }

        return removed;
    }

    public int activePortalSessionCount() {
        return this.portalSessions.size();
    }

    public int closeEmptyActiveInstances(long gameTime) {
        int changedCount = 0;

        for (DungeonInstance instance : this.instances.values()) {
            if (instance.status() != DungeonStatus.ACTIVE) {
                continue;
            }

            if (!instance.participants().isEmpty()) {
                continue;
            }

            if (instance.setStatus(DungeonStatus.PORTAL_CLOSED)) {
                instance.markClosedAt(gameTime);
                changedCount++;
            }
        }

        if (changedCount > 0) {
            this.setDirty();
        }

        return changedCount;
    }

    public Optional<DungeonRaidInstance> findActiveRoomRaid(
            DungeonInstanceId dungeonInstanceId,
            DungeonRoomId roomId
    ) {
        Set<DungeonRaidId> raidIds = this.raidsByInstance.get(dungeonInstanceId);

        if (raidIds == null || raidIds.isEmpty()) {
            return Optional.empty();
        }

        for (DungeonRaidId raidId : raidIds) {
            DungeonRaidInstance raid = this.raids.get(raidId);

            if (raid == null) {
                continue;
            }

            if (raid.isTerminal()) {
                continue;
            }

            if (raid.roomId().isEmpty()) {
                continue;
            }

            if (!raid.roomId().get().equals(roomId)) {
                continue;
            }

            return Optional.of(raid);
        }

        return Optional.empty();
    }

    public DungeonRaidInstance createRoomRaid(
            DungeonInstanceId dungeonInstanceId,
            DungeonRoomId roomId,
            Identifier raidType,
            long gameTime
    ) {
        DungeonRaidInstance raid = DungeonRaidInstance.createRoomRaid(
                dungeonInstanceId,
                roomId,
                raidType,
                gameTime
        );

        this.raids.put(raid.id(), raid);
        this.indexRaid(raid);
        this.setDirty();

        return raid;
    }

    public Optional<DungeonRaidInstance> getRaid(DungeonRaidId id) {
        return Optional.ofNullable(this.raids.get(id));
    }

    public boolean removeRaid(DungeonRaidId id) {
        DungeonRaidInstance removed = this.raids.remove(id);

        if (removed == null) {
            return false;
        }

        this.unindexRaid(removed);
        this.setDirty();

        return true;
    }

    public DungeonRaidInstance getOrCreateRoomRaid(
            DungeonInstanceId dungeonInstanceId,
            DungeonRoomId roomId,
            Identifier raidType,
            long gameTime
    ) {
        return this.findActiveRoomRaid(dungeonInstanceId, roomId)
                .orElseGet(() -> this.createRoomRaid(
                        dungeonInstanceId,
                        roomId,
                        raidType,
                        gameTime
                ));
    }

    public boolean setRaidStatus(
            DungeonRaidId id,
            DungeonRaidStatus status
    ) {
        DungeonRaidInstance raid = this.raids.get(id);

        if (raid == null) {
            return false;
        }

        boolean changed = raid.setStatus(status);

        if (changed) {
            this.setDirty();
        }

        return changed;
    }

    public boolean advanceRaidWave(
            DungeonRaidId id,
            long nextWaveGameTime
    ) {
        DungeonRaidInstance raid = this.raids.get(id);

        if (raid == null) {
            return false;
        }

        raid.advanceWave(nextWaveGameTime);
        this.setDirty();
        return true;
    }

    public boolean markRaidMobSpawned(DungeonRaidId id) {
        DungeonRaidInstance raid = this.raids.get(id);

        if (raid == null) {
            return false;
        }

        raid.markMobSpawned();
        this.setDirty();
        return true;
    }

    public boolean markRaidMobKilled(DungeonRaidId id) {
        DungeonRaidInstance raid = this.raids.get(id);

        if (raid == null) {
            return false;
        }

        raid.markMobKilled();
        this.setDirty();
        return true;
    }

    public boolean retireRuntimeInstance(
            DungeonInstanceId id,
            DungeonSiteUsageStatus finalStatus,
            long gameTime
    ) {
        if (!finalStatus.isTerminal()) {
            throw new IllegalArgumentException(
                    "Runtime instance can only retire a site with terminal status."
            );
        }

        Optional<DungeonInstance> removed = this.removeRuntimeState(id);

        if (removed.isEmpty()) {
            return false;
        }

        DungeonSiteKey siteKey = this.reservedSiteByInstance.remove(id);

        if (siteKey == null) {
            siteKey = removed.get().siteKey();
        }

        this.siteSnapshots.remove(siteKey);

        DungeonSiteRecord record = this.siteRecords.get(siteKey);

        if (record == null) {
            record = DungeonSiteRecord.reserved(
                    siteKey,
                    id,
                    removed.get().createdGameTime()
            );
        }

        this.siteRecords.put(
                siteKey,
                record.retire(finalStatus, gameTime)
        );

        this.setDirty();

        return true;
    }

    private Optional<DungeonInstance> removeRuntimeState(DungeonInstanceId id) {
        DungeonInstance removed = this.instances.remove(id);

        if (removed == null) {
            return Optional.empty();
        }

        DungeonTerritory removedTerritory =
                this.territories.remove(removed.territoryId());

        if (removedTerritory != null) {
            this.unindexTerritory(removedTerritory);
        }

        this.roomStates.removeInstance(id);

        Set<DungeonRaidId> raidIds = this.raidsByInstance.remove(id);

        if (raidIds != null) {
            for (DungeonRaidId raidId : raidIds) {
                this.raids.remove(raidId);
            }
        }

        this.markSessionsCleanedForInstance(id);

        this.setDirty();

        return Optional.of(removed);
    }

    private void markSessionsCleanedForInstance(DungeonInstanceId id) {
        boolean changed = false;

        for (DungeonSession session : this.sessions.values()) {
            if (!session.instanceId().equals(id)) {
                continue;
            }

            if (session.markCleaned()) {
                changed = true;
            }
        }

        if (changed) {
            this.setDirty();
        }
    }

    public void forEachActiveInstance(Consumer<DungeonInstance> consumer) {
        for (DungeonInstance instance : this.instances.values()) {
            if (instance.status() == DungeonStatus.ACTIVE) {
                consumer.accept(instance);
            }
        }
    }

    public List<DungeonInstance> findClosedInstancesReadyForCleanup(long gameTime) {
        List<DungeonInstance> result = new ArrayList<>();

        for (DungeonInstance instance : this.instances.values()) {
            if (instance.status() != DungeonStatus.PORTAL_CLOSED) {
                continue;
            }

            if (instance.closedGameTime() < 0L) {
                continue;
            }

            if (gameTime - instance.closedGameTime() >= CLOSED_INSTANCE_CLEANUP_DELAY_TICKS) {
                result.add(instance);
            }
        }

        return result;
    }

    public void initializeRoomStates(
            DungeonInstance instance,
            DungeonSite site
    ) {
        this.roomStates.initializeRoomStates(instance, site);
    }

    public Optional<DungeonRoomState> getRoomState(
            DungeonInstanceId instanceId,
            DungeonRoomId roomId
    ) {
        return this.roomStates.get(instanceId, roomId);
    }

    public boolean setRoomStatus(
            DungeonInstanceId instanceId,
            DungeonRoomId roomId,
            DungeonRoomStatus status
    ) {
        return this.roomStates.setStatus(instanceId, roomId, status);
    }

    public boolean unlockBossRooms(DungeonInstanceId instanceId) {
        return this.roomStates.unlockBossRooms(instanceId);
    }

    public boolean markRewardClaimed(
            DungeonInstanceId instanceId,
            DungeonRoomId roomId
    ) {
        return this.roomStates.markRewardClaimed(instanceId, roomId);
    }

    public Collection<DungeonRoomState> roomStates(DungeonInstanceId instanceId) {
        return this.roomStates.allForInstance(instanceId);
    }

    public boolean removeRoomStates(DungeonInstanceId instanceId) {
        return this.roomStates.removeInstance(instanceId);
    }


    public boolean isSiteUnreached(DungeonSiteKey siteKey) {
        return !this.siteRecords.containsKey(siteKey);
    }

    public boolean isSiteReserved(DungeonSiteKey siteKey) {
        DungeonSiteRecord record = this.siteRecords.get(siteKey);

        return record != null
                && record.status() == DungeonSiteUsageStatus.RESERVED;
    }

    public String generatedSiteReservationRejectionReason(DungeonSiteKey siteKey) {
        DungeonSiteRecord record = this.siteRecords.get(siteKey);

        if (record == null) {
            return "candidate_accepted";
        }

        if (record.status() == DungeonSiteUsageStatus.RESERVED) {
            return "candidate_reserved";
        }

        if (record.status().isTerminal()) {
            return "candidate_already_reached";
        }

        return "candidate_predicate_rejected";
    }

    public Optional<DungeonSiteRecord> siteRecord(DungeonSiteKey siteKey) {
        return Optional.ofNullable(this.siteRecords.get(siteKey));
    }

    public int siteRecordCount() {
        return this.siteRecords.size();
    }

    public long reservedSiteCount() {
        return this.siteRecords.values()
                .stream()
                .filter(record -> record.status() == DungeonSiteUsageStatus.RESERVED)
                .count();
    }

    public long retiredSiteCount() {
        return this.siteRecords.values()
                .stream()
                .filter(record -> record.status().isTerminal())
                .count();
    }

    public Optional<DungeonSiteKey> reservedSite(DungeonInstanceId instanceId) {
        return Optional.ofNullable(this.reservedSiteByInstance.get(instanceId));
    }

    public Optional<DungeonSite> getSiteSnapshot(DungeonSiteKey siteKey) {
        return Optional.ofNullable(this.siteSnapshots.get(siteKey));
    }

    public boolean putSiteSnapshot(DungeonSite site) {
        DungeonSite previous = this.siteSnapshots.put(site.key(), site);

        if (!site.equals(previous)) {
            this.setDirty();
            return true;
        }

        return false;
    }

    public boolean removeSiteSnapshot(DungeonSiteKey siteKey) {
        boolean changed = this.siteSnapshots.remove(siteKey) != null;

        if (changed) {
            this.setDirty();
        }

        return changed;
    }

    public DungeonInstance reserveSiteForNewInstance(
            DungeonDifficulty difficulty,
            DungeonSite site,
            long gameTime
    ) {
        if (!this.isSiteUnreached(site.key())) {
            throw new IllegalStateException(
                    "Dungeon site is already known/reserved/retired: " + site.key()
            );
        }

        DungeonInstanceCreation creation =
                DungeonInstanceFactory.create(
                        difficulty,
                        site,
                        gameTime
                );

        DungeonTerritory territory = creation.territory();
        DungeonInstance instance = creation.instance();

        this.territories.put(territory.id(), territory);
        this.indexTerritory(territory);

        this.instances.put(instance.id(), instance);

        DungeonSiteRecord record = DungeonSiteRecord.reserved(
                site.key(),
                instance.id(),
                gameTime
        );

        this.siteRecords.put(site.key(), record);
        this.reservedSiteByInstance.put(instance.id(), site.key());

        this.siteSnapshots.put(site.key(), site);

        this.initializeRoomStates(instance, site);

        this.setDirty();

        return instance;
    }

    private void indexTerritory(DungeonTerritory territory) {
        for (long chunkKey : chunkKeysFor(territory.bounds())) {
            this.territoriesByChunk
                    .computeIfAbsent(chunkKey, ignored -> new HashSet<>())
                    .add(territory.id());
        }
    }

    private void unindexTerritory(DungeonTerritory territory) {
        for (long chunkKey : chunkKeysFor(territory.bounds())) {
            Set<DungeonTerritoryId> ids = this.territoriesByChunk.get(chunkKey);

            if (ids == null) {
                continue;
            }

            ids.remove(territory.id());

            if (ids.isEmpty()) {
                this.territoriesByChunk.remove(chunkKey);
            }
        }
    }

    private static List<Long> chunkKeysFor(DungeonBounds bounds) {
        int minChunkX = SectionPos.blockToSectionCoord(bounds.minX());
        int maxChunkX = SectionPos.blockToSectionCoord(bounds.maxX());
        int minChunkZ = SectionPos.blockToSectionCoord(bounds.minZ());
        int maxChunkZ = SectionPos.blockToSectionCoord(bounds.maxZ());

        List<Long> result = new ArrayList<>();

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                result.add(ChunkPos.pack(chunkX, chunkZ));
            }
        }

        return result;
    }

    public Collection<DungeonInstance> instances() {
        return List.copyOf(this.instances.values());
    }

    public int instanceCount() {
        return this.instances.size();
    }

    public int territoryCount() {
        return this.territories.size();
    }

    private void indexRaid(DungeonRaidInstance raid) {
        this.raidsByInstance
                .computeIfAbsent(raid.dungeonInstanceId(), ignored -> new HashSet<>())
                .add(raid.id());
    }

    private void unindexRaid(DungeonRaidInstance raid) {
        Set<DungeonRaidId> raidIds = this.raidsByInstance.get(raid.dungeonInstanceId());

        if (raidIds == null) {
            return;
        }

        raidIds.remove(raid.id());

        if (raidIds.isEmpty()) {
            this.raidsByInstance.remove(raid.dungeonInstanceId());
        }
    }
}
