package io.github.naimjeg.obeliskdepths.dungeon.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import io.github.naimjeg.obeliskdepths.dungeon.corridor.BuiltinDungeonCorridorDefinitions;
import io.github.naimjeg.obeliskdepths.dungeon.corridor.BuiltinDungeonCorridors;
import io.github.naimjeg.obeliskdepths.dungeon.corridor.DungeonCorridorDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.corridor.DungeonCorridorDefinitionValidator;
import io.github.naimjeg.obeliskdepths.dungeon.room.BuiltinDungeonRoomDefinitions;
import io.github.naimjeg.obeliskdepths.dungeon.room.BuiltinDungeonRooms;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomDefinitionValidator;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomRotation;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.room.RoomConnectorDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.template.BuiltinDungeonTemplates;
import io.github.naimjeg.obeliskdepths.dungeon.template.DungeonTemplateResourceValidator;
import io.github.naimjeg.obeliskdepths.dungeon.theme.BuiltinDungeonThemeDefinitions;
import io.github.naimjeg.obeliskdepths.dungeon.theme.BuiltinDungeonThemes;
import io.github.naimjeg.obeliskdepths.dungeon.theme.DungeonThemeDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.theme.DungeonThemeDefinitionValidator;
import io.github.naimjeg.obeliskdepths.dungeon.theme.WeightedDungeonCorridor;
import io.github.naimjeg.obeliskdepths.dungeon.theme.WeightedDungeonRoom;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonCellPos;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonConnectorShapeType;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonConnectorSide;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonRoomFootprint;
import io.github.naimjeg.obeliskdepths.worldgen.structure.generation.DungeonGenerationCatalog;
import io.github.naimjeg.obeliskdepths.worldgen.structure.generation.DungeonTemplateGeometryCatalog;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonTemplateGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DungeonContentDefinitionTest {
    private static final Identifier TEST_ROOM =
            Identifier.fromNamespaceAndPath("obeliskdepths", "test/room");
    private static final Identifier TEST_THEME =
            Identifier.fromNamespaceAndPath("obeliskdepths", "test/theme");
    private static final Identifier TEST_TEMPLATE =
            Identifier.fromNamespaceAndPath(
                    "obeliskdepths",
                    "dungeon/great_swamp/room/combat/open_pavilion_01"
            );
    private static final Identifier STANDARD_CONNECTOR =
            BuiltinDungeonRoomDefinitions.BASIC_FLOOR_PASSAGE_CONNECTOR;
    private static final Identifier TEST_THEME_A = testId("theme/a");
    private static final Identifier TEST_THEME_B = testId("theme/b");
    private static final Identifier TEST_CORRIDOR =
            testId("corridor/straight");
    private static final Identifier TEST_CORRIDOR_TEMPLATE =
            testId("template/corridor/straight");

    private DungeonContentDefinitionTest() {
    }

    public static void main(String[] args) throws Exception {
        testCodecsAndFootprints();
        testPortValidation();
        testBuiltInsAndTheme();
        testGenerationCatalogScopesSelectedTheme();
        testGenerationCatalogRejectsSelectedMissingContent();
        testGenerationCatalogCacheClearsOnInstall();
        testTemplateAssets();
        testRegistryAndAtomicInstall();

        DungeonContent.install(DungeonContentSnapshot.empty());
    }

    private static void testCodecsAndFootprints() {
        RoomConnectorDefinition connector = port(
                "north",
                new DungeonCellPos(0, 0, 0),
                new BlockPos(2, 1, 0),
                DungeonConnectorSide.NORTH
        );
        DungeonRoomDefinition room = room(
                DungeonRoomType.COMBAT,
                DungeonRoomFootprint.fromLayers(List.of(List.of("#"))),
                List.of(connector)
        );
        DungeonThemeDefinition theme = disabledTheme(
                Map.of(DungeonRoomType.COMBAT,
                        List.of(new WeightedDungeonRoom(TEST_ROOM, 1))),
                Map.of()
        );

        assertEquals(connector, roundTrip(RoomConnectorDefinition.CODEC, connector),
                "connector codec round trip");
        assertEquals(room, roundTrip(DungeonRoomDefinition.CODEC, room),
                "room codec round trip");
        assertEquals(theme, roundTrip(DungeonThemeDefinition.CODEC, theme),
                "theme codec round trip");

        DungeonRoomFootprint legacy = DungeonRoomFootprint.CODEC
                .parse(JsonOps.INSTANCE, legacyFootprintJson(3, 1, 2))
                .getOrThrow();
        assertEquals(6, legacy.occupiedCells().size(),
                "legacy rectangular decode");

        DungeonRoomFootprint mask = DungeonRoomFootprint.fromLayers(List.of(
                List.of("###", "##.", "#..")
        ));
        assertEquals(6, mask.occupiedCells().size(),
                "irregular mask should reserve exactly six cells");
        assertEquals(mask, roundTrip(DungeonRoomFootprint.CODEC, mask),
                "layers footprint round trip");

        assertThrows(
                () -> DungeonRoomFootprint.fromLayers(List.of(
                        List.of("##", "#")
                )),
                "unequal row widths should be rejected"
        );
        assertThrows(
                () -> DungeonRoomFootprint.fromLayers(List.of(
                        List.of("##"),
                        List.of("##", "##")
                )),
                "unequal layer dimensions should be rejected"
        );
        assertThrows(
                () -> DungeonRoomFootprint.fromLayers(List.of(List.of("#x"))),
                "invalid mask characters should be rejected"
        );
        assertThrows(
                () -> DungeonRoomFootprint.fromLayers(List.of(List.of(".."))),
                "all-empty masks should be rejected"
        );

        DungeonRoomFootprint rotated =
                mask.rotated(DungeonRoomRotation.CLOCKWISE_90);
        assertEquals(mask.depthCells(), rotated.widthCells(),
                "90-degree rotation swaps width");
        assertEquals(mask.widthCells(), rotated.depthCells(),
                "90-degree rotation swaps depth");
        assertEquals(mask.occupiedCells().size(), rotated.occupiedCells().size(),
                "rotation preserves occupied cell count");
        DungeonRoomFootprint fourTurns = mask
                .rotated(DungeonRoomRotation.CLOCKWISE_90)
                .rotated(DungeonRoomRotation.CLOCKWISE_90)
                .rotated(DungeonRoomRotation.CLOCKWISE_90)
                .rotated(DungeonRoomRotation.CLOCKWISE_90);
        assertEquals(mask, fourTurns, "four clockwise rotations restore shape");

        JsonElement encoded = DungeonRoomFootprint.CODEC
                .encodeStart(JsonOps.INSTANCE, mask)
                .getOrThrow();
        assertTrue(encoded.getAsJsonObject().has("layers"),
                "new footprint encoding should use layers");
        assertTrue(!encoded.getAsJsonObject().has("width_cells"),
                "new footprint encoding should not use legacy rectangle fields");
    }

    private static void testPortValidation() {
        DungeonRoomFootprint footprint = DungeonRoomFootprint.fromLayers(List.of(
                List.of("##", "#.")
        ));

        assertContains(
                DungeonRoomDefinitionValidator.validate(
                        TEST_ROOM,
                        room(DungeonRoomType.COMBAT, footprint, List.of(port(
                                "outside",
                                new DungeonCellPos(1, 0, 1),
                                new BlockPos(7, 1, 2),
                                DungeonConnectorSide.EAST
                        )))
                ),
                "boundary cell is not occupied",
                "port boundary cell must be occupied"
        );

        assertContains(
                DungeonRoomDefinitionValidator.validate(
                        TEST_ROOM,
                        room(DungeonRoomType.COMBAT, footprint, List.of(port(
                                "into_cell",
                                new DungeonCellPos(0, 0, 0),
                                new BlockPos(7, 1, 2),
                                DungeonConnectorSide.EAST
                        )))
                ),
                "faces occupied neighbor",
                "port must face unoccupied neighbor"
        );

        assertTrue(
                DungeonRoomDefinitionValidator.validate(
                        TEST_ROOM,
                        room(DungeonRoomType.COMBAT, footprint, List.of(port(
                                "concave",
                                new DungeonCellPos(0, 0, 1),
                                new BlockPos(15, 1, 8),
                                DungeonConnectorSide.EAST
                        )))
                ).isEmpty(),
                "concave exposed boundary should be accepted"
        );

        assertTrue(
                DungeonRoomDefinitionValidator.validate(
                        TEST_ROOM,
                        BuiltinDungeonRoomDefinitions
                                .greatSwampTreasureObeliskSanctum()
                ).isEmpty(),
                "sanctum east opening should validate along Z"
        );

        assertContains(
                DungeonRoomDefinitionValidator.validate(
                        TEST_ROOM,
                        room(DungeonRoomType.COMBAT,
                                DungeonRoomFootprint.fromLayers(List.of(
                                        List.of("#")
                                )),
                                List.of(port(
                                        "up",
                                        new DungeonCellPos(0, 0, 0),
                                        BlockPos.ZERO,
                                        DungeonConnectorSide.UP
                                )))
                ),
                "unsupported vertical facing",
                "vertical ports should report unsupported generation"
        );
    }

    private static void testBuiltInsAndTheme() {
        Map<Identifier, DungeonRoomDefinition> rooms =
                BuiltinDungeonRoomDefinitions.all();
        Map<Identifier, DungeonCorridorDefinition> corridors =
                BuiltinDungeonCorridorDefinitions.all();
        DungeonThemeDefinition greatSwamp =
                BuiltinDungeonThemeDefinitions.greatSwamp();

        assertTrue(rooms.containsKey(
                        BuiltinDungeonRooms.GREAT_SWAMP_START_OPEN_PAVILION),
                "start open pavilion semantic ID should exist");
        assertTrue(rooms.containsKey(
                        BuiltinDungeonRooms.GREAT_SWAMP_COMBAT_OPEN_PAVILION),
                "combat open pavilion semantic ID should exist");
        assertTrue(rooms.containsKey(
                        BuiltinDungeonRooms.GREAT_SWAMP_TREASURE_OBELISK_SANCTUM),
                "treasure obelisk sanctum semantic ID should exist");
        assertTrue(rooms.containsKey(
                        BuiltinDungeonRooms.GREAT_SWAMP_BOSS_ALTAR),
                "boss altar semantic ID should exist");
        assertEquals(
                BuiltinDungeonTemplates.GREAT_SWAMP_ROOM_COMBAT_OPEN_PAVILION_01,
                rooms.get(BuiltinDungeonRooms.GREAT_SWAMP_COMBAT_OPEN_PAVILION)
                        .template(),
                "combat room definition template ID should be physical"
        );
        assertEquals(
                BuiltinDungeonTemplates.GREAT_SWAMP_ROOM_BOSS_ALTAR_01,
                rooms.get(BuiltinDungeonRooms.GREAT_SWAMP_BOSS_ALTAR)
                        .template(),
                "boss room definition template ID should be physical"
        );
        assertTrue(greatSwamp.roomsFor(DungeonRoomType.COMBAT)
                        .stream()
                        .anyMatch(room -> room.room().equals(
                                BuiltinDungeonRooms
                                        .GREAT_SWAMP_COMBAT_OPEN_PAVILION)),
                "theme should reference semantic room IDs");
        assertEquals(8, corridors.size(),
                "all supplied corridors should have definitions");
        assertEquals(
                6,
                greatSwamp.corridorsFor(DungeonConnectorShapeType.STRAIGHT)
                        .size(),
                "great swamp should reference all straight corridors"
        );
        assertEquals(
                1,
                greatSwamp.corridorsFor(DungeonConnectorShapeType.CORNER)
                        .size(),
                "great swamp should reference all corner corridors"
        );
        assertEquals(
                1,
                greatSwamp.corridorsFor(DungeonConnectorShapeType.T)
                        .size(),
                "great swamp should reference all tee corridors"
        );

        for (Map.Entry<Identifier, DungeonRoomDefinition> entry
                : rooms.entrySet()) {
            assertTrue(
                    DungeonRoomDefinitionValidator
                            .validate(entry.getKey(), entry.getValue())
                            .isEmpty(),
                    "built-in room should validate: " + entry.getKey()
            );
        }
        for (Map.Entry<Identifier, DungeonCorridorDefinition> entry
                : corridors.entrySet()) {
            assertTrue(
                    DungeonCorridorDefinitionValidator
                            .validate(entry.getKey(), entry.getValue())
                            .isEmpty(),
                    "built-in corridor should validate: " + entry.getKey()
            );
        }
        assertTrue(
                DungeonThemeDefinitionValidator
                        .validate(
                                BuiltinDungeonThemes.GREAT_SWAMP,
                                greatSwamp,
                                rooms,
                                corridors
                        )
                        .isEmpty(),
                "enabled great swamp theme should validate"
        );

        DungeonThemeDefinition enabledIncomplete =
                new DungeonThemeDefinition(
                        Map.of(DungeonRoomType.COMBAT,
                                greatSwamp.roomsFor(DungeonRoomType.COMBAT)),
                        greatSwamp.corridorPools(),
                        Optional.empty(),
                        true
                );
        assertContains(
                DungeonThemeDefinitionValidator.validate(
                        TEST_THEME,
                        enabledIncomplete,
                        rooms,
                        corridors
                ),
                "enabled theme requires at least one start room",
                "enabled incomplete theme should fail validation"
        );

        assertContains(
                DungeonThemeDefinitionValidator.validate(
                        TEST_THEME,
                        disabledTheme(
                                Map.of(DungeonRoomType.START,
                                        List.of(new WeightedDungeonRoom(
                                                BuiltinDungeonRooms
                                                        .GREAT_SWAMP_COMBAT_OPEN_PAVILION,
                                                1
                                        ))),
                                Map.of()
                        ),
                        rooms,
                        corridors
                ),
                "but is referenced from start pool",
                "room type mismatch should be reported"
        );

        DungeonContent.install(new DungeonContentSnapshot(
                rooms,
                corridors,
                BuiltinDungeonThemeDefinitions.all(),
                Map.of()
        ));
        DungeonGenerationCatalog catalog = new DungeonGenerationCatalog(
                BuiltinDungeonThemes.GREAT_SWAMP,
                BuiltinDungeonThemeDefinitions.all()
                        .get(BuiltinDungeonThemes.GREAT_SWAMP),
                DungeonContent.active().rooms(),
                DungeonContent.active().corridors(),
                new DungeonTemplateGeometryCatalog(Map.of())
        );
        assertTrue(
                DungeonContent.active().rooms().containsKey(catalog.selectRoom(
                        DungeonRoomType.COMBAT,
                        RandomSource.create(42L),
                        "content definition test"
                )),
                "catalog should select rooms from enabled themes"
        );
        assertTrue(
                DungeonContent.active().corridors().containsKey(
                        catalog.selectCorridor(
                                DungeonConnectorShapeType.STRAIGHT,
                                RandomSource.create(42L),
                                "content definition test"
                        )
                ),
                "catalog should select corridors from enabled themes"
        );

        Identifier sharedTemplate = rooms
                .get(BuiltinDungeonRooms.GREAT_SWAMP_COMBAT_OPEN_PAVILION)
                .template();
        DungeonRoomDefinition alternateSemanticRoom = new DungeonRoomDefinition(
                sharedTemplate,
                DungeonRoomType.START,
                rooms.get(BuiltinDungeonRooms.GREAT_SWAMP_COMBAT_OPEN_PAVILION)
                        .footprint(),
                BlockPos.ZERO,
                BlockPos.ZERO,
                List.of(port(
                        "north",
                        new DungeonCellPos(0, 0, 0),
                        new BlockPos(2, 1, 0),
                        DungeonConnectorSide.NORTH
                )),
                List.of(DungeonRoomRotation.NONE),
                false,
                Optional.empty(),
                Optional.empty(),
                0,
                Integer.MAX_VALUE,
                true,
                false,
                false
        );
        assertEquals(sharedTemplate, alternateSemanticRoom.template(),
                "multiple semantic definitions may share one physical template");
    }

    private static void testGenerationCatalogScopesSelectedTheme() {
        Map<Identifier, DungeonRoomDefinition> rooms = completeRooms();
        Identifier unusedRoom = testId("room/unselected");
        rooms.put(
                unusedRoom,
                roomWithTemplate(
                        testId("template/room/unselected"),
                        DungeonRoomType.COMBAT
                )
        );

        Map<Identifier, DungeonCorridorDefinition> corridors =
                selectedCorridors();
        Identifier unusedCorridor = testId("corridor/unselected");
        corridors.put(
                unusedCorridor,
                corridorWithTemplate(
                        testId("template/corridor/unselected"),
                        DungeonConnectorShapeType.STRAIGHT
                )
        );

        Identifier missingRoom = testId("room/theme_b_missing");
        Identifier missingCorridor = testId("corridor/theme_b_missing");
        Map<DungeonRoomType, List<WeightedDungeonRoom>> brokenRoomPools =
                new EnumMap<>(DungeonRoomType.class);
        brokenRoomPools.put(
                DungeonRoomType.START,
                List.of(new WeightedDungeonRoom(missingRoom, 1))
        );
        Map<DungeonConnectorShapeType, List<WeightedDungeonCorridor>>
                brokenCorridorPools =
                new EnumMap<>(DungeonConnectorShapeType.class);
        brokenCorridorPools.put(
                DungeonConnectorShapeType.STRAIGHT,
                List.of(new WeightedDungeonCorridor(missingCorridor, 1))
        );

        DungeonContentSnapshot snapshot = new DungeonContentSnapshot(
                rooms,
                corridors,
                Map.of(
                        TEST_THEME_A,
                        enabledTheme(
                                completeRoomPools(),
                                straightCorridorPool(TEST_CORRIDOR)
                        ),
                        TEST_THEME_B,
                        enabledTheme(brokenRoomPools, brokenCorridorPools)
                ),
                Map.of()
        );

        DungeonGenerationCatalog catalog =
                DungeonGenerationCatalog.fromSnapshot(
                        TEST_THEME_A,
                        snapshot,
                        selectedGeometryCatalog()
                );

        assertFalse(
                catalog.rooms().containsKey(unusedRoom),
                "catalog must not include rooms outside selected theme"
        );
        assertFalse(
                catalog.corridors().containsKey(unusedCorridor),
                "catalog must not include corridors outside selected theme"
        );
        assertEquals(
                roomId(DungeonRoomType.COMBAT),
                catalog.selectRoom(
                        DungeonRoomType.COMBAT,
                        RandomSource.create(1L),
                        "theme-scoped catalog test"
                ),
                "selected theme room should still resolve"
        );
        assertEquals(
                TEST_CORRIDOR,
                catalog.selectCorridor(
                        DungeonConnectorShapeType.STRAIGHT,
                        RandomSource.create(1L),
                        "theme-scoped catalog test"
                ),
                "selected theme corridor should still resolve"
        );
    }

    private static void testGenerationCatalogRejectsSelectedMissingContent() {
        Identifier missingRoom = testId("room/selected_missing");
        Map<DungeonRoomType, List<WeightedDungeonRoom>> roomPools =
                completeRoomPools();
        roomPools.put(
                DungeonRoomType.START,
                List.of(new WeightedDungeonRoom(missingRoom, 1))
        );
        assertThrowsContaining(
                () -> DungeonGenerationCatalog.fromSnapshot(
                        TEST_THEME_A,
                        snapshot(
                                completeRooms(),
                                selectedCorridors(),
                                enabledTheme(
                                        roomPools,
                                        straightCorridorPool(TEST_CORRIDOR)
                                )
                        ),
                        selectedGeometryCatalog()
                ),
                "selected missing room should fail",
                "category=room",
                "requestedId=" + missingRoom
        );

        Identifier missingCorridor = testId("corridor/selected_missing");
        assertThrowsContaining(
                () -> DungeonGenerationCatalog.fromSnapshot(
                        TEST_THEME_A,
                        snapshot(
                                completeRooms(),
                                selectedCorridors(),
                                enabledTheme(
                                        completeRoomPools(),
                                        straightCorridorPool(missingCorridor)
                                )
                        ),
                        selectedGeometryCatalog()
                ),
                "selected missing corridor should fail",
                "category=corridor",
                "requestedId=" + missingCorridor
        );

        Map<Identifier, DungeonTemplateGeometry> geometry =
                selectedGeometries();
        Identifier missingTemplate = roomTemplateId(DungeonRoomType.TREASURE);
        geometry.remove(missingTemplate);
        assertThrowsContaining(
                () -> DungeonGenerationCatalog.fromSnapshot(
                        TEST_THEME_A,
                        selectedSnapshot(),
                        new DungeonTemplateGeometryCatalog(geometry)
                ),
                "selected missing template geometry should fail",
                "category=template_geometry",
                "requestedId=" + missingTemplate,
                "context=theme=" + TEST_THEME_A
        );
    }

    private static void testGenerationCatalogCacheClearsOnInstall() {
        DungeonContent.install(DungeonContentSnapshot.empty());
        DungeonContentSnapshot snapshot = selectedSnapshot();
        DungeonTemplateGeometryCatalog geometry = selectedGeometryCatalog();

        DungeonGenerationCatalog first = DungeonGenerationCatalog.fromSnapshot(
                TEST_THEME_A,
                snapshot,
                geometry
        );
        DungeonGenerationCatalog second = DungeonGenerationCatalog.fromSnapshot(
                TEST_THEME_A,
                snapshot,
                geometry
        );
        assertSame(first, second, "catalog should be cached by snapshot/theme");

        DungeonContent.install(DungeonContentSnapshot.empty());
        DungeonGenerationCatalog afterReload =
                DungeonGenerationCatalog.fromSnapshot(
                        TEST_THEME_A,
                        snapshot,
                        geometry
                );
        assertNotSame(
                first,
                afterReload,
                "content install should clear cached generation catalogs"
        );
    }

    private static void testTemplateAssets() throws Exception {
        Path resources = Path.of("src/main/resources");
        Map<Identifier, DungeonRoomDefinition> rooms =
                BuiltinDungeonRoomDefinitions.all();
        Map<Identifier, DungeonCorridorDefinition> corridors =
                BuiltinDungeonCorridorDefinitions.all();
        Map<Identifier, DungeonTemplateResourceValidator.Size> sizes =
                new LinkedHashMap<>();

        for (Identifier template : BuiltinDungeonTemplates.ALL_SUPPLIED_TEMPLATES) {
            Path path = DungeonTemplateResourceValidator.templatePath(
                    resources,
                    template
            );
            assertTrue(Files.exists(path), "template should exist: " + path);
            sizes.put(template,
                    DungeonTemplateResourceValidator.readTemplateSize(path));
        }

        assertEquals(
                new DungeonTemplateResourceValidator.Size(8, 10, 8),
                sizes.get(BuiltinDungeonTemplates.GREAT_SWAMP_ROOM_START_OPEN_PAVILION_01),
                "start open pavilion NBT size"
        );
        assertEquals(
                new DungeonTemplateResourceValidator.Size(8, 10, 8),
                sizes.get(BuiltinDungeonTemplates.GREAT_SWAMP_ROOM_COMBAT_OPEN_PAVILION_01),
                "combat open pavilion NBT size"
        );
        assertEquals(
                new DungeonTemplateResourceValidator.Size(32, 40, 32),
                sizes.get(BuiltinDungeonTemplates.GREAT_SWAMP_ROOM_TREASURE_OBELISK_SANCTUM_01),
                "obelisk sanctum NBT size"
        );
        assertEquals(
                new DungeonTemplateResourceValidator.Size(4, 1, 4),
                sizes.get(BuiltinDungeonTemplates.GREAT_SWAMP_CORRIDOR_STRAIGHT_01),
                "straight corridor NBT size"
        );
        assertEquals(
                new DungeonTemplateResourceValidator.Size(4, 1, 4),
                sizes.get(BuiltinDungeonTemplates.GREAT_SWAMP_CORRIDOR_STRAIGHT_06),
                "straight corridor NBT size"
        );
        assertEquals(
                new DungeonTemplateResourceValidator.Size(6, 1, 6),
                sizes.get(BuiltinDungeonTemplates.GREAT_SWAMP_CORRIDOR_CORNER_01),
                "corner corridor NBT size"
        );
        assertEquals(
                new DungeonTemplateResourceValidator.Size(8, 1, 6),
                sizes.get(BuiltinDungeonTemplates.GREAT_SWAMP_CORRIDOR_TEE_01),
                "tee corridor NBT size"
        );

        for (Map.Entry<Identifier, DungeonRoomDefinition> entry
                : rooms.entrySet()) {
            assertTrue(
                    DungeonTemplateResourceValidator
                            .validateRoom(
                                    entry.getKey(),
                                    entry.getValue(),
                                    sizes.get(entry.getValue().template())
                            )
                            .isEmpty(),
                    "room template should fit definition: " + entry.getKey()
            );
        }
        for (Map.Entry<Identifier, DungeonCorridorDefinition> entry
                : corridors.entrySet()) {
            assertTrue(
                    DungeonTemplateResourceValidator
                            .validateCorridor(
                                    entry.getKey(),
                                    entry.getValue(),
                                    sizes.get(entry.getValue().template())
                            )
                            .isEmpty(),
                    "corridor template should fit definition: " + entry.getKey()
            );
        }
        assertTrue(
                DungeonTemplateResourceValidator
                        .validateAllSuppliedTemplatesReferenced(rooms, corridors)
                        .isEmpty(),
                "all supplied templates should be referenced by content definitions"
        );
    }

    private static void testRegistryAndAtomicInstall() {
        Map<Identifier, DungeonRoomDefinition> rooms =
                BuiltinDungeonRoomDefinitions.all();
        Map<Identifier, DungeonCorridorDefinition> corridors =
                BuiltinDungeonCorridorDefinitions.all();
        Map<Identifier, DungeonThemeDefinition> themes =
                BuiltinDungeonThemeDefinitions.all();

        assertThrows(
                () -> DungeonContent.active().rooms().clear(),
                "room snapshot should be immutable"
        );
        assertThrows(
                () -> DungeonContent.active().corridors().clear(),
                "corridor snapshot should be immutable"
        );
        assertThrows(
                () -> DungeonContent.active().themes().clear(),
                "theme snapshot should be immutable"
        );

        boolean installed = DungeonContentReloadListener.validateAndInstall(
                rooms,
                corridors,
                themes
        );
        assertTrue(installed, "valid content should install");

        DungeonContentSnapshot before = DungeonContent.active();
        boolean parseFailed = DungeonContentReloadListener.validateAndInstall(
                Map.of(),
                corridors,
                themes,
                List.of("failed to parse dungeon room obeliskdepths:broken")
        );
        assertTrue(!parseFailed, "parse failure should block installation");
        assertEquals(before, DungeonContent.active(),
                "parse failure should retain active snapshot");

        boolean invalidTheme = DungeonContentReloadListener.validateAndInstall(
                rooms,
                Map.of(),
                themes
        );
        assertTrue(!invalidTheme,
                "invalid theme cross-reference should block installation");
        assertEquals(before, DungeonContent.active(),
                "invalid theme should retain active snapshot");
    }

    private static RoomConnectorDefinition port(
            String id,
            DungeonCellPos boundaryCell,
            BlockPos openingMin,
            DungeonConnectorSide facing
    ) {
        return new RoomConnectorDefinition(
                id,
                boundaryCell,
                openingMin,
                facing,
                STANDARD_CONNECTOR,
                4,
                4,
                true
        );
    }

    private static DungeonRoomDefinition room(
            DungeonRoomType type,
            DungeonRoomFootprint footprint,
            List<RoomConnectorDefinition> ports
    ) {
        return new DungeonRoomDefinition(
                TEST_TEMPLATE,
                type,
                footprint,
                BlockPos.ZERO,
                BlockPos.ZERO,
                ports,
                List.of(DungeonRoomRotation.NONE),
                false,
                Optional.empty(),
                Optional.empty(),
                0,
                Integer.MAX_VALUE,
                true,
                true,
                false
        );
    }

    private static DungeonThemeDefinition disabledTheme(
            Map<DungeonRoomType, List<WeightedDungeonRoom>> roomPools,
            Map<DungeonConnectorShapeType, List<WeightedDungeonCorridor>>
                    corridorPools
    ) {
        EnumMap<DungeonRoomType, List<WeightedDungeonRoom>> roomCopy =
                new EnumMap<>(DungeonRoomType.class);
        roomCopy.putAll(roomPools);
        EnumMap<DungeonConnectorShapeType, List<WeightedDungeonCorridor>>
                corridorCopy = new EnumMap<>(DungeonConnectorShapeType.class);
        corridorCopy.putAll(corridorPools);
        return new DungeonThemeDefinition(
                roomCopy,
                corridorCopy,
                Optional.empty(),
                false
        );
    }

    private static DungeonContentSnapshot selectedSnapshot() {
        return snapshot(
                completeRooms(),
                selectedCorridors(),
                enabledTheme(
                        completeRoomPools(),
                        straightCorridorPool(TEST_CORRIDOR)
                )
        );
    }

    private static DungeonContentSnapshot snapshot(
            Map<Identifier, DungeonRoomDefinition> rooms,
            Map<Identifier, DungeonCorridorDefinition> corridors,
            DungeonThemeDefinition theme
    ) {
        return new DungeonContentSnapshot(
                rooms,
                corridors,
                Map.of(TEST_THEME_A, theme),
                Map.of()
        );
    }

    private static Map<Identifier, DungeonRoomDefinition> completeRooms() {
        Map<Identifier, DungeonRoomDefinition> rooms = new LinkedHashMap<>();
        for (DungeonRoomType type : DungeonRoomType.values()) {
            rooms.put(
                    roomId(type),
                    roomWithTemplate(roomTemplateId(type), type)
            );
        }
        return rooms;
    }

    private static Map<Identifier, DungeonCorridorDefinition>
    selectedCorridors() {
        Map<Identifier, DungeonCorridorDefinition> corridors =
                new LinkedHashMap<>();
        corridors.put(
                TEST_CORRIDOR,
                corridorWithTemplate(
                        TEST_CORRIDOR_TEMPLATE,
                        DungeonConnectorShapeType.STRAIGHT
                )
        );
        return corridors;
    }

    private static Map<DungeonRoomType, List<WeightedDungeonRoom>>
    completeRoomPools() {
        Map<DungeonRoomType, List<WeightedDungeonRoom>> pools =
                new EnumMap<>(DungeonRoomType.class);
        for (DungeonRoomType type : DungeonRoomType.values()) {
            pools.put(type, List.of(new WeightedDungeonRoom(roomId(type), 1)));
        }
        return pools;
    }

    private static Map<DungeonConnectorShapeType, List<WeightedDungeonCorridor>>
    straightCorridorPool(Identifier corridorId) {
        Map<DungeonConnectorShapeType, List<WeightedDungeonCorridor>> pools =
                new EnumMap<>(DungeonConnectorShapeType.class);
        pools.put(
                DungeonConnectorShapeType.STRAIGHT,
                List.of(new WeightedDungeonCorridor(corridorId, 1))
        );
        return pools;
    }

    private static DungeonThemeDefinition enabledTheme(
            Map<DungeonRoomType, List<WeightedDungeonRoom>> roomPools,
            Map<DungeonConnectorShapeType, List<WeightedDungeonCorridor>>
                    corridorPools
    ) {
        return new DungeonThemeDefinition(
                roomPools,
                corridorPools,
                Optional.empty(),
                true
        );
    }

    private static DungeonRoomDefinition roomWithTemplate(
            Identifier template,
            DungeonRoomType type
    ) {
        return new DungeonRoomDefinition(
                template,
                type,
                DungeonRoomFootprint.fromLayers(List.of(List.of("#"))),
                BlockPos.ZERO,
                BlockPos.ZERO,
                List.of(port(
                        "north",
                        new DungeonCellPos(0, 0, 0),
                        new BlockPos(2, 1, 0),
                        DungeonConnectorSide.NORTH
                )),
                List.of(DungeonRoomRotation.NONE),
                false,
                Optional.empty(),
                Optional.empty(),
                0,
                Integer.MAX_VALUE,
                true,
                true,
                false
        );
    }

    private static DungeonCorridorDefinition corridorWithTemplate(
            Identifier template,
            DungeonConnectorShapeType shape
    ) {
        return new DungeonCorridorDefinition(
                template,
                shape,
                DungeonRoomFootprint.auto(),
                List.of(port(
                        "west",
                        new DungeonCellPos(0, 0, 0),
                        BlockPos.ZERO,
                        DungeonConnectorSide.WEST
                )),
                List.of(DungeonRoomRotation.NONE),
                false,
                Optional.empty()
        );
    }

    private static DungeonTemplateGeometryCatalog selectedGeometryCatalog() {
        return new DungeonTemplateGeometryCatalog(selectedGeometries());
    }

    private static Map<Identifier, DungeonTemplateGeometry>
    selectedGeometries() {
        Map<Identifier, DungeonTemplateGeometry> geometries =
                new LinkedHashMap<>();
        for (DungeonRoomType type : DungeonRoomType.values()) {
            geometries.put(
                    roomTemplateId(type),
                    new DungeonTemplateGeometry(8, 8, 8)
            );
        }
        geometries.put(
                TEST_CORRIDOR_TEMPLATE,
                new DungeonTemplateGeometry(4, 4, 4)
        );
        return geometries;
    }

    private static Identifier roomId(DungeonRoomType type) {
        return testId("room/" + type.getSerializedName());
    }

    private static Identifier roomTemplateId(DungeonRoomType type) {
        return testId("template/room/" + type.getSerializedName());
    }

    private static Identifier testId(String path) {
        return Identifier.fromNamespaceAndPath("obeliskdepths", "test/" + path);
    }

    private static JsonObject legacyFootprintJson(
            int width,
            int height,
            int depth
    ) {
        JsonObject json = new JsonObject();
        json.addProperty("width_cells", width);
        json.addProperty("height_cells", height);
        json.addProperty("depth_cells", depth);
        return json;
    }

    private static <T> T roundTrip(Codec<T> codec, T value) {
        JsonElement json = codec.encodeStart(JsonOps.INSTANCE, value)
                .getOrThrow();
        return codec.parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    private static void assertContains(
            List<String> errors,
            String expected,
            String message
    ) {
        for (String error : errors) {
            if (error.contains(expected)) {
                return;
            }
        }

        throw new AssertionError(
                message + " expected substring=" + expected + " errors=" + errors
        );
    }

    private static void assertThrows(Runnable runnable, String message) {
        try {
            runnable.run();
        } catch (RuntimeException expected) {
            return;
        }

        throw new AssertionError(message);
    }

    private static void assertThrowsContaining(
            Runnable runnable,
            String message,
            String... expectedSubstrings
    ) {
        try {
            runnable.run();
        } catch (RuntimeException expected) {
            String text = expected.getMessage();
            for (String expectedSubstring : expectedSubstrings) {
                if (text == null || !text.contains(expectedSubstring)) {
                    throw new AssertionError(
                            message
                                    + " missing substring="
                                    + expectedSubstring
                                    + " error="
                                    + text
                    );
                }
            }
            return;
        }

        throw new AssertionError(message);
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

    private static <T> void assertEquals(
            T expected,
            T actual,
            String message
    ) {
        if (!expected.equals(actual)) {
            throw new AssertionError(
                    message + " expected=" + expected + " actual=" + actual
            );
        }
    }

    private static void assertSame(
            Object expected,
            Object actual,
            String message
    ) {
        if (expected != actual) {
            throw new AssertionError(message);
        }
    }

    private static void assertNotSame(
            Object unexpected,
            Object actual,
            String message
    ) {
        if (unexpected == actual) {
            throw new AssertionError(message);
        }
    }
}
