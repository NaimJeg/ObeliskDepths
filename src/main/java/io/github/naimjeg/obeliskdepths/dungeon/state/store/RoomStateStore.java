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
        Map<DungeonRoomId, DungeonRoomState> statesByRoom =
                this.roomStatesByInstance.computeIfAbsent(
                        instance.id(),
                        ignored -> new HashMap<>()
                );

        boolean changed = false;

        for (DungeonGeneratedRoom room : site.rooms()) {
            if (statesByRoom.containsKey(room.id())) {
                continue;
            }

            statesByRoom.put(
                    room.id(),
                    DungeonRoomState.initial(
                            instance.id(),
                            room.type(),
                            room.id()
                    )
            );

            changed = true;
        }

        if (changed) {
            this.dirty.run();
        }
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
        boolean changed = this.roomStatesByInstance.remove(instanceId) != null;

        if (changed) {
            this.dirty.run();
        }

        return changed;
    }

    private void putLoaded(DungeonRoomState state) {
        this.roomStatesByInstance
                .computeIfAbsent(state.instanceId(), ignored -> new HashMap<>())
                .put(state.roomId(), state);
    }
}
