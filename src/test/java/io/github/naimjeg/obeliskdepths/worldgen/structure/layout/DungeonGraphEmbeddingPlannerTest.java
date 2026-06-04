package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

import io.github.naimjeg.obeliskdepths.dungeon.geometry.*;

import io.github.naimjeg.obeliskdepths.dungeon.content.DevelopmentDungeonContent;
import io.github.naimjeg.obeliskdepths.dungeon.content.DungeonContentSnapshot;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraph;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphAnalysis;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphAnalyzer;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphEdge;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonGraphEdgeKind;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphGenerator;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphNode;
import io.github.naimjeg.obeliskdepths.worldgen.structure.piece.DungeonPieceMetadata;
import io.github.naimjeg.obeliskdepths.worldgen.structure.piece.DungeonPiecePlan;
import io.github.naimjeg.obeliskdepths.worldgen.structure.generation.DungeonGenerationCatalog;
import io.github.naimjeg.obeliskdepths.worldgen.structure.generation.DungeonTemplateGeometryCatalog;
import io.github.naimjeg.obeliskdepths.worldgen.structure.test.DungeonGenerationCatalogTestFixtures;
import io.github.naimjeg.obeliskdepths.worldgen.structure.test.DungeonProceduralTestSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DungeonGraphEmbeddingPlannerTest {
    private static final DungeonGenerationCatalog CATALOG =
            DungeonGenerationCatalogTestFixtures.catalog();

    private DungeonGraphEmbeddingPlannerTest() {
    }

    public static void main(String[] args) {
        deterministicEmbedding();
        radialEmbeddingPlacesBossNearOrigin();
        noRoomOverlap();
        loopEdgesAreEmitted();
        validConnectorDirections();
        bossHubSectorRootsFanOut();
        primaryEntryAnchorInsidePrimaryEntryBounds();
        arbitraryLoadedThemeCatalogCanDriveEmbedding();
    }

    private static void deterministicEmbedding() {
        DungeonGraph graph = DungeonGraphGenerator.generate(12345L);
        BlockPos layoutOrigin = new BlockPos(100, 40, -200);

        DungeonLayoutPlan first = DungeonGraphEmbeddingPlanner.embed(
                graph,
                layoutOrigin,
                CATALOG
        );
        DungeonLayoutPlan second = DungeonGraphEmbeddingPlanner.embed(
                graph,
                layoutOrigin,
                CATALOG
        );

        assertEquals(first, second, "embedding should be deterministic");
    }

    private static void radialEmbeddingPlacesBossNearOrigin() {
        DungeonLayoutPlan plan = representativePlan();
        DungeonLayoutNode boss = plan.requireNode("boss");

        assertTrue(Math.abs(boss.cellOrigin().x()) <= boss.footprint().widthCells(), "boss near origin x");
        assertTrue(Math.abs(boss.cellOrigin().z()) <= boss.footprint().depthCells(), "boss near origin z");
    }

    private static void noRoomOverlap() {
        DungeonLayoutPlan plan = representativePlan();

        for (int i = 0; i < plan.nodes().size(); i++) {
            for (int j = i + 1; j < plan.nodes().size(); j++) {
                assertFalse(
                        plan.nodes().get(i).intersects(plan.nodes().get(j)),
                        "rooms should not overlap: "
                                + plan.nodes().get(i).roomId()
                                + " and "
                                + plan.nodes().get(j).roomId()
                );
            }
        }
    }

    private static void loopEdgesAreEmitted() {
        DungeonLayoutPlan plan = representativePlan();
        long loops = plan.edges().stream()
                .filter(edge -> edge.kind() == DungeonGraphEdgeKind.LOOP)
                .count();

        assertTrue(loops > 0, "loop edges should be emitted to debug layout");
    }

    private static void validConnectorDirections() {
        DungeonSpatialLayoutValidator.validate(representativePlan());
    }

    private static void bossHubSectorRootsFanOut() {
        DungeonGraph graph = bossHubLoopGraph();
        DungeonGraphAnalysis analysis = DungeonGraphAnalyzer.analyze(graph);
        DungeonLayoutPlan layout = DungeonGraphEmbeddingPlanner.embed(
                graph,
                BlockPos.ZERO,
                CATALOG,
                0x5EC70A11L
        );
        Map<String, DungeonLayoutEdge> treeEdgesByChild =
                layout.edges()
                        .stream()
                        .filter(edge -> edge.kind() == DungeonGraphEdgeKind.TREE)
                        .collect(java.util.stream.Collectors.toMap(
                                DungeonLayoutEdge::toRoomId,
                                edge -> edge
                        ));
        DungeonConnectorSide hubIncomingSide =
                treeEdgesByChild.get("boss_hub").toSide();
        List<String> sectorRoots = graph.treeChildren("boss_hub")
                .stream()
                .map(DungeonGraphNode::id)
                .filter(id -> analysis.requireNode(id)
                        .sectorIndex()
                        .isPresent())
                .toList();

        assertEquals(3, sectorRoots.size(), "graph has three sector roots");
        Set<DungeonConnectorSide> sectorRootSides = new LinkedHashSet<>();
        for (String sectorRoot : sectorRoots) {
            DungeonLayoutEdge edge = treeEdgesByChild.get(sectorRoot);
            assertFalse(
                    edge.fromSide() == hubIncomingSide,
                    "sector root must not reuse side facing boss: "
                            + sectorRoot
            );
            assertTrue(
                    sectorRootSides.add(edge.fromSide()),
                    "sector roots use distinct hub sides"
            );
        }
        assertEquals(3, sectorRootSides.size(), "three distinct free sides");

        for (String sectorRoot : sectorRoots) {
            DungeonConnectorSide radialSide =
                    treeEdgesByChild.get(sectorRoot).fromSide();
            for (DungeonGraphNode child : graph.treeChildren(sectorRoot)) {
                DungeonLayoutEdge edge = treeEdgesByChild.get(child.id());
                assertSame(
                        radialSide,
                        edge.fromSide(),
                        "deeper node retains sector radial direction: "
                                + child.id()
                );
            }
        }
    }

    private static void primaryEntryAnchorInsidePrimaryEntryBounds() {
        BlockPos layoutOrigin = new BlockPos(0, 32, 0);
        DungeonGraph graph = DungeonGraphGenerator.generate(42L);
        DungeonPiecePlan piecePlan = firstAcceptedPiecePlan(
                graph,
                layoutOrigin,
                42L
        );
        DungeonPieceMetadata primary = piecePlan.pieces()
                .stream()
                .filter(piece -> piece.id().equals(graph.primaryEntryNodeId()))
                .findFirst()
                .orElseThrow();

        assertTrue(primary.bounds().isInside(primary.anchor()), "primary entry anchor should be inside bounds");
        assertSame(DungeonRoomType.START, primary.role().roomType(), "primary entry is START");
    }

    private static void arbitraryLoadedThemeCatalogCanDriveEmbedding() {
        DungeonContentSnapshot snapshot =
                DevelopmentDungeonContent.createBuiltinSnapshot();
        Identifier arbitraryThemeId = Identifier.fromNamespaceAndPath(
                "obeliskdepths",
                "test/arbitrary_loaded_theme"
        );
        DungeonGenerationCatalog catalog = new DungeonGenerationCatalog(
                arbitraryThemeId,
                snapshot.themes().values().iterator().next(),
                snapshot.rooms(),
                snapshot.corridors(),
                new DungeonTemplateGeometryCatalog(
                        DungeonGenerationCatalogTestFixtures.geometryByTemplate()
                )
        );

        DungeonGraph graph = DungeonGraphGenerator.generate(0xA71E57L);
        DungeonLayoutPlan plan = DungeonGraphEmbeddingPlanner.embed(
                graph,
                new BlockPos(0, 40, 0),
                catalog
        );

        assertFalse(plan.nodes().isEmpty(), "arbitrary theme layout has rooms");
        for (DungeonLayoutNode node : plan.nodes()) {
            assertTrue(
                    catalog.rooms().containsKey(node.definitionId()),
                    "arbitrary theme selected a catalog room: "
                            + node.definitionId()
            );
        }
    }

    private static DungeonLayoutPlan representativePlan() {
        DungeonGraph graph = null;

        for (int seed = 0; seed < 256; seed++) {
            DungeonGraph candidate = DungeonGraphGenerator.generate(0xCAFEF00DL + seed);

            if (DungeonGraphAnalyzer.analyze(candidate).sectors().size() >= 3) {
                graph = candidate;
                break;
            }
        }

        assertTrue(graph != null, "representative graph has radial sectors");
        return DungeonGraphEmbeddingPlanner.embed(
                graph,
                new BlockPos(-64, 20, 128),
                CATALOG
        );
    }

    private static DungeonGraph bossHubLoopGraph() {
        return new DungeonGraph(
                "boss",
                new LinkedHashSet<>(List.of(
                        "sector_a_start",
                        "sector_b_start",
                        "sector_c_start"
                )),
                "sector_a_start",
                List.of(
                        graphNode("boss", DungeonRoomType.BOSS),
                        graphNode("boss_hub", DungeonRoomType.COMBAT),

                        graphNode("sector_a", DungeonRoomType.COMBAT),
                        graphNode("sector_a_outer", DungeonRoomType.COMBAT),
                        graphNode("sector_a_start", DungeonRoomType.START),

                        graphNode("sector_b", DungeonRoomType.COMBAT),
                        graphNode("sector_b_outer", DungeonRoomType.COMBAT),
                        graphNode("sector_b_start", DungeonRoomType.START),

                        graphNode("sector_c", DungeonRoomType.COMBAT),
                        graphNode("sector_c_outer", DungeonRoomType.COMBAT),
                        graphNode("sector_c_start", DungeonRoomType.START)
                ),
                List.of(
                        graphEdge(
                                "tree_boss_hub",
                                "boss",
                                "boss_hub",
                                DungeonGraphEdgeKind.TREE
                        ),

                        graphEdge(
                                "tree_hub_a",
                                "boss_hub",
                                "sector_a",
                                DungeonGraphEdgeKind.TREE
                        ),
                        graphEdge(
                                "tree_a_outer",
                                "sector_a",
                                "sector_a_outer",
                                DungeonGraphEdgeKind.TREE
                        ),
                        graphEdge(
                                "tree_a_start",
                                "sector_a_outer",
                                "sector_a_start",
                                DungeonGraphEdgeKind.TREE
                        ),

                        graphEdge(
                                "tree_hub_b",
                                "boss_hub",
                                "sector_b",
                                DungeonGraphEdgeKind.TREE
                        ),
                        graphEdge(
                                "tree_b_outer",
                                "sector_b",
                                "sector_b_outer",
                                DungeonGraphEdgeKind.TREE
                        ),
                        graphEdge(
                                "tree_b_start",
                                "sector_b_outer",
                                "sector_b_start",
                                DungeonGraphEdgeKind.TREE
                        ),

                        graphEdge(
                                "tree_hub_c",
                                "boss_hub",
                                "sector_c",
                                DungeonGraphEdgeKind.TREE
                        ),
                        graphEdge(
                                "tree_c_outer",
                                "sector_c",
                                "sector_c_outer",
                                DungeonGraphEdgeKind.TREE
                        ),
                        graphEdge(
                                "tree_c_start",
                                "sector_c_outer",
                                "sector_c_start",
                                DungeonGraphEdgeKind.TREE
                        ),

                        graphEdge(
                                "loop_a_b",
                                "sector_a_start",
                                "sector_b_start",
                                DungeonGraphEdgeKind.LOOP
                        )
                )
        );
    }

    private static DungeonGraphNode graphNode(
            String id,
            DungeonRoomType type
    ) {
        return new DungeonGraphNode(id, type);
    }

    private static DungeonGraphEdge graphEdge(
            String id,
            String source,
            String target,
            DungeonGraphEdgeKind kind
    ) {
        return new DungeonGraphEdge(id, source, target, kind);
    }

    private static DungeonPiecePlan firstAcceptedPiecePlan(
            DungeonGraph graph,
            BlockPos layoutOrigin,
            long generationSeed
    ) {
        return DungeonProceduralTestSupport.firstAcceptedProceduralLayout(
                graph,
                layoutOrigin,
                generationSeed,
                "embedding-planner primary-entry piece plan"
        ).pieces();
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

    private static void assertSame(
            Object expected,
            Object actual,
            String message
    ) {
        if (expected != actual) {
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

    private static void assertFalse(
            boolean value,
            String message
    ) {
        if (value) {
            throw new AssertionError(message);
        }
    }
}
