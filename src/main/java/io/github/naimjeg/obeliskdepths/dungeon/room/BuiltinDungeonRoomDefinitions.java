package io.github.naimjeg.obeliskdepths.dungeon.room;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.raid.BuiltinDungeonRaids;
import io.github.naimjeg.obeliskdepths.dungeon.template.BuiltinDungeonTemplates;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonConnectorSide;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonRoomFootprint;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Datagen-only built-in room definitions. Runtime content loading reads the
 * generated JSON instead of depending on these factories.
 */
public final class BuiltinDungeonRoomDefinitions {
    public static final Identifier BASIC_FLOOR_PASSAGE_CONNECTOR =
            id("connector/basic_floor_passage");
    private static final List<DungeonRoomRotation> ALL_ROTATIONS =
            List.of(
                    DungeonRoomRotation.NONE,
                    DungeonRoomRotation.CLOCKWISE_90,
                    DungeonRoomRotation.CLOCKWISE_180,
                    DungeonRoomRotation.COUNTERCLOCKWISE_90
            );

    private BuiltinDungeonRoomDefinitions() {
    }

    public static Map<Identifier, DungeonRoomDefinition> all() {
        Map<Identifier, DungeonRoomDefinition> rooms = new LinkedHashMap<>();
        rooms.put(
                BuiltinDungeonRooms.GREAT_SWAMP_START_OPEN_PAVILION,
                greatSwampStartOpenPavilion()
        );
        rooms.put(
                BuiltinDungeonRooms.GREAT_SWAMP_COMBAT_OPEN_PAVILION,
                greatSwampCombatOpenPavilion()
        );
        rooms.put(
                BuiltinDungeonRooms.GREAT_SWAMP_TREASURE_OBELISK_SANCTUM,
                greatSwampTreasureObeliskSanctum()
        );
        rooms.put(
                BuiltinDungeonRooms.GREAT_SWAMP_BOSS_ALTAR,
                greatSwampBossAltar()
        );
        return Map.copyOf(rooms);
    }

    public static DungeonRoomDefinition greatSwampStartOpenPavilion() {
        return new DungeonRoomDefinition(
                BuiltinDungeonTemplates.GREAT_SWAMP_ROOM_START_OPEN_PAVILION_01,
                DungeonRoomType.START,
                DungeonRoomFootprint.auto(),
                BlockPos.ZERO,
                openPavilionAnchor(),
                openPavilionPorts(),
                ALL_ROTATIONS,
                false,
                Optional.empty(),
                Optional.empty(),
                3,
                Integer.MAX_VALUE,
                true,
                true,
                false
        );
    }

    public static DungeonRoomDefinition greatSwampCombatOpenPavilion() {
        return new DungeonRoomDefinition(
                BuiltinDungeonTemplates.GREAT_SWAMP_ROOM_COMBAT_OPEN_PAVILION_01,
                DungeonRoomType.COMBAT,
                DungeonRoomFootprint.auto(),
                BlockPos.ZERO,
                openPavilionAnchor(),
                openPavilionPorts(),
                ALL_ROTATIONS,
                false,
                Optional.of(BuiltinDungeonRaids.COMBAT_ROOM),
                Optional.empty(),
                1,
                Integer.MAX_VALUE,
                true,
                true,
                false
        );
    }

    public static DungeonRoomDefinition greatSwampTreasureObeliskSanctum() {
        return new DungeonRoomDefinition(
                BuiltinDungeonTemplates.GREAT_SWAMP_ROOM_TREASURE_OBELISK_SANCTUM_01,
                DungeonRoomType.TREASURE,
                DungeonRoomFootprint.auto(),
                BlockPos.ZERO,
                new BlockPos(28, 2, 15),
                List.of(port(
                        "east_entry",
                        new BlockPos(31, 1, 14),
                        DungeonConnectorSide.EAST
                )),
                ALL_ROTATIONS,
                false,
                Optional.empty(),
                Optional.empty(),
                0,
                Integer.MAX_VALUE,
                true,
                false,
                false
        );
    }

    public static DungeonRoomDefinition greatSwampBossAltar() {
        return new DungeonRoomDefinition(
                BuiltinDungeonTemplates.GREAT_SWAMP_ROOM_BOSS_ALTAR_01,
                DungeonRoomType.BOSS,
                DungeonRoomFootprint.auto(),
                BlockPos.ZERO,
                // TODO: Replace with authored boss marker once altar.nbt exposes one.
                new BlockPos(27, 2, 27),
                List.of(port(
                        "east_entry",
                        new BlockPos(53, 1, 25),
                        DungeonConnectorSide.EAST
                )),
                ALL_ROTATIONS,
                false,
                Optional.of(BuiltinDungeonRaids.BOSS_ROOM),
                Optional.empty(),
                0,
                Integer.MAX_VALUE,
                true,
                false,
                false
        );
    }

    private static BlockPos openPavilionAnchor() {
        return new BlockPos(4, 1, 4);
    }

    private static List<RoomConnectorDefinition> openPavilionPorts() {
        return List.of(
                port(
                        "north",
                        new BlockPos(2, 1, 0),
                        DungeonConnectorSide.NORTH
                ),
                port(
                        "south",
                        new BlockPos(2, 1, 7),
                        DungeonConnectorSide.SOUTH
                ),
                port(
                        "west",
                        new BlockPos(0, 1, 2),
                        DungeonConnectorSide.WEST
                ),
                port(
                        "east",
                        new BlockPos(7, 1, 2),
                        DungeonConnectorSide.EAST
                )
        );
    }

    private static RoomConnectorDefinition port(
            String id,
            BlockPos openingMin,
            DungeonConnectorSide facing
    ) {
        return RoomConnectorDefinition.fromOpening(
                id,
                openingMin,
                facing,
                BASIC_FLOOR_PASSAGE_CONNECTOR,
                4,
                4,
                true
        );
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ObeliskDepths.MOD_ID, path);
    }
}
