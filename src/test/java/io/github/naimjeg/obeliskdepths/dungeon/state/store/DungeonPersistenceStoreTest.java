package io.github.naimjeg.obeliskdepths.dungeon.state.store;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.github.naimjeg.obeliskdepths.dungeon.artifact.DungeonRuntimeArtifactRecord;
import io.github.naimjeg.obeliskdepths.dungeon.artifact.DungeonRuntimeArtifactType;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonTerritoryId;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonDifficulty;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidInstance;
import io.github.naimjeg.obeliskdepths.dungeon.reward.DungeonRewardRecord;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSession;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSessionState;
import io.github.naimjeg.obeliskdepths.dungeon.session.SessionAccessPolicy;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonGeneratedRoom;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteRecord;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteUsageStatus;
import io.github.naimjeg.obeliskdepths.dungeon.territory.DungeonBounds;
import io.github.naimjeg.obeliskdepths.dungeon.territory.DungeonTerritory;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

public final class DungeonPersistenceStoreTest {
    private DungeonPersistenceStoreTest() {
    }

    public static void main(String[] args) {
        testSiteStoreReservationIndexesAndDirtyCallbacks();
        testSessionStoreSingularIndexesAndCanonicalMutations();
        testRewardStoreIndexesMutablePlacementAndDirtyCallbacks();
        testRuntimeArtifactStoreIndexesAndDirtyCallbacks();
        testRaidStoreInstanceIndexAndDirtyCallbacks();
        testTerritoryStoreSpatialIndexAndDirtyCallbacks();
        testDungeonSessionCodecDeterministicUuidArrays();
        testDungeonInstanceCodecDeterministicParticipants();
        testPhysicalMembershipReconcileUnchanged();
        testPhysicalMembershipReconcileMultiSession();
        testPhysicalMembershipReconcileTripleSession();
        testPhysicalMembershipReconcileDanglingIndex();
        testPhysicalMembershipReconcileMissingTarget();
        testPortalEntrySucceededReconcilesOldPhysicalMembership();
        testUnregisterPhysicalParticipantFromAllMultiSession();
        testUnregisterPhysicalParticipantFromAllDanglingIndex();
    }

    private static void testSiteStoreReservationIndexesAndDirtyCallbacks() {
        AtomicInteger dirtyCount = new AtomicInteger();
        DungeonSiteStore store = new DungeonSiteStore(dirtyCount::incrementAndGet);
        DungeonInstanceId releasedInstanceId = instanceId("released-site-instance");
        DungeonSite releasedSite = site(new DungeonSiteKey(1, 2), 0);

        assertTrue(store.isUnreached(releasedSite.key()), "new site starts unreached");
        store.reserve(releasedSite, releasedInstanceId, 10L);

        assertEquals(1, dirtyCount.get(), "reserve marks site store dirty");
        assertFalse(store.isUnreached(releasedSite.key()), "reserved site is no longer unreached");
        assertTrue(store.isReserved(releasedSite.key()), "reserved site is indexed by site key");
        assertEquals(Optional.of(releasedSite.key()), store.reservedSite(releasedInstanceId), "reserved site is indexed by instance");
        assertEquals(Optional.of(releasedSite), store.snapshot(releasedSite.key()), "site snapshot is stored");
        assertThrows(
                IllegalStateException.class,
                () -> store.reserve(site(new DungeonSiteKey(5, 6), 128), releasedInstanceId, 11L),
                "instance cannot reserve two sites"
        );
        assertThrows(
                IllegalStateException.class,
                () -> store.reserve(releasedSite, instanceId("released-site-conflict"), 12L),
                "site cannot be reserved by two instances"
        );
        assertFalse(
                store.releaseReservation(instanceId("released-site-non-owner"), releasedSite.key()),
                "non-owner cannot release reservation"
        );
        assertEquals(1, dirtyCount.get(), "rejected reservation mutations do not mark dirty");
        assertTrue(store.isReserved(releasedSite.key()), "non-owner release keeps reservation");
        assertEquals(Optional.of(releasedSite), store.snapshot(releasedSite.key()), "non-owner release keeps snapshot");

        assertTrue(store.releaseReservation(releasedInstanceId, releasedSite.key()), "release removes active reservation");
        assertEquals(2, dirtyCount.get(), "release marks site store dirty");
        assertTrue(store.isUnreached(releasedSite.key()), "released site becomes selectable again");
        assertEquals(Optional.empty(), store.reservedSite(releasedInstanceId), "release clears instance index");
        assertEquals(Optional.empty(), store.snapshot(releasedSite.key()), "release removes snapshot");

        DungeonInstanceId retiredInstanceId = instanceId("retired-site-instance");
        DungeonSite retiredSite = site(new DungeonSiteKey(3, 4), 64);
        store.reserve(retiredSite, retiredInstanceId, 20L);
        assertTrue(
                store.retireReservation(
                        retiredInstanceId,
                        retiredSite.key(),
                        DungeonSiteUsageStatus.COMPLETED,
                        30L
                ),
                "retire records terminal site state"
        );

        assertEquals(4, dirtyCount.get(), "retire marks site store dirty");
        assertFalse(store.isReserved(retiredSite.key()), "terminal site is not reserved");
        assertEquals(1L, store.retiredCount(), "retired count reflects terminal record");
        assertEquals(0L, store.reservedCount(), "reserved index excludes terminal record");
        assertEquals(Optional.empty(), store.snapshot(retiredSite.key()), "retire removes runtime snapshot");

        DungeonSiteStore loadedStore = new DungeonSiteStore(() -> {
        });
        assertThrows(
                IllegalStateException.class,
                () -> loadedStore.loadRecords(List.of(
                        DungeonSiteRecord.reserved(
                                new DungeonSiteKey(9, 10),
                                instanceId("loaded-site-conflict"),
                                1L
                        ),
                        DungeonSiteRecord.reserved(
                                new DungeonSiteKey(9, 11),
                                instanceId("loaded-site-conflict"),
                                2L
                        )
                )),
                "loaded duplicate instance reservation rejected"
        );
    }

    private static void testSessionStoreSingularIndexesAndCanonicalMutations() {
        AtomicInteger dirtyCount = new AtomicInteger();
        DungeonSessionStore store = new DungeonSessionStore(dirtyCount::incrementAndGet);
        UUID starter = uuid("session-starter");
        DungeonInstanceId instanceId = instanceId("session-instance");
        DungeonSiteKey siteKey = new DungeonSiteKey(7, 8);
        DungeonSession session = session(
                "primary-session",
                instanceId,
                siteKey,
                starter,
                SessionAccessPolicy.OPEN,
                5L
        );

        store.add(session);
        assertEquals(1, dirtyCount.get(), "add marks session store dirty");
        assertEquals(Optional.of(session), store.findByInstance(instanceId), "instance lookup is singular");
        assertEquals(Optional.of(session), store.findBySite(siteKey), "site lookup is singular");
        assertThrows(
                IllegalStateException.class,
                () -> store.add(session(
                        "duplicate-instance-session",
                        instanceId,
                        new DungeonSiteKey(7, 9),
                        starter,
                        SessionAccessPolicy.OPEN,
                        6L
                )),
                "duplicate session for same instance rejected"
        );
        assertThrows(
                IllegalStateException.class,
                () -> store.add(session(
                        "duplicate-site-session",
                        instanceId("session-other-instance"),
                        siteKey,
                        starter,
                        SessionAccessPolicy.OPEN,
                        7L
                )),
                "duplicate session for same site rejected"
        );

        DungeonSession nonCanonicalSession = new DungeonSession(
                session.id(),
                session.instanceId(),
                session.starterPlayerId(),
                session.siteKey(),
                session.state(),
                session.accessPolicy(),
                session.participants(),
                session.physicalParticipants(),
                session.spawnedEntityIds(),
                session.createdAtGameTime(),
                session.lastParticipantInsideGameTime(),
                session.tributeBonusActive()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> store.markCleaned(nonCanonicalSession),
                "store rejects same-id session objects it does not own"
        );
        assertEquals(1, dirtyCount.get(), "rejected noncanonical session mutation does not mark dirty");

        DungeonSessionStore loadedStore = new DungeonSessionStore(() -> {
        });
        assertThrows(
                IllegalStateException.class,
                () -> loadedStore.load(List.of(
                        session(
                                "loaded-primary-session",
                                instanceId("loaded-session-instance"),
                                new DungeonSiteKey(8, 1),
                                starter,
                                SessionAccessPolicy.OPEN,
                                1L
                        ),
                        session(
                                "loaded-conflict-session",
                                instanceId("loaded-session-instance"),
                                new DungeonSiteKey(8, 2),
                                starter,
                                SessionAccessPolicy.OPEN,
                                2L
                        )
                )),
                "loaded duplicate session for same instance rejected"
        );
    }

    private static void testRewardStoreIndexesMutablePlacementAndDirtyCallbacks() {
        AtomicInteger dirtyCount = new AtomicInteger();
        DungeonRewardStore store = new DungeonRewardStore(dirtyCount::incrementAndGet);
        DungeonInstanceId instanceId = instanceId("reward-instance");
        DungeonRoomId roomId = DungeonRoomId.of("reward_room");
        DungeonRewardRecord reward = DungeonRewardRecord.bossDefeated(
                instanceId,
                Optional.of(roomId),
                0L
        );

        store.add(reward);
        assertEquals(1, dirtyCount.get(), "add marks reward store dirty");
        assertEquals(Optional.of(reward), store.get(reward.rewardId()), "reward lookup by id works");
        assertEquals(Optional.of(reward), store.findByInstance(instanceId), "reward lookup by instance works");
        assertThrows(
                IllegalStateException.class,
                () -> store.add(DungeonRewardRecord.bossDefeated(
                        instanceId,
                        Optional.of(DungeonRoomId.of("reward_room_duplicate")),
                        1L
                )),
                "duplicate reward for same instance rejected"
        );
        assertEquals(1, dirtyCount.get(), "rejected duplicate reward does not mark dirty");
        DungeonRewardRecord nonCanonicalReward = new DungeonRewardRecord(
                reward.rewardId(),
                reward.instanceId(),
                reward.roomId(),
                reward.status(),
                reward.preferredPlacementOrigin(),
                reward.placedMainPos(),
                reward.rewardSeed(),
                reward.placementFailures(),
                reward.createdGameTime(),
                Optional.empty()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> store.markPlacementPending(nonCanonicalReward),
                "store rejects same-id reward objects it does not own"
        );
        assertEquals(1, dirtyCount.get(), "rejected noncanonical mutation does not mark dirty");

        DungeonRewardStore loadedStore = new DungeonRewardStore(() -> {
        });
        assertThrows(
                IllegalStateException.class,
                () -> loadedStore.load(List.of(
                        reward,
                        DungeonRewardRecord.bossDefeated(
                                instanceId,
                                Optional.of(DungeonRoomId.of("reward_room_loaded_duplicate")),
                                2L
                        )
                )),
                "loaded duplicate reward for same instance rejected"
        );

        BlockPos firstPos = new BlockPos(5, 70, 5);
        assertTrue(store.markAvailable(reward, firstPos), "placement makes reward available");
        assertEquals(2, dirtyCount.get(), "placement marks reward store dirty");
        assertEquals(Optional.of(reward), store.findAt(instanceId, firstPos), "position index records placed reward");

        BlockPos secondPos = new BlockPos(6, 70, 5);
        assertTrue(store.markAvailable(reward, secondPos), "moving placement changes reward");
        assertEquals(Optional.empty(), store.findAt(instanceId, firstPos), "old position index is removed");
        assertEquals(Optional.of(reward), store.findAt(instanceId, secondPos), "new position index is recorded");

        int dirtyBeforeRemove = dirtyCount.get();
        assertTrue(store.remove(reward.rewardId()), "reward removal succeeds");
        assertEquals(dirtyBeforeRemove + 1, dirtyCount.get(), "remove marks reward store dirty");
        assertEquals(Optional.empty(), store.get(reward.rewardId()), "removed reward lookup is empty");
        assertEquals(Optional.empty(), store.findByInstance(instanceId), "instance index is cleared");
        assertEquals(Optional.empty(), store.findAt(instanceId, secondPos), "position index is cleared");
        assertFalse(store.remove(reward.rewardId()), "duplicate removal is idempotent");
        assertEquals(dirtyBeforeRemove + 1, dirtyCount.get(), "idempotent removal does not mark dirty");
    }

    private static void testRuntimeArtifactStoreIndexesAndDirtyCallbacks() {
        AtomicInteger dirtyCount = new AtomicInteger();
        RuntimeArtifactStore store = new RuntimeArtifactStore(dirtyCount::incrementAndGet);
        DungeonInstanceId instanceId = instanceId("artifact-instance");
        DungeonRuntimeArtifactRecord artifact = new DungeonRuntimeArtifactRecord(
                instanceId,
                DungeonRuntimeArtifactType.TEMPORARY_BLOCK,
                Optional.of(new BlockPos(1, 65, 1)),
                Optional.empty(),
                false
        );

        store.add(artifact);
        assertEquals(1, dirtyCount.get(), "add marks artifact store dirty");
        assertTrue(store.forInstance(instanceId).contains(artifact), "instance artifact index includes artifact");
        assertTrue(store.pending().isEmpty(), "non-pending artifact is excluded from pending query");

        DungeonRuntimeArtifactRecord pendingArtifact = artifact.markPending(100L);
        assertTrue(store.replace(artifact, pendingArtifact), "artifact replacement succeeds");
        assertEquals(2, dirtyCount.get(), "replace marks artifact store dirty");
        assertFalse(store.forInstance(instanceId).contains(artifact), "old artifact is unindexed");
        assertTrue(store.forInstance(instanceId).contains(pendingArtifact), "replacement artifact is indexed");
        assertTrue(store.pending().contains(pendingArtifact), "pending query reflects replacement");

        assertEquals(1, store.removeForInstance(instanceId, DungeonRuntimeArtifactType.TEMPORARY_BLOCK), "remove by instance and type removes artifact");
        assertEquals(3, dirtyCount.get(), "bulk removal marks artifact store dirty");
        assertTrue(store.forInstance(instanceId).isEmpty(), "instance artifact index is cleared");
        assertTrue(store.pending().isEmpty(), "pending index is cleared");
        assertEquals(0, store.removeForInstance(instanceId, DungeonRuntimeArtifactType.TEMPORARY_BLOCK), "duplicate bulk removal is idempotent");
        assertEquals(3, dirtyCount.get(), "idempotent bulk removal does not mark dirty");
    }

    private static void testRaidStoreInstanceIndexAndDirtyCallbacks() {
        AtomicInteger dirtyCount = new AtomicInteger();
        DungeonRaidStore store = new DungeonRaidStore(dirtyCount::incrementAndGet);
        DungeonInstanceId instanceId = instanceId("raid-instance");
        DungeonRaidInstance raid = store.createEncounter(
                instanceId,
                Identifier.fromNamespaceAndPath("obeliskdepths", "store_test_raid"),
                2,
                1,
                0L
        );

        assertEquals(1, dirtyCount.get(), "encounter creation marks raid store dirty");
        assertEquals(Optional.of(raid), store.findActiveByInstance(instanceId), "active raid is indexed by instance");
        assertTrue(store.creditNormalKill(raid), "mutable raid progress is recorded through store");
        assertEquals(2, dirtyCount.get(), "raid progress marks store dirty");
        assertTrue(store.markEncounterExpired(raid), "terminal raid transition succeeds");
        assertEquals(3, dirtyCount.get(), "terminal transition marks store dirty");
        assertEquals(Optional.empty(), store.findActiveByInstance(instanceId), "terminal raid is excluded from active lookup");
        assertEquals(Optional.of(raid), store.findByInstance(instanceId), "general lookup can still return terminal raid");
        assertEquals(1, store.removeForInstance(instanceId), "instance raid removal succeeds");
        assertEquals(4, dirtyCount.get(), "raid removal marks store dirty");
        assertEquals(Optional.empty(), store.findByInstance(instanceId), "raid instance index is cleared");
    }

    private static void testTerritoryStoreSpatialIndexAndDirtyCallbacks() {
        AtomicInteger dirtyCount = new AtomicInteger();
        DungeonTerritoryStore store = new DungeonTerritoryStore(dirtyCount::incrementAndGet);
        DungeonInstanceId firstInstanceId = instanceId("territory-first-instance");
        DungeonTerritoryId territoryId = territoryId("territory");
        DungeonTerritory firstTerritory = new DungeonTerritory(
                territoryId,
                firstInstanceId,
                new DungeonBounds(32, 40, 32, 63, 90, 63),
                new BlockPos(40, 64, 40)
        );

        store.put(firstTerritory);
        assertEquals(1, dirtyCount.get(), "territory insertion marks store dirty");
        assertEquals(Optional.of(firstTerritory), store.findContaining(new BlockPos(40, 64, 40)), "spatial index finds containing territory");
        assertEquals(Optional.of(firstInstanceId), store.findOwner(new BlockPos(40, 64, 40)), "owner lookup uses spatial index");

        DungeonInstanceId secondInstanceId = instanceId("territory-second-instance");
        DungeonTerritory replacement = new DungeonTerritory(
                territoryId,
                secondInstanceId,
                new DungeonBounds(96, 40, 96, 127, 90, 127),
                new BlockPos(100, 64, 100)
        );
        store.put(replacement);
        assertEquals(2, dirtyCount.get(), "territory replacement marks store dirty");
        assertEquals(Optional.empty(), store.findContaining(new BlockPos(40, 64, 40)), "old spatial index is cleared");
        assertEquals(Optional.of(secondInstanceId), store.findOwner(new BlockPos(100, 64, 100)), "replacement spatial index is built");

        assertEquals(Optional.of(replacement), store.remove(territoryId), "territory removal succeeds");
        assertEquals(3, dirtyCount.get(), "territory removal marks store dirty");
        assertEquals(Optional.empty(), store.findContaining(new BlockPos(100, 64, 100)), "removed territory is unindexed");
        assertEquals(Optional.empty(), store.remove(territoryId), "duplicate removal is idempotent");
        assertEquals(3, dirtyCount.get(), "idempotent removal does not mark dirty");
    }

    private static void testDungeonSessionCodecDeterministicUuidArrays() {
        UUID starter = uuid("det-session-starter");
        UUID guestA = uuid("det-session-guest-a");
        UUID guestB = uuid("det-session-guest-b");
        DungeonInstanceId instanceId = instanceId("det-session-instance");

        DungeonSession first = sessionWithSets(
                "det-session-first",
                instanceId,
                starter,
                List.of(starter, guestB, guestA),
                List.of(guestB, guestA),
                List.of(guestA, guestB)
        );
        DungeonSession second = sessionWithSets(
                "det-session-second",
                instanceId,
                starter,
                List.of(starter, guestA, guestB),
                List.of(guestA, guestB),
                List.of(guestB, guestA)
        );

        assertEquals(
                encode(first).getAsJsonObject().get("participants"),
                encode(second).getAsJsonObject().get("participants"),
                "DungeonSession participant arrays are deterministic"
        );
        assertEquals(
                encode(first).getAsJsonObject().get("physical_participants"),
                encode(second).getAsJsonObject().get("physical_participants"),
                "DungeonSession physical participant arrays are deterministic"
        );
        assertEquals(
                encode(first).getAsJsonObject().get("spawned_entity_ids"),
                encode(second).getAsJsonObject().get("spawned_entity_ids"),
                "DungeonSession spawned entity arrays are deterministic"
        );
    }

    private static void testDungeonInstanceCodecDeterministicParticipants() {
        UUID guestA = uuid("det-instance-guest-a");
        UUID guestB = uuid("det-instance-guest-b");

        DungeonInstance first = instanceWithParticipants(
                "det-instance-first",
                List.of(guestB, guestA)
        );
        DungeonInstance second = instanceWithParticipants(
                "det-instance-second",
                List.of(guestA, guestB)
        );

        assertEquals(
                encode(first).getAsJsonObject().get("participants"),
                encode(second).getAsJsonObject().get("participants"),
                "DungeonInstance participant arrays are deterministic"
        );
    }

    private static void testPhysicalMembershipReconcileUnchanged() {
        AtomicInteger dirtyCount = new AtomicInteger();
        DungeonSessionStore store = new DungeonSessionStore(dirtyCount::incrementAndGet);
        UUID player = uuid("reconcile-player");
        DungeonInstanceId instanceA = instanceId("reconcile-instance-a");
        DungeonSession sessionA = sessionWithPhysical(
                "reconcile-session-a",
                instanceA,
                new DungeonSiteKey(1, 1),
                player
        );

        store.add(sessionA);
        int before = dirtyCount.get();

        assertTrue(store.reconcilePhysicalParticipant(instanceA, player),
                "unchanged physical membership reconciles to the same target");
        assertTrue(sessionA.isPhysicalParticipant(player),
                "unchanged target still contains the player");
        assertEquals(Set.of(instanceA), store.indexedPhysicalInstanceIds(player),
                "unchanged index reports only the target instance");
        assertEquals(before, dirtyCount.get(),
                "unchanged physical membership does not mark SavedData dirty");
    }

    private static void testPhysicalMembershipReconcileMultiSession() {
        AtomicInteger dirtyCount = new AtomicInteger();
        DungeonSessionStore store = new DungeonSessionStore(dirtyCount::incrementAndGet);
        UUID player = uuid("reconcile-player");
        DungeonInstanceId instanceA = instanceId("reconcile-instance-a");
        DungeonInstanceId instanceB = instanceId("reconcile-instance-b");
        DungeonSession sessionA = sessionWithPhysical(
                "reconcile-session-a",
                instanceA,
                new DungeonSiteKey(1, 1),
                player
        );
        DungeonSession sessionB = sessionWithPhysical(
                "reconcile-session-b",
                instanceB,
                new DungeonSiteKey(1, 2),
                player
        );

        store.add(sessionA);
        store.add(sessionB);
        int before = dirtyCount.get();

        assertTrue(store.reconcilePhysicalParticipant(instanceB, player),
                "multi-session stale physical membership reconciles to B");
        assertEquals(before + 1, dirtyCount.get(),
                "first actual reconciliation marks SavedData dirty exactly once");
        assertFalse(sessionA.isPhysicalParticipant(player),
                "stale session A no longer has the player physically");
        assertTrue(sessionB.isPhysicalParticipant(player),
                "target session B keeps the player physically");
        assertEquals(Set.of(instanceB), store.indexedPhysicalInstanceIds(player),
                "physical index reports only B after reconciliation");

        assertTrue(store.reconcilePhysicalParticipant(instanceB, player),
                "repeated reconciliation remains successful");
        assertEquals(before + 1, dirtyCount.get(),
                "repeated identical reconciliation does not dirty again");
    }

    private static void testPhysicalMembershipReconcileTripleSession() {
        AtomicInteger dirtyCount = new AtomicInteger();
        DungeonSessionStore store = new DungeonSessionStore(dirtyCount::incrementAndGet);
        UUID player = uuid("reconcile-player");
        DungeonInstanceId instanceA = instanceId("reconcile-instance-a");
        DungeonInstanceId instanceB = instanceId("reconcile-instance-b");
        DungeonInstanceId instanceC = instanceId("reconcile-instance-c");
        DungeonSession sessionA = sessionWithPhysical(
                "reconcile-session-a",
                instanceA,
                new DungeonSiteKey(1, 1),
                player
        );
        DungeonSession sessionB = sessionWithPhysical(
                "reconcile-session-b",
                instanceB,
                new DungeonSiteKey(1, 2),
                player
        );
        DungeonSession sessionC = sessionWithPhysical(
                "reconcile-session-c",
                instanceC,
                new DungeonSiteKey(1, 3),
                player
        );

        store.add(sessionA);
        store.add(sessionB);
        store.add(sessionC);
        int before = dirtyCount.get();

        assertTrue(store.reconcilePhysicalParticipant(instanceC, player),
                "triple-session stale physical membership reconciles to C");
        assertEquals(before + 1, dirtyCount.get(),
                "triple-session reconciliation marks dirty once");
        assertFalse(sessionA.isPhysicalParticipant(player),
                "A is removed from physical membership");
        assertFalse(sessionB.isPhysicalParticipant(player),
                "B is removed from physical membership");
        assertTrue(sessionC.isPhysicalParticipant(player),
                "C retains physical membership");
        assertEquals(Set.of(instanceC), store.indexedPhysicalInstanceIds(player),
                "physical index reports only C after triple reconciliation");
    }

    private static void testPhysicalMembershipReconcileDanglingIndex() {
        AtomicInteger dirtyCount = new AtomicInteger();
        DungeonSessionStore store = new DungeonSessionStore(dirtyCount::incrementAndGet);
        UUID player = uuid("reconcile-player");
        DungeonInstanceId instanceA = instanceId("reconcile-instance-a");
        DungeonSession sessionA = sessionWithPhysical(
                "reconcile-session-a",
                instanceA,
                new DungeonSiteKey(1, 1),
                player
        );
        store.add(sessionA);
        injectDanglingPhysicalIndex(store, player, UUID.randomUUID());
        int before = dirtyCount.get();

        assertTrue(store.reconcilePhysicalParticipant(instanceA, player),
                "dangling index rebuild keeps target membership");
        assertEquals(Set.of(instanceA), store.indexedPhysicalInstanceIds(player),
                "dangling physical index entry is removed by rebuild");
        assertEquals(before, dirtyCount.get(),
                "dangling in-memory index rebuild does not mark SavedData dirty");
    }

    private static void testPhysicalMembershipReconcileMissingTarget() {
        AtomicInteger dirtyCount = new AtomicInteger();
        DungeonSessionStore store = new DungeonSessionStore(dirtyCount::incrementAndGet);
        UUID player = uuid("reconcile-player");

        assertFalse(store.reconcilePhysicalParticipant(
                        instanceId("reconcile-missing-instance"),
                        player
                ),
                "missing target session returns false");
        assertEquals(Set.of(), store.indexedPhysicalInstanceIds(player),
                "missing target does not create physical membership");
        assertEquals(0, dirtyCount.get(),
                "missing target reconciliation does not mark SavedData dirty");
    }

    private static void testPortalEntrySucceededReconcilesOldPhysicalMembership() {
        AtomicInteger dirtyCount = new AtomicInteger();
        DungeonSessionStore store = new DungeonSessionStore(dirtyCount::incrementAndGet);
        UUID player = uuid("portal-entry-player");
        DungeonInstanceId instanceA = instanceId("portal-entry-instance-a");
        DungeonInstanceId instanceB = instanceId("portal-entry-instance-b");
        DungeonSession sessionA = sessionWithPhysical(
                "portal-entry-session-a",
                instanceA,
                new DungeonSiteKey(1, 1),
                player
        );
        DungeonSession sessionB = session(
                "portal-entry-session-b",
                instanceB,
                new DungeonSiteKey(1, 2),
                player,
                SessionAccessPolicy.OPEN,
                0L
        );

        store.add(sessionA);
        store.add(sessionB);
        int before = dirtyCount.get();

        assertTrue(store.markPortalEntrySucceeded(sessionB, player, 100L),
                "portal entry finalizes target session");
        assertEquals(before + 1, dirtyCount.get(),
                "portal entry over stale physical membership dirties exactly once");
        assertFalse(sessionA.isPhysicalParticipant(player),
                "old session physical membership is removed by portal entry");
        assertTrue(sessionB.isPhysicalParticipant(player),
                "target session physical membership is established by portal entry");
        assertTrue(sessionB.isParticipant(player),
                "target session logical membership is established by portal entry");
        assertEquals(Set.of(instanceB), store.indexedPhysicalInstanceIds(player),
                "physical index references only the portal entry target");

        assertFalse(store.markPortalEntrySucceeded(sessionB, player, 100L),
                "identical portal entry finalization is idempotent");
        assertEquals(before + 1, dirtyCount.get(),
                "idempotent portal entry does not mark SavedData dirty");
    }

    private static void testUnregisterPhysicalParticipantFromAllMultiSession() {
        AtomicInteger dirtyCount = new AtomicInteger();
        DungeonSessionStore store = new DungeonSessionStore(dirtyCount::incrementAndGet);
        UUID player = uuid("unregister-all-player");
        DungeonSession sessionA = sessionWithPhysical(
                "unregister-all-session-a",
                instanceId("unregister-all-instance-a"),
                new DungeonSiteKey(1, 1),
                player
        );
        DungeonSession sessionB = sessionWithPhysical(
                "unregister-all-session-b",
                instanceId("unregister-all-instance-b"),
                new DungeonSiteKey(1, 2),
                player
        );
        DungeonSession sessionC = sessionWithPhysical(
                "unregister-all-session-c",
                instanceId("unregister-all-instance-c"),
                new DungeonSiteKey(1, 3),
                player
        );

        store.add(sessionA);
        store.add(sessionB);
        store.add(sessionC);
        int before = dirtyCount.get();

        assertEquals(3, store.unregisterPhysicalParticipantFromAll(player),
                "all persisted physical memberships are removed");
        assertFalse(sessionA.isPhysicalParticipant(player),
                "A physical membership is cleared");
        assertFalse(sessionB.isPhysicalParticipant(player),
                "B physical membership is cleared");
        assertFalse(sessionC.isPhysicalParticipant(player),
                "C physical membership is cleared");
        assertEquals(Set.of(), store.indexedPhysicalInstanceIds(player),
                "physical index is empty after unregister-all");
        assertEquals(before + 1, dirtyCount.get(),
                "unregister-all dirties exactly once");
    }

    private static void testUnregisterPhysicalParticipantFromAllDanglingIndex() {
        AtomicInteger dirtyCount = new AtomicInteger();
        DungeonSessionStore store = new DungeonSessionStore(dirtyCount::incrementAndGet);
        UUID player = uuid("unregister-all-dangling-player");
        DungeonSession session = session(
                "unregister-all-dangling-session",
                instanceId("unregister-all-dangling-instance"),
                new DungeonSiteKey(1, 1),
                player,
                SessionAccessPolicy.OPEN,
                0L
        );

        store.add(session);
        injectDanglingPhysicalIndex(store, player, UUID.randomUUID());
        int before = dirtyCount.get();

        assertEquals(0, store.unregisterPhysicalParticipantFromAll(player),
                "dangling-only cleanup does not mutate persisted state");
        assertEquals(Set.of(), store.indexedPhysicalInstanceIds(player),
                "dangling index entry is cleared");
        assertEquals(before, dirtyCount.get(),
                "dangling index cleanup does not mark SavedData dirty");
    }

    @SuppressWarnings("unchecked")
    private static void injectDanglingPhysicalIndex(
            DungeonSessionStore store,
            UUID playerId,
            UUID sessionId
    ) {
        try {
            Field field = DungeonSessionStore.class.getDeclaredField(
                    "sessionIdsByPhysicalParticipant"
            );
            field.setAccessible(true);
            Map<UUID, Set<UUID>> index =
                    (Map<UUID, Set<UUID>>) field.get(store);
            index.computeIfAbsent(playerId, ignored -> new HashSet<>())
                    .add(sessionId);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(
                    "Could not inject dangling physical index test seam",
                    exception
            );
        }
    }

    private static DungeonSite site(
            DungeonSiteKey key,
            int offset
    ) {
        DungeonRoomId roomId = DungeonRoomId.of("start_" + offset);
        DungeonBounds bounds = new DungeonBounds(
                offset,
                40,
                offset,
                offset + 15,
                90,
                offset + 15
        );
        BlockPos startPos = new BlockPos(offset + 1, 64, offset + 1);
        DungeonGeneratedRoom room = new DungeonGeneratedRoom(
                roomId,
                DungeonRoomType.START,
                bounds,
                startPos
        );

        return new DungeonSite(key, bounds, roomId, startPos, List.of(room));
    }

    private static DungeonInstanceId instanceId(String name) {
        return new DungeonInstanceId(uuid(name));
    }

    private static DungeonTerritoryId territoryId(String name) {
        return new DungeonTerritoryId(uuid(name));
    }

    private static DungeonSession session(
            String sessionName,
            DungeonInstanceId instanceId,
            DungeonSiteKey siteKey,
            UUID starter,
            SessionAccessPolicy accessPolicy,
            long createdGameTime
    ) {
        return new DungeonSession(
                uuid(sessionName),
                instanceId,
                starter,
                siteKey,
                DungeonSessionState.ACTIVE,
                accessPolicy,
                Set.of(starter),
                Set.of(),
                Set.of(),
                createdGameTime,
                createdGameTime,
                false
        );
    }

    private static DungeonSession sessionWithPhysical(
            String sessionName,
            DungeonInstanceId instanceId,
            DungeonSiteKey siteKey,
            UUID playerId
    ) {
        return new DungeonSession(
                uuid(sessionName),
                instanceId,
                playerId,
                siteKey,
                DungeonSessionState.ACTIVE,
                SessionAccessPolicy.OPEN,
                Set.of(playerId),
                Set.of(playerId),
                Set.of(),
                0L,
                0L,
                false
        );
    }

    private static DungeonSession sessionWithSets(
            String sessionName,
            DungeonInstanceId instanceId,
            UUID starter,
            List<UUID> participants,
            List<UUID> physicalParticipants,
            List<UUID> spawnedEntityIds
    ) {
        return new DungeonSession(
                uuid(sessionName),
                instanceId,
                starter,
                new DungeonSiteKey(0, 0),
                DungeonSessionState.ACTIVE,
                SessionAccessPolicy.OPEN,
                participants,
                physicalParticipants,
                spawnedEntityIds,
                5L,
                5L,
                false
        );
    }

    private static DungeonInstance instanceWithParticipants(
            String name,
            List<UUID> participants
    ) {
        DungeonInstance instance = new DungeonInstance(
                new DungeonInstanceId(uuid(name)),
                new DungeonSiteKey(0, 0),
                new DungeonDifficulty(1, 0.0F, 1.0F, 1),
                new DungeonTerritoryId(uuid(name + "-territory")),
                new BlockPos(0, 64, 0),
                0L
        );
        participants.forEach(instance::addParticipant);
        return instance;
    }

    private static JsonElement encode(DungeonSession session) {
        return DungeonSession.CODEC.encodeStart(JsonOps.INSTANCE, session)
                .getOrThrow();
    }

    private static JsonElement encode(DungeonInstance instance) {
        return DungeonInstance.CODEC.encodeStart(JsonOps.INSTANCE, instance)
                .getOrThrow();
    }

    private static UUID uuid(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertTrue(
            boolean value,
            String message
    ) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(
            boolean value,
            String message
    ) {
        if (value) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(
            Object expected,
            Object actual,
            String message
    ) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertThrows(
            Class<? extends RuntimeException> expectedType,
            Runnable action,
            String message
    ) {
        try {
            action.run();
        } catch (RuntimeException expected) {
            if (expectedType.isInstance(expected)) {
                return;
            }

            throw new AssertionError(
                    message
                            + ": expected "
                            + expectedType.getSimpleName()
                            + ", got "
                            + expected.getClass().getSimpleName(),
                    expected
            );
        }

        throw new AssertionError(message + ": expected " + expectedType.getSimpleName());
    }
}
