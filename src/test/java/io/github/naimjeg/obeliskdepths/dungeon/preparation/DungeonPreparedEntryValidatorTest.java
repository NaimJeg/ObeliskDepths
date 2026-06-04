package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.id.PortalSessionId;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class DungeonPreparedEntryValidatorTest {
    private DungeonPreparedEntryValidatorTest() {
    }

    public static void main(String[] args) {
        DungeonAsyncTestSupport.bootstrapMinecraft();
        validEntryChecksEveryChunkAndKeepsStoredDestination();
        missingChunkStopsValidationWithoutPreparingAnything();
        identityMismatchSkipsChunkAccess();
        closedAndStaleEntriesAreRejected();
    }

    private static void validEntryChecksEveryChunkAndKeepsStoredDestination() {
        PortalSessionId sessionId = PortalSessionId.create();
        DungeonInstanceId instanceId = DungeonInstanceId.create();
        DungeonSiteKey siteKey = new DungeonSiteKey(3, -2);
        PreparedDungeonDestination destination =
                new PreparedDungeonDestination(new Vec3(48.5D, 70.0D, -31.5D));
        DungeonPreparedPortalEntry entry = entry(
                sessionId,
                instanceId,
                siteKey,
                destination,
                List.of(new ChunkPos(2, 0), new ChunkPos(-1, 4), new ChunkPos(0, 0)),
                100L
        );
        ArrayList<ChunkPos> checked = new ArrayList<>();

        DungeonPreparedEntryValidation result = DungeonPreparedEntryValidator.validate(
                sessionId, instanceId, siteKey, entry, 101L,
                chunkPos -> {
                    checked.add(chunkPos);
                    return true;
                }
        );

        check(result == DungeonPreparedEntryValidation.VALID,
                "valid entry accepted");
        check(checked.equals(entry.entryChunks()),
                "every deterministic planned chunk checked exactly once");
        check(entry.destination() == destination,
                "validation retains the stored prepared destination identity");
        entry.close();
    }

    private static void missingChunkStopsValidationWithoutPreparingAnything() {
        PortalSessionId sessionId = PortalSessionId.create();
        DungeonInstanceId instanceId = DungeonInstanceId.create();
        DungeonSiteKey siteKey = new DungeonSiteKey(0, 0);
        DungeonPreparedPortalEntry entry = entry(
                sessionId,
                instanceId,
                siteKey,
                new PreparedDungeonDestination(new Vec3(0.5D, 64.0D, 0.5D)),
                List.of(new ChunkPos(0, 0), new ChunkPos(1, 0), new ChunkPos(2, 0)),
                10L
        );
        ArrayList<ChunkPos> checked = new ArrayList<>();

        DungeonPreparedEntryValidation result = DungeonPreparedEntryValidator.validate(
                sessionId, instanceId, siteKey, entry, 11L,
                chunkPos -> {
                    checked.add(chunkPos);
                    return chunkPos.x() != 1;
                }
        );

        check(result == DungeonPreparedEntryValidation.CHUNK_UNAVAILABLE,
                "missing prepared chunk denies entry");
        check(checked.equals(List.of(new ChunkPos(0, 0), new ChunkPos(1, 0))),
                "validation stops at the first unavailable chunk");
        entry.close();
    }

    private static void identityMismatchSkipsChunkAccess() {
        PortalSessionId sessionId = PortalSessionId.create();
        DungeonInstanceId instanceId = DungeonInstanceId.create();
        DungeonSiteKey siteKey = new DungeonSiteKey(0, 0);
        DungeonPreparedPortalEntry entry = entry(
                sessionId,
                instanceId,
                siteKey,
                new PreparedDungeonDestination(Vec3.ZERO),
                List.of(new ChunkPos(0, 0)),
                10L
        );
        int[] checks = {0};

        DungeonPreparedEntryValidation result = DungeonPreparedEntryValidator.validate(
                PortalSessionId.create(), instanceId, siteKey, entry, 11L,
                chunkPos -> {
                    checks[0]++;
                    return true;
                }
        );

        check(result == DungeonPreparedEntryValidation.IDENTITY_MISMATCH,
                "session identity mismatch rejected");
        check(checks[0] == 0, "identity mismatch performs no chunk access");
        entry.close();
    }

    private static void closedAndStaleEntriesAreRejected() {
        PortalSessionId sessionId = PortalSessionId.create();
        DungeonInstanceId instanceId = DungeonInstanceId.create();
        DungeonSiteKey siteKey = new DungeonSiteKey(0, 0);
        DungeonPreparedPortalEntry closed = entry(
                sessionId, instanceId, siteKey,
                new PreparedDungeonDestination(Vec3.ZERO),
                List.of(new ChunkPos(0, 0)), 10L
        );
        closed.close();
        check(DungeonPreparedEntryValidator.validate(
                        sessionId, instanceId, siteKey, closed, 11L,
                        ignored -> true
                ) == DungeonPreparedEntryValidation.CLOSED_OR_STALE,
                "closed entry rejected");

        DungeonPreparedPortalEntry stale = entry(
                sessionId, instanceId, siteKey,
                new PreparedDungeonDestination(Vec3.ZERO),
                List.of(new ChunkPos(0, 0)), 10L
        );
        check(DungeonPreparedEntryValidator.validate(
                        sessionId, instanceId, siteKey, stale, 1_210L,
                        ignored -> true
                ) == DungeonPreparedEntryValidation.CLOSED_OR_STALE,
                "stale entry rejected");
        stale.close();
    }

    private static DungeonPreparedPortalEntry entry(
            PortalSessionId sessionId,
            DungeonInstanceId instanceId,
            DungeonSiteKey siteKey,
            PreparedDungeonDestination destination,
            List<ChunkPos> chunks,
            long gameTime
    ) {
        return new DungeonPreparedPortalEntry(
                sessionId,
                instanceId,
                siteKey,
                destination,
                chunks,
                new DungeonPreparationLeaseBundle(List.of()),
                gameTime
        );
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
