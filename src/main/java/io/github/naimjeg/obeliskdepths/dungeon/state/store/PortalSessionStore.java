package io.github.naimjeg.obeliskdepths.dungeon.state.store;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.id.PortalSessionId;
import io.github.naimjeg.obeliskdepths.dungeon.portal.PortalSession;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.*;
import java.util.function.Predicate;

public final class PortalSessionStore {
    private final Map<PortalSessionId, PortalSession> sessions = new LinkedHashMap<>();
    private final Map<DungeonInstanceId, Set<PortalSessionId>> sessionIdsByInstance =
            new HashMap<>();
    private final Map<SourceObeliskKey, Set<PortalSessionId>> sessionIdsBySourceObelisk =
            new HashMap<>();
    private final Map<UUID, Set<PortalSessionId>> sessionIdsByParticipant = new HashMap<>();
    private final Runnable dirty;

    public PortalSessionStore(Runnable dirty) {
        this.dirty = dirty;
    }

    public void load(Collection<PortalSession> sessions) {
        for (PortalSession session : sessions) {
            this.putLoaded(session);
        }
    }

    public Collection<PortalSession> all() {
        return List.copyOf(this.sessions.values());
    }

    /**
     * Returns and rotates a bounded maintenance batch. Rotation prevents a
     * permanently live prefix from starving later sessions without retaining
     * an iterator across server ticks.
     */
    public List<PortalSession> nextMaintenanceBatch(int maximumSessions) {
        if (maximumSessions <= 0 || this.sessions.isEmpty()) {
            return List.of();
        }
        int count = Math.min(maximumSessions, this.sessions.size());
        ArrayList<PortalSession> batch = new ArrayList<>(count);
        Iterator<Map.Entry<PortalSessionId, PortalSession>> iterator =
                this.sessions.entrySet().iterator();
        while (iterator.hasNext() && batch.size() < count) {
            Map.Entry<PortalSessionId, PortalSession> entry = iterator.next();
            batch.add(entry.getValue());
            iterator.remove();
        }
        for (PortalSession session : batch) {
            this.sessions.put(session.id(), session);
        }
        return List.copyOf(batch);
    }

    public PortalSession add(PortalSession session) {
        PortalSession previous = this.sessions.put(session.id(), session);

        if (previous != null) {
            this.unindex(previous);
        }

        this.index(session);

        if (previous != session) {
            this.dirty.run();
        }

        return session;
    }

    public Optional<PortalSession> get(PortalSessionId id) {
        return Optional.ofNullable(this.sessions.get(id));
    }

    public boolean remove(PortalSessionId id) {
        PortalSession removed = this.sessions.remove(id);

        if (removed == null) {
            return false;
        }

        this.unindex(removed);
        this.dirty.run();
        return true;
    }

    public boolean hasValidSessionForInstance(
            DungeonInstanceId instanceId,
            long gameTime
    ) {
        return this.findByInstance(instanceId, gameTime).isPresent();
    }

    public Optional<PortalSession> findByInstance(
            DungeonInstanceId instanceId,
            long gameTime
    ) {
        Set<PortalSessionId> ids = this.sessionIdsByInstance.get(instanceId);

        if (ids == null || ids.isEmpty()) {
            return Optional.empty();
        }

        return ids.stream()
                .map(this.sessions::get)
                .filter(session -> session != null && !session.isExpired(gameTime))
                .min(Comparator.comparingLong(PortalSession::expiresAtGameTime)
                        .thenComparing(session -> session.id().value()));
    }

    public Optional<PortalSession> findValidBySourceObelisk(
            ResourceKey<Level> sourceDimension,
            BlockPos obeliskPos,
            long gameTime
    ) {
        SourceObeliskKey key = new SourceObeliskKey(
                Objects.requireNonNull(sourceDimension, "sourceDimension"),
                Objects.requireNonNull(obeliskPos, "obeliskPos")
        );
        Set<PortalSessionId> ids = this.sessionIdsBySourceObelisk.get(key);
        if (ids == null || ids.isEmpty()) {
            return Optional.empty();
        }
        return ids.stream()
                .map(this.sessions::get)
                .filter(session -> session != null && !session.isExpired(gameTime))
                .min(Comparator.comparingLong(PortalSession::expiresAtGameTime)
                        .thenComparing(session -> session.id().value()));
    }

    public int removeForSourceObelisk(
            ResourceKey<Level> sourceDimension,
            BlockPos obeliskPos
    ) {
        Set<PortalSessionId> ids = Set.copyOf(this.sessionIdsBySourceObelisk.getOrDefault(
                new SourceObeliskKey(sourceDimension, obeliskPos),
                Set.of()
        ));
        int removed = 0;

        for (PortalSessionId id : ids) {
            if (this.removeWithoutDirty(id)) {
                removed++;
            }
        }

        if (removed > 0) {
            this.dirty.run();
        }

        return removed;
    }

    public int removeForInactiveInstances(Predicate<DungeonInstanceId> activeInstance) {
        int removed = 0;
        Iterator<PortalSession> iterator = this.sessions.values().iterator();

        while (iterator.hasNext()) {
            PortalSession session = iterator.next();

            if (!activeInstance.test(session.instanceId())) {
                iterator.remove();
                this.unindex(session);
                removed++;
            }
        }

        if (removed > 0) {
            this.dirty.run();
        }

        return removed;
    }

    public boolean addParticipant(
            PortalSessionId id,
            UUID playerId
    ) {
        PortalSession session = this.sessions.get(id);

        if (session == null) {
            return false;
        }

        boolean changed = session.addParticipant(playerId);

        if (changed) {
            this.addIndexValue(this.sessionIdsByParticipant, playerId, session.id());
            this.dirty.run();
        }

        return changed;
    }

    public boolean removeParticipant(
            PortalSessionId id,
            UUID playerId
    ) {
        PortalSession session = this.sessions.get(id);

        if (session == null) {
            return false;
        }

        boolean changed = session.removeParticipant(playerId);

        if (changed) {
            this.removeIndexValue(this.sessionIdsByParticipant, playerId, session.id());
            this.dirty.run();
        }

        return changed;
    }

    public int removeParticipantFromInstanceSessions(
            DungeonInstanceId instanceId,
            UUID playerId
    ) {
        Set<PortalSessionId> ids =
                Set.copyOf(this.sessionIdsByInstance.getOrDefault(instanceId, Set.of()));
        int changedCount = 0;

        for (PortalSessionId id : ids) {
            PortalSession session = this.sessions.get(id);

            if (session != null && session.removeParticipant(playerId)) {
                this.removeIndexValue(this.sessionIdsByParticipant, playerId, session.id());
                changedCount++;
            }
        }

        if (changedCount > 0) {
            this.dirty.run();
        }

        return changedCount;
    }

    public int purgeExpired(long gameTime) {
        int removed = 0;
        Iterator<PortalSession> iterator = this.sessions.values().iterator();

        while (iterator.hasNext()) {
            PortalSession session = iterator.next();

            if (session.isExpired(gameTime)) {
                iterator.remove();
                this.unindex(session);
                removed++;
            }
        }

        if (removed > 0) {
            this.dirty.run();
        }

        return removed;
    }

    public int size() {
        return this.sessions.size();
    }

    private void putLoaded(PortalSession session) {
        PortalSession previous = this.sessions.put(session.id(), session);

        if (previous != null) {
            throw new IllegalStateException(
                    "Duplicate portal session id in saved data: " + session.id()
            );
        }

        this.index(session);
    }

    private boolean removeWithoutDirty(PortalSessionId id) {
        PortalSession removed = this.sessions.remove(id);

        if (removed == null) {
            return false;
        }

        this.unindex(removed);
        return true;
    }

    private void index(PortalSession session) {
        this.addIndexValue(this.sessionIdsByInstance, session.instanceId(), session.id());
        this.addIndexValue(
                this.sessionIdsBySourceObelisk,
                new SourceObeliskKey(session.sourceDimension(), session.obeliskPos()),
                session.id()
        );

        for (UUID participant : session.participants()) {
            this.addIndexValue(this.sessionIdsByParticipant, participant, session.id());
        }
    }

    private void unindex(PortalSession session) {
        this.removeIndexValue(this.sessionIdsByInstance, session.instanceId(), session.id());
        this.removeIndexValue(
                this.sessionIdsBySourceObelisk,
                new SourceObeliskKey(session.sourceDimension(), session.obeliskPos()),
                session.id()
        );

        for (UUID participant : session.participants()) {
            this.removeIndexValue(this.sessionIdsByParticipant, participant, session.id());
        }
    }

    private <K> void addIndexValue(
            Map<K, Set<PortalSessionId>> index,
            K key,
            PortalSessionId sessionId
    ) {
        index.computeIfAbsent(key, ignored -> new HashSet<>()).add(sessionId);
    }

    private <K> void removeIndexValue(
            Map<K, Set<PortalSessionId>> index,
            K key,
            PortalSessionId sessionId
    ) {
        Set<PortalSessionId> values = index.get(key);

        if (values == null) {
            return;
        }

        values.remove(sessionId);

        if (values.isEmpty()) {
            index.remove(key);
        }
    }

    private record SourceObeliskKey(
            ResourceKey<Level> sourceDimension,
            BlockPos obeliskPos
    ) {
        private SourceObeliskKey {
            obeliskPos = obeliskPos.immutable();
        }
    }
}
