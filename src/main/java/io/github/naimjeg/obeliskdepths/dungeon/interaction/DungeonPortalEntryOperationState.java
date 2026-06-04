package io.github.naimjeg.obeliskdepths.dungeon.interaction;

import java.util.Optional;

/** Authoritative server state exposed to the presentation-only loading screen. */
public enum DungeonPortalEntryOperationState {
    AWAITING_CLIENT_READY(0),
    PREPARING(1),
    READY_TO_TELEPORT(2),
    TELEPORTING(3),
    FINALIZING(4),
    COMPLETED(5),
    FAILED(6),
    CANCELLED(7);

    private final int wireCode;

    DungeonPortalEntryOperationState(int wireCode) {
        this.wireCode = wireCode;
    }

    public int wireCode() {
        return this.wireCode;
    }

    public boolean terminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }

    public static Optional<DungeonPortalEntryOperationState> fromWireCode(int wireCode) {
        for (DungeonPortalEntryOperationState state : values()) {
            if (state.wireCode == wireCode) {
                return Optional.of(state);
            }
        }
        return Optional.empty();
    }
}
