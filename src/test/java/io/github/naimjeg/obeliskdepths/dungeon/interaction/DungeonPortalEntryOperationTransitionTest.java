package io.github.naimjeg.obeliskdepths.dungeon.interaction;

import io.github.naimjeg.obeliskdepths.dungeon.id.PortalSessionId;

import java.util.UUID;

public final class DungeonPortalEntryOperationTransitionTest {
    private DungeonPortalEntryOperationTransitionTest() {
    }

    public static void main(String[] args) {
        completeTransitionPathIsExplicit();
        preparationCannotStartBeforeClientReadyTransition();
        terminalStatesAreImmutable();
        wireCodesRoundTrip();
    }

    private static void completeTransitionPathIsExplicit() {
        DungeonPortalEntryOperation operation = operation();
        operation.transitionTo(DungeonPortalEntryOperationState.PREPARING, 2L);
        operation.transitionTo(
                DungeonPortalEntryOperationState.READY_TO_TELEPORT,
                3L
        );
        operation.transitionTo(DungeonPortalEntryOperationState.TELEPORTING, 4L);
        operation.transitionTo(DungeonPortalEntryOperationState.FINALIZING, 5L);
        operation.transitionTo(DungeonPortalEntryOperationState.COMPLETED, 6L);
        check(operation.state() == DungeonPortalEntryOperationState.COMPLETED,
                "complete path must reach COMPLETED");
        check(operation.stateChangedAtGameTime() == 6L,
                "transition time must be authoritative");
    }

    private static void preparationCannotStartBeforeClientReadyTransition() {
        DungeonPortalEntryOperation operation = operation();
        expectRejected(
                operation,
                DungeonPortalEntryOperationState.READY_TO_TELEPORT
        );
        expectRejected(operation, DungeonPortalEntryOperationState.TELEPORTING);
        expectRejected(operation, DungeonPortalEntryOperationState.FINALIZING);
        check(operation.state()
                        == DungeonPortalEntryOperationState.AWAITING_CLIENT_READY,
                "rejected transitions must not mutate state");
    }

    private static void terminalStatesAreImmutable() {
        DungeonPortalEntryOperation operation = operation();
        operation.transitionTo(DungeonPortalEntryOperationState.FAILED, 2L);
        expectRejected(operation, DungeonPortalEntryOperationState.PREPARING);
        expectRejected(operation, DungeonPortalEntryOperationState.CANCELLED);
    }

    private static void wireCodesRoundTrip() {
        for (DungeonPortalEntryOperationState state
                : DungeonPortalEntryOperationState.values()) {
            check(DungeonPortalEntryOperationState
                            .fromWireCode(state.wireCode())
                            .orElseThrow() == state,
                    "state wire code must round-trip: " + state);
        }
        for (DungeonPortalEntryResult result : DungeonPortalEntryResult.values()) {
            check(DungeonPortalEntryResult.fromWireCode(result.wireCode())
                            .orElseThrow() == result,
                    "result wire code must round-trip: " + result);
        }
    }

    private static DungeonPortalEntryOperation operation() {
        return new DungeonPortalEntryOperation(
                DungeonPortalEntryOperationId.create(),
                UUID.randomUUID(),
                PortalSessionId.create(),
                UUID.randomUUID(),
                1L
        );
    }

    private static void expectRejected(
            DungeonPortalEntryOperation operation,
            DungeonPortalEntryOperationState state
    ) {
        try {
            operation.transitionTo(state, 10L);
            throw new AssertionError("transition should be rejected: " + state);
        } catch (IllegalStateException expected) {
            check(expected.getMessage().contains(operation.id().toString()),
                    "rejection must identify the operation");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
