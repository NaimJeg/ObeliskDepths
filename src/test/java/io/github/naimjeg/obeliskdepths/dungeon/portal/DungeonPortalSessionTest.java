package io.github.naimjeg.obeliskdepths.dungeon.portal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import io.github.naimjeg.obeliskdepths.dungeon.access.DungeonAccessController;
import io.github.naimjeg.obeliskdepths.dungeon.access.DungeonAccessResult;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.id.PortalSessionId;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonDifficulty;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonStatus;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSession;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSessionState;
import io.github.naimjeg.obeliskdepths.dungeon.session.SessionAccessPolicy;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class DungeonPortalSessionTest {
    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(
                    Registries.DIMENSION,
                    Identifier.fromNamespaceAndPath("minecraft", "overworld")
            );

    private DungeonPortalSessionTest() {
    }

    public static void main(String[] args) {
        testPortalSessionCodecRoundTripHasAccessPolicy();
        testPortalSessionCodecLegacyDefaultsToStarterOnly();
        testDungeonSessionCodecLegacyDefaultsToOpen();
        testDungeonSessionNullPolicyRejected();
        testDungeonSessionCodecExplicitPolicies();
        testPortalSessionOpenCodecRoundTripAndDeterministicParticipants();
        testPortalSessionStarterOnlyDecodeValidation();
        testPortalLookupByInstanceIsDeterministic();
        testCurrentStarterOnlyAccessChecks();
        testOpenAccessChecks();
        testAllowlistAccessChecks();
    }

    private static void testPortalSessionCodecRoundTripHasAccessPolicy() {
        UUID opener = uuid("portal-opener");
        UUID participant = uuid("portal-participant");
        PortalSession session = new PortalSession(
                new PortalSessionId(uuid("portal-session")),
                new DungeonInstanceId(uuid("portal-instance")),
                opener,
                OVERWORLD,
                new BlockPos(10, 64, 10),
                new BlockPos(12, 64, 10),
                SessionAccessPolicy.STARTER_ONLY,
                1200L
        );

        JsonElement json = PortalSession.CODEC.encodeStart(
                JsonOps.INSTANCE,
                session
        ).getOrThrow();
        JsonObject object = json.getAsJsonObject();
        assertFalse(
                object.has("admission_mode"),
                "portal session codec must not expose admission mode"
        );
        PortalSession decoded = PortalSession.CODEC.parse(JsonOps.INSTANCE, json)
                .getOrThrow();

        assertEquals(
                SessionAccessPolicy.STARTER_ONLY,
                decoded.accessPolicy(),
                "portal session codec round trips policy"
        );
        assertEquals(OVERWORLD, decoded.sourceDimension(), "source dimension round trip");
        assertEquals(new BlockPos(10, 64, 10), decoded.obeliskPos(), "obelisk pos round trip");
        assertEquals(new BlockPos(12, 64, 10), decoded.portalAnchorPos(), "anchor round trip");
        assertFalse(decoded.isParticipant(opener), "opener not auto-added to participants by codec");
        assertFalse(decoded.isParticipant(participant),
                "non-opener participant rejected by current starter-only validation");
    }

    private static void testPortalSessionCodecLegacyDefaultsToStarterOnly() {
        PortalSession session = session(
                "legacy-session",
                DungeonInstanceId.create(),
                uuid("legacy-opener"),
                100L
        );
        session = withPolicy(session, SessionAccessPolicy.OPEN);
        JsonObject object = encode(session).getAsJsonObject();
        assertTrue(object.remove("access_policy") != null,
                "legacy fixture starts with a serialized policy");

        PortalSession decoded = PortalSession.CODEC.parse(
                JsonOps.INSTANCE,
                object
        ).getOrThrow();

        assertEquals(
                SessionAccessPolicy.STARTER_ONLY,
                decoded.accessPolicy(),
                "legacy portal session without access_policy decodes as STARTER_ONLY"
        );
    }

    private static void testDungeonSessionCodecLegacyDefaultsToOpen() {
        UUID starter = uuid("legacy-dungeon-starter");
        DungeonSession session = new DungeonSession(
                uuid("legacy-dungeon-session"),
                DungeonInstanceId.create(),
                starter,
                new DungeonSiteKey(0, 0),
                DungeonSessionState.ACTIVE,
                SessionAccessPolicy.STARTER_ONLY,
                Set.of(starter),
                Set.of(),
                Set.of(),
                0L,
                0L,
                false
        );

        JsonObject object = DungeonSession.CODEC.encodeStart(
                JsonOps.INSTANCE,
                session
        ).getOrThrow().getAsJsonObject();
        assertTrue(object.remove("access_policy") != null,
                "legacy dungeon-session fixture starts with a serialized policy");

        DungeonSession decoded = DungeonSession.CODEC.parse(
                JsonOps.INSTANCE,
                object
        ).getOrThrow();

        assertEquals(
                SessionAccessPolicy.OPEN,
                decoded.accessPolicy(),
                "legacy dungeon session without access_policy decodes as OPEN"
        );
    }

    private static void testDungeonSessionNullPolicyRejected() {
        UUID starter = uuid("null-policy-starter");
        try {
            new DungeonSession(
                    uuid("null-policy-session"),
                    DungeonInstanceId.create(),
                    starter,
                    new DungeonSiteKey(0, 0),
                    DungeonSessionState.ACTIVE,
                    null,
                    Set.of(starter),
                    Set.of(),
                    Set.of(),
                    0L,
                    0L,
                    false
            );
            throw new AssertionError("null Java-domain access policy should be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("access policy"),
                    "null policy error must reference access policy");
        }
    }

    private static void testDungeonSessionCodecExplicitPolicies() {
        UUID starter = uuid("explicit-policy-starter");

        DungeonSession starterOnly = dungeonSessionWithPolicy(
                starter,
                SessionAccessPolicy.STARTER_ONLY
        );
        DungeonSession decodedStarterOnly = DungeonSession.CODEC.parse(
                JsonOps.INSTANCE,
                encode(starterOnly)
        ).getOrThrow();
        assertEquals(
                SessionAccessPolicy.STARTER_ONLY,
                decodedStarterOnly.accessPolicy(),
                "explicit STARTER_ONLY JSON decodes as STARTER_ONLY"
        );

        DungeonSession open = dungeonSessionWithPolicy(
                starter,
                SessionAccessPolicy.OPEN
        );
        DungeonSession decodedOpen = DungeonSession.CODEC.parse(
                JsonOps.INSTANCE,
                encode(open)
        ).getOrThrow();
        assertEquals(
                SessionAccessPolicy.OPEN,
                decodedOpen.accessPolicy(),
                "explicit OPEN JSON decodes as OPEN"
        );
    }

    private static void testPortalSessionOpenCodecRoundTripAndDeterministicParticipants() {
        UUID opener = uuid("open-opener");
        UUID guestA = uuid("open-guest-a");
        UUID guestB = uuid("open-guest-b");
        PortalSession first = session("open-first", DungeonInstanceId.create(), opener, 100L);
        first = withPolicy(first, SessionAccessPolicy.OPEN);
        first.addParticipant(guestB);
        first.addParticipant(guestA);

        PortalSession second = session("open-second", DungeonInstanceId.create(), opener, 100L);
        second = withPolicy(second, SessionAccessPolicy.OPEN);
        second.addParticipant(guestA);
        second.addParticipant(guestB);

        PortalSession decoded = PortalSession.CODEC.parse(
                JsonOps.INSTANCE,
                encode(first)
        ).getOrThrow();
        assertEquals(SessionAccessPolicy.OPEN, decoded.accessPolicy(),
                "OPEN policy survives codec");
        assertEquals(
                Set.of(guestA, guestB),
                decoded.participants(),
                "OPEN participants survive codec"
        );
        assertEquals(
                encode(first).getAsJsonObject().get("participants"),
                encode(second).getAsJsonObject().get("participants"),
                "OPEN participant serialization is deterministic"
        );
    }

    private static void testPortalSessionStarterOnlyDecodeValidation() {
        UUID opener = uuid("validation-opener");
        UUID guest = uuid("validation-guest");
        UUID otherGuest = uuid("validation-other-guest");

        PortalSession openerOnly = session("validation-opener-only",
                DungeonInstanceId.create(), opener, 100L);
        openerOnly.addParticipant(opener);
        PortalSession decoded = PortalSession.CODEC.parse(
                JsonOps.INSTANCE,
                encode(openerOnly)
        ).getOrThrow();
        assertEquals(
                Set.of(opener),
                decoded.participants(),
                "starter-only persisted opener participant is accepted"
        );

        PortalSession nonOpener = session("validation-non-opener",
                DungeonInstanceId.create(), opener, 100L);
        nonOpener = withPolicy(nonOpener, SessionAccessPolicy.OPEN);
        nonOpener.addParticipant(guest);
        expectDecodeRejected(
                withSerializedPolicy(nonOpener, "starter_only"),
                "starter-only non-opener participant rejected"
        );

        PortalSession multiple = session("validation-multiple",
                DungeonInstanceId.create(), opener, 100L);
        multiple = withPolicy(multiple, SessionAccessPolicy.OPEN);
        multiple.addParticipant(guest);
        multiple.addParticipant(otherGuest);
        expectDecodeRejected(
                withSerializedPolicy(multiple, "starter_only"),
                "starter-only multiple participants rejected"
        );

        PortalSession openGuest = session("validation-open-guest",
                DungeonInstanceId.create(), opener, 100L);
        openGuest = withPolicy(openGuest, SessionAccessPolicy.OPEN);
        openGuest.addParticipant(guest);
        PortalSession openDecoded = PortalSession.CODEC.parse(
                JsonOps.INSTANCE,
                encode(openGuest)
        ).getOrThrow();
        assertEquals(
                Set.of(guest),
                openDecoded.participants(),
                "OPEN non-opener participant is accepted"
        );
    }

    private static void testPortalLookupByInstanceIsDeterministic() {
        DungeonManagerSavedData data = new DungeonManagerSavedData();
        DungeonInstance instance = instance("deterministic-instance");
        data.instances().put(instance);
        UUID laterOpener = uuid("deterministic-later-opener");
        UUID earlierOpener = uuid("deterministic-earlier-opener");
        PortalSession later = session(
                "deterministic-later-session",
                instance.id(),
                laterOpener,
                200L
        );
        PortalSession earlier = session(
                "deterministic-earlier-session",
                instance.id(),
                earlierOpener,
                100L
        );

        data.portalSessions().add(later);
        data.portalSessions().add(earlier);

        assertEquals(
                Optional.of(earlierOpener),
                data.portalSessions()
                        .findByInstance(instance.id(), 10L)
                        .map(PortalSession::opener),
                "instance lookup chooses earliest unexpired portal session"
        );
        assertEquals(
                Optional.of(laterOpener),
                data.portalSessions()
                        .findByInstance(instance.id(), 150L)
                        .map(PortalSession::opener),
                "instance lookup skips expired portal sessions deterministically"
        );
    }

    private static void testCurrentStarterOnlyAccessChecks() {
        UUID opener = uuid("starter-only-opener");
        UUID stranger = uuid("starter-only-stranger");
        DungeonInstance instance = instance("access-instance");
        PortalSession session = session(
                "starter-only-session",
                instance.id(),
                opener,
                100L
        );

        assertEquals(
                DungeonAccessResult.DENY_ACCESS_POLICY,
                DungeonAccessController.canEnter(stranger, session, instance, 1L),
                "starter-only portal denies non-opener"
        );
        assertEquals(
                DungeonAccessResult.ALLOW,
                DungeonAccessController.canEnter(opener, session, instance, 1L),
                "starter-only portal allows opener"
        );
        assertEquals(
                DungeonAccessResult.DENY_PORTAL_EXPIRED,
                DungeonAccessController.canEnter(opener, session, instance, 100L),
                "expired portal denied"
        );

        // opener remains admitted on a repeated access recheck
        assertEquals(
                DungeonAccessResult.ALLOW,
                DungeonAccessController.canEnter(opener, session, instance, 1L),
                "starter-only portal allows opener after admission recheck"
        );

        instance.setStatus(DungeonStatus.PORTAL_CLOSED);
        assertEquals(
                DungeonAccessResult.DENY_INSTANCE_CLOSED,
                DungeonAccessController.canEnter(opener, session, instance, 1L),
                "inactive instance denied"
        );
    }

    private static void testOpenAccessChecks() {
        UUID opener = uuid("open-access-opener");
        UUID stranger = uuid("open-access-stranger");
        DungeonInstance instance = instance("open-access-instance");
        PortalSession session = session(
                "open-access-session",
                instance.id(),
                opener,
                100L
        );
        session = withPolicy(session, SessionAccessPolicy.OPEN);

        assertEquals(
                DungeonAccessResult.ALLOW,
                DungeonAccessController.canEnter(stranger, session, instance, 1L),
                "open portal allows non-opener"
        );
        assertEquals(
                DungeonAccessResult.ALLOW,
                DungeonAccessController.canEnter(opener, session, instance, 1L),
                "open portal allows opener"
        );
        assertEquals(
                DungeonAccessResult.DENY_PORTAL_EXPIRED,
                DungeonAccessController.canEnter(stranger, session, instance, 100L),
                "open portal expiry still denies"
        );
        instance.setStatus(DungeonStatus.PORTAL_CLOSED);
        assertEquals(
                DungeonAccessResult.DENY_INSTANCE_CLOSED,
                DungeonAccessController.canEnter(stranger, session, instance, 1L),
                "open portal inactive instance still denies"
        );
    }

    private static void testAllowlistAccessChecks() {
        UUID opener = uuid("allowlist-access-opener");
        UUID stranger = uuid("allowlist-access-stranger");
        DungeonInstance instance = instance("allowlist-access-instance");
        PortalSession session = session(
                "allowlist-access-session",
                instance.id(),
                opener,
                100L
        );
        session = withPolicy(session, SessionAccessPolicy.ALLOWLIST);

        assertEquals(
                DungeonAccessResult.ALLOW,
                DungeonAccessController.canEnter(opener, session, instance, 1L),
                "allowlist portal allows opener"
        );
        assertEquals(
                DungeonAccessResult.DENY_ACCESS_POLICY,
                DungeonAccessController.canEnter(stranger, session, instance, 1L),
                "allowlist portal fail-closes non-opener"
        );
        assertEquals(
                DungeonAccessResult.DENY_PORTAL_EXPIRED,
                DungeonAccessController.canEnter(opener, session, instance, 100L),
                "allowlist portal expiry still denies"
        );
        instance.setStatus(DungeonStatus.PORTAL_CLOSED);
        assertEquals(
                DungeonAccessResult.DENY_INSTANCE_CLOSED,
                DungeonAccessController.canEnter(opener, session, instance, 1L),
                "allowlist portal inactive instance still denies"
        );
    }

    private static void testParticipantRollbackPreservesPreExistingMembership() {
        UUID opener = uuid("rollback-opener");
        UUID preExisting = uuid("rollback-existing");
        UUID attempted = uuid("rollback-attempted");
        DungeonInstance instance = instance("rollback-instance");
        PortalSession session = session(
                "rollback-session",
                instance.id(),
                opener,
                100L
        );
        instance.addParticipant(preExisting);
        session.addParticipant(preExisting);
        instance.addParticipant(attempted);
        session.addParticipant(attempted);

        assertTrue(instance.removeParticipant(attempted), "attempted instance membership removed");
        assertTrue(session.removeParticipant(attempted), "attempted portal membership removed");
        assertTrue(instance.isParticipant(preExisting), "pre-existing instance membership remains");
        assertTrue(session.isParticipant(preExisting), "pre-existing portal membership remains");
    }

    private static PortalSession session(
            String sessionName,
            DungeonInstanceId instanceId,
            UUID opener,
            long expiresAt
    ) {
        return new PortalSession(
                new PortalSessionId(uuid(sessionName)),
                instanceId,
                opener,
                OVERWORLD,
                new BlockPos(0, 64, 0),
                new BlockPos(2, 64, 0),
                SessionAccessPolicy.STARTER_ONLY,
                expiresAt
        );
    }

    private static PortalSession withPolicy(
            PortalSession session,
            SessionAccessPolicy accessPolicy
    ) {
        return new PortalSession(
                session.id(),
                session.instanceId(),
                session.opener(),
                session.sourceDimension(),
                session.obeliskPos(),
                session.portalAnchorPos(),
                accessPolicy,
                session.expiresAtGameTime()
        );
    }

    private static JsonElement encode(PortalSession session) {
        return PortalSession.CODEC.encodeStart(JsonOps.INSTANCE, session)
                .getOrThrow();
    }

    private static JsonElement encode(DungeonSession session) {
        return DungeonSession.CODEC.encodeStart(JsonOps.INSTANCE, session)
                .getOrThrow();
    }

    private static DungeonSession dungeonSessionWithPolicy(
            UUID starter,
            SessionAccessPolicy accessPolicy
    ) {
        return new DungeonSession(
                uuid("explicit-dungeon-" + accessPolicy),
                DungeonInstanceId.create(),
                starter,
                new DungeonSiteKey(0, 0),
                DungeonSessionState.ACTIVE,
                accessPolicy,
                Set.of(starter),
                Set.of(),
                Set.of(),
                0L,
                0L,
                false
        );
    }

    private static JsonElement withSerializedPolicy(
            PortalSession session,
            String serializedPolicy
    ) {
        JsonObject object = encode(session).getAsJsonObject();
        object.addProperty("access_policy", serializedPolicy);
        return object;
    }

    private static void expectDecodeRejected(
            JsonElement json,
            String message
    ) {
        try {
            PortalSession.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
            throw new AssertionError(message);
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().length() > 0, message);
        } catch (RuntimeException expected) {
            assertTrue(expected.getMessage() != null, message);
        }
    }

    private static DungeonInstance instance(String name) {
        return new DungeonInstance(
                new DungeonInstanceId(uuid(name)),
                new DungeonSiteKey(0, 0),
                new DungeonDifficulty(1, 0.0F, 1.0F, 1),
                new DungeonSiteKey(0, 0).toTerritoryId(),
                new BlockPos(0, 64, 0),
                0L
        );
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
            throw new AssertionError(
                    message + ": expected=" + expected + ", actual=" + actual
            );
        }
    }
}
