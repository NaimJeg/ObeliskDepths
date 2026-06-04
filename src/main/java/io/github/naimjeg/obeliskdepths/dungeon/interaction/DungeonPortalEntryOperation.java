package io.github.naimjeg.obeliskdepths.dungeon.interaction;

import io.github.naimjeg.obeliskdepths.dungeon.id.PortalSessionId;

import java.util.Objects;
import java.util.UUID;

/** Mutable only through the owning {@link DungeonPortalEntryOperationRuntime}. */
final class DungeonPortalEntryOperation {
    private final DungeonPortalEntryOperationId id;
    private final UUID playerId;
    private final PortalSessionId portalSessionId;
    private final UUID sourcePortalEntityId;
    private final long createdAtGameTime;
    private DungeonPortalEntryOperationState state;
    private long stateChangedAtGameTime;
    private boolean ownsRecovery;

    DungeonPortalEntryOperation(
            DungeonPortalEntryOperationId id,
            UUID playerId,
            PortalSessionId portalSessionId,
            UUID sourcePortalEntityId,
            long createdAtGameTime
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.portalSessionId = Objects.requireNonNull(
                portalSessionId, "portalSessionId"
        );
        this.sourcePortalEntityId = Objects.requireNonNull(
                sourcePortalEntityId, "sourcePortalEntityId"
        );
        this.createdAtGameTime = createdAtGameTime;
        this.state = DungeonPortalEntryOperationState.AWAITING_CLIENT_READY;
        this.stateChangedAtGameTime = createdAtGameTime;
    }

    DungeonPortalEntryOperationId id() {
        return this.id;
    }

    UUID playerId() {
        return this.playerId;
    }

    PortalSessionId portalSessionId() {
        return this.portalSessionId;
    }

    UUID sourcePortalEntityId() {
        return this.sourcePortalEntityId;
    }

    long createdAtGameTime() {
        return this.createdAtGameTime;
    }

    DungeonPortalEntryOperationState state() {
        return this.state;
    }

    long stateChangedAtGameTime() {
        return this.stateChangedAtGameTime;
    }

    boolean ownsRecovery() {
        return this.ownsRecovery;
    }

    void markRecoveryOwned() {
        this.ownsRecovery = true;
    }

    void transitionTo(
            DungeonPortalEntryOperationState next,
            long gameTime
    ) {
        Objects.requireNonNull(next, "next");
        if (!canTransition(this.state, next)) {
            throw new IllegalStateException(
                    "Invalid dungeon portal entry operation transition "
                            + this.id + ": " + this.state + " -> " + next
            );
        }
        this.state = next;
        this.stateChangedAtGameTime = gameTime;
    }

    private static boolean canTransition(
            DungeonPortalEntryOperationState current,
            DungeonPortalEntryOperationState next
    ) {
        if (current.terminal() || current == next) {
            return false;
        }
        return switch (current) {
            case AWAITING_CLIENT_READY -> next
                    == DungeonPortalEntryOperationState.PREPARING
                    || next == DungeonPortalEntryOperationState.FAILED
                    || next == DungeonPortalEntryOperationState.CANCELLED;
            case PREPARING -> next
                    == DungeonPortalEntryOperationState.READY_TO_TELEPORT
                    || next == DungeonPortalEntryOperationState.FAILED
                    || next == DungeonPortalEntryOperationState.CANCELLED;
            case READY_TO_TELEPORT -> next
                    == DungeonPortalEntryOperationState.TELEPORTING
                    || next == DungeonPortalEntryOperationState.FAILED
                    || next == DungeonPortalEntryOperationState.CANCELLED;
            case TELEPORTING -> next
                    == DungeonPortalEntryOperationState.FINALIZING
                    || next == DungeonPortalEntryOperationState.FAILED;
            case FINALIZING -> next
                    == DungeonPortalEntryOperationState.COMPLETED
                    || next == DungeonPortalEntryOperationState.CANCELLED;
            case COMPLETED, FAILED, CANCELLED -> false;
        };
    }
}
