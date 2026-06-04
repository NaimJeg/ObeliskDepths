package io.github.naimjeg.obeliskdepths.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.worldgen.ModBiomes;
import io.github.naimjeg.obeliskdepths.worldgen.ModNoiseSettings;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GeneratedResourceManifestTest {
    private static final Path PROJECT_ROOT = Path.of("")
            .toAbsolutePath()
            .normalize();
    private static final Path GENERATED_RESOURCES =
            PROJECT_ROOT.resolve("src/generated/resources");
    private static final Path PROCESSED_RESOURCES =
            PROJECT_ROOT.resolve("build/resources/main");
    private static final Path DIMENSION_RESOURCE = PROJECT_ROOT.resolve(
            "src/main/resources/data/obeliskdepths/dimension/obelisk_depths.json"
    );

    private GeneratedResourceManifestTest() {
    }

    public static void main(String[] args) throws Exception {
        Identifier biomeId = ModBiomes.GREAT_SWAMP.identifier();
        Identifier noiseSettingsId = ModNoiseSettings.OBLISK_DEPTHS.identifier();

        assertEquals(
                Identifier.fromNamespaceAndPath(ObeliskDepths.MOD_ID,
                        "great_swamp"),
                biomeId,
                "great swamp biome key"
        );
        assertEquals(
                Identifier.fromNamespaceAndPath(ObeliskDepths.MOD_ID,
                        "obelisk_depths"),
                noiseSettingsId,
                "obelisk depths noise settings key"
        );

        Path generatedBiome = resourcePath(GENERATED_RESOURCES,
                "worldgen/biome", biomeId);
        Path generatedNoiseSettings = resourcePath(GENERATED_RESOURCES,
                "worldgen/noise_settings", noiseSettingsId);
        Path processedBiome = resourcePath(PROCESSED_RESOURCES,
                "worldgen/biome", biomeId);
        Path processedNoiseSettings = resourcePath(PROCESSED_RESOURCES,
                "worldgen/noise_settings", noiseSettingsId);

        assertFile(generatedBiome, "generated biome json");
        assertFile(generatedNoiseSettings, "generated noise-settings json");
        assertFile(processedBiome, "processed biome json");
        assertFile(processedNoiseSettings, "processed noise-settings json");

        JsonObject dimension = readJson(DIMENSION_RESOURCE).getAsJsonObject();
        JsonObject generator = dimension.getAsJsonObject("generator");
        JsonObject biomeSource = generator.getAsJsonObject("biome_source");

        assertEquals(
                biomeId.toString(),
                biomeSource.get("biome").getAsString(),
                "dimension biome reference"
        );
        assertEquals(
                noiseSettingsId.toString(),
                generator.get("settings").getAsString(),
                "dimension noise-settings reference"
        );

        assertGeneratedBiomeShape(generatedBiome);
        assertGeneratedNoiseSettingsShape(generatedNoiseSettings);
        assertGradlePipelineIncludesGeneratedResources();
        assertProviderAndReloadDirectoriesMatch();
    }

    private static Path resourcePath(
            Path root,
            String registryDirectory,
            Identifier id
    ) {
        return root
                .resolve("data")
                .resolve(id.getNamespace())
                .resolve(registryDirectory)
                .resolve(id.getPath() + ".json");
    }

    private static void assertGeneratedBiomeShape(Path path) throws IOException {
        JsonObject json = readJson(path).getAsJsonObject();
        assertTrue(json.has("temperature"), "biome should contain temperature");
        assertTrue(json.has("downfall"), "biome should contain downfall");
        assertTrue(json.has("effects"), "biome should contain effects");
        assertTrue(json.has("attributes"), "biome should contain current-version attributes");
        assertTrue(json.has("spawners"), "biome should contain spawners");
        assertTrue(json.has("features"), "biome should contain features");
    }

    private static void assertGeneratedNoiseSettingsShape(Path path)
            throws IOException {
        JsonObject json = readJson(path).getAsJsonObject();
        assertTrue(json.has("noise"), "noise settings should contain noise");
        assertTrue(json.has("default_block"),
                "noise settings should contain default_block");
        assertTrue(json.has("default_fluid"),
                "noise settings should contain default_fluid");
        assertTrue(json.has("noise_router"),
                "noise settings should contain noise_router");
        assertTrue(json.has("surface_rule"),
                "noise settings should contain surface_rule");
        assertTrue(json.has("sea_level"),
                "noise settings should contain sea_level");
    }

    private static void assertGradlePipelineIncludesGeneratedResources()
            throws IOException {
        String buildGradle = Files.readString(PROJECT_ROOT.resolve("build.gradle"));
        assertTrue(
                buildGradle.contains("srcDir('src/generated/resources')"),
                "main resources must include src/generated/resources"
        );
        assertTrue(
                buildGradle.contains("verifyGeneratedWorldgenResources"),
                "source generated-resource verification task must exist"
        );
        assertTrue(
                buildGradle.contains("verifyProcessedWorldgenResources"),
                "processed generated-resource verification task must exist"
        );
        assertTrue(
                buildGradle.contains("verifyJarWorldgenResources"),
                "jar generated-resource verification task must exist"
        );
    }

    private static void assertProviderAndReloadDirectoriesMatch()
            throws IOException {
        String roomProvider = Files.readString(PROJECT_ROOT.resolve(
                "src/main/java/io/github/naimjeg/obeliskdepths/data/"
                        + "DungeonRoomDefinitionProvider.java"
        ));
        String corridorProvider = Files.readString(PROJECT_ROOT.resolve(
                "src/main/java/io/github/naimjeg/obeliskdepths/data/"
                        + "DungeonCorridorDefinitionProvider.java"
        ));
        String themeProvider = Files.readString(PROJECT_ROOT.resolve(
                "src/main/java/io/github/naimjeg/obeliskdepths/data/"
                        + "DungeonThemeDefinitionProvider.java"
        ));
        String reloadListener = Files.readString(PROJECT_ROOT.resolve(
                "src/main/java/io/github/naimjeg/obeliskdepths/dungeon/"
                        + "content/DungeonContentReloadListener.java"
        ));

        assertContains(roomProvider, "\"dungeon_room\"",
                "room provider output directory");
        assertContains(corridorProvider, "\"dungeon_corridor\"",
                "corridor provider output directory");
        assertContains(themeProvider, "\"dungeon_theme\"",
                "theme provider output directory");
        assertContains(reloadListener, "FileToIdConverter.json(\"dungeon_room\")",
                "room reload directory");
        assertContains(reloadListener,
                "FileToIdConverter.json(\"dungeon_corridor\")",
                "corridor reload directory");
        assertContains(reloadListener,
                "FileToIdConverter.json(\"dungeon_theme\")",
                "theme reload directory");
        assertContains(reloadListener,
                "FileToIdConverter.json(\"dungeon_layout\")",
                "layout reload directory");
    }

    private static JsonElement readJson(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path));
    }

    private static void assertFile(Path path, String label) {
        assertTrue(Files.isRegularFile(path), label + " should exist: " + path);
    }

    private static void assertContains(
            String haystack,
            String needle,
            String label
    ) {
        assertTrue(haystack.contains(needle),
                label + " should contain " + needle);
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(
                    label + " expected " + expected + " but was " + actual
            );
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
