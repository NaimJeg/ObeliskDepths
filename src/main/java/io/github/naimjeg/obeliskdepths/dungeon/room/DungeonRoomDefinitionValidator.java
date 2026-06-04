package io.github.naimjeg.obeliskdepths.dungeon.room;

import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonCellPos;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonConnectorSide;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonRoomFootprint;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DungeonRoomDefinitionValidator {
    private DungeonRoomDefinitionValidator() {
    }

    public static List<String> validate(
            Identifier definitionId,
            DungeonRoomDefinition definition
    ) {
        List<String> errors = new ArrayList<>();

        if (definitionId == null) {
            errors.add("definition id must not be null");
        }

        if (definition == null) {
            errors.add("definition must not be null");
            return List.copyOf(errors);
        }

        Set<String> portIds = new HashSet<>();

        for (RoomConnectorDefinition port : definition.ports()) {
            if (!portIds.add(port.id())) {
                errors.add("duplicate port id: " + port.id());
            }

            validatePort(definition, port, errors);
        }

        if (definition.type() == DungeonRoomType.START
                && definition.ports().stream().noneMatch(
                DungeonRoomDefinitionValidator::horizontalUsablePort
        )) {
            errors.add("START room requires at least one horizontal port");
        }

        if ((definition.type() == DungeonRoomType.COMBAT
                || definition.type() == DungeonRoomType.BOSS)
                && definition.ports().stream().noneMatch(
                DungeonRoomDefinitionValidator::horizontalUsablePort
        )) {
            errors.add(definition.type()
                    + " room requires at least one horizontal port");
        }

        return List.copyOf(errors);
    }

    private static void validatePort(
            DungeonRoomDefinition definition,
            RoomConnectorDefinition port,
            List<String> errors
    ) {
        DungeonRoomFootprint footprint = definition.footprint();
        DungeonCellPos cell = port.boundaryCell();
        DungeonConnectorSide side = port.facing();

        if (!footprint.isAuto()) {
            if (!footprint.containsCell(cell)) {
                errors.add("port "
                        + port.id()
                        + " boundary cell is not occupied by routing footprint: "
                        + cell);
                return;
            }

            DungeonCellPos outside = new DungeonCellPos(
                    cell.x() + side.dx(),
                    cell.y() + side.dy(),
                    cell.z() + side.dz()
            );

            if (footprint.containsCell(outside)) {
                errors.add("port "
                        + port.id()
                        + " faces occupied neighbor in routing mask instead of exposed "
                        + side.getSerializedName()
                        + " boundary: "
                        + outside);
            }
        }

        if (side.vertical()) {
            errors.add("port "
                    + port.id()
                    + " uses unsupported vertical facing "
                    + side.getSerializedName()
                    + " for current generation");
        }

        validateOpening(definition, port, errors);
    }

    private static void validateOpening(
            DungeonRoomDefinition definition,
            RoomConnectorDefinition port,
            List<String> errors
    ) {
        BlockPos opening = port.openingMin();

        if (opening.getX() < 0 || opening.getY() < 0 || opening.getZ() < 0) {
            errors.add("port "
                    + port.id()
                    + " opening_min must be non-negative: "
                    + opening);
        }
    }

    private static boolean horizontalUsablePort(RoomConnectorDefinition port) {
        return port != null && !port.facing().vertical();
    }
}
