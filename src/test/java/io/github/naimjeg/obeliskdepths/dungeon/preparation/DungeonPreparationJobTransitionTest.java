package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.tribute.ResolvedTribute;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class DungeonPreparationJobTransitionTest {
    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION,
                    Identifier.fromNamespaceAndPath("minecraft", "overworld"));

    private DungeonPreparationJobTransitionTest() {}

    static {
        DungeonAsyncTestSupport.bootstrapMinecraft();
    }

   public static void main(String[] args) {
       validCompleteTransitionPath();
       invalidTransitionsRejected();
       terminalImmutability();
       advanceToCannotEnterTerminalStages();
       failureHasTypedFailureCause();
       cancellationHasTypedCancellationCause();
       readyHasNoTerminalCause();
       failReleasesLeasesExactlyOnce();
       cancelReleasesLeasesExactlyOnce();
       releaseAllAttemptsEveryLeaseAndPreservesSuppressedFailures();
       releaseAllIsHarmlessAfterFailure();
       terminalRejectedLeaseIsClosedExactlyOnce();
       terminalRejectedLeaseCloseFailurePreservesRejection();
       committingToCancelledRejected();
       committingToFailedAccepted();
       readyToCommitToFailedAccepted();
       retryFromWaitingForEntryChunksAccepted();
       retryFromValidatingEntryAccepted();
       readingStructureStartToPlanningEntryChunksIsLegal();
       requestingEntryChunksToWaitingForEntryChunksIsLegal();
       waitingForEntryChunksToValidatingEntryChunksIsLegal();
       waitingForEntryChunksToRequestingEntryChunksIsIllegal();
       noProjectionStageTransition();
   }

    private static void validCompleteTransitionPath() {
        DungeonPreparationJob job = job();
        advanceToCommitting(job);
        job.complete(10L);
        check(job.stage() == DungeonPreparationStage.READY, "complete: READY");
        check(job.terminalCause() == null, "complete: no terminal cause");
    }

    private static void readyToCommitToFailedAccepted() {
        DungeonPreparationJob job = job();
        advanceToReadyToCommit(job);
        job.fail(
                DungeonPreparationJobFailureReason.INVALID_TRIBUTE,
                "preflight failed",
                10L
        );
        check(job.stage() == DungeonPreparationStage.FAILED,
                "preflight failure: READY_TO_COMMIT -> FAILED");
    }

    private static void invalidTransitionsRejected() {
        DungeonPreparationJob job = job();
        try {
            job.advanceTo(DungeonPreparationStage.COMMITTING, 1L);
            check(false, "invalid: should throw");
        } catch (IllegalStateException e) {
            check(e.getMessage().contains(job.id().toString()), "invalid: job id");
            check(e.getMessage().contains("QUEUED"), "invalid: current");
            check(e.getMessage().contains("COMMITTING"), "invalid: requested");
        }
    }

    private static void terminalImmutability() {
        DungeonPreparationJob job = job();
        job.advanceTo(DungeonPreparationStage.VALIDATING, 1L);
        job.fail(DungeonPreparationJobFailureReason.INTERNAL_ERROR, "test", 2L);
        try {
            job.advanceTo(DungeonPreparationStage.SCANNING_EXISTING_SITES, 3L);
            check(false, "terminal: should throw");
        } catch (IllegalStateException e) {
            check(e.getMessage().contains(job.id().toString()), "terminal: job id");
            check(e.getMessage().contains("FAILED"), "terminal: current");
            check(e.getMessage().contains("SCANNING_EXISTING_SITES"), "terminal: requested");
        }
    }

    private static void advanceToCannotEnterTerminalStages() {
        DungeonPreparationJob job = job();
        expectAdvanceRejected(job, DungeonPreparationStage.FAILED);
        expectAdvanceRejected(job, DungeonPreparationStage.CANCELLED);
        expectAdvanceRejected(job, DungeonPreparationStage.READY);
    }

    private static void failureHasTypedFailureCause() {
        DungeonPreparationJob job = job();
        job.advanceTo(DungeonPreparationStage.VALIDATING, 1L);
        job.fail(DungeonPreparationJobFailureReason.SITE_CONFLICT, "detail", 2L);
        check(job.stage() == DungeonPreparationStage.FAILED, "failure: stage");
        check(job.terminalCause() instanceof DungeonPreparationFailureCause,
                "failure: typed cause");
        DungeonPreparationFailureCause cause =
                (DungeonPreparationFailureCause) job.terminalCause();
        check(cause.reason() == DungeonPreparationJobFailureReason.SITE_CONFLICT,
                "failure: reason");
        check("detail".equals(cause.detail()), "failure: detail");
    }

    private static void cancellationHasTypedCancellationCause() {
        DungeonPreparationJob job = job();
        job.cancel(DungeonPreparationCancellationReason.USER_CANCELLED, "detail", 1L);
        check(job.stage() == DungeonPreparationStage.CANCELLED, "cancel: stage");
        check(job.terminalCause() instanceof DungeonPreparationCancellationCause,
                "cancel: typed cause");
        DungeonPreparationCancellationCause cause =
                (DungeonPreparationCancellationCause) job.terminalCause();
        check(cause.reason() == DungeonPreparationCancellationReason.USER_CANCELLED,
                "cancel: reason");
    }

    private static void readyHasNoTerminalCause() {
        DungeonPreparationJob job = job();
        advanceToCommitting(job);
        job.complete(10L);
        DungeonPreparationJobSnapshot snapshot = job.snapshot();
        check(snapshot.stage() == DungeonPreparationStage.READY, "ready: stage");
        check(snapshot.terminalCause() == null, "ready: no cause");
    }

    private static void failReleasesLeasesExactlyOnce() {
        DungeonPreparationJob job = job();
        job.advanceTo(DungeonPreparationStage.VALIDATING, 1L);
        CountingLease lease = new CountingLease();
        job.addCloseableLease(lease);
        job.fail(DungeonPreparationJobFailureReason.INTERNAL_ERROR, "test", 2L);
        check(lease.closed == 1, "fail: one release");
        check(job.leases().isEmpty(), "fail: leases cleared");
    }

    private static void cancelReleasesLeasesExactlyOnce() {
        DungeonPreparationJob job = job();
        CountingLease lease = new CountingLease();
        job.addCloseableLease(lease);
        job.cancel(DungeonPreparationCancellationReason.USER_CANCELLED, "test", 2L);
        check(lease.closed == 1, "cancel: one release");
        check(job.leases().isEmpty(), "cancel: leases cleared");
    }

    private static void releaseAllAttemptsEveryLeaseAndPreservesSuppressedFailures() {
        DungeonPreparationJob job = job();
        job.advanceTo(DungeonPreparationStage.VALIDATING, 1L);
        ThrowingLease first = new ThrowingLease("first");
        CountingLease second = new CountingLease();
        ThrowingLease third = new ThrowingLease("third");
        job.addCloseableLease(first);
        job.addCloseableLease(second);
        job.addCloseableLease(third);

        try {
            job.fail(DungeonPreparationJobFailureReason.INTERNAL_ERROR, "test", 2L);
            check(false, "release aggregate: should throw");
        } catch (IllegalStateException exception) {
            check(exception.getMessage().contains(job.id().toString()),
                    "release aggregate: job id");
            check(exception.getSuppressed().length == 2,
                    "release aggregate: suppressed failures");
        }
        check(first.closed == 1, "release aggregate: first attempted");
        check(second.closed == 1, "release aggregate: second attempted");
        check(third.closed == 1, "release aggregate: third attempted");
        check(job.leases().isEmpty(), "release aggregate: leases cleared");
        check(job.stage() == DungeonPreparationStage.FAILED,
                "release aggregate: terminal stage retained");
    }

    private static void releaseAllIsHarmlessAfterFailure() {
        DungeonPreparationJob job = job();
        job.advanceTo(DungeonPreparationStage.VALIDATING, 1L);
        ThrowingLease lease = new ThrowingLease("first");
        job.addCloseableLease(lease);
        try {
            job.fail(DungeonPreparationJobFailureReason.INTERNAL_ERROR, "test", 2L);
        } catch (IllegalStateException expected) {
            // expected
        }
        job.releaseAllLeases();
        check(lease.closed == 1, "repeat release: no second close");
    }

    private static void terminalRejectedLeaseIsClosedExactlyOnce() {
        DungeonPreparationJob job = job();
        job.cancel(DungeonPreparationCancellationReason.USER_CANCELLED, "test", 1L);
        CountingLease lease = new CountingLease();
        try {
            job.addCloseableLease(lease);
            check(false, "terminal lease: should throw");
        } catch (IllegalStateException exception) {
            check(exception.getMessage().contains("Cannot add lease"),
                    "terminal lease: rejection");
        }
        check(lease.closed == 1, "terminal lease: closed once");
        check(job.leases().isEmpty(), "terminal lease: not retained");
    }

    private static void terminalRejectedLeaseCloseFailurePreservesRejection() {
        DungeonPreparationJob job = job();
        job.cancel(DungeonPreparationCancellationReason.USER_CANCELLED, "test", 1L);
        ThrowingLease lease = new ThrowingLease("rejected");
        try {
            job.addCloseableLease(lease);
            check(false, "terminal lease close failure: should throw");
        } catch (IllegalStateException exception) {
            check(exception.getMessage().contains("Rejected lease could not be closed"),
                    "terminal lease close failure: close message");
            check(exception.getSuppressed().length == 1,
                    "terminal lease close failure: rejection suppressed");
        }
        check(lease.closed == 1, "terminal lease close failure: closed once");
        check(job.leases().isEmpty(), "terminal lease close failure: not retained");
    }

    private static void committingToCancelledRejected() {
        DungeonPreparationJob job = job();
        advanceToCommitting(job);
        try {
            job.cancel(DungeonPreparationCancellationReason.USER_CANCELLED, "test", 11L);
            check(false, "committing cancel: should throw");
        } catch (IllegalStateException e) {
            check(e.getMessage().contains("COMMITTING"), "committing cancel: current");
            check(e.getMessage().contains("CANCELLED"), "committing cancel: requested");
        }
    }

    private static void committingToFailedAccepted() {
        DungeonPreparationJob job = job();
        advanceToCommitting(job);
        job.fail(DungeonPreparationJobFailureReason.INTERNAL_ERROR, "test", 11L);
        check(job.stage() == DungeonPreparationStage.FAILED, "committing fail: FAILED");
    }

    private static void retryFromWaitingForEntryChunksAccepted() {
        DungeonPreparationJob job = job();
        job.advanceTo(DungeonPreparationStage.VALIDATING, 1L);
        job.advanceTo(DungeonPreparationStage.SCANNING_EXISTING_SITES, 2L);
        job.advanceTo(DungeonPreparationStage.SELECTING_CANDIDATE, 3L);
        job.advanceTo(DungeonPreparationStage.REQUESTING_START_CHUNK, 4L);
        job.advanceTo(DungeonPreparationStage.WAITING_FOR_START_CHUNK, 5L);
        job.advanceTo(DungeonPreparationStage.READING_STRUCTURE_START, 6L);
        job.advanceTo(DungeonPreparationStage.PLANNING_ENTRY_CHUNKS, 7L);
        job.advanceTo(DungeonPreparationStage.REQUESTING_ENTRY_CHUNKS, 8L);
        job.advanceTo(DungeonPreparationStage.WAITING_FOR_ENTRY_CHUNKS, 9L);
        job.advanceTo(DungeonPreparationStage.SELECTING_CANDIDATE, 10L);
        check(job.stage() == DungeonPreparationStage.SELECTING_CANDIDATE,
                "entry retry: waiting-entry -> selecting");
    }

    private static void retryFromValidatingEntryAccepted() {
        DungeonPreparationJob job = job();
        job.advanceTo(DungeonPreparationStage.VALIDATING, 1L);
        job.advanceTo(DungeonPreparationStage.SCANNING_EXISTING_SITES, 2L);
        job.advanceTo(DungeonPreparationStage.SELECTING_CANDIDATE, 3L);
        job.advanceTo(DungeonPreparationStage.REQUESTING_START_CHUNK, 4L);
        job.advanceTo(DungeonPreparationStage.WAITING_FOR_START_CHUNK, 5L);
        job.advanceTo(DungeonPreparationStage.READING_STRUCTURE_START, 6L);
        job.advanceTo(DungeonPreparationStage.PLANNING_ENTRY_CHUNKS, 7L);
        job.advanceTo(DungeonPreparationStage.REQUESTING_ENTRY_CHUNKS, 8L);
        job.advanceTo(DungeonPreparationStage.WAITING_FOR_ENTRY_CHUNKS, 9L);
        job.advanceTo(DungeonPreparationStage.VALIDATING_ENTRY_CHUNKS, 10L);
        job.advanceTo(DungeonPreparationStage.VALIDATING_ENTRY, 11L);
        job.advanceTo(DungeonPreparationStage.SELECTING_CANDIDATE, 12L);
        check(job.stage() == DungeonPreparationStage.SELECTING_CANDIDATE,
                "entry retry: validating-entry -> selecting");
    }

    private static void expectAdvanceRejected(
            DungeonPreparationJob job,
            DungeonPreparationStage stage
    ) {
        try {
            job.advanceTo(stage, 1L);
            check(false, "advance terminal: should throw " + stage);
        } catch (IllegalStateException e) {
            check(e.getMessage().contains(stage.name()), "advance terminal: mentions stage");
        }
    }

   private static void advanceToCommitting(DungeonPreparationJob job) {
        advanceToReadyToCommit(job);
        job.advanceTo(DungeonPreparationStage.COMMITTING, 13L);
    }

   private static void advanceToReadyToCommit(DungeonPreparationJob job) {
        job.advanceTo(DungeonPreparationStage.VALIDATING, 1L);
        job.advanceTo(DungeonPreparationStage.SCANNING_EXISTING_SITES, 2L);
        job.advanceTo(DungeonPreparationStage.SELECTING_CANDIDATE, 3L);
        job.advanceTo(DungeonPreparationStage.REQUESTING_START_CHUNK, 4L);
        job.advanceTo(DungeonPreparationStage.WAITING_FOR_START_CHUNK, 5L);
        job.advanceTo(DungeonPreparationStage.READING_STRUCTURE_START, 6L);
        job.advanceTo(DungeonPreparationStage.PLANNING_ENTRY_CHUNKS, 7L);
        job.advanceTo(DungeonPreparationStage.REQUESTING_ENTRY_CHUNKS, 8L);
        job.advanceTo(DungeonPreparationStage.WAITING_FOR_ENTRY_CHUNKS, 9L);
        job.advanceTo(DungeonPreparationStage.VALIDATING_ENTRY_CHUNKS, 10L);
        job.advanceTo(DungeonPreparationStage.VALIDATING_ENTRY, 11L);
        job.advanceTo(DungeonPreparationStage.READY_TO_COMMIT, 12L);
    }

    private static DungeonPreparationJob job() {
        return new DungeonPreparationJob(
                DungeonPreparationJobId.create(),
                DungeonPreparationRequest.forTests(
                        UUID.randomUUID(),
                        OVERWORLD,
                        new BlockPos(0, 64, 0),
                        validTribute(),
                        1
                ),
                0L
        );
    }

    private static ResolvedTribute validTribute() {
        return new ResolvedTribute(true, 1, 1, 0.0F, 1.0F, 1);
    }

    private static final class CountingLease implements AutoCloseable {
        int closed;

        @Override
        public void close() {
            this.closed++;
        }
    }

    private static final class ThrowingLease implements AutoCloseable {
        final String name;
        int closed;

        ThrowingLease(String name) {
            this.name = name;
        }

        @Override
        public void close() {
            this.closed++;
            throw new IllegalStateException(this.name);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
   private static void readingStructureStartToPlanningEntryChunksIsLegal() {
       DungeonPreparationJob job = job();
       job.advanceTo(DungeonPreparationStage.VALIDATING, 1L);
       job.advanceTo(DungeonPreparationStage.SCANNING_EXISTING_SITES, 2L);
       job.advanceTo(DungeonPreparationStage.SELECTING_CANDIDATE, 3L);
       job.advanceTo(DungeonPreparationStage.REQUESTING_START_CHUNK, 4L);
       job.advanceTo(DungeonPreparationStage.WAITING_FOR_START_CHUNK, 5L);
       job.advanceTo(DungeonPreparationStage.READING_STRUCTURE_START, 6L);
       job.advanceTo(DungeonPreparationStage.PLANNING_ENTRY_CHUNKS, 7L);
       check(job.stage() == DungeonPreparationStage.PLANNING_ENTRY_CHUNKS,
               "transition: READING_STRUCTURE_START -> PLANNING_ENTRY_CHUNKS legal");
   }

   private static void requestingEntryChunksToWaitingForEntryChunksIsLegal() {
       DungeonPreparationJob job = job();
       job.advanceTo(DungeonPreparationStage.VALIDATING, 1L);
       job.advanceTo(DungeonPreparationStage.SCANNING_EXISTING_SITES, 2L);
       job.advanceTo(DungeonPreparationStage.SELECTING_CANDIDATE, 3L);
       job.advanceTo(DungeonPreparationStage.REQUESTING_START_CHUNK, 4L);
       job.advanceTo(DungeonPreparationStage.WAITING_FOR_START_CHUNK, 5L);
       job.advanceTo(DungeonPreparationStage.READING_STRUCTURE_START, 6L);
       job.advanceTo(DungeonPreparationStage.PLANNING_ENTRY_CHUNKS, 7L);
       job.advanceTo(DungeonPreparationStage.REQUESTING_ENTRY_CHUNKS, 8L);
       job.advanceTo(DungeonPreparationStage.WAITING_FOR_ENTRY_CHUNKS, 9L);
       check(job.stage() == DungeonPreparationStage.WAITING_FOR_ENTRY_CHUNKS,
               "transition: REQUESTING_ENTRY_CHUNKS -> WAITING_FOR_ENTRY_CHUNKS legal");
   }

   private static void waitingForEntryChunksToValidatingEntryChunksIsLegal() {
       DungeonPreparationJob job = job();
       job.advanceTo(DungeonPreparationStage.VALIDATING, 1L);
       job.advanceTo(DungeonPreparationStage.SCANNING_EXISTING_SITES, 2L);
       job.advanceTo(DungeonPreparationStage.SELECTING_CANDIDATE, 3L);
       job.advanceTo(DungeonPreparationStage.REQUESTING_START_CHUNK, 4L);
       job.advanceTo(DungeonPreparationStage.WAITING_FOR_START_CHUNK, 5L);
       job.advanceTo(DungeonPreparationStage.READING_STRUCTURE_START, 6L);
       job.advanceTo(DungeonPreparationStage.PLANNING_ENTRY_CHUNKS, 7L);
       job.advanceTo(DungeonPreparationStage.REQUESTING_ENTRY_CHUNKS, 8L);
       job.advanceTo(DungeonPreparationStage.WAITING_FOR_ENTRY_CHUNKS, 9L);
       job.advanceTo(DungeonPreparationStage.VALIDATING_ENTRY_CHUNKS, 10L);
       check(job.stage() == DungeonPreparationStage.VALIDATING_ENTRY_CHUNKS,
               "transition: WAITING_FOR_ENTRY_CHUNKS -> VALIDATING_ENTRY_CHUNKS legal");
   }

   private static void waitingForEntryChunksToRequestingEntryChunksIsIllegal() {
       DungeonPreparationJob job = job();
       job.advanceTo(DungeonPreparationStage.VALIDATING, 1L);
       job.advanceTo(DungeonPreparationStage.SCANNING_EXISTING_SITES, 2L);
       job.advanceTo(DungeonPreparationStage.SELECTING_CANDIDATE, 3L);
       job.advanceTo(DungeonPreparationStage.REQUESTING_START_CHUNK, 4L);
       job.advanceTo(DungeonPreparationStage.WAITING_FOR_START_CHUNK, 5L);
       job.advanceTo(DungeonPreparationStage.READING_STRUCTURE_START, 6L);
       job.advanceTo(DungeonPreparationStage.PLANNING_ENTRY_CHUNKS, 7L);
       job.advanceTo(DungeonPreparationStage.REQUESTING_ENTRY_CHUNKS, 8L);
       job.advanceTo(DungeonPreparationStage.WAITING_FOR_ENTRY_CHUNKS, 9L);
       try {
           job.advanceTo(DungeonPreparationStage.REQUESTING_ENTRY_CHUNKS, 10L);
           check(false, "transition: WAITING_FOR_ENTRY_CHUNKS -> REQUESTING_ENTRY_CHUNKS should throw");
       } catch (IllegalStateException e) {
           check(e.getMessage().contains("WAITING_FOR_ENTRY_CHUNKS"),
                   "transition: illegal backward mentions current stage");
           check(e.getMessage().contains("REQUESTING_ENTRY_CHUNKS"),
                   "transition: illegal backward mentions requested stage");
       }
   }

   private static void noProjectionStageTransition() {
       boolean hasProjecting = false;
       for (DungeonPreparationStage stage : DungeonPreparationStage.values()) {
           if (stage.name().equals("PROJECTING_STRUCTURE_SITE")) {
               hasProjecting = true;
               break;
           }
       }
       check(!hasProjecting, "transition: PROJECTING_STRUCTURE_SITE not present");
   }
}
