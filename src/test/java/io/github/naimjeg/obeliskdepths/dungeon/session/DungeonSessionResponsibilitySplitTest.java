package io.github.naimjeg.obeliskdepths.dungeon.session;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import io.github.naimjeg.obeliskdepths.dungeon.state.store.DungeonSessionStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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
        testPhysicalMembershipBypassApisAreGone();
        testCompletionOwnsBossAndRewardPhaseTransitions();
        testCleanupStateMutationIsIdempotent();
        testLivePhysicalParticipantDecision();
        testStalePhysicalPresenceSelfHeal();
        testPhysicalOutsiderPruning();
        testAbandonGraceDecisionDoesNotRefreshPresence();
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
        String presenceService = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/presence/DungeonPhysicalPresenceService.java");
        assertTrue(presence.contains("findCurrentPhysicalInstance"), "presence exposes physical instance lookup");
        assertTrue(presence.contains("isPhysicallyPresentIn"), "presence exposes physical presence check");
        assertTrue(presence.contains("isInsideDungeonTerritory"), "presence exposes territory position check");
        assertTrue(presence.contains("DungeonSpatialIndex.findPhysicalOwnerAt"), "presence delegates spatial lookup to spatial index");
        assertFalse(presence.contains("SessionAccessPolicy"), "presence must not decide access policy");
        assertFalse(presence.contains("canAccessSession"), "presence must not perform logical authorization");
        assertTrue(presenceService.contains("reconcilePhysicalParticipant"),
                "physical service reconciles all persisted memberships");
        assertFalse(presenceService.contains("findPhysicalInstance" + "ByPlayer"),
                "physical service does not select one arbitrary previous instance");
    }

    private static void testLifecycleOwnsPortalToSessionTransitions() throws IOException {
        String lifecycle = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/session/DungeonSessionLifecycle.java");
        assertTrue(lifecycle.contains("acquireForPortal"), "lifecycle owns portal-to-session creation");
        assertTrue(lifecycle.contains("record DungeonSessionAcquisition"), "lifecycle reports session creation ownership");
        assertTrue(
                lifecycle.replace("\r\n", "\n")
                        .contains("data.sessions().add(created);\n        return new DungeonSessionAcquisition(created, true);"),
                "created session ownership should be returned immediately after persistence"
        );
        assertTrue(lifecycle.contains("markPortalEntrySucceeded"), "lifecycle owns portal entry transition");
        assertTrue(lifecycle.contains("registerParticipant"), "lifecycle owns session participant initialization");
        assertFalse(lifecycle.contains("public static boolean registerPhysical" + "Participant("),
                "lifecycle must not expose generic physical registration");
        assertTrue(lifecycle.contains("reconcilePhysicalParticipant"),
                "lifecycle owns atomic physical membership reconciliation");
        assertTrue(lifecycle.contains("portalSession.accessPolicy()"),
                "lifecycle derives portal policy from PortalSession");
        assertFalse(lifecycle.contains("SessionAccessPolicy.STARTER_ONLY"),
                "lifecycle must not independently choose STARTER_ONLY");
        assertFalse(lifecycle.contains("accessPolicyFor"), "portal admission conversion is deleted");
        assertFalse(lifecycle.contains("PortalAdmission" + "Mode"), "portal admission modes are deleted from lifecycle");
        assertFalse(lifecycle.contains("SessionAccessPolicy.ALLOWLIST"), "portal flows must not create allowlist sessions");
        assertTrue(lifecycle.contains("Existing dungeon session access policy mismatch"), "existing session policy mismatch is rejected");
        assertTrue(lifecycle.contains("throw new IllegalStateException"), "policy mismatch throws invariant violation");
        assertFalse(lifecycle.contains("setAccessPolicy"), "portal flow must not mutate existing session policy");
        assertFalse(lifecycle.contains("DungeonEncounterDirector.cleanupInstance"), "lifecycle must not own cleanup");
        assertFalse(lifecycle.contains("DungeonRewardService"), "lifecycle must not own reward workflow");

        assertFalse(
                read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/session/DungeonSessionAccess.java")
                        .contains("PortalAdmission" + "Mode"),
                "authorization must not convert portal admission"
        );
        assertFalse(
                read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/session/DungeonSessionPresence.java")
                        .contains("PortalAdmission" + "Mode"),
                "presence must not convert portal admission"
        );
        assertFalse(
                read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/session/DungeonSessionCompletion.java")
                        .contains("PortalAdmission" + "Mode"),
                "completion must not convert portal admission"
        );
        assertFalse(
                read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/session/DungeonSessionCleanup.java")
                        .contains("PortalAdmission" + "Mode"),
                "cleanup must not convert portal admission"
        );
    }

    private static void testPhysicalMembershipBypassApisAreGone() throws IOException {
        String store = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/state/store/DungeonSessionStore.java");
        String lifecycle = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/session/DungeonSessionLifecycle.java");
        String cleanup = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/session/DungeonSessionCleanup.java");
        String presence = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/presence/DungeonPhysicalPresenceService.java");

        assertFalse(store.contains("public boolean registerPhysical" + "Participant("),
                "store must not expose generic physical registration");
        assertFalse(store.contains("public Set<DungeonInstanceId> physicalInstances" + "ForPlayer("),
                "store must not expose physical instance lookup");
        assertFalse(lifecycle.contains("public static boolean registerPhysical" + "Participant("),
                "lifecycle must not expose generic physical registration");
        assertFalse(cleanup.contains("hasLiveLogical" + "PhysicalParticipant"),
                "cleanup must not retain duplicate liveness helper");
        assertFalse(presence.contains("PHYSICAL_INSTANCE" + "_BY_PLAYER"),
                "physical presence service must be stateless");
    }

    private static void testCompletionOwnsBossAndRewardPhaseTransitions() throws IOException {
        String completion = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/session/DungeonSessionCompletion.java");
        assertTrue(completion.contains("markCompleted"), "completion owns completed session state");
        assertFalse(completion.contains("markBossKilled"), "reward records own boss reward state");
        assertFalse(completion.contains("initializeEncounterProgress"), "raid records own encounter progress");
        assertFalse(completion.contains("acquireForPortal"), "completion must not create sessions");
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

        assertTrue(cleanup.contains("hasLiveLogicalParticipantInside("),
                "cleanup requires live logical participant presence");
        assertTrue(cleanup.contains("pruneStalePhysicalParticipants("),
                "cleanup self-heals stale logical physical membership");
        assertTrue(cleanup.contains("getPlayer(playerId)"),
                "cleanup resolves persisted UUIDs to live ServerPlayers");
        assertFalse(cleanup.contains(".anyMatch(session::isParticipant)"),
                "raw physical/logical intersection is not liveness authority");
        assertFalse(cleanup.contains("physicalParticipants().isEmpty()"),
                "non-empty physical cache is not liveness authority");
    }

    private static void testLivePhysicalParticipantDecision() {
        UUID starter = stableUuid("live-starter");
        UUID guest = stableUuid("live-guest");
        UUID outsider = stableUuid("live-outsider");

        DungeonSession open = session(
                SessionAccessPolicy.OPEN,
                starter,
                Set.of(starter, guest)
        );
        open.registerPhysicalParticipant(guest);
        List<UUID> removed = new ArrayList<>();

        assertTrue(
                DungeonSessionCleanup.pruneStalePhysicalParticipants(
                        open.participants(),
                        open.physicalParticipants(),
                        guest::equals,
                        removed::add
                ),
                "live OPEN guest keeps the run active"
        );
        assertTrue(removed.isEmpty(), "live guest is retained");

        removed.clear();
        assertFalse(
                DungeonSessionCleanup.pruneStalePhysicalParticipants(
                        open.participants(),
                        open.physicalParticipants(),
                        playerId -> false,
                        removed::add
                ),
                "offline persisted guest is not live"
        );
        assertTrue(removed.contains(guest), "offline guest is pruned");
        assertEquals(1, removed.size(), "offline guest is the only pruned member");

        removed.clear();
        assertFalse(
                DungeonSessionCleanup.pruneStalePhysicalParticipants(
                        Set.of(starter),
                        Set.of(outsider),
                        playerId -> true,
                        removed::add
                ),
                "physical outsider does not keep the logical run active"
        );
        assertTrue(removed.isEmpty(), "live physical outsider is retained");

        removed.clear();
        assertTrue(
                DungeonSessionCleanup.pruneStalePhysicalParticipants(
                        Set.of(starter),
                        Set.of(starter),
                        starter::equals,
                        removed::add
                ),
                "live STARTER_ONLY starter keeps the run active"
        );
        assertTrue(removed.isEmpty(), "live starter is retained");
    }

    private static void testStalePhysicalPresenceSelfHeal() {
        AtomicInteger dirtyCount = new AtomicInteger();
        DungeonSessionStore store = new DungeonSessionStore(dirtyCount::incrementAndGet);
        UUID starter = stableUuid("stale-starter");
        UUID guest = stableUuid("stale-guest");
        UUID outsider = stableUuid("stale-outsider");
        DungeonSession session = session(
                SessionAccessPolicy.OPEN,
                starter,
                Set.of(starter, guest)
        );
        session.registerPhysicalParticipant(guest);
        session.registerPhysicalParticipant(outsider);
        store.add(session);

        int dirtyBefore = dirtyCount.get();
        assertFalse(
                DungeonSessionCleanup.pruneStalePhysicalParticipants(
                        session.participants(),
                        session.physicalParticipants(),
                        playerId -> false,
                        playerId -> store.unregisterPhysicalParticipant(session, playerId)
                ),
                "stale logical participant is not live"
        );
        assertFalse(session.isPhysicalParticipant(guest),
                "stale logical physical participant is removed from persisted state");
        assertFalse(session.isPhysicalParticipant(outsider),
                "offline physical outsider is also removed from persisted state");
        assertEquals(dirtyBefore + 2, dirtyCount.get(),
                "each stale physical removal marks session store dirty");
    }

    private static void testPhysicalOutsiderPruning() {
        UUID starter = stableUuid("prune-starter");
        UUID guest = stableUuid("prune-guest");
        UUID outsider = stableUuid("prune-outsider");

        List<UUID> removed = new ArrayList<>();
        assertFalse(
                DungeonSessionCleanup.pruneStalePhysicalParticipants(
                        Set.of(starter),
                        Set.of(outsider),
                        playerId -> false,
                        removed::add
                ),
                "offline physical outsider does not keep the run alive"
        );
        assertTrue(removed.contains(outsider),
                "offline physical outsider is removed from the physical cache");

        removed.clear();
        assertFalse(
                DungeonSessionCleanup.pruneStalePhysicalParticipants(
                        Set.of(starter),
                        Set.of(outsider),
                        playerId -> true,
                        removed::add
                ),
                "live physical outsider does not keep the run alive"
        );
        assertTrue(removed.isEmpty(),
                "live physical outsider remains in the physical cache");

        removed.clear();
        assertTrue(
                DungeonSessionCleanup.pruneStalePhysicalParticipants(
                        Set.of(starter, guest),
                        Set.of(guest),
                        playerId -> true,
                        removed::add
                ),
                "live logical physical guest keeps the run alive"
        );
        assertTrue(removed.isEmpty(),
                "live logical physical guest is retained");

        removed.clear();
        assertFalse(
                DungeonSessionCleanup.pruneStalePhysicalParticipants(
                        Set.of(starter, guest),
                        Set.of(guest),
                        playerId -> false,
                        removed::add
                ),
                "offline logical physical guest does not keep the run alive"
        );
        assertTrue(removed.contains(guest),
                "offline logical physical guest is removed");
    }

    private static void testAbandonGraceDecisionDoesNotRefreshPresence() {
        long lastInside = 100L;
        assertFalse(
                DungeonSessionCleanup.shouldAbandon(
                        lastInside + DungeonSessionCleanup.ABANDON_GRACE_TICKS - 1L,
                        lastInside,
                        false
                ),
                "session remains in grace before abandonment"
        );
        assertTrue(
                DungeonSessionCleanup.shouldAbandon(
                        lastInside + DungeonSessionCleanup.ABANDON_GRACE_TICKS,
                        lastInside,
                        false
                ),
                "session abandons after grace with no live participant"
        );
        assertFalse(
                DungeonSessionCleanup.shouldAbandon(
                        lastInside + DungeonSessionCleanup.ABANDON_GRACE_TICKS,
                        lastInside,
                        true
                ),
                "live participant prevents abandonment"
        );

        DungeonSession session = session(
                SessionAccessPolicy.OPEN,
                stableUuid("grace-starter"),
                Set.of()
        );
        session.markParticipantInside(lastInside);
        session.markAbandonPending();
        assertTrue(session.lastParticipantInsideGameTime() == lastInside,
                "abandon pending does not refresh last participant inside time");
        assertTrue(session.state() == DungeonSessionState.ABANDON_PENDING,
                "abandon pending state is recorded");
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
