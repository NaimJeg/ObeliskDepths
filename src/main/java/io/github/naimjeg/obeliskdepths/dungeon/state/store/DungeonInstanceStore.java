package io.github.naimjeg.obeliskdepths.dungeon.state.store;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonStatus;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class DungeonInstanceStore {
    private final Map<DungeonInstanceId, DungeonInstance> instances = new HashMap<>();
    private final Map<DungeonStatus, Set<DungeonInstanceId>> instanceIdsByStatus =
            new EnumMap<>(DungeonStatus.class);
    private final Runnable dirty;

    public DungeonInstanceStore(Runnable dirty) {
        this.dirty = dirty;
    }

    public void load(Collection<DungeonInstance> instances) {
        for (DungeonInstance instance : instances) {
            this.putLoaded(instance);
        }
    }

    public Collection<DungeonInstance> all() {
        return List.copyOf(this.instances.values());
    }

    public boolean isEmpty() {
        return this.instances.isEmpty();
    }

    public int size() {
        return this.instances.size();
    }

    public Optional<DungeonInstance> get(DungeonInstanceId id) {
        return Optional.ofNullable(this.instances.get(id));
    }

    public DungeonInstance put(DungeonInstance instance) {
        DungeonInstance previous = this.instances.put(instance.id(), instance);

        if (previous != null) {
            this.unindex(previous);
        }

        this.index(instance);

        if (previous != instance) {
            this.dirty.run();
        }

        return instance;
    }

    public Optional<DungeonInstance> remove(DungeonInstanceId id) {
        DungeonInstance removed = this.instances.remove(id);

        if (removed == null) {
            return Optional.empty();
        }

        this.unindex(removed);
        this.dirty.run();
        return Optional.of(removed);
    }

    public boolean setStatus(
            DungeonInstanceId id,
            DungeonStatus status
    ) {
        DungeonInstance instance = this.instances.get(id);

        if (instance == null) {
            return false;
        }

        DungeonStatus previousStatus = instance.status();
        boolean changed = instance.setStatus(status);

        if (changed) {
            this.reindexStatus(instance, previousStatus);
            this.dirty.run();
        }

        return changed;
    }

    public boolean fail(
            DungeonInstanceId id,
            long gameTime
    ) {
        DungeonInstance instance = this.instances.get(id);

        if (instance == null) {
            return false;
        }

        DungeonStatus previousStatus = instance.status();
        boolean changed = instance.setStatus(DungeonStatus.FAILED);

        if (instance.closedGameTime() < 0L) {
            instance.markClosedAt(gameTime);
            changed = true;
        }

        if (changed) {
            if (previousStatus != instance.status()) {
                this.reindexStatus(instance, previousStatus);
            }

            this.dirty.run();
        }

        return changed;
    }

    public boolean markPortalClosed(
            DungeonInstanceId id,
            long gameTime
    ) {
        DungeonInstance instance = this.instances.get(id);

        if (instance == null) {
            return false;
        }

        DungeonStatus previousStatus = instance.status();
        boolean changed = instance.setStatus(DungeonStatus.PORTAL_CLOSED);

        if (instance.closedGameTime() < 0L) {
            instance.markClosedAt(gameTime);
            changed = true;
        }

        if (changed) {
            if (previousStatus != instance.status()) {
                this.reindexStatus(instance, previousStatus);
            }

            this.dirty.run();
        }

        return changed;
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
            this.dirty.run();
        }

        return changed;
    }

    public boolean removeParticipant(
            DungeonInstanceId id,
            UUID playerId
    ) {
        DungeonInstance instance = this.instances.get(id);

        if (instance == null) {
            return false;
        }

        boolean changed = instance.removeParticipant(playerId);

        if (changed) {
            this.dirty.run();
        }

        return changed;
    }

    public void forEachActive(Consumer<DungeonInstance> consumer) {
        for (DungeonInstanceId id : this.idsForStatus(DungeonStatus.ACTIVE)) {
            DungeonInstance instance = this.instances.get(id);

            if (instance != null && instance.status() == DungeonStatus.ACTIVE) {
                consumer.accept(instance);
            }
        }
    }

    public List<DungeonInstance> findClosedReadyForCleanup(
            long gameTime,
            long cleanupDelayTicks
    ) {
        List<DungeonInstance> result = new ArrayList<>();

        for (DungeonStatus status : List.of(
                DungeonStatus.PORTAL_CLOSED,
                DungeonStatus.FAILED,
                DungeonStatus.EXPIRED
        )) {
            for (DungeonInstanceId id : this.idsForStatus(status)) {
                DungeonInstance instance = this.instances.get(id);

                if (instance == null || instance.closedGameTime() < 0L) {
                    continue;
                }

                if (gameTime - instance.closedGameTime() >= cleanupDelayTicks) {
                    result.add(instance);
                }
            }
        }

        return result;
    }

    private void putLoaded(DungeonInstance instance) {
        DungeonInstance previous = this.instances.put(instance.id(), instance);

        if (previous != null) {
            throw new IllegalStateException(
                    "Duplicate dungeon instance id in saved data: " + instance.id()
            );
        }

        this.index(instance);
    }

    private Set<DungeonInstanceId> idsForStatus(DungeonStatus status) {
        return Set.copyOf(this.instanceIdsByStatus.getOrDefault(status, Set.of()));
    }

    private void index(DungeonInstance instance) {
        this.instanceIdsByStatus
                .computeIfAbsent(instance.status(), ignored -> new HashSet<>())
                .add(instance.id());
    }

    private void unindex(DungeonInstance instance) {
        Set<DungeonInstanceId> ids = this.instanceIdsByStatus.get(instance.status());

        if (ids == null) {
            return;
        }

        ids.remove(instance.id());

        if (ids.isEmpty()) {
            this.instanceIdsByStatus.remove(instance.status());
        }
    }

    private void reindexStatus(
            DungeonInstance instance,
            DungeonStatus previousStatus
    ) {
        Set<DungeonInstanceId> previousIds = this.instanceIdsByStatus.get(previousStatus);

        if (previousIds != null) {
            previousIds.remove(instance.id());

            if (previousIds.isEmpty()) {
                this.instanceIdsByStatus.remove(previousStatus);
            }
        }

        this.index(instance);
    }
}
