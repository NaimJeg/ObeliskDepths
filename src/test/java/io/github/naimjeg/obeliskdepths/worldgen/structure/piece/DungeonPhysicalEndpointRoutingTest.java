package io.github.naimjeg.obeliskdepths.worldgen.structure.piece;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonGraphEdgeKind;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonCellBox;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonCellPos;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonConnectorSide;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonLayoutConstants;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutEdge;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutNode;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutPlan;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonRoomFootprint;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;

public final class DungeonPhysicalEndpointRoutingTest {
    private static final Identifier TEST_DEFINITION =
            Identifier.fromNamespaceAndPath("obeliskdepths", "test/route");

    private DungeonPhysicalEndpointRoutingTest() {
    }

    public static void main(String[] args) {
        routesToPhysicalRoomBoundaryThroughOwnPadding();
        traceDisabledDoesNotAffectRoute();
    }

    private static void routesToPhysicalRoomBoundaryThroughOwnPadding() {
        DungeonLayoutNode from = room(
                "from",
                new DungeonCellPos(0, 0, 0),
                EnumSet.of(DungeonConnectorSide.NORTH)
        );
        DungeonLayoutNode to = room(
                "to",
                new DungeonCellPos(0, 0, -5),
                EnumSet.of(DungeonConnectorSide.SOUTH)
        );
        DungeonLayoutNode unrelated = new DungeonLayoutNode(
                "unrelated",
                DungeonRoomType.COMBAT,
                TEST_DEFINITION,
                new DungeonCellPos(2, 0, -1),
                DungeonRoomFootprint.rectangular(1, 1, 1),
                EnumSet.of(DungeonConnectorSide.WEST)
        );
        DungeonLayoutPlan plan = new DungeonLayoutPlan(
                DungeonLayoutConstants.CELL_SIZE,
                List.of(from, to, unrelated),
                List.of(new DungeonLayoutEdge(
                        "edge_from_to",
                        "from",
                        "to",
                        DungeonConnectorSide.NORTH,
                        DungeonConnectorSide.SOUTH,
                        1,
                        DungeonGraphEdgeKind.TREE,
                        List.of()
                ))
        );
        DungeonCellBox fromPhysical = new DungeonCellBox(1, 0, 1, 1, 1, 1);
        DungeonCellBox toPhysical = new DungeonCellBox(1, 0, -3, 1, 1, 1);
        DungeonRoutingResult result = DungeonCorridorRouter.route(
                plan,
                Map.of(
                        "from", fromPhysical,
                        "to", toPhysical
                )
        );
        DungeonRoutedCorridor route = result.corridors().getFirst();

        assertEquals(new DungeonCellPos(1, 0, 0), route.path().getFirst(), "start cell is adjacent to physical north side");
        assertEquals(new DungeonCellPos(1, 0, -2), route.path().getLast(), "goal cell is adjacent to physical south side");
        assertTrue(from.cellBox().contains(route.path().getFirst()), "start crosses only endpoint planning padding");
        assertFalse(fromPhysical.contains(route.path().getFirst()), "start does not enter physical room");
        assertFalse(route.path().contains(new DungeonCellPos(2, 0, -1)), "route avoids unrelated room reservation");
    }

    private static void traceDisabledDoesNotAffectRoute() {
        DungeonLayoutNode from = room(
                "from",
                new DungeonCellPos(0, 0, 0),
                EnumSet.of(DungeonConnectorSide.NORTH)
        );
        DungeonLayoutNode to = room(
                "to",
                new DungeonCellPos(0, 0, -5),
                EnumSet.of(DungeonConnectorSide.SOUTH)
        );
        DungeonLayoutPlan plan = new DungeonLayoutPlan(
                DungeonLayoutConstants.CELL_SIZE,
                List.of(from, to),
                List.of(new DungeonLayoutEdge(
                        "edge_from_to",
                        "from",
                        "to",
                        DungeonConnectorSide.NORTH,
                        DungeonConnectorSide.SOUTH,
                        1,
                        DungeonGraphEdgeKind.TREE,
                        List.of()
                ))
        );
        Map<String, DungeonCellBox> physical = Map.of(
                "from", new DungeonCellBox(1, 0, 1, 1, 1, 1),
                "to", new DungeonCellBox(1, 0, -3, 1, 1, 1)
        );

        assertEquals(
                DungeonCorridorRouter.route(plan, physical),
                DungeonCorridorRouter.route(plan, physical),
                "routing remains deterministic with tracing disabled"
        );
    }

    private static DungeonLayoutNode room(
            String id,
            DungeonCellPos origin,
            EnumSet<DungeonConnectorSide> sides
    ) {
        return new DungeonLayoutNode(
                id,
                DungeonRoomType.COMBAT,
                TEST_DEFINITION,
                origin,
                DungeonRoomFootprint.rectangular(3, 1, 3),
                sides
        );
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
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
