package io.github.naimjeg.obeliskdepths.dungeon.runtime;

import com.mojang.serialization.JsonOps;
import io.github.naimjeg.obeliskdepths.dungeon.encounter.DungeonEncounterPhase;
import io.github.naimjeg.obeliskdepths.dungeon.encounter.DungeonEncounterDirector;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.raid.BuiltinDungeonRaids;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidInstance;
import io.github.naimjeg.obeliskdepths.dungeon.reward.DungeonRewardRecord;
import io.github.naimjeg.obeliskdepths.dungeon.reward.DungeonRewardPlacement;
import io.github.naimjeg.obeliskdepths.dungeon.reward.DungeonRewardStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;

public final class DungeonRuntimeStateTest {
    private DungeonRuntimeStateTest() {
    }

    public static void main(String[] args) {
        testUnknownAndDuplicateMobResolution();
        testMissingMobStateIsBounded();
        testAuthoritativeNormalProgress();
        testBossKillDoesNotIncrementNormalProgress();
        testRaidCodecRestoresRuntimeProgress();
        testRewardStateRequiresPlacementForAvailability();
        testRewardPreferredOriginAndSearchVolume();
        testRewardClaimIsIdempotent();
    }

    private static void testUnknownAndDuplicateMobResolution() {
        DungeonRaidInstance encounter = encounter("resolution", 3);
        UUID tracked = UUID.nameUUIDFromBytes("tracked".getBytes());
        UUID unknown = UUID.nameUUIDFromBytes("unknown".getBytes());

        assertTrue(encounter.trackMob(tracked), "tracked mob is accepted");
        assertFalse(encounter.resolveMob(unknown), "unknown mob cannot resolve");
        assertTrue(encounter.resolveMob(tracked), "tracked mob resolves once");
        assertFalse(encounter.resolveMob(tracked), "duplicate resolve is rejected");
        assertEquals(0, encounter.resolvedMobIds().size(), "legacy resolved history is not retained");
    }

    private static void testMissingMobStateIsBounded() {
        DungeonRaidInstance encounter = encounter("missing", 3);
        UUID mob = UUID.nameUUIDFromBytes("missing-mob".getBytes());
        long start = 100L;

        encounter.trackMob(mob);
        assertTrue(encounter.markMobTemporarilyMissing(mob, start), "missing timestamp is persisted");
        assertFalse(encounter.markMobTemporarilyMissing(mob, start + 20L), "missing timestamp is stable");
        assertEquals(Optional.of(start), encounter.missingSinceGameTime(mob), "missing since value");
        assertTrue(
                start + DungeonEncounterDirector.MISSING_MOB_RECONCILE_TIMEOUT_TICKS
                        >= encounter.missingSinceGameTime(mob).orElseThrow(),
                "timeout can be evaluated from persisted timestamp"
        );
        assertTrue(encounter.resolveMob(mob), "permanently missing mob can be terminally reconciled");
    }

    private static void testAuthoritativeNormalProgress() {
        DungeonRaidInstance encounter = encounter("progress", 2);

        assertTrue(encounter.creditNormalKill(), "first normal kill credits");
        assertTrue(encounter.creditNormalKill(), "second normal kill credits");
        assertFalse(encounter.creditNormalKill(), "quota clamps extra normal progress");
        assertEquals(2, encounter.creditedNormalKills(), "credited normal kills are authoritative");
        assertTrue(encounter.normalKillQuotaComplete(), "quota completion uses encounter state");
    }

    private static void testBossKillDoesNotIncrementNormalProgress() {
        DungeonRaidInstance encounter = encounter("boss", 2);
        encounter.setEncounterPhase(DungeonEncounterPhase.BOSS);
        assertTrue(encounter.markBossCompleted(), "boss completion records once");
        assertEquals(0, encounter.creditedNormalKills(), "boss completion does not credit normal progress");
    }

    private static void testRaidCodecRestoresRuntimeProgress() {
        DungeonRaidInstance encounter = encounter("codec", 5);
        UUID mob = UUID.nameUUIDFromBytes("codec-mob".getBytes());
        encounter.trackMob(mob);
        encounter.markMobTemporarilyMissing(mob, 42L);
        encounter.creditNormalKill();

        var encoded = DungeonRaidInstance.CODEC.encodeStart(JsonOps.INSTANCE, encounter)
                .getOrThrow();
        DungeonRaidInstance decoded = DungeonRaidInstance.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow();

        assertEquals(5, decoded.normalKillQuota(), "quota survives codec");
        assertEquals(1, decoded.creditedNormalKills(), "progress survives codec");
        assertEquals(DungeonEncounterPhase.COMBAT, decoded.encounterPhase(), "phase survives codec");
        assertTrue(decoded.trackedMobIds().contains(mob), "tracked mob survives codec");
        assertEquals(Optional.of(42L), decoded.missingSinceGameTime(mob), "missing timestamp survives codec");
    }

    private static void testRewardStateRequiresPlacementForAvailability() {
        DungeonRewardRecord reward = DungeonRewardRecord.bossDefeated(
                new DungeonInstanceId(UUID.nameUUIDFromBytes("reward-instance".getBytes())),
                Optional.empty(),
                0L
        );

        assertEquals(DungeonRewardStatus.BOSS_DEFEATED, reward.status(), "boss death is not availability");
        assertTrue(reward.markPlacementPending(), "reward can enter placement pending");
        assertEquals(DungeonRewardStatus.PLACEMENT_PENDING, reward.status(), "pending is explicit");
        assertTrue(reward.markAvailable(new BlockPos(1, 2, 3)), "placement makes reward available");
        assertEquals(Optional.of(new BlockPos(1, 2, 3)), reward.rewardPos(), "available reward has position");
    }

    private static void testRewardPreferredOriginAndSearchVolume() {
        BlockPos origin = new BlockPos(10, 64, -5);
        DungeonRewardRecord reward = DungeonRewardRecord.bossDefeated(
                new DungeonInstanceId(UUID.nameUUIDFromBytes("origin-instance".getBytes())),
                Optional.empty(),
                Optional.of(origin),
                0L
        );

        assertEquals(Optional.of(origin), reward.preferredPlacementOrigin(), "boss death position is saved");
        assertEquals(Optional.empty(), reward.placedMainPos(), "boss death position is not overloaded as placed position");

        List<BlockPos> candidates = DungeonRewardPlacement.orderedPlacementCandidates(origin);
        assertEquals(4096, candidates.size(), "16x16x16 search volume has exact size");
        assertEquals(origin, candidates.getFirst(), "nearest candidate is the origin");
        assertEquals(candidates, DungeonRewardPlacement.orderedPlacementCandidates(origin), "candidate order is deterministic");

        int minDx = Integer.MAX_VALUE;
        int maxDx = Integer.MIN_VALUE;
        int minDy = Integer.MAX_VALUE;
        int maxDy = Integer.MIN_VALUE;
        int minDz = Integer.MAX_VALUE;
        int maxDz = Integer.MIN_VALUE;
        for (BlockPos candidate : candidates) {
            minDx = Math.min(minDx, candidate.getX() - origin.getX());
            maxDx = Math.max(maxDx, candidate.getX() - origin.getX());
            minDy = Math.min(minDy, candidate.getY() - origin.getY());
            maxDy = Math.max(maxDy, candidate.getY() - origin.getY());
            minDz = Math.min(minDz, candidate.getZ() - origin.getZ());
            maxDz = Math.max(maxDz, candidate.getZ() - origin.getZ());
        }

        assertEquals(-8, minDx, "minimum x offset");
        assertEquals(7, maxDx, "maximum x offset");
        assertEquals(-8, minDy, "minimum y offset");
        assertEquals(7, maxDy, "maximum y offset");
        assertEquals(-8, minDz, "minimum z offset");
        assertEquals(7, maxDz, "maximum z offset");
        assertTrue(
                DungeonRewardPlacement.squaredDistance(origin, candidates.get(1))
                        <= DungeonRewardPlacement.squaredDistance(origin, candidates.getLast()),
                "nearer candidates are ordered before farther candidates"
        );
    }

    private static void testRewardClaimIsIdempotent() {
        DungeonRewardRecord reward = DungeonRewardRecord.bossDefeated(
                new DungeonInstanceId(UUID.nameUUIDFromBytes("claim-instance".getBytes())),
                Optional.empty(),
                0L
        );
        reward.markAvailable(new BlockPos(1, 2, 3));

        assertTrue(reward.markOpened(), "first physical claim opens reward");
        assertFalse(reward.markOpened(), "duplicate open is idempotent");
        assertFalse(reward.markClaiming(), "opened reward cannot enter legacy claiming");
        assertTrue(reward.markClaimed(), "completed spray changes state");
        assertFalse(reward.markClaimed(), "duplicate claim is idempotent");
        assertEquals(DungeonRewardStatus.CLAIMED, reward.status(), "reward remains claimed");
    }

    private static DungeonRaidInstance encounter(
            String name,
            int quota
    ) {
        return DungeonRaidInstance.createInstanceEncounter(
                new DungeonInstanceId(UUID.nameUUIDFromBytes((name + "-instance").getBytes())),
                BuiltinDungeonRaids.COMBAT_ROOM,
                quota,
                3,
                0L
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
            Object expected,
            Object actual,
            String message
    ) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
