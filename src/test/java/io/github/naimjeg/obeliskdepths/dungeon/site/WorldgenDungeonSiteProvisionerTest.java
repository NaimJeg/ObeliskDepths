package io.github.naimjeg.obeliskdepths.dungeon.site;

import io.github.naimjeg.obeliskdepths.dungeon.territory.DungeonBounds;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class WorldgenDungeonSiteProvisionerTest {
    private WorldgenDungeonSiteProvisionerTest() {
    }

    public static void main(String[] args) throws IOException {
        plansEveryChunkIntersectingBoundsOnce();
        appliesOneChunkSafetyMargin();
        rejectsUnreasonableBounds();
        newlyGeneratedSitePreparesBoundsOnce();
        existingSitePreparesBoundsOnce();
        provisionerDoesNotPlaceBlocksManually();
    }

    private static void plansEveryChunkIntersectingBoundsOnce() {
        WorldgenDungeonSiteProvisioner.ChunkGenerationPlan plan =
                WorldgenDungeonSiteProvisioner.planChunksForBounds(
                        new DungeonBounds(0, 0, 0, 31, 10, 31),
                        0,
                        16
                );

        assertEquals(0, plan.minChunkX(), "min chunk x");
        assertEquals(0, plan.minChunkZ(), "min chunk z");
        assertEquals(1, plan.maxChunkX(), "max chunk x");
        assertEquals(1, plan.maxChunkZ(), "max chunk z");
        assertEquals(
                List.of(
                        new WorldgenDungeonSiteProvisioner.SiteChunk(0, 0),
                        new WorldgenDungeonSiteProvisioner.SiteChunk(0, 1),
                        new WorldgenDungeonSiteProvisioner.SiteChunk(1, 0),
                        new WorldgenDungeonSiteProvisioner.SiteChunk(1, 1)
                ),
                plan.chunks(),
                "chunks intersecting bounds"
        );
        assertEquals(
                new LinkedHashSet<>(plan.chunks()).size(),
                plan.chunks().size(),
                "planned chunks are unique"
        );
    }

    private static void appliesOneChunkSafetyMargin() {
        WorldgenDungeonSiteProvisioner.ChunkGenerationPlan plan =
                WorldgenDungeonSiteProvisioner.planChunksForBounds(
                        new DungeonBounds(0, 0, 0, 31, 10, 31),
                        1,
                        32
                );

        assertEquals(-1, plan.minChunkX(), "margin min chunk x");
        assertEquals(-1, plan.minChunkZ(), "margin min chunk z");
        assertEquals(2, plan.maxChunkX(), "margin max chunk x");
        assertEquals(2, plan.maxChunkZ(), "margin max chunk z");
        assertEquals(16, plan.chunks().size(), "margin chunk count");
        assertEquals(
                new LinkedHashSet<>(plan.chunks()).size(),
                plan.chunks().size(),
                "margin chunks are unique"
        );
    }

    private static void rejectsUnreasonableBounds() {
        assertThrows(
                () -> WorldgenDungeonSiteProvisioner.planChunksForBounds(
                        new DungeonBounds(0, 0, 0, 16 * 64, 10, 16 * 64),
                        1,
                        32
                ),
                "unreasonable site bounds should fail clearly"
        );
    }

    private static void newlyGeneratedSitePreparesBoundsOnce() {
        ResolvedDungeonSite site = testResolvedSite();
        List<DungeonBounds> prepared = new ArrayList<>();

        ResolvedDungeonSite result =
                WorldgenDungeonSiteProvisioner.prepareGeneratedSiteForEntry(
                        site,
                        prepared::add
                );

        assertEquals(site, result, "generated preparation returns same site");
        assertEquals(1, prepared.size(), "generated site prepared once");
        assertEquals(site.site().bounds(), prepared.getFirst(),
                "generated site bounds prepared");
    }

    private static void existingSitePreparesBoundsOnce() {
        ResolvedDungeonSite site = testResolvedSite();
        List<DungeonBounds> prepared = new ArrayList<>();

        ResolvedDungeonSite result =
                WorldgenDungeonSiteProvisioner.prepareExistingSiteForEntry(
                        site,
                        prepared::add
                );

        assertEquals(site, result, "existing preparation returns same site");
        assertEquals(1, prepared.size(), "existing site prepared once");
        assertEquals(site.site().bounds(), prepared.getFirst(),
                "existing site bounds prepared");
    }

    private static void provisionerDoesNotPlaceBlocksManually()
            throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/io/github/naimjeg/obeliskdepths/dungeon/site/WorldgenDungeonSiteProvisioner.java"
        ));
        Set<String> forbidden = Set.of(
                ".setBlock(",
                ".setBlockAndUpdate(",
                ".destroyBlock(",
                ".removeBlock("
        );
        for (String token : forbidden) {
            assertFalse(
                    source.contains(token),
                    "provisioner must not manually place or remove blocks: "
                            + token
            );
        }
        assertTrue(
                source.contains("ChunkStatus.FULL"),
                "provisioner requests vanilla FULL chunk generation"
        );
        assertFalse(
                source.contains("prepareForEntry(level, resolved)"),
                "generated site path must not prepare accepted bounds twice"
        );
    }

    private static ResolvedDungeonSite testResolvedSite() {
        DungeonBounds bounds = new DungeonBounds(0, 0, 0, 31, 8, 31);
        DungeonRoomId startId = DungeonRoomId.of("start");
        DungeonGeneratedRoom room = new DungeonGeneratedRoom(
                startId,
                DungeonRoomType.START,
                bounds,
                new BlockPos(4, 1, 4)
        );
        return new ResolvedDungeonSite(
                new DungeonSite(
                        new DungeonSiteKey(1, 2),
                        bounds,
                        startId,
                        new BlockPos(4, 1, 4),
                        List.of(room)
                ),
                DungeonSiteProjectionSource.GENERATED_STRUCTURE_START
        );
    }

    private static void assertThrows(
            Runnable action,
            String message
    ) {
        try {
            action.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new AssertionError(message);
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
