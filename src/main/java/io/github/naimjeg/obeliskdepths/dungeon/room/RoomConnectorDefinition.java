package io.github.naimjeg.obeliskdepths.dungeon.room;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonCellPos;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonConnectorSide;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonLayoutConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/**
 * Authored room or corridor port metadata. The boundary cell is relative
 * layout-cell space. The opening minimum is the template-local minimum block of
 * the physical opening: NORTH/SOUTH width extends along X, EAST/WEST width
 * extends along Z, and height always extends along Y. Facing points outward.
 */
public record RoomConnectorDefinition(
        String id,
        DungeonCellPos boundaryCell,
        BlockPos openingMin,
        DungeonConnectorSide facing,
        Identifier connectorType,
        int widthBlocks,
        int heightBlocks,
        boolean required
) {
    /**
     * Returns the room-local routing cell that contains the physical opening.
     * The adjacent external routing cell is derived later from this cell and
     * the connector facing; callers should not maintain a separate stale hint.
     */
    public static DungeonCellPos boundaryCellForOpening(BlockPos openingMin) {
        if (openingMin == null) {
            throw new IllegalArgumentException("Connector openingMin must not be null");
        }
        return new DungeonCellPos(
                DungeonLayoutConstants.blockToCellFloorX(openingMin.getX()),
                DungeonLayoutConstants.blockToCellFloorY(openingMin.getY()),
                DungeonLayoutConstants.blockToCellFloorZ(openingMin.getZ())
        );
    }

    public static RoomConnectorDefinition fromOpening(
            String id,
            BlockPos openingMin,
            DungeonConnectorSide facing,
            Identifier connectorType,
            int widthBlocks,
            int heightBlocks,
            boolean required
    ) {
        return new RoomConnectorDefinition(
                id,
                boundaryCellForOpening(openingMin),
                openingMin,
                facing,
                connectorType,
                widthBlocks,
                heightBlocks,
                required
        );
    }

    public static final Codec<RoomConnectorDefinition> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING
                            .fieldOf("id")
                            .forGetter(RoomConnectorDefinition::id),
                    DungeonCellPos.CODEC
                            .fieldOf("boundary_cell")
                            .forGetter(RoomConnectorDefinition::boundaryCell),
                    BlockPos.CODEC
                            .fieldOf("opening_min")
                            .forGetter(RoomConnectorDefinition::openingMin),
                    DungeonConnectorSide.CODEC
                            .fieldOf("facing")
                            .forGetter(RoomConnectorDefinition::facing),
                    Identifier.CODEC
                            .fieldOf("connector_type")
                            .forGetter(RoomConnectorDefinition::connectorType),
                    Codec.INT
                            .optionalFieldOf("width_blocks", 1)
                            .forGetter(RoomConnectorDefinition::widthBlocks),
                    Codec.INT
                            .optionalFieldOf("height_blocks", 2)
                            .forGetter(RoomConnectorDefinition::heightBlocks),
                    Codec.BOOL
                            .optionalFieldOf("required", false)
                            .forGetter(RoomConnectorDefinition::required)
            ).apply(instance, RoomConnectorDefinition::new));

    public RoomConnectorDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Room connector id must not be blank"
            );
        }

        if (boundaryCell == null) {
            throw new IllegalArgumentException(
                    "Room connector boundaryCell must not be null: " + id
            );
        }

        if (openingMin == null) {
            throw new IllegalArgumentException(
                    "Room connector openingMin must not be null: " + id
            );
        }

        if (facing == null) {
            throw new IllegalArgumentException(
                    "Room connector facing must not be null: " + id
            );
        }

        if (connectorType == null) {
            throw new IllegalArgumentException(
                    "Room connector connectorType must not be null: " + id
            );
        }

        if (widthBlocks <= 0) {
            throw new IllegalArgumentException(
                    "Room connector widthBlocks must be positive: " + id
            );
        }

        if (heightBlocks <= 0) {
            throw new IllegalArgumentException(
                    "Room connector heightBlocks must be positive: " + id
            );
        }
    }
}
