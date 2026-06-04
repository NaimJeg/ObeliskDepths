package io.github.naimjeg.obeliskdepths.dungeon.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.artifact.DungeonRuntimeArtifactRecord;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonStatus;
import io.github.naimjeg.obeliskdepths.dungeon.portal.PortalSession;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidId;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidInstance;
import io.github.naimjeg.obeliskdepths.dungeon.reward.DungeonRewardRecord;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomState;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSession;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteRecord;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteUsageStatus;
import io.github.naimjeg.obeliskdepths.dungeon.state.store.*;
import io.github.naimjeg.obeliskdepths.dungeon.territory.DungeonTerritory;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.*;

public final class DungeonManagerSavedData extends SavedData {

    private final DungeonInstanceStore instances = new DungeonInstanceStore(this::setDirty);
    private final DungeonSessionStore sessions = new DungeonSessionStore(this::setDirty);
    private final PortalSessionStore portalSessions = new PortalSessionStore(this::setDirty);
    private final RoomStateStore roomStates = new RoomStateStore(this::setDirty);
    private final DungeonSiteStore sites = new DungeonSiteStore(this::setDirty);
    private final DungeonRewardStore rewards = new DungeonRewardStore(this::setDirty);
    private final RuntimeArtifactStore runtimeArtifacts = new RuntimeArtifactStore(this::setDirty);
    private final DungeonRaidStore raids = new DungeonRaidStore(this::setDirty);
    private final DungeonTerritoryStore territories = new DungeonTerritoryStore(this::setDirty);
    private final Set<String> reportedReservedDungeonIssues = new HashSet<>();
    private final TeardownTransactionObserver teardownObserver;

    private static final Identifier FILE_ID =
            Identifier.fromNamespaceAndPath(ObeliskDepths.MOD_ID, "dungeons");

    public static final Codec<DungeonManagerSavedData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            DungeonInstance.CODEC.listOf()
                    .optionalFieldOf("instances", List.of())
                    .forGetter(data -> List.copyOf(data.instances.all())),
            DungeonTerritory.CODEC.listOf()
                    .optionalFieldOf("territories", List.of())
                    .forGetter(data -> List.copyOf(data.territories.all())),
            PortalSession.CODEC.listOf()
                    .optionalFieldOf("portal_sessions", List.of())
                    .forGetter(data -> List.copyOf(data.portalSessions.all())),
            DungeonRaidInstance.CODEC.listOf()
                    .optionalFieldOf("raids", List.of())
                    .forGetter(data -> List.copyOf(data.raids.all())),
            DungeonSession.CODEC.listOf()
                    .optionalFieldOf("sessions", List.of())
                    .forGetter(data -> List.copyOf(data.sessions.all())),
            DungeonRoomState.CODEC.listOf()
                    .optionalFieldOf("room_states", List.of())
                    .forGetter(data -> data.roomStates.flatten()),
            DungeonSiteRecord.CODEC.listOf()
                    .optionalFieldOf("site_records", List.of())
                    .forGetter(data -> List.copyOf(data.sites.records())),
            DungeonSite.CODEC.listOf()
                    .optionalFieldOf("site_snapshots", List.of())
                    .forGetter(data -> List.copyOf(data.sites.snapshots())),
            DungeonRewardRecord.CODEC.listOf()
                    .optionalFieldOf("rewards", List.of())
                    .forGetter(data -> List.copyOf(data.rewards.all())),
            DungeonRuntimeArtifactRecord.CODEC.listOf()
                    .optionalFieldOf("runtime_artifacts", List.of())
                    .forGetter(data -> List.copyOf(data.runtimeArtifacts.all()))
    ).apply(instance, DungeonManagerSavedData::new));

    public static final SavedDataType<DungeonManagerSavedData> TYPE =
            new SavedDataType<>(
                    FILE_ID,
                    DungeonManagerSavedData::new,
                    CODEC,
                    DataFixTypes.SAVED_DATA_MAP_DATA
            );

    public DungeonManagerSavedData() {
        this(TeardownTransactionObserver.NO_OP);
    }

    DungeonManagerSavedData(
            TeardownTransactionObserver teardownObserver
    ) {
        this.teardownObserver = Objects.requireNonNull(
                teardownObserver,
                "teardownObserver"
        );
    }

    private DungeonManagerSavedData(
            List<DungeonInstance> instances,
            List<DungeonTerritory> territories,
            List<PortalSession> portalSessions,
            List<DungeonRaidInstance> raids,
            List<DungeonSession> sessions,
            List<DungeonRoomState> roomStates,
            List<DungeonSiteRecord> siteRecords,
            List<DungeonSite> siteSnapshots,
            List<DungeonRewardRecord> rewards,
            List<DungeonRuntimeArtifactRecord> runtimeArtifacts
    ) {
        this();

        this.instances.load(instances);
        this.territories.load(territories);
        this.portalSessions.load(portalSessions);
        this.raids.load(raids);
        this.sessions.load(sessions);
        this.sites.loadRecords(siteRecords);
        this.sites.loadSnapshots(siteSnapshots);
        this.rewards.load(rewards);
        this.runtimeArtifacts.load(runtimeArtifacts);
        this.roomStates.load(roomStates);

        DungeonSavedDataInvariantValidator.validateOrThrow(this);
    }

    public static DungeonManagerSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public DungeonInstanceStore instances() {
        return this.instances;
    }

    public DungeonSessionStore sessions() {
        return this.sessions;
    }

    public PortalSessionStore portalSessions() {
        return this.portalSessions;
    }

    public RoomStateStore roomStates() {
        return this.roomStates;
    }

    public DungeonSiteStore sites() {
        return this.sites;
    }

    public DungeonRewardStore rewards() {
        return this.rewards;
    }

    public RuntimeArtifactStore runtimeArtifacts() {
        return this.runtimeArtifacts;
    }

    public DungeonRaidStore raids() {
        return this.raids;
    }

    public DungeonTerritoryStore territories() {
        return this.territories;
    }

    public Optional<ReservedDungeonAggregate> requireReservedDungeon(
            io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId instanceId,
            DungeonSiteKey siteKey
    ) {
        return this.validateReservedDungeon(instanceId, siteKey).aggregate();
    }

    public void reportReservedDungeonInvariantViolation(
            String boundary,
            io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId instanceId,
            DungeonSiteKey siteKey
    ) {
        ReservedDungeonValidation validation =
                this.validateReservedDungeon(instanceId, siteKey);
        if (validation.aggregate().isPresent()) {
            return;
        }

        String issueKey = boundary
                + "|"
                + instanceId
                + "|"
                + siteKey
                + "|"
                + validation.violations();
        if (!this.reportedReservedDungeonIssues.add(issueKey)) {
            return;
        }

        ObeliskDepths.LOGGER.error(
                "Reserved dungeon aggregate invariant violation at {}: instance={}, site={}, violations={}",
                boundary,
                instanceId,
                siteKey,
                validation.violations()
        );
    }

    /**
     * Releases every runtime artefact for a reserved dungeon instance
     * in a single aggregate-root transaction.
     */
    public boolean releaseReservedDungeonRuntime(
            io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId instanceId
    ) {
        Objects.requireNonNull(instanceId, "instanceId");

        TeardownTransactionSnapshot snapshot =
                this.captureTeardownSnapshot(instanceId);

        if (snapshot == null) {
            return false;
        }

        TeardownMutationJournal journal = new TeardownMutationJournal();

        try {
            this.applyTeardown(snapshot, journal);

            /*
             * Mark the site step before entering the store mutation. This makes
             * rollback safe even if the store throws after a partial mutation.
             * The restoration API is idempotent when the mutation did not occur.
             */
            journal.siteMutationAttempted = true;

            if (!this.sites.releaseReservation(
                    instanceId,
                    snapshot.instance().siteKey()
            )) {
                throw new IllegalStateException(
                        "Site release failed inside transaction: " + instanceId
                );
            }

            this.teardownObserver.after(TeardownStep.SITE_MUTATED);
        } catch (RuntimeException failure) {
            this.rollbackTeardown(snapshot, journal, failure);
            throw failure;
        }

        return true;
    }

    /**
     * Retires every runtime artefact for a reserved dungeon instance,
     * preserving a terminal site record.
     */
    public boolean retireReservedDungeonRuntime(
            io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId instanceId,
            DungeonSiteUsageStatus finalStatus,
            long gameTime
    ) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(finalStatus, "finalStatus");

        if (!finalStatus.isTerminal()) {
            throw new IllegalArgumentException(
                    "Runtime instance can only retire a site with terminal status."
            );
        }

        TeardownTransactionSnapshot snapshot =
                this.captureTeardownSnapshot(instanceId);

        if (snapshot == null) {
            return false;
        }

        TeardownMutationJournal journal = new TeardownMutationJournal();

        try {
            this.applyTeardown(snapshot, journal);

            /*
             * Set before the call so a partial store mutation is recoverable.
             */
            journal.siteMutationAttempted = true;

            if (!this.sites.retireReservation(
                    instanceId,
                    snapshot.instance().siteKey(),
                    finalStatus,
                    gameTime
            )) {
                throw new IllegalStateException(
                        "Site retirement failed inside transaction: " + instanceId
                );
            }

            this.teardownObserver.after(TeardownStep.SITE_MUTATED);
        } catch (RuntimeException failure) {
            this.rollbackTeardown(snapshot, journal, failure);
            throw failure;
        }

        return true;
    }

    private TeardownTransactionSnapshot captureTeardownSnapshot(
            io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId instanceId
    ) {
        Objects.requireNonNull(instanceId, "instanceId");

        Optional<DungeonInstance> existingInstance =
                this.instances.get(instanceId);

        if (existingInstance.isEmpty()) {
            return null;
        }

        DungeonInstance instance = existingInstance.orElseThrow();
        ReservedDungeonValidation validation =
                this.validateReservedDungeon(instanceId, instance.siteKey());

        if (validation.aggregate().isEmpty()) {
            throw new IllegalStateException(
                    "Incomplete reserved dungeon aggregate for teardown: instance="
                            + instanceId
                            + " violations="
                            + validation.violations()
            );
        }

        ReservedDungeonAggregate aggregate =
                validation.aggregate().orElseThrow();

        /*
         * Do not perform a second reservedState lookup after validation.
         * Construct the exact rollback state from the validated aggregate.
         */
        DungeonSiteStore.ReservedSiteState reservedSiteState =
                new DungeonSiteStore.ReservedSiteState(
                        aggregate.siteRecord(),
                        aggregate.site()
                );

        return new TeardownTransactionSnapshot(
                aggregate.instance(),
                aggregate.territory(),
                aggregate.roomStates(),
                this.raids.allForInstance(instanceId),
                this.sessions.captureForCleanup(instanceId),
                reservedSiteState
        );
    }

    private void applyTeardown(
            TeardownTransactionSnapshot snapshot,
            TeardownMutationJournal journal
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(journal, "journal");

        if (snapshot.sessionSnapshot().isPresent()) {
            DungeonSessionStore.SessionCleanupSnapshot cleanupSnapshot =
                    snapshot.sessionSnapshot().orElseThrow();

            if (!this.sessions.markCleanedForTransaction(cleanupSnapshot)) {
                throw new IllegalStateException(
                        "Session cleanup failed inside transaction for instance: "
                                + snapshot.instance().id()
                );
            }

            /*
             * markCleanedForTransaction also accepts an already-cleaned snapshot.
             * Only journal an actual state/bonus mutation.
             */
            if (cleanupSnapshot.requiresCleanupMutation()) {
                journal.sessionCleaned = true;
                this.teardownObserver.after(TeardownStep.SESSION_CLEANED);
            }
        }

        journal.removedRaids = this.raids.removeAllForInstance(
                snapshot.instance().id()
        );
        verifyRemovedRaidsEqual(
                snapshot.raids(),
                journal.removedRaids,
                "Raid mismatch in teardown transaction for instance "
                        + snapshot.instance().id()
        );
        this.teardownObserver.after(TeardownStep.RAIDS_REMOVED);

        journal.removedRoomStates =
                this.roomStates.removeInstanceStates(
                        snapshot.instance().id()
                );
        verifyRemovedRoomStatesEqual(
                snapshot.roomStates(),
                journal.removedRoomStates,
                "Room-state mismatch in teardown transaction for instance "
                        + snapshot.instance().id()
        );
        this.teardownObserver.after(TeardownStep.ROOM_STATES_REMOVED);

        DungeonTerritory removedTerritory =
                this.territories.remove(snapshot.territory().id())
                        .orElseThrow(() -> new IllegalStateException(
                                "Territory removal failed inside transaction: "
                                        + snapshot.territory().id()
                        ));

        journal.removedTerritory = removedTerritory;

        if (!removedTerritory.equals(snapshot.territory())) {
            throw new IllegalStateException(
                    "Territory removal returned unexpected value: expected="
                            + snapshot.territory()
                            + " actual="
                            + removedTerritory
            );
        }

        this.teardownObserver.after(TeardownStep.TERRITORY_REMOVED);

        DungeonInstance removedInstance =
                this.instances.remove(snapshot.instance().id())
                        .orElseThrow(() -> new IllegalStateException(
                                "Instance removal failed inside transaction: "
                                        + snapshot.instance().id()
                        ));

        journal.removedInstance = removedInstance;

        /*
         * DungeonInstance does not define value equality. The store must return
         * the exact object captured at transaction start.
         */
        if (removedInstance != snapshot.instance()) {
            throw new IllegalStateException(
                    "Instance removal returned an unexpected object: expected="
                            + snapshot.instance().id()
                            + " actual="
                            + removedInstance.id()
            );
        }

        this.teardownObserver.after(TeardownStep.INSTANCE_REMOVED);
    }

    private void rollbackTeardown(
            TeardownTransactionSnapshot snapshot,
            TeardownMutationJournal journal,
            RuntimeException originalFailure
    ) {
        RuntimeException rollbackFailure = null;

        /*
         * Strict reverse order:
         * site -> instance -> territory -> room states -> raids -> session.
         */
        if (journal.siteMutationAttempted) {
            rollbackFailure = attemptTeardownRollback(
                    rollbackFailure,
                    () -> this.sites.restoreReservedStateForTransaction(
                            snapshot.instance().id(),
                            snapshot.reservedSiteState()
                    )
            );
        }

        if (journal.removedInstance != null) {
            rollbackFailure = attemptTeardownRollback(
                    rollbackFailure,
                    () -> this.instances.put(journal.removedInstance)
            );
        }

        if (journal.removedTerritory != null) {
            rollbackFailure = attemptTeardownRollback(
                    rollbackFailure,
                    () -> this.territories.put(journal.removedTerritory)
            );
        }

        if (!journal.removedRoomStates.isEmpty()) {
            rollbackFailure = attemptTeardownRollback(
                    rollbackFailure,
                    () -> this.roomStates.restoreInstanceStates(
                            snapshot.instance().id(),
                            journal.removedRoomStates
                    )
            );
        }

        if (!journal.removedRaids.isEmpty()) {
            rollbackFailure = attemptTeardownRollback(
                    rollbackFailure,
                    () -> this.raids.restoreRemovedRaids(
                            journal.removedRaids
                    )
            );
        }

        if (journal.sessionCleaned) {
            rollbackFailure = attemptTeardownRollback(
                    rollbackFailure,
                    () -> this.sessions.restoreAfterFailedCleanup(
                            snapshot.sessionSnapshot().orElseThrow()
                    )
            );
        }

        if (rollbackFailure != null) {
            originalFailure.addSuppressed(rollbackFailure);
        }
    }

    private static RuntimeException attemptTeardownRollback(
            RuntimeException aggregateFailure,
            Runnable action
    ) {
        try {
            action.run();
            return aggregateFailure;
        } catch (RuntimeException failure) {
            if (aggregateFailure == null) {
                return failure;
            }

            aggregateFailure.addSuppressed(failure);
            return aggregateFailure;
        }
    }

    private static void verifyRemovedRaidsEqual(
            List<DungeonRaidInstance> expected,
            List<DungeonRaidInstance> actual,
            String message
    ) {
        Map<DungeonRaidId, DungeonRaidInstance> expectedById =
                indexRaids(expected, message + " expected");
        Map<DungeonRaidId, DungeonRaidInstance> actualById =
                indexRaids(actual, message + " actual");

        if (!expectedById.keySet().equals(actualById.keySet())) {
            throw new IllegalStateException(
                    message
                            + " expectedIds="
                            + expectedById.keySet()
                            + " actualIds="
                            + actualById.keySet()
            );
        }

        for (DungeonRaidId id : expectedById.keySet()) {
            if (expectedById.get(id) != actualById.get(id)) {
                throw new IllegalStateException(
                        message + " object mismatch for raid " + id
                );
            }
        }
    }

    private static Map<DungeonRaidId, DungeonRaidInstance> indexRaids(
            Collection<DungeonRaidInstance> raids,
            String context
    ) {
        Map<DungeonRaidId, DungeonRaidInstance> result =
                new LinkedHashMap<>();

        for (DungeonRaidInstance raid : raids) {
            Objects.requireNonNull(raid, context + " raid");

            DungeonRaidInstance previous = result.put(raid.id(), raid);
            if (previous != null) {
                throw new IllegalStateException(
                        context + " contains duplicate raid id " + raid.id()
                );
            }
        }

        return result;
    }

    private static void verifyRemovedRoomStatesEqual(
            List<DungeonRoomState> expected,
            List<DungeonRoomState> actual,
            String message
    ) {
        Map<DungeonRoomId, DungeonRoomState> expectedById =
                indexRoomStates(expected, message + " expected");
        Map<DungeonRoomId, DungeonRoomState> actualById =
                indexRoomStates(actual, message + " actual");

        if (!expectedById.keySet().equals(actualById.keySet())) {
            throw new IllegalStateException(
                    message
                            + " expectedIds="
                            + expectedById.keySet()
                            + " actualIds="
                            + actualById.keySet()
            );
        }

        for (DungeonRoomId id : expectedById.keySet()) {
            if (expectedById.get(id) != actualById.get(id)) {
                throw new IllegalStateException(
                        message + " object mismatch for room " + id
                );
            }
        }
    }

    private static Map<DungeonRoomId, DungeonRoomState> indexRoomStates(
            Collection<DungeonRoomState> states,
            String context
    ) {
        Map<DungeonRoomId, DungeonRoomState> result =
                new LinkedHashMap<>();

        for (DungeonRoomState state : states) {
            Objects.requireNonNull(state, context + " room state");

            DungeonRoomState previous =
                    result.put(state.roomId(), state);

            if (previous != null) {
                throw new IllegalStateException(
                        context
                                + " contains duplicate room id "
                                + state.roomId()
                );
            }
        }

        return result;
    }

    private record TeardownTransactionSnapshot(
            DungeonInstance instance,
            DungeonTerritory territory,
            List<DungeonRoomState> roomStates,
            List<DungeonRaidInstance> raids,
            Optional<DungeonSessionStore.SessionCleanupSnapshot> sessionSnapshot,
            DungeonSiteStore.ReservedSiteState reservedSiteState
    ) {
        private TeardownTransactionSnapshot {
            Objects.requireNonNull(instance, "instance");
            Objects.requireNonNull(territory, "territory");

            roomStates = List.copyOf(
                    Objects.requireNonNull(roomStates, "roomStates")
            );

            raids = List.copyOf(
                    Objects.requireNonNull(raids, "raids")
            );

            Objects.requireNonNull(
                    sessionSnapshot,
                    "sessionSnapshot"
            );

            Objects.requireNonNull(
                    reservedSiteState,
                    "reservedSiteState"
            );
        }
    }

    private static final class TeardownMutationJournal {
        private boolean sessionCleaned;
        private List<DungeonRaidInstance> removedRaids = List.of();
        private List<DungeonRoomState> removedRoomStates = List.of();
        private DungeonTerritory removedTerritory;
        private DungeonInstance removedInstance;
        private boolean siteMutationAttempted;
    }

    enum TeardownStep {
        SESSION_CLEANED,
        RAIDS_REMOVED,
        ROOM_STATES_REMOVED,
        TERRITORY_REMOVED,
        INSTANCE_REMOVED,
        SITE_MUTATED
    }

    @FunctionalInterface
    interface TeardownTransactionObserver {
        TeardownTransactionObserver NO_OP = ignored -> {
        };

        void after(TeardownStep step);
    }

    public ReservedDungeonValidation validateReservedDungeon(
            io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId instanceId,
            DungeonSiteKey siteKey
    ) {
        Objects.requireNonNull(instanceId, "instanceId");
        Objects.requireNonNull(siteKey, "siteKey");

        List<String> violations = new ArrayList<>();
        Optional<DungeonInstance> instance = this.instances.get(instanceId);
        if (instance.isEmpty()) {
            violations.add("missing instance " + instanceId);
            return ReservedDungeonValidation.invalid(violations);
        }

        DungeonInstance dungeonInstance = instance.get();
        if (!dungeonInstance.siteKey().equals(siteKey)) {
            violations.add("instance site key mismatch instance="
                    + instanceId
                    + " expected="
                    + siteKey
                    + " actual="
                    + dungeonInstance.siteKey());
        }

        if (!requiresReservedSite(dungeonInstance.status())) {
            violations.add("instance status does not permit live reservation instance="
                    + instanceId
                    + " status="
                    + dungeonInstance.status().getSerializedName());
        }

        Optional<DungeonSiteStore.ReservedSiteState> reservedState =
                this.sites.reservedState(instanceId, siteKey);
        if (reservedState.isEmpty()) {
            violations.add("missing matching reserved site record/index/snapshot for instance="
                    + instanceId
                    + " site="
                    + siteKey);
        }

        Optional<DungeonTerritory> territory =
                this.territories.get(dungeonInstance.territoryId());
        if (territory.isEmpty()) {
            violations.add("missing territory "
                    + dungeonInstance.territoryId()
                    + " for instance "
                    + instanceId);
        }

        if (!violations.isEmpty()) {
            return ReservedDungeonValidation.invalid(violations);
        }

        DungeonSite site = reservedState.orElseThrow().snapshot();
        DungeonSiteRecord siteRecord = reservedState.orElseThrow().record();
        DungeonTerritory dungeonTerritory = territory.orElseThrow();
        if (!dungeonTerritory.instanceId().equals(instanceId)) {
            violations.add("territory owner mismatch territory="
                    + dungeonTerritory.id()
                    + " expected="
                    + instanceId
                    + " actual="
                    + dungeonTerritory.instanceId());
        }
        if (!dungeonTerritory.bounds().equals(site.bounds())) {
            violations.add("territory bounds mismatch instance="
                    + instanceId
                    + " territory="
                    + dungeonTerritory.bounds()
                    + " site="
                    + site.bounds());
        }
        if (!dungeonTerritory.startPos().equals(site.startPos())) {
            violations.add("territory start mismatch instance="
                    + instanceId
                    + " territory="
                    + dungeonTerritory.startPos()
                    + " site="
                    + site.startPos());
        }

        List<DungeonRoomState> states =
                List.copyOf(this.roomStates.allForInstance(instanceId));
        validateRoomStateSet(instanceId, site, states, violations);

        if (!violations.isEmpty()) {
            return ReservedDungeonValidation.invalid(violations);
        }

        return ReservedDungeonValidation.valid(new ReservedDungeonAggregate(
                dungeonInstance,
                siteRecord,
                site,
                dungeonTerritory,
                states
        ));
    }

    public static boolean requiresReservedSite(DungeonStatus status) {
        return status == DungeonStatus.ACTIVE
                || status == DungeonStatus.REWARD_PHASE
                || status == DungeonStatus.PORTAL_CLOSED
                || status == DungeonStatus.FAILED
                || status == DungeonStatus.EXPIRED;
    }

    static void validateRoomStateSet(
            io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId instanceId,
            DungeonSite site,
            Collection<DungeonRoomState> states,
            List<String> violations
    ) {
        Map<DungeonRoomId, DungeonRoomState> stateByRoom = new LinkedHashMap<>();
        for (DungeonRoomState state : states) {
            if (!state.instanceId().equals(instanceId)) {
                violations.add("room state belongs to wrong instance expected="
                        + instanceId
                        + " actual="
                        + state.instanceId()
                        + " room="
                        + state.roomId());
                continue;
            }
            DungeonRoomState previous = stateByRoom.put(state.roomId(), state);
            if (previous != null) {
                violations.add("duplicate room state instance="
                        + instanceId
                        + " room="
                        + state.roomId());
            }
        }

        Set<DungeonRoomId> expectedRoomIds = new LinkedHashSet<>();
        for (var room : site.rooms()) {
            if (!expectedRoomIds.add(room.id())) {
                violations.add("duplicate generated room in site site="
                        + site.key()
                        + " room="
                        + room.id());
            }
        }

        for (DungeonRoomId expected : expectedRoomIds) {
            if (!stateByRoom.containsKey(expected)) {
                violations.add("missing room state instance="
                        + instanceId
                        + " room="
                        + expected);
            }
        }
        for (DungeonRoomId actual : stateByRoom.keySet()) {
            if (!expectedRoomIds.contains(actual)) {
                violations.add("extraneous room state instance="
                        + instanceId
                        + " room="
                        + actual
                        + " site="
                        + site.key());
            }
        }
    }

    public record ReservedDungeonValidation(
            Optional<ReservedDungeonAggregate> aggregate,
            List<String> violations
    ) {
        public ReservedDungeonValidation {
            aggregate = Objects.requireNonNull(aggregate, "aggregate");
            violations = List.copyOf(Objects.requireNonNull(violations, "violations"));
            if (aggregate.isPresent() == !violations.isEmpty()) {
                throw new IllegalArgumentException(
                        "Reserved dungeon validation must contain either aggregate or violations."
                );
            }
        }

        static ReservedDungeonValidation valid(ReservedDungeonAggregate aggregate) {
            return new ReservedDungeonValidation(Optional.of(aggregate), List.of());
        }

        static ReservedDungeonValidation invalid(List<String> violations) {
            return new ReservedDungeonValidation(Optional.empty(), violations);
        }
    }
}
