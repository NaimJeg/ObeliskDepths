package io.github.naimjeg.obeliskdepths.worldgen.structure;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomRotation;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSitePlacement;
import io.github.naimjeg.obeliskdepths.dungeon.site.PrototypeDungeonSitePlanner;
import io.github.naimjeg.obeliskdepths.worldgen.structure.generation.DungeonGenerationPlan;
import io.github.naimjeg.obeliskdepths.worldgen.structure.generation.PlacedDungeonRoom;
import io.github.naimjeg.obeliskdepths.worldgen.structure.generation.RoutedDungeonConnection;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonGraphEdgeKind;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonCellPos;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonPortReference;
import io.github.naimjeg.obeliskdepths.worldgen.structure.tree.GreatSwampTreeTerrainPlacement;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class DungeonTerrainIntegrationTest {
    private static final Path MAIN_RESOURCES = Path.of("src/main/resources");

    private DungeonTerrainIntegrationTest() {
    }

    public static void main(String[] args) throws Exception {
        terrainMedianAndClamp();
        wholePlanTranslationUsesOneOffset();
        previewYIsNotAuthoritativeStructureY();
        horizontalCandidateDistanceIgnoresY();
        foundationSupportPositionsAreBounded();
        datapackTerrainResourcesAreConsistent();
        giantTreePlacementInvariants();
    }

    private static void terrainMedianAndClamp() {
        BoundingBox planned = new BoundingBox(0, 10, 0, 15, 30, 15);
        DungeonTerrainHeightSampler.Result result =
                DungeonTerrainHeightSampler.calculate(
                        samples(60, 63, 70, 61, 65, 67, 64, 62, 66),
                        planned,
                        LevelHeightAccessor.create(-64, 384)
                );

        assertEquals(64, result.medianHeight(), "upper median height");
        assertEquals(65, result.requestedBaseY(), "surface offset");
        assertEquals(65, result.baseY(), "unclamped base y");
        assertEquals(55, result.verticalOffset(), "vertical offset");
        assertEquals(65, result.translatedBounds().minY(), "translated min y");
        assertEquals(85, result.translatedBounds().maxY(), "translated max y");

        DungeonTerrainHeightSampler.Result clamped =
                DungeonTerrainHeightSampler.calculate(
                        samples(95, 96, 97, 98, 99, 100, 101, 102, 103),
                        planned,
                        LevelHeightAccessor.create(0, 96)
                );
        assertEquals(75, clamped.baseY(), "base y clamps to fit bounds");
        assertEquals(95, clamped.translatedBounds().maxY(), "clamped max build y");
    }

    private static void wholePlanTranslationUsesOneOffset() {
        DungeonGenerationPlan plan = samplePlan();
        DungeonGenerationPlan translated = plan.translatedY(37);

        assertEquals(plan.origin().offset(0, 37, 0), translated.origin(),
                "origin translated");
        assertEquals(plan.siteBounds().minY() + 37,
                translated.siteBounds().minY(), "site min y translated");
        assertEquals(plan.siteBounds().maxY() + 37,
                translated.siteBounds().maxY(), "site max y translated");
        assertEquals(plan.connections().getFirst().cells(),
                translated.connections().getFirst().cells(),
                "routing cells remain coarse cell-space data");

        for (int index = 0; index < plan.rooms().size(); index++) {
            PlacedDungeonRoom before = plan.rooms().get(index);
            PlacedDungeonRoom after = translated.rooms().get(index);
            assertEquals(before.cellOrigin(), after.cellOrigin(),
                    "cell origin unchanged: " + before.id());
            assertEquals(before.anchor().getY() - plan.origin().getY(),
                    after.anchor().getY() - translated.origin().getY(),
                    "anchor offset preserved: " + before.id());
            assertEquals(before.templateOrigin().offset(0, 37, 0),
                    after.templateOrigin(), "template origin translated: " + before.id());
            assertEquals(before.bounds().minY() + 37, after.bounds().minY(),
                    "room min y translated: " + before.id());
        }
    }

    private static void previewYIsNotAuthoritativeStructureY() {
        assertEquals(10, DungeonSitePlacement.PREVIEW_Y, "preview y value");
    }

    private static void horizontalCandidateDistanceIgnoresY() {
        long distance = PrototypeDungeonSitePlanner.horizontalDistanceSqr(
                new BlockPos(0, -64, 0),
                new BlockPos(3, 320, 4)
        );
        assertEquals(25L, distance, "horizontal distance ignores y");
    }

    private static void foundationSupportPositionsAreBounded() {
        BoundingBox bounds = new BoundingBox(0, 20, 0, 16, 20, 16);
        List<BlockPos> supports = DungeonFoundationPlacer.supportPositions(bounds);

        assertTrue(!supports.isEmpty(), "support positions exist");
        assertTrue(supports.size() < 17 * 17,
                "support positions are not a full floor scan");
        assertTrue(supports.contains(new BlockPos(0, 20, 0)),
                "includes north-west corner");
        assertTrue(supports.contains(new BlockPos(16, 20, 16)),
                "includes south-east corner");
        assertTrue(supports.contains(new BlockPos(8, 20, 8)),
                "includes center support");
        for (BlockPos support : supports) {
            assertEquals(20, support.getY(), "support y");
            assertTrue(bounds.isInside(support), "support remains inside floor bounds");
        }
    }

    private static void datapackTerrainResourcesAreConsistent()
            throws IOException {
        JsonObject dimension = readJson("data/obeliskdepths/dimension/obelisk_depths.json");
        JsonObject generator = dimension.getAsJsonObject("generator");
        assertEquals("minecraft:noise", generator.get("type").getAsString(),
                "dimension uses noise generator");
        assertEquals("minecraft:fixed",
                generator.getAsJsonObject("biome_source").get("type").getAsString(),
                "dimension uses fixed biome source");
        assertEquals("obeliskdepths:great_swamp",
                generator.getAsJsonObject("biome_source").get("biome").getAsString(),
                "dimension biome");
        assertEquals("minecraft:overworld", generator.get("settings").getAsString(),
                "dimension noise settings");

        JsonObject dimensionType = readJson(
                "data/obeliskdepths/dimension_type/obelisk_depths.json"
        );
        assertEquals(-64, dimensionType.get("min_y").getAsInt(),
                "dimension type min y");
        assertEquals(384, dimensionType.get("height").getAsInt(),
                "dimension type height");
        assertEquals(384, dimensionType.get("logical_height").getAsInt(),
                "dimension type logical height");

        JsonObject biome = readJson("data/obeliskdepths/worldgen/biome/great_swamp.json");
        assertTrue(biome.get("has_precipitation").getAsBoolean(),
                "great swamp precipitates");
        assertTrue(containsFeature(biome, "minecraft:cave"),
                "great swamp has cave carver");
        assertTrue(containsFeature(biome, "minecraft:ore_diamond"),
                "great swamp has underground ores");
        assertEquals(1, countFeature(biome, "minecraft:trees_swamp"),
                "vanilla swamp tree feature appears once");
        assertEquals(1, countFeature(biome, "obeliskdepths:dense_swamp_trees"),
                "dense swamp tree feature appears once");

        JsonObject denseTrees = readJson(
                "data/obeliskdepths/worldgen/placed_feature/dense_swamp_trees.json"
        );
        assertEquals("minecraft:swamp_oak",
                denseTrees.get("feature").getAsString(),
                "dense tree configured feature");
    }

    private static void giantTreePlacementInvariants()
            throws IOException {
        JsonObject structureSet = readJson(
                "data/obeliskdepths/worldgen/structure_set/great_swamp_hourglass_trees.json"
        );
        JsonObject placement = structureSet.getAsJsonObject("placement");
        int spacing = placement.get("spacing").getAsInt();
        int separation = placement.get("separation").getAsInt();
        assertTrue(separation < spacing, "giant tree separation below spacing");
        assertEquals(18, spacing, "giant tree spacing");
        assertEquals(10, separation, "giant tree separation");

        assertEquals(65, GreatSwampTreeTerrainPlacement.terrainRelativeMinY(
                        64,
                        -64,
                        319
                ),
                "tree min y follows terrain surface");
        assertEquals(195, GreatSwampTreeTerrainPlacement.terrainRelativeMinY(
                        300,
                        -64,
                        319
                ),
                "tree min y clamps below build top");
    }

    private static DungeonGenerationPlan samplePlan() {
        Identifier definition = Identifier.fromNamespaceAndPath(
                "obeliskdepths",
                "test_room"
        );
        Identifier template = Identifier.fromNamespaceAndPath(
                "obeliskdepths",
                "test_template"
        );
        PlacedDungeonRoom start = new PlacedDungeonRoom(
                "start",
                DungeonRoomType.START,
                definition,
                template,
                new DungeonCellPos(0, 0, 0),
                new BlockPos(100, 10, 100),
                new BlockPos(104, 11, 104),
                DungeonRoomRotation.NONE,
                false,
                new BoundingBox(100, 10, 100, 107, 18, 107),
                List.of(),
                true
        );
        PlacedDungeonRoom boss = new PlacedDungeonRoom(
                "boss",
                DungeonRoomType.BOSS,
                definition,
                template,
                new DungeonCellPos(2, 0, 0),
                new BlockPos(116, 10, 100),
                new BlockPos(120, 11, 104),
                DungeonRoomRotation.NONE,
                false,
                new BoundingBox(116, 10, 100, 123, 18, 107),
                List.of(),
                false
        );
        RoutedDungeonConnection connection = new RoutedDungeonConnection(
                "corridor_tree_start_boss",
                new DungeonPortReference("start", "east"),
                new DungeonPortReference("boss", "west"),
                DungeonGraphEdgeKind.TREE,
                List.of(
                        new DungeonCellPos(1, 0, 0),
                        new DungeonCellPos(2, 0, 0)
                )
        );
        return new DungeonGenerationPlan(
                new BlockPos(96, 8, 96),
                new BoundingBox(98, 8, 98, 125, 22, 109),
                "start",
                List.of(start, boss),
                List.of(connection)
        );
    }

    private static List<DungeonTerrainHeightSampler.HeightSample> samples(
            int... heights
    ) {
        if (heights.length != 9) {
            throw new IllegalArgumentException("expected nine heights");
        }
        java.util.ArrayList<DungeonTerrainHeightSampler.HeightSample> samples =
                new java.util.ArrayList<>();
        for (int index = 0; index < heights.length; index++) {
            samples.add(new DungeonTerrainHeightSampler.HeightSample(
                    index / 3,
                    index % 3,
                    heights[index]
            ));
        }
        return List.copyOf(samples);
    }

    private static JsonObject readJson(String relativePath)
            throws IOException {
        return JsonParser.parseString(Files.readString(
                MAIN_RESOURCES.resolve(relativePath)
        )).getAsJsonObject();
    }

    private static boolean containsFeature(
            JsonObject biome,
            String id
    ) {
        return countFeature(biome, id) > 0;
    }

    private static int countFeature(
            JsonObject biome,
            String id
    ) {
        int count = 0;
        JsonArray steps = biome.getAsJsonArray("features");
        for (int i = 0; i < steps.size(); i++) {
            JsonArray features = steps.get(i).getAsJsonArray();
            for (int j = 0; j < features.size(); j++) {
                if (id.equals(features.get(j).getAsString())) {
                    count++;
                }
            }
        }
        JsonArray carvers = biome.getAsJsonArray("carvers");
        for (int i = 0; i < carvers.size(); i++) {
            if (id.equals(carvers.get(i).getAsString())) {
                count++;
            }
        }
        return count;
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
