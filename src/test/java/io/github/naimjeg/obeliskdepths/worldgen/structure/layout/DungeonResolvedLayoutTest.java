package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

import io.github.naimjeg.obeliskdepths.dungeon.geometry.*;

import com.mojang.serialization.JsonOps;
import io.github.naimjeg.obeliskdepths.dungeon.layout.AuthoredDungeonConnection;
import io.github.naimjeg.obeliskdepths.dungeon.layout.AuthoredDungeonRoom;
import io.github.naimjeg.obeliskdepths.dungeon.layout.DungeonLayoutDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.room.BuiltinDungeonRoomDefinitions;
import io.github.naimjeg.obeliskdepths.dungeon.room.BuiltinDungeonRooms;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomRotation;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.room.RoomConnectorDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.template.BuiltinDungeonTemplates;
import io.github.naimjeg.obeliskdepths.worldgen.debug.DungeonWorldgenTrace;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraph;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphEdge;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonGraphEdgeKind;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphGenerator;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphNode;
import io.github.naimjeg.obeliskdepths.worldgen.structure.piece.DungeonPieceMetadata;
import io.github.naimjeg.obeliskdepths.worldgen.structure.piece.DungeonPiecePlan;
import io.github.naimjeg.obeliskdepths.worldgen.structure.piece.DungeonPiecePlanCompiler;
import io.github.naimjeg.obeliskdepths.worldgen.structure.generation.DungeonGenerationCatalog;
import io.github.naimjeg.obeliskdepths.worldgen.structure.test.DungeonGenerationCatalogTestFixtures;
import io.github.naimjeg.obeliskdepths.worldgen.structure.test.DungeonProceduralTestSupport;
import io.github.naimjeg.obeliskdepths.worldgen.structure.test.DungeonProceduralTestSupport.AcceptedProceduralLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class DungeonResolvedLayoutTest {
    private static final Identifier TEST_DEFINITION =
            Identifier.fromNamespaceAndPath("obeliskdepths", "test/non_square");
    private static final Identifier TEST_TEMPLATE =
            Identifier.fromNamespaceAndPath(
                    "obeliskdepths",
                    "dungeon/test/non_square"
            );
    private static final Identifier CONNECTOR_TYPE =
            BuiltinDungeonRoomDefinitions.BASIC_FLOOR_PASSAGE_CONNECTOR;
    private static final DungeonWorldgenTrace.Context TRACE =
            DungeonWorldgenTrace.Context.disabled(null);
    private static final DungeonGenerationCatalog CATALOG =
            DungeonGenerationCatalogTestFixtures.catalog();

    private DungeonResolvedLayoutTest() {
    }

    public static void main(String[] args) throws Exception {
        transformsPortsAndOpenings();
        explicitFootprintRemainsIrregular();
        proceduralPortAssignmentUsesDistinctPorts();
        impossibleProceduralPortAssignmentFails();
        authoredLayoutCodecRoundTrip();
        unknownAuthoredPortFails();
        authoredPathMustBeContiguous();
        authoredPathIsNotRerouted();
        generatedPathStartsAndEndsAtPorts();
        compilerPreservesResolvedRoomOrigins();
        routeThroughUnrelatedRoomFails();
        overlappingRoutesFail();
        loopRoutingFailureDoesNotDropEdge();
        generatedProceduralLayoutPreservesLoopEdge();
        topologyValidatorRejectsRewiredLoopEdge();
        topologyValidatorRejectsRewiredTreeEdge();
        topologyValidatorRejectsKindSwapWithPreservedCounts();
        topologyValidatorRejectsExchangedEdgeIds();
        topologyValidatorRejectsDuplicatedLayoutEdgeAndOmittedEdge();
        topologyValidatorRejectsResolvedWrongKind();
        topologyValidatorRejectsResolvedWrongEndpoints();
        topologyValidatorAcceptsValidChain();
        compilerCollisionValidationDoesNotParsePieceIds();
        deterministicResolvedLayoutAndPiecePlan();
        builtinGreatSwampResolvesAndCompiles();
    }

    private static void transformsPortsAndOpenings() {
        DungeonRoomDefinition definition = nonSquareDefinition(
                DungeonRoomFootprint.auto(),
                List.of(port(
                        "east",
                        new DungeonCellPos(1, 0, 1),
                        new BlockPos(12, 1, 10),
                        DungeonConnectorSide.EAST
                ))
        );
        ResolvedDungeonRoom room = DungeonLayoutResolver.resolveRoomForTests(
                "room",
                TEST_DEFINITION,
                definition,
                new DungeonCellPos(0, 0, 0),
                DungeonRoomRotation.CLOCKWISE_90,
                false,
                new DungeonTemplateGeometry(13, 9, 27),
                BlockPos.ZERO
        );
        ResolvedDungeonPort port = room.requirePort("east");

        assertSame(DungeonConnectorSide.SOUTH, port.facing(),
                "east port rotates to south");
        assertEquals(new DungeonCellPos(2, 0, 1), port.boundaryCell(),
                "boundary cell rotates in routing space");
        assertEquals(new BlockPos(13, 1, 12), port.openingMin(),
                "opening minimum rotates in exact block space");
    }

    private static void explicitFootprintRemainsIrregular() {
        DungeonRoomFootprint mask = DungeonRoomFootprint.fromLayers(List.of(
                List.of("##.", "#..", "###")
        ));
        DungeonRoomDefinition definition = nonSquareDefinition(
                mask,
                List.of(port(
                        "north",
                        new DungeonCellPos(0, 0, 0),
                        new BlockPos(1, 1, 0),
                        DungeonConnectorSide.NORTH
                ))
        );
        ResolvedDungeonRoom room = DungeonLayoutResolver.resolveRoomForTests(
                "irregular",
                TEST_DEFINITION,
                definition,
                new DungeonCellPos(0, 0, 0),
                DungeonRoomRotation.CLOCKWISE_90,
                false,
                new DungeonTemplateGeometry(13, 9, 27),
                BlockPos.ZERO
        );

        assertEquals(mask.occupiedCells().size(),
                room.transformedFootprint().occupiedCells().size(),
                "rotation preserves explicit mask cell count");
        assertFalse(room.transformedFootprint().isRectangular(),
                "explicit mask must not become a rectangle");
    }

    private static void proceduralPortAssignmentUsesDistinctPorts() {
        DungeonLayoutPlan plan = new DungeonLayoutPlan(
                DungeonLayoutConstants.CELL_SIZE,
                List.of(
                        node("start", DungeonRoomType.START,
                                new DungeonCellPos(0, 0, 0),
                                DungeonConnectorSide.NORTH,
                                DungeonConnectorSide.EAST),
                        node("north", DungeonRoomType.COMBAT,
                                new DungeonCellPos(0, 0, -6),
                                DungeonConnectorSide.SOUTH),
                        node("east", DungeonRoomType.COMBAT,
                                new DungeonCellPos(6, 0, 0),
                                DungeonConnectorSide.WEST)
                ),
                List.of(
                        edge("edge_north", "start", "north",
                                DungeonConnectorSide.NORTH,
                                DungeonConnectorSide.SOUTH),
                        edge("edge_east", "start", "east",
                                DungeonConnectorSide.EAST,
                                DungeonConnectorSide.WEST)
                )
        );
        ResolvedDungeonLayout resolved =
                DungeonLayoutResolver.resolveProcedural(
                        BlockPos.ZERO,
                        plan,
                        "start",
                        CATALOG,
                        TRACE
                );

        assertFalse(
                resolved.connections().get(0).from().portId()
                        .equals(resolved.connections().get(1).from().portId()),
                "procedural assignment uses distinct source ports"
        );
    }

    private static void impossibleProceduralPortAssignmentFails() {
        DungeonLayoutPlan plan = new DungeonLayoutPlan(
                DungeonLayoutConstants.CELL_SIZE,
                List.of(
                        node("boss", DungeonRoomType.BOSS,
                                new DungeonCellPos(0, 0, 0),
                                DungeonConnectorSide.EAST,
                                DungeonConnectorSide.WEST),
                        node("a", DungeonRoomType.COMBAT,
                                new DungeonCellPos(16, 0, 0),
                                DungeonConnectorSide.WEST),
                        node("b", DungeonRoomType.COMBAT,
                                new DungeonCellPos(-8, 0, 0),
                                DungeonConnectorSide.EAST)
                ),
                List.of(
                        edge("edge_a", "boss", "a",
                                DungeonConnectorSide.EAST,
                                DungeonConnectorSide.WEST),
                        edge("edge_b", "boss", "b",
                                DungeonConnectorSide.WEST,
                                DungeonConnectorSide.EAST)
                )
        );

        assertThrows(
                DungeonLayoutGenerationException.class,
                "Cannot resolve exact-facing ports",
                () -> DungeonLayoutResolver.resolveProcedural(
                        BlockPos.ZERO,
                        plan,
                        "boss",
                        CATALOG,
                        TRACE
                ),
                "one-port boss cannot satisfy two distinct graph edges"
        );
    }

    private static void authoredLayoutCodecRoundTrip() {
        DungeonLayoutDefinition layout = simpleAuthoredLayout(List.of());
        var encoded = DungeonLayoutDefinition.CODEC
                .encodeStart(JsonOps.INSTANCE, layout)
                .getOrThrow();
        DungeonLayoutDefinition decoded = DungeonLayoutDefinition.CODEC
                .parse(JsonOps.INSTANCE, encoded)
                .getOrThrow();

        assertEquals(layout, decoded, "authored layout codec round trip");
    }

    private static void unknownAuthoredPortFails() {
        DungeonLayoutDefinition layout = new DungeonLayoutDefinition(
                "start",
                simpleRooms(),
                List.of(new AuthoredDungeonConnection(
                        "bad",
                        new DungeonPortReference("start", "missing"),
                        new DungeonPortReference("combat", "west"),
                        DungeonGraphEdgeKind.TREE,
                        List.of()
                ))
        );

        assertThrows(
                IllegalArgumentException.class,
                "unknown port",
                () -> DungeonLayoutResolver.resolveAuthored(
                        BlockPos.ZERO,
                        layout,
                        CATALOG,
                        TRACE
                ),
                "unknown authored port should fail"
        );
    }

    private static void authoredPathMustBeContiguous() {
        assertThrows(
                IllegalArgumentException.class,
                "path is not contiguous",
                () -> new AuthoredDungeonConnection(
                        "bad_path",
                        new DungeonPortReference("start", "east"),
                        new DungeonPortReference("combat", "west"),
                        DungeonGraphEdgeKind.TREE,
                        List.of(
                                new DungeonCellPos(2, 0, 0),
                                new DungeonCellPos(4, 0, 0)
                        )
                ),
                "noncontiguous authored paths are rejected"
        );
    }

    private static void authoredPathIsNotRerouted() {
        List<DungeonCellPos> path = List.of(
                new DungeonCellPos(1, 0, 0),
                new DungeonCellPos(2, 0, 0),
                new DungeonCellPos(3, 0, 0)
        );
        ResolvedDungeonLayout resolved = DungeonLayoutResolver.resolveAuthored(
                BlockPos.ZERO,
                simpleAuthoredLayout(path),
                CATALOG,
                TRACE
        );

        assertEquals(path, resolved.connections().getFirst().routeCells(),
                "authored path remains authoritative");
    }

    private static void generatedPathStartsAndEndsAtPorts() {
        ResolvedDungeonLayout resolved = DungeonLayoutResolver.resolveAuthored(
                BlockPos.ZERO,
                simpleAuthoredLayout(List.of()),
                CATALOG,
                TRACE
        );
        ResolvedDungeonConnection connection =
                resolved.connections().getFirst();
        ResolvedDungeonRoom fromRoom =
                resolved.requireRoom(connection.from().roomId());
        ResolvedDungeonRoom toRoom = resolved.requireRoom(connection.to().roomId());

        assertEquals(
                fromRoom.requirePort(connection.from().portId())
                        .outsideCell(fromRoom.cellOrigin()),
                connection.routeCells().getFirst(),
                "generated route starts at assigned source port"
        );
        assertEquals(
                toRoom.requirePort(connection.to().portId())
                        .outsideCell(toRoom.cellOrigin()),
                connection.routeCells().getLast(),
                "generated route ends at assigned target port"
        );
    }

    private static void compilerPreservesResolvedRoomOrigins() {
        ResolvedDungeonLayout resolved = DungeonLayoutResolver.resolveAuthored(
                BlockPos.ZERO,
                simpleAuthoredLayout(List.of()),
                CATALOG,
                TRACE
        );
        DungeonPiecePlan plan = DungeonPiecePlanCompiler.compile(
                resolved,
                null,
                TRACE
        );

        for (ResolvedDungeonRoom room : resolved.rooms()) {
            DungeonPieceMetadata piece = plan.pieces()
                    .stream()
                    .filter(candidate -> candidate.id().equals(room.roomId()))
                    .findFirst()
                    .orElseThrow();
            assertEquals(room.templateOrigin(), piece.templateOrigin(),
                    "compiler preserves resolved room origin");
            assertEquals(room.rotation(), piece.rotation(),
                    "compiler preserves resolved room rotation");
            assertEquals(room.mirrored(), piece.mirror(),
                    "compiler preserves resolved room mirror");
        }
    }

    private static void routeThroughUnrelatedRoomFails() {
        DungeonLayoutDefinition layout = new DungeonLayoutDefinition(
                "start",
                List.of(
                        new AuthoredDungeonRoom(
                                "start",
                                BuiltinDungeonRooms.GREAT_SWAMP_START_OPEN_PAVILION,
                                new DungeonCellPos(0, 0, 0),
                                DungeonRoomRotation.NONE,
                                false
                        ),
                        new AuthoredDungeonRoom(
                                "middle",
                                BuiltinDungeonRooms.GREAT_SWAMP_COMBAT_OPEN_PAVILION,
                                new DungeonCellPos(2, 0, 0),
                                DungeonRoomRotation.NONE,
                                false
                        ),
                        new AuthoredDungeonRoom(
                                "combat",
                                BuiltinDungeonRooms.GREAT_SWAMP_COMBAT_OPEN_PAVILION,
                                new DungeonCellPos(6, 0, 0),
                                DungeonRoomRotation.NONE,
                                false
                        )
                ),
                List.of(new AuthoredDungeonConnection(
                        "blocked",
                        new DungeonPortReference("start", "east"),
                        new DungeonPortReference("combat", "west"),
                        DungeonGraphEdgeKind.TREE,
                        List.of(
                                new DungeonCellPos(1, 0, 0),
                                new DungeonCellPos(2, 0, 0),
                                new DungeonCellPos(3, 0, 0),
                                new DungeonCellPos(4, 0, 0),
                                new DungeonCellPos(5, 0, 0)
                        )
                ))
        );

        assertThrows(
                DungeonLayoutGenerationException.class,
                "obstructed or overlaps",
                () -> DungeonLayoutResolver.resolveAuthored(
                        BlockPos.ZERO,
                        layout,
                        CATALOG,
                        TRACE
                ),
                "authored route through unrelated room footprint should fail"
        );
    }

    private static void overlappingRoutesFail() {
        DungeonLayoutDefinition layout = new DungeonLayoutDefinition(
                "start",
                List.of(
                        new AuthoredDungeonRoom(
                                "start",
                                BuiltinDungeonRooms.GREAT_SWAMP_START_OPEN_PAVILION,
                                new DungeonCellPos(0, 0, 0),
                                DungeonRoomRotation.NONE,
                                false
                        ),
                        new AuthoredDungeonRoom(
                                "a",
                                BuiltinDungeonRooms.GREAT_SWAMP_COMBAT_OPEN_PAVILION,
                                new DungeonCellPos(4, 0, 0),
                                DungeonRoomRotation.NONE,
                                false
                        ),
                        new AuthoredDungeonRoom(
                                "b",
                                BuiltinDungeonRooms.GREAT_SWAMP_COMBAT_OPEN_PAVILION,
                                new DungeonCellPos(4, 0, 2),
                                DungeonRoomRotation.CLOCKWISE_90,
                                false
                        )
                ),
                List.of(
                        new AuthoredDungeonConnection(
                                "one",
                                new DungeonPortReference("start", "east"),
                                new DungeonPortReference("a", "west"),
                                DungeonGraphEdgeKind.TREE,
                                List.of(
                                        new DungeonCellPos(1, 0, 0),
                                        new DungeonCellPos(2, 0, 0),
                                        new DungeonCellPos(3, 0, 0)
                                )
                        ),
                        new AuthoredDungeonConnection(
                                "two",
                                new DungeonPortReference("start", "south"),
                                new DungeonPortReference("b", "west"),
                                DungeonGraphEdgeKind.TREE,
                                List.of(
                                        new DungeonCellPos(0, 0, 1),
                                        new DungeonCellPos(1, 0, 1),
                                        new DungeonCellPos(2, 0, 1),
                                        new DungeonCellPos(2, 0, 0),
                                        new DungeonCellPos(3, 0, 0),
                                        new DungeonCellPos(4, 0, 0),
                                        new DungeonCellPos(4, 0, 1)
                                )
                        )
                )
        );

        assertThrows(
                DungeonLayoutGenerationException.class,
                "obstructed or overlaps",
                () -> DungeonLayoutResolver.resolveAuthored(
                        BlockPos.ZERO,
                        layout,
                        CATALOG,
                        TRACE
                ),
                "overlapping authored corridor routes should fail"
        );
    }

    private static void loopRoutingFailureDoesNotDropEdge() {
        DungeonLayoutDefinition blockedLoop = new DungeonLayoutDefinition(
                "start",
                List.of(
                        new AuthoredDungeonRoom(
                                "start",
                                BuiltinDungeonRooms.GREAT_SWAMP_START_OPEN_PAVILION,
                                new DungeonCellPos(0, 0, 0),
                                DungeonRoomRotation.NONE,
                                false
                        ),
                        new AuthoredDungeonRoom(
                                "middle",
                                BuiltinDungeonRooms.GREAT_SWAMP_COMBAT_OPEN_PAVILION,
                                new DungeonCellPos(2, 0, 0),
                                DungeonRoomRotation.NONE,
                                false
                        ),
                        new AuthoredDungeonRoom(
                                "combat",
                                BuiltinDungeonRooms.GREAT_SWAMP_COMBAT_OPEN_PAVILION,
                                new DungeonCellPos(6, 0, 0),
                                DungeonRoomRotation.NONE,
                                false
                        )
                ),
                List.of(new AuthoredDungeonConnection(
                        "blocked_loop",
                        new DungeonPortReference("start", "east"),
                        new DungeonPortReference("combat", "west"),
                        DungeonGraphEdgeKind.LOOP,
                        List.of(
                                new DungeonCellPos(1, 0, 0),
                                new DungeonCellPos(2, 0, 0),
                                new DungeonCellPos(3, 0, 0),
                                new DungeonCellPos(4, 0, 0),
                                new DungeonCellPos(5, 0, 0)
                        )
                ))
        );

        assertThrows(
                DungeonLayoutGenerationException.class,
                "obstructed or overlaps",
                () -> DungeonLayoutResolver.resolveAuthored(
                        BlockPos.ZERO,
                        blockedLoop,
                        CATALOG,
                        TRACE
                ),
                "unroutable LOOP must fail instead of being dropped"
        );

        DungeonLayoutDefinition openLoop = new DungeonLayoutDefinition(
                "start",
                simpleRooms(),
                List.of(new AuthoredDungeonConnection(
                        "open_loop",
                        new DungeonPortReference("start", "east"),
                        new DungeonPortReference("combat", "west"),
                        DungeonGraphEdgeKind.LOOP,
                        List.of()
                ))
        );
        ResolvedDungeonLayout resolved = DungeonLayoutResolver.resolveAuthored(
                BlockPos.ZERO,
                openLoop,
                CATALOG,
                TRACE
        );

        assertEquals(1, resolved.connections().size(),
                "successful retry keeps loop connection");
        assertEquals("open_loop", resolved.connections().getFirst().id(),
                "loop id survives retry");
        assertSame(DungeonGraphEdgeKind.LOOP,
                resolved.connections().getFirst().kind(),
                "loop kind survives retry");
        assertTrue(
                !resolved.connections().getFirst().routeCells().isEmpty(),
                "loop route is non-empty after retry"
        );
    }

    private static void generatedProceduralLayoutPreservesLoopEdge() {
        DungeonGraph graph = bossHubLoopGraph();
        AcceptedProceduralLayout accepted = firstAcceptedProceduralLayout(
                graph,
                new BlockPos(96, 32, -128),
                0x5EC70A11L
        );
        Set<String> expectedIds = accepted.layout()
                .edges()
                .stream()
                .map(DungeonLayoutEdge::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> actualIds = accepted.resolved()
                .connections()
                .stream()
                .map(ResolvedDungeonConnection::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        assertTrue(graph.loopEdges().stream().anyMatch(edge -> edge.id().equals("loop_a_b")),
                "test graph contains a loop");
        assertTrue(expectedIds.contains("corridor_loop_a_b"),
                "layout contains normalized loop edge id");
        assertEquals(expectedIds, actualIds,
                "resolved connection IDs exactly match layout edge IDs");
        assertTrue(
                resolvedCycleRank(accepted.resolved())
                        >= graphCycleRank(graph),
                "resolved cycle rank preserves graph cycle rank"
        );
        for (ResolvedDungeonConnection connection : accepted.resolved()
                .connections()) {
            assertTrue(!connection.routeCells().isEmpty(),
                    "resolved route is non-empty: " + connection.id());
            assertTrue(contiguous(connection.routeCells()),
                    "resolved route is contiguous: " + connection.id());
        }
    }

    private static void topologyValidatorRejectsRewiredLoopEdge() {
        TopologyFixture fixture = topologyFixture();
        DungeonLayoutEdge loop = requireLayoutEdge(
                fixture.accepted().layout(),
                "corridor_loop_a_b"
        );
        DungeonLayoutEdge tree = fixture.accepted().layout().edges()
                .stream()
                .filter(edge -> edge.kind() == DungeonGraphEdgeKind.TREE)
                .findFirst()
                .orElseThrow();
        DungeonLayoutPlan rewired = replaceLayoutEdge(
                fixture.accepted().layout(),
                loop.id(),
                copyEdgeWithEndpoints(loop, tree.fromRoomId(), tree.toRoomId())
        );

        assertTopologyFails(fixture.graph(), rewired, fixture.accepted().resolved(),
                "rewired loop edge must fail exact graph-to-layout validation");
    }

    private static void topologyValidatorRejectsRewiredTreeEdge() {
        TopologyFixture fixture = topologyFixture();
        DungeonLayoutEdge tree = fixture.accepted().layout().edges()
                .stream()
                .filter(edge -> edge.kind() == DungeonGraphEdgeKind.TREE)
                .findFirst()
                .orElseThrow();
        DungeonLayoutEdge loop = requireLayoutEdge(
                fixture.accepted().layout(),
                "corridor_loop_a_b"
        );
        DungeonLayoutPlan rewired = replaceLayoutEdge(
                fixture.accepted().layout(),
                tree.id(),
                copyEdgeWithEndpoints(tree, loop.fromRoomId(), loop.toRoomId())
        );

        assertTopologyFails(fixture.graph(), rewired, fixture.accepted().resolved(),
                "rewired tree edge must fail exact graph-to-layout validation");
    }

    private static void topologyValidatorRejectsKindSwapWithPreservedCounts() {
        TopologyFixture fixture = topologyFixture();
        DungeonLayoutEdge loop = requireLayoutEdge(
                fixture.accepted().layout(),
                "corridor_loop_a_b"
        );
        DungeonLayoutEdge tree = fixture.accepted().layout().edges()
                .stream()
                .filter(edge -> edge.kind() == DungeonGraphEdgeKind.TREE)
                .findFirst()
                .orElseThrow();
        DungeonLayoutPlan swapped = replaceLayoutEdges(
                fixture.accepted().layout(),
                List.of(
                        copyEdgeWithKind(loop, DungeonGraphEdgeKind.TREE),
                        copyEdgeWithKind(tree, DungeonGraphEdgeKind.LOOP)
                )
        );

        assertTopologyFails(fixture.graph(), swapped, fixture.accepted().resolved(),
                "kind swap with preserved counts must fail exact validation");
    }

    private static void topologyValidatorRejectsExchangedEdgeIds() {
        TopologyFixture fixture = topologyFixture();
        DungeonLayoutEdge first = fixture.accepted().layout().edges().get(0);
        DungeonLayoutEdge second = fixture.accepted().layout().edges().get(1);
        DungeonLayoutPlan swapped = replaceLayoutEdgesByOriginal(
                fixture.accepted().layout(),
                List.of(
                        new EdgeReplacement(first.id(),
                                copyEdgeWithId(first, second.id())),
                        new EdgeReplacement(second.id(),
                                copyEdgeWithId(second, first.id()))
                )
        );

        assertTopologyFails(fixture.graph(), swapped, fixture.accepted().resolved(),
                "exchanged edge IDs must fail exact validation");
    }

    private static void topologyValidatorRejectsDuplicatedLayoutEdgeAndOmittedEdge() {
        TopologyFixture fixture = topologyFixture();
        DungeonLayoutEdge first = fixture.accepted().layout().edges().get(0);
        DungeonLayoutEdge second = fixture.accepted().layout().edges().get(1);
        DungeonLayoutPlan duplicated = replaceLayoutEdge(
                fixture.accepted().layout(),
                second.id(),
                new DungeonLayoutEdge(
                        second.id(),
                        first.fromRoomId(),
                        first.toRoomId(),
                        first.fromSide(),
                        first.toSide(),
                        first.widthCells(),
                        first.kind(),
                        first.plannedPath()
                )
        );

        assertTopologyFails(fixture.graph(), duplicated,
                fixture.accepted().resolved(),
                "duplicated layout edge with omitted topology must fail");
    }

    private static void topologyValidatorRejectsResolvedWrongKind() {
        TopologyFixture fixture = topologyFixture();
        ResolvedDungeonConnection loop = requireResolvedConnection(
                fixture.accepted().resolved(),
                "corridor_loop_a_b"
        );
        ResolvedDungeonLayout resolved = fixture.accepted().resolved()
                .withConnections(replaceResolvedConnection(
                        fixture.accepted().resolved().connections(),
                        loop.id(),
                        copyConnectionWithKind(loop, DungeonGraphEdgeKind.TREE)
                ));

        assertTopologyFails(fixture.graph(), fixture.accepted().layout(), resolved,
                "resolved connection with wrong kind must fail");
    }

    private static void topologyValidatorRejectsResolvedWrongEndpoints() {
        TopologyFixture fixture = topologyFixture();
        ResolvedDungeonConnection loop = requireResolvedConnection(
                fixture.accepted().resolved(),
                "corridor_loop_a_b"
        );
        ResolvedDungeonConnection tree = fixture.accepted().resolved()
                .connections()
                .stream()
                .filter(connection -> connection.kind() == DungeonGraphEdgeKind.TREE)
                .findFirst()
                .orElseThrow();
        ResolvedDungeonLayout resolved = fixture.accepted().resolved()
                .withConnections(replaceResolvedConnection(
                        fixture.accepted().resolved().connections(),
                        loop.id(),
                        new ResolvedDungeonConnection(
                                loop.id(),
                                tree.from(),
                                tree.to(),
                                loop.kind(),
                                tree.routeCells()
                        )
                ));

        assertTopologyFails(fixture.graph(), fixture.accepted().layout(), resolved,
                "resolved connection with wrong endpoints must fail");
    }

    private static void topologyValidatorAcceptsValidChain() {
        TopologyFixture fixture = topologyFixture();
        DungeonResolvedTopologyValidator.validateProcedural(
                fixture.graph(),
                fixture.accepted().layout(),
                fixture.accepted().resolved(),
                1
        );
    }

    private static void compilerCollisionValidationDoesNotParsePieceIds()
            throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/io/github/naimjeg/obeliskdepths/worldgen/structure/piece/DungeonPiecePlanCompiler.java"
        ));
        assertFalse(source.contains("corridorPrefix("),
                "collision ownership must not parse corridor piece IDs");
        assertFalse(source.contains("corridorTouchesAssociatedRoom"),
                "corridor-room ownership must use explicit provenance");
        assertFalse(source.contains("prefix.contains"),
                "room ID substring matching must not drive collision legality");
        assertTrue(source.contains("endpointBoundsByRoom"),
                "collision legality uses explicit endpoint bounds");
    }

    private static void deterministicResolvedLayoutAndPiecePlan() {
        long seed = 0x9876ABCDEFL;
        DungeonGraph graph = DungeonGraphGenerator.generate(seed);
        BlockPos origin = new BlockPos(64, 32, -96);
        AcceptedProceduralLayout first = firstAcceptedProceduralLayout(
                graph,
                origin,
                seed
        );
        AcceptedProceduralLayout second = acceptedProceduralLayout(
                graph,
                origin,
                seed,
                first.attemptIndex()
        );

        assertEquals(first.resolved(), second.resolved(),
                "resolved layout is deterministic");
        assertEquals(pieceSummary(first.pieces()), pieceSummary(second.pieces()),
                "resolved piece plan is deterministic");
    }

    private static void builtinGreatSwampResolvesAndCompiles() {
        long seed = 0x1234ABCDL;
        DungeonGraph graph = DungeonGraphGenerator.generate(seed);
        BlockPos origin = new BlockPos(32, 32, -48);
        DungeonPiecePlan pieces = firstAcceptedProceduralLayout(
                graph,
                origin,
                seed
        ).pieces();

        assertTrue(pieces.roomCount() > 0, "built-in room pieces compile");
        assertTrue(pieces.corridorCount() > 0,
                "built-in corridor pieces compile");
    }

    private static DungeonLayoutDefinition simpleAuthoredLayout(
            List<DungeonCellPos> path
    ) {
        return new DungeonLayoutDefinition(
                "start",
                simpleRooms(),
                List.of(new AuthoredDungeonConnection(
                        "start_to_combat",
                        new DungeonPortReference("start", "east"),
                        new DungeonPortReference("combat", "west"),
                        DungeonGraphEdgeKind.TREE,
                        path
                ))
        );
    }

    private static List<AuthoredDungeonRoom> simpleRooms() {
        return List.of(
                new AuthoredDungeonRoom(
                        "start",
                        BuiltinDungeonRooms.GREAT_SWAMP_START_OPEN_PAVILION,
                        new DungeonCellPos(0, 0, 0),
                        DungeonRoomRotation.NONE,
                        false
                ),
                new AuthoredDungeonRoom(
                        "combat",
                        BuiltinDungeonRooms.GREAT_SWAMP_COMBAT_OPEN_PAVILION,
                        new DungeonCellPos(4, 0, 0),
                        DungeonRoomRotation.NONE,
                        false
                )
        );
    }

    private static DungeonLayoutNode node(
            String id,
            DungeonRoomType type,
            DungeonCellPos origin,
            DungeonConnectorSide first,
            DungeonConnectorSide... rest
    ) {
        EnumSet<DungeonConnectorSide> sides = EnumSet.of(first, rest);
        return new DungeonLayoutNode(
                id,
                type,
                definitionIdFor(type),
                origin,
                DungeonRoomFootprint.rectangular(2, 2, 2),
                sides
        );
    }

    private static Identifier definitionIdFor(DungeonRoomType type) {
        return switch (type) {
            case START -> BuiltinDungeonRooms.GREAT_SWAMP_START_OPEN_PAVILION;
            case COMBAT -> BuiltinDungeonRooms.GREAT_SWAMP_COMBAT_OPEN_PAVILION;
            case TREASURE ->
                    BuiltinDungeonRooms.GREAT_SWAMP_TREASURE_OBELISK_SANCTUM;
            case BOSS -> BuiltinDungeonRooms.GREAT_SWAMP_BOSS_ALTAR;
        };
    }

    private static DungeonLayoutEdge edge(
            String id,
            String from,
            String to,
            DungeonConnectorSide fromSide,
            DungeonConnectorSide toSide
    ) {
        return new DungeonLayoutEdge(
                id,
                from,
                to,
                fromSide,
                toSide,
                1,
                DungeonGraphEdgeKind.TREE,
                List.of()
        );
    }

    private static DungeonRoomDefinition nonSquareDefinition(
            DungeonRoomFootprint footprint,
            List<RoomConnectorDefinition> ports
    ) {
        return new DungeonRoomDefinition(
                TEST_TEMPLATE,
                DungeonRoomType.COMBAT,
                footprint,
                BlockPos.ZERO,
                new BlockPos(6, 1, 13),
                ports,
                List.of(
                        DungeonRoomRotation.NONE,
                        DungeonRoomRotation.CLOCKWISE_90,
                        DungeonRoomRotation.CLOCKWISE_180,
                        DungeonRoomRotation.COUNTERCLOCKWISE_90
                ),
                false,
                Optional.empty(),
                Optional.empty(),
                0,
                Integer.MAX_VALUE,
                true,
                true,
                false
        );
    }

    private static RoomConnectorDefinition port(
            String id,
            DungeonCellPos boundaryCell,
            BlockPos openingMin,
            DungeonConnectorSide facing
    ) {
        return new RoomConnectorDefinition(
                id,
                boundaryCell,
                openingMin,
                facing,
                CONNECTOR_TYPE,
                4,
                4,
                true
        );
    }

    private static List<String> pieceSummary(DungeonPiecePlan plan) {
        return plan.pieces()
                .stream()
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

    private static int graphCycleRank(DungeonGraph graph) {
        return DungeonResolvedTopologyValidator.cycleRank(
                graph.nodes().size(),
                graph.edges().size(),
                1
        );
    }

    private static int resolvedCycleRank(ResolvedDungeonLayout resolved) {
        return DungeonResolvedTopologyValidator.cycleRank(
                resolved.rooms().size(),
                resolved.connections().size(),
                1
        );
    }

    private static boolean contiguous(List<DungeonCellPos> route) {
        for (int index = 1; index < route.size(); index++) {
            DungeonCellPos previous = route.get(index - 1);
            DungeonCellPos current = route.get(index);
            int distance = Math.abs(previous.x() - current.x())
                    + Math.abs(previous.y() - current.y())
                    + Math.abs(previous.z() - current.z());
            if (distance != 1) {
                return false;
            }
        }
        return true;
    }

    private static TopologyFixture topologyFixture() {
        DungeonGraph graph = bossHubLoopGraph();
        return new TopologyFixture(
                graph,
                firstAcceptedProceduralLayout(
                        graph,
                        new BlockPos(96, 32, -128),
                        0x5EC70A11L
                )
        );
    }

    private static void assertTopologyFails(
            DungeonGraph graph,
            DungeonLayoutPlan layout,
            ResolvedDungeonLayout resolved,
            String message
    ) {
        assertThrows(
                DungeonLayoutGenerationException.class,
                "Resolved procedural dungeon topology validation failed",
                () -> DungeonResolvedTopologyValidator.validateProcedural(
                        graph,
                        layout,
                        resolved,
                        1
                ),
                message
        );
    }

    private static DungeonLayoutEdge requireLayoutEdge(
            DungeonLayoutPlan layout,
            String id
    ) {
        return layout.edges()
                .stream()
                .filter(edge -> edge.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static ResolvedDungeonConnection requireResolvedConnection(
            ResolvedDungeonLayout layout,
            String id
    ) {
        return layout.connections()
                .stream()
                .filter(connection -> connection.id().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static DungeonLayoutPlan replaceLayoutEdge(
            DungeonLayoutPlan layout,
            String id,
            DungeonLayoutEdge replacement
    ) {
        return replaceLayoutEdges(layout, List.of(replacement));
    }

    private static DungeonLayoutPlan replaceLayoutEdges(
            DungeonLayoutPlan layout,
            List<DungeonLayoutEdge> replacements
    ) {
        List<DungeonLayoutEdge> edges = layout.edges()
                .stream()
                .map(edge -> replacements.stream()
                        .filter(replacement -> replacement.id().equals(edge.id()))
                        .findFirst()
                        .orElse(edge))
                .toList();
        return new DungeonLayoutPlan(layout.cellSize(), layout.nodes(), edges);
    }

    private static DungeonLayoutPlan replaceLayoutEdgesByOriginal(
            DungeonLayoutPlan layout,
            List<EdgeReplacement> replacements
    ) {
        List<DungeonLayoutEdge> edges = layout.edges()
                .stream()
                .map(edge -> replacements.stream()
                        .filter(replacement -> replacement.originalId()
                                .equals(edge.id()))
                        .findFirst()
                        .map(EdgeReplacement::edge)
                        .orElse(edge))
                .toList();
        return new DungeonLayoutPlan(layout.cellSize(), layout.nodes(), edges);
    }

    private static List<ResolvedDungeonConnection> replaceResolvedConnection(
            List<ResolvedDungeonConnection> connections,
            String id,
            ResolvedDungeonConnection replacement
    ) {
        return connections.stream()
                .map(connection -> connection.id().equals(id)
                        ? replacement
                        : connection)
                .toList();
    }

    private static DungeonLayoutEdge copyEdgeWithEndpoints(
            DungeonLayoutEdge edge,
            String fromRoomId,
            String toRoomId
    ) {
        return new DungeonLayoutEdge(
                edge.id(),
                fromRoomId,
                toRoomId,
                edge.fromSide(),
                edge.toSide(),
                edge.widthCells(),
                edge.kind(),
                edge.plannedPath()
        );
    }

    private static DungeonLayoutEdge copyEdgeWithKind(
            DungeonLayoutEdge edge,
            DungeonGraphEdgeKind kind
    ) {
        return new DungeonLayoutEdge(
                edge.id(),
                edge.fromRoomId(),
                edge.toRoomId(),
                edge.fromSide(),
                edge.toSide(),
                edge.widthCells(),
                kind,
                edge.plannedPath()
        );
    }

    private static DungeonLayoutEdge copyEdgeWithId(
            DungeonLayoutEdge edge,
            String id
    ) {
        return new DungeonLayoutEdge(
                id,
                edge.fromRoomId(),
                edge.toRoomId(),
                edge.fromSide(),
                edge.toSide(),
                edge.widthCells(),
                edge.kind(),
                edge.plannedPath()
        );
    }

    private static ResolvedDungeonConnection copyConnectionWithKind(
            ResolvedDungeonConnection connection,
            DungeonGraphEdgeKind kind
    ) {
        return new ResolvedDungeonConnection(
                connection.id(),
                connection.from(),
                connection.to(),
                kind,
                connection.routeCells()
        );
    }

    private static AcceptedProceduralLayout firstAcceptedProceduralLayout(
            DungeonGraph graph,
            BlockPos origin,
            long generationSeed
    ) {
        return DungeonProceduralTestSupport.firstAcceptedProceduralLayout(
                graph,
                origin,
                generationSeed,
                "resolved-layout procedural fixture"
        );
    }

    private static AcceptedProceduralLayout acceptedProceduralLayout(
            DungeonGraph graph,
            BlockPos origin,
            long generationSeed,
            int attemptIndex
    ) {
        return DungeonProceduralTestSupport.acceptedProceduralLayout(
                graph,
                origin,
                generationSeed,
                attemptIndex
        );
    }

    private record TopologyFixture(
            DungeonGraph graph,
            AcceptedProceduralLayout accepted
    ) {
    }

    private record EdgeReplacement(
            String originalId,
            DungeonLayoutEdge edge
    ) {
    }

    private static <T extends RuntimeException> void assertThrows(
            Class<T> expectedType,
            String expectedMessageFragment,
            Runnable action,
            String message
    ) {
        try {
            action.run();
        } catch (RuntimeException actual) {
            if (!expectedType.isInstance(actual)) {
                throw new AssertionError(
                        message
                                + ": expected exception type="
                                + expectedType.getSimpleName()
                                + ", actual="
                                + actual.getClass().getSimpleName(),
                        actual
                );
            }
            if (expectedMessageFragment != null
                    && !actual.getMessage().contains(expectedMessageFragment)) {
                throw new AssertionError(
                        message
                                + ": expected message containing='"
                                + expectedMessageFragment
                                + "', actual='"
                                + actual.getMessage()
                                + "'",
                        actual
                );
            }
            return;
        }
        throw new AssertionError(
                message
                        + ": expected exception type="
                        + expectedType.getSimpleName()
        );
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

    private static void assertSame(
            Object expected,
            Object actual,
            String message
    ) {
        if (expected != actual) {
            throw new AssertionError(
                    message + ": expected=" + expected + ", actual=" + actual
            );
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
}
