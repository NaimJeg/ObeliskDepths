package io.github.naimjeg.obeliskdepths.dungeon.session;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import io.github.naimjeg.obeliskdepths.dungeon.state.store.DungeonSessionStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class DungeonSessionResponsibilitySplitTest {
    private DungeonSessionResponsibilitySplitTest() {
    }

    public static void main(String[] args) throws IOException {
        testEachAccessPolicy();
        testPresenceOwnsPhysicalChecksOnly();
        testLifecycleOwnsPortalToSessionTransitions();
        testCompletionOwnsBossAndRewardPhaseTransitions();
        testCleanupStateMutationIsIdempotent();
    }

    private static void testEachAccessPolicy() {
        UUID starter = stableUuid("starter");
        UUID guest = stableUuid("guest");
        UUID outsider = stableUuid("outsider");

        DungeonSession starterOnly = session(SessionAccessPolicy.STARTER_ONLY, starter, Set.of());
        assertTrue(DungeonSessionAccess.canAccessSession(starter, starterOnly), "starter can access starter-only session");
        assertFalse(DungeonSessionAccess.canAccessSession(guest, starterOnly), "guest cannot access starter-only session");

        DungeonSession open = session(SessionAccessPolicy.OPEN, starter, Set.of());
        assertTrue(DungeonSessionAccess.canAccessSession(starter, open), "starter can access open session");
        assertTrue(DungeonSessionAccess.canAccessSession(guest, open), "guest can access open session");

        DungeonSession allowlist = session(SessionAccessPolicy.ALLOWLIST, starter, Set.of(guest));
        assertTrue(DungeonSessionAccess.canAccessSession(starter, allowlist), "starter is implicitly allowlisted");
        assertTrue(DungeonSessionAccess.canAccessSession(guest, allowlist), "allowlisted participant can access");
        assertFalse(DungeonSessionAccess.canAccessSession(outsider, allowlist), "non-allowlisted player cannot access");
        assertFalse(DungeonSessionAccess.canAccessSession((UUID) null, allowlist), "null player is denied");
        assertFalse(DungeonSessionAccess.canAccessSession(guest, null), "missing session is denied");
    }

    private static void testPresenceOwnsPhysicalChecksOnly() throws IOException {
        String presence = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/session/DungeonSessionPresence.java");
        assertTrue(presence.contains("findCurrentPhysicalInstance"), "presence exposes physical instance lookup");
        assertTrue(presence.contains("isPhysicallyPresentIn"), "presence exposes physical presence check");
        assertTrue(presence.contains("isInsideDungeonTerritory"), "presence exposes territory position check");
        assertTrue(presence.contains("DungeonSpatialIndex.findPhysicalOwnerAt"), "presence delegates spatial lookup to spatial index");
        assertFalse(presence.contains("SessionAccessPolicy"), "presence must not decide access policy");
        assertFalse(presence.contains("canAccessSession"), "presence must not perform logical authorization");
    }

    private static void testLifecycleOwnsPortalToSessionTransitions() throws IOException {
        String lifecycle = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/session/DungeonSessionLifecycle.java");
        assertTrue(lifecycle.contains("getOrCreateForPortal"), "lifecycle owns portal-to-session creation");
        assertTrue(lifecycle.contains("markPortalEntrySucceeded"), "lifecycle owns portal entry transition");
        assertTrue(lifecycle.contains("registerParticipant"), "lifecycle owns session participant initialization");
        assertTrue(lifecycle.contains("registerPhysicalParticipant"), "lifecycle owns physical participant lifecycle mutation");
        assertTrue(lifecycle.contains("private static SessionAccessPolicy accessPolicyFor"), "portal admission conversion is private to lifecycle");
        assertTrue(lifecycle.contains("case SOLO -> SessionAccessPolicy.STARTER_ONLY"), "solo admission maps to starter-only access");
        assertTrue(lifecycle.contains("case OPEN_JOIN -> SessionAccessPolicy.OPEN"), "open admission maps to open access");
        assertFalse(lifecycle.contains("SessionAccessPolicy.ALLOWLIST"), "portal flows must not create allowlist sessions");
        assertTrue(lifecycle.contains("Existing dungeon session access policy mismatch"), "existing session policy mismatch is rejected");
        assertTrue(lifecycle.contains("throw new IllegalStateException"), "policy mismatch throws invariant violation");
        assertFalse(lifecycle.contains("setAccessPolicy"), "portal flow must not mutate existing session policy");
        assertFalse(lifecycle.contains("DungeonEncounterDirector.cleanupInstance"), "lifecycle must not own cleanup");
        assertFalse(lifecycle.contains("DungeonRewardService"), "lifecycle must not own reward workflow");

        assertFalse(
                read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/session/DungeonSessionAccess.java")
                        .contains("PortalAdmissionMode"),
                "authorization must not convert portal admission"
        );
        assertFalse(
                read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/session/DungeonSessionPresence.java")
                        .contains("PortalAdmissionMode"),
                "presence must not convert portal admission"
        );
        assertFalse(
                read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/session/DungeonSessionCompletion.java")
                        .contains("PortalAdmissionMode"),
                "completion must not convert portal admission"
        );
        assertFalse(
                read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/session/DungeonSessionCleanup.java")
                        .contains("PortalAdmissionMode"),
                "cleanup must not convert portal admission"
        );
    }

    private static void testCompletionOwnsBossAndRewardPhaseTransitions() throws IOException {
        String completion = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/session/DungeonSessionCompletion.java");
        assertTrue(completion.contains("markCompleted"), "completion owns completed session state");
        assertFalse(completion.contains("markBossKilled"), "reward records own boss reward state");
        assertFalse(completion.contains("initializeEncounterProgress"), "raid records own encounter progress");
        assertFalse(completion.contains("getOrCreateForPortal"), "completion must not create sessions");
        assertFalse(completion.contains("cleanupSession"), "completion must not perform cleanup");
    }

    private static void testCleanupStateMutationIsIdempotent() throws IOException {
        AtomicInteger dirtyCount = new AtomicInteger();
        DungeonSessionStore store = new DungeonSessionStore(dirtyCount::incrementAndGet);
        DungeonSession session = session(SessionAccessPolicy.OPEN, stableUuid("cleanup-starter"), Set.of());

        store.add(session);
        assertEquals(1, dirtyCount.get(), "adding session marks dirty");
        assertTrue(store.markCleaned(session), "first cleanup state transition succeeds");
        assertEquals(2, dirtyCount.get(), "first cleanup transition marks dirty");
        assertFalse(store.markCleaned(session), "second cleanup state transition is idempotent");
        assertEquals(2, dirtyCount.get(), "idempotent cleanup does not mark dirty again");

        String cleanup = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/session/DungeonSessionCleanup.java");
        assertTrue(cleanup.contains("if (!session.state().needsRuntimeTick())"), "abandon cleanup guards terminal sessions");
        assertTrue(cleanup.contains("DungeonEncounterDirector.cleanupInstance"), "cleanup coordinates encounter cleanup");
        assertTrue(cleanup.contains("DungeonRuntimeArtifactCleanupService.cleanupInstanceArtifacts"), "cleanup coordinates artifact cleanup");
        assertTrue(cleanup.contains("DungeonSessionProgressBarService.removeSession"), "cleanup removes progress UI state");
    }

    private static DungeonSession session(
            SessionAccessPolicy accessPolicy,
            UUID starter,
            Set<UUID> participants
    ) {
        return new DungeonSession(
                stableUuid("session-" + accessPolicy + "-" + participants),
                new DungeonInstanceId(stableUuid("instance-" + accessPolicy + "-" + participants)),
                starter,
                new DungeonSiteKey(0, accessPolicy.ordinal()),
                DungeonSessionState.ACTIVE,
                accessPolicy,
                participants,
                Set.of(),
                Set.of(),
                0L,
                0L,
                false
        );
    }

    private static UUID stableUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(String file) throws IOException {
        return Files.readString(Path.of(file));
    }

    private static void assertEquals(
            int expected,
            int actual,
            String message
    ) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }
}
