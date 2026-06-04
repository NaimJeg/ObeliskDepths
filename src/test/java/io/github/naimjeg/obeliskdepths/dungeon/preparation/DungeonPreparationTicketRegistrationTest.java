package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DungeonPreparationTicketRegistrationTest {
    private static final Path MAIN = Path.of("src", "main", "java");

    private DungeonPreparationTicketRegistrationTest() {}

    public static void main(String[] args) throws IOException {
        ticketTypeRegistered();
        productionBackendUsesRadiusZeroAddAndRemove();
    }

    private static void ticketTypeRegistered() throws IOException {
        String mod = read("io/github/naimjeg/obeliskdepths/ObeliskDepths.java");
        String tickets = read("io/github/naimjeg/obeliskdepths/registry/ModTicketTypes.java");
        assertContains(mod, "ModTicketTypes.register(modEventBus)",
                "mod constructor should register ticket types");
        assertContains(tickets, "DUNGEON_PREPARATION", "ticket holder should exist");
        assertContains(tickets, "\"dungeon_preparation\"", "ticket id should be stable");
        assertContains(tickets, "TicketType.FLAG_LOADING", "ticket should load chunks");
    }

    private static void productionBackendUsesRadiusZeroAddAndRemove() throws IOException {
        String manager = read(
                "io/github/naimjeg/obeliskdepths/dungeon/preparation/chunk/DungeonChunkLeaseManager.java"
        );
        assertContains(manager, "addTicketAndLoadWithRadius", "backend should load with ticket");
        assertContains(manager, "removeTicketWithRadius", "backend should release matching ticket");
        assertContains(manager, "ModTicketTypes.DUNGEON_PREPARATION.get()",
                "backend should use preparation ticket");
        assertContains(manager, "chunkPos,\n                                0",
                "add radius should be zero");
        assertContains(manager, "chunkPos,\n                    0",
                "release radius should be zero");
    }

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative)).replace("\r\n", "\n");
    }

    private static void assertContains(
            String source,
            String expected,
            String message
    ) {
        if (!source.contains(expected)) {
            throw new AssertionError(message + ": missing '" + expected + "'");
        }
    }
}
