package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.id.PortalSessionId;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Atomic, synchronous, owner-thread activation transaction. */
final class DungeonActivationTransaction {
    private final DungeonActivationCommitPlan plan;
    private final DungeonActivationTransactionBackend backend;
    private final DungeonActivationTransactionTelemetry telemetry;
    private final DungeonActivationFailureInjector failureInjector;

    DungeonActivationTransaction(
            DungeonActivationCommitPlan plan,
            DungeonActivationTransactionBackend backend,
            DungeonActivationTransactionMetrics metrics,
            DungeonActivationFailureInjector failureInjector
    ) {
        this(
                plan,
                backend,
                DungeonActivationTransactionTelemetry.production(metrics),
                failureInjector
        );
    }

    DungeonActivationTransaction(
            DungeonActivationCommitPlan plan,
            DungeonActivationTransactionBackend backend,
            DungeonActivationTransactionTelemetry telemetry,
            DungeonActivationFailureInjector failureInjector
    ) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.backend = Objects.requireNonNull(backend, "backend");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
        this.failureInjector = Objects.requireNonNull(
                failureInjector,
                "failureInjector"
        );
    }

    DungeonActivationCommitResult execute() {
        this.backend.assertOwnerThread();
        recordAttemptSafely();
        DungeonActivationCompensationStack compensations =
                new DungeonActivationCompensationStack(() -> {
                    this.backend.assertOwnerThread();
                    return true;
                });
        try {
            Optional<DungeonActivationTransactionFailure> revalidation = profile(
                    DungeonPreparationProfiler.Operation.COMMIT_REVALIDATE,
                    () -> this.backend.revalidate(this.plan)
            );
            if (revalidation.isPresent()) {
                throw revalidation.get();
            }

            this.failureInjector.at(
                    DungeonActivationFailureInjector.FailurePoint.BEFORE_RESERVATION
            );
            DungeonInstanceId instanceId = profile(
                    DungeonPreparationProfiler.Operation.RESERVE_SITE,
                    () -> this.backend.reserveSite(this.plan)
            ).orElseThrow(() -> failure(
                    DungeonActivationCommitFailureReason.SITE_CONFLICT,
                    "site no longer reservable"
            ));
            compensations.register(
                    "release site reservation",
                    () -> this.backend.releaseReservedSite(instanceId)
            );
            this.failureInjector.at(
                    DungeonActivationFailureInjector.FailurePoint.AFTER_RESERVATION
            );

            PortalSessionId portalSessionId = profile(
                    DungeonPreparationProfiler.Operation.CREATE_PORTAL_SESSION,
                    () -> this.backend.createPortalSession(this.plan, instanceId)
            );
            compensations.register(
                    "remove portal session",
                    () -> this.backend.removeCreatedPortalSession(portalSessionId)
            );
            this.failureInjector.at(
                    DungeonActivationFailureInjector.FailurePoint.AFTER_PORTAL_SESSION
            );

            DungeonActivationTransactionBackend.DungeonSessionResult session = profile(
                    DungeonPreparationProfiler.Operation.ACQUIRE_DUNGEON_SESSION,
                    () -> this.backend.acquireDungeonSession(
                            this.plan, instanceId, portalSessionId
                    )
            );
            if (session.created()) {
                compensations.register(
                        "remove dungeon session",
                        () -> this.backend.removeCreatedDungeonSession(
                                session.sessionId()
                        )
                );
            }
            this.failureInjector.at(
                    DungeonActivationFailureInjector.FailurePoint.AFTER_DUNGEON_SESSION
            );

            DungeonActivationTransactionBackend.PortalEntityResult portalEntity =
                    profile(
                            DungeonPreparationProfiler.Operation.ENSURE_PORTAL_ENTITY,
                            () -> this.backend.ensurePortalEntity(portalSessionId)
                    );
            if (!portalEntity.success()) {
                throw failure(
                        DungeonActivationCommitFailureReason.PORTAL_SPAWN_FAILED,
                        "portal entity could not be ensured"
                );
            }
            if (portalEntity.created()) {
                compensations.register(
                        "remove portal entity",
                        this.backend::removeCreatedPortalEntity
                );
            }
            this.failureInjector.at(
                    DungeonActivationFailureInjector.FailurePoint.AFTER_PORTAL_ENTITY
            );

            this.backend.detachEntryLeases(this.plan);
            compensations.register(
                    "close detached entry leases",
                    this.backend::closeDetachedEntryLeases
            );
            this.failureInjector.at(
                    DungeonActivationFailureInjector.FailurePoint.AFTER_LEASE_DETACHMENT
            );

            profileVoid(
                    DungeonPreparationProfiler.Operation.REGISTER_PREPARED_ENTRY,
                    () -> this.backend.registerPreparedEntry(
                            this.plan, instanceId, portalSessionId
                    )
            );
            compensations.register(
                    "remove prepared entry",
                    () -> this.backend.removeRegisteredPreparedEntry(portalSessionId)
            );
            this.failureInjector.at(
                    DungeonActivationFailureInjector.FailurePoint.AFTER_PREPARED_ENTRY
            );
            this.backend.releaseStartLeaseAfterPreparedEntry();
            this.failureInjector.at(
                    DungeonActivationFailureInjector.FailurePoint.AFTER_START_LEASE_RELEASE
            );

            this.failureInjector.at(
                    DungeonActivationFailureInjector.FailurePoint.BEFORE_CLAIM_RELEASE
            );
            boolean released = profile(
                    DungeonPreparationProfiler.Operation.RELEASE_SITE_CLAIM,
                    () -> this.backend.releaseSiteClaim(this.plan)
            );
            if (!released) {
                throw failure(
                        DungeonActivationCommitFailureReason.SITE_CLAIM_LOST,
                        "site claim identity changed before commit"
                );
            }
            compensations.register(
                    "restore site claim",
                    () -> this.backend.restoreSiteClaim(this.plan)
            );
            this.failureInjector.at(
                    DungeonActivationFailureInjector.FailurePoint.AFTER_CLAIM_RELEASE
            );

            DungeonActivationCommitResult success =
                    DungeonActivationCommitResult.success(
                            instanceId,
                            portalSessionId
                    );

            /*
             * Validate the compensation stack before crossing the irreversible boundary.
             * Everything before tribute consumption remains compensatable.
             */
            compensations.prepareCommit();

            this.failureInjector.at(
                    DungeonActivationFailureInjector.FailurePoint
                            .BEFORE_TRIBUTE_CONSUMPTION
            );

            long tributeStart = profilerStartSafely();

            /*
             * Tribute consumption is the final irreversible business mutation.
             *
             * Do not use profileVoid() here: its profiler callback runs in a finally block
             * before control returns, which would leave a failure window between tribute
             * consumption and transaction sealing.
             */
            this.backend.consumeTribute(this.plan);

            /*
             * Seal immediately after successful tribute consumption. This method is
             * deliberately non-throwing and performs no external work.
             */
            compensations.completePreparedCommit();

            /*
             * Everything below is observational. A telemetry failure must not change the
             * already committed business result.
             */
            profilerRecordSafely(
                    DungeonPreparationProfiler.Operation.CONSUME_TRIBUTE,
                    tributeStart
            );
            recordSuccessSafely();

            return success;

        } catch (RuntimeException originalFailure) {
            DungeonActivationCompensationStack.RollbackReport rollback;
            try {
                rollback = rollbackPending(compensations, originalFailure);
            } catch (Error rollbackError) {
                logRollbackError(rollbackError);
                throw rollbackError;
            }
            boolean expected = originalFailure
                    instanceof DungeonActivationTransactionFailure;
            if (!expected || rollback.failures() > 0) {
                ObeliskDepths.LOGGER.error(
                        "Prepared dungeon activation transaction failed: job={}, site={}",
                        this.plan.jobId(),
                        this.plan.expectedSiteKey(),
                        originalFailure
                );
            }
            DungeonActivationCommitFailureReason reason =
                    originalFailure instanceof DungeonActivationTransactionFailure failure
                            ? failure.reason()
                            : DungeonActivationCommitFailureReason.INTERNAL_ERROR;
            return DungeonActivationCommitResult.failure(
                    reason,
                    failureDetail(originalFailure)
            );
        } catch (Error originalError) {
            try {
                rollbackPending(compensations, originalError);
            } catch (Error rollbackError) {
                logRollbackError(rollbackError);
                throw rollbackError;
            }
            throw originalError;
        }
    }

    private void logRollbackError(Error rollbackError) {
        ObeliskDepths.LOGGER.error(
                "Activation rollback raised an Error: job={}, site={}",
                this.plan.jobId(),
                this.plan.expectedSiteKey(),
                rollbackError
        );
    }

    private DungeonActivationCompensationStack.RollbackReport rollbackPending(
            DungeonActivationCompensationStack compensations,
            Throwable originalFailure
    ) {
        if (compensations.pendingStepCount() == 0) {
            return new DungeonActivationCompensationStack.RollbackReport(
                    0, 0, java.util.List.of()
            );
        }
        long rollbackStart = profilerStartSafely();
        DungeonActivationCompensationStack.RollbackReport report;
        try {
            report = compensations.rollback(originalFailure);
        } finally {
            profilerRecordSafely(
                    DungeonPreparationProfiler.Operation.COMMIT_ROLLBACK,
                    rollbackStart
            );
        }
        recordRollbackSafely(report);
        return report;
    }

    private <T> T profile(
            DungeonPreparationProfiler.Operation operation,
            Supplier<T> action
    ) {
        long start = profilerStartSafely();
        try {
            return action.get();
        } finally {
            profilerRecordSafely(operation, start);
        }
    }

    private long profilerStartSafely() {
        try {
            return this.telemetry.profilerStart();
        } catch (RuntimeException telemetryFailure) {
            return Long.MIN_VALUE;
        }
    }

    private void profilerRecordSafely(
            DungeonPreparationProfiler.Operation operation,
            long start
    ) {
        if (start == Long.MIN_VALUE) {
            return;
        }
        try {
            this.telemetry.profilerRecord(operation, start);
        } catch (RuntimeException telemetryFailure) {
            ObeliskDepths.LOGGER.warn(
                    "Activation profiler record failed for {}",
                    operation,
                    telemetryFailure
            );
        }
    }

    private void recordSuccessSafely() {
        try {
            this.telemetry.recordSuccess();
        } catch (RuntimeException telemetryFailure) {
            ObeliskDepths.LOGGER.warn(
                    "Activation success metric failed",
                    telemetryFailure
            );
        }
    }

    private void recordAttemptSafely() {
        try {
            this.telemetry.recordAttempt();
        } catch (RuntimeException telemetryFailure) {
            ObeliskDepths.LOGGER.warn(
                    "Activation attempt metric failed",
                    telemetryFailure
            );
        }
    }

    private void recordRollbackSafely(
            DungeonActivationCompensationStack.RollbackReport report
    ) {
        try {
            this.telemetry.recordRollback(report);
        } catch (RuntimeException telemetryFailure) {
            ObeliskDepths.LOGGER.warn(
                    "Activation rollback metric failed",
                    telemetryFailure
            );
        }
    }

    private void profileVoid(
            DungeonPreparationProfiler.Operation operation,
            Runnable action
    ) {
        profile(operation, () -> {
            action.run();
            return null;
        });
    }

    private static DungeonActivationTransactionFailure failure(
            DungeonActivationCommitFailureReason reason,
            String detail
    ) {
        return new DungeonActivationTransactionFailure(reason, detail);
    }

    private static String failureDetail(Throwable failure) {
        String detail = failure.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = failure.getClass().getSimpleName();
        }
        if (failure.getSuppressed().length > 0) {
            detail += "; rollback failures=" + failure.getSuppressed().length;
        }
        return detail;
    }
}
