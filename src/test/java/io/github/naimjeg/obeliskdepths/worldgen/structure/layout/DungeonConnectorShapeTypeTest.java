package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

import io.github.naimjeg.obeliskdepths.dungeon.geometry.*;

import java.util.EnumSet;

public final class DungeonConnectorShapeTypeTest {
    private DungeonConnectorShapeTypeTest() {
    }

    public static void main(String[] args) {
        assertSame(
                DungeonConnectorShapeType.CAP,
                DungeonConnectorShapeType.fromSides(EnumSet.of(DungeonConnectorSide.NORTH)),
                "one connector is a cap"
        );
        assertSame(
                DungeonConnectorShapeType.STRAIGHT,
                DungeonConnectorShapeType.fromSides(EnumSet.of(
                        DungeonConnectorSide.NORTH,
                        DungeonConnectorSide.SOUTH
                )),
                "opposite connectors are straight"
        );
        assertSame(
                DungeonConnectorShapeType.CORNER,
                DungeonConnectorShapeType.fromSides(EnumSet.of(
                        DungeonConnectorSide.NORTH,
                        DungeonConnectorSide.EAST
                )),
                "adjacent connectors are a corner"
        );
        assertSame(
                DungeonConnectorShapeType.T,
                DungeonConnectorShapeType.fromSides(EnumSet.of(
                        DungeonConnectorSide.NORTH,
                        DungeonConnectorSide.EAST,
                        DungeonConnectorSide.WEST
                )),
                "three connectors are T"
        );
        assertSame(
                DungeonConnectorShapeType.CROSS,
                DungeonConnectorShapeType.fromSides(EnumSet.of(
                        DungeonConnectorSide.NORTH,
                        DungeonConnectorSide.SOUTH,
                        DungeonConnectorSide.EAST,
                        DungeonConnectorSide.WEST
                )),
                "four horizontal connectors are cross"
        );
        assertSame(
                DungeonConnectorShapeType.VERTICAL_CAP,
                DungeonConnectorShapeType.fromSides(EnumSet.of(DungeonConnectorSide.UP)),
                "one vertical connector is a vertical cap"
        );
        assertSame(
                DungeonConnectorShapeType.VERTICAL_THROUGH,
                DungeonConnectorShapeType.fromSides(EnumSet.of(
                        DungeonConnectorSide.UP,
                        DungeonConnectorSide.DOWN
                )),
                "opposite vertical connectors are vertical through"
        );
        assertSame(
                DungeonConnectorShapeType.MIXED_3D,
                DungeonConnectorShapeType.fromSides(EnumSet.of(
                        DungeonConnectorSide.UP,
                        DungeonConnectorSide.NORTH
                )),
                "vertical plus horizontal connectors are mixed 3D"
        );
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
}
