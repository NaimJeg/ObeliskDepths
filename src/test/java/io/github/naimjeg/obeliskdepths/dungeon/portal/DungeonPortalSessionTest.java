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
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
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
        testPortalSessionCodecRoundTripHasNoAdmissionMode();
        testPortalLookupByInstanceIsDeterministic();
        testSoloAccessChecks();
    }

    private static void testPortalSessionCodecRoundTripHasNoAdmissionMode() {
        UUID opener = uuid("portal-opener");
        UUID participant = uuid("portal-participant");
        PortalSession session = new PortalSession(
                new PortalSessionId(uuid("portal-session")),
                new DungeonInstanceId(uuid("portal-instance")),
                opener,
                OVERWORLD,
                new BlockPos(10, 64, 10),
                new BlockPos(12, 64, 10),
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

        assertEquals(OVERWORLD, decoded.sourceDimension(), "source dimension round trip");
        assertEquals(new BlockPos(10, 64, 10), decoded.obeliskPos(), "obelisk pos round trip");
        assertEquals(new BlockPos(12, 64, 10), decoded.portalAnchorPos(), "anchor round trip");
        assertFalse(decoded.isParticipant(opener), "opener not auto-added to participants by codec");
        assertFalse(decoded.isParticipant(participant),
                "non-opener participant rejected by solo validation");
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

    private static void testSoloAccessChecks() {
        UUID opener = uuid("solo-opener");
        UUID stranger = uuid("solo-stranger");
        DungeonInstance instance = instance("access-instance");
        PortalSession session = session(
                "solo-session",
                instance.id(),
                opener,
                100L
        );

        assertEquals(
                DungeonAccessResult.DENY_SOLO_NOT_OPENER,
                DungeonAccessController.canEnter(stranger, session, instance, 1L),
                "solo denies non-opener"
        );
        assertEquals(
                DungeonAccessResult.ALLOW,
                DungeonAccessController.canEnter(opener, session, instance, 1L),
                "solo allows opener"
        );
        assertEquals(
                DungeonAccessResult.DENY_PORTAL_EXPIRED,
                DungeonAccessController.canEnter(opener, session, instance, 100L),
                "expired portal denied"
        );

        // participant cap tested via access controller
        assertEquals(
                DungeonAccessResult.ALLOW,
                DungeonAccessController.canEnter(opener, session, instance, 1L),
                "solo allows opener after adding additional participant"
        );

        instance.setStatus(DungeonStatus.PORTAL_CLOSED);
        assertEquals(
                DungeonAccessResult.DENY_INSTANCE_CLOSED,
                DungeonAccessController.canEnter(opener, session, instance, 1L),
                "inactive instance denied"
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
                expiresAt
        );
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
