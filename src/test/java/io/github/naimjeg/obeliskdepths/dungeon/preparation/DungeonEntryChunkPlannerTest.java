package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonGeneratedRoom;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import io.github.naimjeg.obeliskdepths.dungeon.territory.DungeonBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.List;

public final class DungeonEntryChunkPlannerTest {
    private DungeonEntryChunkPlannerTest() {
    }

    public static void main(String[] args) {
        DungeonAsyncTestSupport.bootstrapMinecraft();

        oneBlockRoom();
        oneChunkRoom();
        roomCrossingPositiveBoundary();
        roomCrossingOnlyXBoundary();
        roomCrossingOnlyZBoundary();
        roomCrossingNegativeBoundary();
        mixedSignCoordinates();
        inclusiveMaxAt15();
        inclusiveMaxAt16();
        oneChunkSafetyRing();
        rowMajorOrdering();
        noDuplicates();
        resultIsImmutable();
        missingEntryRoomRejected();
        negativeSafetyRingRejected();
        excessiveSafetyRingRejected();
        negativeBlockCoordinatesFloorDiv();
    }

    private static void roomCrossingOnlyXBoundary() {
        DungeonEntryChunkPlan plan = DungeonEntryChunkPlanner.plan(
                siteWithRoom(bounds(15, 0, 2, 16, 0, 3)), 0
        );
        check(plan.chunks().equals(List.of(
                new ChunkPos(0, 0), new ChunkPos(1, 0)
        )), "cross X only: exact ordered footprint");
    }

    private static void roomCrossingOnlyZBoundary() {
        DungeonEntryChunkPlan plan = DungeonEntryChunkPlanner.plan(
                siteWithRoom(bounds(2, 0, 15, 3, 0, 16)), 0
        );
        check(plan.chunks().equals(List.of(
                new ChunkPos(0, 0), new ChunkPos(0, 1)
        )), "cross Z only: exact ordered footprint");
    }

    private static void mixedSignCoordinates() {
        DungeonEntryChunkPlan plan = DungeonEntryChunkPlanner.plan(
                siteWithRoom(bounds(-17, 0, -1, 16, 0, 1)), 0
        );
        check(plan.roomMinChunk().equals(new ChunkPos(-2, -1)),
                "mixed sign: floor-div minimum");
        check(plan.roomMaxChunk().equals(new ChunkPos(1, 0)),
                "mixed sign: positive maximum");
        check(plan.chunks().size() == 8, "mixed sign: complete footprint");
    }

    private static void oneBlockRoom() {
        DungeonSite site = siteWithRoom(bounds(0, 0, 0, 0, 0, 0));
        DungeonEntryChunkPlan plan = DungeonEntryChunkPlanner.plan(site, 0);

        check(plan.chunks().size() == 1, "one block: single chunk");
        check(plan.chunks().get(0).equals(new ChunkPos(0, 0)), "one block: chunk (0,0)");
    }

    private static void oneChunkRoom() {
        DungeonSite site = siteWithRoom(bounds(0, 0, 0, 15, 0, 15));
        DungeonEntryChunkPlan plan = DungeonEntryChunkPlanner.plan(site, 0);

        check(plan.chunks().size() == 1, "one chunk: single chunk");
        check(plan.chunks().get(0).equals(new ChunkPos(0, 0)), "one chunk: chunk (0,0)");
        check(plan.roomMinChunk().equals(new ChunkPos(0, 0)), "one chunk: room min");
        check(plan.roomMaxChunk().equals(new ChunkPos(0, 0)), "one chunk: room max");
    }

    private static void roomCrossingPositiveBoundary() {
        DungeonSite site = siteWithRoom(bounds(10, 0, 10, 25, 0, 25));
        DungeonEntryChunkPlan plan = DungeonEntryChunkPlanner.plan(site, 0);

        check(plan.chunks().size() == 4, "cross pos: 4 chunks");
        check(plan.roomMinChunk().equals(new ChunkPos(0, 0)), "cross pos: min chunk (0,0)");
        check(plan.roomMaxChunk().equals(new ChunkPos(1, 1)), "cross pos: max chunk (1,1)");
        check(plan.chunks().contains(new ChunkPos(0, 0)), "cross pos: (0,0)");
        check(plan.chunks().contains(new ChunkPos(0, 1)), "cross pos: (0,1)");
        check(plan.chunks().contains(new ChunkPos(1, 0)), "cross pos: (1,0)");
        check(plan.chunks().contains(new ChunkPos(1, 1)), "cross pos: (1,1)");
    }

    private static void roomCrossingNegativeBoundary() {
        DungeonSite site = siteWithRoom(bounds(-25, 0, -25, -10, 0, -10));
        DungeonEntryChunkPlan plan = DungeonEntryChunkPlanner.plan(site, 0);

        check(plan.roomMinChunk().equals(new ChunkPos(-2, -2)), "cross neg: min chunk");
        check(plan.roomMaxChunk().equals(new ChunkPos(-1, -1)), "cross neg: max chunk");
        check(plan.chunks().size() == 4, "cross neg: 4 chunks");
        check(plan.chunks().contains(new ChunkPos(-2, -2)), "cross neg: (-2,-2)");
        check(plan.chunks().contains(new ChunkPos(-2, -1)), "cross neg: (-2,-1)");
        check(plan.chunks().contains(new ChunkPos(-1, -2)), "cross neg: (-1,-2)");
        check(plan.chunks().contains(new ChunkPos(-1, -1)), "cross neg: (-1,-1)");
    }

    private static void inclusiveMaxAt15() {
        DungeonSite site = siteWithRoom(bounds(0, 0, 0, 15, 0, 15));
        DungeonEntryChunkPlan plan = DungeonEntryChunkPlanner.plan(site, 0);

        check(plan.roomMaxChunk().equals(new ChunkPos(0, 0)), "max at 15: still chunk 0");
    }

    private static void inclusiveMaxAt16() {
        DungeonSite site = siteWithRoom(bounds(0, 0, 0, 16, 0, 16));
        DungeonEntryChunkPlan plan = DungeonEntryChunkPlanner.plan(site, 0);

        check(plan.roomMaxChunk().equals(new ChunkPos(1, 1)), "max at 16: chunk 1");
    }

    private static void oneChunkSafetyRing() {
        DungeonSite site = siteWithRoom(bounds(0, 0, 0, 15, 0, 15));
        DungeonEntryChunkPlan plan = DungeonEntryChunkPlanner.plan(site, 1);

        check(plan.requestedMinChunk().equals(new ChunkPos(-1, -1)), "safety ring: min");
        check(plan.requestedMaxChunk().equals(new ChunkPos(1, 1)), "safety ring: max");
        check(plan.chunks().size() == 9, "safety ring: 3x3 = 9 chunks");
    }

    private static void rowMajorOrdering() {
        DungeonSite site = siteWithRoom(bounds(0, 0, 0, 16, 0, 16));
        DungeonEntryChunkPlan plan = DungeonEntryChunkPlanner.plan(site, 0);

        List<ChunkPos> chunks = plan.chunks();
        check(chunks.get(0).equals(new ChunkPos(0, 0)), "row major: (0,0)");
        check(chunks.get(1).equals(new ChunkPos(0, 1)), "row major: (0,1)");
        check(chunks.get(2).equals(new ChunkPos(1, 0)), "row major: (1,0)");
        check(chunks.get(3).equals(new ChunkPos(1, 1)), "row major: (1,1)");
    }

    private static void noDuplicates() {
        DungeonSite site = siteWithRoom(bounds(0, 0, 0, 16, 0, 16));
        DungeonEntryChunkPlan plan = DungeonEntryChunkPlanner.plan(site, 0);

        List<ChunkPos> chunks = plan.chunks();
        long distinctCount = chunks.stream().distinct().count();
        check(distinctCount == (long) chunks.size(), "no dupes: all unique");
    }

    private static void resultIsImmutable() {
        DungeonEntryChunkPlan plan = DungeonEntryChunkPlanner.plan(
                siteWithRoom(bounds(0, 0, 0, 16, 0, 16)), 0
        );
        try {
            plan.chunks().add(new ChunkPos(2, 2));
            check(false, "immutable: mutation should throw");
        } catch (UnsupportedOperationException expected) {
        }
    }

    private static void missingEntryRoomRejected() {
        DungeonSite site = siteWithoutEntryRoom();
        try {
            DungeonEntryChunkPlanner.plan(site, 0);
            check(false, "missing room: should throw");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("primary-entry room"), "missing room: message");
        }
    }

    private static void negativeSafetyRingRejected() {
        DungeonSite site = siteWithRoom(bounds(0, 0, 0, 15, 0, 15));
        try {
            DungeonEntryChunkPlanner.plan(site, -1);
            check(false, "negative safety: should throw");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("non-negative"), "negative safety: message");
        }
    }

    private static void excessiveSafetyRingRejected() {
        DungeonSite site = siteWithRoom(bounds(0, 0, 0, 15, 0, 15));
        try {
            DungeonEntryChunkPlanner.plan(site, 17);
            check(false, "excessive safety: should throw");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("too large"),
                    "excessive safety: message");
        }
    }

    private static void negativeBlockCoordinatesFloorDiv() {
        DungeonSite site = siteWithRoom(bounds(-1, 0, -1, -1, 0, -1));
        DungeonEntryChunkPlan plan = DungeonEntryChunkPlanner.plan(site, 0);

        check(plan.roomMinChunk().equals(new ChunkPos(-1, -1)), "neg floorDiv: chunk -1");
        check(plan.roomMaxChunk().equals(new ChunkPos(-1, -1)), "neg floorDiv: single chunk");
    }

    private static DungeonSite siteWithRoom(DungeonBounds bounds) {
        DungeonRoomId roomId = DungeonRoomId.of("test_start");
        DungeonGeneratedRoom room = new DungeonGeneratedRoom(
                roomId,
                DungeonRoomType.START,
                bounds,
                new BlockPos(0, 0, 0)
        );
        return new DungeonSite(
                new DungeonSiteKey(0, 0),
                bounds,
                roomId,
                new BlockPos(0, 0, 0),
                List.of(room)
        );
    }

    private static DungeonSite siteWithoutEntryRoom() {
        DungeonRoomId roomId = DungeonRoomId.of("test_combat");
        DungeonGeneratedRoom room = new DungeonGeneratedRoom(
                roomId,
                DungeonRoomType.COMBAT,
                bounds(0, 0, 0, 15, 0, 15),
                new BlockPos(0, 0, 0)
        );
        return new DungeonSite(
                new DungeonSiteKey(0, 0),
                bounds(0, 0, 0, 15, 0, 15),
                roomId,
                new BlockPos(0, 0, 0),
                List.of(room)
        );
    }

    private static DungeonBounds bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return new DungeonBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
