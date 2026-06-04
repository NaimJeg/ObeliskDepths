package io.github.naimjeg.obeliskdepths.dungeon.session;

import io.github.naimjeg.obeliskdepths.dungeon.encounter.DungeonEncounterDirector;
import io.github.naimjeg.obeliskdepths.dungeon.encounter.DungeonEncounterPhase;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonDifficulty;
import io.github.naimjeg.obeliskdepths.dungeon.raid.BuiltinDungeonRaids;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidId;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidInstance;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidStatus;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomState;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomStatus;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.state.store.RoomStateStore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class DungeonEncounterProgressTest {
    private DungeonEncounterProgressTest() {
    }

    public static void main(String[] args) {
        testBossBarProgressHelpersReadRaidState();
        testEncounterDuplicateResolution();
        testEncounterPhaseTransitions();
        testInitialBossRoomLocked();
        testUnlockBossRoomsIsIdempotent();
    }

    private static void testBossBarProgressHelpersReadRaidState() {
        DungeonRaidInstance runtimeZero = encounterWithProgress(4, 0, DungeonEncounterPhase.COMBAT);
        assertEquals(0.0F, DungeonSessionProgressBarService.completedProgress(runtimeZero), "zero kills displays empty");

        DungeonRaidInstance runtimeHalf = encounterWithProgress(4, 2, DungeonEncounterPhase.COMBAT);
        assertEquals(0.5F, DungeonSessionProgressBarService.completedProgress(runtimeHalf), "half quota displays half full");

        DungeonRaidInstance runtimeFull = encounterWithProgress(4, 4, DungeonEncounterPhase.COMBAT);
        assertEquals(1.0F, DungeonSessionProgressBarService.completedProgress(runtimeFull), "full quota displays full");

        DungeonRaidInstance runtimeNegative = encounterWithProgress(4, -3, DungeonEncounterPhase.COMBAT);
        assertEquals(0.0F, DungeonSessionProgressBarService.completedProgress(runtimeNegative), "negative progress clamps empty");

        DungeonRaidInstance runtimeOver = encounterWithProgress(4, 99, DungeonEncounterPhase.COMBAT);
        assertEquals(1.0F, DungeonSessionProgressBarService.completedProgress(runtimeOver), "over quota clamps full");

        DungeonRaidInstance runtimeZeroTarget = encounterWithProgress(0, 0, DungeonEncounterPhase.COMBAT);
        assertEquals(0.0F, DungeonSessionProgressBarService.completedProgress(runtimeZeroTarget), "zero target does not divide by zero");

        DungeonRaidInstance runtimeBoss = encounterWithProgress(4, 4, DungeonEncounterPhase.BOSS);
        assertEquals(1.0F, DungeonSessionProgressBarService.completedProgress(runtimeBoss), "boss phase preserves completed combat display");
    }

    private static void testEncounterDuplicateResolution() {
        DungeonRaidInstance encounter = DungeonRaidInstance.createInstanceEncounter(
                new DungeonInstanceId(UUID.nameUUIDFromBytes("encounter-instance".getBytes())),
                BuiltinDungeonRaids.COMBAT_ROOM,
                12,
                4,
                0L
        );
        UUID mob = UUID.nameUUIDFromBytes("encounter-mob".getBytes());
        UUID unknownMob = UUID.nameUUIDFromBytes("encounter-unknown-mob".getBytes());

        assertEquals(
                12,
                encounter.normalKillQuota(),
                "encounter stores fixed normal-combat quota"
        );
        assertTrue(encounter.trackMob(mob), "first tracked mob is accepted");
        assertFalse(encounter.resolveMob(unknownMob), "unknown mob resolution is rejected");
        assertTrue(encounter.resolveMob(mob), "first resolution is accepted");
        assertFalse(encounter.resolveMob(mob), "duplicate resolution is ignored");
        assertEquals(
                0,
                encounter.trackedMobIds().size(),
                "resolved mob is removed from living tracked set"
        );
    }

    private static void testEncounterPhaseTransitions() {
        DungeonRaidInstance encounter = DungeonRaidInstance.createInstanceEncounter(
                new DungeonInstanceId(UUID.nameUUIDFromBytes("phase-instance".getBytes())),
                BuiltinDungeonRaids.COMBAT_ROOM,
                DungeonEncounterDirector.fixedNormalKillQuota(
                        new DungeonDifficulty(1, 1.0F, 1.0F, 1)
                ),
                DungeonEncounterDirector.desiredLivingMobCount(
                        new DungeonDifficulty(1, 1.0F, 1.0F, 1)
                ),
                0L
        );

        assertEquals(
                DungeonEncounterPhase.COMBAT,
                encounter.encounterPhase(),
                "new encounter begins in combat phase"
        );
        assertTrue(
                encounter.setEncounterPhase(DungeonEncounterPhase.BOSS),
                "combat transitions to boss once"
        );
        assertFalse(
                encounter.setEncounterPhase(DungeonEncounterPhase.BOSS),
                "boss transition is idempotent"
        );
        assertTrue(encounter.markBossCompleted(), "boss completion is recorded once");
        assertFalse(encounter.markBossCompleted(), "boss completion is idempotent");
        assertTrue(encounter.markEncounterComplete(), "complete transition updates encounter");
        assertEquals(
                DungeonEncounterPhase.COMPLETE,
                encounter.encounterPhase(),
                "complete transition sets phase"
        );
        assertEquals(
                DungeonRaidStatus.WON,
                encounter.status(),
                "complete transition sets raid status"
        );
        assertTrue(encounter.isTerminal(), "complete encounter is terminal");

        DungeonRaidInstance expired = DungeonRaidInstance.createInstanceEncounter(
                new DungeonInstanceId(UUID.nameUUIDFromBytes("expired-instance".getBytes())),
                BuiltinDungeonRaids.COMBAT_ROOM,
                12,
                4,
                0L
        );
        assertTrue(expired.markEncounterExpired(), "expired transition updates encounter");
        assertEquals(
                DungeonEncounterPhase.EXPIRED,
                expired.encounterPhase(),
                "expired transition sets phase"
        );
        assertEquals(
                DungeonRaidStatus.EXPIRED,
                expired.status(),
                "expired transition sets raid status"
        );
        assertTrue(expired.isTerminal(), "expired encounter is terminal");

        DungeonRaidInstance failed = DungeonRaidInstance.createInstanceEncounter(
                new DungeonInstanceId(UUID.nameUUIDFromBytes("failed-instance".getBytes())),
                BuiltinDungeonRaids.COMBAT_ROOM,
                12,
                4,
                0L
        );
        assertTrue(failed.markEncounterFailed(), "failed transition updates encounter");
        assertEquals(
                DungeonEncounterPhase.FAILED,
                failed.encounterPhase(),
                "failed transition sets phase"
        );
        assertEquals(
                DungeonRaidStatus.FAILED,
                failed.status(),
                "failed transition sets raid status"
        );
        assertTrue(failed.isTerminal(), "failed encounter is terminal");
    }

    private static void testInitialBossRoomLocked() {
        DungeonInstanceId instanceId =
                new DungeonInstanceId(UUID.nameUUIDFromBytes("initial-instance".getBytes()));

        assertEquals(
                DungeonRoomStatus.DISCOVERED,
                DungeonRoomState.initial(
                        instanceId,
                        DungeonRoomType.START,
                        DungeonRoomId.of("start")
                ).status(),
                "START rooms begin discovered"
        );
        assertEquals(
                DungeonRoomStatus.LOCKED,
                DungeonRoomState.initial(
                        instanceId,
                        DungeonRoomType.BOSS,
                        DungeonRoomId.of("boss")
                ).status(),
                "BOSS rooms begin locked"
        );
    }

    private static void testUnlockBossRoomsIsIdempotent() {
        int[] dirtyCount = {0};
        RoomStateStore store = new RoomStateStore(() -> dirtyCount[0]++);
        DungeonInstanceId instanceId =
                new DungeonInstanceId(UUID.nameUUIDFromBytes("unlock-instance".getBytes()));
        DungeonRoomId bossRoomId = DungeonRoomId.of("boss");

        store.load(List.of(
                new DungeonRoomState(
                        instanceId,
                        bossRoomId,
                        DungeonRoomType.BOSS,
                        DungeonRoomStatus.LOCKED,
                        false
                ),
                new DungeonRoomState(
                        instanceId,
                        DungeonRoomId.of("combat"),
                        DungeonRoomType.COMBAT,
                        DungeonRoomStatus.UNDISCOVERED,
                        false
                )
        ));

        assertTrue(store.unlockBossRooms(instanceId), "first boss unlock changes state");
        assertEquals(1, dirtyCount[0], "unlock marks dirty once");
        assertEquals(
                DungeonRoomStatus.UNDISCOVERED,
                store.get(instanceId, bossRoomId).orElseThrow().status(),
                "boss room becomes accessible but undiscovered"
        );
        assertFalse(store.unlockBossRooms(instanceId), "second boss unlock is idempotent");
        assertEquals(1, dirtyCount[0], "idempotent unlock does not mark dirty again");
    }

    private static DungeonRaidInstance encounterWithProgress(
            int quota,
            int credited,
            DungeonEncounterPhase phase
    ) {
        return new DungeonRaidInstance(
                DungeonRaidId.create(),
                new DungeonInstanceId(UUID.nameUUIDFromBytes(("progress-" + quota + "-" + credited + "-" + phase).getBytes())),
                Optional.empty(),
                BuiltinDungeonRaids.COMBAT_ROOM,
                DungeonRaidStatus.ACTIVE,
                0,
                0L,
                0,
                0,
                phase,
                quota,
                credited,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                0L,
                0,
                false
        );
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
            float expected,
            float actual,
            String message
    ) {
        if (Float.compare(expected, actual) != 0) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(
            int expected,
            int actual,
            String message
    ) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
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
}
