package io.github.naimjeg.obeliskdepths.worldgen.structure.graph;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutGenerationProfile;
import java.util.List;
import java.util.OptionalInt;

public final class DungeonGraphGeneratorTest {
    private DungeonGraphGeneratorTest() {
    }

    public static void main(String[] args) {
        determinism();
        invariantsAcrossManySeeds();
        expectedProfileBehavior();
        invalidGraphs();
    }

    private static void determinism() {
        for (DungeonLayoutGenerationProfile profile : DungeonLayoutGenerationProfile.values()) {
            long seed = 0x5EED5EEDL;
            DungeonGraph first = DungeonGraphGenerator.generate(seed, profile);
            DungeonGraph second = DungeonGraphGenerator.generate(seed, profile);

            assertEquals(first, second, "same seed/profile should produce identical graph for " + profile);
        }
    }

    private static void invariantsAcrossManySeeds() {
        for (DungeonLayoutGenerationProfile profile : DungeonLayoutGenerationProfile.values()) {
            for (int index = 0; index < 1_000; index++) {
                long seed = index * 0x9E3779B97F4A7C15L;
                DungeonGraph graph = DungeonGraphGenerator.generate(seed, profile);

                DungeonGraphValidator.validate(graph);
                assertEquals(graph.nodes().size() - 1, graph.edges().size(), "tree edge count");
                assertEquals(1L, countType(graph, DungeonRoomType.START), "one START");
                assertEquals(1L, countType(graph, DungeonRoomType.BOSS), "one BOSS");
                assertEquals(1L, countType(graph, DungeonRoomType.EXIT), "one EXIT");
                assertCriticalPathOrdering(graph);

                for (DungeonGraphNode node : graph.nodes()) {
                    if (node.branchCap()) {
                        assertTrue(graph.outgoingEdges(node.id()).isEmpty(), "branch cap terminal: " + node.id());
                    }
                }
            }
        }
    }

    private static void expectedProfileBehavior() {
        for (DungeonLayoutGenerationProfile profile : DungeonLayoutGenerationProfile.values()) {
            int minCritical = Integer.MAX_VALUE;
            int maxCritical = 0;
            int minBranches = Integer.MAX_VALUE;
            int maxBranches = 0;
            int minNodes = Integer.MAX_VALUE;
            int maxNodes = 0;

            for (int index = 0; index < 1_000; index++) {
                DungeonGraph graph = DungeonGraphGenerator.generate(index, profile);
                int critical = graph.criticalPathNodes().size();
                int branches = graph.branchCount();

                minCritical = Math.min(minCritical, critical);
                maxCritical = Math.max(maxCritical, critical);
                minBranches = Math.min(minBranches, branches);
                maxBranches = Math.max(maxBranches, branches);
                minNodes = Math.min(minNodes, graph.nodes().size());
                maxNodes = Math.max(maxNodes, graph.nodes().size());
            }

            assertTrue(minCritical >= profile.minCriticalPathLength(), "critical min for " + profile);
            assertTrue(maxCritical <= profile.maxCriticalPathLength(), "critical max for " + profile);
            assertTrue(minBranches >= 0, "branch min nonnegative for " + profile);
            assertTrue(maxBranches <= profile.maxBranches(), "branch max for " + profile);
            assertTrue(minNodes >= profile.minCriticalPathLength(), "node min for " + profile);
            assertTrue(maxNodes <= profile.maxCriticalPathLength() + profile.maxBranches(), "node max for " + profile);
        }
    }

    private static void invalidGraphs() {
        assertInvalid(
                new DungeonGraph(
                        List.of(
                                critical("start", DungeonRoomType.START, 0),
                                critical("start", DungeonRoomType.COMBAT, 1),
                                critical("boss", DungeonRoomType.BOSS, 2),
                                critical("exit", DungeonRoomType.EXIT, 3)
                        ),
                        List.of(
                                edge("e1", "start", "boss"),
                                edge("e2", "boss", "exit")
                        )
                ),
                "Duplicate graph node id"
        );
        assertInvalid(validGraphWith(edge("missing", "start", "missing")), "missing target node");
        assertInvalid(
                new DungeonGraph(
                        List.of(
                                critical("start", DungeonRoomType.START, 0),
                                branch("a"),
                                critical("boss", DungeonRoomType.BOSS, 1),
                                critical("exit", DungeonRoomType.EXIT, 2)
                        ),
                        List.of(edge("e1", "start", "exit"), edge("e2", "a", "boss"), edge("e3", "boss", "a"))
                ),
                "acyclic"
        );
        assertInvalid(
                new DungeonGraph(
                        List.of(
                                critical("start", DungeonRoomType.START, 0),
                                critical("boss", DungeonRoomType.BOSS, 1),
                                critical("exit", DungeonRoomType.EXIT, 2),
                                branch("treasure")
                        ),
                        List.of(edge("e1", "start", "boss"), edge("e2", "boss", "exit"))
                ),
                "connected"
        );
        assertInvalid(
                new DungeonGraph(
                        List.of(
                                critical("start", DungeonRoomType.START, 0),
                                critical("start_2", DungeonRoomType.START, 1),
                                critical("boss", DungeonRoomType.BOSS, 2),
                                critical("exit", DungeonRoomType.EXIT, 3)
                        ),
                        List.of(edge("e1", "start", "start_2"), edge("e2", "start_2", "boss"), edge("e3", "boss", "exit"))
                ),
                "exactly one START"
        );
        assertInvalid(
                new DungeonGraph(
                        List.of(
                                critical("start", DungeonRoomType.START, 0),
                                branch("treasure"),
                                critical("boss", DungeonRoomType.BOSS, 1),
                                critical("exit", DungeonRoomType.EXIT, 2)
                        ),
                        List.of(edge("e1", "start", "treasure"), edge("e2", "treasure", "boss"), edge("e3", "boss", "exit"))
                ),
                "Branch cap node must have no outgoing edges"
        );
        assertInvalid(
                new DungeonGraph(
                        List.of(
                                critical("start", DungeonRoomType.START, 0),
                                critical("combat_01", DungeonRoomType.COMBAT, 1),
                                critical("boss", DungeonRoomType.BOSS, 2),
                                critical("exit", DungeonRoomType.EXIT, 3)
                        ),
                        List.of(edge("e1", "start", "combat_01"), edge("e2", "start", "boss"), edge("e3", "boss", "exit"))
                ),
                "Critical path continuity"
        );
    }

    private static DungeonGraph validGraphWith(DungeonGraphEdge extraEdge) {
        return new DungeonGraph(
                List.of(
                        critical("start", DungeonRoomType.START, 0),
                        critical("combat_01", DungeonRoomType.COMBAT, 1),
                        critical("boss", DungeonRoomType.BOSS, 2),
                        critical("exit", DungeonRoomType.EXIT, 3)
                ),
                List.of(
                        edge("e1", "start", "combat_01"),
                        edge("e2", "combat_01", "boss"),
                        edge("e3", "boss", "exit"),
                        extraEdge
                )
        );
    }

    private static DungeonGraphNode critical(
            String id,
            DungeonRoomType type,
            int index
    ) {
        return DungeonGraphNode.critical(id, type, index);
    }

    private static DungeonGraphNode branch(String id) {
        return new DungeonGraphNode(id, DungeonRoomType.TREASURE, false, true, OptionalInt.empty());
    }

    private static DungeonGraphEdge edge(
            String id,
            String source,
            String target
    ) {
        return new DungeonGraphEdge(id, source, target);
    }

    private static long countType(
            DungeonGraph graph,
            DungeonRoomType type
    ) {
        return graph.nodes().stream().filter(node -> node.type() == type).count();
    }

    private static void assertCriticalPathOrdering(DungeonGraph graph) {
        List<DungeonGraphNode> critical = graph.criticalPathNodes();

        assertSame(DungeonRoomType.START, critical.get(0).type(), "critical starts at START");
        assertSame(DungeonRoomType.BOSS, critical.get(critical.size() - 2).type(), "BOSS before EXIT");
        assertSame(DungeonRoomType.EXIT, critical.get(critical.size() - 1).type(), "critical ends at EXIT");

        for (int index = 1; index < critical.size() - 2; index++) {
            assertSame(DungeonRoomType.COMBAT, critical.get(index).type(), "critical middle is combat");
        }
    }

    private static void assertInvalid(
            DungeonGraph graph,
            String expectedMessagePart
    ) {
        try {
            DungeonGraphValidator.validate(graph);
        } catch (DungeonGraphValidationException exception) {
            assertTrue(
                    exception.getMessage().contains(expectedMessagePart),
                    "expected validation message to contain '" + expectedMessagePart + "', got '" + exception.getMessage() + "'"
            );
            return;
        }

        throw new AssertionError("Expected invalid graph: " + expectedMessagePart);
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

    private static void assertTrue(
            boolean value,
            String message
    ) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void assertSame(
            Object expected,
            Object actual,
            String message
    ) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
