package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.preparation.chunk.DungeonChunkLease;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class DungeonPreparationJob {
    private final DungeonPreparationJobId id;
    private final DungeonPreparationRequest request;
    private final long createdAtGameTime;
    private long lastTransitionGameTime;
    private DungeonPreparationStage stage;
    private DungeonPreparationTerminalCause terminalCause;
    private final List<AutoCloseable> leases = new ArrayList<>();

    DungeonPreparationJob(
            DungeonPreparationJobId id,
            DungeonPreparationRequest request,
            long createdAtGameTime
    ) {
        this.id = id;
        this.request = request;
        this.createdAtGameTime = createdAtGameTime;
        this.lastTransitionGameTime = createdAtGameTime;
        this.stage = DungeonPreparationStage.QUEUED;
    }

    public DungeonPreparationJobId id() {
        return this.id;
    }

    public DungeonPreparationRequest request() {
        return this.request;
    }

    public long createdAtGameTime() {
        return this.createdAtGameTime;
    }

    public long lastTransitionGameTime() {
        return this.lastTransitionGameTime;
    }

    public DungeonPreparationStage stage() {
        return this.stage;
    }

    public DungeonPreparationJobFailureReason failureReason() {
        if (this.terminalCause instanceof DungeonPreparationFailureCause cause) {
            return cause.reason();
        }
        return null;
    }

    public String failureDetail() {
        if (this.terminalCause instanceof DungeonPreparationFailureCause cause) {
            return cause.detail();
        }
        return null;
    }

    public DungeonPreparationCancellationReason cancellationReason() {
        if (this.terminalCause instanceof DungeonPreparationCancellationCause cause) {
            return cause.reason();
        }
        return null;
    }

    public String cancellationDetail() {
        if (this.terminalCause instanceof DungeonPreparationCancellationCause cause) {
            return cause.detail();
        }
        return null;
    }

    public DungeonPreparationTerminalCause terminalCause() {
        return this.terminalCause;
    }

    void advanceTo(DungeonPreparationStage next, long gameTime) {
        if (next == DungeonPreparationStage.FAILED
                || next == DungeonPreparationStage.CANCELLED
                || next == DungeonPreparationStage.READY) {
            throw invalidTerminalAdvance(next);
        }
        transitionInternal(next, null, gameTime);
    }

    void fail(DungeonPreparationJobFailureReason reason, String detail, long gameTime) {
        transitionInternal(
                DungeonPreparationStage.FAILED,
                new DungeonPreparationFailureCause(reason, detail),
                gameTime
        );
    }

    void cancel(
            DungeonPreparationCancellationReason reason,
            String detail,
            long gameTime
    ) {
        transitionInternal(
                DungeonPreparationStage.CANCELLED,
                new DungeonPreparationCancellationCause(reason, detail),
                gameTime
        );
    }

    void complete(long gameTime) {
        transitionInternal(DungeonPreparationStage.READY, null, gameTime);
    }

    public void addLease(DungeonChunkLease lease) {
        addCloseableLease(lease);
    }

    void addCloseableLease(AutoCloseable lease) {
        Objects.requireNonNull(lease, "lease");
        if (this.stage.isTerminal()) {
            IllegalStateException rejection = new IllegalStateException(
                    "Cannot add lease to terminal job: " + this.id
            );
            try {
                lease.close();
            } catch (Exception exception) {
                IllegalStateException closeFailure = new IllegalStateException(
                        "Rejected lease could not be closed for terminal job "
                                + this.id,
                        exception
                );
                closeFailure.addSuppressed(rejection);
                throw closeFailure;
            }
            throw rejection;
        }
        this.leases.add(lease);
    }

    void releaseAllLeases() {
        RuntimeException aggregateFailure = null;
        List<AutoCloseable> ownedLeases = List.copyOf(this.leases);
        this.leases.clear();

        for (AutoCloseable lease : ownedLeases) {
            try {
                lease.close();
            } catch (Throwable throwable) {
                if (throwable instanceof ThreadDeath
                        || throwable instanceof VirtualMachineError
                        || throwable instanceof LinkageError) {
                    throw (Error) throwable;
                }

                if (aggregateFailure == null) {
                    aggregateFailure = new IllegalStateException(
                            "Failed to release one or more preparation leases for job "
                                    + this.id
                    );
                }
                aggregateFailure.addSuppressed(throwable);
            }
        }

        if (aggregateFailure != null) {
            throw aggregateFailure;
        }
    }

    public List<AutoCloseable> leases() {
        return Collections.unmodifiableList(this.leases);
    }

    public DungeonPreparationJobSnapshot snapshot() {
        return new DungeonPreparationJobSnapshot(
                this.id,
                this.request,
                this.createdAtGameTime,
                this.lastTransitionGameTime,
                this.stage,
                this.terminalCause
        );
    }

    boolean isTerminal() {
        return this.stage.isTerminal();
    }

    private void transitionInternal(
            DungeonPreparationStage next,
            DungeonPreparationTerminalCause terminalCause,
            long gameTime
    ) {
        Objects.requireNonNull(next, "next");
        validateTerminalCause(next, terminalCause);
        validateTransition(next);
        this.stage = next;
        this.lastTransitionGameTime = gameTime;
        if (next == DungeonPreparationStage.FAILED
                || next == DungeonPreparationStage.CANCELLED) {
            this.terminalCause = terminalCause;
            releaseAllLeases();
        }
    }

    private void validateTransition(DungeonPreparationStage next) {
        if (this.stage.isTerminal()) {
            throw new IllegalStateException(
                    "Job " + this.id + " is terminal at stage " + this.stage
                            + "; cannot transition to " + next
            );
        }

        boolean valid = switch (this.stage) {
            case QUEUED -> next == DungeonPreparationStage.VALIDATING
                    || next == DungeonPreparationStage.CANCELLED;
            case VALIDATING -> next == DungeonPreparationStage.SCANNING_EXISTING_SITES
                    || next == DungeonPreparationStage.FAILED
                    || next == DungeonPreparationStage.CANCELLED;
            case SCANNING_EXISTING_SITES -> next == DungeonPreparationStage.SELECTING_CANDIDATE
                    || next == DungeonPreparationStage.WAITING_FOR_START_CHUNK
                    || next == DungeonPreparationStage.READY_TO_COMMIT
                    || next == DungeonPreparationStage.FAILED
                    || next == DungeonPreparationStage.CANCELLED;
            case SELECTING_CANDIDATE -> next == DungeonPreparationStage.WAITING_FOR_START_CHUNK
                    || next == DungeonPreparationStage.FAILED
                    || next == DungeonPreparationStage.CANCELLED;
            case WAITING_FOR_START_CHUNK -> next == DungeonPreparationStage.READING_STRUCTURE_START
                    || next == DungeonPreparationStage.FAILED
                    || next == DungeonPreparationStage.CANCELLED;
            case READING_STRUCTURE_START -> next == DungeonPreparationStage.WAITING_FOR_ENTRY_CHUNKS
                    || next == DungeonPreparationStage.FAILED
                    || next == DungeonPreparationStage.CANCELLED;
            case WAITING_FOR_ENTRY_CHUNKS -> next == DungeonPreparationStage.VALIDATING_ENTRY
                    || next == DungeonPreparationStage.FAILED
                    || next == DungeonPreparationStage.CANCELLED;
            case VALIDATING_ENTRY -> next == DungeonPreparationStage.READY_TO_COMMIT
                    || next == DungeonPreparationStage.FAILED
                    || next == DungeonPreparationStage.CANCELLED;
            case READY_TO_COMMIT -> next == DungeonPreparationStage.COMMITTING
                    || next == DungeonPreparationStage.CANCELLED;
            case COMMITTING -> next == DungeonPreparationStage.READY
                    || next == DungeonPreparationStage.FAILED;
            default -> false;
        };

        if (!valid) {
            throw new IllegalStateException(
                    "Invalid transition for job " + this.id
                            + ": " + this.stage + " -> " + next
            );
        }
    }

    private void validateTerminalCause(
            DungeonPreparationStage next,
            DungeonPreparationTerminalCause terminalCause
    ) {
        if (next == DungeonPreparationStage.FAILED) {
            if (!(terminalCause instanceof DungeonPreparationFailureCause)) {
                throw new IllegalArgumentException("FAILED requires a failure cause.");
            }
            return;
        }
        if (next == DungeonPreparationStage.CANCELLED) {
            if (!(terminalCause instanceof DungeonPreparationCancellationCause)) {
                throw new IllegalArgumentException("CANCELLED requires a cancellation cause.");
            }
            return;
        }
        if (terminalCause != null) {
            throw new IllegalArgumentException(
                    "Stage " + next + " must not have a terminal cause."
            );
        }
    }

    private IllegalStateException invalidTerminalAdvance(DungeonPreparationStage next) {
        return new IllegalStateException(
                "Invalid transition for job " + this.id
                        + ": " + this.stage + " -> " + next
        );
    }
}
