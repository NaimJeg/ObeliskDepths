package io.github.naimjeg.obeliskdepths.equipment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.naimjeg.damagenexus.api.display.DisplayText;
import io.github.naimjeg.obeliskdepths.tempering.ObeliskTemperingDirectionRegistry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

final class ObeliskEquipmentResourceContractTest {
    private static final Path GENERATED = Path.of("src/generated/resources");
    private static final Path EN_US = GENERATED.resolve(
            "assets/obeliskdepths/lang/en_us.json");
    private static final Path ZH_CN = GENERATED.resolve(
            "assets/obeliskdepths/lang/zh_cn.json");

    @Test
    void languagesHaveCompleteMatchingEquipmentKeys() throws IOException {
        JsonObject english = json(EN_US);
        JsonObject chinese = json(ZH_CN);
        assertEquals(english.keySet(), chinese.keySet());

        for (ObeliskEquipmentContent content
                : ObeliskEquipmentTemplateCatalog.all()) {
            var display = content.definition().display();
            assertDisplayKey(
                    english,
                    chinese,
                    translatable(display.name().orElseThrow()).key()
            );
            assertDisplayKey(
                    english,
                    chinese,
                    translatable(display.flavorText().orElseThrow()).key()
            );
            for (var summary : display.authoredSummary()) {
                DisplayText.Translatable translated = translatable(summary);
                String key = translated.key();
                assertDisplayKey(english, chinese, key);
                assertEquals(
                        translated.fallback().orElseThrow(),
                        english.get(key).getAsString(),
                        key
                );
            }
        }

        ObeliskTemperingDirectionRegistry.bootstrapBuiltIns();
        for (var direction
                : ObeliskTemperingDirectionRegistry.orderedDirectionIds()) {
            String path = direction.getPath();
            assertDisplayKey(english, chinese,
                    "tempering_direction.obeliskdepths." + path);
            assertDisplayKey(english, chinese,
                    "tempering_direction.obeliskdepths." + path
                            + ".description");
        }
        assertFalse(english.has("tempering_direction.obeliskdepths.guard"));
        assertFalse(english.has("tempering_direction.obeliskdepths.echo"));
    }

    @Test
    void tooltipTextMatchesTheFinalFormulas() throws IOException {
        JsonObject english = json(EN_US);
        JsonObject chinese = json(ZH_CN);

        Map<String, List<String>> expected = new LinkedHashMap<>();
        expected.put("tempered", List.of("+3 physical damage"));
        expected.put("brutal", List.of("+10% global damage"));
        expected.put("razor_edged", List.of("+12% physical damage"));
        expected.put("piercing", List.of("+1.5 physical true damage"));
        expected.put("sundering", List.of("Target armor effectiveness -12%"));
        expected.put("deadly", List.of(
                "+20% physical damage when the final critical result is true"));
        expected.put("critical_edge", List.of(
                "+20% physical damage when the final critical result is true"));
        expected.put("ambushers", List.of(
                "+18% global damage above 80% target health"));
        expected.put("executioners", List.of(
                "+20% physical damage below 35% target health"));
        expected.put("giant_slayers", List.of(
                "+20% global damage against bosses"));
        expected.put("flaming", List.of("+3 fire damage"));
        expected.put("fire_edge", List.of(
                "+4 fire damage", "+15% fire damage"));
        expected.put("flameforged", List.of(
                "Converts 20% physical damage to fire"));
        expected.put("smoldering", List.of(
                "+15% global damage against burning targets"));
        expected.put("frostbound", List.of("+3 cold damage"));
        expected.put("frostforged", List.of(
                "Converts 20% physical damage to cold"));
        expected.put("stormcharged", List.of("+3 lightning damage"));
        expected.put("stormforged", List.of(
                "Converts 18% physical damage to lightning"));
        expected.put("impacting", List.of("+2.5 kinetic damage"));
        expected.put("arcane", List.of("+3 magic damage"));
        expected.put("spellblade", List.of(
                "Gain 15% physical damage as additional magic damage"));
        expected.put("venomous", List.of("+3 poison damage"));
        expected.put("toxic_edge", List.of(
                "Gain 15% physical damage as additional poison damage"));
        expected.put("withering", List.of("+2 wither damage"));
        expected.put("unique.grandfather", List.of(
                "+50% damage when the final critical result is true"));
        expected.put("unique.harlequin_crest", List.of(
                "10% damage reduction"));
        expected.put("unique.tyraels_might", List.of(
                "+10 resistance rating to all supported damage channels",
                "+4 magic damage while above 99% health"));
        expected.put("unique.tibaults_will", List.of(
                "20% more damage while Unstoppable"));
        expected.put("unique.blood_moon_breeches", List.of(
                "20% more damage against Cursed targets"));
        expected.put("unique.cowl_of_the_nameless", List.of(
                "15% more damage against Crowd-Controlled targets"));

        assertEquals(30, expected.size());
        for (Map.Entry<String, List<String>> entry : expected.entrySet()) {
            for (int index = 0; index < entry.getValue().size(); index++) {
                String key = "entry.obeliskdepths." + entry.getKey()
                        + ".tooltip." + index;
                assertEquals(entry.getValue().get(index),
                        english.get(key).getAsString(), key);
            }
        }

        assertEquals("最终暴击判定为真时，+20% 物理伤害",
                text(chinese, "entry.obeliskdepths.deadly.tooltip.0"));
        assertEquals("目标护甲效果 -12%",
                text(chinese, "entry.obeliskdepths.sundering.tooltip.0"));
        assertEquals("获得物理伤害 15% 的额外魔法伤害",
                text(chinese, "entry.obeliskdepths.spellblade.tooltip.0"));
        assertEquals("将 20% 物理伤害转化为火焰伤害",
                text(chinese, "entry.obeliskdepths.flameforged.tooltip.0"));

        for (ObeliskUniqueEquipmentDefinition unique
                : ObeliskUniqueEquipmentCatalog.all()) {
            assertDisplayKey(
                    english,
                    chinese,
                    unique.displayNameTranslationKey()
            );
        }
        assertEquals("祖父",
                text(chinese, "item.obeliskdepths.unique.grandfather"));
        assertEquals("受到的伤害降低 10%",
                text(chinese,
                        "entry.obeliskdepths.unique.harlequin_crest.tooltip.0"));
    }

    @Test
    void generatedRecipesAndTagsUseOnlyTheFinalIds() throws IOException {
        Path recipes = GENERATED.resolve(
                "data/obeliskdepths/recipe/tempering");
        Set<String> expectedRecipes = Set.of(
                "balance_tier_1.json", "edge_tier_1.json",
                "flame_tier_1.json", "frost_tier_1.json",
                "storm_tier_1.json", "arcane_tier_1.json",
                "venom_tier_1.json", "precision_tier_1.json",
                "hunt_tier_1.json"
        );
        try (var files = Files.list(recipes)) {
            assertEquals(expectedRecipes, files
                    .map(path -> path.getFileName().toString())
                    .collect(java.util.stream.Collectors.toSet()));
        }
        for (String recipe : expectedRecipes) {
            JsonObject json = json(recipes.resolve(recipe));
            assertTrue(json.get("replace_existing").getAsBoolean(), recipe);
            assertEquals("#obeliskdepths:equipment/all",
                    json.get("weapon").getAsString(), recipe);
        }

        Path tags = GENERATED.resolve(
                "data/obeliskdepths/tags/item/equipment");
        assertTrue(Files.isRegularFile(tags.resolve("weapons.json")));
        assertTrue(Files.isRegularFile(tags.resolve("armor_head.json")));
        assertTrue(Files.isRegularFile(tags.resolve("armor_chest.json")));
        assertTrue(Files.isRegularFile(tags.resolve("armor_legs.json")));
        assertTrue(Files.isRegularFile(tags.resolve("armor_feet.json")));
        assertTrue(Files.isRegularFile(tags.resolve("all.json")));
        assertFalse(Files.exists(GENERATED.resolve(
                "data/obeliskdepths/tags/item/temperable_weapons.json")));

        Path effectTags = GENERATED.resolve(
                "data/obeliskdepths/tags/mob_effect/equipment");
        assertTagValues(
                effectTags.resolve("unstoppable.json"),
                Set.of("minecraft:resistance")
        );
        assertTagValues(
                effectTags.resolve("curses.json"),
                Set.of(
                        "minecraft:weakness",
                        "minecraft:wither",
                        "minecraft:bad_omen"
                )
        );
        assertTagValues(
                effectTags.resolve("crowd_controlled.json"),
                Set.of(
                        "minecraft:slowness",
                        "minecraft:blindness",
                        "minecraft:darkness",
                        "minecraft:levitation",
                        "minecraft:weakness"
                )
        );
    }

    private static void assertTagValues(Path path, Set<String> expected)
            throws IOException {
        assertTrue(Files.isRegularFile(path), "missing tag " + path);
        JsonObject tag = json(path);
        Set<String> values = new java.util.LinkedHashSet<>();
        tag.getAsJsonArray("values").forEach(value ->
                values.add(value.getAsString()));
        assertEquals(expected, values, path.toString());
    }

    private static void assertDisplayKey(
            JsonObject english,
            JsonObject chinese,
            String key
    ) {
        assertTrue(english.has(key), "missing en_us key " + key);
        assertTrue(chinese.has(key), "missing zh_cn key " + key);
        assertFalse(english.get(key).getAsString().isBlank(), key);
        assertFalse(chinese.get(key).getAsString().isBlank(), key);
    }

    private static String text(JsonObject object, String key) {
        return object.get(key).getAsString();
    }

    private static DisplayText.Translatable translatable(DisplayText text) {
        return assertInstanceOf(DisplayText.Translatable.class, text);
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
