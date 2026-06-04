package io.github.naimjeg.obeliskdepths.dungeon.site;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonDifficulty;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import io.github.naimjeg.obeliskdepths.dungeon.state.ReservedDungeonAggregate;
import io.github.naimjeg.obeliskdepths.dungeon.territory.DungeonBounds;
import io.github.naimjeg.obeliskdepths.dungeon.territory.DungeonTerritory;
import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class DungeonRuntimeSnapshotAuthorityTest {
    private DungeonRuntimeSnapshotAuthorityTest() {
    }

    public static void main(String[] args) throws IOException {
        reservedInstanceWithSnapshotResolves();
        reservedInstanceWithoutSnapshotFailsClosed();
        mismatchedInstanceOrSiteFailsClosed();
        runtimeResolutionDoesNotInvokeLoadedSiteReader();
        preparedEntryRecoveryUsesPersistedSnapshot();
    }

    private static void reservedInstanceWithSnapshotResolves() {
        DungeonInstanceId instanceId = instanceId("snapshot-instance");
        DungeonSiteKey key = new DungeonSiteKey(10, 11);
        DungeonManagerSavedData data = fullData(site(key), instanceId);

        Optional<ReservedDungeonAggregate> result =
                data.requireReservedDungeon(instanceId, key);

        assertTrue(
                result.isPresent(),
                "reserved aggregate resolves"
        );
        assertEquals(
                key,
                result.orElseThrow().site().key(),
                "aggregate contains correct site key"
        );
    }

    private static void reservedInstanceWithoutSnapshotFailsClosed() {
        DungeonManagerSavedData data = new DungeonManagerSavedData();
        DungeonInstanceId instanceId = instanceId("missing-snapshot-instance");
        DungeonSiteKey key = new DungeonSiteKey(12, 13);

        assertTrue(
                data.requireReservedDungeon(instanceId, key).isEmpty(),
                "missing instance fails closed"
        );
    }

    private static void mismatchedInstanceOrSiteFailsClosed() {
        DungeonInstanceId instanceId = instanceId("mismatch-instance");
        DungeonSiteKey key = new DungeonSiteKey(14, 15);
        DungeonManagerSavedData data = fullData(site(key), instanceId);

        assertTrue(
                data.requireReservedDungeon(
                        instanceId("other-instance"),
                        key
                ).isEmpty(),
                "wrong instance fails closed"
        );
        assertTrue(
                data.requireReservedDungeon(
                        instanceId,
                        new DungeonSiteKey(16, 17)
                ).isEmpty(),
                "wrong site fails closed"
        );
    }

    private static void runtimeResolutionDoesNotInvokeLoadedSiteReader()
            throws IOException {
        List<Path> runtimeSources = List.of(
                Path.of("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/encounter/DungeonEncounterDirector.java"),
                Path.of("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/reward/DungeonRewardPlacement.java"),
                Path.of("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/room/DungeonRoomRuntimeService.java"),
                Path.of("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/session/DungeonSessionPresence.java"),
                Path.of("src/main/java/io/github/naimjeg/obeliskdepths/command/DungeonDebugCommandUtil.java")
        );
        for (Path source : runtimeSources) {
            String text = Files.readString(source);
            assertFalse(
                    text.contains("LoadedDungeonSiteReader"),
                    source + " must not read loaded structure starts at runtime"
            );
            assertFalse(
                    text.contains("lookupLoaded("),
                    source + " must not read StructureStart at runtime"
            );
            assertTrue(
                    text.contains("requireReservedDungeon"),
                    source + " must use aggregate lookup authority"
            );
        }
    }

    private static void preparedEntryRecoveryUsesPersistedSnapshot()
            throws IOException {
        String entry = Files.readString(Path.of(
                "src/main/java/io/github/naimjeg/obeliskdepths/dungeon/interaction/DungeonPortalEntryService.java"
        ));
        assertOrder(
                entry,
                "requireReservedDungeon",
                "submitOrReusePreparedEntryRecovery",
                "portal recovery must require persisted snapshot before scheduling recovery"
        );
        assertFalse(
                entry.contains("LoadedDungeonSiteReader"),
                "portal recovery must not project generated sites"
        );

        String runtime = Files.readString(Path.of(
                "src/main/java/io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonPreparationRuntime.java"
        ));
        assertTrue(
                runtime.contains("requireReservedDungeon"),
                "recovery submission validates caller site through aggregate lookup"
        );
        assertFalse(
                runtime.contains("lookupLoaded("),
                "prepared-entry recovery must not read StructureStart"
        );
    }

    private static DungeonManagerSavedData fullData(
            DungeonSite site,
            DungeonInstanceId instanceId
    ) {
        DungeonManagerSavedData data = new DungeonManagerSavedData();
        DungeonInstance instance = new DungeonInstance(
                instanceId,
                site.key(),
                new DungeonDifficulty(1, 0.0F, 1.0F, 1),
                site.key().toTerritoryId(),
                site.startPos(),
                1L
        );
        data.instances().put(instance);
        data.territories().put(new DungeonTerritory(
                instance.territoryId(),
                instanceId,
                site.bounds(),
                site.startPos()
        ));
        data.roomStates().initializeRoomStates(instance, site);
        data.sites().reserve(site, instanceId, 1L);
        return data;
    }

    private static DungeonSite site(DungeonSiteKey key) {
        DungeonRoomId roomId = DungeonRoomId.of("start");
        BlockPos start = new BlockPos(0, 64, 0);
        DungeonBounds bounds = new DungeonBounds(0, 60, 0, 15, 80, 15);
        DungeonGeneratedRoom room = new DungeonGeneratedRoom(
                roomId,
                DungeonRoomType.START,
                bounds,
                start
        );
        return new DungeonSite(key, bounds, roomId, start, List.of(room));
    }

    private static DungeonInstanceId instanceId(String name) {
        return new DungeonInstanceId(UUID.nameUUIDFromBytes(
                name.getBytes(StandardCharsets.UTF_8)
        ));
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

    private static void assertOrder(
            String text,
            String first,
            String second,
            String message
    ) {
        int firstIndex = text.indexOf(first);
        int secondIndex = text.indexOf(second);
        if (firstIndex < 0 || secondIndex < 0 || firstIndex >= secondIndex) {
            throw new AssertionError(
                    message + ": expected '" + first + "' before '" + second + "'"
            );
        }
    }
}
