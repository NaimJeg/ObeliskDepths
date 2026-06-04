package io.github.naimjeg.obeliskdepths.equipment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class UniqueEquipmentAssetContractTest {
    private static final Path GENERATED = Path.of("src/generated/resources");
    private static final Path MAIN = Path.of("src/main/resources");
    private static final Set<String> MANUAL_DISPLAY_CONTEXT_MODELS = Set.of(
            "grandfather"
    );

    @Test
    void generatedUniqueDefinitionsHaveGeneratedClientItemAndModelAssets()
            throws IOException {
        for (ObeliskUniqueEquipmentDefinition definition
                : ObeliskUniqueEquipmentCatalog.all()) {
            String path = definition.assetId().getPath();
            if (MANUAL_DISPLAY_CONTEXT_MODELS.contains(path)) {
                continue;
            }
            JsonObject clientItem = json(GENERATED.resolve(
                    "assets/obeliskdepths/items/" + path + ".json"
            ));
            JsonObject model = json(GENERATED.resolve(
                    "assets/obeliskdepths/models/item/" + path + ".json"
            ));

            JsonObject clientModel = clientItem.getAsJsonObject("model");
            assertEquals("minecraft:model", clientModel.get("type").getAsString());
            assertEquals(
                    "obeliskdepths:item/" + path,
                    clientModel.get("model").getAsString()
            );
            assertEquals(
                    definition.slot() == ObeliskEquipmentSlot.WEAPON
                            ? "minecraft:item/handheld"
                            : "minecraft:item/generated",
                    model.get("parent").getAsString()
            );
            assertEquals(
                    "obeliskdepths:item/" + path,
                    model.getAsJsonObject("textures")
                            .get("layer0")
                            .getAsString()
            );
        }
    }

    @Test
    void grandfatherUsesManualDisplayContextModel() throws IOException {
        ObeliskUniqueEquipmentDefinition grandfather =
                ObeliskUniqueEquipmentCatalog.all().stream()
                        .filter(definition -> definition.templateId()
                                .equals(ObeliskEquipmentIds.GRANDFATHER))
                        .findFirst()
                        .orElseThrow();
        assertEquals("grandfather", grandfather.assetId().getPath());
        assertTrue(MANUAL_DISPLAY_CONTEXT_MODELS.contains(
                grandfather.assetId().getPath()
        ));

        Path assets = MAIN.resolve("assets/obeliskdepths");
        JsonObject clientModel = json(
                assets.resolve("items/grandfather.json")
        ).getAsJsonObject("model");
        assertEquals("minecraft:select",
                clientModel.get("type").getAsString());
        assertEquals("minecraft:display_context",
                clientModel.get("property").getAsString());
        assertEquals(1, clientModel.getAsJsonArray("cases").size());

        JsonObject guiCase = clientModel.getAsJsonArray("cases")
                .get(0)
                .getAsJsonObject();
        assertEquals("gui", guiCase.get("when").getAsString());
        assertModelReference(
                guiCase.getAsJsonObject("model"),
                "obeliskdepths:item/grandfather_gui"
        );
        assertModelReference(
                clientModel.getAsJsonObject("fallback"),
                "obeliskdepths:item/grandfather"
        );

        assertItemModel(
                assets.resolve("models/item/grandfather.json"),
                "minecraft:item/handheld",
                "obeliskdepths:item/grandfather"
        );
        assertItemModel(
                assets.resolve("models/item/grandfather_gui.json"),
                "minecraft:item/generated",
                "obeliskdepths:item/grandfather_gui"
        );
        assertTrue(Files.isRegularFile(
                assets.resolve("textures/item/grandfather.png")
        ), "missing manual Grandfather world/hand texture");
        assertTrue(Files.isRegularFile(
                assets.resolve("textures/item/grandfather_gui.png")
        ), "missing manual Grandfather GUI texture");
    }

    @Test
    void stackFactorySetsTemplateIdAsItemModel() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/io/github/naimjeg/obeliskdepths/equipment/"
                        + "ObeliskUniqueEquipmentStacks.java"
        ));
        assertTrue(source.contains("DataComponents.ITEM_MODEL"),
                "unique stack factory must set ITEM_MODEL");
        assertTrue(source.contains("definition.assetId()"),
                "unique stack factory must use the derived client asset id");
        assertFalse(source.contains("DataComponents.EQUIPPABLE"),
                "stack construction must not patch registered equipment routing");
    }

    @Test
    void armorEquipmentAssetsContainOnlyTheirSlotLayer() throws IOException {
        for (ObeliskUniqueEquipmentDefinition definition
                : ObeliskUniqueEquipmentCatalog.all()) {
            Path asset = GENERATED.resolve("assets/obeliskdepths/equipment/"
                    + definition.assetId().getPath() + ".json");
            if (definition.slot() == ObeliskEquipmentSlot.WEAPON) {
                assertFalse(Files.exists(asset));
                continue;
            }
            JsonObject layers = json(asset).getAsJsonObject("layers");
            String expected = definition.slot() == ObeliskEquipmentSlot.ARMOR_LEGS
                    ? "humanoid_leggings" : "humanoid";
            assertEquals(1, layers.size());
            assertTrue(layers.has(expected));
            assertEquals("obeliskdepths:" + definition.assetId().getPath(),
                    layers.getAsJsonArray(expected).get(0).getAsJsonObject()
                            .get("texture").getAsString());
        }
    }

    private static JsonObject json(Path path) throws IOException {
        assertTrue(Files.isRegularFile(path), "missing asset: " + path);
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static void assertModelReference(
            JsonObject model,
            String expectedModel
    ) {
        assertEquals("minecraft:model", model.get("type").getAsString());
        assertEquals(expectedModel, model.get("model").getAsString());
    }

    private static void assertItemModel(
            Path path,
            String expectedParent,
            String expectedTexture
    ) throws IOException {
        JsonObject model = json(path);
        assertEquals(expectedParent, model.get("parent").getAsString());
        assertEquals(expectedTexture,
                model.getAsJsonObject("textures").get("layer0").getAsString());
    }
}
