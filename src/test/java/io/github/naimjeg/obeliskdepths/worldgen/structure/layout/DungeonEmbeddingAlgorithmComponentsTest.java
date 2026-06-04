package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonConnectorSide;
import java.util.List;

public final class DungeonEmbeddingAlgorithmComponentsTest {
    private static final List<DungeonConnectorSide> HORIZONTAL_SIDES = List.of(
            DungeonConnectorSide.NORTH,
            DungeonConnectorSide.EAST,
            DungeonConnectorSide.SOUTH,
            DungeonConnectorSide.WEST
    );

    private DungeonEmbeddingAlgorithmComponentsTest() {
    }

    public static void main(String[] args) {
        testSoftPreferredSideOrdersCandidatesDeterministically();
        testHardPreferredSideRestrictsCandidates();
        testLateralOffsetsAreSymmetricAndDeterministic();
        testInvalidCandidateRequestsFailWithDiagnostics();
    }

    private static void testSoftPreferredSideOrdersCandidatesDeterministically() {
        assertEquals(
                List.of(
                        DungeonConnectorSide.SOUTH,
                        DungeonConnectorSide.NORTH,
                        DungeonConnectorSide.EAST,
                        DungeonConnectorSide.WEST
                ),
                DungeonPlacementCandidateGenerator.sideOrder(
                        HORIZONTAL_SIDES,
                        DungeonConnectorSide.SOUTH,
                        false
                ),
                "Soft preferred side order"
        );

        assertEquals(
                HORIZONTAL_SIDES,
                DungeonPlacementCandidateGenerator.sideOrder(
                        HORIZONTAL_SIDES,
                        null,
                        false
                ),
                "No preferred side order"
        );
    }

    private static void testHardPreferredSideRestrictsCandidates() {
        assertEquals(
                List.of(DungeonConnectorSide.WEST),
                DungeonPlacementCandidateGenerator.sideOrder(
                        HORIZONTAL_SIDES,
                        DungeonConnectorSide.WEST,
                        true
                ),
                "Hard preferred side order"
        );
    }

    private static void testLateralOffsetsAreSymmetricAndDeterministic() {
        assertEquals(
                List.of(0, -1, 1, -2, 2, -3, 3),
                DungeonPlacementCandidateGenerator.lateralOffsets(3),
                "Lateral offset order"
        );
        assertEquals(
                List.of(0),
                DungeonPlacementCandidateGenerator.lateralOffsets(0),
                "Zero lateral offset order"
        );
    }

    private static void testInvalidCandidateRequestsFailWithDiagnostics() {
        assertFailureContains(
                () -> DungeonPlacementCandidateGenerator.sideOrder(
                        HORIZONTAL_SIDES,
                        DungeonConnectorSide.UP,
                        false
                ),
                "Preferred side is not a candidate side: UP",
                "Invalid preferred side"
        );
        assertFailureContains(
                () -> DungeonPlacementCandidateGenerator.lateralOffsets(-1),
                "Maximum lateral offset must not be negative: -1",
                "Invalid lateral offset"
        );
    }

    private static void assertFailureContains(
            Runnable operation,
            String expectedMessage,
            String message
    ) {
        try {
            operation.run();
        } catch (IllegalArgumentException exception) {
            if (!exception.getMessage().contains(expectedMessage)) {
                throw new AssertionError(
                        message
                                + ": expected diagnostic containing '"
                                + expectedMessage
                                + "' but was '"
                                + exception.getMessage()
                                + "'"
                );
            }
            return;
        }
        throw new AssertionError(message + ": expected failure");
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
