package io.github.naimjeg.obeliskdepths.dungeon.session;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomState;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomStatus;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import io.github.naimjeg.obeliskdepths.dungeon.state.store.RoomStateStore;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class DungeonKillProgressTest {
    private DungeonKillProgressTest() {
    }

    public static void main(String[] args) {
        testCompletionThreshold();
        testBossBarProgressHelpers();
        testDuplicateKillDeduplication();
        testInitialBossRoomLocked();
        testUnlockBossRoomsIsIdempotent();
    }

    private static void testCompletionThreshold() {
        DungeonKillProgress progress = new DungeonKillProgress(
                100,
                94,
                0.95F
        );

        assertFalse(
                progress.isComplete(),
                "94/100 should not satisfy the 95% threshold"
        );

        assertTrue(
                progress.withAddedKillScore(1).isComplete(),
                "95/100 should satisfy the 95% threshold"
        );

        assertEquals(
                0.95F,
                DungeonKillProgress.empty().completionThreshold(),
                "default completion threshold"
        );
    }

    private static void testBossBarProgressHelpers() {
        DungeonKillProgress empty = DungeonKillProgress.empty();

        assertEquals(
                0,
                empty.targetKillScore(),
                "empty progress has no effective target"
        );
        assertFalse(
                DungeonSessionProgressBarService.shouldDisplayProgress(empty),
                "zero required score should not display progress bar"
        );

        DungeonKillProgress started = new DungeonKillProgress(40, 0, 0.95F);
        assertEquals(38, started.targetKillScore(), "ceil target score");
        assertEquals(1.0F, started.remainingProgress(), "bar starts full");

        DungeonKillProgress complete = new DungeonKillProgress(40, 38, 0.95F);
        assertEquals(0.0F, complete.remainingProgress(), "bar drains empty at target");

        DungeonKillProgress overComplete = new DungeonKillProgress(40, 100, 0.95F);
        assertEquals(
                38,
                overComplete.clampedCurrentKillScore(),
                "current score clamps to target"
        );
        assertEquals(
                0.0F,
                overComplete.remainingProgress(),
                "over-complete progress remains empty"
        );
    }

    private static void testDuplicateKillDeduplication() {
        UUID starter = UUID.nameUUIDFromBytes("starter".getBytes());
        UUID mob = UUID.nameUUIDFromBytes("mob".getBytes());
        DungeonSession session = new DungeonSession(
                UUID.nameUUIDFromBytes("session".getBytes()),
                new DungeonInstanceId(UUID.nameUUIDFromBytes("instance".getBytes())),
                starter,
                new DungeonSiteKey(0, 0),
                DungeonSessionState.ACTIVE,
                DungeonAccessMode.OPEN,
                Set.of(starter),
                Set.of(starter),
                Set.of(),
                DungeonKillProgress.empty(),
                DungeonRewardState.empty(),
                0L,
                0L,
                false
        );

        assertTrue(
                session.registerSpawnedEntity(mob, 1),
                "registered mob should add required score"
        );
        assertTrue(
                session.markSpawnedEntityKilled(mob, 1),
                "first registered kill should add progress"
        );
        assertFalse(
                session.markSpawnedEntityKilled(mob, 1),
                "duplicate registered kill should be ignored"
        );
        assertEquals(
                1,
                session.progress().currentKillScore(),
                "duplicate kill must not increment progress twice"
        );
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
