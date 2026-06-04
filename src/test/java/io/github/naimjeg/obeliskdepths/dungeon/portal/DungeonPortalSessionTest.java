package io.github.naimjeg.obeliskdepths.dungeon.portal;

import com.google.gson.JsonElement;
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
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class DungeonPortalSessionTest {
    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("minecraft", "overworld"));
    private static final ResourceKey<Level> NETHER =
            ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("minecraft", "the_nether"));

    private DungeonPortalSessionTest() {
    }

    public static void main(String[] args) {
        testPortalSessionCodecRoundTrip();
        testPortalLookupDistinguishesSourceDimension();
        testPortalLookupByInstanceIsDeterministic();
        testAccessChecks();
        testParticipantRollbackPreservesPreExistingMembership();
    }

    private static void testPortalSessionCodecRoundTrip() {
        UUID opener = UUID.nameUUIDFromBytes("portal-opener".getBytes());
        UUID participant = UUID.nameUUIDFromBytes("portal-participant".getBytes());
        PortalSession session = new PortalSession(
                new PortalSessionId(UUID.nameUUIDFromBytes("portal-session".getBytes())),
                new DungeonInstanceId(UUID.nameUUIDFromBytes("portal-instance".getBytes())),
                opener,
                OVERWORLD,
                new BlockPos(10, 64, 10),
                new BlockPos(12, 64, 10),
                PortalAdmissionMode.OPEN_JOIN,
                1200L
        );
        session.addParticipant(participant);

        JsonElement json = PortalSession.CODEC.encodeStart(JsonOps.INSTANCE, session)
                .getOrThrow();
        PortalSession decoded = PortalSession.CODEC.parse(JsonOps.INSTANCE, json)
                .getOrThrow();

        assertEquals(OVERWORLD, decoded.sourceDimension(), "source dimension round trip");
        assertEquals(new BlockPos(10, 64, 10), decoded.obeliskPos(), "obelisk pos round trip");
        assertEquals(new BlockPos(12, 64, 10), decoded.portalAnchorPos(), "anchor round trip");
        assertTrue(decoded.isParticipant(participant), "participants round trip");
    }

    private static void testPortalLookupDistinguishesSourceDimension() {
        DungeonManagerSavedData data = new DungeonManagerSavedData();
        DungeonInstance overworldInstance = instance("overworld-instance");
        DungeonInstance netherInstance = instance("nether-instance");
        putInstance(data, overworldInstance);
        putInstance(data, netherInstance);
        BlockPos sameObeliskPos = new BlockPos(5, 70, 5);
        PortalSession overworldSession = addPortalSession(
                data,
                overworldInstance.id(),
                UUID.nameUUIDFromBytes("overworld-opener".getBytes()),
                OVERWORLD,
                sameObeliskPos,
                sameObeliskPos.east(2),
                PortalAdmissionMode.OPEN_JOIN,
                0L
        );
        PortalSession netherSession = addPortalSession(
                data,
                netherInstance.id(),
                UUID.nameUUIDFromBytes("nether-opener".getBytes()),
                NETHER,
                sameObeliskPos,
                sameObeliskPos.west(2),
                PortalAdmissionMode.OPEN_JOIN,
                0L
        );

        assertEquals(
                Optional.of(overworldSession.id()),
                data.portalSessions()
                        .findActiveOpenJoinSession(
                                OVERWORLD,
                                sameObeliskPos,
                                10L,
                                instanceId -> data.instances()
                                        .get(instanceId)
                                        .map(instance -> instance.status() == DungeonStatus.ACTIVE)
                                        .orElse(false)
                        )
                        .map(PortalSession::id),
                "overworld lookup returns overworld session"
        );
        assertEquals(
                Optional.of(netherSession.id()),
                data.portalSessions()
                        .findActiveOpenJoinSession(
                                NETHER,
                                sameObeliskPos,
                                10L,
                                instanceId -> data.instances()
                                        .get(instanceId)
                                        .map(instance -> instance.status() == DungeonStatus.ACTIVE)
                                        .orElse(false)
                        )
                        .map(PortalSession::id),
                "nether lookup returns nether session"
        );
    }

    private static void testPortalLookupByInstanceIsDeterministic() {
        DungeonManagerSavedData data = new DungeonManagerSavedData();
        DungeonInstance instance = instance("deterministic-instance");
        putInstance(data, instance);
        UUID laterOpener = UUID.nameUUIDFromBytes("deterministic-later-opener".getBytes());
        UUID earlierOpener = UUID.nameUUIDFromBytes("deterministic-earlier-opener".getBytes());
        PortalSession later = session(
                "deterministic-later-session",
                instance.id(),
                laterOpener,
                PortalAdmissionMode.OPEN_JOIN,
                200L
        );
        PortalSession earlier = session(
                "deterministic-earlier-session",
                instance.id(),
                earlierOpener,
                PortalAdmissionMode.OPEN_JOIN,
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

    private static void testAccessChecks() {
        UUID opener = UUID.nameUUIDFromBytes("solo-opener".getBytes());
        UUID stranger = UUID.nameUUIDFromBytes("solo-stranger".getBytes());
        DungeonInstance instance = instance("access-instance");
        PortalSession solo = session(
                "solo-session",
                instance.id(),
                opener,
                PortalAdmissionMode.SOLO,
                100L
        );

        assertEquals(
                DungeonAccessResult.DENY_SOLO_NOT_OPENER,
                DungeonAccessController.canEnter(stranger, solo, instance, 1L),
                "solo denies non-opener"
        );
        assertEquals(
                DungeonAccessResult.DENY_PORTAL_EXPIRED,
                DungeonAccessController.canEnter(opener, solo, instance, 100L),
                "expired portal denied"
        );

        PortalSession openJoin = session(
                "open-join-session",
                instance.id(),
                opener,
                PortalAdmissionMode.OPEN_JOIN,
                100L
        );
        openJoin.addParticipant(UUID.nameUUIDFromBytes("p1".getBytes()));
        openJoin.addParticipant(UUID.nameUUIDFromBytes("p2".getBytes()));
        openJoin.addParticipant(UUID.nameUUIDFromBytes("p3".getBytes()));
        openJoin.addParticipant(UUID.nameUUIDFromBytes("p4".getBytes()));

        assertEquals(
                DungeonAccessResult.DENY_MAX_PARTICIPANTS,
                DungeonAccessController.canEnter(
                        UUID.nameUUIDFromBytes("p5".getBytes()),
                        openJoin,
                        instance,
                        1L
                ),
                "open-join participant cap enforced"
        );

        instance.setStatus(DungeonStatus.PORTAL_CLOSED);
        assertEquals(
                DungeonAccessResult.DENY_INSTANCE_CLOSED,
                DungeonAccessController.canEnter(opener, openJoin, instance, 1L),
                "inactive instance denied"
        );
    }

    private static void testParticipantRollbackPreservesPreExistingMembership() {
        UUID opener = UUID.nameUUIDFromBytes("rollback-opener".getBytes());
        UUID preExisting = UUID.nameUUIDFromBytes("rollback-existing".getBytes());
        UUID attempted = UUID.nameUUIDFromBytes("rollback-attempted".getBytes());
        DungeonInstance instance = instance("rollback-instance");
        PortalSession session = session(
                "rollback-session",
                instance.id(),
                opener,
                PortalAdmissionMode.OPEN_JOIN,
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
            PortalAdmissionMode admissionMode,
            long expiresAt
    ) {
        return new PortalSession(
                new PortalSessionId(UUID.nameUUIDFromBytes(sessionName.getBytes())),
                instanceId,
                opener,
                OVERWORLD,
                new BlockPos(0, 64, 0),
                new BlockPos(2, 64, 0),
                admissionMode,
                expiresAt
        );
    }

    private static DungeonInstance instance(String name) {
        return new DungeonInstance(
                new DungeonInstanceId(UUID.nameUUIDFromBytes(name.getBytes())),
                new DungeonSiteKey(0, 0),
                difficulty(),
                new DungeonSiteKey(0, 0).toTerritoryId(),
                new BlockPos(0, 64, 0),
                0L
        );
    }

    private static DungeonDifficulty difficulty() {
        return new DungeonDifficulty(1, 0.0F, 1.0F, 1);
    }

    private static void putInstance(
            DungeonManagerSavedData data,
            DungeonInstance instance
    ) {
        data.instances().put(instance);
    }

    private static PortalSession addPortalSession(
            DungeonManagerSavedData data,
            DungeonInstanceId instanceId,
            UUID opener,
            ResourceKey<Level> sourceDimension,
            BlockPos obeliskPos,
            BlockPos portalAnchorPos,
            PortalAdmissionMode admissionMode,
            long gameTime
    ) {
        return data.portalSessions().add(new PortalSession(
                PortalSessionId.create(),
                instanceId,
                opener,
                sourceDimension,
                obeliskPos,
                portalAnchorPos,
                admissionMode,
                gameTime + 20L * 60L
        ));
    }

    private static void assertTrue(
            boolean value,
            String message
    ) {
        if (!value) {
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
}
