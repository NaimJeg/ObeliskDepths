package io.github.naimjeg.obeliskdepths.worldgen.structure.generation;

import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphEdge;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonCellPos;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonGraphEmbeddingPlanner;
import io.github.naimjeg.obeliskdepths.worldgen.structure.test.DungeonProceduralTestSupport;
import io.github.naimjeg.obeliskdepths.worldgen.structure.test.DungeonProceduralTestSupport.AcceptedGenerationPlan;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;

public final class DungeonGenerationPlanTest {
    private static final BlockPos ORIGIN = new BlockPos(304, 32, 1344);

    private DungeonGenerationPlanTest() {
    }

    public static void main(String[] args) {
        deterministicPlan();
        exactEdgesPreserved();
        routesAreContiguousAndNonEmpty();
        canonicalCorridorCellsAreUnique();
        traceSummaryUsesCompiledPiecePlanCounts();
    }

    private static void deterministicPlan() {
        AcceptedGenerationPlan accepted = firstAcceptedPlan();
        DungeonGenerationPlan second = DungeonProceduralTestSupport.generationPlan(
                accepted.graph(),
                accepted.analysis(),
                ORIGIN,
                accepted.seed(),
                accepted.attemptIndex()
        );

        assertEquals(
                signature(accepted.plan()),
                signature(second),
                "same graph seed and attempt should produce identical plan"
        );
    }

    private static void exactEdgesPreserved() {
        AcceptedGenerationPlan accepted = firstAcceptedPlan();
        Set<String> expected = new LinkedHashSet<>();
        for (DungeonGraphEdge edge : accepted.graph().edges()) {
            expected.add(DungeonGraphEmbeddingPlanner.layoutEdgeIdFor(edge));
        }
        Set<String> actual = new LinkedHashSet<>();
        for (RoutedDungeonConnection connection : accepted.plan().connections()) {
            actual.add(connection.id());
        }

        assertEquals(expected, actual, "plan preserves every graph edge id");
    }

    private static void routesAreContiguousAndNonEmpty() {
        DungeonGenerationPlan plan = firstAcceptedPlan().plan();
        for (RoutedDungeonConnection connection : plan.connections()) {
            assertFalse(
                    connection.cells().isEmpty(),
                    "route is non-empty: " + connection.id()
            );
            for (int index = 1; index < connection.cells().size(); index++) {
                DungeonCellPos previous = connection.cells().get(index - 1);
                DungeonCellPos current = connection.cells().get(index);
                int distance = Math.abs(previous.x() - current.x())
                        + Math.abs(previous.y() - current.y())
                        + Math.abs(previous.z() - current.z());
                assertEquals(
                        1,
                        distance,
                        "route is contiguous: " + connection.id()
                );
            }
        }
    }

    private static void canonicalCorridorCellsAreUnique() {
        DungeonGenerationPlan plan = firstAcceptedPlan().plan();
        List<DungeonCorridorCell> cells =
                DungeonCorridorCellResolver.resolve(plan);
        Set<DungeonCellPos> unique = new LinkedHashSet<>();
        for (DungeonCorridorCell cell : cells) {
            assertTrue(
                    unique.add(cell.cell()),
                    "corridor grid emits each occupied cell once: " + cell.cell()
            );
            assertTrue(
                    cell.openingMask() != 0,
                    "corridor cell has an opening mask: " + cell.cell()
            );
        }
        assertTrue(!cells.isEmpty(), "accepted plan has corridor cells");
        assertTrue(
                cells.size() <= plan.routedCellCount(),
                "canonical grid should not duplicate routed cells"
        );
    }

    private static void traceSummaryUsesCompiledPiecePlanCounts() {
        String source;
        try {
            source = Files.readString(Path.of(
                    "src",
                    "main",
                    "java",
                    "io",
                    "github",
                    "naimjeg",
                    "obeliskdepths",
                    "worldgen",
                    "structure",
                    "ObeliskDungeonStructure.java"
            ));
        } catch (IOException exception) {
            throw new AssertionError("Could not read structure source", exception);
        }
        source = source.replace("\r\n", "\n");
        String trace = slice(
                source,
                "private static void tracePlanAttemptSummary",
                "private static void tracePlanRooms"
        );
        String generationAttempt = slice(
                source,
                "DungeonGenerationPlan generationPlan = null;",
                "} catch (RuntimeException exception)"
        );
        String contentCatch = slice(
                source,
                "} catch (DungeonContentException exception)",
                "} catch (DungeonLayoutGenerationException exception)"
        );
        String layoutCatch = slice(
                source,
                "} catch (DungeonLayoutGenerationException exception)",
                "} catch (RuntimeException exception)"
        );

        assertContains(generationAttempt, "DungeonPiecePlan piecePlan = null;",
                "attempt should start with unavailable compiled plan");
        assertContains(generationAttempt, "piecePlan = DungeonPiecePlanCompiler.compile",
                "attempt should assign compiled plan immediately after compilation");
        assertNotContains(generationAttempt, "DungeonPiecePlan piecePlan = DungeonPiecePlanCompiler.compile",
                "compiled plan assignment must not shadow the attempt local");
        assertContains(generationAttempt, "if (!compliance.compliant())",
                "reference-compliance rejection should happen after compilation");
        assertContains(generationAttempt, "piecePlan,",
                "attempt summaries should pass the current compiled plan when available");
        assertContains(contentCatch, "generationPlan,\n                        piecePlan,",
                "content failure after compilation should report compiled counts");
        assertContains(layoutCatch, "generationPlan,\n                        piecePlan,",
                "layout/reference failure after compilation should report compiled counts");
        assertNotContains(generationAttempt, "generationPlan,\n                        null,",
                "catch blocks must not discard an in-scope compiled plan");
        assertContains(trace, "DungeonPiecePlan piecePlan",
                "trace should accept compiled piece plan");
        assertContains(trace, "piecePlan.pieces().size()",
                "trace should report compiled piece count");
        assertContains(trace, "piecePlan.roomCount()",
                "trace should report compiled room pieces");
        assertContains(trace, "piecePlan.corridorCount()",
                "trace should report compiled corridor pieces");
        assertContains(trace, "compiledPieces",
                "trace should include compiled piece field");
        assertContains(trace, "\"<unavailable>\"",
                "trace should mark pre-compilation failures unavailable");
        assertContains(trace, "layoutConnections",
                "trace should keep logical connections distinct");
        assertContains(trace, "proceduralCorridorCells",
                "trace should keep procedural corridor cells distinct");
        assertNotContains(trace, "corridorTemplatePlacements=0",
                "trace must not report fabricated corridor templates");
        assertNotContains(trace, "physicalPieces",
                "trace must not report fabricated physical piece totals");
        assertNotContains(trace, "corridorCells == 0 ? 0 : 1",
                "trace must not collapse corridors to one piece");
    }

    private static AcceptedGenerationPlan firstAcceptedPlan() {
        return DungeonProceduralTestSupport.firstAcceptedGenerationPlan(
                0x5EC70A11D00DFEEDL,
                128,
                ORIGIN,
                "generation-plan invariant sample"
        );
    }

    private static String signature(DungeonGenerationPlan plan) {
        StringBuilder builder = new StringBuilder();
        builder.append("bounds=")
                .append(plan.siteBounds())
                .append(";entry=")
                .append(plan.primaryEntryRoomId());
        for (PlacedDungeonRoom room : plan.rooms()) {
            builder.append(";room=")
                    .append(room.id())
                    .append("@")
                    .append(room.cellOrigin())
                    .append(":")
                    .append(room.templateOrigin())
                    .append(":")
                    .append(room.rotation().getSerializedName())
                    .append(":")
                    .append(room.mirror());
        }
        for (RoutedDungeonConnection connection : plan.connections()) {
            builder.append(";edge=")
                    .append(connection.id())
                    .append(":")
                    .append(connection.kind())
                    .append(":")
                    .append(connection.from())
                    .append("->")
                    .append(connection.to())
                    .append(":")
                    .append(connection.cells());
        }
        return builder.toString();
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

    private static String slice(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        if (startIndex < 0 || endIndex < 0) {
            throw new AssertionError("Could not slice source between " + start + " and " + end);
        }
        return source.substring(startIndex, endIndex);
    }

    private static void assertContains(
            String source,
            String expected,
            String message
    ) {
        if (!source.contains(expected)) {
            throw new AssertionError(message + ": missing '" + expected + "'");
        }
    }

    private static void assertNotContains(
            String source,
            String forbidden,
            String message
    ) {
        if (source.contains(forbidden)) {
            throw new AssertionError(message + ": found '" + forbidden + "'");
        }
    }

}
