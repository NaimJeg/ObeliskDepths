package io.github.naimjeg.obeliskdepths.dungeon.state.store;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSession;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSessionState;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class DungeonSessionStore {
    private final Map<UUID, DungeonSession> sessions = new HashMap<>();
    private final Map<DungeonInstanceId, UUID> sessionIdByInstance = new HashMap<>();
    private final Map<DungeonSiteKey, UUID> sessionIdBySite = new HashMap<>();
    private final Map<UUID, Set<UUID>> sessionIdsByParticipant = new HashMap<>();
    private final Map<UUID, Set<UUID>> sessionIdsByPhysicalParticipant = new HashMap<>();
    private final Runnable dirty;

    public DungeonSessionStore(Runnable dirty) {
        this.dirty = dirty;
    }

    public void load(Collection<DungeonSession> sessions) {
        for (DungeonSession session : sessions) {
            this.putLoaded(session);
        }
    }

    public Collection<DungeonSession> all() {
        return List.copyOf(this.sessions.values());
    }

    public DungeonSession add(DungeonSession session) {
        DungeonSession previous = this.sessions.get(session.id());
        if (previous == session) {
            return session;
        }

        if (previous != null) {
            throw new IllegalStateException(
                    "Duplicate dungeon session id: " + session.id()
            );
        }

        this.validateUniqueSessionIndexes(session);
        this.sessions.put(session.id(), session);
        this.index(session);
        this.dirty.run();
        return session;
    }

    public Optional<DungeonSession> get(UUID id) {
        return Optional.ofNullable(this.sessions.get(id));
    }

    public boolean remove(UUID id) {
        DungeonSession removed = this.sessions.remove(id);

        if (removed == null) {
            return false;
        }

        this.unindex(removed);
        this.dirty.run();
        return true;
    }

    public Optional<DungeonSession> findByInstance(DungeonInstanceId instanceId) {
        return Optional.ofNullable(this.sessionIdByInstance.get(instanceId))
                .map(this.sessions::get);
    }

    public Optional<DungeonSession> findBySite(DungeonSiteKey siteKey) {
        return Optional.ofNullable(this.sessionIdBySite.get(siteKey))
                .map(this.sessions::get);
    }

    public Optional<DungeonSession> findByPlayer(UUID playerId) {
        return this.deterministicSession(this.sessionIdsByParticipant.get(playerId));
    }

    public Optional<DungeonInstanceId> findPhysicalInstanceByPlayer(UUID playerId) {
        return this.deterministicSession(this.sessionIdsByPhysicalParticipant.get(playerId))
                .map(DungeonSession::instanceId);
    }

    public boolean registerParticipant(
            DungeonSession session,
            UUID playerId
    ) {
        DungeonSession stored = this.requireStored(session);
        return this.mutateAndReindexParticipants(
                stored,
                () -> stored.registerParticipant(playerId)
        );
    }

    public boolean removeParticipant(
            DungeonSession session,
            UUID playerId
    ) {
        DungeonSession stored = this.requireStored(session);
        return this.mutateAndReindexParticipants(
                stored,
                () -> stored.removeParticipant(playerId)
        );
    }

    public boolean registerPhysicalParticipant(
            DungeonSession session,
            UUID playerId
    ) {
        DungeonSession stored = this.requireStored(session);
        return this.mutateAndReindexParticipants(
                stored,
                () -> stored.registerPhysicalParticipant(playerId)
        );
    }

    public boolean unregisterPhysicalParticipant(
            DungeonSession session,
            UUID playerId
    ) {
        DungeonSession stored = this.requireStored(session);
        return this.mutateAndReindexParticipants(
                stored,
                () -> stored.unregisterPhysicalParticipant(playerId)
        );
    }

    public int unregisterPhysicalParticipantFromAll(UUID playerId) {
        Set<UUID> sessionIds =
                Set.copyOf(this.sessionIdsByPhysicalParticipant.getOrDefault(playerId, Set.of()));
        int changed = 0;

        for (UUID sessionId : sessionIds) {
            DungeonSession session = this.sessions.get(sessionId);

            if (session != null && session.unregisterPhysicalParticipant(playerId)) {
                changed++;
            }
        }

        if (changed > 0) {
            this.rebuildPhysicalParticipantIndexFor(playerId);
            this.dirty.run();
        }

        return changed;
    }

    public boolean markPortalEntrySucceeded(
            DungeonSession session,
            UUID playerId,
            long gameTime
    ) {
        DungeonSession stored = this.requireStored(session);
        return this.mutateAndReindexParticipants(
                stored,
                () -> stored.markPortalEntrySucceeded(playerId, gameTime)
        );
    }

    public boolean setState(
            DungeonSession session,
            DungeonSessionState state
    ) {
        DungeonSession stored = this.requireStored(session);
        return this.mark(stored.setState(state));
    }

    public boolean markStarterInside(
            DungeonSession session,
            long gameTime
    ) {
        DungeonSession stored = this.requireStored(session);
        return this.mark(stored.markStarterInside(gameTime));
    }

    public boolean markAbandonPending(DungeonSession session) {
        DungeonSession stored = this.requireStored(session);
        return this.mark(stored.markAbandonPending());
    }

    public boolean markAbandoned(DungeonSession session) {
        DungeonSession stored = this.requireStored(session);
        return this.mark(stored.markAbandoned());
    }

    public boolean markCleaned(DungeonSession session) {
        DungeonSession stored = this.requireStored(session);
        return this.mark(stored.markCleaned());
    }

    public boolean markCompleted(DungeonSession session) {
        DungeonSession stored = this.requireStored(session);
        return this.mark(stored.markCompleted());
    }

    public boolean markFailed(DungeonSession session) {
        DungeonSession stored = this.requireStored(session);
        return this.mark(stored.markFailed());
    }

    public int clearSpawnedEntityIds(DungeonSession session) {
        DungeonSession stored = this.requireStored(session);
        int removed = stored.clearSpawnedEntityIds();

        if (removed > 0) {
            this.dirty.run();
        }

        return removed;
    }

    public boolean markCleanedForInstance(DungeonInstanceId instanceId) {
        UUID id = this.sessionIdByInstance.get(instanceId);
        if (id == null) {
            return false;
        }

        DungeonSession session = this.sessions.get(id);

        if (session == null || !session.markCleaned()) {
            return false;
        }

        this.dirty.run();
        return true;
    }

    private void putLoaded(DungeonSession session) {
        if (this.sessions.containsKey(session.id())) {
            throw new IllegalStateException(
                    "Duplicate dungeon session id in saved data: " + session.id()
            );
        }

        this.validateUniqueSessionIndexes(session);
        this.sessions.put(session.id(), session);
        this.index(session);
    }

    private DungeonSession requireStored(DungeonSession session) {
        DungeonSession stored = this.sessions.get(session.id());
        if (stored == null || stored != session) {
            throw new IllegalArgumentException(
                    "Dungeon session is not owned by this store: " + session.id()
            );
        }

        return stored;
    }

    private void validateUniqueSessionIndexes(DungeonSession session) {
        UUID existingInstanceSession = this.sessionIdByInstance.get(session.instanceId());
        if (existingInstanceSession != null && !existingInstanceSession.equals(session.id())) {
            throw new IllegalStateException(
                    "Dungeon instance already has a session: "
                            + session.instanceId()
                            + " existing="
                            + existingInstanceSession
                            + " new="
                            + session.id()
            );
        }

        UUID existingSiteSession = this.sessionIdBySite.get(session.siteKey());
        if (existingSiteSession != null && !existingSiteSession.equals(session.id())) {
            throw new IllegalStateException(
                    "Dungeon site already has a session: "
                            + session.siteKey()
                            + " existing="
                            + existingSiteSession
                            + " new="
                            + session.id()
            );
        }
    }

    private Optional<DungeonSession> deterministicSession(Set<UUID> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) {
            return Optional.empty();
        }

        return sessionIds.stream()
                .map(this.sessions::get)
                .filter(session -> session != null)
                .min(Comparator.comparingLong(DungeonSession::createdAtGameTime)
                        .thenComparing(DungeonSession::id));
    }

    private boolean mark(boolean changed) {
        if (changed) {
            this.dirty.run();
        }

        return changed;
    }

    private boolean mutateAndReindexParticipants(
            DungeonSession session,
            BooleanMutation mutation
    ) {
        Set<UUID> participantsBefore = Set.copyOf(session.participants());
        Set<UUID> physicalParticipantsBefore = Set.copyOf(session.physicalParticipants());
        boolean changed = mutation.apply();

        if (!changed) {
            return false;
        }

        this.reindexParticipantDelta(
                this.sessionIdsByParticipant,
                participantsBefore,
                session.participants(),
                session.id()
        );
        this.reindexParticipantDelta(
                this.sessionIdsByPhysicalParticipant,
                physicalParticipantsBefore,
                session.physicalParticipants(),
                session.id()
        );
        this.dirty.run();
        return true;
    }

    private void reindexParticipantDelta(
            Map<UUID, Set<UUID>> index,
            Set<UUID> before,
            Set<UUID> after,
            UUID sessionId
    ) {
        for (UUID removed : before) {
            if (!after.contains(removed)) {
                this.removeIndexValue(index, removed, sessionId);
            }
        }

        for (UUID added : after) {
            if (!before.contains(added)) {
                this.addIndexValue(index, added, sessionId);
            }
        }
    }

    private void rebuildPhysicalParticipantIndexFor(UUID playerId) {
        this.sessionIdsByPhysicalParticipant.remove(playerId);
        for (DungeonSession session : this.sessions.values()) {
            if (session.isPhysicalParticipant(playerId)) {
                this.addIndexValue(
                        this.sessionIdsByPhysicalParticipant,
                        playerId,
                        session.id()
                );
            }
        }
    }

    private void index(DungeonSession session) {
        this.sessionIdByInstance.put(session.instanceId(), session.id());
        this.sessionIdBySite.put(session.siteKey(), session.id());

        for (UUID participant : session.participants()) {
            this.addIndexValue(this.sessionIdsByParticipant, participant, session.id());
        }

        for (UUID participant : session.physicalParticipants()) {
            this.addIndexValue(this.sessionIdsByPhysicalParticipant, participant, session.id());
        }
    }

    private void unindex(DungeonSession session) {
        this.sessionIdByInstance.remove(session.instanceId(), session.id());
        this.sessionIdBySite.remove(session.siteKey(), session.id());

        for (UUID participant : session.participants()) {
            this.removeIndexValue(this.sessionIdsByParticipant, participant, session.id());
        }

        for (UUID participant : session.physicalParticipants()) {
            this.removeIndexValue(
                    this.sessionIdsByPhysicalParticipant,
                    participant,
                    session.id()
            );
        }
    }

    private <K> void addIndexValue(
            Map<K, Set<UUID>> index,
            K key,
            UUID sessionId
    ) {
        index.computeIfAbsent(key, ignored -> new HashSet<>()).add(sessionId);
    }

    private <K> void removeIndexValue(
            Map<K, Set<UUID>> index,
            K key,
            UUID sessionId
    ) {
        Set<UUID> values = index.get(key);

        if (values == null) {
            return;
        }

        values.remove(sessionId);

        if (values.isEmpty()) {
            index.remove(key);
        }
    }

    @FunctionalInterface
    private interface BooleanMutation {
        boolean apply();
    }
}
