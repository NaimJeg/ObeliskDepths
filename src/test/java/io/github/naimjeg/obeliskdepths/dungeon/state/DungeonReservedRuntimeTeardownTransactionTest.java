package io.github.naimjeg.obeliskdepths.dungeon.state;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonDifficulty;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstanceCreation;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstanceFactory;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidInstance;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomState;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSession;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSessionState;
import io.github.naimjeg.obeliskdepths.dungeon.session.SessionAccessPolicy;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonGeneratedRoom;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteRecord;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteUsageStatus;
import io.github.naimjeg.obeliskdepths.dungeon.state.store.DungeonSiteStore;
import io.github.naimjeg.obeliskdepths.dungeon.territory.DungeonBounds;
import io.github.naimjeg.obeliskdepths.dungeon.territory.DungeonTerritory;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class DungeonReservedRuntimeTeardownTransactionTest {
    private static final long RESERVED_AT = 100L;
    private static final long RETIRED_AT = 500L;

    private static final Identifier TEST_RAID_TYPE =
            Identifier.fromNamespaceAndPath(
                    "obeliskdepths",
                    "test_teardown"
            );

    private DungeonReservedRuntimeTeardownTransactionTest() {
    }

    public static void main(String[] args) {
        missingInstanceReturnsFalse();
        incompleteAggregateFailsBeforeMutation();
        releaseRemovesCompleteRuntimeAggregate();
        retirementAcceptsAlreadyCleanedSession();
        failureAfterEveryMutationBoundaryRollsBack();
        siteRestorationRejectsConflictingSnapshot();
    }

    private static void missingInstanceReturnsFalse() {
        DungeonManagerSavedData data =
                new DungeonManagerSavedData();
        DungeonInstanceId missing =
                new DungeonInstanceId(uuid("missing-instance"));

        assertFalse(
                data.releaseReservedDungeonRuntime(missing),
                "missing release must return false"
        );
        assertFalse(
                data.retireReservedDungeonRuntime(
                        missing,
                        DungeonSiteUsageStatus.COMPLETED,
                        RETIRED_AT
                ),
                "missing retirement must return false"
        );
    }

    private static void incompleteAggregateFailsBeforeMutation() {
        DungeonManagerSavedData data =
                new DungeonManagerSavedData();

        DungeonSite site = createSite(
                new DungeonSiteKey(30, 31),
                480,
                496
        );
        DungeonInstanceCreation creation =
                DungeonInstanceFactory.create(
                        difficulty(),
                        site,
                        RESERVED_AT
                );

        data.instances().put(creation.instance());

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> data.releaseReservedDungeonRuntime(
                        creation.instance().id()
                ),
                "incomplete aggregate must fail"
        );

        assertContains(
                failure.getMessage(),
                "Incomplete reserved dungeon aggregate",
                "incomplete aggregate diagnostic"
        );
        assertSame(
                creation.instance(),
                data.instances()
                        .get(creation.instance().id())
                        .orElseThrow(),
                "failed prevalidation must not remove instance"
        );
    }

    private static void releaseRemovesCompleteRuntimeAggregate() {
        Fixture fixture = createFixture(step -> {
        });

        assertTrue(
                fixture.data().releaseReservedDungeonRuntime(
                        fixture.instance().id()
                ),
                "release must succeed"
        );

        assertRuntimeRemoved(fixture);

        assertTrue(
                fixture.data().sites().isUnreached(
                        fixture.site().key()
                ),
                "released site must become unreached"
        );
        assertTrue(
                fixture.data().sites()
                        .record(fixture.site().key())
                        .isEmpty(),
                "released site record must be removed"
        );
        assertTrue(
                fixture.data().sites()
                        .reservedState(
                                fixture.instance().id(),
                                fixture.site().key()
                        )
                        .isEmpty(),
                "released reservation indexes and snapshot must be removed"
        );

        assertEquals(
                DungeonSessionState.CLEANED,
                fixture.session().state(),
                "release must clean session"
        );
        assertFalse(
                fixture.session().tributeBonusActive(),
                "release must disable tribute bonus"
        );
    }

    private static void retirementAcceptsAlreadyCleanedSession() {
        Fixture fixture = createFixture(step -> {
        });

        assertTrue(
                fixture.data().sessions().markCleaned(
                        fixture.session()
                ),
                "pre-clean must mutate active session"
        );

        var cleanupSnapshot =
                fixture.data().sessions()
                        .captureForCleanup(fixture.instance().id())
                        .orElseThrow();

        assertFalse(
                cleanupSnapshot.requiresCleanupMutation(),
                "cleaned snapshot must be a transaction no-op"
        );
        assertTrue(
                fixture.data().sessions()
                        .markCleanedForTransaction(cleanupSnapshot),
                "already-cleaned transaction validation must succeed"
        );

        assertTrue(
                fixture.data().retireReservedDungeonRuntime(
                        fixture.instance().id(),
                        DungeonSiteUsageStatus.COMPLETED,
                        RETIRED_AT
                ),
                "retirement must accept a previously cleaned session"
        );

        assertRuntimeRemoved(fixture);

        DungeonSiteRecord retiredRecord =
                fixture.data().sites()
                        .record(fixture.site().key())
                        .orElseThrow();

        assertEquals(
                DungeonSiteUsageStatus.COMPLETED,
                retiredRecord.status(),
                "retirement status"
        );
        assertEquals(
                RESERVED_AT,
                retiredRecord.firstReservedGameTime(),
                "retirement must preserve first reservation time"
        );
        assertEquals(
                RETIRED_AT,
                retiredRecord.lastUpdatedGameTime(),
                "retirement update time"
        );
        assertTrue(
                retiredRecord.activeInstanceId().isEmpty(),
                "terminal record must not retain active instance"
        );
        assertTrue(
                fixture.data().sites()
                        .reservedState(
                                fixture.instance().id(),
                                fixture.site().key()
                        )
                        .isEmpty(),
                "retirement must remove live reservation state"
        );
    }

    private static void failureAfterEveryMutationBoundaryRollsBack() {
        for (DungeonManagerSavedData.TeardownStep failureStep
                : DungeonManagerSavedData.TeardownStep.values()) {
            Fixture fixture = createFixture(step -> {
                if (step == failureStep) {
                    throw new InjectedFailure(step);
                }
            });

            InjectedFailure failure = assertThrows(
                    InjectedFailure.class,
                    () -> fixture.data()
                            .retireReservedDungeonRuntime(
                                    fixture.instance().id(),
                                    DungeonSiteUsageStatus.COMPLETED,
                                    RETIRED_AT
                            ),
                    "injected failure after " + failureStep
            );

            assertEquals(
                    failureStep,
                    failure.step(),
                    "injected failure step"
            );

            assertAggregateRestored(
                    fixture,
                    "rollback after " + failureStep
            );
        }
    }

    private static void siteRestorationRejectsConflictingSnapshot() {
        DungeonSiteStore store =
                new DungeonSiteStore(() -> {
                });
        DungeonInstanceId instanceId =
                new DungeonInstanceId(uuid("site-conflict-instance"));
        DungeonSiteKey siteKey =
                new DungeonSiteKey(80, 81);

        DungeonSite original =
                createSite(siteKey, 1280, 1296);

        store.reserve(original, instanceId, RESERVED_AT);

        DungeonSiteStore.ReservedSiteState originalState =
                store.reservedState(instanceId, siteKey)
                        .orElseThrow();

        assertTrue(
                store.retireReservation(
                        instanceId,
                        siteKey,
                        DungeonSiteUsageStatus.COMPLETED,
                        RETIRED_AT
                ),
                "setup retirement"
        );

        DungeonSite conflicting =
                createSite(siteKey, 1600, 1616);
        store.loadSnapshots(List.of(conflicting));

        assertThrows(
                IllegalStateException.class,
                () -> store.restoreReservedStateForTransaction(
                        instanceId,
                        originalState
                ),
                "restore must reject a different snapshot"
        );

        assertEquals(
                conflicting,
                only(store.snapshots()),
                "conflicting snapshot must not be overwritten"
        );
    }

    private static Fixture createFixture(
            DungeonManagerSavedData.TeardownTransactionObserver observer
    ) {
        DungeonManagerSavedData data =
                new DungeonManagerSavedData(observer);

        DungeonSite site = createSite(
                new DungeonSiteKey(10, 11),
                160,
                176
        );

        DungeonInstanceCreation creation =
                DungeonInstanceFactory.create(
                        difficulty(),
                        site,
                        RESERVED_AT
                );

        DungeonInstance instance = creation.instance();
        DungeonTerritory territory = creation.territory();

        data.territories().put(territory);
        data.instances().put(instance);
        data.sites().reserve(
                site,
                instance.id(),
                RESERVED_AT
        );
        data.roomStates().initializeRoomStates(instance, site);

        DungeonRaidInstance raid =
                data.raids().createEncounter(
                        instance.id(),
                        TEST_RAID_TYPE,
                        4,
                        2,
                        RESERVED_AT
                );

        DungeonSession session =
                DungeonSession.createActive(
                        instance,
                        uuid("starter"),
                        SessionAccessPolicy.OPEN,
                        true,
                        RESERVED_AT
                );
        data.sessions().add(session);

        DungeonSiteStore.ReservedSiteState siteState =
                data.sites()
                        .reservedState(instance.id(), site.key())
                        .orElseThrow();

        List<DungeonRoomState> roomStates =
                List.copyOf(
                        data.roomStates()
                                .allForInstance(instance.id())
                );

        return new Fixture(
                data,
                site,
                instance,
                territory,
                session,
                raid,
                roomStates,
                siteState
        );
    }

    private static void assertRuntimeRemoved(Fixture fixture) {
        DungeonManagerSavedData data = fixture.data();
        DungeonInstanceId instanceId = fixture.instance().id();

        assertTrue(
                data.instances().get(instanceId).isEmpty(),
                "instance must be removed"
        );
        assertTrue(
                data.territories()
                        .get(fixture.territory().id())
                        .isEmpty(),
                "territory must be removed"
        );
        assertTrue(
                data.roomStates()
                        .allForInstance(instanceId)
                        .isEmpty(),
                "room states must be removed"
        );
        assertTrue(
                data.raids()
                        .allForInstance(instanceId)
                        .isEmpty(),
                "raids must be removed"
        );
    }

    private static void assertAggregateRestored(
            Fixture fixture,
            String context
    ) {
        DungeonManagerSavedData data = fixture.data();
        DungeonInstanceId instanceId = fixture.instance().id();

        assertSame(
                fixture.instance(),
                data.instances().get(instanceId).orElseThrow(),
                context + ": instance"
        );
        assertEquals(
                fixture.territory(),
                data.territories()
                        .get(fixture.territory().id())
                        .orElseThrow(),
                context + ": territory"
        );

        assertRoomStateIdentities(
                fixture.roomStates(),
                data.roomStates().allForInstance(instanceId),
                context + ": room states"
        );

        DungeonRaidInstance restoredRaid =
                data.raids().get(fixture.raid().id())
                        .orElseThrow();

        assertSame(
                fixture.raid(),
                restoredRaid,
                context + ": raid"
        );

        assertEquals(
                DungeonSessionState.ACTIVE,
                fixture.session().state(),
                context + ": session state"
        );
        assertTrue(
                fixture.session().tributeBonusActive(),
                context + ": tribute bonus"
        );

        DungeonSiteStore.ReservedSiteState restoredSite =
                data.sites()
                        .reservedState(
                                instanceId,
                                fixture.site().key()
                        )
                        .orElseThrow();

        assertEquals(
                fixture.originalSiteState(),
                restoredSite,
                context + ": reserved site state"
        );
        assertTrue(
                data.requireReservedDungeon(
                        instanceId,
                        fixture.site().key()
                ).isPresent(),
                context + ": aggregate validation"
        );
    }

    private static void assertRoomStateIdentities(
            Collection<DungeonRoomState> expected,
            Collection<DungeonRoomState> actual,
            String context
    ) {
        Set<DungeonRoomId> expectedIds = new HashSet<>();
        Set<DungeonRoomId> actualIds = new HashSet<>();

        for (DungeonRoomState state : expected) {
            expectedIds.add(state.roomId());
        }
        for (DungeonRoomState state : actual) {
            actualIds.add(state.roomId());
        }

        assertEquals(
                expectedIds,
                actualIds,
                context + " ids"
        );

        for (DungeonRoomState expectedState : expected) {
            DungeonRoomState actualState = actual.stream()
                    .filter(candidate ->
                            candidate.roomId().equals(
                                    expectedState.roomId()
                            ))
                    .findFirst()
                    .orElseThrow();

            assertSame(
                    expectedState,
                    actualState,
                    context + " object " + expectedState.roomId()
            );
        }
    }

    private static DungeonSite createSite(
            DungeonSiteKey key,
            int minX,
            int minZ
    ) {
        DungeonRoomId startRoomId =
                DungeonRoomId.of("start");
        DungeonRoomId combatRoomId =
                DungeonRoomId.of("combat");

        DungeonBounds startBounds =
                new DungeonBounds(
                        minX,
                        48,
                        minZ,
                        minX + 15,
                        80,
                        minZ + 15
                );

        DungeonBounds combatBounds =
                new DungeonBounds(
                        minX + 16,
                        48,
                        minZ,
                        minX + 31,
                        80,
                        minZ + 15
                );

        DungeonBounds siteBounds =
                new DungeonBounds(
                        minX,
                        48,
                        minZ,
                        minX + 31,
                        80,
                        minZ + 15
                );

        BlockPos startPos =
                new BlockPos(minX + 4, 64, minZ + 4);

        return new DungeonSite(
                key,
                siteBounds,
                startRoomId,
                startPos,
                List.of(
                        new DungeonGeneratedRoom(
                                startRoomId,
                                DungeonRoomType.START,
                                startBounds,
                                startPos
                        ),
                        new DungeonGeneratedRoom(
                                combatRoomId,
                                DungeonRoomType.COMBAT,
                                combatBounds,
                                new BlockPos(
                                        minX + 20,
                                        64,
                                        minZ + 4
                                )
                        )
                )
        );
    }

    private static DungeonDifficulty difficulty() {
        return new DungeonDifficulty(
                1,
                0.0F,
                1.0F,
                1
        );
    }

    private static UUID uuid(String value) {
        return UUID.nameUUIDFromBytes(
                value.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static <T> T only(Collection<T> values) {
        if (values.size() != 1) {
            throw new AssertionError(
                    "Expected one value, found " + values.size()
            );
        }
        return values.iterator().next();
    }

    private static void assertTrue(
            boolean condition,
            String message
    ) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(
            boolean condition,
            String message
    ) {
        if (condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertSame(
            Object expected,
            Object actual,
            String message
    ) {
        if (expected != actual) {
            throw new AssertionError(
                    message
                            + ": expected same object, expected="
                            + expected
                            + " actual="
                            + actual
            );
        }
    }

    private static void assertEquals(
            Object expected,
            Object actual,
            String message
    ) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(
                    message
                            + ": expected="
                            + expected
                            + " actual="
                            + actual
            );
        }
    }

    private static void assertContains(
            String value,
            String token,
            String message
    ) {
        if (value == null || !value.contains(token)) {
            throw new AssertionError(
                    message
                            + ": expected token="
                            + token
                            + " actual="
                            + value
            );
        }
    }

    private static <T extends RuntimeException> T assertThrows(
            Class<T> expectedType,
            Runnable action,
            String message
    ) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            if (expectedType.isInstance(failure)) {
                return expectedType.cast(failure);
            }

            throw new AssertionError(
                    message
                            + ": expected="
                            + expectedType.getName()
                            + " actual="
                            + failure.getClass().getName(),
                    failure
            );
        }

        throw new AssertionError(
                message
                        + ": expected exception "
                        + expectedType.getName()
        );
    }

    private record Fixture(
            DungeonManagerSavedData data,
            DungeonSite site,
            DungeonInstance instance,
            DungeonTerritory territory,
            DungeonSession session,
            DungeonRaidInstance raid,
            List<DungeonRoomState> roomStates,
            DungeonSiteStore.ReservedSiteState originalSiteState
    ) {
        private Fixture {
            roomStates = List.copyOf(roomStates);
        }
    }

    private static final class InjectedFailure
            extends RuntimeException {
        private final DungeonManagerSavedData.TeardownStep step;

        private InjectedFailure(
                DungeonManagerSavedData.TeardownStep step
        ) {
            super("Injected teardown failure after " + step);
            this.step = step;
        }

        private DungeonManagerSavedData.TeardownStep step() {
            return this.step;
        }
    }
}