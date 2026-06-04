package io.github.naimjeg.obeliskdepths.dungeon.presence;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSession;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSessionState;
import io.github.naimjeg.obeliskdepths.dungeon.session.SessionAccessPolicy;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import io.github.naimjeg.obeliskdepths.dungeon.state.store.DungeonSessionStore;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class DungeonPhysicalPresenceServiceTest {
    private DungeonPhysicalPresenceServiceTest() {
    }

    public static void main(String[] args) {
        testFailedCurrentRecordingClearsOldPhysicalMembership();
        testNoCurrentOwnerClearsPhysicalMembership();
    }

    private static void testFailedCurrentRecordingClearsOldPhysicalMembership() {
        AtomicInteger dirtyCount = new AtomicInteger();
        DungeonSessionStore store = new DungeonSessionStore(dirtyCount::incrementAndGet);
        UUID player = stableUuid("failed-presence-player");
        DungeonInstanceId instanceA =
                new DungeonInstanceId(stableUuid("failed-presence-instance-a"));
        DungeonSession sessionA = physicalSession(
                "failed-presence-session-a",
                instanceA,
                player
        );
        DungeonInstanceId instanceB =
                new DungeonInstanceId(stableUuid("failed-presence-instance-b"));

        store.add(sessionA);
        AtomicInteger recordCount = new AtomicInteger();
        AtomicInteger clearCount = new AtomicInteger();

        DungeonPhysicalPresenceService.resolveCurrentPhysicalPresence(
                true,
                Optional.of(instanceB),
                instanceId -> {
                    recordCount.incrementAndGet();
                    return false;
                },
                () -> {
                    clearCount.incrementAndGet();
                    store.unregisterPhysicalParticipantFromAll(player);
                }
        );

        assertFalse(sessionA.isPhysicalParticipant(player),
                "failed current recording removes old physical membership");
        assertNoPhysicalMembership(store, player,
                "failed current recording leaves no physical membership");
        assertEquals(1, recordCount.get(),
                "current B recording is attempted once");
        assertEquals(1, clearCount.get(),
                "failed current recording clears physical membership once");
        assertEquals(2, dirtyCount.get(),
                "old A physical removal marks store dirty once");
    }

    private static void testNoCurrentOwnerClearsPhysicalMembership() {
        AtomicInteger dirtyCount = new AtomicInteger();
        DungeonSessionStore store = new DungeonSessionStore(dirtyCount::incrementAndGet);
        UUID player = stableUuid("no-owner-player");
        DungeonInstanceId instanceA =
                new DungeonInstanceId(stableUuid("no-owner-instance-a"));
        DungeonSession sessionA = physicalSession(
                "no-owner-session-a",
                instanceA,
                player
        );

        store.add(sessionA);
        AtomicInteger recordCount = new AtomicInteger();
        AtomicInteger clearCount = new AtomicInteger();

        DungeonPhysicalPresenceService.resolveCurrentPhysicalPresence(
                true,
                Optional.empty(),
                instanceId -> {
                    recordCount.incrementAndGet();
                    return true;
                },
                () -> {
                    clearCount.incrementAndGet();
                    store.unregisterPhysicalParticipantFromAll(player);
                }
        );

        assertFalse(sessionA.isPhysicalParticipant(player),
                "missing current owner removes old physical membership");
        assertEquals(0, recordCount.get(),
                "missing current owner does not attempt recording");
        assertEquals(1, clearCount.get(),
                "missing current owner clears physical membership once");
        assertEquals(2, dirtyCount.get(),
                "missing owner cleanup marks store dirty once");
    }

    private static DungeonSession physicalSession(
            String sessionName,
            DungeonInstanceId instanceId,
            UUID playerId
    ) {
        return new DungeonSession(
                stableUuid(sessionName),
                instanceId,
                playerId,
                new DungeonSiteKey(0, 0),
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

    private static void assertNoPhysicalMembership(
            DungeonSessionStore store,
            UUID playerId,
            String message
    ) {
        for (DungeonSession session : store.all()) {
            assertFalse(session.isPhysicalParticipant(playerId), message);
        }
    }

    private static UUID stableUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertFalse(boolean value, String message) {
        if (value) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(
                    message + ": expected=" + expected + ", actual=" + actual
            );
        }
    }
}
