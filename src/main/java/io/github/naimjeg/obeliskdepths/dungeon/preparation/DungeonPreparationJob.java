package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.preparation.chunk.DungeonChunkLease;

import java.util.*;

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
        if (!this.leases.isEmpty()) {
            throw new IllegalStateException(
                    "Cannot complete job " + this.id
                            + " while it still owns leases");
        }
        transitionInternal(DungeonPreparationStage.READY, null, gameTime);
    }

    /**
     * Transitions a COMMITTING job directly to READY.
     *
     * <p>This is the minimal publication step after an external
     * activation transaction has succeeded.  The job must be in
     * {@code COMMITTING} and must own no leases.  Ownership was
     * already transferred during the activation commit.</p>
     *
     * @param gameTime the current game time
     * @throws IllegalStateException if the job is not COMMITTING or
     *         still owns leases
     */
    void markCommittedReady(long gameTime) {
        if (this.stage != DungeonPreparationStage.COMMITTING) {
            throw new IllegalStateException(
                    "markCommittedReady requires COMMITTING, but job "
                            + this.id + " is " + this.stage
            );
        }
        if (!this.leases.isEmpty()) {
            throw new IllegalStateException(
                    "markCommittedReady: job " + this.id
                            + " still owns leases"
            );
        }
        transitionInternal(DungeonPreparationStage.READY, null, gameTime);
    }

    public DungeonPreparationLeaseBundle detachLeases() {
        if (this.stage.isTerminal()) {
            throw new IllegalStateException(
                    "Cannot detach leases from terminal job: " + this.id);
        }
        if (this.leases.isEmpty()) {
            return new DungeonPreparationLeaseBundle(Collections.emptyList());
        }
        List<AutoCloseable> detached = new ArrayList<>(this.leases);
        this.leases.clear();
        return new DungeonPreparationLeaseBundle(detached);
    }

    public DungeonPreparationLeaseBundle detachSelectedLeases(
            List<? extends AutoCloseable> selectedLeases
    ) {
        Objects.requireNonNull(selectedLeases, "selectedLeases");
        if (this.stage.isTerminal()) {
            throw new IllegalStateException(
                    "Cannot detach leases from terminal job: " + this.id);
        }
        if (selectedLeases.isEmpty()) {
            return new DungeonPreparationLeaseBundle(Collections.emptyList());
        }

        Set<AutoCloseable> seen =
                Collections.newSetFromMap(new IdentityHashMap<>());
        List<AutoCloseable> selected = new ArrayList<>(selectedLeases.size());
        for (AutoCloseable lease : selectedLeases) {
            Objects.requireNonNull(lease, "selected lease");
            if (!seen.add(lease)) {
                throw new IllegalArgumentException(
                        "Duplicate lease selected for transfer from job "
                                + this.id
                );
            }
            if (indexOfLease(lease) < 0) {
                throw new IllegalArgumentException(
                        "Job " + this.id
                                + " does not own a selected lease"
                );
            }
            selected.add(lease);
        }

        DungeonPreparationLeaseBundle bundle =
                new DungeonPreparationLeaseBundle(selected);
        for (AutoCloseable lease : selected) {
            int index = indexOfLease(lease);
            if (index < 0) {
                throw new IllegalStateException(
                        "Selected lease ownership changed during transfer for job "
                                + this.id
                );
            }
            this.leases.remove(index);
        }

        return bundle;
    }

    public void addLease(DungeonChunkLease lease) {
        Objects.requireNonNull(lease, "lease");
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

    void closeAndRemoveLease(AutoCloseable lease) {
        Objects.requireNonNull(lease, "lease");
        int index = indexOfLease(lease);
        if (index < 0) {
            throw new IllegalArgumentException(
                    "Job " + this.id + " does not own the supplied lease"
            );
        }

        AutoCloseable removed = this.leases.remove(index);
        try {
            removed.close();
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to close preparation lease for job " + this.id,
                    exception
            );
        }
    }

    void releaseAllLeases() {
        List<AutoCloseable> ownedLeases = List.copyOf(this.leases);
        this.leases.clear();
        try {
            new DungeonPreparationLeaseBundle(ownedLeases).close();
        } catch (RuntimeException failure) {
            IllegalStateException contextual = new IllegalStateException(
                    "Failed to close one or more preparation leases for job "
                            + this.id
            );
            Throwable[] individualFailures = failure.getSuppressed();
            if (individualFailures.length == 0) {
                contextual.addSuppressed(failure);
            } else {
                for (Throwable individualFailure : individualFailures) {
                    contextual.addSuppressed(individualFailure);
                }
            }
            throw contextual;
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
                    || next == DungeonPreparationStage.REQUESTING_START_CHUNK
                    || next == DungeonPreparationStage.READY_TO_COMMIT
                    || next == DungeonPreparationStage.FAILED
                    || next == DungeonPreparationStage.CANCELLED;
            case SELECTING_CANDIDATE -> next == DungeonPreparationStage.REQUESTING_START_CHUNK
                    || next == DungeonPreparationStage.FAILED
                    || next == DungeonPreparationStage.CANCELLED;
            case REQUESTING_START_CHUNK -> next == DungeonPreparationStage.WAITING_FOR_START_CHUNK
                    || next == DungeonPreparationStage.SELECTING_CANDIDATE
                    || next == DungeonPreparationStage.FAILED
                    || next == DungeonPreparationStage.CANCELLED;
            case WAITING_FOR_START_CHUNK -> next == DungeonPreparationStage.READING_STRUCTURE_START
                    || next == DungeonPreparationStage.SELECTING_CANDIDATE
                    || next == DungeonPreparationStage.FAILED
                    || next == DungeonPreparationStage.CANCELLED;
            case READING_STRUCTURE_START -> next == DungeonPreparationStage.PLANNING_ENTRY_CHUNKS
                    || next == DungeonPreparationStage.SELECTING_CANDIDATE
                    || next == DungeonPreparationStage.FAILED
                    || next == DungeonPreparationStage.CANCELLED;
            case PLANNING_ENTRY_CHUNKS -> next == DungeonPreparationStage.REQUESTING_ENTRY_CHUNKS
                    || next == DungeonPreparationStage.SELECTING_CANDIDATE
                    || next == DungeonPreparationStage.FAILED
                    || next == DungeonPreparationStage.CANCELLED;
            case REQUESTING_ENTRY_CHUNKS -> next == DungeonPreparationStage.WAITING_FOR_ENTRY_CHUNKS
                    || next == DungeonPreparationStage.SELECTING_CANDIDATE
                    || next == DungeonPreparationStage.FAILED
                    || next == DungeonPreparationStage.CANCELLED;
            case WAITING_FOR_ENTRY_CHUNKS -> next == DungeonPreparationStage.VALIDATING_ENTRY_CHUNKS
                    || next == DungeonPreparationStage.SELECTING_CANDIDATE
                    || next == DungeonPreparationStage.FAILED
                    || next == DungeonPreparationStage.CANCELLED;
            case VALIDATING_ENTRY_CHUNKS -> next == DungeonPreparationStage.VALIDATING_ENTRY
                    || next == DungeonPreparationStage.SELECTING_CANDIDATE
                    || next == DungeonPreparationStage.FAILED
                    || next == DungeonPreparationStage.CANCELLED;
            case VALIDATING_ENTRY -> next == DungeonPreparationStage.READY_TO_COMMIT
                    || next == DungeonPreparationStage.SELECTING_CANDIDATE
                    || next == DungeonPreparationStage.FAILED
                    || next == DungeonPreparationStage.CANCELLED;
            case READY_TO_COMMIT -> next == DungeonPreparationStage.COMMITTING
                    || next == DungeonPreparationStage.FAILED
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

    private int indexOfLease(AutoCloseable lease) {
        for (int i = 0; i < this.leases.size(); i++) {
            if (this.leases.get(i) == lease) {
                return i;
            }
        }
        return -1;
    }
}
