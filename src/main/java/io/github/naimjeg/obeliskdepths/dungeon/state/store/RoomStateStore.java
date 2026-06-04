package io.github.naimjeg.obeliskdepths.dungeon.state.store;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomState;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomStatus;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonGeneratedRoom;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;

import java.util.*;

public final class RoomStateStore {
    private final Map<DungeonInstanceId, Map<DungeonRoomId, DungeonRoomState>> roomStatesByInstance =
            new HashMap<>();

    private final Runnable dirty;

    public RoomStateStore(Runnable dirty) {
        this.dirty = dirty;
    }

    public void load(Collection<DungeonRoomState> states) {
        for (DungeonRoomState state : states) {
            this.putLoaded(state);
        }
    }

    public List<DungeonRoomState> flatten() {
        return this.roomStatesByInstance.values()
                .stream()
                .flatMap(map -> map.values().stream())
                .toList();
    }

    public void initializeRoomStates(
            DungeonInstance instance,
            DungeonSite site
    ) {
        this.validateCanInitializeRoomStates(instance, site);
        Map<DungeonRoomId, DungeonRoomState> statesByRoom = new HashMap<>();

        for (DungeonGeneratedRoom room : site.rooms()) {
            statesByRoom.put(
                    room.id(),
                    DungeonRoomState.initial(
                            instance.id(),
                            room.type(),
                            room.id()
                    )
            );
        }

        this.roomStatesByInstance.put(instance.id(), statesByRoom);
        this.dirty.run();
    }

    public void validateCanInitializeRoomStates(
            DungeonInstance instance,
            DungeonSite site
    ) {
        if (this.roomStatesByInstance.containsKey(instance.id())) {
            throw new IllegalStateException(
                    "Dungeon room states already exist for instance: " + instance.id()
            );
        }

        Set<DungeonRoomId> roomIds = new HashSet<>();
        for (DungeonGeneratedRoom room : site.rooms()) {
            if (!roomIds.add(room.id())) {
                throw new IllegalStateException(
                        "Duplicate generated room id in dungeon site: "
                                + site.key()
                                + " room="
                                + room.id()
                );
            }
        }
    }

    public boolean hasAnyForInstance(DungeonInstanceId instanceId) {
        Map<DungeonRoomId, DungeonRoomState> states = this.roomStatesByInstance.get(instanceId);
        return states != null && !states.isEmpty();
    }

    public Optional<DungeonRoomState> get(
            DungeonInstanceId instanceId,
            DungeonRoomId roomId
    ) {
        Map<DungeonRoomId, DungeonRoomState> states =
                this.roomStatesByInstance.get(instanceId);

        if (states == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(states.get(roomId));
    }

    public Collection<DungeonRoomState> allForInstance(DungeonInstanceId instanceId) {
        Map<DungeonRoomId, DungeonRoomState> states =
                this.roomStatesByInstance.get(instanceId);

        if (states == null) {
            return List.of();
        }

        return List.copyOf(states.values());
    }

    public boolean setStatus(
            DungeonInstanceId instanceId,
            DungeonRoomId roomId,
            DungeonRoomStatus status
    ) {
        Optional<DungeonRoomState> state = this.get(instanceId, roomId);

        if (state.isEmpty()) {
            return false;
        }

        boolean changed = state.get().setStatus(status);

        if (changed) {
            this.dirty.run();
        }

        return changed;
    }

    public boolean unlockBossRooms(DungeonInstanceId instanceId) {
        Map<DungeonRoomId, DungeonRoomState> states =
                this.roomStatesByInstance.get(instanceId);

        if (states == null || states.isEmpty()) {
            return false;
        }

        boolean changed = false;

        for (DungeonRoomState state : states.values()) {
            if (state.type() != DungeonRoomType.BOSS
                    || state.status() != DungeonRoomStatus.LOCKED) {
                continue;
            }

            changed |= state.setStatus(DungeonRoomStatus.UNDISCOVERED);
        }

        if (changed) {
            this.dirty.run();
        }

        return changed;
    }

    public boolean markRewardClaimed(
            DungeonInstanceId instanceId,
            DungeonRoomId roomId
    ) {
        Optional<DungeonRoomState> state = this.get(instanceId, roomId);

        if (state.isEmpty()) {
            return false;
        }

        boolean changed = state.get().markRewardClaimed();

        if (changed) {
            this.dirty.run();
        }

        return changed;
    }

    public boolean removeInstance(DungeonInstanceId instanceId) {
        return !this.removeInstanceStates(instanceId).isEmpty();
    }

    public List<DungeonRoomState> removeInstanceStates(DungeonInstanceId instanceId) {
        Map<DungeonRoomId, DungeonRoomState> removed =
                this.roomStatesByInstance.remove(instanceId);

        if (removed == null || removed.isEmpty()) {
            return List.of();
        }

        this.dirty.run();
        return List.copyOf(removed.values());
    }

    public void restoreInstanceStates(
            DungeonInstanceId instanceId,
            Collection<DungeonRoomState> states
    ) {
        if (this.roomStatesByInstance.containsKey(instanceId)) {
            throw new IllegalStateException(
                    "Dungeon room states already exist for instance: " + instanceId
            );
        }

        Map<DungeonRoomId, DungeonRoomState> statesByRoom = new HashMap<>();
        for (DungeonRoomState state : states) {
            if (!state.instanceId().equals(instanceId)) {
                throw new IllegalArgumentException(
                        "Cannot restore room state for another instance: "
                                + state.instanceId()
                );
            }

            DungeonRoomState previous = statesByRoom.put(state.roomId(), state);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate restored dungeon room state: " + state.roomId()
                );
            }
        }

        if (!statesByRoom.isEmpty()) {
            this.roomStatesByInstance.put(instanceId, statesByRoom);
            this.dirty.run();
        }
    }

    private void putLoaded(DungeonRoomState state) {
        this.roomStatesByInstance
                .computeIfAbsent(state.instanceId(), ignored -> new HashMap<>())
                .put(state.roomId(), state);
    }
}
