package io.github.naimjeg.obeliskdepths.architecture;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public final class WoodTypeRegistrationArchitectureTest {
    private static final Path PROJECT_ROOT = Path.of("")
            .toAbsolutePath()
            .normalize();
    private static final Path MAIN_JAVA = PROJECT_ROOT.resolve("src/main/java");
    private static final Path MAIN_RESOURCES =
            PROJECT_ROOT.resolve("src/main/resources");
    private static final Path TEMPLATES =
            PROJECT_ROOT.resolve("src/main/templates");
    private static final Path MOD_WOOD_TYPES = MAIN_JAVA.resolve(
            "io/github/naimjeg/obeliskdepths/registry/ModWoodTypes.java"
    );
    private static final Path MOD_BLOCKS = MAIN_JAVA.resolve(
            "io/github/naimjeg/obeliskdepths/registry/ModBlocks.java"
    );
    private static final Path CLIENT_HANDLER = MAIN_JAVA.resolve(
            "io/github/naimjeg/obeliskdepths/client/"
                    + "ObeliskDepthsClientHandler.java"
    );
    private static final Path WOOD_TYPE_MIXIN = MAIN_JAVA.resolve(
            "io/github/naimjeg/obeliskdepths/mixin/WoodTypeMixin.java"
    );
    private static final Path MIXIN_JSON =
            MAIN_RESOURCES.resolve("obeliskdepths.mixins.json");
    private static final Path MODS_TOML = TEMPLATES.resolve(
            "META-INF/neoforge.mods.toml"
    );

    private WoodTypeRegistrationArchitectureTest() {
    }

    public static void main(String[] args) throws IOException {
        assertMixinJsonIsCommonAndRequired();
        assertNeoForgeMetadataReferencesMixinJson();
        assertWoodTypeClinitMixinShape();
        assertModWoodTypesHasOnlyExplicitBackendRegistration();
        assertModBlocksUsesLazyWoodTypeSuppliers();
        assertClientSheetsOnlyConsumesAccessor();
        assertNoOldPublicWoodTypeFieldAccessRemains();
        assertProductionRegistrationCallsAreIsolated();
    }

    private static void assertMixinJsonIsCommonAndRequired()
            throws IOException {
        assertFile(MIXIN_JSON, "common mixin json");

        JsonObject json = JsonParser.parseString(Files.readString(MIXIN_JSON))
                .getAsJsonObject();
        assertTrue(json.get("required").getAsBoolean(),
                "mixin json must be required");
        assertEquals("0.8", json.get("minVersion").getAsString(),
                "mixin minVersion");
        assertEquals(
                "io.github.naimjeg.obeliskdepths.mixin",
                json.get("package").getAsString(),
                "mixin package"
        );
        assertEquals(
                "JAVA_25",
                json.get("compatibilityLevel").getAsString(),
                "mixin compatibilityLevel should follow the Java toolchain"
        );
        assertJsonArrayContains(
                json.getAsJsonArray("mixins"),
                "WoodTypeMixin",
                "WoodTypeMixin must be in the common mixins list"
        );
        assertFalse(
                json.has("client"),
                "WoodTypeMixin must not be client-only"
        );
        assertEquals(
                1,
                json.getAsJsonObject("injectors")
                        .get("defaultRequire")
                        .getAsInt(),
                "mixin defaultRequire"
        );
    }

    private static void assertNeoForgeMetadataReferencesMixinJson()
            throws IOException {
        String metadata = Files.readString(MODS_TOML);

        assertContains(metadata, "[[mixins]]",
                "NeoForge metadata should declare a mixins block");
        assertContains(metadata, "config=\"${mod_id}.mixins.json\"",
                "NeoForge metadata should reference the generated mixin config");
        assertNotContains(metadata, "NetherLink",
                "metadata must not add NetherLink ordering");
        assertNotContains(metadata, "netherlink",
                "metadata must not add NetherLink ordering");
    }

    private static void assertWoodTypeClinitMixinShape() throws IOException {
        String source = Files.readString(WOOD_TYPE_MIXIN);

        assertContains(source, "@Mixin(WoodType.class)",
                "mixin must target current mapped WoodType class");
        assertContains(source, "method = \"<clinit>\"",
                "mixin must inject into class initializer");
        assertContains(source, "at = @At(\"TAIL\")",
                "mixin must inject at TAIL");
        assertContains(source, "require = 1",
                "mixin injection must require exactly one target");
        assertContains(source, "private static void obeliskdepths$registerWoodTypes",
                "mixin handler must be static");
        assertContains(source, "ModWoodTypes.registerFromVanillaClinit();",
                "mixin must call the explicit registration boundary");
        assertNotContains(source, "@Shadow",
                "mixin must not shadow Vanilla containers");
        assertNotContains(source, "@Overwrite",
                "mixin must not overwrite Vanilla <clinit>");
    }

    private static void assertModWoodTypesHasOnlyExplicitBackendRegistration()
            throws IOException {
        String source = Files.readString(MOD_WOOD_TYPES);

        assertNotContains(source, "public static final BlockSetType",
                "ModWoodTypes must not expose eager BlockSetType fields");
        assertNotContains(source, "public static final WoodType",
                "ModWoodTypes must not expose eager WoodType fields");
        assertNotContains(source, "= BlockSetType.register",
                "ModWoodTypes static field initialization must not register block sets");
        assertNotContains(source, "= WoodType.register",
                "ModWoodTypes static field initialization must not register wood types");
        assertContains(source, "private static volatile BlockSetType greatSwampTaxodiumSet;",
                "BlockSetType should be published through a volatile field");
        assertContains(source, "private static volatile WoodType greatSwampTaxodium;",
                "WoodType should be published through a volatile field");
        assertContains(source, "public static synchronized void registerFromVanillaClinit()",
                "registration boundary should be synchronized and explicit");
        assertContains(source, "private static boolean registering;",
                "registration should guard recursive bootstrap");
        assertContains(source, "BlockSetType.register(new BlockSetType(name))",
                "production backend should perform BlockSetType registration");
        assertContains(source, "WoodType.register(new WoodType(name, setType))",
                "production backend should perform WoodType registration");
        assertNotContains(source, "WoodType.OAK",
                "accessors must not force Vanilla WoodType initialization");
        assertNotContains(source, "BlockSetType.OAK",
                "accessors must not force Vanilla BlockSetType initialization");
    }

    private static void assertModBlocksUsesLazyWoodTypeSuppliers()
            throws IOException {
        String source = Files.readString(MOD_BLOCKS)
                .replace("\r\n", "\n");

        assertContains(source, "ModWoodTypes::greatSwampTaxodium",
                "wood set should receive a WoodType supplier");
        assertContains(source, "ModWoodTypes::greatSwampTaxodiumSet",
                "wood set should receive a BlockSetType supplier");
        assertContains(source, "Supplier<WoodType> woodType",
                "registerWoodSet should accept a WoodType supplier");
        assertContains(source, "Supplier<BlockSetType> blockSetType",
                "registerWoodSet should accept a BlockSetType supplier");
        assertContains(source, "new FenceGateBlock(\n                        woodType.get(),",
                "fence gate factory should read WoodType lazily");
        assertContains(source, "new DoorBlock(\n                        blockSetType.get(),",
                "door factory should read BlockSetType lazily");
        assertContains(source, "new TrapDoorBlock(\n                        blockSetType.get(),",
                "trapdoor factory should read BlockSetType lazily");
        assertNotContains(source, "ModWoodTypes.GREAT_SWAMP_TAXODIUM",
                "ModBlocks must not read the old eager WoodType field");
        assertNotContains(source, "ModWoodTypes.GREAT_SWAMP_TAXODIUM_SET",
                "ModBlocks must not read the old eager BlockSetType field");
        assertNotContains(source, "WoodType.OAK",
                "ModBlocks must not fallback to Oak wood type");
        assertNotContains(source, "BlockSetType.OAK",
                "ModBlocks must not fallback to Oak block set type");
    }

    private static void assertClientSheetsOnlyConsumesAccessor()
            throws IOException {
        String source = Files.readString(CLIENT_HANDLER);

        assertContains(source,
                "Sheets.addWoodType(ModWoodTypes.greatSwampTaxodium())",
                "client setup should consume the registered WoodType accessor");
        assertContains(source, "event.enqueueWork",
                "Sheets mutation should remain enqueued on the client thread");
        assertNotContains(source, "registerFromVanillaClinit",
                "client setup must not compensate by registering wood types");
        assertNotContains(source, "ModWoodTypes.GREAT_SWAMP_TAXODIUM",
                "client setup must not use the removed eager field");
    }

    private static void assertNoOldPublicWoodTypeFieldAccessRemains()
            throws IOException {
        assertNoMainSourceContains(
                "ModWoodTypes.GREAT_SWAMP_TAXODIUM",
                "production code must not read the old eager WoodType field"
        );
        assertNoMainSourceContains(
                "ModWoodTypes.GREAT_SWAMP_TAXODIUM_SET",
                "production code must not read the old eager BlockSetType field"
        );
    }

    private static void assertProductionRegistrationCallsAreIsolated()
            throws IOException {
        assertOnlyMainSourceContains(
                "WoodType.register(",
                MOD_WOOD_TYPES,
                "WoodType registration must be isolated to ModWoodTypes"
        );
        assertOnlyMainSourceContains(
                "BlockSetType.register(",
                MOD_WOOD_TYPES,
                "BlockSetType registration must be isolated to ModWoodTypes"
        );
        assertOnlyMainSourceContains(
                "new WoodType(",
                MOD_WOOD_TYPES,
                "WoodType construction must be isolated to ModWoodTypes"
        );
        assertOnlyMainSourceContains(
                "new BlockSetType(",
                MOD_WOOD_TYPES,
                "BlockSetType construction must be isolated to ModWoodTypes"
        );
    }

    private static void assertNoMainSourceContains(
            String token,
            String message
    ) throws IOException {
        List<Path> violations = sourcesContaining(token);
        if (!violations.isEmpty()) {
            throw new AssertionError(
                    message + ": " + normalizeList(violations)
            );
        }
    }

    private static void assertOnlyMainSourceContains(
            String token,
            Path expected,
            String message
    ) throws IOException {
        List<Path> matches = sourcesContaining(token);
        if (!matches.equals(List.of(expected))) {
            throw new AssertionError(
                    message + " expected only " + expected
                            + " but matched " + normalizeList(matches)
            );
        }
    }

    private static List<Path> sourcesContaining(String token)
            throws IOException {
        try (Stream<Path> paths = Files.walk(MAIN_JAVA)) {
            return paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> contains(path, token))
                    .toList();
        }
    }

    private static boolean contains(Path path, String token) {
        try {
            return Files.readString(path).contains(token);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void assertJsonArrayContains(
            JsonArray array,
            String value,
            String label
    ) {
        for (int i = 0; i < array.size(); i++) {
            if (value.equals(array.get(i).getAsString())) {
                return;
            }
        }
        throw new AssertionError(label + ": missing " + value);
    }

    private static void assertFile(Path path, String label) {
        assertTrue(Files.isRegularFile(path),
                "Missing " + label + ": " + path);
    }

    private static void assertContains(
            String haystack,
            String needle,
            String label
    ) {
        assertTrue(haystack.contains(needle),
                label + " should contain " + needle);
    }

    private static void assertNotContains(
            String haystack,
            String needle,
            String label
    ) {
        assertFalse(haystack.contains(needle),
                label + " should not contain " + needle);
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

    private static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }

    private static String normalizeList(List<Path> paths) {
        return paths.stream()
                .map(Path::toString)
                .map(path -> path.replace('\\', '/'))
                .toList()
                .toString();
    }
}
