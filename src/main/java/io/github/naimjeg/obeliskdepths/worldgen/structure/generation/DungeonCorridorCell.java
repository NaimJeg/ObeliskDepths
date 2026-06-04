package io.github.naimjeg.obeliskdepths.worldgen.structure.generation;

import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonCellPos;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonConnectorSide;
import java.util.EnumSet;
import java.util.Set;

public record DungeonCorridorCell(
        DungeonCellPos cell,
        int openingMask
) {
    public DungeonCorridorCell {
        if (cell == null) {
            throw new IllegalArgumentException("Corridor cell position is required");
        }
        if (openingMask == 0) {
            throw new IllegalArgumentException(
                    "Corridor cell must have at least one opening: " + cell
            );
        }
    }

    public static int maskOf(Set<DungeonConnectorSide> openings) {
        if (openings == null || openings.isEmpty()) {
            return 0;
        }
        int mask = 0;
        for (DungeonConnectorSide side : openings) {
            mask |= bit(side);
        }
        return mask;
    }

    public static int bit(DungeonConnectorSide side) {
        if (side == null) {
            throw new IllegalArgumentException("Connector side is required");
        }
        return 1 << side.ordinal();
    }

    public boolean has(DungeonConnectorSide side) {
        return (this.openingMask & bit(side)) != 0;
    }

    public Set<DungeonConnectorSide> openings() {
        EnumSet<DungeonConnectorSide> result =
                EnumSet.noneOf(DungeonConnectorSide.class);
        for (DungeonConnectorSide side : DungeonConnectorSide.values()) {
            if (has(side)) {
                result.add(side);
            }
        }
        return result;
    }
}
