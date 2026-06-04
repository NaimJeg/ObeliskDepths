package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.id.PortalSessionId;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.chunk.DungeonChunkLeaseState;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.chunk.DungeonChunkLoadOutcome;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonGeneratedRoom;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSafeSpawnScan;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSafeSpawnScanPurpose;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSafeSpawnScanResult;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSafeSpawnScanState;
import io.github.naimjeg.obeliskdepths.dungeon.site.reader.DungeonSiteCandidateCursor;
import io.github.naimjeg.obeliskdepths.dungeon.territory.DungeonBounds;
import io.github.naimjeg.obeliskdepths.dungeon.tribute.ResolvedTribute;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public final class DungeonPreparationJobExecutorTest {
    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION,
                    Identifier.fromNamespaceAndPath("minecraft", "overworld"));

    private DungeonPreparationJobExecutorTest() {
    }

    static {
        DungeonAsyncTestSupport.bootstrapMinecraft();
    }

   public static void main(String[] args) {
       queuedTickOnlyAdvancesToValidating();
       validatingStartsBoundedPersistedScanner();
        submissionBudgetOneSubmitsAtMostOnePerTick();
        inFlightLimitOneSubmitsOnlyAfterCompletion();
        scannerReportPublishesDuringBudgetedMailboxDrain();
       persistedSuccessPathRetainsStartLeaseAndResolvedSite();
       noPersistedSitesStopsAtOrderedGenerationCandidateSelection();
       failedProbeDoesNotAbortLaterPersistedCandidate();
       leaseFailureReleasesLeaseAndRetriesNextPersistedCandidateLater();
       leaseFailureWithoutFallbackFailsAsChunkLoadFailed();
       entryLeaseFailureRetriesNextCandidate();
       entryChunkUnloadBeforeValidationRetriesNextCandidate();
        safeSpawnFailureRetriesNextCandidate();
        validatingEntrySpansTicksUnderSafeSpawnBudget();
        wallClockExhaustionPausesPreparationWithoutFailure();
        cancellingContextDiscardsSafeSpawnScanAndClosesLeases();
        entryValidationCursorsResumeWithoutRescanning();
       invalidLoadedSiteReleasesLeaseAndRetriesNextPersistedCandidate();
       reservationRecheckFiltersPersistedAndGenerationCandidates();
       emptyCandidateScanFailsDeterministically();
       cancellingContextIgnoresLateScannerCallback();
       starterOnlyRequestReachesReadyToCommit();
       preflightFailureTerminatesWithoutCommit();
       commitSuccessReachesReady();
       postCommitNotificationFailureDoesNotFailReadyJob();
       commitFailureReachesFailed();
       claimLossFailureMappedToSiteClaimLost();
       committedPublicationFailureRecordsMetrics();
       publicationRetryDoesNotRepeatCommittedTransaction();
       committedResultRetainedOnPublicationFailure();
       activeScannerProgressSnapshotIsNumeric();
       entryProgressSnapshotCountsRequestedAndReady();
       generationAttemptsNeverExceedFour();
       wrongThreadTickIsRejectedBeforeMutation();
       waitingForEntryChunksWithMissingLeaseFailsInvariant();
       backendAcquisitionFailureCleansClaimAndFailsJob();
       nullStartLeaseResultFailsJob();       rejectedLeaseNotDoubleClosed();
   }

    private static void queuedTickOnlyAdvancesToValidating() {
        Harness harness = Harness.withCandidates(2);

        harness.tick();

        check(harness.job.stage() == DungeonPreparationStage.VALIDATING,
                "queued: advanced to VALIDATING");
        check(harness.backend.findCandidateCalls == 0,
                "queued: no candidate discovery");
        check(harness.backend.createdScanners.isEmpty(),
                "queued: no scanner");
    }

    private static void validatingStartsBoundedPersistedScanner() {
        Harness harness = Harness.withCandidates(3);
        harness.tick();

        harness.boundedTick();

        check(harness.job.stage()
                        == DungeonPreparationStage.SCANNING_EXISTING_SITES,
                "validating: scanner stage");
        check(harness.backend.findCandidateCalls == 1,
                "validating: one candidate discovery");
        check(harness.backend.lastRequestedLimit == 6,
                "validating: configured candidate limit passed through");
        check(harness.backend.createdScanners.size() == 1,
                "validating: one scanner");
        check(harness.backend.createdScannerConcurrency.get(0)
                        == DungeonPreparationLimits.MAX_IN_FLIGHT_PERSISTED_PROBES_PER_LEVEL,
                "validating: default scanner concurrency");
        check(harness.backend.probeBackend(0).probeCalls() == 0,
                "validating: scanner start performs no submissions");
        harness.boundedTick();
        DungeonAsyncTestSupport.ControlledProbeBackend probe =
                harness.backend.probeBackend(0);
        check(probe.probeCalls() == 3,
                "scanning: all candidates submitted below default bound");
        check(probe.probeCalls()
                        < DungeonPreparationLimits
                                .PERSISTED_PROBE_SUBMISSIONS_PER_LEVEL_TICK,
                "scanning: below default submission bound");
        check(probe.probeCalls()
                        < DungeonPreparationLimits
                                .MAX_IN_FLIGHT_PERSISTED_PROBES_PER_LEVEL,
                "scanning: below default in-flight bound");
    }

    private static void submissionBudgetOneSubmitsAtMostOnePerTick() {
        Harness harness = Harness.withCandidates(3);
        harness.tick();
        harness.executor.tick(
                harness.job,
                productionBudget(
                        1,
                        DungeonPreparationLimits
                                .MAX_IN_FLIGHT_PERSISTED_PROBES_PER_LEVEL
                )
        );

        check(harness.job.stage()
                        == DungeonPreparationStage.SCANNING_EXISTING_SITES,
                "submission budget: scanner starts");
        check(harness.backend.probeBackend(0).probeCalls() == 0,
                "submission budget: start performs no submissions");

        harness.executor.tick(
                harness.job,
                productionBudget(
                        1,
                        DungeonPreparationLimits
                                .MAX_IN_FLIGHT_PERSISTED_PROBES_PER_LEVEL
                )
        );
        check(harness.backend.probeBackend(0).probeCalls() == 1,
                "submission budget: one per scanning tick");

        harness.executor.tick(
                harness.job,
                productionBudget(
                        1,
                        DungeonPreparationLimits
                                .MAX_IN_FLIGHT_PERSISTED_PROBES_PER_LEVEL
                )
        );
        check(harness.backend.probeBackend(0).probeCalls() == 2,
                "submission budget: next tick receives next allowance");
    }

    private static void inFlightLimitOneSubmitsOnlyAfterCompletion() {
        Harness harness = Harness.withCandidates(3);
        harness.tick();
        harness.executor.tick(
                harness.job,
                productionBudget(
                        DungeonPreparationLimits
                                .PERSISTED_PROBE_SUBMISSIONS_PER_LEVEL_TICK,
                        1
                )
        );

        check(harness.job.stage()
                        == DungeonPreparationStage.SCANNING_EXISTING_SITES,
                "in-flight limit: scanner starts");

        harness.executor.tick(
                harness.job,
                productionBudget(
                        DungeonPreparationLimits
                                .PERSISTED_PROBE_SUBMISSIONS_PER_LEVEL_TICK,
                        1
                )
        );
        check(harness.backend.probeBackend(0).probeCalls() == 1,
                "in-flight limit: first probe only");
        check(harness.executor.activePersistedProbeCount() == 1,
                "in-flight limit: one outstanding probe");

        harness.executor.tick(
                harness.job,
                productionBudget(
                        DungeonPreparationLimits
                                .PERSISTED_PROBE_SUBMISSIONS_PER_LEVEL_TICK,
                        1
                )
        );
        check(harness.backend.probeBackend(0).probeCalls() == 1,
                "in-flight limit: no second probe while one is outstanding");
        check(harness.executor.activePersistedProbeCount() == 1,
                "in-flight limit: outstanding count unchanged");

        DungeonAsyncTestSupport.ControlledProbeBackend probe =
                harness.backend.probeBackend(0);
        probe.completeAvailable(0);
        check(harness.executor.activePersistedProbeCount() == 0,
                "in-flight limit: completion releases permit");

        harness.executor.tick(
                harness.job,
                productionBudget(
                        DungeonPreparationLimits
                                .PERSISTED_PROBE_SUBMISSIONS_PER_LEVEL_TICK,
                        1
                )
        );
        check(probe.probeCalls() == 2,
                "in-flight limit: later tick submits replacement");
        check(harness.executor.activePersistedProbeCount() == 1,
                "in-flight limit: replacement holds one permit");
    }

    private static void activeScannerProgressSnapshotIsNumeric() {
        Harness harness = Harness.withCandidates(3);
        harness.tick();
        harness.tick();

        DungeonPreparationProgressSnapshot snapshot =
                harness.executor.progressSnapshot(harness.job.id()).orElseThrow();
        check(snapshot.stage() == DungeonPreparationStage.SCANNING_EXISTING_SITES,
                "progress scanner: stage");
        check(snapshot.totalCandidateChunks() == 3,
                "progress scanner: total");
        check(snapshot.submittedCandidateChunks() == 0,
                "progress scanner: no start submissions");
        check(snapshot.completedCandidateChunks() == 0,
                "progress scanner: completed");
        check(snapshot.inFlightCandidateChunks() == 0,
                "progress scanner: no start in flight");

        harness.tick();
        snapshot = harness.executor.progressSnapshot(harness.job.id()).orElseThrow();
        check(snapshot.submittedCandidateChunks() == 3,
                "progress scanner: submitted after scanning tick");
        check(snapshot.inFlightCandidateChunks() == 3,
                "progress scanner: in flight after scanning tick");
    }

    private static void scannerReportPublishesDuringBudgetedMailboxDrain() {
        Harness harness = Harness.withCandidates(1);
        harness.advanceToScanning();
        DungeonAsyncTestSupport.ControlledProbeBackend probe =
                harness.backend.probeBackend(0);

        probe.completeAvailable(0);

        check(harness.job.stage()
                        == DungeonPreparationStage.SCANNING_EXISTING_SITES,
                "callback: stage unchanged before scanner drain");
        check(harness.executor.pendingScannerCompletionCount() == 1,
                "callback: scanner mailbox contains completion");
        check(probe.ownerExecutor.pendingTaskCount() == 0,
                "callback: scanner performs no owner dispatch");
        check(harness.backend.ownerExecutor.pendingTaskCount() == 0,
                "callback: runtime owner task not queued");
        harness.tick();
        check(harness.backend.ownerExecutor.pendingTaskCount() == 0,
                "callback: report callback performs no redundant dispatch");
        check(harness.job.stage()
                        == DungeonPreparationStage.REQUESTING_START_CHUNK,
                "callback: report classified during owner mailbox drain");
        harness.tick();
        check(harness.job.stage()
                        == DungeonPreparationStage.WAITING_FOR_START_CHUNK,
                "callback: start request processed on later tick");
    }

    private static void persistedSuccessPathRetainsStartLeaseAndResolvedSite() {
        Harness harness = Harness.withCandidates(1);
        DungeonSiteKey key = harness.backend.candidates.get(0);
        DungeonSite site = site(key);
        harness.backend.loadedSites.put(
                key,
                DungeonPreparationLoadedSiteResult.accepted(site)
        );
        harness.advanceToScanning();
        harness.completeReportAsAvailable();

        harness.tick();

        check(harness.job.stage()
                        == DungeonPreparationStage.REQUESTING_START_CHUNK,
                "success: requesting start chunk");
        harness.tick();
        check(harness.job.stage()
                        == DungeonPreparationStage.WAITING_FOR_START_CHUNK,
                "success: waiting for start chunk after request");
        check(harness.job.leases().size() == 1,
                "success: job owns start lease immediately");
        FakeStartLease lease = harness.backend.leases.get(0);
        harness.tick();
        check(harness.job.stage()
                        == DungeonPreparationStage.WAITING_FOR_START_CHUNK,
                "success: pending lease waits");

        lease.state = DungeonChunkLeaseState.READY;
        harness.tick();
        check(harness.job.stage()
                        == DungeonPreparationStage.READING_STRUCTURE_START,
                "success: ready lease advances to read stage");
        check(harness.backend.readLoadedCalls.isEmpty(),
                "success: loaded read deferred to next tick");

        harness.tick();

        check(harness.job.stage()
                        == DungeonPreparationStage.PLANNING_ENTRY_CHUNKS,
                "success: planning entry chunks");
        harness.tick();
        check(harness.job.stage()
                        == DungeonPreparationStage.REQUESTING_ENTRY_CHUNKS,
                "success: requesting entry chunks");
        check(harness.executor.resolvedSiteFor(harness.job.id())
                        .filter(site::equals)
                        .isPresent(),
                "success: resolved site retained");
        check(harness.job.leases().size() == 1,
                "success: start lease retained by job");
        check(lease.closed == 0,
                "success: retained lease not closed");
        check(harness.backend.readLoadedCalls.equals(List.of(key)),
                "success: loaded read called once");
        check(harness.backend.reservationChecks.size() >= 2,
                "success: reservation checked during scan and before acceptance");
    }

    private static void noPersistedSitesStopsAtOrderedGenerationCandidateSelection() {
        Harness harness = Harness.withCandidates(3);
        harness.advanceToScanning();
        DungeonAsyncTestSupport.ControlledProbeBackend probe =
                harness.backend.probeBackend(0);
        probe.complete(0, DungeonAsyncTestSupport.notPersisted(DungeonAsyncTestSupport.chunk(0)));
        probe.complete(1, belowRequired(DungeonAsyncTestSupport.chunk(1)));
        probe.complete(2, DungeonAsyncTestSupport.notPersisted(DungeonAsyncTestSupport.chunk(2)));
        harness.transferProbeCompletionsToMailbox(probe);

        harness.tick();

        check(harness.job.stage() == DungeonPreparationStage.SELECTING_CANDIDATE,
                "generation: selecting candidate");
        check(harness.executor.generationCandidatesFor(harness.job.id())
                        .equals(harness.backend.candidates),
                "generation: input order retained");
    }

    private static void failedProbeDoesNotAbortLaterPersistedCandidate() {
        Harness harness = Harness.withCandidates(2);
        DungeonSiteKey second = harness.backend.candidates.get(1);
        harness.backend.loadedSites.put(
                second,
                DungeonPreparationLoadedSiteResult.accepted(site(second))
        );
        harness.advanceToScanning();
        DungeonAsyncTestSupport.ControlledProbeBackend probe =
                harness.backend.probeBackend(0);
        RuntimeException failure = new RuntimeException("disk");
        probe.completeExceptionally(0, failure);
        probe.completeAvailable(1);
        harness.transferProbeCompletionsToMailbox(probe);

        harness.tick();

        check(harness.job.stage()
                        == DungeonPreparationStage.REQUESTING_START_CHUNK,
                "failed probe: later candidate selected for request");
        harness.tick();
        check(harness.job.stage()
                        == DungeonPreparationStage.WAITING_FOR_START_CHUNK,
                "failed probe: later candidate requested");
        check(harness.backend.leases.get(0).chunkPos().equals(second.toChunkPos()),
                "failed probe: second candidate leased");
    }

    private static void leaseFailureReleasesLeaseAndRetriesNextPersistedCandidateLater() {
        Harness harness = Harness.withCandidates(2);
        DungeonSiteKey first = harness.backend.candidates.get(0);
        DungeonSiteKey second = harness.backend.candidates.get(1);
        harness.backend.loadedSites.put(
                second,
                DungeonPreparationLoadedSiteResult.accepted(site(second))
        );
        harness.advanceToScanning();
        harness.completeReportAsAvailable();
        harness.tick();
        check(harness.job.stage() == DungeonPreparationStage.REQUESTING_START_CHUNK,
                "lease retry: first candidate selected");
        harness.tick();
        FakeStartLease firstLease = harness.backend.leases.get(0);
        firstLease.state = DungeonChunkLeaseState.FAILED;
        firstLease.outcome = Optional.of(new DungeonChunkLoadOutcome.UnloadedResult("unloaded"));

        harness.tick();

        check(firstLease.closed == 1, "lease retry: failed lease closed");
        check(harness.job.leases().isEmpty(),
                "lease retry: failed lease removed from job");
        check(harness.job.stage() == DungeonPreparationStage.SELECTING_CANDIDATE,
                "lease retry: selecting next on later tick");
        check(harness.backend.leases.size() == 1,
                "lease retry: no same-tick replacement");

        harness.tick();

        check(harness.job.stage()
                        == DungeonPreparationStage.REQUESTING_START_CHUNK,
                "lease retry: next candidate selected later");
        harness.tick();
        check(harness.job.stage()
                        == DungeonPreparationStage.WAITING_FOR_START_CHUNK,
                "lease retry: next lease acquired after request");
        check(harness.backend.leases.size() == 2,
                "lease retry: second lease acquired");
        check(harness.backend.leases.get(0).chunkPos().equals(first.toChunkPos()),
                "lease retry: first was attempted");
        check(harness.backend.leases.get(1).chunkPos().equals(second.toChunkPos()),
                "lease retry: second attempted after failure");
    }

    private static void leaseFailureWithoutFallbackFailsAsChunkLoadFailed() {
        Harness harness = Harness.withCandidates(1);
        harness.advanceToScanning();
        harness.completeReportAsAvailable();
        harness.tick();
        harness.tick();
        FakeStartLease lease = harness.backend.leases.get(0);
        lease.state = DungeonChunkLeaseState.FAILED;
        lease.outcome = Optional.of(new DungeonChunkLoadOutcome.UnloadedResult("no chunk"));

        harness.tick();

        check(harness.job.stage() == DungeonPreparationStage.FAILED,
                "lease terminal: failed");
        check(harness.job.failureReason()
                        == DungeonPreparationJobFailureReason.CHUNK_LOAD_FAILED,
                "lease terminal: chunk-load reason");
        check(lease.closed == 1,
                "lease terminal: failed lease closed once");
    }

    private static void entryLeaseFailureRetriesNextCandidate() {
        Harness harness = Harness.withCandidates(2);
        DungeonSiteKey first = harness.backend.candidates.get(0);
        DungeonSiteKey second = harness.backend.candidates.get(1);
        harness.backend.loadedSites.put(
                first,
                DungeonPreparationLoadedSiteResult.accepted(site(first))
        );
        harness.backend.loadedSites.put(
                second,
                DungeonPreparationLoadedSiteResult.accepted(site(second))
        );
        harness.advanceToEntryChunksRequested();
        FakeStartLease firstStartLease = harness.backend.leases.get(0);
        FakeStartLease entryLease = harness.backend.leases.get(1);
        entryLease.state = DungeonChunkLeaseState.FAILED;
        entryLease.outcome = Optional.of(new DungeonChunkLoadOutcome.UnloadedResult("entry failed"));

        harness.tick();

        check(harness.job.stage() == DungeonPreparationStage.SELECTING_CANDIDATE,
                "entry lease retry: selecting next candidate");
        check(firstStartLease.closed == 1,
                "entry lease retry: start lease closed once");
        check(entryLease.closed == 1,
                "entry lease retry: entry lease closed once");
        check(harness.job.leases().isEmpty(),
                "entry lease retry: no leaked job leases");

        harness.tick();
        check(harness.job.stage() == DungeonPreparationStage.REQUESTING_START_CHUNK,
                "entry lease retry: second candidate selected");
        harness.tick();
        check(harness.backend.leases.size() == 3,
                "entry lease retry: second start lease acquired");
        check(harness.backend.leases.get(2).chunkPos().equals(second.toChunkPos()),
                "entry lease retry: second candidate used");
    }

    private static void entryChunkUnloadBeforeValidationRetriesNextCandidate() {
        Harness harness = Harness.withCandidates(2);
        DungeonSiteKey first = harness.backend.candidates.get(0);
        DungeonSiteKey second = harness.backend.candidates.get(1);
        harness.backend.loadedSites.put(
                first,
                DungeonPreparationLoadedSiteResult.accepted(site(first))
        );
        harness.backend.loadedSites.put(
                second,
                DungeonPreparationLoadedSiteResult.accepted(site(second))
        );
        harness.advanceToEntryChunksRequested();
        FakeStartLease firstStartLease = harness.backend.leases.get(0);
        FakeStartLease entryLease = harness.backend.leases.get(1);
        entryLease.state = DungeonChunkLeaseState.READY;
        harness.backend.chunkLoaded.put(entryLease.chunkPos(), false);

        harness.tick();
        check(harness.job.stage() == DungeonPreparationStage.VALIDATING_ENTRY_CHUNKS,
                "entry unload retry: validating loaded entry chunks");
        harness.tick();

        check(harness.job.stage() == DungeonPreparationStage.SELECTING_CANDIDATE,
                "entry unload retry: selecting next candidate");
        check(firstStartLease.closed == 1,
                "entry unload retry: start lease closed once");
        check(entryLease.closed == 1,
                "entry unload retry: entry lease closed once");
        check(harness.job.leases().isEmpty(),
                "entry unload retry: no leaked job leases");
    }

    private static void safeSpawnFailureRetriesNextCandidate() {
        Harness harness = Harness.withCandidates(2);
        DungeonSiteKey first = harness.backend.candidates.get(0);
        DungeonSiteKey second = harness.backend.candidates.get(1);
        harness.backend.loadedSites.put(
                first,
                DungeonPreparationLoadedSiteResult.accepted(site(first))
        );
        harness.backend.loadedSites.put(
                second,
                DungeonPreparationLoadedSiteResult.accepted(site(second))
        );
        harness.advanceToValidatingEntry();
        harness.backend.safeEntryAvailable = false;
        FakeStartLease firstStartLease = harness.backend.leases.get(0);
        FakeStartLease entryLease = harness.backend.leases.get(1);

        harness.tick();

        CountingSafeSpawnScan failedScan = harness.backend.lastSafeSpawnScan;
        check(harness.job.stage() == DungeonPreparationStage.SELECTING_CANDIDATE,
                "safe spawn retry: selecting next candidate");
        check(firstStartLease.closed == 1,
                "safe spawn retry: start lease closed once");
        check(entryLease.closed == 1,
                "safe spawn retry: entry lease closed once");
        check(harness.job.leases().isEmpty(),
                "safe spawn retry: no leaked job leases");

        harness.backend.safeEntryAvailable = true;
        harness.backend.safeSpawnCandidatesBeforeResult = 2L;
        harness.tick();
        harness.tick();
        harness.backend.leases.get(2).state = DungeonChunkLeaseState.READY;
        harness.tick();
        harness.tick();
        harness.tick();
        harness.tick();
        harness.backend.leases.get(3).state = DungeonChunkLeaseState.READY;
        harness.tick();
        harness.tick();
        harness.tick();

        check(harness.job.stage() == DungeonPreparationStage.READY_TO_COMMIT,
                "safe spawn retry: second candidate succeeds");
        check(harness.backend.lastSafeSpawnScan != failedScan,
                "safe spawn retry: new candidate owns a fresh scan");
    }

    private static void validatingEntrySpansTicksUnderSafeSpawnBudget() {
        Harness harness = Harness.withCandidates(1);
        harness.backend.safeSpawnCandidatesBeforeResult = 130L;
        harness.advanceToValidatingEntry();

        harness.tickWithSafeSpawnBudget(64);
        check(harness.job.stage() == DungeonPreparationStage.VALIDATING_ENTRY,
                "safe-spawn batching: stage spans first tick");
        DungeonPreparationProgressSnapshot first =
                harness.executor.progressSnapshot(harness.job.id()).orElseThrow();
        check(first.checkedSafeSpawnCandidates() == 64L,
                "safe-spawn batching: first tick checks 64");
        check(first.totalSafeSpawnCandidates() == 130L,
                "safe-spawn batching: exact total retained");

        harness.tickWithSafeSpawnBudget(64);
        check(harness.job.stage() == DungeonPreparationStage.VALIDATING_ENTRY,
                "safe-spawn batching: stage spans second tick");
        check(harness.executor.progressSnapshot(harness.job.id()).orElseThrow()
                        .checkedSafeSpawnCandidates() == 128L,
                "safe-spawn batching: second tick resumes cursor");

        harness.tickWithSafeSpawnBudget(64);
        check(harness.job.stage() == DungeonPreparationStage.READY_TO_COMMIT,
                "safe-spawn batching: third tick finds destination");
    }

    private static void cancellingContextDiscardsSafeSpawnScanAndClosesLeases() {
        Harness harness = Harness.withCandidates(1);
        harness.backend.safeSpawnCandidatesBeforeResult = 130L;
        harness.advanceToValidatingEntry();
        harness.tickWithSafeSpawnBudget(1);
        CountingSafeSpawnScan scan = harness.backend.lastSafeSpawnScan;

        harness.executor.cancelContext(harness.job.id());

        check(scan.result().state() == DungeonSafeSpawnScanState.CANCELLED,
                "safe-spawn cancel: scan cancelled");
        check(harness.job.leases().isEmpty(),
                "safe-spawn cancel: owned leases removed");
        check(harness.backend.leases.stream().allMatch(lease -> lease.closed == 1),
                "safe-spawn cancel: every owned lease closed once");
    }

    private static void wallClockExhaustionPausesPreparationWithoutFailure() {
        Harness harness = Harness.withCandidates(1);
        DungeonPreparationTickBudget.NanoClock clock = new DungeonPreparationTickBudget.NanoClock() {
            private int calls;

            @Override
            public long nanoTime() {
                return this.calls++ == 0 ? 0L : 2L;
            }
        };
        DungeonPreparationTickBudget budget =
                DungeonPreparationTickBudget.boundedForTests(
                        clock,
                        1L,
                        1, 1, 1, 1, 1, 1, 1, 1, 64, 1, 8, 4
                );

        harness.executor.tick(harness.job, budget);

        check(harness.job.stage() == DungeonPreparationStage.QUEUED,
                "wall clock: exhausted tick leaves preparation paused");
        check(!harness.job.isTerminal(),
                "wall clock: exhaustion is not a failure");
    }

    private static void entryValidationCursorsResumeWithoutRescanning() {
        Harness harness = Harness.withCandidates(1);
        harness.advanceToEntryChunksRequested();
        DungeonPreparationExecutionContext context =
                harness.executor.ctx(harness.job.id());
        DungeonEntryChunkPlan original = context.entryChunkPlan().orElseThrow();
        ArrayList<ChunkPos> chunks = new ArrayList<>(original.chunks());
        for (int index = 1; index < 4; index++) {
            chunks.add(new ChunkPos(index * 10, index * 10));
        }
        context.entryChunkPlan(new DungeonEntryChunkPlan(
                original.roomMinChunk(),
                original.roomMaxChunk(),
                original.requestedMinChunk(),
                original.requestedMaxChunk(),
                chunks
        ));
        harness.backend.leases.get(1).state = DungeonChunkLeaseState.READY;
        for (int index = 1; index < chunks.size(); index++) {
            FakeStartLease lease = new FakeStartLease(chunks.get(index));
            lease.state = DungeonChunkLeaseState.READY;
            harness.backend.leases.add(lease);
            harness.job.addCloseableLease(lease);
            context.addEntryChunkLease(lease);
        }

        for (int expected = 1; expected <= chunks.size(); expected++) {
            harness.executor.tick(harness.job, oneValidationBudget());
            check(context.nextEntryLeaseValidationIndex() == expected,
                    "lease cursor advances exactly once per time slice");
            for (int index = 0; index < chunks.size(); index++) {
                int expectedReads = index < expected ? 1 : 0;
                check(harness.backend.leases.get(index + 1).stateReads
                                == expectedReads,
                        "ready lease " + index + " is not rescanned");
            }
        }
        check(harness.job.stage()
                        == DungeonPreparationStage.VALIDATING_ENTRY_CHUNKS,
                "lease cursor advances stage only after complete pass");

        for (int expected = 1; expected <= chunks.size(); expected++) {
            harness.executor.tick(harness.job, oneValidationBudget());
            check(context.nextLoadedChunkValidationIndex() == expected,
                    "loaded cursor advances exactly once per time slice");
            check(harness.backend.loadedChecks.size() == expected,
                    "loaded chunks are not rescanned");
        }
        check(harness.job.stage() == DungeonPreparationStage.VALIDATING_ENTRY,
                "loaded cursor advances stage only after complete pass");

        context.entryChunkPlan(original);
        check(context.nextEntryLeaseValidationIndex() == 0
                        && context.nextLoadedChunkValidationIndex() == 0,
                "new entry plan resets both validation cursors");
    }

    private static DungeonPreparationTickBudget oneValidationBudget() {
        DungeonPreparationTickBudget.NanoClock clock = new DungeonPreparationTickBudget.NanoClock() {
            private long value;

            @Override
            public long nanoTime() {
                return this.value++;
            }
        };
        return DungeonPreparationTickBudget.boundedForTests(
                clock,
                3L,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                64,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE
        );
    }

    private static DungeonPreparationTickBudget productionBudget(
            int persistedProbeSubmissions,
            int maxInFlightPersistedProbes
    ) {
        return DungeonPreparationTickBudget.boundedForTests(
                () -> 0L,
                DungeonPreparationLimits.MAX_PREPARATION_NANOS_PER_LEVEL_TICK,
                DungeonPreparationLimits.START_CHUNK_REQUESTS_PER_LEVEL_TICK,
                DungeonPreparationLimits.ENTRY_CHUNK_REQUESTS_PER_LEVEL_TICK,
                DungeonPreparationLimits.CANDIDATE_KEYS_ENUMERATED_PER_LEVEL_TICK,
                DungeonPreparationLimits.LOADED_FAST_PATH_PROBES_PER_LEVEL_TICK,
                DungeonPreparationLimits.PERSISTED_SCANNER_STARTS_PER_LEVEL_TICK,
                persistedProbeSubmissions,
                DungeonPreparationLimits.PERSISTED_PROBE_COMPLETION_DRAINS_PER_LEVEL_TICK,
                DungeonPreparationLimits.PERSISTED_PROBE_RESULTS_CLASSIFIED_PER_LEVEL_TICK,
                DungeonPreparationLimits.SAFE_SPAWN_CANDIDATES_PER_LEVEL_TICK,
                DungeonPreparationLimits.MAX_ACTIVE_PERSISTED_SCANNERS_PER_LEVEL,
                maxInFlightPersistedProbes,
                DungeonPreparationLimits.MAX_GENERATION_ATTEMPTS
        );
    }

    private static void invalidLoadedSiteReleasesLeaseAndRetriesNextPersistedCandidate() {
        Harness harness = Harness.withCandidates(2);
        DungeonSiteKey first = harness.backend.candidates.get(0);
        DungeonSiteKey second = harness.backend.candidates.get(1);
        harness.backend.loadedSites.put(
                first,
                DungeonPreparationLoadedSiteResult.rejected(
                        DungeonPreparationJobFailureReason.STRUCTURE_START_INVALID,
                        "invalid start"
                )
        );
        harness.backend.loadedSites.put(
                second,
                DungeonPreparationLoadedSiteResult.accepted(site(second))
        );
        harness.advanceToScanning();
        harness.completeReportAsAvailable();
        harness.tick();
        harness.tick();
        FakeStartLease firstLease = harness.backend.leases.get(0);
        firstLease.state = DungeonChunkLeaseState.READY;
        harness.tick();

        harness.tick();

        check(firstLease.closed == 1,
                "read retry: invalid candidate lease closed");
        check(harness.job.stage() == DungeonPreparationStage.SELECTING_CANDIDATE,
                "read retry: selecting next candidate");
        harness.tick();
        check(harness.job.stage() == DungeonPreparationStage.REQUESTING_START_CHUNK,
                "read retry: requesting second candidate");
        harness.tick();
        check(harness.backend.leases.get(1).chunkPos().equals(second.toChunkPos()),
                "read retry: second candidate leased");
    }

    private static void reservationRecheckFiltersPersistedAndGenerationCandidates() {
        Harness harness = Harness.withCandidates(3);
        DungeonSiteKey first = harness.backend.candidates.get(0);
        DungeonSiteKey second = harness.backend.candidates.get(1);
        DungeonSiteKey third = harness.backend.candidates.get(2);
        harness.backend.reservationReasons.put(first, "candidate_reserved");
        harness.backend.reservationReasons.put(second, "candidate_reserved");
        harness.advanceToScanning();
        DungeonAsyncTestSupport.ControlledProbeBackend probe =
                harness.backend.probeBackend(0);
        probe.completeAvailable(0);
        probe.complete(1, DungeonAsyncTestSupport.notPersisted(second.toChunkPos()));
        probe.complete(2, DungeonAsyncTestSupport.notPersisted(third.toChunkPos()));
        harness.transferProbeCompletionsToMailbox(probe);

        harness.tick();

        check(harness.job.stage() == DungeonPreparationStage.SELECTING_CANDIDATE,
                "reservation: only generation-safe candidate remains");
        check(harness.executor.generationCandidatesFor(harness.job.id())
                        .equals(List.of(third)),
                "reservation: rejected candidates filtered");
        check(harness.backend.leases.isEmpty(),
                "reservation: rejected persisted candidate not leased");
    }

    private static void emptyCandidateScanFailsDeterministically() {
        Harness harness = Harness.withCandidates(0);
        harness.advanceToScanning();
        harness.backend.ownerExecutor.drain();

        harness.tick();

        check(harness.job.stage() == DungeonPreparationStage.FAILED,
                "empty: failed");
        check(harness.job.failureReason()
                        == DungeonPreparationJobFailureReason.NO_SITE_AVAILABLE,
                "empty: no site available");
    }

    private static void cancellingContextIgnoresLateScannerCallback() {
        Harness harness = Harness.withCandidates(1);
        harness.advanceToScanning();
        DungeonAsyncTestSupport.ControlledProbeBackend probe =
                harness.backend.probeBackend(0);

        harness.executor.cancelContext(harness.job.id());
        harness.registry.cancel(
                harness.job.id(),
                DungeonPreparationCancellationReason.USER_CANCELLED,
                "test",
                harness.backend.gameTime()
        );
        probe.completeAvailable(0);
        harness.transferProbeCompletionsToMailbox(probe);

        check(harness.job.stage() == DungeonPreparationStage.CANCELLED,
                "cancel late: job remains cancelled");
        check(harness.executor.resolvedSiteFor(harness.job.id()).isEmpty(),
                "cancel late: no resolved site");
        check(harness.backend.leases.isEmpty(),
                "cancel late: no lease acquired");
    }

    private static void starterOnlyRequestReachesReadyToCommit() {
        Harness harness = Harness.withCandidates(1);

        harness.advanceToReadyToCommit();

        check(harness.job.stage() == DungeonPreparationStage.READY_TO_COMMIT,
                "ready-to-commit: stage");
        check(harness.executor.resolvedSiteFor(harness.job.id()).isPresent(),
                "ready-to-commit: resolved site retained");
    }

    private static void commitSuccessReachesReady() {
        SuccessfulCommitter committer = new SuccessfulCommitter();
        Harness harness = Harness.withCandidatesAndCommitter(1, committer);
        harness.advanceToReadyToCommit();

        harness.tick();
        check(harness.job.stage() == DungeonPreparationStage.READY_TO_COMMIT,
                "commit preflight: remains ready-to-commit for one tick");
        harness.tick();

        check(harness.job.stage() == DungeonPreparationStage.READY,
                "commit success: ready");
        check(harness.job.leases().isEmpty(),
                "commit success: job owns zero leases");
        check(committer.detachedBundle != null
                        && !committer.detachedBundle.leases().isEmpty(),
                "commit success: entry leases transferred");
        check(committer.afterReadyCalls == 1,
                "commit success: after-ready hook called");
        committer.detachedBundle.close();
    }

    private static void postCommitNotificationFailureDoesNotFailReadyJob() {
        SuccessfulCommitter committer = new SuccessfulCommitter();
        committer.throwAfterReady = true;
        Harness harness = Harness.withCandidatesAndCommitter(1, committer);
        harness.backend.tolerateRuntimeFailures = true;
        harness.advanceToReadyToCommit();

        harness.tick();
        harness.tick();

        check(harness.job.stage() == DungeonPreparationStage.READY,
                "post commit failure: job remains ready");
        check(harness.job.failureReason() == null,
                "post commit failure: no failure reason");
        check(harness.backend.loggedRuntimeFailures == 1,
                "post commit failure: logged once");
        committer.detachedBundle.close();
    }

    private static void commitFailureReachesFailed() {
        Harness harness = Harness.withCandidatesAndCommitter(
                1,
                (job, context) -> DungeonActivationCommitResult.failure(
                        DungeonActivationCommitFailureReason.INVALID_TRIBUTE,
                        "changed"
                )
        );
        harness.advanceToReadyToCommit();

        harness.tick();
        harness.tick();

        check(harness.job.stage() == DungeonPreparationStage.FAILED,
                "commit failure: failed");
        check(harness.job.failureReason()
                        == DungeonPreparationJobFailureReason.INVALID_TRIBUTE,
                "commit failure: mapped reason");
        check(harness.job.leases().isEmpty(),
                "commit failure: leases released");
    }

    private static void claimLossFailureMappedToSiteClaimLost() {
        Harness harness = Harness.withCandidatesAndCommitter(
                1,
                (job, context) -> DungeonActivationCommitResult.failure(
                        DungeonActivationCommitFailureReason.SITE_CLAIM_LOST,
                        "claim not owned"
                )
        );
        harness.advanceToReadyToCommit();

        harness.tick();
        harness.tick();

        check(harness.job.stage() == DungeonPreparationStage.FAILED,
                "claim loss: failed");
        check(harness.job.failureReason()
                        == DungeonPreparationJobFailureReason.SITE_CLAIM_LOST,
                "claim loss: mapped reason");
        check(harness.job.failureDetail() != null
                        && harness.job.failureDetail().contains("claim not owned"),
                "claim loss: detail preserved");
        check(harness.metrics.claimReleaseInvariantFailures == 1,
                "claim loss: production metrics sink recorded");
    }

    private static void committedPublicationFailureRecordsMetrics() {
        Harness harness = Harness.withCandidatesAndCommitter(
                1,
                (job, context) -> DungeonActivationCommitResult.success(
                        DungeonInstanceId.create(),
                        PortalSessionId.create()
                )
        );
        harness.backend.tolerateRuntimeFailures = true;
        harness.advanceToReadyToCommit();

        harness.tick();
        harness.tick();

        check(harness.job.stage() == DungeonPreparationStage.COMMITTING,
                "publication failure metrics: remains committing");
        check(harness.metrics.committedPublicationFailures == 1,
                "publication failure metrics: recorded once");
        check(harness.backend.loggedRuntimeFailures == 1,
                "publication failure metrics: logged once");
    }

    private static void committedResultRetainedOnPublicationFailure() {
        SuccessfulCommitter committer = new SuccessfulCommitter();
        Harness harness = Harness.withCandidatesAndCommitter(1, committer);
        harness.advanceToReadyToCommit();

        harness.tick();
        harness.tick();

        check(harness.job.stage() == DungeonPreparationStage.READY,
                "publication success: ready");
        check(harness.job.leases().isEmpty(),
                "publication success: no leases");
        check(committer.afterReadyCalls == 1,
                "publication success: after-ready called once");
        committer.detachedBundle.close();
    }

    private static void entryProgressSnapshotCountsRequestedAndReady() {
        Harness harness = Harness.withCandidates(1);
        DungeonSiteKey key = harness.backend.candidates.get(0);
        harness.backend.loadedSites.put(
                key,
                DungeonPreparationLoadedSiteResult.accepted(site(key))
        );
        harness.advanceToEntryChunksRequested();
        DungeonPreparationProgressSnapshot requested =
                harness.executor.progressSnapshot(harness.job.id()).orElseThrow();
        check(requested.totalEntryChunks() == 1,
                "entry progress: total chunks");
        check(requested.requestedEntryChunks() == 1,
                "entry progress: requested chunks");
        check(requested.readyEntryChunks() == 0,
                "entry progress: no ready chunks");

        harness.backend.leases.get(1).state = DungeonChunkLeaseState.READY;
        DungeonPreparationProgressSnapshot ready =
                harness.executor.progressSnapshot(harness.job.id()).orElseThrow();
        check(ready.readyEntryChunks() == 1,
                "entry progress: ready chunks");
    }

    private static void generationAttemptsNeverExceedFour() {
        Harness harness = Harness.withCandidates(8);
        harness.advanceToScanning();
        DungeonAsyncTestSupport.ControlledProbeBackend probe =
                harness.backend.probeBackend(0);
        for (int i = 0; i < probe.probeCalls(); i++) {
            probe.complete(i, DungeonAsyncTestSupport.notPersisted(
                    harness.backend.candidates.get(i).toChunkPos()
            ));
        }
        harness.transferProbeCompletionsToMailbox(probe);

        harness.tick();

        check(harness.executor.generationCandidatesFor(harness.job.id()).size()
                        == DungeonPreparationLimits.MAX_GENERATION_ATTEMPTS,
                "generation cap: four absent candidates retained");
    }

   private static void wrongThreadTickIsRejectedBeforeMutation() {
        Harness harness = Harness.withCandidates(1);
        harness.backend.ownerExecutor.setOwnerThread(false);
        try {
            harness.executor.tick(harness.job);
            check(false, "wrong thread: should throw");
        } catch (IllegalStateException expected) {
            check(harness.job.stage() == DungeonPreparationStage.QUEUED,
                    "wrong thread: stage unchanged");
        } finally {
            harness.backend.ownerExecutor.setOwnerThread(true);
        }
    }

    private static DungeonPersistedChunkProbeResult belowRequired(ChunkPos chunkPos) {
        return new DungeonPersistedChunkProbeResult(
                chunkPos,
                DungeonPersistedChunkProbeResult.Classification.BELOW_REQUIRED_STATUS,
                Optional.of(ChunkStatus.EMPTY),
                "below",
                Optional.empty()
        );
    }

    private static DungeonSite site(DungeonSiteKey key) {
        DungeonRoomId roomId = DungeonRoomId.of("start");
        BlockPos start = new BlockPos(1, 1, 1);
        DungeonBounds bounds = new DungeonBounds(0, 0, 0, 4, 4, 4);
        DungeonGeneratedRoom room = new DungeonGeneratedRoom(
                roomId,
                DungeonRoomType.START,
                bounds,
                start
        );
        return new DungeonSite(
                key,
                bounds,
                roomId,
                start,
                List.of(room)
        );
    }

    private static DungeonPreparationRequest request() {
        return DungeonPreparationRequest.forTests(
                UUID.randomUUID(),
                OVERWORLD,
                new BlockPos(0, 64, 0),
                validTribute(),
                1
        );
    }

    private static ResolvedTribute validTribute() {
        return new ResolvedTribute(true, 1, 1, 0.0F, 1.0F, 1);
    }

    private static void check(boolean condition, String message) {
        DungeonAsyncTestSupport.check(condition, message);
    }

    private static final class Harness {
        final FakeExecutionBackend backend;
        final DungeonPreparationJobRegistry registry = new DungeonPreparationJobRegistry();
        final DungeonPreparationJobExecutor executor;
        final DungeonPreparationJob job;
        final CountingFailureMetrics metrics = new CountingFailureMetrics();

        private Harness(List<DungeonSiteKey> candidates) {
            this(candidates, (job, context) -> DungeonActivationCommitResult.failure(
                    DungeonActivationCommitFailureReason.INTERNAL_ERROR,
                    "no commit expected"
            ));
        }

        private Harness(
                List<DungeonSiteKey> candidates,
                DungeonPreparationCommitter committer
        ) {
            this.backend = new FakeExecutionBackend(candidates);
            DungeonSiteClaimManager claimMgr = new DungeonSiteClaimManager(new FakeClaimBackend());
            this.executor = new DungeonPreparationJobExecutor(
                    this.registry,
                    this.backend,
                    claimMgr,
                    committer,
                    this.metrics
            );
            this.job = new DungeonPreparationJob(
                    DungeonPreparationJobId.create(),
                    request(),
                    this.backend.gameTime()
            );
            check(this.registry.submit(this.job).isAccepted(),
                    "harness: job accepted");
            this.executor.createContext(this.job);
        }

        static Harness withCandidates(int count) {
            return new Harness(DungeonAsyncTestSupport.candidates(count));
        }

        static Harness withCandidatesAndCommitter(
                int count,
                DungeonPreparationCommitter committer
        ) {
            return new Harness(DungeonAsyncTestSupport.candidates(count), committer);
        }

        void tick() {
            this.executor.tick(this.job);
        }

        void boundedTick() {
            this.executor.tick(
                    this.job,
                    DungeonPreparationTickBudget.perLevelTick(() -> 0L)
            );
        }

        void tickWithSafeSpawnBudget(int candidates) {
            this.executor.tick(
                    this.job,
                    DungeonPreparationTickBudget.boundedForTests(
                            () -> 0L,
                            Long.MAX_VALUE,
                            Integer.MAX_VALUE,
                            Integer.MAX_VALUE,
                            Integer.MAX_VALUE,
                            Integer.MAX_VALUE,
                            Integer.MAX_VALUE,
                            Integer.MAX_VALUE,
                            Integer.MAX_VALUE,
                            Integer.MAX_VALUE,
                            candidates,
                            Integer.MAX_VALUE,
                            Integer.MAX_VALUE,
                            Integer.MAX_VALUE
                    )
            );
        }

        void advanceToScanning() {
            tick();
            tick();
            check(this.job.stage()
                            == DungeonPreparationStage.SCANNING_EXISTING_SITES,
                    "harness: scanning");
            tick();
        }

        void completeReportAsAvailable() {
            DungeonAsyncTestSupport.ControlledProbeBackend probe =
                    this.backend.probeBackend(0);
            for (int i = 0; i < probe.probeCalls(); i++) {
                probe.completeAvailable(i);
            }
            transferProbeCompletionsToMailbox(probe);
        }

        void transferProbeCompletionsToMailbox(
                DungeonAsyncTestSupport.ControlledProbeBackend probe
        ) {
            probe.ownerExecutor.drain();
        }

        void advanceToReadyToCommit() {
            DungeonSiteKey key = this.backend.candidates.get(0);
            this.backend.loadedSites.put(
                    key,
                    DungeonPreparationLoadedSiteResult.accepted(site(key))
            );
            advanceToScanning();
            completeReportAsAvailable();
            tick();
            check(this.job.stage() == DungeonPreparationStage.REQUESTING_START_CHUNK,
                    "harness: requesting start");
            tick();
            check(this.job.stage() == DungeonPreparationStage.WAITING_FOR_START_CHUNK,
                    "harness: waiting start");
            this.backend.leases.get(0).state = DungeonChunkLeaseState.READY;
            tick();
            check(this.job.stage() == DungeonPreparationStage.READING_STRUCTURE_START,
                    "harness: reading start");
            tick();
            check(this.job.stage() == DungeonPreparationStage.PLANNING_ENTRY_CHUNKS,
                    "harness: planning entry chunks");
            tick();
            check(this.job.stage() == DungeonPreparationStage.REQUESTING_ENTRY_CHUNKS,
                    "harness: requesting entry chunks");
            tick();
            check(this.job.stage() == DungeonPreparationStage.WAITING_FOR_ENTRY_CHUNKS,
                    "harness: waiting entry chunks");
            for (int i = 1; i < this.backend.leases.size(); i++) {
                this.backend.leases.get(i).state = DungeonChunkLeaseState.READY;
            }
            tick();
            check(this.job.stage() == DungeonPreparationStage.VALIDATING_ENTRY_CHUNKS,
                    "harness: validating entry chunks");
            tick();
            check(this.job.stage() == DungeonPreparationStage.VALIDATING_ENTRY,
                    "harness: validating entry");
            tick();
        }

        void advanceToEntryChunksRequested() {
            DungeonSiteKey key = this.backend.candidates.get(0);
            this.backend.loadedSites.putIfAbsent(
                    key,
                    DungeonPreparationLoadedSiteResult.accepted(site(key))
            );
            advanceToScanning();
            completeReportAsAvailable();
            tick();
            tick();
            this.backend.leases.get(0).state = DungeonChunkLeaseState.READY;
            tick();
            tick();
            tick();
            tick();
            check(this.job.stage() == DungeonPreparationStage.WAITING_FOR_ENTRY_CHUNKS,
                    "harness: waiting entry chunks");
            check(this.backend.leases.size() >= 2,
                    "harness: entry lease requested");
        }

        void advanceToValidatingEntry() {
            advanceToEntryChunksRequested();
            this.backend.leases.get(1).state = DungeonChunkLeaseState.READY;
            tick();
            check(this.job.stage() == DungeonPreparationStage.VALIDATING_ENTRY_CHUNKS,
                    "harness: validating entry chunks");
            tick();
            check(this.job.stage() == DungeonPreparationStage.VALIDATING_ENTRY,
                    "harness: validating entry");
        }
    }

   private static void waitingForEntryChunksWithMissingLeaseFailsInvariant() {
       Harness harness = Harness.withCandidates(2);
       DungeonSiteKey key = harness.backend.candidates.get(0);
       harness.backend.loadedSites.put(
               key,
               DungeonPreparationLoadedSiteResult.accepted(site(key))
       );
       harness.advanceToEntryChunksRequested();

       check(harness.job.stage() == DungeonPreparationStage.WAITING_FOR_ENTRY_CHUNKS,
               "invariant setup: waiting for entry chunks");
       check(harness.job.leases().size() >= 2,
               "invariant setup: job owns start lease and entry lease");

       DungeonPreparationExecutionContext ctx =
               harness.executor.ctx(harness.job.id());
       check(ctx != null, "invariant setup: context exists");

       DungeonEntryChunkPlan originalPlan = ctx.entryChunkPlan().orElseThrow();
       int planSize = originalPlan.chunks().size();

       List<ChunkPos> extraChunks = new ArrayList<>(originalPlan.chunks());
       extraChunks.add(new ChunkPos(
               originalPlan.chunks().get(0).x() + 10,
               originalPlan.chunks().get(0).z() + 10
       ));
       DungeonEntryChunkPlan inflatedPlan = new DungeonEntryChunkPlan(
               originalPlan.roomMinChunk(),
               originalPlan.roomMaxChunk(),
               originalPlan.requestedMinChunk(),
               originalPlan.requestedMaxChunk(),
               extraChunks
       );
       ctx.entryChunkPlan(inflatedPlan);

       int entryLeaseCount = ctx.entryChunkLeaseCount();
       check(entryLeaseCount < inflatedPlan.chunks().size(),
               "invariant setup: lease count " + entryLeaseCount
                       + " < plan size " + inflatedPlan.chunks().size());

       FakeStartLease startLease = harness.backend.leases.get(0);
       int startLeaseCloseBefore = startLease.closed;

       harness.tick();

       check(harness.job.stage() == DungeonPreparationStage.FAILED,
               "invariant: stage is FAILED");
       check(harness.job.failureReason()
                       == DungeonPreparationJobFailureReason.INTERNAL_ERROR,
               "invariant: failure reason INTERNAL_ERROR");
       check(harness.job.failureDetail() != null
                       && harness.job.failureDetail().contains(
                               "entry chunk lease invariant violated"),
               "invariant: detail contains invariant violated");
       check(harness.job.failureDetail().contains(
                       String.valueOf(inflatedPlan.chunks().size())),
               "invariant: detail contains expected count");
       check(harness.job.failureDetail().contains(
                       String.valueOf(entryLeaseCount)),
               "invariant: detail contains actual count");

       check(harness.job.leases().size() == 0,
               "invariant: job-owned lease count == 0");
       check(startLease.closed == startLeaseCloseBefore + 1,
               "invariant: start lease closed exactly once");

       for (int i = 1; i < harness.backend.leases.size(); i++) {
           check(harness.backend.leases.get(i).closed >= 1,
                   "invariant: entry lease " + i + " closed at least once");
       }

       check(harness.executor.ctx(harness.job.id()) == null,
               "invariant: execution context removed");
   }


   private static void backendAcquisitionFailureCleansClaimAndFailsJob() {
       Harness harness = Harness.withCandidates(1);
       harness.advanceToScanning();
       harness.completeReportAsAvailable();
       harness.tick();

       check(harness.job.stage() == DungeonPreparationStage.REQUESTING_START_CHUNK,
               "acq fail setup: requesting start chunk");

       RuntimeException acquisitionFailure = new RuntimeException("backend down");
       harness.backend.acquisitionFailure = acquisitionFailure;
       harness.backend.tolerateRuntimeFailures = true;

       harness.tick();

       check(harness.backend.loggedRuntimeFailures >= 1,
               "acq fail: runtime failure logged");
       check(harness.job.stage() == DungeonPreparationStage.FAILED,
               "acq fail: job FAILED");
       check(harness.job.failureReason()
                       == DungeonPreparationJobFailureReason.INTERNAL_ERROR,
               "acq fail: INTERNAL_ERROR");
       check(harness.job.leases().isEmpty(),
               "acq fail: no lease retained");
       check(harness.executor.ctx(harness.job.id()) == null,
               "acq fail: execution context cleaned");
   }

   private static void nullStartLeaseResultFailsJob() {
       Harness harness = Harness.withCandidates(1);
       harness.advanceToScanning();
       harness.completeReportAsAvailable();
       harness.tick();

       check(harness.job.stage() == DungeonPreparationStage.REQUESTING_START_CHUNK,
               "null lease setup: requesting start chunk");

       harness.backend.returnNullLease = true;
        harness.backend.tolerateRuntimeFailures = true;

       harness.tick();

       check(harness.job.stage() == DungeonPreparationStage.FAILED,
               "null lease: job FAILED");
       check(harness.job.failureReason()
                       == DungeonPreparationJobFailureReason.INTERNAL_ERROR,
               "null lease: INTERNAL_ERROR");
       check(harness.job.failureDetail() != null
                       && harness.job.failureDetail().contains(
                               "backend returned a null start chunk lease"),
               "null lease: detail contains null lease message");
       check(harness.job.leases().isEmpty(),
               "null lease: no lease retained");
       check(harness.executor.ctx(harness.job.id()) == null,
               "null lease: execution context cleaned");
   }

   private static void claimReleaseCleanupFailureSuppressedOnPrimary() {
       RuntimeException acquisitionFailure = new RuntimeException("primary");
       Harness harness = Harness.withCandidates(1);
       harness.advanceToScanning();
       harness.completeReportAsAvailable();
       harness.tick();

       check(harness.job.stage() == DungeonPreparationStage.REQUESTING_START_CHUNK,
               "cleanup suppressed setup: requesting start chunk");

       harness.backend.acquisitionFailure = acquisitionFailure;
       harness.backend.tolerateRuntimeFailures = true;

       RuntimeException cleanupFailure = new RuntimeException("claim release");
       harness.backend.claimReleaseFailure = cleanupFailure;

       harness.tick();

       check(harness.job.stage() == DungeonPreparationStage.FAILED,
               "cleanup suppressed: job FAILED");

       Throwable[] suppressed = acquisitionFailure.getSuppressed();
       check(suppressed.length >= 1,
               "cleanup suppressed: cleanup failure suppressed on primary");
       check(suppressed[0] == cleanupFailure,
               "cleanup suppressed: exact cleanup exception");

       check(harness.executor.ctx(harness.job.id()) == null,
               "cleanup suppressed: execution context cleaned");
   }

   private static void rejectedLeaseNotDoubleClosed() {
       Harness harness = Harness.withCandidates(1);
       harness.advanceToScanning();
       harness.completeReportAsAvailable();
       harness.tick();

       check(harness.job.stage() == DungeonPreparationStage.REQUESTING_START_CHUNK,
               "rejected lease setup: requesting start chunk");

       harness.backend.tolerateRuntimeFailures = true;
       harness.backend.beforeReturnCallback = () -> {
           try {
               harness.registry.fail(
                       harness.job.id(),
                       DungeonPreparationJobFailureReason.INTERNAL_ERROR,
                       "terminal-before-addLease",
                       harness.backend.gameTime()
               );
           } catch (RuntimeException e) {
               // job already terminal
           }
       };

       int preLeaseCount = harness.backend.leases.size();

       harness.tick();

       check(harness.job.stage() == DungeonPreparationStage.FAILED,
               "rejected lease: job FAILED");
       check(harness.job.leases().isEmpty(),
               "rejected lease: no job lease retained");

       if (harness.backend.leases.size() > preLeaseCount) {
           FakeStartLease newLease = harness.backend.leases.get(preLeaseCount);
           check(newLease.closed == 1,
                   "rejected lease: lease closed exactly once (by addCloseableLease rejection)");
       }

       check(harness.executor.ctx(harness.job.id()) == null,
               "rejected lease: execution context cleaned");
   }
    private static final class ListCandidateCursor
            implements DungeonSiteCandidateCursor {
        private final List<DungeonSiteKey> candidates;
        private int producedCount;

        ListCandidateCursor(List<DungeonSiteKey> candidates) {
            this.candidates = List.copyOf(candidates);
        }

        @Override
        public int advance(int maximumKeys, Consumer<DungeonSiteKey> sink) {
            if (maximumKeys < 0) {
                throw new IllegalArgumentException(
                        "maximumKeys must be non-negative"
                );
            }
            int emitted = 0;
            while (emitted < maximumKeys
                    && this.producedCount < this.candidates.size()) {
                sink.accept(this.candidates.get(this.producedCount));
                this.producedCount++;
                emitted++;
            }
            return emitted;
        }

        @Override
        public boolean exhausted() {
            return this.producedCount >= this.candidates.size();
        }

        @Override
        public int producedCount() {
            return this.producedCount;
        }

        @Override
        public List<DungeonSiteKey> producedKeys() {
            return List.copyOf(this.candidates.subList(0, this.producedCount));
        }
    }

    private static final class CountingFailureMetrics
            implements DungeonPreparationFailureMetrics {
        int claimReleaseInvariantFailures;
        int committedPublicationFailures;

        @Override
        public void recordClaimReleaseInvariantFailure() {
            this.claimReleaseInvariantFailures++;
        }

        @Override
        public void recordCommittedPublicationFailure() {
            this.committedPublicationFailures++;
        }
    }

    private static final class SuccessfulCommitter
            implements DungeonPreparationCommitter {
        DungeonPreparationLeaseBundle detachedBundle;
        int afterReadyCalls;
        boolean throwAfterReady;

        @Override
        public DungeonActivationCommitResult commit(
                DungeonPreparationJob job,
                DungeonPreparationExecutionContext context
        ) {
            context.currentStartLease().ifPresent(lease -> {
                job.closeAndRemoveLease(lease);
                context.clearCurrentStartLease();
            });
            List<DungeonPreparationStartChunkLease> entryLeases =
                    context.entryChunkLeases();
            this.detachedBundle = job.detachSelectedLeases(entryLeases);
            context.clearEntryChunkLeases();
            context.resetEntryChunkRequestIndex();
            return DungeonActivationCommitResult.success(
                    DungeonInstanceId.create(),
                    PortalSessionId.create()
            );
        }

        @Override
        public void afterCommitReady(
                DungeonPreparationJob job,
                DungeonActivationCommitResult result
        ) {
            this.afterReadyCalls++;
            if (this.throwAfterReady) {
                throw new IllegalStateException("synthetic after-ready failure");
            }
        }
    }

   private static final class FakeExecutionBackend
            implements DungeonPreparationExecutionBackend {
        final DungeonAsyncTestSupport.ControlledOwnerExecutor ownerExecutor =
                new DungeonAsyncTestSupport.ControlledOwnerExecutor();
        final List<DungeonSiteKey> candidates;
        final List<AsyncDungeonSiteProbe> createdScanners = new ArrayList<>();
        final List<Integer> createdScannerConcurrency = new ArrayList<>();
        final List<DungeonAsyncTestSupport.ControlledProbeBackend> probeBackends =
                new ArrayList<>();
        final List<FakeStartLease> leases = new ArrayList<>();
        final Map<DungeonSiteKey, DungeonPreparationLoadedSiteResult> loadedSites =
                new HashMap<>();
        final Map<DungeonSiteKey, String> reservationReasons = new HashMap<>();
       final Map<ChunkPos, Boolean> chunkLoaded = new HashMap<>();
       final List<ChunkPos> loadedChecks = new ArrayList<>();
        final List<DungeonSiteKey> reservationChecks = new ArrayList<>();
        final List<DungeonSiteKey> readLoadedCalls = new ArrayList<>();
        int findCandidateCalls;
        int lastRequestedLimit;
       int loggedRuntimeFailures;
       boolean tolerateRuntimeFailures;
       boolean safeEntryAvailable = true;
       long safeSpawnCandidatesBeforeResult = 1L;
       CountingSafeSpawnScan lastSafeSpawnScan;
       long gameTime = 100L;
       RuntimeException acquisitionFailure;
       boolean returnNullLease;
       RuntimeException claimReleaseFailure;
   boolean terminalBeforeAddLease;
   Runnable beforeReturnCallback;

        FakeExecutionBackend(List<DungeonSiteKey> candidates) {
            this.candidates = List.copyOf(candidates);
        }

        @Override
        public void assertOwnerThread() {
            if (!this.ownerExecutor.isOwnerThread()) {
                throw new IllegalStateException("fake backend off owner thread");
            }
        }

        @Override
        public long gameTime() {
            return this.gameTime++;
        }

        @Override
        public int maxCandidateCount() {
            return 6;
        }

        @Override
        public DungeonSiteCandidateCursor createCandidateCursor(
                DungeonPreparationRequest request,
                int requestedLimit
        ) {
            assertOwnerThread();
            this.findCandidateCalls++;
            this.lastRequestedLimit = requestedLimit;
            return new ListCandidateCursor(this.candidates);
        }

        @Override
        public AsyncDungeonSiteProbe createSiteProbe(
                List<DungeonSiteKey> candidates,
                ChunkStatus requiredStatus,
                int maxConcurrentProbes
        ) {
            assertOwnerThread();
            DungeonAsyncTestSupport.ControlledProbeBackend probeBackend =
                    new DungeonAsyncTestSupport.ControlledProbeBackend();
            AsyncDungeonSiteProbe scanner = new AsyncDungeonSiteProbe(
                    candidates,
                    requiredStatus,
                    maxConcurrentProbes,
                    probeBackend
            );
            this.probeBackends.add(probeBackend);
            this.createdScanners.add(scanner);
            this.createdScannerConcurrency.add(maxConcurrentProbes);
            return scanner;
        }

       @Override
       public DungeonPreparationStartChunkLease acquireStartChunk(DungeonSiteKey key) {
           assertOwnerThread();
           if (this.acquisitionFailure != null) {
               RuntimeException failure = this.acquisitionFailure;
               this.acquisitionFailure = null;
               throw failure;
           }
           if (this.returnNullLease) {
               this.returnNullLease = false;
               return null;
           }
           FakeStartLease lease = new FakeStartLease(key.toChunkPos());
           this.leases.add(lease);
           if (this.beforeReturnCallback != null) {
               Runnable callback = this.beforeReturnCallback;
               this.beforeReturnCallback = null;
               callback.run();
           }
           return lease;
       }

        @Override
        public DungeonPreparationStartChunkLease acquireEntryChunk(ChunkPos chunkPos) {
            assertOwnerThread();
            FakeStartLease lease = new FakeStartLease(chunkPos);
            this.leases.add(lease);
            return lease;
        }

        @Override
        public boolean isChunkLoaded(ChunkPos chunkPos) {
            assertOwnerThread();
            this.loadedChecks.add(chunkPos);
            return this.chunkLoaded.getOrDefault(chunkPos, true);
        }

        @Override
        public DungeonSafeSpawnScan createSafeEntryScan(DungeonSite site) {
            assertOwnerThread();
            this.lastSafeSpawnScan = new CountingSafeSpawnScan(
                    this.safeSpawnCandidatesBeforeResult,
                    this.safeEntryAvailable
            );
            return this.lastSafeSpawnScan;
        }

        @Override
        public DungeonPreparationLoadedSiteResult readLoadedSite(DungeonSiteKey key) {
            assertOwnerThread();
            this.readLoadedCalls.add(key);
            return this.loadedSites.getOrDefault(
                    key,
                    DungeonPreparationLoadedSiteResult.rejected(
                            DungeonPreparationJobFailureReason.STRUCTURE_START_MISSING,
                            "missing"
                    )
            );
        }

        @Override
        public String generatedReservationRejectionReason(DungeonSiteKey key) {
            assertOwnerThread();
            this.reservationChecks.add(key);
            return this.reservationReasons.getOrDefault(
                    key,
                    "candidate_accepted"
            );
        }

        @Override
        public void logJobRuntimeFailure(
                DungeonPreparationJob job,
                RuntimeException exception
        ) {
            if (this.tolerateRuntimeFailures) {
                this.loggedRuntimeFailures++;
                return;
            }
            throw new AssertionError(
                    "Unexpected executor runtime failure",
                    exception
            );
        }

        DungeonAsyncTestSupport.ControlledProbeBackend probeBackend(int index) {
            return this.probeBackends.get(index);
        }
    }

    private static void publicationRetryDoesNotRepeatCommittedTransaction() {
        int[] commitCalls = {0};
        int[] tributeConsumptions = {0};
        int[] readyNotifications = {0};
        DungeonPreparationCommitter committer = new DungeonPreparationCommitter() {
            @Override
            public DungeonActivationCommitResult commit(
                    DungeonPreparationJob job,
                    DungeonPreparationExecutionContext context
            ) {
                commitCalls[0]++;
                tributeConsumptions[0]++;
                return DungeonActivationCommitResult.success(
                        DungeonInstanceId.create(), PortalSessionId.create()
                );
            }

            @Override
            public void afterCommitReady(
                    DungeonPreparationJob job,
                    DungeonActivationCommitResult result
            ) {
                readyNotifications[0]++;
            }
        };
        Harness harness = Harness.withCandidatesAndCommitter(1, committer);
        harness.backend.tolerateRuntimeFailures = true;
        harness.advanceToReadyToCommit();

        harness.tick();
        DungeonPreparationExecutionContext context =
                harness.executor.ctx(harness.job.id());
        check(harness.job.stage() == DungeonPreparationStage.READY_TO_COMMIT,
                "publication retry: first tick is preflight only");
        check(context != null && context.commitPlan().isPresent(),
                "publication retry: immutable plan stored");
        check(commitCalls[0] == 0, "publication retry: no transaction on preflight tick");

        harness.tick();
        check(harness.job.stage() == DungeonPreparationStage.COMMITTING,
                "publication retry: failed publication remains committing");
        check(context.committedResult() != null,
                "publication retry: committed result retained");
        check(commitCalls[0] == 1 && tributeConsumptions[0] == 1,
                "publication retry: transaction and tribute executed once");

        harness.job.releaseAllLeases();
        harness.tick();
        check(harness.job.stage() == DungeonPreparationStage.READY,
                "publication retry: later publication reaches ready");
        check(commitCalls[0] == 1 && tributeConsumptions[0] == 1,
                "publication retry: transaction is never executed twice");
        check(readyNotifications[0] == 1,
                "publication retry: ready notification occurs once");
        check(harness.executor.ctx(harness.job.id()) == null,
                "publication retry: successful publication clears context");
    }

    private static void preflightFailureTerminatesWithoutCommit() {
        int[] commitCalls = {0};
        DungeonPreparationCommitter committer = new DungeonPreparationCommitter() {
            @Override
            public DungeonActivationPreflightResult preflight(
                    DungeonPreparationJob job,
                    DungeonPreparationExecutionContext context
            ) {
                return DungeonActivationPreflightResult.failure(
                        DungeonActivationCommitFailureReason.INVALID_TRIBUTE,
                        "synthetic preflight failure"
                );
            }

            @Override
            public DungeonActivationCommitResult commit(
                    DungeonPreparationJob job,
                    DungeonPreparationExecutionContext context
            ) {
                commitCalls[0]++;
                throw new AssertionError("commit must not run after failed preflight");
            }
        };
        Harness harness = Harness.withCandidatesAndCommitter(1, committer);
        harness.advanceToReadyToCommit();

        harness.tick();

        check(harness.job.stage() == DungeonPreparationStage.FAILED,
                "preflight failure: job failed");
        check(harness.job.failureReason()
                        == DungeonPreparationJobFailureReason.INVALID_TRIBUTE,
                "preflight failure: structured reason retained");
        check(commitCalls[0] == 0, "preflight failure: no mutation attempted");
    }

    private static final class CountingSafeSpawnScan
            implements DungeonSafeSpawnScan {
        private final long total;
        private final boolean findsResult;
        private long checked;
        private DungeonSafeSpawnScanState state = DungeonSafeSpawnScanState.RUNNING;

        private CountingSafeSpawnScan(long total, boolean findsResult) {
            this.total = total;
            this.findsResult = findsResult;
        }

        @Override
        public DungeonSafeSpawnScanResult advance(
                DungeonPreparationTickBudget budget,
                DungeonSafeSpawnScanPurpose purpose
        ) {
            while (this.state == DungeonSafeSpawnScanState.RUNNING
                    && this.checked < this.total
                    && budget.hasTimeRemaining()
                    && budget.tryConsumeSafeSpawnCandidate()) {
                this.checked++;
            }
            if (this.state == DungeonSafeSpawnScanState.RUNNING
                    && this.checked == this.total) {
                this.state = this.findsResult
                        ? DungeonSafeSpawnScanState.FOUND
                        : DungeonSafeSpawnScanState.EXHAUSTED;
            }
            return result();
        }

        @Override
        public DungeonSafeSpawnScanResult result() {
            return new DungeonSafeSpawnScanResult(
                    this.state,
                    this.checked,
                    this.total,
                    this.state == DungeonSafeSpawnScanState.FOUND
                            ? Optional.of(new Vec3(0, 64, 0))
                            : Optional.empty()
            );
        }

        @Override
        public void cancel() {
            if (this.state == DungeonSafeSpawnScanState.RUNNING) {
                this.state = DungeonSafeSpawnScanState.CANCELLED;
            }
        }
    }

    private static final class FakeClaimBackend
            implements DungeonSiteClaimBackend {
        boolean ownerThread = true;

        @Override
        public boolean isOwnerThread() {
            return this.ownerThread;
        }
    }

    private static final class FakeStartLease
            implements DungeonPreparationStartChunkLease {
        private final ChunkPos chunkPos;
        DungeonChunkLeaseState state = DungeonChunkLeaseState.PENDING;
        Optional<DungeonChunkLoadOutcome> outcome = Optional.empty();
        int closed;
        int stateReads;

        FakeStartLease(ChunkPos chunkPos) {
            this.chunkPos = chunkPos;
        }

        @Override
        public ChunkPos chunkPos() {
            return this.chunkPos;
        }

        @Override
        public DungeonChunkLeaseState state() {
            this.stateReads++;
            return this.state;
        }

        @Override
        public Optional<DungeonChunkLoadOutcome> outcome() {
            return this.outcome;
        }

        @Override
        public void close() {
            this.closed++;
            this.state = DungeonChunkLeaseState.CANCELLED;
        }
    }
}
