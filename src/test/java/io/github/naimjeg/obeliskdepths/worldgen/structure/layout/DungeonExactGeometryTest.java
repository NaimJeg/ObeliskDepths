package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

import io.github.naimjeg.obeliskdepths.dungeon.geometry.*;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomRotation;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraph;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphGenerator;
import io.github.naimjeg.obeliskdepths.worldgen.structure.generation.DungeonGenerationCatalog;
import io.github.naimjeg.obeliskdepths.worldgen.structure.piece.DungeonPieceMetadata;
import io.github.naimjeg.obeliskdepths.worldgen.structure.piece.DungeonPiecePlan;
import io.github.naimjeg.obeliskdepths.worldgen.structure.piece.DungeonPiecePlanCompiler;
import io.github.naimjeg.obeliskdepths.worldgen.structure.test.DungeonGenerationCatalogTestFixtures;
import java.util.List;
import net.minecraft.core.BlockPos;

public final class DungeonExactGeometryTest {
    private static final DungeonGenerationCatalog CATALOG =
            DungeonGenerationCatalogTestFixtures.catalog();

    private DungeonExactGeometryTest() {
    }

    public static void main(String[] args) {
        cellConversions();
        nonSquareRotations();
        exactCollision();
        deterministicPieceMetadata();
    }

    private static void cellConversions() {
        assertEquals(8, DungeonLayoutConstants.CELL_SIZE, "routing cell size");
        assertEquals(8, DungeonLayoutConstants.CELL_SIZE_X, "routing cell size x");
        assertEquals(8, DungeonLayoutConstants.CELL_SIZE_Y, "routing cell size y");
        assertEquals(8, DungeonLayoutConstants.CELL_SIZE_Z, "routing cell size z");
        assertCells(new DungeonTemplateGeometry(1, 1, 1), 1, 1, 1);
        assertCells(new DungeonTemplateGeometry(4, 8, 4), 1, 1, 1);
        assertCells(new DungeonTemplateGeometry(5, 9, 5), 1, 2, 1);
        assertCells(new DungeonTemplateGeometry(23, 11, 17), 3, 2, 3);
        assertCells(new DungeonTemplateGeometry(13, 9, 27), 2, 2, 4);
        assertCells(new DungeonTemplateGeometry(31, 15, 10), 4, 2, 2);

        assertEquals(-1, DungeonLayoutConstants.blockToCellFloorX(-1),
                "negative x floor");
        assertEquals(-1, DungeonLayoutConstants.blockToCellFloorY(-1),
                "negative y floor");
        assertEquals(-1, DungeonLayoutConstants.blockToCellFloorZ(-1),
                "negative z floor");
        assertEquals(-2, DungeonLayoutConstants.blockToCellFloorX(-9),
                "negative x floor beyond boundary");
        assertEquals(0, DungeonLayoutConstants.blockToCellFloorX(7),
                "positive boundary floor");
        assertEquals(1, DungeonLayoutConstants.blockToCellFloorX(8),
                "positive exact boundary floor");
        assertEquals(-8, DungeonLayoutConstants.cellToBlockX(-1),
                "cell x round trip");
        assertEquals(-8, DungeonLayoutConstants.cellToBlockY(-1),
                "cell y round trip");
        assertEquals(-8, DungeonLayoutConstants.cellToBlockZ(-1),
                "cell z round trip");
        assertThrows(
                () -> DungeonLayoutConstants.blockSizeToCellCountX(0),
                "zero x size rejected"
        );
        assertThrows(
                () -> DungeonLayoutConstants.blockSizeToCellCountZ(-3),
                "negative z size rejected"
        );
    }

    private static void nonSquareRotations() {
        DungeonTemplateGeometry geometry = new DungeonTemplateGeometry(
                13,
                9,
                27
        );

        assertHorizontal(geometry.transformed(DungeonRoomRotation.NONE),
                13, 27, "none");
        assertHorizontal(geometry.transformed(DungeonRoomRotation.CLOCKWISE_90),
                27, 13, "clockwise");
        assertHorizontal(geometry.transformed(DungeonRoomRotation.CLOCKWISE_180),
                13, 27, "half turn");
        assertHorizontal(geometry.transformed(
                        DungeonRoomRotation.COUNTERCLOCKWISE_90),
                27, 13, "counterclockwise");
    }

    private static void exactCollision() {
        DungeonBlockBox first = new DungeonBlockBox(0, 0, 0, 4, 8, 4);
        DungeonBlockBox touching = new DungeonBlockBox(4, 0, 0, 8, 8, 4);
        DungeonBlockBox overlapping = new DungeonBlockBox(3, 0, 0, 7, 8, 4);
        DungeonBlockBox sameCellA = new DungeonBlockBox(0, 0, 0, 2, 4, 2);
        DungeonBlockBox sameCellB = new DungeonBlockBox(2, 0, 0, 4, 4, 2);
        BlockPos origin = BlockPos.ZERO;

        assertFalse(first.intersects(touching), "touching boxes do not overlap");
        assertTrue(first.intersects(overlapping), "one block overlap");
        assertTrue(first.expand(1).intersects(touching), "clearance expansion");
        assertEquals(
                sameCellA.toRoutingCellBox(origin),
                sameCellB.toRoutingCellBox(origin),
                "same coarse routing cell"
        );
        assertFalse(
                sameCellA.intersects(sameCellB),
                "same routing cell can hold non-overlapping exact boxes"
        );

        DungeonBlockBox negative = new DungeonBlockBox(-5, -8, -5, -1, 0, -1);
        assertEquals(-1, negative.toRoutingCellBox(origin).minX(),
                "negative cell coverage x");
        assertEquals(-1, negative.toRoutingCellBox(origin).minY(),
                "negative cell coverage y");
        assertEquals(-1, negative.toRoutingCellBox(origin).minZ(),
                "negative cell coverage z");
    }

    private static void deterministicPieceMetadata() {
        DungeonGraph graph = DungeonGraphGenerator.generate(0x1234ABCDL);
        BlockPos layoutOrigin = new BlockPos(32, 32, -48);
        DungeonLayoutPlan firstLayout =
                DungeonGraphEmbeddingPlanner.embed(graph, layoutOrigin, CATALOG);
        DungeonLayoutPlan secondLayout =
                DungeonGraphEmbeddingPlanner.embed(graph, layoutOrigin, CATALOG);
        DungeonPiecePlan first = DungeonPiecePlanCompiler.compile(
                layoutOrigin,
                firstLayout,
                graph.primaryEntryNodeId(),
                CATALOG
        );
        DungeonPiecePlan second = DungeonPiecePlanCompiler.compile(
                layoutOrigin,
                secondLayout,
                graph.primaryEntryNodeId(),
                CATALOG
        );

        assertEquals(summarize(first.pieces()), summarize(second.pieces()),
                "piece metadata deterministic");
        assertEquals(first.routedCorridors(), second.routedCorridors(),
                "corridor routing deterministic");
    }

    private static List<String> summarize(List<DungeonPieceMetadata> pieces) {
        return pieces.stream()
                .map(piece -> piece.id()
                        + "|"
                        + piece.role()
                        + "|"
                        + piece.templateId().map(Object::toString).orElse("")
                        + "|"
                        + piece.templateOrigin()
                        + "|"
                        + piece.rotation()
                        + "|"
                        + piece.mirror()
                        + "|"
                        + piece.bounds())
                .toList();
    }

    private static void assertCells(
            DungeonTemplateGeometry geometry,
            int x,
            int y,
            int z
    ) {
        assertEquals(x, geometry.routingCellsX(), "routing cells x");
        assertEquals(y, geometry.routingCellsY(), "routing cells y");
        assertEquals(z, geometry.routingCellsZ(), "routing cells z");
    }

    private static void assertHorizontal(
            DungeonTemplateGeometry geometry,
            int x,
            int z,
            String message
    ) {
        assertEquals(x, geometry.sizeX(), message + " x");
        assertEquals(9, geometry.sizeY(), message + " y");
        assertEquals(z, geometry.sizeZ(), message + " z");
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean value, String message) {
        if (value) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(
                    message + " expected=" + expected + " actual=" + actual
            );
        }
    }

    private static void assertThrows(
            Runnable action,
            String message
    ) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }
}
