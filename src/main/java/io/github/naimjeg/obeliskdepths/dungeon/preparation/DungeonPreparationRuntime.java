package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.block.ObeliskBlock;
import io.github.naimjeg.obeliskdepths.block.ObeliskPart;
import io.github.naimjeg.obeliskdepths.dungeon.id.PortalSessionId;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonStatus;
import io.github.naimjeg.obeliskdepths.dungeon.portal.DungeonPortalEntityService;
import io.github.naimjeg.obeliskdepths.dungeon.portal.PortalSession;
import io.github.naimjeg.obeliskdepths.dungeon.portal.PortalSessionRemovalReason;
import io.github.naimjeg.obeliskdepths.dungeon.portal.PortalSessionRemovalResult;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.chunk.DungeonChunkLease;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.chunk.DungeonChunkLeaseManager;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.chunk.DungeonChunkLeaseManager.TerminalCleanupResult;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.chunk.DungeonChunkLeaseState;
import io.github.naimjeg.obeliskdepths.dungeon.site.*;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import io.github.naimjeg.obeliskdepths.menu.ObeliskPortalMenu;
import io.github.naimjeg.obeliskdepths.registry.ModBlocks;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

@EventBusSubscriber(modid = ObeliskDepths.MOD_ID)
public final class DungeonPreparationRuntime
        implements DungeonPreparationFailureMetrics {
    private static final WeakHashMap<ServerLevel, DungeonPreparationRuntime> LEVEL_RUNTIMES =
            new WeakHashMap<>();
    private static long terminalUnresolvedTicketDebt;
    private final ServerLevel level;
    private final DungeonPreparationJobRegistry jobRegistry;
    private final DungeonChunkLeaseManager leaseManager;
    private final DungeonSiteClaimManager claimManager;
    private final DungeonPreparedEntryRegistry preparedEntryRegistry;
    private final DungeonPreparationJobExecutor jobExecutor;
    private final Map<PortalSessionId, PreparedEntryRecoveryJob> recoveryJobs =
            new LinkedHashMap<>();
    private final Map<PortalSessionId, PostTeleportHandoff> postTeleportHandoffs =
            new HashMap<>();
    private final ArrayDeque<PortalSessionId> postTeleportHandoffOrder =
            new ArrayDeque<>();
    private boolean cleared;
    private int roundRobinCursor;
    private int recoveryRoundRobinCursor;
    private long preparedEntriesRemovedAsStale;
    private long preparedEntriesRemovedForMissingSession;
    private long preparedEntriesRemovedForMissingInstance;
    private long preparedEntriesRemovedForInactiveInstance;
    private long portalSessionsRemovedDuringReconciliation;
    private long preparedEntryCloseFailures;
    private long claimReleaseInvariantFailures;
    private long committedPublicationFailures;
    private long completedPostTeleportHandoffs;
    private long timedOutPostTeleportHandoffs;
    private long abortedPostTeleportHandoffs;
    private int highWaterActiveJobs;
    private int highWaterActiveLeases;

    private DungeonPreparationRuntime(ServerLevel level) {
        this.level = level;
        this.jobRegistry = new DungeonPreparationJobRegistry();
        this.leaseManager = DungeonChunkLeaseManager.createForLevel(level);
        this.claimManager = DungeonSiteClaimManager.createForLevel(level);
        this.preparedEntryRegistry = DungeonPreparedEntryRegistry.createForLevel(level);
        this.jobExecutor = new DungeonPreparationJobExecutor(
                this.jobRegistry,
                new ServerDungeonPreparationExecutionBackend(
                        level,
                        this.leaseManager
                ),
                this.claimManager,
                new DungeonPreparationCommitCoordinator(
                        level,
                        this.preparedEntryRegistry,
                        this.claimManager
                ),
                this
        );
        this.cleared = false;
        this.roundRobinCursor = 0;
        this.recoveryRoundRobinCursor = 0;
        this.highWaterActiveJobs = 0;
        this.highWaterActiveLeases = 0;
    }

    public static DungeonPreparationRuntime getOrCreate(ServerLevel level) {
        assertServerThread(level);
        DungeonPreparationRuntime runtime =
                LEVEL_RUNTIMES.computeIfAbsent(level, DungeonPreparationRuntime::new);
        runtime.assertUsable();
        return runtime;
    }

    public static DungeonPreparationRuntime get(ServerLevel level) {
        assertServerThread(level);
        DungeonPreparationRuntime runtime = LEVEL_RUNTIMES.get(level);
        if (runtime != null && runtime.cleared) {
            LEVEL_RUNTIMES.remove(level, runtime);
            return null;
        }
        return runtime;
    }

    DungeonPreparationJobRegistry jobRegistry() {
        assertUsable();
        return this.jobRegistry;
    }

    DungeonChunkLeaseManager leaseManager() {
        assertUsable();
        return this.leaseManager;
    }

    DungeonSiteClaimManager claimManager() {
        assertUsable();
        return this.claimManager;
    }

    DungeonPreparedEntryRegistry preparedEntryRegistry() {
        assertUsable();
        return this.preparedEntryRegistry;
    }

    public DungeonPreparationSubmission submit(DungeonPreparationRequest request) {
        assertOwnerThread();
        Objects.requireNonNull(request, "request");
        if (this.cleared) {
            return DungeonPreparationSubmission.rejected(
                    DungeonPreparationSubmissionRejectionReason.RUNTIME_CLEARED,
                    null,
                    "preparation runtime has been cleared"
            );
        }
        if (this.jobRegistry.activeCount()
                >= DungeonPreparationLimits.MAX_ACTIVE_PREPARATION_JOBS_PER_LEVEL) {
            return DungeonPreparationSubmission.rejected(
                    DungeonPreparationSubmissionRejectionReason.ACTIVE_JOB_LIMIT,
                    null,
                    "active preparation job limit reached"
            );
        }

        DungeonPreparationJob job = new DungeonPreparationJob(
                DungeonPreparationJobId.create(),
                request,
                this.level.getGameTime()
        );
        DungeonPreparationJobRegistry.SubmissionResult result =
                this.jobRegistry.submit(job);
        if (!result.isAccepted()) {
            return DungeonPreparationSubmission.rejected(
                    submissionRejectionReason(result.rejectionReason()),
                    result.conflictingJobId(),
                    result.rejectionReason().name()
            );
        }

        this.jobExecutor.createContext(job);
        return DungeonPreparationSubmission.accepted(job.id());
    }

    Optional<DungeonSite> resolvedSiteFor(DungeonPreparationJobId id) {
        assertUsable();
        return this.jobExecutor.resolvedSiteFor(id);
    }

    List<DungeonSiteKey> generationCandidatesFor(DungeonPreparationJobId id) {
        assertUsable();
        return this.jobExecutor.generationCandidatesFor(id);
    }

    public void tick(ServerLevel level) {
        tick(level, DungeonPreparationTickBudget.perLevelTick());
    }

    public void tick(
            ServerLevel level,
            DungeonPreparationTickBudget budget
    ) {
        assertOwnerLevel(level);
        assertUsable();
        Objects.requireNonNull(budget, "budget");
        DungeonPreparationProfiling.run(
                DungeonPreparationProfiler.global(),
                DungeonPreparationProfiler.Operation.RUNTIME_TICK,
                () -> true,
                "Preparation runtime tick",
                () -> tickProfiled(level, budget)
        );
    }

    private void tickProfiled(
            ServerLevel level,
            DungeonPreparationTickBudget budget
    ) {
        long gameTime = level.getGameTime();
        if (budget.hasTimeRemaining()) {
            this.leaseManager.retryPendingReleases(
                    DungeonPreparationLimits
                            .PENDING_TICKET_RELEASE_RETRIES_PER_LEVEL_TICK,
                    budget::hasTimeRemaining
            );
        }
        cancelInvalidActiveJobs(gameTime);

        List<DungeonPreparationJob> activeJobs =
                this.jobRegistry.activeJobsSnapshot();
        updateHighWaterMetrics();
        if (!activeJobs.isEmpty()) {
            int size = activeJobs.size();
            int start = Math.floorMod(this.roundRobinCursor, size);
            int processed = runRoundRobin(
                    size,
                    start,
                    budget::hasTimeRemaining,
                    index -> this.jobExecutor.tick(activeJobs.get(index), budget)
            );
            this.roundRobinCursor = nextRoundRobinCursor(
                    start,
                    processed,
                    size
            );
        }
        tickRecoveryJobs(gameTime, budget);
        updateHighWaterMetrics();
        this.jobRegistry.purgeTerminal(
                gameTime,
                DungeonPreparationJobRegistry.TERMINAL_RETENTION_TICKS
        );
    }

    public Optional<DungeonPreparationJobSnapshot> snapshot(
            DungeonPreparationJobId id
    ) {
        assertUsable();
        Objects.requireNonNull(id, "id");
        return this.jobRegistry.findById(id)
                .map(DungeonPreparationJob::snapshot);
    }

    public Optional<DungeonPreparationProgressSnapshot> progressSnapshot(
            DungeonPreparationJobId id
    ) {
        assertUsable();
        Objects.requireNonNull(id, "id");
        return this.jobExecutor.progressSnapshot(id);
    }

    public Optional<DungeonPreparationJobSnapshot> activeSnapshotForPlayer(
            UUID playerId
    ) {
        assertUsable();
        Objects.requireNonNull(playerId, "playerId");
        return this.jobRegistry.findActiveByPlayer(playerId)
                .map(DungeonPreparationJob::snapshot);
    }

    public Optional<DungeonPreparedPortalEntry> preparedPortalEntry(
            PortalSessionId portalSessionId
    ) {
        assertUsable();
        return this.preparedEntryRegistry.get(portalSessionId);
    }

    /**
     * Transfers a prepared entry to a bounded owner-thread handoff. The entry
     * stays registered and keeps its leases until vanilla player chunk
     * tracking has sent the destination chunk.
     */
    public void beginPostTeleportHandoff(
            PortalSessionId portalSessionId,
            DungeonPreparedPortalEntry expectedEntry,
            ServerPlayer teleportedPlayer
    ) {
        assertUsable();
        Objects.requireNonNull(portalSessionId, "portalSessionId");
        Objects.requireNonNull(expectedEntry, "expectedEntry");
        Objects.requireNonNull(teleportedPlayer, "teleportedPlayer");
        if (expectedEntry.isClosed()
                || this.preparedEntryRegistry.get(portalSessionId)
                .orElse(null) != expectedEntry) {
            throw new IllegalStateException(
                    "Post-teleport handoff requires the exact registered entry"
            );
        }
        if (this.postTeleportHandoffs.containsKey(portalSessionId)) {
            throw new IllegalStateException(
                    "Portal session already has a post-teleport handoff"
            );
        }

        ServerPlayer listedPlayer = this.level.getServer().getPlayerList()
                .getPlayer(teleportedPlayer.getUUID());
        BlockPos destinationBlock = BlockPos.containing(
                expectedEntry.destination().position()
        );
        PostTeleportHandoff handoff = new PostTeleportHandoff(
                portalSessionId,
                expectedEntry,
                teleportedPlayer,
                listedPlayer == teleportedPlayer,
                new ChunkPos(
                        Math.floorDiv(destinationBlock.getX(), 16),
                        Math.floorDiv(destinationBlock.getZ(), 16)
                ),
                this.level.getGameTime()
        );
        this.postTeleportHandoffs.put(portalSessionId, handoff);
        this.postTeleportHandoffOrder.addLast(portalSessionId);
    }

    public boolean isPostTeleportHandoffActive(PortalSessionId portalSessionId) {
        assertUsable();
        Objects.requireNonNull(portalSessionId, "portalSessionId");
        return this.postTeleportHandoffs.containsKey(portalSessionId);
    }

    /** Runs a fixed, fair owner-thread batch independent of the job budget. */
    public void tickPostTeleportHandoffs(long gameTime) {
        assertUsable();
        int maximum = DungeonPreparationLimits
                .POST_TELEPORT_HANDOFFS_PER_LEVEL_TICK;
        int attempts = Math.min(
                maximum,
                this.postTeleportHandoffOrder.size()
        );
        Error firstError = null;

        for (int i = 0; i < attempts; i++) {
            PortalSessionId sessionId = this.postTeleportHandoffOrder.removeFirst();
            PostTeleportHandoff handoff = this.postTeleportHandoffs.get(sessionId);
            if (handoff == null) {
                continue;
            }

            PostTeleportHandoffDecision decision = evaluatePostTeleportHandoff(
                    handoff,
                    gameTime
            );
            if (decision == PostTeleportHandoffDecision.WAIT) {
                this.postTeleportHandoffOrder.addLast(sessionId);
                continue;
            }

            this.postTeleportHandoffs.remove(sessionId, handoff);
            try {
                closePostTeleportHandoff(handoff, decision, gameTime);
            } catch (RuntimeException failure) {
                this.preparedEntryCloseFailures++;
                logPostTeleportHandoffFailure(handoff, decision, failure);
            } catch (Error error) {
                if (firstError == null) {
                    firstError = error;
                } else if (firstError != error) {
                    firstError.addSuppressed(error);
                }
            }
        }

        if (firstError != null) {
            throw firstError;
        }
    }

    private PostTeleportHandoffDecision evaluatePostTeleportHandoff(
            PostTeleportHandoff handoff,
            long gameTime
    ) {
        ServerPlayer player = handoff.player();
        boolean samePlayer = handoff.requirePlayerListIdentity()
                ? this.level.getServer().getPlayerList()
                .getPlayer(player.getUUID()) == player
                : this.level.players().stream().anyMatch(candidate -> candidate == player);
        boolean inDestinationLevel = player.level() == this.level;
        boolean inDestinationChunk = inDestinationLevel
                && player.chunkPosition().equals(handoff.destinationChunk());
        boolean entryChunksAvailable = inDestinationLevel
                && handoff.entry().entryChunks().stream().allMatch(chunkPos ->
                this.level.getChunkSource().getChunkNow(
                        chunkPos.x(), chunkPos.z()
                ) != null
        );
        boolean destinationTracked = inDestinationChunk
                && this.level.getChunkSource().chunkMap.isChunkTracked(
                player,
                handoff.destinationChunk().x(),
                handoff.destinationChunk().z()
        );
        return postTeleportHandoffDecision(
                samePlayer && !player.isRemoved(),
                player.isAlive(),
                inDestinationLevel,
                inDestinationChunk,
                entryChunksAvailable,
                destinationTracked,
                gameTime - handoff.startedAtGameTime()
        );
    }

    static PostTeleportHandoffDecision postTeleportHandoffDecision(
            boolean sameConnectedPlayer,
            boolean alive,
            boolean inDestinationLevel,
            boolean inDestinationChunk,
            boolean entryChunksAvailable,
            boolean destinationTracked,
            long elapsedTicks
    ) {
        if (!sameConnectedPlayer || !alive) {
            return PostTeleportHandoffDecision.PLAYER_UNAVAILABLE;
        }
        if (!inDestinationLevel || !inDestinationChunk) {
            return PostTeleportHandoffDecision.PLAYER_LEFT_DESTINATION;
        }
        if (entryChunksAvailable && destinationTracked) {
            return PostTeleportHandoffDecision.TRACKING_ESTABLISHED;
        }
        if (elapsedTicks >= DungeonPreparationLimits
                .POST_TELEPORT_HANDOFF_TIMEOUT_TICKS) {
            return PostTeleportHandoffDecision.TIMED_OUT;
        }
        return PostTeleportHandoffDecision.WAIT;
    }

    private void closePostTeleportHandoff(
            PostTeleportHandoff handoff,
            PostTeleportHandoffDecision decision,
            long gameTime
    ) {
        switch (decision) {
            case TRACKING_ESTABLISHED -> this.completedPostTeleportHandoffs++;
            case TIMED_OUT -> this.timedOutPostTeleportHandoffs++;
            case PLAYER_UNAVAILABLE, PLAYER_LEFT_DESTINATION ->
                    this.abortedPostTeleportHandoffs++;
            case WAIT -> throw new IllegalStateException(
                    "Cannot close a waiting post-teleport handoff"
            );
        }
        boolean removed = this.preparedEntryRegistry.removeAndCloseExact(
                handoff.portalSessionId(), handoff.entry()
        );
        if (!removed && !handoff.entry().isClosed()) {
            // The captured object can never be a replacement registry entry.
            handoff.entry().close();
        }

        try {
            ObeliskDepths.LOGGER.debug(
                    "Post-teleport prepared-entry handoff completed: session={}, player={}, result={}, elapsedTicks={}, activeLeases={}, pendingReleases={}",
                    handoff.portalSessionId(),
                    handoff.player().getUUID(),
                    decision,
                    gameTime - handoff.startedAtGameTime(),
                    this.leaseManager.activeLeaseCount(),
                    this.leaseManager.pendingReleaseCount()
            );
        } catch (RuntimeException ignoredDiagnosticFailure) {
            // Handoff completion is authoritative; diagnostics are observational.
        }
    }

    private static void logPostTeleportHandoffFailure(
            PostTeleportHandoff handoff,
            PostTeleportHandoffDecision decision,
            RuntimeException failure
    ) {
        try {
            ObeliskDepths.LOGGER.error(
                    "Post-teleport prepared-entry cleanup failed: session={}, player={}, result={}",
                    handoff.portalSessionId(),
                    handoff.player().getUUID(),
                    decision,
                    failure
            );
        } catch (RuntimeException ignoredDiagnosticFailure) {
            // Cleanup accounting must not depend on diagnostic logging.
        }
    }

    private void cancelPostTeleportHandoff(PortalSessionId portalSessionId) {
        if (this.postTeleportHandoffs.remove(portalSessionId) != null) {
            this.postTeleportHandoffOrder.remove(portalSessionId);
        }
    }

    public Optional<DungeonPreparedPortalEntry> removeAndClosePreparedEntry(
            PortalSessionId portalSessionId
    ) {
        assertUsable();
        Objects.requireNonNull(portalSessionId, "portalSessionId");
        RuntimeException ordinaryFailure = null;
        Error cleanupError = null;
        try {
            cancelPostTeleportHandoff(portalSessionId);
            cancelPreparedEntryRecovery(portalSessionId);
        } catch (RuntimeException failure) {
            ordinaryFailure = failure;
        } catch (Error error) {
            cleanupError = error;
        }

        Optional<DungeonPreparedPortalEntry> removed = Optional.empty();
        try {
            removed = this.preparedEntryRegistry.removeAndClose(
                    portalSessionId
            );
        } catch (RuntimeException failure) {
            if (cleanupError != null) {
                cleanupError.addSuppressed(failure);
            } else if (ordinaryFailure == null) {
                ordinaryFailure = failure;
            } else if (ordinaryFailure != failure) {
                ordinaryFailure.addSuppressed(failure);
            }
        } catch (Error error) {
            if (cleanupError == null) {
                cleanupError = error;
                if (ordinaryFailure != null) {
                    cleanupError.addSuppressed(ordinaryFailure);
                    ordinaryFailure = null;
                }
            } else if (cleanupError != error) {
                cleanupError.addSuppressed(error);
            }
        }

        if (cleanupError != null) {
            throw cleanupError;
        }
        if (ordinaryFailure != null) {
            throw ordinaryFailure;
        }
        return removed;
    }

    public boolean removeAndClosePreparedEntryExact(
            PortalSessionId portalSessionId,
            DungeonPreparedPortalEntry expectedEntry
    ) {
        assertUsable();
        Objects.requireNonNull(portalSessionId, "portalSessionId");
        Objects.requireNonNull(expectedEntry, "expectedEntry");
        PostTeleportHandoff handoff = this.postTeleportHandoffs.get(
                portalSessionId
        );
        if (handoff != null && handoff.entry() == expectedEntry) {
            cancelPostTeleportHandoff(portalSessionId);
        }
        return this.preparedEntryRegistry.removeAndCloseExact(
                portalSessionId,
                expectedEntry
        );
    }

    public DungeonPreparedEntryRecoveryStatus submitOrReusePreparedEntryRecovery(
            PortalSession session,
            DungeonInstance instance,
            DungeonSite site
    ) {
        assertUsable();
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(instance, "instance");
        Objects.requireNonNull(site, "site");

        long gameTime = this.level.getGameTime();
        if (session.isExpired(gameTime)
                || instance.status() != DungeonStatus.ACTIVE
                || !session.instanceId().equals(instance.id())
                || !site.key().equals(instance.siteKey())) {
            return DungeonPreparedEntryRecoveryStatus.REJECTED;
        }

        if (this.postTeleportHandoffs.containsKey(session.id())) {
            return DungeonPreparedEntryRecoveryStatus.ALREADY_PREPARED;
        }

        Optional<DungeonPreparedPortalEntry> prepared =
                this.preparedEntryRegistry.get(session.id());
        if (prepared.isPresent()
                && !prepared.get().isClosed()
                && !prepared.get().isStale(gameTime)
                && prepared.get().instanceId().equals(instance.id())
                && prepared.get().siteKey().equals(instance.siteKey())) {
            return DungeonPreparedEntryRecoveryStatus.ALREADY_PREPARED;
        }

        DungeonPreparedEntryRecoveryStatus admission = recoveryAdmission(
                this.recoveryJobs.containsKey(session.id()),
                this.recoveryJobs.size()
        );
        if (admission != DungeonPreparedEntryRecoveryStatus.STARTED) {
            return admission;
        }

        DungeonManagerSavedData data = DungeonManagerSavedData.get(this.level);
        Optional<DungeonSite> snapshot = data.requireReservedDungeon(
                instance.id(),
                instance.siteKey()
        ).map(io.github.naimjeg.obeliskdepths.dungeon.state.ReservedDungeonAggregate::site);
        if (data.portalSessions().get(session.id()).isEmpty()
                || data.instances().get(instance.id()).isEmpty()
                || snapshot.filter(site::equals).isEmpty()) {
            return DungeonPreparedEntryRecoveryStatus.REJECTED;
        }

        this.recoveryJobs.put(
                session.id(),
                new PreparedEntryRecoveryJob(
                        session.id(),
                        instance.id(),
                        site,
                        gameTime
                )
        );
        return DungeonPreparedEntryRecoveryStatus.STARTED;
    }

    public boolean isPreparedEntryRecoveryActive(PortalSessionId portalSessionId) {
        assertUsable();
        return this.recoveryJobs.containsKey(portalSessionId);
    }

    /**
     * Cancels only the transient recovery job for one portal session.
     * Existing prepared entries and post-teleport handoffs are untouched.
     */
    public void cancelPreparedEntryRecoveryForSession(
            PortalSessionId portalSessionId
    ) {
        assertUsable();
        Objects.requireNonNull(portalSessionId, "portalSessionId");
        cancelPreparedEntryRecovery(portalSessionId);
    }

    public int reconcilePreparedEntries(
            DungeonManagerSavedData data,
            long gameTime,
            int maximumEntries,
            BooleanSupplier hasTimeRemaining
    ) {
        assertUsable();
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(hasTimeRemaining, "hasTimeRemaining");
        if (maximumEntries < 0) {
            throw new IllegalArgumentException(
                    "maximumEntries must be non-negative"
            );
        }
        List<DungeonPreparedPortalEntry> entries =
                this.preparedEntryRegistry.nextMaintenanceBatch(maximumEntries);
        if (entries.isEmpty() || maximumEntries == 0) {
            return 0;
        }

        List<PreparedEntryReconciliationAction> actions = new ArrayList<>();
        for (DungeonPreparedPortalEntry entry : entries) {
            if (!hasTimeRemaining.getAsBoolean()) {
                break;
            }
            PreparedEntryRemovalReason reason =
                    removalReasonForPreparedEntry(data, entry, gameTime);
            if (reason == null) {
                continue;
            }
            actions.add(new PreparedEntryReconciliationAction(
                    entry.portalSessionId(),
                    entry,
                    reason
            ));
        }

        int portalSessionsRemoved = 0;
        int entriesRemoved = 0;

        for (PreparedEntryReconciliationAction action : actions) {
            if (!hasTimeRemaining.getAsBoolean()) {
                break;
            }
            try {
                if (isWholeSessionReason(action.reason())) {
                    if (this.preparedEntryRegistry
                            .get(action.portalSessionId())
                            .orElse(null) != action.expectedEntry()) {
                        continue;
                    }
                    PortalSessionRemovalResult result =
                            io.github.naimjeg.obeliskdepths.dungeon.portal.DungeonPortalSessionLifecycle
                                    .removeWithResult(
                                            this.level,
                                            action.portalSessionId(),
                                            toPortalSessionRemovalReason(action.reason())
                                    );
                    if (result.sessionRemoved()) {
                        portalSessionsRemoved++;
                        this.portalSessionsRemovedDuringReconciliation++;
                    }
                    if (!result.cleanupFailures().isEmpty()) {
                        this.preparedEntryCloseFailures++;
                    }
                    recordPreparedEntryRemoval(action.reason());
                    ObeliskDepths.LOGGER.debug(
                            "Prepared entry removed during reconciliation: session={}, reason={}",
                            action.portalSessionId(),
                            action.reason()
                    );
                } else {
                    if (removeAndClosePreparedEntryExact(
                            action.portalSessionId(),
                            action.expectedEntry()
                    )) {
                        entriesRemoved++;
                        recordPreparedEntryRemoval(action.reason());
                        ObeliskDepths.LOGGER.debug(
                                "Prepared entry removed during reconciliation: session={}, reason={}",
                                action.portalSessionId(),
                                action.reason()
                        );
                    }
                }
            } catch (RuntimeException exception) {
                ObeliskDepths.LOGGER.error(
                        "Prepared entry reconciliation failed for session={}, reason={}",
                        action.portalSessionId(),
                        action.reason(),
                        exception
                );
                this.preparedEntryCloseFailures++;
            }
        }

        return entriesRemoved + portalSessionsRemoved;
    }


    public DungeonPreparationRuntimeMetrics metricsSnapshot() {
        assertUsable();
        updateHighWaterMetrics();
        return new DungeonPreparationRuntimeMetrics(
                this.jobRegistry.activeCount(),
                this.leaseManager.activeLeaseCount(),
                this.leaseManager.pendingReleaseCount(),
                this.leaseManager.pendingReleaseFailureCount(),
                terminalUnresolvedTicketDebt,
                this.preparedEntryRegistry.size(),
                this.preparedEntryRegistry.preparedEntryChunkCount(),
                this.preparedEntriesRemovedAsStale,
                this.preparedEntriesRemovedForMissingSession,
                this.preparedEntriesRemovedForMissingInstance,
                this.preparedEntriesRemovedForInactiveInstance,
                this.portalSessionsRemovedDuringReconciliation,
                this.preparedEntryCloseFailures,
                this.claimReleaseInvariantFailures,
                this.committedPublicationFailures,
                this.postTeleportHandoffs.size(),
                this.completedPostTeleportHandoffs,
                this.timedOutPostTeleportHandoffs,
                this.abortedPostTeleportHandoffs,
                this.recoveryJobs.size(),
                this.jobExecutor.activePersistedScannerCount(),
                this.jobExecutor.pendingScannerCompletionCount(),
                this.highWaterActiveJobs,
                this.highWaterActiveLeases
        );
    }

    private void updateHighWaterMetrics() {
        this.highWaterActiveJobs = Math.max(
                this.highWaterActiveJobs,
                this.jobRegistry.activeCount()
        );
        this.highWaterActiveLeases = Math.max(
                this.highWaterActiveLeases,
                this.leaseManager.activeLeaseCount()
        );
    }

    public void cancelJobsForPlayer(
            UUID playerId,
            DungeonPreparationCancellationReason reason,
            String detail,
            long gameTime
    ) {
        assertUsable();
        this.jobRegistry.findActiveByPlayer(playerId)
                .ifPresent(job -> cancelJob(job, reason, detail, gameTime));
    }

    public void cancelJobsForPlayerOutsideSourceDimension(
            UUID playerId,
            ResourceKey<Level> currentDimension,
            DungeonPreparationCancellationReason reason,
            String detail,
            long gameTime
    ) {
        assertUsable();
        this.jobRegistry.findActiveByPlayer(playerId)
                .filter(job -> !job.request().sourceDimension().equals(currentDimension))
                .ifPresent(job -> cancelJob(job, reason, detail, gameTime));
    }

    public TerminalCleanupResult clear(
            DungeonPreparationCancellationReason reason,
            String detail,
            long gameTime
    ) {
        assertOwnerThread();
        if (this.cleared) {
            return this.leaseManager.lastTerminalCleanupResult();
        }
        this.cleared = true;
        CleanupFailures failures = new CleanupFailures();
        TerminalCleanupResult[] ticketResult = new TerminalCleanupResult[1];
        try {
            failures.run(
                    "Failed to cancel preparation execution contexts",
                    () -> this.jobExecutor.cancelAllContexts()
            );
            failures.run(
                    "Failed to cancel preparation jobs",
                    () -> this.jobRegistry.clearAllActive(
                            reason, detail, gameTime
                    )
            );
            failures.run(
                    "Failed to cancel prepared-entry recovery jobs",
                    () -> clearRecoveryJobs()
            );
            failures.run(
                    "Failed to clear post-teleport handoffs",
                    () -> {
                        this.postTeleportHandoffs.clear();
                        this.postTeleportHandoffOrder.clear();
                    }
            );
            failures.run(
                    "Failed to clear prepared portal entries",
                    () -> this.preparedEntryRegistry.clearAll()
            );
            failures.run(
                    "Failed to clear candidate claims",
                    () -> this.claimManager.clearAll()
            );
            failures.run(
                    "Failed to clear preparation chunk leases",
                    () -> {
                        try {
                            ticketResult[0] = this.leaseManager.clear();
                        } finally {
                            ticketResult[0] =
                                    this.leaseManager.lastTerminalCleanupResult();
                        }
                    }
            );
        } finally {
            reportTerminalTicketDebt(ticketResult[0]);
            LEVEL_RUNTIMES.remove(this.level, this);
        }
        failures.throwIfPresent();
        return ticketResult[0];
    }

    private void reportTerminalTicketDebt(TerminalCleanupResult result) {
        if (result == null || result.unresolvedCount() == 0) {
            return;
        }
        terminalUnresolvedTicketDebt += result.unresolvedCount();
        try {
            ObeliskDepths.LOGGER.error(
                    "Dungeon preparation runtime discarded with unresolved physical ticket cleanup debt: dimension={}, unresolved={}, attempts={}, passes={}, positions={}",
                    this.level.dimension().identifier(),
                    result.unresolvedCount(),
                    result.attempts(),
                    result.passes(),
                    result.unresolvedPositions()
            );
        } catch (RuntimeException ignoredDiagnosticFailure) {
            // Terminal diagnostics are observational and must not skip removal.
        }
    }

    static long terminalUnresolvedTicketDebt() {
        return terminalUnresolvedTicketDebt;
    }

    public static void clearAllOnServerStopping() {
        Set<DungeonPreparationRuntime> runtimes =
                new LinkedHashSet<>(new ArrayList<>(LEVEL_RUNTIMES.values()));

        CleanupFailures failures = new CleanupFailures();
        try {
            for (DungeonPreparationRuntime runtime : runtimes) {
                if (runtime == null) {
                    continue;
                }
                failures.run(
                        "Failed to clear preparation runtime during server stop",
                        () -> runtime.clear(
                            DungeonPreparationCancellationReason.SERVER_STOPPING,
                            "server stopping",
                            runtime.level.getGameTime()
                        )
                );
            }
        } finally {
            // All bounded terminal drains have completed; retain no unloaded level.
            LEVEL_RUNTIMES.clear();
        }

        try {
            failures.throwIfPresent();
        } catch (RuntimeException aggregateFailure) {
            ObeliskDepths.LOGGER.error(
                    "Failed to clear one or more dungeon preparation runtimes during server stop",
                    aggregateFailure
            );
        }
    }

    private static RuntimeException appendFailure(
            RuntimeException aggregateFailure,
            String message,
            RuntimeException exception
    ) {
        RuntimeException result = aggregateFailure;
        if (result == null) {
            result = new IllegalStateException(message);
        }
        result.addSuppressed(exception);
        return result;
    }

    private static final class CleanupFailures {
        private RuntimeException ordinaryFailures;
        private Error firstError;

        void run(String detail, Runnable cleanup) {
            try {
                cleanup.run();
            } catch (RuntimeException failure) {
                capture(detail, failure);
            } catch (Error error) {
                capture(detail, error);
            }
        }

        private void capture(String detail, Throwable failure) {
            if (failure instanceof Error error) {
                if (this.firstError == null) {
                    this.firstError = error;
                    if (this.ordinaryFailures != null) {
                        this.firstError.addSuppressed(this.ordinaryFailures);
                        this.ordinaryFailures = null;
                    }
                } else if (this.firstError != error) {
                    this.firstError.addSuppressed(error);
                }
                return;
            }
            IllegalStateException contextual =
                    new IllegalStateException(detail, failure);
            if (this.firstError != null) {
                this.firstError.addSuppressed(contextual);
                return;
            }
            if (this.ordinaryFailures == null) {
                this.ordinaryFailures = new IllegalStateException(
                        "One or more preparation runtime cleanup steps failed"
                );
            }
            this.ordinaryFailures.addSuppressed(contextual);
        }

        void throwIfPresent() {
            if (this.firstError != null) {
                throw this.firstError;
            }
            if (this.ordinaryFailures != null) {
                throw this.ordinaryFailures;
            }
        }
    }

    private void cancelJob(
            DungeonPreparationJob job,
            DungeonPreparationCancellationReason reason,
            String detail,
            long gameTime
    ) {
        this.jobExecutor.cancelContext(job.id());
        this.jobRegistry.cancel(job.id(), reason, detail, gameTime);
    }

    static int nextRoundRobinCursor(int start, int processed, int size) {
        if (size <= 0 || processed < 0 || processed > size) {
            throw new IllegalArgumentException("invalid round-robin progress");
        }
        if (processed == 0) {
            return Math.floorMod(start, size);
        }
        int advance = processed < size ? processed : 1;
        return (int)Math.floorMod((long)start + advance, size);
    }

    static int runRoundRobin(
            int size,
            int start,
            BooleanSupplier hasTimeRemaining,
            IntConsumer processor
    ) {
        return runBoundedRoundRobin(
                size, start, size, hasTimeRemaining, processor
        );
    }

    static int runBoundedRoundRobin(
            int size,
            int start,
            int maximumEntries,
            BooleanSupplier hasTimeRemaining,
            IntConsumer processor
    ) {
        Objects.requireNonNull(hasTimeRemaining, "hasTimeRemaining");
        Objects.requireNonNull(processor, "processor");
        if (size < 0) {
            throw new IllegalArgumentException("size must be non-negative");
        }
        if (maximumEntries < 0) {
            throw new IllegalArgumentException(
                    "maximumEntries must be non-negative"
            );
        }
        if (size == 0) {
            return 0;
        }
        int normalizedStart = Math.floorMod(start, size);
        int processed = 0;
        int limit = Math.min(size, maximumEntries);
        while (processed < limit && hasTimeRemaining.getAsBoolean()) {
            processor.accept((int)(((long)normalizedStart + processed) % size));
            processed++;
        }
        return processed;
    }

    static DungeonPreparedEntryRecoveryStatus recoveryAdmission(
            boolean existingSession,
            int activeJobs
    ) {
        if (activeJobs < 0) {
            throw new IllegalArgumentException("activeJobs must be non-negative");
        }
        if (existingSession) {
            return DungeonPreparedEntryRecoveryStatus.REUSED;
        }
        return activeJobs
                >= DungeonPreparationLimits.MAX_ACTIVE_RECOVERY_JOBS_PER_LEVEL
                ? DungeonPreparedEntryRecoveryStatus.REJECTED
                : DungeonPreparedEntryRecoveryStatus.STARTED;
    }

    private void tickRecoveryJobs(
            long gameTime,
            DungeonPreparationTickBudget budget
    ) {
        if (this.recoveryJobs.isEmpty()) {
            return;
        }
        List<PreparedEntryRecoveryJob> jobs =
                new ArrayList<>(this.recoveryJobs.values());
        int size = jobs.size();
        int start = Math.floorMod(this.recoveryRoundRobinCursor, size);
        int processed = runRoundRobin(size, start, budget::hasTimeRemaining, index -> {
            PreparedEntryRecoveryJob job = jobs.get(index);
            if (!this.recoveryJobs.containsKey(job.portalSessionId())) {
                return;
            }
            try {
                if (job.tick(this, gameTime, budget).done()) {
                    this.recoveryJobs.remove(job.portalSessionId(), job);
                }
            } catch (RuntimeException failure) {
                failRecoveryJob(job, failure);
            } catch (Error error) {
                this.recoveryJobs.remove(job.portalSessionId(), job);
                try {
                    job.close();
                } catch (RuntimeException | Error cleanupFailure) {
                    if (cleanupFailure != error) {
                        error.addSuppressed(cleanupFailure);
                    }
                }
                throw error;
            }
        });
        this.recoveryRoundRobinCursor = nextRoundRobinCursor(
                start,
                processed,
                size
        );
    }

    private void failRecoveryJob(
            PreparedEntryRecoveryJob job,
            RuntimeException failure
    ) {
        this.recoveryJobs.remove(job.portalSessionId(), job);
        try {
            job.close();
        } catch (RuntimeException cleanupFailure) {
            if (cleanupFailure != failure) {
                failure.addSuppressed(cleanupFailure);
            }
        } catch (Error cleanupError) {
            cleanupError.addSuppressed(failure);
            throw cleanupError;
        }
        ObeliskDepths.LOGGER.error(
                "Prepared-entry recovery failed and was cancelled: session={}",
                job.portalSessionId(),
                failure
        );
    }

    private void cancelPreparedEntryRecovery(PortalSessionId portalSessionId) {
        PreparedEntryRecoveryJob job = this.recoveryJobs.remove(portalSessionId);
        if (job != null) {
            job.close();
        }
    }

    private void clearRecoveryJobs() {
        List<AutoCloseable> jobs =
                new ArrayList<>(this.recoveryJobs.values());
        this.recoveryJobs.clear();
        new DungeonPreparationLeaseBundle(jobs).close();
    }

    private void cancelInvalidActiveJobs(long gameTime) {
        List<DungeonPreparationJob> activeJobs =
                this.jobRegistry.activeJobsSnapshot();
        for (DungeonPreparationJob job : activeJobs) {
            if (job.stage() == DungeonPreparationStage.COMMITTING) {
                continue;
            }
            if (gameTime - job.createdAtGameTime()
                    >= DungeonPreparationLimits.JOB_TIMEOUT_TICKS) {
                cancelJob(
                        job,
                        DungeonPreparationCancellationReason.TIMEOUT,
                        "preparation timed out",
                        gameTime
                );
                continue;
            }
            Optional<CancellationCheck> cancellation =
                    cancellationFor(job.request());
            cancellation.ifPresent(check -> cancelJob(
                    job,
                    check.reason(),
                    check.detail(),
                    gameTime
            ));
        }
    }

    private Optional<CancellationCheck> cancellationFor(
            DungeonPreparationRequest request
    ) {
        ServerPlayer player = this.level.getServer()
                .getPlayerList()
                .getPlayer(request.playerId());
        if (player == null) {
            return Optional.of(new CancellationCheck(
                    DungeonPreparationCancellationReason.PLAYER_DISCONNECTED,
                    "player disconnected"
            ));
        }
        if (player.isDeadOrDying()) {
            return Optional.of(new CancellationCheck(
                    DungeonPreparationCancellationReason.PLAYER_DIED,
                    "player died or is dying"
            ));
        }
        ServerLevel sourceLevel = this.level.getServer()
                .getLevel(request.sourceDimension());
        if (sourceLevel == null
                || !player.level().dimension().equals(request.sourceDimension())) {
            return Optional.of(new CancellationCheck(
                    DungeonPreparationCancellationReason.PLAYER_DIMENSION_CHANGED,
                    "player left source dimension"
            ));
        }
        if (!(player.containerMenu instanceof ObeliskPortalMenu menu)
                || menu.containerId != request.sourceContainerId()
                || !menu.matchesActivePreparationForRuntime(request)) {
            return Optional.of(new CancellationCheck(
                    DungeonPreparationCancellationReason.MENU_CLOSED,
                    "source obelisk menu is no longer active"
            ));
        }
        if (!isValidBottomObelisk(sourceLevel, request.obeliskPos())) {
            return Optional.of(new CancellationCheck(
                    DungeonPreparationCancellationReason.OBELISK_INVALID,
                    "source obelisk is no longer valid"
            ));
        }
        if (!withinMenuDistance(player, request.obeliskPos())) {
            return Optional.of(new CancellationCheck(
                    DungeonPreparationCancellationReason.PLAYER_MOVED_TOO_FAR,
                    "player moved out of obelisk interaction range"
            ));
        }
        return Optional.empty();
    }

    private static boolean isValidBottomObelisk(
            ServerLevel level,
            net.minecraft.core.BlockPos pos
    ) {
        var state = level.getBlockState(pos);
        return state.is(ModBlocks.OBELISK.get())
                && state.hasProperty(ObeliskBlock.PART)
                && state.getValue(ObeliskBlock.PART) == ObeliskPart.BOTTOM;
    }

    private static boolean withinMenuDistance(
            ServerPlayer player,
            net.minecraft.core.BlockPos pos
    ) {
        return player.distanceToSqr(
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D
        ) <= 64.0D;
    }

    private record CancellationCheck(
            DungeonPreparationCancellationReason reason,
            String detail
    ) {
    }

    private PreparedEntryRemovalReason removalReasonForPreparedEntry(
            DungeonManagerSavedData data,
            DungeonPreparedPortalEntry entry,
            long gameTime
    ) {
        if (entry.isClosed()) {
            return PreparedEntryRemovalReason.CLOSED;
        }
        if (entry.isStale(gameTime)
                && !this.postTeleportHandoffs.containsKey(
                entry.portalSessionId()
        )) {
            return PreparedEntryRemovalReason.STALE;
        }
        Optional<PortalSession> session =
                data.portalSessions().get(entry.portalSessionId());
        if (session.isEmpty()) {
            return PreparedEntryRemovalReason.MISSING_SESSION;
        }
        if (session.get().isExpired(gameTime)) {
            return PreparedEntryRemovalReason.SESSION_EXPIRED;
        }
        Optional<DungeonInstance> instance =
                data.instances().get(session.get().instanceId());
        if (instance.isEmpty()) {
            return PreparedEntryRemovalReason.MISSING_INSTANCE;
        }
        if (instance.get().status() != DungeonStatus.ACTIVE) {
            return PreparedEntryRemovalReason.INACTIVE_INSTANCE;
        }
        if (!entry.instanceId().equals(session.get().instanceId())
                || !entry.siteKey().equals(instance.get().siteKey())) {
            return PreparedEntryRemovalReason.IDENTITY_MISMATCH;
        }

        ServerLevel sourceLevel = this.level.getServer()
                .getLevel(session.get().sourceDimension());
        if (sourceLevel == null) {
            return PreparedEntryRemovalReason.SOURCE_PORTAL_UNAVAILABLE;
        }

        BlockPos anchor = session.get().portalAnchorPos();
        int chunkX = anchor.getX() >> 4;
        int chunkZ = anchor.getZ() >> 4;

        if (sourceLevel.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
            return null;
        }

        BlockPos obeliskPos = session.get().obeliskPos();
        if (sourceLevel.getChunkSource().getChunkNow(
                obeliskPos.getX() >> 4,
                obeliskPos.getZ() >> 4
        ) != null && !isValidBottomObelisk(sourceLevel, obeliskPos)) {
            return PreparedEntryRemovalReason.SOURCE_PORTAL_UNAVAILABLE;
        }

        if (DungeonPortalEntityService.findLoadedPortalReadOnly(
                sourceLevel,
                session.get().id(),
                anchor
        ).isEmpty()) {
            return PreparedEntryRemovalReason.SOURCE_PORTAL_UNAVAILABLE;
        }
        return null;
    }

    private void recordPreparedEntryRemoval(PreparedEntryRemovalReason reason) {
        switch (reason) {
            case STALE, CLOSED -> this.preparedEntriesRemovedAsStale++;
            case MISSING_SESSION, SESSION_EXPIRED ->
                    this.preparedEntriesRemovedForMissingSession++;
            case MISSING_INSTANCE -> this.preparedEntriesRemovedForMissingInstance++;
            case INACTIVE_INSTANCE, IDENTITY_MISMATCH, SOURCE_PORTAL_UNAVAILABLE ->
                    this.preparedEntriesRemovedForInactiveInstance++;
        }
    }

    private boolean isWholeSessionReason(PreparedEntryRemovalReason reason) {
        return switch (reason) {
            case SESSION_EXPIRED,
                    MISSING_INSTANCE,
                    INACTIVE_INSTANCE,
                    IDENTITY_MISMATCH,
                    SOURCE_PORTAL_UNAVAILABLE -> true;
            case CLOSED,
                    STALE,
                    MISSING_SESSION -> false;
        };
    }

    private static PortalSessionRemovalReason toPortalSessionRemovalReason(
            PreparedEntryRemovalReason reason
    ) {
        return switch (reason) {
            case SESSION_EXPIRED -> PortalSessionRemovalReason.EXPIRED;
            case MISSING_INSTANCE -> PortalSessionRemovalReason.INSTANCE_MISSING;
            case INACTIVE_INSTANCE -> PortalSessionRemovalReason.INSTANCE_INACTIVE;
            case IDENTITY_MISMATCH -> PortalSessionRemovalReason.SOURCE_PORTAL_INVALID;
            case SOURCE_PORTAL_UNAVAILABLE -> PortalSessionRemovalReason.SOURCE_PORTAL_INVALID;
            default -> throw new IllegalArgumentException(
                    "Not a whole-session removal reason: " + reason
            );
        };
    }

    private record PreparedEntryReconciliationAction(
            PortalSessionId portalSessionId,
            DungeonPreparedPortalEntry expectedEntry,
            PreparedEntryRemovalReason reason
    ) {
    }

    enum PostTeleportHandoffDecision {
        WAIT,
        TRACKING_ESTABLISHED,
        PLAYER_UNAVAILABLE,
        PLAYER_LEFT_DESTINATION,
        TIMED_OUT
    }

    private record PostTeleportHandoff(
            PortalSessionId portalSessionId,
            DungeonPreparedPortalEntry entry,
            ServerPlayer player,
            boolean requirePlayerListIdentity,
            ChunkPos destinationChunk,
            long startedAtGameTime
    ) {
    }

    private record PreparedEntryRecoveryResult(boolean done) {
        private static final PreparedEntryRecoveryResult KEEP_RUNNING =
                new PreparedEntryRecoveryResult(false);
        private static final PreparedEntryRecoveryResult DONE =
                new PreparedEntryRecoveryResult(true);
    }

    private static final class PreparedEntryRecoveryJob implements AutoCloseable {
        private final PortalSessionId portalSessionId;
        private final io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId instanceId;
        private final DungeonSite site;
        private final DungeonEntryChunkPlan entryPlan;
        private final List<DungeonChunkLease> leases = new ArrayList<>();
        private final long createdGameTime;
        private int nextLeaseRequestIndex;
        private int nextLeaseStateValidationIndex;
        private int nextLoadedChunkValidationIndex;
        private DungeonSafeSpawnScan safeSpawnScan;
        private boolean closed;

        PreparedEntryRecoveryJob(
                PortalSessionId portalSessionId,
                io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId instanceId,
                DungeonSite site,
                long createdGameTime
        ) {
            this.portalSessionId =
                    Objects.requireNonNull(portalSessionId, "portalSessionId");
            this.instanceId = Objects.requireNonNull(instanceId, "instanceId");
            this.site = Objects.requireNonNull(site, "site");
            this.entryPlan = DungeonEntryChunkPlanner.plan(site, 0);
            this.createdGameTime = createdGameTime;
        }

        PortalSessionId portalSessionId() {
            return this.portalSessionId;
        }

        PreparedEntryRecoveryResult tick(
                DungeonPreparationRuntime runtime,
                long gameTime,
                DungeonPreparationTickBudget budget
        ) {
            return DungeonPreparationProfiling.supply(
                    DungeonPreparationProfiler.global(),
                    DungeonPreparationProfiler.Operation.RECOVERY_JOB_TICK,
                    () -> runtime.level.getServer().isSameThread(),
                    "Prepared-entry recovery",
                    () -> tickProfiled(runtime, gameTime, budget)
            );
        }

        private PreparedEntryRecoveryResult tickProfiled(
                DungeonPreparationRuntime runtime,
                long gameTime,
                DungeonPreparationTickBudget budget
        ) {
            if (this.closed) {
                return PreparedEntryRecoveryResult.DONE;
            }
            if (!budget.hasTimeRemaining()) {
                return PreparedEntryRecoveryResult.KEEP_RUNNING;
            }
            if (gameTime - this.createdGameTime
                    >= DungeonPreparationLimits.JOB_TIMEOUT_TICKS) {
                close();
                return PreparedEntryRecoveryResult.DONE;
            }
            if (!persistentStateStillValid(runtime, gameTime)) {
                close();
                return PreparedEntryRecoveryResult.DONE;
            }

            List<ChunkPos> chunks = this.entryPlan.chunks();
            while (this.nextLeaseRequestIndex < chunks.size()
                    && budget.hasTimeRemaining()
                    && budget.tryConsumeEntryChunkRequest()) {
                this.leases.add(runtime.leaseManager.acquire(
                        chunks.get(this.nextLeaseRequestIndex)
                ));
                this.nextLeaseRequestIndex++;
            }
            if (this.nextLeaseRequestIndex < chunks.size()) {
                return PreparedEntryRecoveryResult.KEEP_RUNNING;
            }

            while (this.nextLeaseStateValidationIndex < this.leases.size()) {
                if (!budget.hasTimeRemaining()) {
                    return PreparedEntryRecoveryResult.KEEP_RUNNING;
                }
                DungeonChunkLease lease = this.leases.get(
                        this.nextLeaseStateValidationIndex
                );
                DungeonChunkLeaseState state = lease.state();
                if (state == DungeonChunkLeaseState.PENDING) {
                    return PreparedEntryRecoveryResult.KEEP_RUNNING;
                }
                if (state != DungeonChunkLeaseState.READY) {
                    close();
                    return PreparedEntryRecoveryResult.DONE;
                }
                this.nextLeaseStateValidationIndex++;
            }

            while (this.nextLoadedChunkValidationIndex < chunks.size()) {
                if (!budget.hasTimeRemaining()) {
                    return PreparedEntryRecoveryResult.KEEP_RUNNING;
                }
                ChunkPos chunkPos = chunks.get(
                        this.nextLoadedChunkValidationIndex
                );
                if (runtime.level.getChunkSource()
                        .getChunkNow(chunkPos.x(), chunkPos.z()) == null) {
                    close();
                    return PreparedEntryRecoveryResult.DONE;
                }
                this.nextLoadedChunkValidationIndex++;
            }

            if (this.safeSpawnScan == null) {
                this.safeSpawnScan = DungeonSafeSpawnResolver.createPrimaryEntryScan(
                        runtime.level,
                        this.site
                );
            }
            DungeonSafeSpawnScanResult scanResult = this.safeSpawnScan.advance(
                    budget,
                    DungeonSafeSpawnScanPurpose.RECOVERY
            );
            if (scanResult.state() == DungeonSafeSpawnScanState.RUNNING) {
                return PreparedEntryRecoveryResult.KEEP_RUNNING;
            }
            if (scanResult.state() != DungeonSafeSpawnScanState.FOUND) {
                close();
                return PreparedEntryRecoveryResult.DONE;
            }

            publishPreparedEntry(
                    runtime,
                    scanResult.resolvedPosition().orElseThrow(),
                    gameTime
            );
            this.safeSpawnScan = null;
            this.closed = true;
            return PreparedEntryRecoveryResult.DONE;
        }

        private boolean persistentStateStillValid(
                DungeonPreparationRuntime runtime,
                long gameTime
        ) {
            DungeonManagerSavedData data = DungeonManagerSavedData.get(runtime.level);
            Optional<PortalSession> session =
                    data.portalSessions().get(this.portalSessionId);
            if (session.isEmpty() || session.get().isExpired(gameTime)) {
                return false;
            }
            Optional<DungeonInstance> instance =
                    data.instances().get(this.instanceId);
            if (instance.isEmpty()
                    || instance.get().status() != DungeonStatus.ACTIVE
                    || !session.get().instanceId().equals(this.instanceId)
                    || !instance.get().siteKey().equals(this.site.key())) {
                return false;
            }
            return data.requireReservedDungeon(
                            this.instanceId,
                            this.site.key()
                    )
                    .map(io.github.naimjeg.obeliskdepths.dungeon.state.ReservedDungeonAggregate::site)
                    .filter(this.site::equals)
                    .isPresent();
        }

        private void publishPreparedEntry(
                DungeonPreparationRuntime runtime,
                Vec3 spawn,
                long gameTime
        ) {
            DungeonPreparationLeaseBundle localBundle =
                    new DungeonPreparationLeaseBundle(new ArrayList<>(this.leases));
            this.leases.clear();
            DungeonPreparedPortalEntry localEntry = null;
            try {
                localEntry = new DungeonPreparedPortalEntry(
                        this.portalSessionId,
                        this.instanceId,
                        this.site.key(),
                        new PreparedDungeonDestination(spawn),
                        this.entryPlan.chunks(),
                        localBundle,
                        gameTime
                );
                localBundle = null;
                runtime.preparedEntryRegistry.register(localEntry);
                localEntry = null;
            } finally {
                if (localEntry != null) {
                    localEntry.close();
                } else if (localBundle != null) {
                    localBundle.close();
                }
            }
        }

        @Override
        public void close() {
            if (this.closed) {
                return;
            }
            this.closed = true;
            RuntimeException scanFailure = null;
            Error scanError = null;
            if (this.safeSpawnScan != null) {
                try {
                    this.safeSpawnScan.cancel();
                } catch (RuntimeException exception) {
                    scanFailure = appendFailure(
                            scanFailure,
                            "Failed to cancel prepared-entry safe-spawn scan",
                            exception
                    );
                } catch (Error error) {
                    scanError = error;
                } finally {
                    this.safeSpawnScan = null;
                }
            }
            List<AutoCloseable> owned = new ArrayList<>(this.leases);
            this.leases.clear();
            try {
                new DungeonPreparationLeaseBundle(owned).close();
            } catch (RuntimeException | Error leaseFailure) {
                if (scanError != null) {
                    if (leaseFailure != scanError) {
                        scanError.addSuppressed(leaseFailure);
                    }
                } else if (leaseFailure instanceof Error error) {
                    scanError = error;
                    if (scanFailure != null) {
                        scanError.addSuppressed(scanFailure);
                        scanFailure = null;
                    }
                } else {
                    scanFailure = appendFailure(
                            scanFailure,
                            "Failed to close prepared-entry recovery leases",
                            (RuntimeException)leaseFailure
                    );
                }
            }
            if (scanError != null) {
                throw scanError;
            }
            if (scanFailure != null) {
                throw scanFailure;
            }
        }
    }

    private enum PreparedEntryRemovalReason {
        CLOSED,
        STALE,
        MISSING_SESSION,
        SESSION_EXPIRED,
        MISSING_INSTANCE,
        INACTIVE_INSTANCE,
        IDENTITY_MISMATCH,
        SOURCE_PORTAL_UNAVAILABLE
    }

    @Override
    public void recordClaimReleaseInvariantFailure() {
        this.claimReleaseInvariantFailures++;
    }

    @Override
    public void recordCommittedPublicationFailure() {
        this.committedPublicationFailures++;
    }

    private static DungeonPreparationSubmissionRejectionReason submissionRejectionReason(
            DungeonPreparationJobRegistry.SubmissionRejectionReason reason
    ) {
        return switch (reason) {
            case DUPLICATE_JOB_ID ->
                    DungeonPreparationSubmissionRejectionReason.DUPLICATE_JOB_ID;
            case DUPLICATE_PLAYER ->
                    DungeonPreparationSubmissionRejectionReason.DUPLICATE_PLAYER;
            case DUPLICATE_OBELISK ->
                    DungeonPreparationSubmissionRejectionReason.DUPLICATE_OBELISK;
        };
    }

    private void assertUsable() {
        assertOwnerThread();
        if (this.cleared) {
            throw new IllegalStateException("DungeonPreparationRuntime has been cleared.");
        }
    }

    private void assertOwnerLevel(ServerLevel level) {
        if (level != this.level) {
            throw new IllegalArgumentException("Runtime used with a non-owning level.");
        }
        assertOwnerThread();
    }

    private void assertOwnerThread() {
        assertServerThread(this.level);
    }

    private static void assertServerThread(ServerLevel level) {
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException(
                    "DungeonPreparationRuntime must be accessed on the server thread."
            );
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level
                && level.dimension().equals(ModDimensions.OBELISK_DEPTHS_LEVEL)) {
            DungeonPreparationRuntime runtime = DungeonPreparationRuntime.get(level);
            if (runtime != null) {
                runtime.clear(
                        DungeonPreparationCancellationReason.LEVEL_UNLOADED,
                        "level unloaded",
                        level.getGameTime()
                );
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        DungeonPreparationProfiler.global().flushToLog();
        clearAllOnServerStopping();
    }
}
