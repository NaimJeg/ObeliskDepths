package io.github.naimjeg.obeliskdepths.dungeon.runtime;

import io.github.naimjeg.obeliskdepths.worldgen.structure.placement.ObeliskDungeonPlacementSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DungeonLocatorConfigurationTest {
    private static final Path STRUCTURE_SET =
            Path.of("src", "main", "resources", "data", "obeliskdepths", "worldgen", "structure_set", "obelisk_dungeons.json");

    private DungeonLocatorConfigurationTest() {
    }

    public static void main(String[] args) throws IOException {
        String json = Files.readString(STRUCTURE_SET);

        assertContains(json, "\"type\": \"minecraft:random_spread\"", "structure set should use random spread placement");
        assertContains(json, "\"spacing\": " + ObeliskDungeonPlacementSettings.SPACING, "Java spacing mirror must match JSON");
        assertContains(json, "\"separation\": " + ObeliskDungeonPlacementSettings.SEPARATION, "Java separation mirror must match JSON");
        assertContains(json, "\"salt\": " + ObeliskDungeonPlacementSettings.SALT, "Java salt mirror must match JSON");

        if (ObeliskDungeonPlacementSettings.MAX_LOOKUP_CANDIDATES <= 0) {
            throw new AssertionError("candidate lookup limit must be positive");
        }
    }

    private static void assertContains(
            String source,
            String expected,
            String message
    ) {
        if (!source.contains(expected)) {
            throw new AssertionError(message + ": missing " + expected);
        }
    }
}
