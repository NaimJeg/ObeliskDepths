package io.github.naimjeg.obeliskdepths.worldgen.structure.piece;

import java.util.List;

public final class DungeonRouteAlgorithmComponentsTest {
    private DungeonRouteAlgorithmComponentsTest() {
    }

    public static void main(String[] args) {
        testManhattanCandidateOrderIsDeterministic();
        testAxisAlignedPathPreservesEndpointOrder();
        testCombinedRouteDoesNotDuplicateJoinCells();
        testRouteScoringCountsTurnsAndSidePenalty();
        testShortestCandidateUsesStableTieBreakers();
    }

    private static void testManhattanCandidateOrderIsDeterministic() {
        DungeonRouteCell start = new DungeonRouteCell(0, 0);
        DungeonRouteCell goal = new DungeonRouteCell(2, 1);

        List<List<DungeonRouteCell>> candidates =
                DungeonRouteCandidateGenerator.manhattanCandidates(
                        start,
                        goal
                );

        assertEquals(
                List.of(
                        List.of(
                                new DungeonRouteCell(0, 0),
                                new DungeonRouteCell(1, 0),
                                new DungeonRouteCell(2, 0),
                                new DungeonRouteCell(2, 1)
                        ),
                        List.of(
                                new DungeonRouteCell(0, 0),
                                new DungeonRouteCell(0, 1),
                                new DungeonRouteCell(1, 1),
                                new DungeonRouteCell(2, 1)
                        )
                ),
                candidates,
                "Manhattan candidate order"
        );

        List<List<DungeonRouteCell>> straight =
                DungeonRouteCandidateGenerator.manhattanCandidates(
                        start,
                        new DungeonRouteCell(2, 0)
                );
        assertEquals(1, straight.size(), "Straight route deduplicates");
    }

    private static void testAxisAlignedPathPreservesEndpointOrder() {
        assertEquals(
                List.of(
                        new DungeonRouteCell(3, 2),
                        new DungeonRouteCell(2, 2),
                        new DungeonRouteCell(1, 2)
                ),
                DungeonRouteCandidateGenerator.axisAlignedPath(
                        new DungeonRouteCell(3, 2),
                        new DungeonRouteCell(1, 2)
                ),
                "Reverse x-axis path"
        );

        assertEquals(
                List.of(),
                DungeonRouteCandidateGenerator.axisAlignedPath(
                        new DungeonRouteCell(0, 0),
                        new DungeonRouteCell(1, 1)
                ),
                "Non-axis-aligned path"
        );
    }

    private static void testCombinedRouteDoesNotDuplicateJoinCells() {
        List<DungeonRouteCell> combined = DungeonRouteCandidateGenerator.combine(
                List.of(new DungeonRouteCell(0, 0), new DungeonRouteCell(1, 0)),
                List.of(new DungeonRouteCell(1, 0), new DungeonRouteCell(1, 1)),
                List.of(new DungeonRouteCell(1, 1), new DungeonRouteCell(2, 1))
        );

        assertEquals(
                List.of(
                        new DungeonRouteCell(0, 0),
                        new DungeonRouteCell(1, 0),
                        new DungeonRouteCell(1, 1),
                        new DungeonRouteCell(2, 1)
                ),
                combined,
                "Combined route"
        );
    }

    private static void testRouteScoringCountsTurnsAndSidePenalty() {
        List<DungeonRouteCell> path = List.of(
                new DungeonRouteCell(0, 0),
                new DungeonRouteCell(1, 0),
                new DungeonRouteCell(2, 0),
                new DungeonRouteCell(2, 1),
                new DungeonRouteCell(2, 2),
                new DungeonRouteCell(3, 2)
        );

        assertEquals(2, DungeonRouteScorer.countTurns(path), "Turn count");
        assertEquals(
                82,
                DungeonRouteScorer.scorePath(path, 10, 8, 6),
                "Route score"
        );
        assertEquals(
                5,
                DungeonRouteScorer.manhattan(
                        new DungeonRouteCell(-1, 2),
                        new DungeonRouteCell(2, 4)
                ),
                "Manhattan distance"
        );
    }

    private static void testShortestCandidateUsesStableTieBreakers() {
        List<DungeonRouteCell> secondLexicographic = List.of(
                new DungeonRouteCell(1, 0),
                new DungeonRouteCell(1, 1)
        );
        List<DungeonRouteCell> firstLexicographic = List.of(
                new DungeonRouteCell(0, 0),
                new DungeonRouteCell(0, 1)
        );

        assertEquals(
                firstLexicographic,
                DungeonRouteCandidateGenerator.shortest(List.of(
                        secondLexicographic,
                        firstLexicographic
                )),
                "Shortest candidate tie-breaker"
        );
    }

    private static void assertEquals(
            Object expected,
            Object actual,
            String message
    ) {
        if (!expected.equals(actual)) {
            throw new AssertionError(
                    message + ": expected " + expected + " but was " + actual
            );
        }
    }
}
