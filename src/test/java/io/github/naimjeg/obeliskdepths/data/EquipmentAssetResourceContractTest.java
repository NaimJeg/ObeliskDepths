package io.github.naimjeg.obeliskdepths.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EquipmentAssetResourceContractTest {
    private static final Path PROJECT_ROOT = Path.of("")
            .toAbsolutePath()
            .normalize();

    @Test
    void exileEquipmentAssetUsesFinalCanonicalTextures() throws IOException {
        Path file = PROJECT_ROOT.resolve(
                "src/generated/resources/assets/obeliskdepths/equipment/exile.json"
        );
        assertTrue(Files.isRegularFile(file), "generated exile equipment json exists");

        JsonObject json = JsonParser.parseString(Files.readString(file))
                .getAsJsonObject();
        JsonObject layers = json.getAsJsonObject("layers");

        assertEquals(
                "obeliskdepths:exile/outer",
                layers.getAsJsonArray("humanoid")
                        .get(0)
                        .getAsJsonObject()
                        .get("texture")
                        .getAsString()
        );
        assertEquals(
                "obeliskdepths:exile/inner",
                layers.getAsJsonArray("humanoid_leggings")
                        .get(0)
                        .getAsJsonObject()
                        .get("texture")
                        .getAsString()
        );
    }

    @Test
    void exileArmorDoesNotUseVanillaIronEquipmentAsset() throws IOException {
        String armorMaterial = Files.readString(PROJECT_ROOT.resolve(
                "src/main/java/io/github/naimjeg/obeliskdepths/registry/"
                        + "ModArmorMaterials.java"
        ));

        assertTrue(armorMaterial.contains("ModEquipmentAssets.EXILE"),
                "Exile armor must use mod equipment asset");
        assertFalse(armorMaterial.contains("EquipmentAssets.IRON"),
                "Exile armor must not use iron equipment asset");
    }
}
