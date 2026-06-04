package io.github.naimjeg.obeliskdepths.dungeon.corridor;

import io.github.naimjeg.obeliskdepths.dungeon.room.BuiltinDungeonRoomDefinitions;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomRotation;
import io.github.naimjeg.obeliskdepths.dungeon.room.RoomConnectorDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.template.BuiltinDungeonTemplates;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonCellPos;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonConnectorShapeType;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonConnectorSide;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonLayoutConstants;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonRoomFootprint;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Datagen-only built-in corridor definitions. Runtime content loading reads the
 * generated JSON instead of depending on these factories.
 */
public final class BuiltinDungeonCorridorDefinitions {
    private static final List<DungeonRoomRotation> ALL_ROTATIONS =
            List.of(
                    DungeonRoomRotation.NONE,
                    DungeonRoomRotation.CLOCKWISE_90,
                    DungeonRoomRotation.CLOCKWISE_180,
                    DungeonRoomRotation.COUNTERCLOCKWISE_90
            );

    private BuiltinDungeonCorridorDefinitions() {
    }

    public static Map<Identifier, DungeonCorridorDefinition> all() {
        Map<Identifier, DungeonCorridorDefinition> corridors =
                new LinkedHashMap<>();
        add(corridors, BuiltinDungeonCorridors.GREAT_SWAMP_STRAIGHT_01,
                BuiltinDungeonTemplates.GREAT_SWAMP_CORRIDOR_STRAIGHT_01,
                straight());
        add(corridors, BuiltinDungeonCorridors.GREAT_SWAMP_STRAIGHT_02,
                BuiltinDungeonTemplates.GREAT_SWAMP_CORRIDOR_STRAIGHT_02,
                straight());
        add(corridors, BuiltinDungeonCorridors.GREAT_SWAMP_STRAIGHT_03,
                BuiltinDungeonTemplates.GREAT_SWAMP_CORRIDOR_STRAIGHT_03,
                straight());
        add(corridors, BuiltinDungeonCorridors.GREAT_SWAMP_STRAIGHT_04,
                BuiltinDungeonTemplates.GREAT_SWAMP_CORRIDOR_STRAIGHT_04,
                straight());
        add(corridors, BuiltinDungeonCorridors.GREAT_SWAMP_STRAIGHT_05,
                BuiltinDungeonTemplates.GREAT_SWAMP_CORRIDOR_STRAIGHT_05,
                straight());
        add(corridors, BuiltinDungeonCorridors.GREAT_SWAMP_STRAIGHT_06,
                BuiltinDungeonTemplates.GREAT_SWAMP_CORRIDOR_STRAIGHT_06,
                straight());
        add(corridors, BuiltinDungeonCorridors.GREAT_SWAMP_CORNER_01,
                BuiltinDungeonTemplates.GREAT_SWAMP_CORRIDOR_CORNER_01,
                corner());
        add(corridors, BuiltinDungeonCorridors.GREAT_SWAMP_TEE_01,
                BuiltinDungeonTemplates.GREAT_SWAMP_CORRIDOR_TEE_01,
                tee());
        return Map.copyOf(corridors);
    }

    private static void add(
            Map<Identifier, DungeonCorridorDefinition> definitions,
            Identifier id,
            Identifier template,
            CorridorShape shape
    ) {
        definitions.put(id, new DungeonCorridorDefinition(
                template,
                shape.type(),
                DungeonRoomFootprint.auto(),
                shape.ports(),
                ALL_ROTATIONS,
                false,
                Optional.empty()
        ));
    }

    private static CorridorShape straight() {
        /*
         * Current authored corridor NBTs are one-block-high floor templates.
         * They contain no reliable structure-block markers, so connector
         * openings are explicit one-cell-wide boundary apertures inside the
         * exact 4x1x4 template bounds.
         */
        return new CorridorShape(
                DungeonConnectorShapeType.STRAIGHT,
                List.of(
                        port("west", new BlockPos(0, 0, 0),
                                DungeonConnectorSide.WEST, 4, 1),
                        port("east", new BlockPos(3, 0, 0),
                                DungeonConnectorSide.EAST, 4, 1)
                )
        );
    }

    private static CorridorShape corner() {
        /*
         * corner_01 is 6x1x6. The connectors are kept on the south/east
         * boundary faces expected by the compiler's base corner orientation.
         */
        return new CorridorShape(
                DungeonConnectorShapeType.CORNER,
                List.of(
                        port("south", new BlockPos(1, 0, 5),
                                DungeonConnectorSide.SOUTH, 4, 1),
                        port("east", new BlockPos(5, 0, 1),
                                DungeonConnectorSide.EAST, 4, 1)
                )
        );
    }

    private static CorridorShape tee() {
        /*
         * tee_01 is 8x1x6. The base T orientation intentionally omits EAST;
         * DungeonPiecePlanCompiler rotates this base definition for other
         * three-way connection sets.
         */
        return new CorridorShape(
                DungeonConnectorShapeType.T,
                List.of(
                        port("north", new BlockPos(2, 0, 0),
                                DungeonConnectorSide.NORTH, 4, 1),
                        port("south", new BlockPos(2, 0, 5),
                                DungeonConnectorSide.SOUTH, 4, 1),
                        port("west", new BlockPos(0, 0, 1),
                                DungeonConnectorSide.WEST, 4, 1)
                )
        );
    }

    private static RoomConnectorDefinition port(
            String id,
            BlockPos openingMin,
            DungeonConnectorSide side,
            int widthBlocks,
            int heightBlocks
    ) {
        DungeonCellPos hint = new DungeonCellPos(
                DungeonLayoutConstants.blockToCellFloorX(openingMin.getX()),
                DungeonLayoutConstants.blockToCellFloorY(openingMin.getY()),
                DungeonLayoutConstants.blockToCellFloorZ(openingMin.getZ())
        );
        return new RoomConnectorDefinition(
                id,
                hint,
                openingMin,
                side,
                BuiltinDungeonRoomDefinitions.BASIC_FLOOR_PASSAGE_CONNECTOR,
                widthBlocks,
                heightBlocks,
                true
        );
    }

    private record CorridorShape(
            DungeonConnectorShapeType type,
            List<RoomConnectorDefinition> ports
    ) {
    }
}
