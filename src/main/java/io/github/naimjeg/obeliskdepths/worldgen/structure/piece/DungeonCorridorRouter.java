package io.github.naimjeg.obeliskdepths.worldgen.structure.piece;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonGraphEdgeKind;
import io.github.naimjeg.obeliskdepths.worldgen.debug.DungeonWorldgenTrace;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonCellBox;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonCellPos;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonConnectorSide;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutEdge;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutGenerationException;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutNode;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutPlan;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.ResolvedDungeonConnection;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.ResolvedDungeonLayout;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.ResolvedDungeonPort;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.ResolvedDungeonRoom;
import io.github.naimjeg.obeliskdepths.worldgen.structure.generation.DungeonGenerationCatalog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

public final class DungeonCorridorRouter {
    public static final int ROOM_CLEARANCE_CELLS = 0;
    public static final int RESOLVED_CORRIDOR_TEMPLATE_CLEARANCE_CELLS = 0;
    public static final int MAX_INDIVIDUAL_ROUTE_CELLS = 96;
    public static final int MAX_TOTAL_ROUTE_CELLS = 384;

    private static final int SEARCH_PADDING_CELLS = 16;
    private static final int MAX_EXPLORED_STATES = 4_000;
    private static final int MOVE_COST = 10;
    private static final int TURN_COST = 24;
    private static final int NEAR_UNRELATED_ROOM_COST = 12;
    private static final int OUTSIDE_LAYOUT_HULL_COST = 8;
    private static final int UNPLANNED_SIDE_COST = 80;
    private static final int MAX_ROUTE_TURNS = 3;
    private static final int ROUTE_STRETCH_ALLOWANCE = 4;
    private static final double MAX_ROUTE_STRETCH = 1.75D;

    private static final List<DungeonConnectorSide> HORIZONTAL_SIDES = List.of(
            DungeonConnectorSide.NORTH,
            DungeonConnectorSide.EAST,
            DungeonConnectorSide.SOUTH,
            DungeonConnectorSide.WEST
    );

    private DungeonCorridorRouter() {
    }

    public static DungeonRoutingResult route(DungeonLayoutPlan plan) {
        return route(plan, Map.of(), DungeonWorldgenTrace.Context.disabled(null));
    }

    public static ResolvedDungeonLayout route(
            ResolvedDungeonLayout layout,
            DungeonGenerationCatalog catalog,
            DungeonWorldgenTrace.Context traceContext
    ) {
        if (catalog == null) {
            throw new IllegalArgumentException(
                    "Dungeon generation catalog is required for resolved routing"
            );
        }

        RoutingBounds bounds = searchBoundsResolved(layout.rooms());
        Set<DungeonRouteCell> reserved = new HashSet<>();
        List<ResolvedDungeonConnection> routed = new ArrayList<>();
        int totalLength = 0;
        List<ResolvedDungeonConnection> orderedConnections =
                orderedResolvedConnections(layout);

        for (ResolvedDungeonConnection connection : orderedConnections) {
            ResolvedDungeonRoom fromRoom =
                    layout.requireRoom(connection.from().roomId());
            ResolvedDungeonRoom toRoom =
                    layout.requireRoom(connection.to().roomId());
            ResolvedDungeonPort fromPort = fromRoom.requirePort(
                    connection.from().portId()
            );
            ResolvedDungeonPort toPort = toRoom.requirePort(
                    connection.to().portId()
            );
            DungeonCellPos startPos = fromPort.outsideCell(
                    fromRoom.cellOrigin()
            );
            DungeonCellPos goalPos = toPort.outsideCell(toRoom.cellOrigin());
            Set<DungeonRouteCell> reservedForConnection = reservedForConnection(
                    layout,
                    routed,
                    connection
            );

            if (startPos.y() != goalPos.y()) {
                throw new DungeonLayoutGenerationException(
                        "Resolved corridor endpoints have different elevations: "
                                + connection.id()
                                + " start="
                                + startPos
                                + " goal="
                                + goalPos
                );
            }

            List<DungeonRouteCell> route;
            try {
                route = connection.routeCells().isEmpty()
                        ? routeResolvedConnection(
                                layout,
                                reservedForConnection,
                                bounds,
                                connection,
                                startPos,
                                goalPos
                        )
                        : proceduralConnection(connection)
                        ? routeProceduralPlannedConnection(
                                layout,
                                reservedForConnection,
                                bounds,
                                connection,
                                startPos,
                                goalPos,
                                traceContext
                        )
                        : validateAuthoredResolvedPath(
                                layout,
                                reservedForConnection,
                                bounds,
                                connection,
                                startPos,
                                goalPos
                        );
            } catch (DungeonLayoutGenerationException exception) {
                DungeonWorldgenTrace.warnAlways(
                        traceContext,
                        () -> "stage=resolved-route-failed edge="
                                + connection.id()
                                + " kind="
                                + connection.kind()
                                + " from="
                                + connection.from()
                                + " to="
                                + connection.to()
                                + " reason="
                                + exception.getMessage()
                );
                throw exception;
            }

            DungeonRoutedCorridor corridor = new DungeonRoutedCorridor(
                    connection.id(),
                    connection.from().roomId(),
                    connection.to().roomId(),
                    fromPort.facing(),
                    toPort.facing(),
                    connection.kind(),
                    route.stream()
                            .map(cell -> new DungeonCellPos(
                                    cell.x(),
                                    startPos.y(),
                                    cell.z()
                            ))
                            .toList()
            );
            validateRouteQuality(corridor, route.getFirst(), route.getLast());
            totalLength += corridor.lengthCells();
            if (totalLength > MAX_TOTAL_ROUTE_CELLS) {
                throw new DungeonLayoutGenerationException(
                        "Total resolved corridor route length exceeds budget: "
                                + totalLength
                );
            }
            for (int routeIndex = 0; routeIndex < route.size(); routeIndex++) {
                DungeonRouteCell cell = route.get(routeIndex);
                if (overlapsIllegalRoutedCell(
                        routed,
                        connection,
                        routeIndex,
                        route.size(),
                        cell
                )) {
                    throw new DungeonLayoutGenerationException(
                            "Resolved corridor route overlaps an earlier route: "
                                    + connection.id()
                                    + " cell="
                                    + cell
                    );
                }
                reserved.add(cell);
            }
            DungeonWorldgenTrace.debug(
                    traceContext,
                    () -> "stage=resolved-route-edge edge="
                            + connection.id()
                            + " kind="
                            + connection.kind()
                            + " from="
                            + connection.from()
                            + " to="
                            + connection.to()
                            + " start="
                            + startPos
                            + " goal="
                            + goalPos
                            + " length="
                            + corridor.lengthCells()
                            + " turns="
                            + countDungeonTurns(corridor.path())
                            + " authoredPath="
                            + !connection.routeCells().isEmpty()
                            + " path="
                            + DungeonWorldgenTrace.bounded(corridor.path())
            );
            routed.add(new ResolvedDungeonConnection(
                    connection.id(),
                    connection.from(),
                    connection.to(),
                    connection.kind(),
                    corridor.path()
            ));
        }

        return layout.withConnections(routed);
    }

    private static List<ResolvedDungeonConnection> orderedResolvedConnections(
            ResolvedDungeonLayout layout
    ) {
        Map<String, Integer> originalOrder = new HashMap<>();
        for (int index = 0; index < layout.connections().size(); index++) {
            originalOrder.put(layout.connections().get(index).id(), index);
        }
        return layout.connections()
                .stream()
                .sorted((first, second) -> {
                    boolean firstProcedural = proceduralConnection(first);
                    boolean secondProcedural = proceduralConnection(second);
                    if (firstProcedural != secondProcedural) {
                        return Integer.compare(
                                originalOrder.get(first.id()),
                                originalOrder.get(second.id())
                        );
                    }
                    if (!firstProcedural) {
                        return Integer.compare(
                                originalOrder.get(first.id()),
                                originalOrder.get(second.id())
                        );
                    }
                    int kindComparison = Integer.compare(
                            resolvedKindOrder(first.kind()),
                            resolvedKindOrder(second.kind())
                    );
                    if (kindComparison != 0) {
                        return kindComparison;
                    }
                    int distanceComparison = Integer.compare(
                            resolvedConnectionDistance(layout, second),
                            resolvedConnectionDistance(layout, first)
                    );
                    if (distanceComparison != 0) {
                        return distanceComparison;
                    }
                    return Integer.compare(
                            originalOrder.get(first.id()),
                            originalOrder.get(second.id())
                    );
                })
                .toList();
    }

    private static int resolvedKindOrder(DungeonGraphEdgeKind kind) {
        return switch (kind) {
            case TREE -> 0;
            case LOOP -> 1;
            case SECRET -> 2;
        };
    }

    private static int resolvedConnectionDistance(
            ResolvedDungeonLayout layout,
            ResolvedDungeonConnection connection
    ) {
        ResolvedDungeonRoom from = layout.requireRoom(connection.from().roomId());
        ResolvedDungeonRoom to = layout.requireRoom(connection.to().roomId());
        return Math.max(
                cellDistanceFromCenter(from.cellOrigin()),
                cellDistanceFromCenter(to.cellOrigin())
        );
    }

    private static int cellDistanceFromCenter(DungeonCellPos cell) {
        return Math.abs(cell.x()) + Math.abs(cell.z());
    }

    public static DungeonRoutingResult route(
            DungeonLayoutPlan plan,
            Map<String, DungeonCellBox> physicalEndpointBoxes
    ) {
        return route(plan, physicalEndpointBoxes, DungeonWorldgenTrace.Context.disabled(null));
    }

    public static DungeonRoutingResult route(
            DungeonLayoutPlan plan,
            Map<String, DungeonCellBox> physicalEndpointBoxes,
            DungeonWorldgenTrace.Context traceContext
    ) {
        Map<String, DungeonLayoutNode> nodes = new LinkedHashMap<>();
        for (DungeonLayoutNode node : plan.nodes()) {
            nodes.put(node.roomId(), node);
        }

        List<DungeonLayoutEdge> orderedEdges = plan.edges()
                .stream()
                .sorted(edgeOrder(nodes))
                .toList();
        RoutingBounds bounds = searchBounds(plan.nodes());
        Set<DungeonRouteCell> reserved = new HashSet<>();
        List<DungeonRoutedCorridor> routed = new ArrayList<>();
        int totalLength = 0;
        Map<String, DungeonCellBox> endpointBoxes = physicalEndpointBoxes == null
                ? Map.of()
                : Map.copyOf(physicalEndpointBoxes);

        for (DungeonLayoutEdge edge : orderedEdges) {
            DungeonLayoutNode from = nodes.get(edge.fromRoomId());
            DungeonLayoutNode to = nodes.get(edge.toRoomId());

            if (from == null || to == null) {
                throw new DungeonLayoutGenerationException(
                        "Cannot route corridor with missing endpoint: " + edge.id()
                );
            }

            RouteCandidate candidate = routeEdge(
                    plan.nodes(),
                    endpointBoxes,
                    reserved,
                    bounds,
                    edge,
                    from,
                    to,
                    traceContext
            ).orElseThrow(() -> new DungeonLayoutGenerationException(
                    "No non-overlapping route for corridor "
                            + edge.id()
                            + " from="
                            + edge.fromRoomId()
                            + " to="
                            + edge.toRoomId()
                            + " fromBox="
                            + from.cellBox()
                            + " toBox="
                            + to.cellBox()
                            + " fromPhysicalBox="
                            + endpointBox(from, endpointBoxes)
                            + " toPhysicalBox="
                            + endpointBox(to, endpointBoxes)
                            + " reservedCells="
                            + reserved.size()
            ));
            DungeonRoutedCorridor corridor = candidate.corridor();

            validateRouteQuality(
                    corridor,
                    candidate.start(),
                    candidate.goal()
            );

            totalLength += corridor.lengthCells();
            if (totalLength > MAX_TOTAL_ROUTE_CELLS) {
                throw new DungeonLayoutGenerationException(
                        "Total corridor route length exceeds budget: "
                                + totalLength
                );
            }

            for (DungeonCellPos pos : corridor.path()) {
                reserved.add(new DungeonRouteCell(pos.x(), pos.z()));
            }
            DungeonWorldgenTrace.debug(
                    traceContext,
                    () -> "stage=route-edge edge="
                            + edge.id()
                            + " kind="
                            + edge.kind()
                            + " from="
                            + edge.fromRoomId()
                            + " to="
                            + edge.toRoomId()
                            + " requestedFromSide="
                            + edge.fromSide().getSerializedName()
                            + " requestedToSide="
                            + edge.toSide().getSerializedName()
                            + " fromPlanningBox="
                            + from.cellBox()
                            + " toPlanningBox="
                            + to.cellBox()
                            + " fromPhysicalBox="
                            + endpointBox(from, endpointBoxes)
                            + " toPhysicalBox="
                            + endpointBox(to, endpointBoxes)
                            + " start="
                            + candidate.start()
                            + " goal="
                            + candidate.goal()
                            + " length="
                            + corridor.lengthCells()
                            + " turns="
                            + countDungeonTurns(corridor.path())
                            + " path="
                            + DungeonWorldgenTrace.bounded(corridor.path())
                            + " reservedBefore="
                            + (reserved.size() - corridor.path().size())
                            + " reservedAfter="
                            + reserved.size()
                            + " startInsidePlanningPadding="
                            + insidePlanningPadding(from, endpointBoxes, candidate.start())
                            + " goalInsidePlanningPadding="
                            + insidePlanningPadding(to, endpointBoxes, candidate.goal())
            );
            routed.add(corridor);
        }

        return new DungeonRoutingResult(routed);
    }

    private static List<DungeonRouteCell> routeResolvedConnection(
            ResolvedDungeonLayout layout,
            Set<DungeonRouteCell> reserved,
            RoutingBounds bounds,
            ResolvedDungeonConnection connection,
            DungeonCellPos startPos,
            DungeonCellPos goalPos
    ) {
        DungeonRouteCell start = new DungeonRouteCell(startPos.x(), startPos.z());
        DungeonRouteCell goal = new DungeonRouteCell(goalPos.x(), goalPos.z());
        List<List<DungeonRouteCell>> candidates = new ArrayList<>();

        for (List<DungeonRouteCell> manhattan : DungeonRouteCandidateGenerator.manhattanCandidates(start, goal)) {
            if (legalResolvedPath(
                    layout,
                    reserved,
                    bounds,
                    connection,
                    manhattan,
                    RESOLVED_CORRIDOR_TEMPLATE_CLEARANCE_CELLS
            )) {
                DungeonRouteCandidateGenerator.addUnique(candidates, manhattan);
            }
        }
        List<DungeonRouteCell> best = DungeonRouteCandidateGenerator.shortest(candidates);
        if (!best.isEmpty()
                && routeWithinQuality(connection.kind(), best, start, goal)) {
            return best;
        }

        DungeonRouteCandidateGenerator.addUnique(candidates, aStarResolved(
                layout,
                reserved,
                bounds,
                connection,
                start,
                goal,
                RESOLVED_CORRIDOR_TEMPLATE_CLEARANCE_CELLS
        ));
        best = DungeonRouteCandidateGenerator.shortest(candidates);
        if (!best.isEmpty()
                && routeWithinQuality(connection.kind(), best, start, goal)) {
            return best;
        }
        DungeonRouteCandidateGenerator.addUnique(
                candidates,
                aStarResolved(
                        layout,
                        reserved,
                        bounds,
                        connection,
                        start,
                        goal,
                        0
                )
        );
        best = DungeonRouteCandidateGenerator.shortest(candidates);
        if (!best.isEmpty()
                && routeWithinQuality(connection.kind(), best, start, goal)) {
            return best;
        }
        if (best.isEmpty()) {
            throw new DungeonLayoutGenerationException(
                    "No resolved route for connection "
                            + connection.id()
                            + " from="
                            + connection.from()
                            + " start="
                            + startPos
                            + " to="
                            + connection.to()
                            + " goal="
                            + goalPos
            );
        }
        throw new DungeonLayoutGenerationException(
                "No resolved route within quality budget for connection "
                        + connection.id()
                        + " from="
                        + connection.from()
                        + " start="
                        + startPos
                        + " to="
                        + connection.to()
                        + " goal="
                        + goalPos
                        + " bestLength="
                        + best.size()
        );
    }

    private static Set<DungeonRouteCell> reservedForConnection(
            ResolvedDungeonLayout layout,
            List<ResolvedDungeonConnection> routed,
            ResolvedDungeonConnection current
    ) {
        Set<DungeonRouteCell> blocked = new HashSet<>();
        for (ResolvedDungeonConnection previous : routed) {
            for (DungeonCellPos cell : previous.routeCells()) {
                DungeonRouteCell blockedCell = new DungeonRouteCell(cell.x(), cell.z());
                if (!isReleasedEndpointCell(
                        layout,
                        previous,
                        current,
                        blockedCell
                )) {
                    blocked.add(blockedCell);
                }
            }
        }
        return blocked;
    }

    private static boolean overlapsIllegalRoutedCell(
            List<ResolvedDungeonConnection> routed,
            ResolvedDungeonConnection current,
            int currentRouteIndex,
            int currentRouteSize,
            DungeonRouteCell cell
    ) {
        for (ResolvedDungeonConnection previous : routed) {
            for (DungeonCellPos previousCell : previous.routeCells()) {
                if (previousCell.x() == cell.x()
                        && previousCell.z() == cell.z()
                        && !isLegalSharedEndpointOverlap(
                        previous,
                        current,
                        currentRouteIndex,
                        currentRouteSize,
                        cell
                )) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isReleasedEndpointCell(
            ResolvedDungeonLayout layout,
            ResolvedDungeonConnection first,
            ResolvedDungeonConnection second,
            DungeonRouteCell cell
    ) {
        for (String roomId : sharedEndpointRooms(first, second)) {
            if (resolvedEndpointCell(layout, first, roomId)
                    .map(cell::equals)
                    .orElse(false)
                    || resolvedEndpointCell(layout, second, roomId)
                    .map(cell::equals)
                    .orElse(false)) {
                return true;
            }
        }
        return false;
    }

    private static Optional<DungeonRouteCell> resolvedEndpointCell(
            ResolvedDungeonLayout layout,
            ResolvedDungeonConnection connection,
            String roomId
    ) {
        if (connection.from().roomId().equals(roomId)) {
            ResolvedDungeonRoom room = layout.requireRoom(roomId);
            ResolvedDungeonPort port = room.requirePort(connection.from().portId());
            DungeonCellPos cell = port.outsideCell(room.cellOrigin());
            return Optional.of(new DungeonRouteCell(cell.x(), cell.z()));
        }
        if (connection.to().roomId().equals(roomId)) {
            ResolvedDungeonRoom room = layout.requireRoom(roomId);
            ResolvedDungeonPort port = room.requirePort(connection.to().portId());
            DungeonCellPos cell = port.outsideCell(room.cellOrigin());
            return Optional.of(new DungeonRouteCell(cell.x(), cell.z()));
        }
        return Optional.empty();
    }

    private static boolean isLegalSharedEndpointOverlap(
            ResolvedDungeonConnection previous,
            ResolvedDungeonConnection current,
            int currentRouteIndex,
            int currentRouteSize,
            DungeonRouteCell cell
    ) {
        for (String roomId : sharedEndpointRooms(previous, current)) {
            boolean previousEndpoint = endpointCell(previous, roomId)
                    .map(cell::equals)
                    .orElse(false);
            boolean currentEndpoint = isEndpointCell(
                    current,
                    roomId,
                    currentRouteIndex,
                    currentRouteSize,
                    cell
            );
            if (previousEndpoint && currentEndpoint) {
                return true;
            }
        }
        return false;
    }

    private static List<String> sharedEndpointRooms(
            ResolvedDungeonConnection first,
            ResolvedDungeonConnection second
    ) {
        List<String> shared = new ArrayList<>();
        if (first.from().roomId().equals(second.from().roomId())
                || first.from().roomId().equals(second.to().roomId())) {
            shared.add(first.from().roomId());
        }
        if (!first.to().roomId().equals(first.from().roomId())
                && (first.to().roomId().equals(second.from().roomId())
                || first.to().roomId().equals(second.to().roomId()))) {
            shared.add(first.to().roomId());
        }
        return shared;
    }

    private static Optional<DungeonRouteCell> endpointCell(
            ResolvedDungeonConnection connection,
            String roomId
    ) {
        if (connection.routeCells().isEmpty()) {
            return Optional.empty();
        }
        if (connection.from().roomId().equals(roomId)) {
            DungeonCellPos cell = connection.routeCells().getFirst();
            return Optional.of(new DungeonRouteCell(cell.x(), cell.z()));
        }
        if (connection.to().roomId().equals(roomId)) {
            DungeonCellPos cell = connection.routeCells().getLast();
            return Optional.of(new DungeonRouteCell(cell.x(), cell.z()));
        }
        return Optional.empty();
    }

    private static boolean isEndpointCell(
            ResolvedDungeonConnection connection,
            String roomId,
            int routeIndex,
            int routeSize,
            DungeonRouteCell cell
    ) {
        if (connection.from().roomId().equals(roomId)
                && routeIndex == 0) {
            return true;
        }
        return connection.to().roomId().equals(roomId)
                && routeIndex == routeSize - 1;
    }

    private static boolean proceduralConnection(
            ResolvedDungeonConnection connection
    ) {
        return connection.id().startsWith("corridor_tree_")
                || connection.id().startsWith("corridor_loop_")
                || connection.id().startsWith("corridor_secret_");
    }

    private static List<DungeonRouteCell> routeProceduralPlannedConnection(
            ResolvedDungeonLayout layout,
            Set<DungeonRouteCell> reserved,
            RoutingBounds bounds,
            ResolvedDungeonConnection connection,
            DungeonCellPos startPos,
            DungeonCellPos goalPos,
            DungeonWorldgenTrace.Context traceContext
    ) {
        List<DungeonRouteCell> planned = connection.routeCells()
                .stream()
                .map(cell -> new DungeonRouteCell(cell.x(), cell.z()))
                .toList();
        DungeonRouteCell start = new DungeonRouteCell(startPos.x(), startPos.z());
        DungeonRouteCell goal = new DungeonRouteCell(goalPos.x(), goalPos.z());

        if (planned.getFirst().equals(start)
                && planned.getLast().equals(goal)
                && legalResolvedPath(
                layout,
                reserved,
                bounds,
                connection,
                planned,
                0
        )
                && routeWithinQuality(
                connection.kind(),
                planned,
                start,
                goal
        )) {
            return planned;
        }

        List<DungeonRouteCell> retargeted = retargetProceduralPlannedPath(
                layout,
                reserved,
                bounds,
                connection,
                start,
                goal,
                planned
        );
        if (!retargeted.isEmpty()
                && legalResolvedPath(
                layout,
                reserved,
                bounds,
                connection,
                retargeted,
                0
        )
                && routeWithinQuality(
                connection.kind(),
                retargeted,
                start,
                goal
        )) {
            return retargeted;
        }

        DungeonWorldgenTrace.debug(
                traceContext,
                () -> "stage=resolved-route-replan edge="
                        + connection.id()
                        + " kind="
                        + connection.kind()
                        + " reason=plannedPathCouldNotRetarget"
                        + " plannedStart="
                        + connection.routeCells().getFirst()
                        + " plannedGoal="
                        + connection.routeCells().getLast()
                        + " resolvedStart="
                        + startPos
                        + " resolvedGoal="
                        + goalPos
        );
        return routeResolvedConnection(
                layout,
                reserved,
                bounds,
                connection,
                startPos,
                goalPos
        );
    }

    private static List<DungeonRouteCell> retargetProceduralPlannedPath(
            ResolvedDungeonLayout layout,
            Set<DungeonRouteCell> reserved,
            RoutingBounds bounds,
            ResolvedDungeonConnection connection,
            DungeonRouteCell start,
            DungeonRouteCell goal,
            List<DungeonRouteCell> planned
    ) {
        if (planned.isEmpty()) {
            return List.of();
        }

        List<DungeonRouteCell> prefix = routeResolvedSegment(
                layout,
                reserved,
                bounds,
                connection,
                start,
                planned.getFirst()
        );
        if (prefix.isEmpty()) {
            return List.of();
        }
        List<DungeonRouteCell> suffix = routeResolvedSegment(
                layout,
                reserved,
                bounds,
                connection,
                planned.getLast(),
                goal
        );
        if (suffix.isEmpty()) {
            return List.of();
        }

        return DungeonRouteCandidateGenerator.combine(prefix, planned, suffix);
    }

    private static List<DungeonRouteCell> routeResolvedSegment(
            ResolvedDungeonLayout layout,
            Set<DungeonRouteCell> reserved,
            RoutingBounds bounds,
            ResolvedDungeonConnection connection,
            DungeonRouteCell start,
            DungeonRouteCell goal
    ) {
        if (start.equals(goal)) {
            return List.of(start);
        }

        List<List<DungeonRouteCell>> candidates = new ArrayList<>();
        for (List<DungeonRouteCell> manhattan : DungeonRouteCandidateGenerator.manhattanCandidates(start, goal)) {
            if (legalResolvedSegment(
                layout,
                reserved,
                bounds,
                connection,
                manhattan,
                RESOLVED_CORRIDOR_TEMPLATE_CLEARANCE_CELLS
            )) {
                DungeonRouteCandidateGenerator.addUnique(candidates, manhattan);
            }
        }

        DungeonRouteCandidateGenerator.addUnique(candidates, aStarResolvedSegment(
                layout,
                reserved,
                bounds,
                connection,
                start,
                goal,
                RESOLVED_CORRIDOR_TEMPLATE_CLEARANCE_CELLS
        ));
        DungeonRouteCandidateGenerator.addUnique(candidates, aStarResolvedSegment(
                layout,
                reserved,
                bounds,
                connection,
                start,
                goal,
                0
        ));
        if (candidates.isEmpty()) {
            return List.of();
        }
        return DungeonRouteCandidateGenerator.shortest(candidates);
    }

    private static boolean routeWithinQuality(
            DungeonGraphEdgeKind kind,
            List<DungeonRouteCell> route,
            DungeonRouteCell start,
            DungeonRouteCell goal
    ) {
        if (route.size() > MAX_INDIVIDUAL_ROUTE_CELLS) {
            return false;
        }

        int directDistance = DungeonRouteScorer.manhattan(start, goal);
        int routedSteps = Math.max(0, route.size() - 1);
        double maximumStretch = kind == DungeonGraphEdgeKind.LOOP
                ? 3.25D
                : MAX_ROUTE_STRETCH;
        int stretchAllowance = kind == DungeonGraphEdgeKind.LOOP
                ? ROUTE_STRETCH_ALLOWANCE + 8
                : ROUTE_STRETCH_ALLOWANCE;
        int maximumSteps = Math.max(
                4,
                (int) Math.ceil(directDistance * maximumStretch)
                        + stretchAllowance
        );
        if (routedSteps > maximumSteps) {
            return false;
        }

        int maximumTurns = kind == DungeonGraphEdgeKind.LOOP
                ? MAX_ROUTE_TURNS + 7
                : MAX_ROUTE_TURNS;
        return DungeonRouteScorer.countTurns(route) <= maximumTurns;
    }

    private static boolean legalResolvedSegment(
            ResolvedDungeonLayout layout,
            Set<DungeonRouteCell> reserved,
            RoutingBounds bounds,
            ResolvedDungeonConnection connection,
            List<DungeonRouteCell> path,
            int unrelatedRoomClearance
    ) {
        Set<DungeonRouteCell> blocked = blockedCellsResolved(
                layout,
                connection,
                unrelatedRoomClearance
        );
        blocked.remove(path.getFirst());
        blocked.remove(path.getLast());
        Set<DungeonRouteCell> seen = new HashSet<>();
        for (DungeonRouteCell cell : path) {
            if (!seen.add(cell)
                    || !bounds.containsSearch(cell)
                    || reserved.contains(cell)
                    || blocked.contains(cell)) {
                return false;
            }
        }
        return true;
    }

    private static List<DungeonRouteCell> validateAuthoredResolvedPath(
            ResolvedDungeonLayout layout,
            Set<DungeonRouteCell> reserved,
            RoutingBounds bounds,
            ResolvedDungeonConnection connection,
            DungeonCellPos startPos,
            DungeonCellPos goalPos
    ) {
        List<DungeonCellPos> authored = connection.routeCells();
        if (authored.isEmpty()) {
            throw new DungeonLayoutGenerationException(
                    "Authored resolved route is empty: " + connection.id()
            );
        }
        if (!authored.getFirst().equals(startPos)
                || !authored.getLast().equals(goalPos)) {
            throw new DungeonLayoutGenerationException(
                    "Authored route endpoints do not match resolved ports: "
                            + connection.id()
                            + " expectedStart="
                            + startPos
                            + " actualStart="
                            + authored.getFirst()
                            + " expectedGoal="
                            + goalPos
                            + " actualGoal="
                            + authored.getLast()
            );
        }
        for (DungeonCellPos cell : authored) {
            if (cell.y() != startPos.y()) {
                throw new DungeonLayoutGenerationException(
                        "Authored route changes elevation without vertical routing support: "
                                + connection.id()
                                + " cell="
                                + cell
                );
            }
        }
        List<DungeonRouteCell> path = authored.stream()
                .map(cell -> new DungeonRouteCell(cell.x(), cell.z()))
                .toList();
        if (!legalResolvedPath(layout, reserved, bounds, connection, path)) {
            throw new DungeonLayoutGenerationException(
                    "Authored route is obstructed or overlaps another route: "
                            + connection.id()
                            + " path="
                            + authored
            );
        }
        return path;
    }

    private static List<DungeonRouteCell> aStarResolved(
            ResolvedDungeonLayout layout,
            Set<DungeonRouteCell> reserved,
            RoutingBounds bounds,
            ResolvedDungeonConnection connection,
            DungeonRouteCell start,
            DungeonRouteCell goal,
            int unrelatedRoomClearance
    ) {
        Set<DungeonRouteCell> blocked = blockedCellsResolved(
                layout,
                connection,
                unrelatedRoomClearance
        );
        blocked.addAll(reserved);
        if (reserved.contains(start) || reserved.contains(goal)) {
            return List.of();
        }
        blocked.remove(start);
        blocked.remove(goal);

        RouteState startState = new RouteState(start, null);
        Map<RouteState, Integer> bestCost = new HashMap<>();
        Map<RouteState, RouteState> previous = new HashMap<>();
        PriorityQueue<SearchNode> open = new PriorityQueue<>(searchNodeOrder());
        long sequence = 0L;
        bestCost.put(startState, 0);
        open.add(new SearchNode(
                startState,
                0,
                heuristic(start, goal),
                sequence++
        ));

        int explored = 0;
        while (!open.isEmpty() && explored++ < MAX_EXPLORED_STATES) {
            SearchNode currentNode = open.remove();
            RouteState current = currentNode.state();
            int knownCost = bestCost.getOrDefault(current, Integer.MAX_VALUE);
            if (currentNode.cost() != knownCost) {
                continue;
            }
            if (current.cell().equals(goal)) {
                return reconstruct(previous, current);
            }

            for (Step step : orderedSteps(current.cell(), goal)) {
                DungeonRouteCell nextCell = step.cell();
                if (!bounds.containsSearch(nextCell)
                        || blocked.contains(nextCell)) {
                    continue;
                }
                int stepCost = MOVE_COST
                        + turnCost(current.direction(), step.direction())
                        + resolvedRoomProximityPenalty(
                                nextCell,
                                layout,
                                connection
                        )
                        + bounds.outsideHullDistance(nextCell)
                        * OUTSIDE_LAYOUT_HULL_COST;
                int tentativeCost = currentNode.cost() + stepCost;
                RouteState next = new RouteState(nextCell, step.direction());
                if (tentativeCost >= bestCost.getOrDefault(
                        next,
                        Integer.MAX_VALUE
                )) {
                    continue;
                }
                bestCost.put(next, tentativeCost);
                previous.put(next, current);
                open.add(new SearchNode(
                        next,
                        tentativeCost,
                        tentativeCost + heuristic(nextCell, goal),
                        sequence++
                ));
            }
        }
        return List.of();
    }

    private static List<DungeonRouteCell> aStarResolvedSegment(
            ResolvedDungeonLayout layout,
            Set<DungeonRouteCell> reserved,
            RoutingBounds bounds,
            ResolvedDungeonConnection connection,
            DungeonRouteCell start,
            DungeonRouteCell goal,
            int unrelatedRoomClearance
    ) {
        Set<DungeonRouteCell> blocked = blockedCellsResolved(
                layout,
                connection,
                unrelatedRoomClearance
        );
        blocked.addAll(reserved);
        if (reserved.contains(start) || reserved.contains(goal)) {
            return List.of();
        }
        blocked.remove(start);
        blocked.remove(goal);

        RouteState startState = new RouteState(start, null);
        Map<RouteState, Integer> bestCost = new HashMap<>();
        Map<RouteState, RouteState> previous = new HashMap<>();
        PriorityQueue<SearchNode> open = new PriorityQueue<>(searchNodeOrder());
        long sequence = 0L;
        bestCost.put(startState, 0);
        open.add(new SearchNode(
                startState,
                0,
                heuristic(start, goal),
                sequence++
        ));

        int explored = 0;
        while (!open.isEmpty() && explored++ < MAX_EXPLORED_STATES) {
            SearchNode currentNode = open.remove();
            RouteState current = currentNode.state();
            int knownCost = bestCost.getOrDefault(current, Integer.MAX_VALUE);
            if (currentNode.cost() != knownCost) {
                continue;
            }
            if (current.cell().equals(goal)) {
                return reconstruct(previous, current);
            }

            for (Step step : orderedSteps(current.cell(), goal)) {
                DungeonRouteCell nextCell = step.cell();
                if (!bounds.containsSearch(nextCell)
                        || blocked.contains(nextCell)) {
                    continue;
                }
                int stepCost = MOVE_COST
                        + turnCost(current.direction(), step.direction())
                        + resolvedRoomProximityPenalty(
                        nextCell,
                        layout,
                        connection
                )
                        + bounds.outsideHullDistance(nextCell)
                        * OUTSIDE_LAYOUT_HULL_COST;
                int tentativeCost = currentNode.cost() + stepCost;
                RouteState next = new RouteState(nextCell, step.direction());
                if (tentativeCost >= bestCost.getOrDefault(
                        next,
                        Integer.MAX_VALUE
                )) {
                    continue;
                }
                bestCost.put(next, tentativeCost);
                previous.put(next, current);
                open.add(new SearchNode(
                        next,
                        tentativeCost,
                        tentativeCost + heuristic(nextCell, goal),
                        sequence++
                ));
            }
        }
        return List.of();
    }

    private static boolean legalResolvedPath(
            ResolvedDungeonLayout layout,
            Set<DungeonRouteCell> reserved,
            RoutingBounds bounds,
            ResolvedDungeonConnection connection,
            List<DungeonRouteCell> path
    ) {
        return legalResolvedPath(
                layout,
                reserved,
                bounds,
                connection,
                path,
                RESOLVED_CORRIDOR_TEMPLATE_CLEARANCE_CELLS
        );
    }

    private static boolean legalResolvedPath(
            ResolvedDungeonLayout layout,
            Set<DungeonRouteCell> reserved,
            RoutingBounds bounds,
            ResolvedDungeonConnection connection,
            List<DungeonRouteCell> path,
            int unrelatedRoomClearance
    ) {
        if (path.isEmpty()) {
            return false;
        }
        Set<DungeonRouteCell> blocked = blockedCellsResolved(
                layout,
                connection,
                unrelatedRoomClearance
        );
        blocked.remove(path.getFirst());
        blocked.remove(path.getLast());
        Set<DungeonRouteCell> seen = new HashSet<>();
        for (DungeonRouteCell cell : path) {
            if (!seen.add(cell)) {
                return false;
            }
            if (!bounds.containsSearch(cell)
                    || reserved.contains(cell)
                    || blocked.contains(cell)) {
                return false;
            }
        }
        return true;
    }

    private static Set<DungeonRouteCell> blockedCellsResolved(
            ResolvedDungeonLayout layout,
            ResolvedDungeonConnection connection
    ) {
        return blockedCellsResolved(
                layout,
                connection,
                RESOLVED_CORRIDOR_TEMPLATE_CLEARANCE_CELLS
        );
    }

    private static Set<DungeonRouteCell> blockedCellsResolved(
            ResolvedDungeonLayout layout,
            ResolvedDungeonConnection connection,
            int unrelatedRoomClearance
    ) {
        Set<DungeonRouteCell> blocked = new HashSet<>();
        for (ResolvedDungeonRoom room : layout.rooms()) {
            int clearance = room.roomId().equals(connection.from().roomId())
                    || room.roomId().equals(connection.to().roomId())
                    ? 0
                    : unrelatedRoomClearance;
            for (DungeonCellPos cell : room.occupiedWorldCells()) {
                for (int dx = -clearance;
                     dx <= clearance;
                     dx++) {
                    for (int dz = -clearance;
                         dz <= clearance;
                         dz++) {
                        blocked.add(new DungeonRouteCell(cell.x() + dx, cell.z() + dz));
                    }
                }
            }
        }

        ResolvedDungeonRoom fromRoom =
                layout.requireRoom(connection.from().roomId());
        ResolvedDungeonRoom toRoom =
                layout.requireRoom(connection.to().roomId());
        ResolvedDungeonPort fromPort =
                fromRoom.requirePort(connection.from().portId());
        ResolvedDungeonPort toPort = toRoom.requirePort(connection.to().portId());
        DungeonCellPos fromStart = fromPort.outsideCell(fromRoom.cellOrigin());
        DungeonCellPos toGoal = toPort.outsideCell(toRoom.cellOrigin());
        blocked.remove(new DungeonRouteCell(fromStart.x(), fromStart.z()));
        blocked.remove(new DungeonRouteCell(toGoal.x(), toGoal.z()));
        return blocked;
    }

    private static int resolvedRoomProximityPenalty(
            DungeonRouteCell cell,
            ResolvedDungeonLayout layout,
            ResolvedDungeonConnection connection
    ) {
        int penalty = 0;
        for (ResolvedDungeonRoom room : layout.rooms()) {
            if (room.roomId().equals(connection.from().roomId())
                    || room.roomId().equals(connection.to().roomId())) {
                continue;
            }
            int distance = distanceToBox(cell, room.cellBox());
            if (distance == 1) {
                penalty += NEAR_UNRELATED_ROOM_COST;
            } else if (distance == 2) {
                penalty += NEAR_UNRELATED_ROOM_COST / 2;
            }
        }
        return penalty;
    }

    private static Optional<RouteCandidate> routeEdge(
            List<DungeonLayoutNode> rooms,
            Map<String, DungeonCellBox> endpointBoxes,
            Set<DungeonRouteCell> reserved,
            RoutingBounds bounds,
            DungeonLayoutEdge edge,
            DungeonLayoutNode from,
            DungeonLayoutNode to,
            DungeonWorldgenTrace.Context traceContext
    ) {
        if (!edge.plannedPath().isEmpty()) {
            Optional<RouteCandidate> planned = plannedRoute(
                    rooms,
                    endpointBoxes,
                    reserved,
                    bounds,
                    edge,
                    from,
                    to
            );
            if (planned.isPresent() || endpointBoxes.isEmpty()) {
                return planned;
            }
        }

        if (edge.kind() == DungeonGraphEdgeKind.TREE) {
            Optional<RouteCandidate> direct = directTreeRoute(
                    rooms,
                    endpointBoxes,
                    reserved,
                    bounds,
                    edge,
                    from,
                    to
            );

            if (direct.isPresent()) {
                return direct;
            }
        }

        Optional<RouteCandidate> planned = bestRouteForSides(
                rooms,
                endpointBoxes,
                reserved,
                bounds,
                edge,
                from,
                to,
                List.of(edge.fromSide()),
                List.of(edge.toSide())
        );

        if (planned.isPresent()) {
            return planned;
        }

        // Keep the layout authoritative: alternatives are considered only as a
        // failure recovery path, and they are heavily penalized.
        return bestRouteForSides(
                rooms,
                endpointBoxes,
                reserved,
                bounds,
                edge,
                from,
                to,
                orderedSides(from, to, edge.fromSide()),
                orderedSides(to, from, edge.toSide())
        );
    }

    private static Optional<RouteCandidate> plannedRoute(
            List<DungeonLayoutNode> rooms,
            Map<String, DungeonCellBox> endpointBoxes,
            Set<DungeonRouteCell> reserved,
            RoutingBounds bounds,
            DungeonLayoutEdge edge,
            DungeonLayoutNode from,
            DungeonLayoutNode to
    ) {
        List<DungeonRouteCell> path = edge.plannedPath()
                .stream()
                .map(pos -> new DungeonRouteCell(pos.x(), pos.z()))
                .toList();

        if (path.isEmpty()) {
            return Optional.empty();
        }

        DungeonRouteCell start = path.getFirst();
        DungeonRouteCell goal = path.getLast();

        if (!exteriorPorts(endpointBox(from, endpointBoxes), edge.fromSide()).contains(start)
                || !exteriorPorts(endpointBox(to, endpointBoxes), edge.toSide()).contains(goal)) {
            if (!endpointBoxes.isEmpty()) {
                return retargetPlannedRoute(
                        rooms,
                        endpointBoxes,
                        reserved,
                        bounds,
                        edge,
                        from,
                        to,
                        path
                );
            }

            throw new DungeonLayoutGenerationException(
                    "Planned corridor endpoints do not match connector sides: "
                            + edge.id()
            );
        }

        for (DungeonRouteCell cell : path) {
            if (!bounds.containsSearch(cell)
                    || reserved.contains(cell)
                    || insideBlockedRoom(rooms, endpointBoxes, from.roomId(), to.roomId(), cell)) {
                throw new DungeonLayoutGenerationException(
                        "Planned corridor is obstructed: edge="
                                + edge.id()
                                + " cell="
                                + cell
                );
            }
        }

        DungeonRoutedCorridor corridor = new DungeonRoutedCorridor(
                edge.id(),
                edge.fromRoomId(),
                edge.toRoomId(),
                edge.fromSide(),
                edge.toSide(),
                edge.kind(),
                edge.plannedPath()
        );
        return Optional.of(new RouteCandidate(
                corridor,
                start,
                goal,
                path.size() * MOVE_COST
                        + DungeonRouteScorer.countTurns(path) * TURN_COST
        ));
    }

    private static Optional<RouteCandidate> retargetPlannedRoute(
            List<DungeonLayoutNode> rooms,
            Map<String, DungeonCellBox> endpointBoxes,
            Set<DungeonRouteCell> reserved,
            RoutingBounds bounds,
            DungeonLayoutEdge edge,
            DungeonLayoutNode from,
            DungeonLayoutNode to,
            List<DungeonRouteCell> plannedPath
    ) {
        if (plannedPath.isEmpty()) {
            return Optional.empty();
        }

        DungeonRouteCell plannedStart = plannedPath.getFirst();
        DungeonRouteCell plannedGoal = plannedPath.getLast();
        RouteCandidate best = null;

        for (DungeonRouteCell start : orderedPorts(from, endpointBoxes, edge.fromSide(), to)) {
            List<DungeonRouteCell> prefix = DungeonRouteCandidateGenerator.axisAlignedPath(start, plannedStart);
            if (prefix.isEmpty() && !start.equals(plannedStart)) {
                continue;
            }

            for (DungeonRouteCell goal : orderedPorts(to, endpointBoxes, edge.toSide(), from)) {
                List<DungeonRouteCell> suffix = DungeonRouteCandidateGenerator.axisAlignedPath(plannedGoal, goal);
                if (suffix.isEmpty() && !goal.equals(plannedGoal)) {
                    continue;
                }

                List<DungeonRouteCell> combined = DungeonRouteCandidateGenerator.combine(prefix, plannedPath, suffix);
                if (!legalPath(
                        rooms,
                        endpointBoxes,
                        reserved,
                        bounds,
                        edge.fromRoomId(),
                        edge.toRoomId(),
                        combined
                )) {
                    continue;
                }

                DungeonRoutedCorridor corridor = new DungeonRoutedCorridor(
                        edge.id(),
                        edge.fromRoomId(),
                        edge.toRoomId(),
                        edge.fromSide(),
                        edge.toSide(),
                        edge.kind(),
                        combined.stream()
                                .map(cell -> new DungeonCellPos(
                                        cell.x(),
                                        0,
                                        cell.z()
                                ))
                                .toList()
                );
                RouteCandidate candidate = new RouteCandidate(
                        corridor,
                        start,
                        goal,
                        combined.size() * MOVE_COST
                                + DungeonRouteScorer.countTurns(combined) * TURN_COST
                );

                if (best == null
                        || routeCandidateOrder().compare(candidate, best) < 0) {
                    best = candidate;
                }
            }
        }

        return Optional.ofNullable(best);
    }

    private static boolean legalPath(
            List<DungeonLayoutNode> rooms,
            Map<String, DungeonCellBox> endpointBoxes,
            Set<DungeonRouteCell> reserved,
            RoutingBounds bounds,
            String fromRoomId,
            String toRoomId,
            List<DungeonRouteCell> path
    ) {
        Set<DungeonRouteCell> seen = new HashSet<>();
        for (DungeonRouteCell cell : path) {
            if (!seen.add(cell)) {
                return false;
            }
            if (!bounds.containsSearch(cell)
                    || reserved.contains(cell)
                    || insideBlockedRoom(rooms, endpointBoxes, fromRoomId, toRoomId, cell)) {
                return false;
            }
        }

        return true;
    }

    private static Optional<RouteCandidate> directTreeRoute(
            List<DungeonLayoutNode> rooms,
            Map<String, DungeonCellBox> endpointBoxes,
            Set<DungeonRouteCell> reserved,
            RoutingBounds bounds,
            DungeonLayoutEdge edge,
            DungeonLayoutNode from,
            DungeonLayoutNode to
    ) {
        Set<DungeonRouteCell> blocked = blockedCells(
                rooms,
                endpointBoxes,
                from.roomId(),
                to.roomId()
        );
        blocked.addAll(reserved);
        RouteCandidate best = null;

        for (DungeonRouteCell start : orderedPorts(from, endpointBoxes, edge.fromSide(), to)) {
            for (DungeonRouteCell goal : orderedPorts(to, endpointBoxes, edge.toSide(), from)) {
                List<DungeonRouteCell> path = DungeonRouteCandidateGenerator.axisAlignedPath(start, goal);

                if (path.isEmpty()) {
                    continue;
                }

                boolean legal = true;
                for (DungeonRouteCell cell : path) {
                    if (!bounds.containsSearch(cell)
                            || blocked.contains(cell)) {
                        legal = false;
                        break;
                    }
                }

                if (!legal) {
                    continue;
                }

                DungeonRoutedCorridor corridor = new DungeonRoutedCorridor(
                        edge.id(),
                        edge.fromRoomId(),
                        edge.toRoomId(),
                        edge.fromSide(),
                        edge.toSide(),
                        edge.kind(),
                        path.stream()
                                .map(cell -> new DungeonCellPos(
                                        cell.x(),
                                        0,
                                        cell.z()
                                ))
                                .toList()
                );
                RouteCandidate candidate = new RouteCandidate(
                        corridor,
                        start,
                        goal,
                        path.size() * MOVE_COST
                );

                if (best == null
                        || routeCandidateOrder().compare(candidate, best) < 0) {
                    best = candidate;
                }
            }
        }

        return Optional.ofNullable(best);
    }

    private static Optional<RouteCandidate> bestRouteForSides(
            List<DungeonLayoutNode> rooms,
            Map<String, DungeonCellBox> endpointBoxes,
            Set<DungeonRouteCell> reserved,
            RoutingBounds bounds,
            DungeonLayoutEdge edge,
            DungeonLayoutNode from,
            DungeonLayoutNode to,
            List<DungeonConnectorSide> fromSides,
            List<DungeonConnectorSide> toSides
    ) {
        RouteCandidate best = null;

        for (DungeonConnectorSide fromSide : fromSides) {
            List<DungeonRouteCell> startPorts = orderedPorts(from, endpointBoxes, fromSide, to)
                    .stream()
                    .filter(port -> !insideBlockedRoom(rooms, endpointBoxes, from.roomId(), to.roomId(), port))
                    .toList();

            for (DungeonConnectorSide toSide : toSides) {
                List<DungeonRouteCell> goalPorts = orderedPorts(to, endpointBoxes, toSide, from)
                        .stream()
                        .filter(port -> !insideBlockedRoom(rooms, endpointBoxes, from.roomId(), to.roomId(), port))
                        .toList();

                for (DungeonRouteCell start : startPorts) {
                    for (DungeonRouteCell goal : goalPorts) {
                        List<DungeonRouteCell> path = aStar(
                                rooms,
                                endpointBoxes,
                                reserved,
                                bounds,
                                from.roomId(),
                                to.roomId(),
                                start,
                                goal
                        );

                        if (path.isEmpty()) {
                            continue;
                        }

                        int sidePenalty = 0;
                        if (fromSide != edge.fromSide()) {
                            sidePenalty += UNPLANNED_SIDE_COST;
                        }
                        if (toSide != edge.toSide()) {
                            sidePenalty += UNPLANNED_SIDE_COST;
                        }

                        int score = path.size() * MOVE_COST
                                + DungeonRouteScorer.countTurns(path) * TURN_COST
                                + sidePenalty;
                        DungeonRoutedCorridor corridor = new DungeonRoutedCorridor(
                                edge.id(),
                                edge.fromRoomId(),
                                edge.toRoomId(),
                                fromSide,
                                toSide,
                                edge.kind(),
                                path.stream()
                                        .map(cell -> new DungeonCellPos(
                                                cell.x(),
                                                0,
                                                cell.z()
                                        ))
                                        .toList()
                        );
                        RouteCandidate candidate = new RouteCandidate(
                                corridor,
                                start,
                                goal,
                                score
                        );

                        if (best == null
                                || routeCandidateOrder().compare(candidate, best) < 0) {
                            best = candidate;
                        }
                    }
                }
            }
        }

        return Optional.ofNullable(best);
    }

    private static List<DungeonRouteCell> aStar(
            List<DungeonLayoutNode> rooms,
            Map<String, DungeonCellBox> endpointBoxes,
            Set<DungeonRouteCell> reserved,
            RoutingBounds bounds,
            String fromRoomId,
            String toRoomId,
            DungeonRouteCell start,
            DungeonRouteCell goal
    ) {
        Set<DungeonRouteCell> blocked = blockedCells(rooms, endpointBoxes, fromRoomId, toRoomId);
        blocked.addAll(reserved);

        if (reserved.contains(start) || reserved.contains(goal)) {
            return List.of();
        }

        blocked.remove(start);
        blocked.remove(goal);

        RouteState startState = new RouteState(start, null);
        Map<RouteState, Integer> bestCost = new HashMap<>();
        Map<RouteState, RouteState> previous = new HashMap<>();
        PriorityQueue<SearchNode> open = new PriorityQueue<>(searchNodeOrder());
        long sequence = 0L;

        bestCost.put(startState, 0);
        open.add(new SearchNode(
                startState,
                0,
                heuristic(start, goal),
                sequence++
        ));

        int explored = 0;

        while (!open.isEmpty() && explored++ < MAX_EXPLORED_STATES) {
            SearchNode currentNode = open.remove();
            RouteState current = currentNode.state();
            int knownCost = bestCost.getOrDefault(current, Integer.MAX_VALUE);

            if (currentNode.cost() != knownCost) {
                continue;
            }

            if (current.cell().equals(goal)) {
                return reconstruct(previous, current);
            }

            for (Step step : orderedSteps(current.cell(), goal)) {
                DungeonRouteCell nextCell = step.cell();

                if (!bounds.containsSearch(nextCell)
                        || blocked.contains(nextCell)) {
                    continue;
                }

                int stepCost = MOVE_COST
                        + turnCost(current.direction(), step.direction())
                        + roomProximityPenalty(
                                nextCell,
                                rooms,
                                fromRoomId,
                                toRoomId
                        )
                        + bounds.outsideHullDistance(nextCell)
                        * OUTSIDE_LAYOUT_HULL_COST;
                int tentativeCost = currentNode.cost() + stepCost;
                RouteState next = new RouteState(
                        nextCell,
                        step.direction()
                );

                if (tentativeCost >= bestCost.getOrDefault(
                        next,
                        Integer.MAX_VALUE
                )) {
                    continue;
                }

                bestCost.put(next, tentativeCost);
                previous.put(next, current);
                open.add(new SearchNode(
                        next,
                        tentativeCost,
                        tentativeCost + heuristic(nextCell, goal),
                        sequence++
                ));
            }
        }

        return List.of();
    }

    private static List<DungeonRouteCell> reconstruct(
            Map<RouteState, RouteState> previous,
            RouteState current
    ) {
        ArrayList<DungeonRouteCell> path = new ArrayList<>();
        path.add(current.cell());

        while (previous.containsKey(current)) {
            current = previous.get(current);
            path.add(current.cell());
        }

        java.util.Collections.reverse(path);
        return path;
    }

    private static void validateRouteQuality(
            DungeonRoutedCorridor corridor,
            DungeonRouteCell start,
            DungeonRouteCell goal
    ) {
        if (corridor.lengthCells() > MAX_INDIVIDUAL_ROUTE_CELLS) {
            throw new DungeonLayoutGenerationException(
                    "Corridor route exceeds maximum length: "
                            + corridor.edgeId()
                            + " lengthCells="
                            + corridor.lengthCells()
            );
        }

        int directDistance = DungeonRouteScorer.manhattan(start, goal);
        int routedSteps = Math.max(0, corridor.lengthCells() - 1);
        double maximumStretch = corridor.kind() == DungeonGraphEdgeKind.LOOP
                ? 3.25D
                : MAX_ROUTE_STRETCH;
        int stretchAllowance = corridor.kind() == DungeonGraphEdgeKind.LOOP
                ? ROUTE_STRETCH_ALLOWANCE + 8
                : ROUTE_STRETCH_ALLOWANCE;
        int maximumSteps = Math.max(
                4,
                (int) Math.ceil(directDistance * maximumStretch)
                        + stretchAllowance
        );

        if (routedSteps > maximumSteps) {
            throw new DungeonLayoutGenerationException(
                    "Corridor has excessive route stretch: edge="
                            + corridor.edgeId()
                            + " directSteps="
                            + directDistance
                            + " routedSteps="
                            + routedSteps
                            + " maximumSteps="
                            + maximumSteps
            );
        }

        int turns = countDungeonTurns(corridor.path());
        int maximumTurns = corridor.kind() == DungeonGraphEdgeKind.LOOP
                ? MAX_ROUTE_TURNS + 7
                : MAX_ROUTE_TURNS;
        if (turns > maximumTurns) {
            throw new DungeonLayoutGenerationException(
                    "Corridor has too many turns: edge="
                            + corridor.edgeId()
                            + " turns="
                            + turns
                            + " maximum="
                            + maximumTurns
            );
        }
    }

    private static Set<DungeonRouteCell> blockedCells(
            List<DungeonLayoutNode> rooms,
            Map<String, DungeonCellBox> endpointBoxes,
            String fromRoomId,
            String toRoomId
    ) {
        Set<DungeonRouteCell> blocked = new HashSet<>();

        for (DungeonLayoutNode room : rooms) {
            DungeonCellBox box;
            if (room.roomId().equals(fromRoomId)
                    || room.roomId().equals(toRoomId)) {
                box = endpointBox(room, endpointBoxes);
            } else {
                box = room.cellBox().expanded(ROOM_CLEARANCE_CELLS);
            }

            for (int x = box.minX(); x < box.maxXExclusive(); x++) {
                for (int z = box.minZ(); z < box.maxZExclusive(); z++) {
                    blocked.add(new DungeonRouteCell(x, z));
                }
            }
        }

        return blocked;
    }

    private static int roomProximityPenalty(
            DungeonRouteCell cell,
            List<DungeonLayoutNode> rooms,
            String fromRoomId,
            String toRoomId
    ) {
        int penalty = 0;

        for (DungeonLayoutNode room : rooms) {
            if (room.roomId().equals(fromRoomId)
                    || room.roomId().equals(toRoomId)) {
                continue;
            }

            int distance = distanceToBox(cell, room.cellBox());
            if (distance == 1) {
                penalty += NEAR_UNRELATED_ROOM_COST;
            } else if (distance == 2) {
                penalty += NEAR_UNRELATED_ROOM_COST / 2;
            }
        }

        return penalty;
    }

    private static int distanceToBox(
            DungeonRouteCell cell,
            DungeonCellBox box
    ) {
        int dx;
        if (cell.x() < box.minX()) {
            dx = box.minX() - cell.x();
        } else if (cell.x() >= box.maxXExclusive()) {
            dx = cell.x() - box.maxXExclusive() + 1;
        } else {
            dx = 0;
        }

        int dz;
        if (cell.z() < box.minZ()) {
            dz = box.minZ() - cell.z();
        } else if (cell.z() >= box.maxZExclusive()) {
            dz = cell.z() - box.maxZExclusive() + 1;
        } else {
            dz = 0;
        }

        return dx + dz;
    }

    private static boolean insideAnyRoom(
            List<DungeonLayoutNode> rooms,
            DungeonRouteCell cell
    ) {
        for (DungeonLayoutNode room : rooms) {
            DungeonCellBox box = room.cellBox();

            if (cell.x() >= box.minX()
                    && cell.x() < box.maxXExclusive()
                    && cell.z() >= box.minZ()
                    && cell.z() < box.maxZExclusive()) {
                return true;
            }
        }

        return false;
    }

    private static boolean insideBlockedRoom(
            List<DungeonLayoutNode> rooms,
            Map<String, DungeonCellBox> endpointBoxes,
            String fromRoomId,
            String toRoomId,
            DungeonRouteCell cell
    ) {
        for (DungeonLayoutNode room : rooms) {
            DungeonCellBox box = room.roomId().equals(fromRoomId)
                    || room.roomId().equals(toRoomId)
                    ? endpointBox(room, endpointBoxes)
                    : room.cellBox();

            if (cell.x() >= box.minX()
                    && cell.x() < box.maxXExclusive()
                    && cell.z() >= box.minZ()
                    && cell.z() < box.maxZExclusive()) {
                return true;
            }
        }

        return false;
    }

    private static List<Step> orderedSteps(
            DungeonRouteCell current,
            DungeonRouteCell goal
    ) {
        List<Step> steps = new ArrayList<>(List.of(
                new Step(
                        new DungeonRouteCell(current.x(), current.z() - 1),
                        DungeonConnectorSide.NORTH
                ),
                new Step(
                        new DungeonRouteCell(current.x() + 1, current.z()),
                        DungeonConnectorSide.EAST
                ),
                new Step(
                        new DungeonRouteCell(current.x(), current.z() + 1),
                        DungeonConnectorSide.SOUTH
                ),
                new Step(
                        new DungeonRouteCell(current.x() - 1, current.z()),
                        DungeonConnectorSide.WEST
                )
        ));
        steps.sort(Comparator
                .comparingInt((Step step) -> DungeonRouteScorer.manhattan(step.cell(), goal))
                .thenComparingInt(step -> step.direction().ordinal()));
        return steps;
    }

    private static List<DungeonConnectorSide> orderedSides(
            DungeonLayoutNode source,
            DungeonLayoutNode target,
            DungeonConnectorSide planned
    ) {
        DungeonConnectorSide preferred = preferredSide(source, target);
        ArrayList<DungeonConnectorSide> sides = new ArrayList<>(
                HORIZONTAL_SIDES
        );
        sides.sort(Comparator
                .comparingInt((DungeonConnectorSide side) -> {
                    if (side == planned) {
                        return 0;
                    }
                    if (side == preferred) {
                        return 1;
                    }
                    if (side == planned.opposite()) {
                        return 3;
                    }
                    return 2;
                })
                .thenComparingInt(Enum::ordinal));
        return sides;
    }

    private static DungeonConnectorSide preferredSide(
            DungeonLayoutNode source,
            DungeonLayoutNode target
    ) {
        DungeonRouteCell sourceCenter = roomCenter(source);
        DungeonRouteCell targetCenter = roomCenter(target);
        int dx = targetCenter.x() - sourceCenter.x();
        int dz = targetCenter.z() - sourceCenter.z();

        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0
                    ? DungeonConnectorSide.EAST
                    : DungeonConnectorSide.WEST;
        }

        return dz >= 0
                ? DungeonConnectorSide.SOUTH
                : DungeonConnectorSide.NORTH;
    }

    private static List<DungeonRouteCell> orderedPorts(
            DungeonLayoutNode room,
            Map<String, DungeonCellBox> endpointBoxes,
            DungeonConnectorSide side,
            DungeonLayoutNode target
    ) {
        DungeonRouteCell targetCenter = roomCenter(endpointBox(target, endpointBoxes));
        return exteriorPorts(endpointBox(room, endpointBoxes), side)
                .stream()
                .sorted(Comparator
                        .comparingInt((DungeonRouteCell cell) -> DungeonRouteScorer.manhattan(
                                cell,
                                targetCenter
                        ))
                        .thenComparingInt(DungeonRouteCell::x)
                        .thenComparingInt(DungeonRouteCell::z))
                .toList();
    }

    private static List<DungeonRouteCell> exteriorPorts(
            DungeonCellBox box,
            DungeonConnectorSide side
    ) {
        List<DungeonRouteCell> ports = new ArrayList<>();

        switch (side) {
            case NORTH -> {
                for (int x = box.minX(); x < box.maxXExclusive(); x++) {
                    ports.add(new DungeonRouteCell(x, box.minZ() - 1));
                }
            }
            case SOUTH -> {
                for (int x = box.minX(); x < box.maxXExclusive(); x++) {
                    ports.add(new DungeonRouteCell(x, box.maxZExclusive()));
                }
            }
            case WEST -> {
                for (int z = box.minZ(); z < box.maxZExclusive(); z++) {
                    ports.add(new DungeonRouteCell(box.minX() - 1, z));
                }
            }
            case EAST -> {
                for (int z = box.minZ(); z < box.maxZExclusive(); z++) {
                    ports.add(new DungeonRouteCell(box.maxXExclusive(), z));
                }
            }
            case UP, DOWN -> throw new UnsupportedOperationException(
                    "Vertical routing is not supported: " + side
            );
        }

        return ports;
    }

    private static DungeonRouteCell roomCenter(DungeonLayoutNode room) {
        return roomCenter(room.cellBox());
    }

    private static DungeonRouteCell roomCenter(DungeonCellBox box) {
        return new DungeonRouteCell(
                box.minX() + box.sizeX() / 2,
                box.minZ() + box.sizeZ() / 2
        );
    }

    private static DungeonCellBox endpointBox(
            DungeonLayoutNode room,
            Map<String, DungeonCellBox> endpointBoxes
    ) {
        return endpointBoxes.getOrDefault(room.roomId(), room.cellBox());
    }

    private static boolean insidePlanningPadding(
            DungeonLayoutNode room,
            Map<String, DungeonCellBox> endpointBoxes,
            DungeonRouteCell cell
    ) {
        DungeonCellBox planning = room.cellBox();
        DungeonCellBox physical = endpointBox(room, endpointBoxes);
        boolean insidePlanning = cell.x() >= planning.minX()
                && cell.x() < planning.maxXExclusive()
                && cell.z() >= planning.minZ()
                && cell.z() < planning.maxZExclusive();
        boolean insidePhysical = cell.x() >= physical.minX()
                && cell.x() < physical.maxXExclusive()
                && cell.z() >= physical.minZ()
                && cell.z() < physical.maxZExclusive();
        return insidePlanning && !insidePhysical;
    }

    private static Comparator<DungeonLayoutEdge> edgeOrder(
            Map<String, DungeonLayoutNode> nodes
    ) {
        return Comparator
                .comparingInt((DungeonLayoutEdge edge) -> edgePriority(
                        edge,
                        nodes
                ))
                .thenComparing(
                        Comparator.comparingInt(
                                (DungeonLayoutEdge edge) -> estimatedDistance(
                                        edge,
                                        nodes
                                )
                        ).reversed()
                )
                .thenComparing(DungeonLayoutEdge::id);
    }

    private static int edgePriority(
            DungeonLayoutEdge edge,
            Map<String, DungeonLayoutNode> nodes
    ) {
        DungeonLayoutNode from = nodes.get(edge.fromRoomId());
        DungeonLayoutNode to = nodes.get(edge.toRoomId());

        return switch (edge.kind()) {
            case TREE -> 1;
            case LOOP -> 2;
            case SECRET -> 3;
        };
    }

    private static int estimatedDistance(
            DungeonLayoutEdge edge,
            Map<String, DungeonLayoutNode> nodes
    ) {
        DungeonLayoutNode from = nodes.get(edge.fromRoomId());
        DungeonLayoutNode to = nodes.get(edge.toRoomId());

        if (from == null || to == null) {
            return 0;
        }

        return DungeonRouteScorer.manhattan(roomCenter(from), roomCenter(to));
    }

    private static RoutingBounds searchBounds(
            List<DungeonLayoutNode> rooms
    ) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (DungeonLayoutNode room : rooms) {
            DungeonCellBox box = room.cellBox();
            minX = Math.min(minX, box.minX());
            minZ = Math.min(minZ, box.minZ());
            maxX = Math.max(maxX, box.maxXExclusive());
            maxZ = Math.max(maxZ, box.maxZExclusive());
        }

        return new RoutingBounds(
                minX,
                minZ,
                maxX,
                maxZ,
                minX - SEARCH_PADDING_CELLS,
                minZ - SEARCH_PADDING_CELLS,
                maxX + SEARCH_PADDING_CELLS,
                maxZ + SEARCH_PADDING_CELLS
        );
    }

    private static RoutingBounds searchBoundsResolved(
            List<ResolvedDungeonRoom> rooms
    ) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (ResolvedDungeonRoom room : rooms) {
            DungeonCellBox box = room.cellBox();
            minX = Math.min(minX, box.minX());
            minZ = Math.min(minZ, box.minZ());
            maxX = Math.max(maxX, box.maxXExclusive());
            maxZ = Math.max(maxZ, box.maxZExclusive());
        }

        return new RoutingBounds(
                minX,
                minZ,
                maxX,
                maxZ,
                minX - SEARCH_PADDING_CELLS,
                minZ - SEARCH_PADDING_CELLS,
                maxX + SEARCH_PADDING_CELLS,
                maxZ + SEARCH_PADDING_CELLS
        );
    }

    private static Comparator<RouteCandidate> routeCandidateOrder() {
        return Comparator
                .comparingInt(RouteCandidate::score)
                .thenComparingInt(candidate -> candidate.start().x())
                .thenComparingInt(candidate -> candidate.start().z())
                .thenComparingInt(candidate -> candidate.goal().x())
                .thenComparingInt(candidate -> candidate.goal().z());
    }

    private static Comparator<SearchNode> searchNodeOrder() {
        return Comparator
                .comparingInt(SearchNode::estimatedTotalCost)
                .thenComparingInt(SearchNode::cost)
                .thenComparingInt(node -> node.state().cell().x())
                .thenComparingInt(node -> node.state().cell().z())
                .thenComparingInt(node -> node.state().direction() == null
                        ? -1
                        : node.state().direction().ordinal())
                .thenComparingLong(SearchNode::sequence);
    }

    private static int heuristic(
            DungeonRouteCell current,
            DungeonRouteCell goal
    ) {
        return DungeonRouteScorer.manhattan(current, goal) * MOVE_COST;
    }

    private static int turnCost(
            DungeonConnectorSide previous,
            DungeonConnectorSide next
    ) {
        return previous == null || previous == next ? 0 : TURN_COST;
    }

    private static int countDungeonTurns(List<DungeonCellPos> path) {
        if (path.size() < 3) {
            return 0;
        }

        int turns = 0;
        int previousDx = Integer.compare(
                path.get(1).x() - path.get(0).x(),
                0
        );
        int previousDz = Integer.compare(
                path.get(1).z() - path.get(0).z(),
                0
        );

        for (int index = 2; index < path.size(); index++) {
            int dx = Integer.compare(
                    path.get(index).x() - path.get(index - 1).x(),
                    0
            );
            int dz = Integer.compare(
                    path.get(index).z() - path.get(index - 1).z(),
                    0
            );

            if (dx != previousDx || dz != previousDz) {
                turns++;
            }
            previousDx = dx;
            previousDz = dz;
        }

        return turns;
    }

    private record Step(
            DungeonRouteCell cell,
            DungeonConnectorSide direction
    ) {
    }

    private record RouteState(
            DungeonRouteCell cell,
            DungeonConnectorSide direction
    ) {
    }

    private record SearchNode(
            RouteState state,
            int cost,
            int estimatedTotalCost,
            long sequence
    ) {
    }

    private record RouteCandidate(
            DungeonRoutedCorridor corridor,
            DungeonRouteCell start,
            DungeonRouteCell goal,
            int score
    ) {
    }

    private record RoutingBounds(
            int hullMinX,
            int hullMinZ,
            int hullMaxXExclusive,
            int hullMaxZExclusive,
            int searchMinX,
            int searchMinZ,
            int searchMaxXExclusive,
            int searchMaxZExclusive
    ) {
        private boolean containsSearch(DungeonRouteCell cell) {
            return cell.x() >= this.searchMinX
                    && cell.x() < this.searchMaxXExclusive
                    && cell.z() >= this.searchMinZ
                    && cell.z() < this.searchMaxZExclusive;
        }

        private int outsideHullDistance(DungeonRouteCell cell) {
            int dx = 0;
            if (cell.x() < this.hullMinX) {
                dx = this.hullMinX - cell.x();
            } else if (cell.x() >= this.hullMaxXExclusive) {
                dx = cell.x() - this.hullMaxXExclusive + 1;
            }

            int dz = 0;
            if (cell.z() < this.hullMinZ) {
                dz = this.hullMinZ - cell.z();
            } else if (cell.z() >= this.hullMaxZExclusive) {
                dz = cell.z() - this.hullMaxZExclusive + 1;
            }

            return dx + dz;
        }
    }
}
