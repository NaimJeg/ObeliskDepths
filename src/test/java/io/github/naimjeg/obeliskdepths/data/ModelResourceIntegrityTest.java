package io.github.naimjeg.obeliskdepths.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.naimjeg.obeliskdepths.ObeliskDepths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class ModelResourceIntegrityTest {
    private static final Path PROJECT_ROOT = Path.of("")
            .toAbsolutePath()
            .normalize();
    private static final Path MAIN_RESOURCES =
            PROJECT_ROOT.resolve("src/main/resources");
    private static final Path GENERATED_RESOURCES =
            PROJECT_ROOT.resolve("src/generated/resources");
    private static final Path[] RESOURCE_ROOTS = {
            MAIN_RESOURCES,
            GENERATED_RESOURCES
    };

    private ModelResourceIntegrityTest() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, Path> projectModels = collectProjectModels();
        validateModelFiles(projectModels);
        validateBlockstateModelReferences(projectModels);
        validateItemDefinitionModelReferences(projectModels);

        assertTextureExists("obeliskdepths:block/great_swamp_root_dirt",
                "root dirt texture follows registered block id");
        assertTextureExists(
                "obeliskdepths:block/amphixylon_root_tangle_side",
                "root tangle all-face texture"
        );
        assertTextureExists("obeliskdepths:item/amphixylon_door",
                "amphixylon door item texture");
    }

    private static Map<String, Path> collectProjectModels() throws IOException {
        Map<String, Path> models = new HashMap<>();

        for (Path root : RESOURCE_ROOTS) {
            Path modelRoot = root
                    .resolve("assets")
                    .resolve(ObeliskDepths.MOD_ID)
                    .resolve("models");
            if (!Files.isDirectory(modelRoot)) {
                continue;
            }

            try (Stream<Path> paths = Files.walk(modelRoot)) {
                paths.filter(path -> path.toString().endsWith(".json"))
                        .forEach(path -> {
                            Path relative = modelRoot.relativize(path);
                            String modelPath = stripJson(relative);
                            models.put(
                                    ObeliskDepths.MOD_ID + ":" + modelPath,
                                    path
                            );
                        });
            }
        }

        return models;
    }

    private static void validateModelFiles(Map<String, Path> projectModels)
            throws IOException {
        for (Map.Entry<String, Path> entry : projectModels.entrySet()) {
            Path modelFile = entry.getValue();
            JsonObject model = readJson(modelFile).getAsJsonObject();
            Set<String> textureSlots = new HashSet<>();

            if (model.has("textures")) {
                JsonObject textures = model.getAsJsonObject("textures");
                for (Map.Entry<String, JsonElement> texture
                        : textures.entrySet()) {
                    textureSlots.add(texture.getKey());
                    if (texture.getValue().isJsonPrimitive()) {
                        validateTextureReference(
                                modelFile,
                                texture.getKey(),
                                texture.getValue().getAsString()
                        );
                    }
                }
            }

            if (model.has("parent")) {
                validateModelReference(
                        projectModels,
                        modelFile,
                        model.get("parent").getAsString()
                );
            }

            validateTextureSlotReferences(modelFile, model, textureSlots);
        }
    }

    private static void validateBlockstateModelReferences(
            Map<String, Path> projectModels
    ) throws IOException {
        for (Path root : RESOURCE_ROOTS) {
            Path blockstateRoot = root
                    .resolve("assets")
                    .resolve(ObeliskDepths.MOD_ID)
                    .resolve("blockstates");
            if (!Files.isDirectory(blockstateRoot)) {
                continue;
            }

            try (Stream<Path> paths = Files.walk(blockstateRoot)) {
                for (Path path : paths
                        .filter(file -> file.toString().endsWith(".json"))
                        .toList()) {
                    collectModelProperties(
                            projectModels,
                            path,
                            readJson(path)
                    );
                }
            }
        }
    }

    private static void validateItemDefinitionModelReferences(
            Map<String, Path> projectModels
    ) throws IOException {
        for (Path root : RESOURCE_ROOTS) {
            Path itemRoot = root
                    .resolve("assets")
                    .resolve(ObeliskDepths.MOD_ID)
                    .resolve("items");
            if (!Files.isDirectory(itemRoot)) {
                continue;
            }

            try (Stream<Path> paths = Files.walk(itemRoot)) {
                for (Path path : paths
                        .filter(file -> file.toString().endsWith(".json"))
                        .toList()) {
                    collectModelProperties(
                            projectModels,
                            path,
                            readJson(path)
                    );
                }
            }
        }
    }

    private static void collectModelProperties(
            Map<String, Path> projectModels,
            Path sourceFile,
            JsonElement element
    ) {
        if (element == null || element.isJsonNull()) {
            return;
        }

        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectModelProperties(projectModels, sourceFile, child);
            }
            return;
        }

        if (!element.isJsonObject()) {
            return;
        }

        JsonObject object = element.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if ("model".equals(entry.getKey())
                    && entry.getValue().isJsonPrimitive()) {
                validateModelReference(
                        projectModels,
                        sourceFile,
                        entry.getValue().getAsString()
                );
            }
            collectModelProperties(projectModels, sourceFile, entry.getValue());
        }
    }

    private static void validateTextureSlotReferences(
            Path modelFile,
            JsonElement element,
            Set<String> textureSlots
    ) {
        if (element == null || element.isJsonNull()) {
            return;
        }

        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                validateTextureSlotReferences(modelFile, child, textureSlots);
            }
            return;
        }

        if (!element.isJsonObject()) {
            return;
        }

        JsonObject object = element.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if ("texture".equals(entry.getKey())
                    && entry.getValue().isJsonPrimitive()) {
                String reference = entry.getValue().getAsString();
                if (reference.startsWith("#")) {
                    String slot = reference.substring(1);
                    assertTrue(textureSlots.contains(slot),
                            "Undefined texture slot "
                                    + reference
                                    + " in "
                                    + modelFile);
                }
            }
            validateTextureSlotReferences(
                    modelFile,
                    entry.getValue(),
                    textureSlots
            );
        }
    }

    private static void validateTextureReference(
            Path modelFile,
            String slot,
            String reference
    ) {
        if (reference.startsWith("#")) {
            return;
        }

        if (!reference.contains(":")) {
            throw new AssertionError(
                    "Texture reference must be explicitly namespaced in "
                            + modelFile
                            + " slot "
                            + slot
                            + ": "
                            + reference
            );
        }

        String[] parts = reference.split(":", 2);
        if (!ObeliskDepths.MOD_ID.equals(parts[0])) {
            return;
        }

        assertTextureExists(reference,
                "texture slot " + slot + " in " + modelFile);
    }

    private static void validateModelReference(
            Map<String, Path> projectModels,
            Path sourceFile,
            String reference
    ) {
        if (!reference.contains(":")) {
            return;
        }

        String[] parts = reference.split(":", 2);
        if (!ObeliskDepths.MOD_ID.equals(parts[0])) {
            return;
        }

        assertTrue(projectModels.containsKey(reference),
                "Missing project model parent/reference "
                        + reference
                        + " in "
                        + sourceFile);
    }

    private static void assertTextureExists(String reference, String label) {
        String[] parts = reference.split(":", 2);
        Path relativeTexture = Path.of("assets")
                .resolve(parts[0])
                .resolve("textures")
                .resolve(parts[1] + ".png");

        for (Path root : RESOURCE_ROOTS) {
            if (Files.isRegularFile(root.resolve(relativeTexture))) {
                return;
            }
        }

        throw new AssertionError(
                "Missing " + label + ": " + reference
                        + " expected at "
                        + relativeTexture
        );
    }

    private static JsonElement readJson(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path));
    }

    private static String stripJson(Path relative) {
        String path = relative.toString().replace('\\', '/');
        return path.substring(0, path.length() - ".json".length());
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
