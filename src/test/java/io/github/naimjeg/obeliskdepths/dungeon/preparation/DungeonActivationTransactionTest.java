package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.id.PortalSessionId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonGeneratedRoom;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteProjectionSource;
import io.github.naimjeg.obeliskdepths.dungeon.site.ResolvedDungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.territory.DungeonBounds;
import io.github.naimjeg.obeliskdepths.dungeon.tribute.ResolvedTribute;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class DungeonActivationTransactionTest {
    private DungeonActivationTransactionTest() {
    }

    static {
        DungeonAsyncTestSupport.bootstrapMinecraft();
    }

    public static void main(String[] args) {
        failureInjectionRollsBackEveryMutationBoundary();
        backendFailuresUseTheSameRollbackPath();
        eachCompensationFailureIsSuppressed();
        compensationFailuresAreSuppressedAndDoNotStopRollback();
        preexistingSessionAndPortalAreNotDeleted();
        successDisarmsRollbackAndConsumesTributeLast();
        transactionErrorRollsBackAndIsRethrown();
        rollbackErrorEscapesTransactionAfterAllCompensations();
        attemptTelemetryFailureCannotPreventTransaction();
        successTelemetryFailuresCannotUndoCommittedTransaction();
        transactionRejectsOffOwnerExecution();
    }

    private static void eachCompensationFailureIsSuppressed() {
        for (String name : java.util.List.of(
                "claim",
                "prepared",
                "leases",
                "portal",
                "dungeon session",
                "portal session",
                "reservation"
        )) {
            FakeBackend backend = new FakeBackend();
            backend.failingCompensations.add(name);
            DungeonActivationCommitResult result = transaction(
                    backend,
                    new DungeonActivationTransactionMetrics(),
                    point -> {
                        if (point == DungeonActivationFailureInjector.FailurePoint.BEFORE_TRIBUTE_CONSUMPTION) {
                            throw new IllegalStateException("force compensation");
                        }
                    }
            ).execute();
            check(!result.success(), name + ": transaction failed");
            check(result.detail().contains("rollback failures=1"),
                    name + ": failure retained as suppressed");
            check(backend.compensationAttempts == 7,
                    name + ": later compensations still executed");
        }
    }

    private static void backendFailuresUseTheSameRollbackPath() {
        FakeBackend revalidation = new FakeBackend();
        revalidation.revalidationFailure = true;
        assertFailedAndClean(
                revalidation, "final revalidation",
                DungeonActivationCommitFailureReason.INVALID_TRIBUTE
        );

        FakeBackend reservation = new FakeBackend();
        reservation.reservationSucceeds = false;
        assertFailedAndClean(
                reservation, "reservation refusal",
                DungeonActivationCommitFailureReason.SITE_CONFLICT
        );

        FakeBackend portal = new FakeBackend();
        portal.portalEntitySucceeds = false;
        assertFailedAndClean(
                portal, "portal creation failure",
                DungeonActivationCommitFailureReason.PORTAL_SPAWN_FAILED
        );

        FakeBackend startLease = new FakeBackend();
        startLease.startLeaseReleaseFails = true;
        assertFailedAndClean(
                startLease, "start lease release failure",
                DungeonActivationCommitFailureReason.INTERNAL_ERROR
        );

        FakeBackend claim = new FakeBackend();
        claim.claimReleaseSucceeds = false;
        assertFailedAndClean(
                claim, "claim release failure",
                DungeonActivationCommitFailureReason.SITE_CLAIM_LOST
        );

        FakeBackend tribute = new FakeBackend();
        tribute.tributeConsumptionFails = true;
        assertFailedAndClean(
                tribute, "tribute validation failure",
                DungeonActivationCommitFailureReason.INVALID_TRIBUTE
        );
    }

    private static void assertFailedAndClean(
            FakeBackend backend,
            String boundary,
            DungeonActivationCommitFailureReason expectedReason
    ) {
        DungeonActivationCommitResult result = transaction(
                backend,
                new DungeonActivationTransactionMetrics(),
                DungeonActivationFailureInjector.NONE
        ).execute();
        check(!result.success(), boundary + ": transaction fails");
        check(result.failureReason().orElseThrow() == expectedReason,
                boundary + ": typed failure reason");
        check(!backend.reserved, boundary + ": reservation absent");
        check(!backend.portalSession, boundary + ": portal session absent");
        check(!backend.newDungeonSession, boundary + ": dungeon session absent");
        check(!backend.createdPortalEntity, boundary + ": portal absent");
        check(!backend.detachedLeases, boundary + ": lease ownership closed");
        check(!backend.preparedEntry, boundary + ": prepared entry absent");
        check(!backend.claimReleased, boundary + ": claim retained or restored");
        check(!backend.tributeConsumed, boundary + ": tribute unchanged");
    }

    private static void failureInjectionRollsBackEveryMutationBoundary() {
        for (DungeonActivationFailureInjector.FailurePoint point
                : DungeonActivationFailureInjector.FailurePoint.values()) {
            FakeBackend backend = new FakeBackend();
            DungeonActivationTransactionMetrics metrics =
                    new DungeonActivationTransactionMetrics();
            DungeonActivationCommitResult result = transaction(
                    backend,
                    metrics,
                    candidate -> {
                        if (candidate == point) {
                            throw new IllegalStateException("injected " + point);
                        }
                    }
            ).execute();

            check(!result.success(), "injected failure must fail: " + point);
            check(!backend.reserved, "reservation rolled back: " + point);
            check(!backend.portalSession, "portal session rolled back: " + point);
            check(!backend.newDungeonSession, "new dungeon session rolled back: " + point);
            check(!backend.createdPortalEntity, "created portal rolled back: " + point);
            check(!backend.preparedEntry, "prepared entry rolled back: " + point);
            check(!backend.detachedLeases, "detached leases closed: " + point);
            check(!backend.claimReleased, "claim restored: " + point);
            check(!backend.tributeConsumed, "tribute unchanged: " + point);
            check(backend.events.equals(expectedEvents(point)),
                    "exact forward mutation order: " + point + " " + backend.events);
            check(backend.rollbacks.equals(expectedRollbacks(point)),
                    "exact reverse compensation order: " + point + " " + backend.rollbacks);
            if (point.ordinal()
                    >= DungeonActivationFailureInjector.FailurePoint.AFTER_CLAIM_RELEASE.ordinal()) {
                check(backend.activeClaim == backend.originalClaim,
                        "exact original claim restored: " + point);
            }
            if (point.ordinal()
                    >= DungeonActivationFailureInjector.FailurePoint.AFTER_LEASE_DETACHMENT.ordinal()) {
                check(backend.leaseCloseCalls == 1,
                        "entry lease bundle closed exactly once: " + point);
            }
            check(backend.rollbackOrderIsReverse(),
                    "compensations remain reverse ordered: " + point);
            DungeonActivationTransactionMetrics.Snapshot snapshot = metrics.snapshot();
            check(snapshot.attempts() == 1, "attempt metric: " + point);
            check(snapshot.rollbackAttempts()
                            == (point == DungeonActivationFailureInjector.FailurePoint.BEFORE_RESERVATION ? 0 : 1),
                    "rollback attempt metric: " + point);
        }
    }

    private static java.util.List<String> expectedEvents(
            DungeonActivationFailureInjector.FailurePoint point
    ) {
        java.util.List<String> all = java.util.List.of(
                "reserve",
                "portal session",
                "dungeon session",
                "portal entity",
                "detach leases",
                "prepared entry",
                "release start lease",
                "release claim"
        );
        int count = switch (point) {
            case BEFORE_RESERVATION -> 0;
            case AFTER_RESERVATION -> 1;
            case AFTER_PORTAL_SESSION -> 2;
            case AFTER_DUNGEON_SESSION -> 3;
            case AFTER_PORTAL_ENTITY -> 4;
            case AFTER_LEASE_DETACHMENT -> 5;
            case AFTER_PREPARED_ENTRY -> 6;
            case AFTER_START_LEASE_RELEASE, BEFORE_CLAIM_RELEASE -> 7;
            case AFTER_CLAIM_RELEASE, BEFORE_TRIBUTE_CONSUMPTION -> 8;
        };
        return all.subList(0, count);
    }

    private static java.util.List<String> expectedRollbacks(
            DungeonActivationFailureInjector.FailurePoint point
    ) {
        ArrayList<String> expected = new ArrayList<>();
        if (point == DungeonActivationFailureInjector.FailurePoint.AFTER_CLAIM_RELEASE
                || point == DungeonActivationFailureInjector.FailurePoint.BEFORE_TRIBUTE_CONSUMPTION) {
            expected.add("claim");
        }
        if (point.ordinal()
                >= DungeonActivationFailureInjector.FailurePoint.AFTER_PREPARED_ENTRY.ordinal()) {
            expected.add("prepared");
        }
        if (point.ordinal()
                >= DungeonActivationFailureInjector.FailurePoint.AFTER_LEASE_DETACHMENT.ordinal()) {
            expected.add("leases");
        }
        if (point.ordinal()
                >= DungeonActivationFailureInjector.FailurePoint.AFTER_PORTAL_ENTITY.ordinal()) {
            expected.add("portal");
        }
        if (point.ordinal()
                >= DungeonActivationFailureInjector.FailurePoint.AFTER_DUNGEON_SESSION.ordinal()) {
            expected.add("dungeon session");
        }
        if (point.ordinal()
                >= DungeonActivationFailureInjector.FailurePoint.AFTER_PORTAL_SESSION.ordinal()) {
            expected.add("portal session");
        }
        if (point.ordinal()
                >= DungeonActivationFailureInjector.FailurePoint.AFTER_RESERVATION.ordinal()) {
            expected.add("reservation");
        }
        return expected;
    }

    private static void compensationFailuresAreSuppressedAndDoNotStopRollback() {
        FakeBackend backend = new FakeBackend();
        backend.failingCompensations.addAll(Set.of(
                "claim", "prepared", "portal", "reservation"
        ));
        DungeonActivationTransactionMetrics metrics =
                new DungeonActivationTransactionMetrics();
        DungeonActivationCommitResult result = transaction(
                backend,
                metrics,
                point -> {
                    if (point == DungeonActivationFailureInjector.FailurePoint.BEFORE_TRIBUTE_CONSUMPTION) {
                        throw new IllegalStateException("force rollback");
                    }
                }
        ).execute();

        check(!result.success(), "compensation failure transaction fails");
        check(result.detail().contains("rollback failures=4"),
                "all compensation failures remain suppressed");
        check(backend.compensationAttempts >= 7,
                "rollback continues through every inverse");
        DungeonActivationTransactionMetrics.Snapshot snapshot = metrics.snapshot();
        check(snapshot.rollbackFailures() == 4,
                "metrics retain every rollback failure");
        check(snapshot.rollbackStepsExecuted() >= 7,
                "metrics retain every rollback step");
    }

    private static void preexistingSessionAndPortalAreNotDeleted() {
        FakeBackend backend = new FakeBackend();
        backend.sessionCreatedByTransaction = false;
        backend.portalCreatedByTransaction = false;
        DungeonActivationCommitResult result = transaction(
                backend,
                new DungeonActivationTransactionMetrics(),
                point -> {
                    if (point == DungeonActivationFailureInjector.FailurePoint.AFTER_PORTAL_ENTITY) {
                        throw new IllegalStateException("rollback reused resources");
                    }
                }
        ).execute();
        check(!result.success(), "reused resource failure");
        check(backend.sessionRemovalCalls == 0,
                "pre-existing dungeon session not deleted");
        check(backend.portalRemovalCalls == 0,
                "pre-existing portal entity not deleted");
    }

    private static void successDisarmsRollbackAndConsumesTributeLast() {
        FakeBackend backend = new FakeBackend();
        DungeonActivationTransactionMetrics metrics =
                new DungeonActivationTransactionMetrics();
        DungeonActivationCommitResult result = transaction(
                backend, metrics, DungeonActivationFailureInjector.NONE
        ).execute();
        check(result.success(), "transaction success");
        check(result.instanceId().orElseThrow() == backend.reservedInstanceId,
                "success returns created instance identifier");
        check(result.portalSessionId().orElseThrow() == backend.createdPortalSessionId,
                "success returns created portal-session identifier");
        check(backend.tributeConsumed, "tribute consumed");
        check(backend.activationReadyAtTribute,
                "portal, sessions, and prepared entry exist before tribute");
        check(backend.preparedEntry && !backend.detachedLeases
                        && !backend.jobOwnsEntryLeases,
                "prepared registry owns transferred entry leases on success");
        check(backend.startLeaseReleased,
                "obsolete start lease released before tribute");
        check(backend.activeClaim == null,
                "transient claim released on success");
        check(backend.tributeConsumeCalls == 1,
                "success consumes tribute exactly once");
        check(backend.events.get(backend.events.size() - 1).equals("consume tribute"),
                "tribute is the final mutation");
        check(backend.compensationAttempts == 0,
                "success disarms compensation");
        check(metrics.snapshot().attempts() == 1
                        && metrics.snapshot().successes() == 1,
                "success metrics");
    }

    private static void transactionRejectsOffOwnerExecution() {
        FakeBackend backend = new FakeBackend();
        backend.owner = false;
        try {
            transaction(
                    backend,
                    new DungeonActivationTransactionMetrics(),
                    DungeonActivationFailureInjector.NONE
            ).execute();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError("off-owner transaction must be rejected");
    }

    private static void transactionErrorRollsBackAndIsRethrown() {
        FakeBackend backend = new FakeBackend();
        DungeonActivationTransactionMetrics metrics =
                new DungeonActivationTransactionMetrics();
        AssertionError fatal = new AssertionError("fatal transaction error");
        try {
            transaction(
                    backend,
                    metrics,
                    point -> {
                        if (point == DungeonActivationFailureInjector.FailurePoint.AFTER_CLAIM_RELEASE) {
                            throw fatal;
                        }
                    }
            ).execute();
            throw new AssertionError("transaction Error must be rethrown");
        } catch (AssertionError thrown) {
            check(thrown == fatal, "original transaction Error identity retained");
        }
        check(!backend.reserved && !backend.portalSession
                        && !backend.newDungeonSession && !backend.createdPortalEntity
                        && !backend.preparedEntry && !backend.detachedLeases,
                "Error path reverses every business mutation");
        check(backend.activeClaim == backend.originalClaim,
                "Error path restores exact claim");
        check(backend.leaseCloseCalls == 1,
                "Error path closes lease bundle exactly once");
        check(!backend.tributeConsumed, "Error path does not consume tribute");
        check(metrics.snapshot().rollbackAttempts() == 1
                        && metrics.snapshot().rollbackStepsExecuted() == 7,
                "Error path records completed rollback metrics");
    }

    private static void rollbackErrorEscapesTransactionAfterAllCompensations() {
        FakeBackend backend = new FakeBackend();
        AssertionError rollbackError = new AssertionError("fatal compensation");
        backend.errorCompensationName = "prepared";
        backend.compensationError = rollbackError;
        try {
            transaction(
                    backend,
                    new DungeonActivationTransactionMetrics(),
                    point -> {
                        if (point == DungeonActivationFailureInjector.FailurePoint.BEFORE_TRIBUTE_CONSUMPTION) {
                            throw new DungeonActivationTransactionFailure(
                                    DungeonActivationCommitFailureReason.INVALID_TRIBUTE,
                                    "original transaction failure"
                            );
                        }
                    }
            ).execute();
            throw new AssertionError("rollback Error must escape transaction");
        } catch (AssertionError thrown) {
            check(thrown == rollbackError, "rollback Error identity retained");
        }
        check(backend.compensationAttempts == 7,
                "all compensation steps attempted after rollback Error");
        check(!backend.reserved && !backend.portalSession
                        && !backend.newDungeonSession && !backend.createdPortalEntity,
                "later cleanup continues after rollback Error");
        check(java.util.Arrays.stream(rollbackError.getSuppressed())
                        .anyMatch(failure -> failure instanceof
                                DungeonActivationTransactionFailure),
                "original transaction failure attached to rollback Error");
    }

    private static DungeonActivationTransaction transaction(
            FakeBackend backend,
            DungeonActivationTransactionMetrics metrics,
            DungeonActivationFailureInjector injector
    ) {
        return new DungeonActivationTransaction(plan(), backend, metrics, injector);
    }

    private static void attemptTelemetryFailureCannotPreventTransaction() {
        FakeBackend backend = new FakeBackend();
        FakeTelemetry telemetry = new FakeTelemetry(backend);
        telemetry.failAttempt = true;

        DungeonActivationCommitResult result = new DungeonActivationTransaction(
                plan(), backend, telemetry, DungeonActivationFailureInjector.NONE
        ).execute();

        check(result.success(), "attempt telemetry failure cannot reject transaction");
        check(backend.tributeConsumeCalls == 1,
                "attempt telemetry failure still performs transaction exactly once");
        check(backend.compensationAttempts == 0,
                "attempt telemetry failure does not trigger rollback");
    }

    private static void successTelemetryFailuresCannotUndoCommittedTransaction() {
        FakeBackend backend = new FakeBackend();
        FakeTelemetry telemetry = new FakeTelemetry(backend);
        telemetry.failConsumeProfile = true;
        telemetry.failSuccess = true;

        DungeonActivationCommitResult result = new DungeonActivationTransaction(
                plan(), backend, telemetry, DungeonActivationFailureInjector.NONE
        ).execute();

        check(result.success(), "success telemetry failures preserve business success");
        check(telemetry.consumeProfileCalls == 1 && telemetry.successCalls == 1,
                "both post-seal telemetry hooks were attempted");
        check(telemetry.observedConsumedTribute,
                "success telemetry runs only after tribute mutation");
        check(backend.tributeConsumeCalls == 1,
                "success telemetry failure cannot repeat tribute consumption");
        check(backend.compensationAttempts == 0,
                "success telemetry failure cannot run compensation after sealing");
    }

    private static DungeonActivationCommitPlan plan() {
        DungeonSiteKey key = new DungeonSiteKey(0, 0);
        DungeonRoomId roomId = DungeonRoomId.of("start");
        DungeonBounds bounds = new DungeonBounds(0, 0, 0, 4, 4, 4);
        DungeonSite site = new DungeonSite(
                key,
                bounds,
                roomId,
                new BlockPos(1, 1, 1),
                java.util.List.of(new DungeonGeneratedRoom(
                        roomId, DungeonRoomType.START, bounds,
                        new BlockPos(1, 1, 1)
                ))
        );
        ChunkPos chunk = new ChunkPos(0, 0);
        DungeonEntryChunkPlan entryPlan = new DungeonEntryChunkPlan(
                chunk, chunk, chunk, chunk, java.util.List.of(chunk)
        );
        DungeonPreparationRequest request = DungeonPreparationRequest.forTests(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                ResourceKey.create(
                        Registries.DIMENSION,
                        Identifier.fromNamespaceAndPath("minecraft", "overworld")
                ),
                new BlockPos(0, 64, 0),
                new ResolvedTribute(true, 1, 1, 0.0F, 1.0F, 1),
                7
        );
        DungeonPreparationJobId jobId = DungeonPreparationJobId.create();
        return new DungeonActivationCommitPlan(
                jobId,
                request.playerId(),
                request.sourceDimension(),
                request.obeliskPos(),
                request.sourceContainerId(),
                request.tributeFingerprint(),
                request.expectedTribute(),
                new ResolvedDungeonSite(
                        site,
                        DungeonSiteProjectionSource.GENERATED_STRUCTURE_START
                ),
                new PreparedDungeonDestination(new Vec3(1.5D, 1.0D, 1.5D)),
                entryPlan,
                key,
                new DungeonSiteClaimIdentity(key, jobId, 1L),
                entryPlan.chunks()
        );
    }

    private static final class FakeBackend
            implements DungeonActivationTransactionBackend {
        private final ArrayList<String> events = new ArrayList<>();
        private final ArrayList<String> rollbacks = new ArrayList<>();
        private final Set<String> failingCompensations = new HashSet<>();
        private boolean owner = true;
        private boolean revalidationFailure;
        private boolean reservationSucceeds = true;
        private boolean portalEntitySucceeds = true;
        private boolean claimReleaseSucceeds = true;
        private boolean tributeConsumptionFails;
        private boolean startLeaseReleaseFails;
        private boolean sessionCreatedByTransaction = true;
        private boolean portalCreatedByTransaction = true;
        private boolean reserved;
        private DungeonInstanceId reservedInstanceId;
        private boolean portalSession;
        private PortalSessionId createdPortalSessionId;
        private boolean newDungeonSession;
        private boolean dungeonSessionEnsured;
        private boolean createdPortalEntity;
        private boolean portalEntityEnsured;
        private boolean detachedLeases;
        private boolean jobOwnsEntryLeases = true;
        private boolean preparedEntry;
        private boolean startLeaseReleased;
        private boolean claimReleased;
        private boolean tributeConsumed;
        private boolean activationReadyAtTribute;
        private int tributeConsumeCalls;
        private final Object originalClaim = new Object();
        private Object activeClaim = this.originalClaim;
        private int leaseCloseCalls;
        private String errorCompensationName;
        private Error compensationError;
        private int sessionRemovalCalls;
        private int portalRemovalCalls;
        private int compensationAttempts;

        @Override
        public void assertOwnerThread() {
            if (!this.owner) {
                throw new IllegalStateException("off owner");
            }
        }

        @Override
        public Optional<DungeonActivationTransactionFailure> revalidate(
                DungeonActivationCommitPlan plan
        ) {
            if (this.revalidationFailure) {
                return Optional.of(new DungeonActivationTransactionFailure(
                        DungeonActivationCommitFailureReason.INVALID_TRIBUTE,
                        "synthetic revalidation failure"
                ));
            }
            return Optional.empty();
        }

        @Override
        public Optional<DungeonInstanceId> reserveSite(DungeonActivationCommitPlan plan) {
            this.events.add("reserve");
            if (!this.reservationSucceeds) {
                return Optional.empty();
            }
            this.reserved = true;
            this.reservedInstanceId = DungeonInstanceId.create();
            return Optional.of(this.reservedInstanceId);
        }

        @Override
        public void releaseReservedSite(DungeonInstanceId instanceId) {
            compensate("reservation");
            this.reserved = false;
        }

        @Override
        public PortalSessionId createPortalSession(
                DungeonActivationCommitPlan plan,
                DungeonInstanceId instanceId
        ) {
            this.events.add("portal session");
            this.portalSession = true;
            this.createdPortalSessionId = PortalSessionId.create();
            return this.createdPortalSessionId;
        }

        @Override
        public void removeCreatedPortalSession(PortalSessionId portalSessionId) {
            compensate("portal session");
            this.portalSession = false;
        }

        @Override
        public DungeonSessionResult acquireDungeonSession(
                DungeonActivationCommitPlan plan,
                DungeonInstanceId instanceId,
                PortalSessionId portalSessionId
        ) {
            this.events.add("dungeon session");
            this.dungeonSessionEnsured = true;
            this.newDungeonSession = this.sessionCreatedByTransaction;
            return new DungeonSessionResult(
                    UUID.randomUUID(), this.sessionCreatedByTransaction
            );
        }

        @Override
        public void removeCreatedDungeonSession(UUID sessionId) {
            this.sessionRemovalCalls++;
            compensate("dungeon session");
            this.newDungeonSession = false;
        }

        @Override
        public PortalEntityResult ensurePortalEntity(PortalSessionId portalSessionId) {
            this.events.add("portal entity");
            if (!this.portalEntitySucceeds) {
                return new PortalEntityResult(false, false);
            }
            this.portalEntityEnsured = true;
            this.createdPortalEntity = this.portalCreatedByTransaction;
            return new PortalEntityResult(true, this.portalCreatedByTransaction);
        }

        @Override
        public void removeCreatedPortalEntity() {
            this.portalRemovalCalls++;
            compensate("portal");
            this.createdPortalEntity = false;
        }

        @Override
        public void detachEntryLeases(DungeonActivationCommitPlan plan) {
            this.events.add("detach leases");
            this.detachedLeases = true;
            this.jobOwnsEntryLeases = false;
        }

        @Override
        public void closeDetachedEntryLeases() {
            compensate("leases");
            if (this.detachedLeases) {
                this.leaseCloseCalls++;
            }
            this.detachedLeases = false;
        }

        @Override
        public void registerPreparedEntry(
                DungeonActivationCommitPlan plan,
                DungeonInstanceId instanceId,
                PortalSessionId portalSessionId
        ) {
            this.events.add("prepared entry");
            this.preparedEntry = true;
            this.detachedLeases = false;
        }

        @Override
        public void removeRegisteredPreparedEntry(PortalSessionId portalSessionId) {
            compensate("prepared");
            if (this.preparedEntry) {
                this.leaseCloseCalls++;
            }
            this.preparedEntry = false;
            this.detachedLeases = false;
        }

        @Override
        public void releaseStartLeaseAfterPreparedEntry() {
            this.events.add("release start lease");
            if (this.startLeaseReleaseFails) {
                throw new IllegalStateException("synthetic start lease release failure");
            }
            this.startLeaseReleased = true;
        }

        @Override
        public boolean releaseSiteClaim(DungeonActivationCommitPlan plan) {
            this.events.add("release claim");
            if (!this.claimReleaseSucceeds) {
                return false;
            }
            this.claimReleased = true;
            this.activeClaim = null;
            return true;
        }

        @Override
        public void restoreSiteClaim(DungeonActivationCommitPlan plan) {
            compensate("claim");
            this.claimReleased = false;
            this.activeClaim = this.originalClaim;
        }

        @Override
        public void consumeTribute(DungeonActivationCommitPlan plan) {
            this.events.add("consume tribute");
            this.tributeConsumeCalls++;
            this.activationReadyAtTribute = this.reserved
                    && this.portalSession
                    && this.dungeonSessionEnsured
                    && this.portalEntityEnsured
                    && this.preparedEntry
                    && !this.detachedLeases
                    && !this.jobOwnsEntryLeases
                    && this.startLeaseReleased
                    && this.activeClaim == null;
            if (this.tributeConsumptionFails) {
                throw new DungeonActivationTransactionFailure(
                        DungeonActivationCommitFailureReason.INVALID_TRIBUTE,
                        "synthetic tribute validation failure"
                );
            }
            this.tributeConsumed = true;
        }

        private void compensate(String name) {
            this.compensationAttempts++;
            this.rollbacks.add(name);
            if (name.equals(this.errorCompensationName)) {
                throw this.compensationError;
            }
            if (this.failingCompensations.contains(name)) {
                throw new IllegalStateException("failed compensation " + name);
            }
        }

        private boolean rollbackOrderIsReverse() {
            int previous = Integer.MAX_VALUE;
            for (String rollback : this.rollbacks) {
                int current = switch (rollback) {
                    case "claim" -> 7;
                    case "prepared" -> 6;
                    case "leases" -> 5;
                    case "portal" -> 4;
                    case "dungeon session" -> 3;
                    case "portal session" -> 2;
                    case "reservation" -> 1;
                    default -> 0;
                };
                if (current > previous) {
                    return false;
                }
                previous = current;
            }
            return true;
        }
    }

    private static final class FakeTelemetry
            implements DungeonActivationTransactionTelemetry {
        private final FakeBackend backend;
        private boolean failAttempt;
        private boolean failSuccess;
        private boolean failConsumeProfile;
        private boolean observedConsumedTribute;
        private int consumeProfileCalls;
        private int successCalls;

        private FakeTelemetry(FakeBackend backend) {
            this.backend = backend;
        }

        @Override
        public long profilerStart() {
            return 0L;
        }

        @Override
        public void profilerRecord(
                DungeonPreparationProfiler.Operation operation,
                long startNanos
        ) {
            if (operation == DungeonPreparationProfiler.Operation.CONSUME_TRIBUTE) {
                this.consumeProfileCalls++;
                this.observedConsumedTribute = this.backend.tributeConsumed;
                if (this.failConsumeProfile) {
                    throw new IllegalStateException("synthetic success profiler failure");
                }
            }
        }

        @Override
        public void recordAttempt() {
            if (this.failAttempt) {
                throw new IllegalStateException("synthetic attempt metric failure");
            }
        }

        @Override
        public void recordSuccess() {
            this.successCalls++;
            if (this.failSuccess) {
                throw new IllegalStateException("synthetic success metric failure");
            }
        }

        @Override
        public void recordRollback(
                DungeonActivationCompensationStack.RollbackReport report
        ) {
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
