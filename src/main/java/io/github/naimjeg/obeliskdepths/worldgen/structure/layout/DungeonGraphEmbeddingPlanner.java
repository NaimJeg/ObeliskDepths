package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

import io.github.naimjeg.obeliskdepths.dungeon.geometry.*;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraph;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphAnalysis;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphAnalyzer;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphEdge;
import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonGraphEdgeKind;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphNode;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphValidator;
import io.github.naimjeg.obeliskdepths.worldgen.structure.generation.DungeonGenerationCatalog;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

/**
 * Converts the authoritative boss-rooted topology into a compact orthogonal
 * room layout.
 *
 * <p>This is a world-generation planner only. Runtime dungeon code must consume
 * generated layout/site data and must never create or reposition rooms.</p>
 */
public final class DungeonGraphEmbeddingPlanner {
    private static final int MIN_CORRIDOR_GAP_CELLS = 1;
    private static final int MAX_CORRIDOR_GAP_CELLS = 6;
    private static final int MAX_LATERAL_OFFSET_CELLS = 6;
    private static final int MAX_VALID_PLACEMENT_CANDIDATES = 64;
    private static final int MAX_ROUTED_PLACEMENT_CANDIDATES = 32;

    private static final int CORRIDOR_LENGTH_WEIGHT = 18;
    private static final int BOUNDING_GROWTH_WEIGHT = 3;
    private static final int CONGESTION_WEIGHT = 12;
    private static final int CONNECTOR_REUSE_WEIGHT = 48;
    private static final int LATERAL_OFFSET_WEIGHT = 2;
    private static final int PREFERRED_SIDE_WEIGHT = 1024;
    private static final int TURN_COST = 24;
    private static final int ROUTE_SEARCH_PADDING_CELLS = 10;
    private static final int MAX_ROUTE_SEARCH_STATES = 4_000;
    private static final int PHYSICAL_CORRIDOR_CLEARANCE_CELLS = 1;
    private static final long ROOM_SELECTION_SALT = 0x524F4F4D5F504F4FL;

    private static final List<DungeonConnectorSide> HORIZONTAL_SIDES = List.of(
            DungeonConnectorSide.NORTH,
            DungeonConnectorSide.EAST,
            DungeonConnectorSide.SOUTH,
            DungeonConnectorSide.WEST
    );

    private static final List<DungeonConnectorSide> ROOT_CHILD_SIDE_ORDER = List.of(
            DungeonConnectorSide.NORTH,
            DungeonConnectorSide.EAST,
            DungeonConnectorSide.WEST,
            DungeonConnectorSide.SOUTH
    );

    private DungeonGraphEmbeddingPlanner() {
    }

    public static DungeonLayoutPlan embed(
            DungeonGraph graph,
            BlockPos layoutOrigin,
            DungeonGenerationCatalog catalog
    ) {
        return embed(graph, layoutOrigin, catalog, 0L);
    }

    public static DungeonLayoutPlan embed(
            DungeonGraph graph,
            BlockPos layoutOrigin,
            DungeonGenerationCatalog catalog,
            long attemptSalt
    ) {
        DungeonGraphValidator.validate(graph);
        DungeonGraphAnalysis analysis = DungeonGraphAnalyzer.analyze(graph);
        return embed(
                graph,
                analysis,
                layoutOrigin,
                catalog,
                attemptSalt
        );
    }

    public static DungeonLayoutPlan embed(
            DungeonGraph graph,
            DungeonGraphAnalysis analysis,
            BlockPos layoutOrigin,
            DungeonGenerationCatalog catalog,
            long attemptSalt
    ) {
        Map<String, Draft> drafts = new LinkedHashMap<>();
        Map<String, EdgePlan> edgePlans = new LinkedHashMap<>();
        Set<GridCell> reservedCorridors = new HashSet<>();
        RandomSource roomRandom = RandomSource.create(
                attemptSalt ^ ROOM_SELECTION_SALT
        );

        DungeonGraphNode rootNode = graph.requireNode(graph.rootNodeId());
        Identifier rootDefinitionId = catalog.selectRoom(
                rootNode.type(),
                roomRandom,
                "embedding node=" + rootNode.id()
        );
        DungeonRoomFootprint rootFootprint =
                footprintFor(rootDefinitionId, catalog);
        Draft root = new Draft(
                rootNode.id(),
                rootNode.type(),
                rootDefinitionId,
                centeredOrigin(rootFootprint),
                rootFootprint,
                0,
                null,
                null,
                null
        );
        drafts.put(root.id(), root);

        placeTree(
                graph,
                analysis,
                root,
                drafts,
                edgePlans,
                reservedCorridors,
                catalog,
                roomRandom,
                attemptSalt
        );
        planAvailableNonTreeEdges(
                graph,
                drafts,
                edgePlans,
                reservedCorridors
        );

        if (drafts.size() != graph.nodes().size()) {
            List<String> missing = graph.nodes()
                    .stream()
                    .map(DungeonGraphNode::id)
                    .filter(id -> !drafts.containsKey(id))
                    .toList();
            throw new DungeonLayoutGenerationException(
                    "Compact embedding did not place every graph node: " + missing
            );
        }

        List<String> unplannedEdges = graph.edges()
                .stream()
                .map(DungeonGraphEdge::id)
                .filter(id -> !edgePlans.containsKey(id))
                .toList();
        if (!unplannedEdges.isEmpty()) {
            throw new DungeonLayoutGenerationException(
                    "Compact embedding did not plan every graph edge: "
                            + unplannedEdges
            );
        }

        Map<String, EnumSet<DungeonConnectorSide>> connectors = new LinkedHashMap<>();
        for (String nodeId : drafts.keySet()) {
            connectors.put(nodeId, EnumSet.noneOf(DungeonConnectorSide.class));
        }

        List<DungeonLayoutEdge> edges = new ArrayList<>();
        for (DungeonGraphEdge graphEdge : graph.edges()) {
            addEmbeddedEdge(
                    edges,
                    connectors,
                    edgePlans,
                    graphEdge
            );
        }

        List<DungeonLayoutNode> nodes = drafts.values()
                .stream()
                .map(draft -> new DungeonLayoutNode(
                        draft.id(),
                        draft.type(),
                        draft.definitionId(),
                        draft.cellOrigin(),
                        draft.footprint(),
                        connectors.get(draft.id())
                ))
                .toList();

        DungeonLayoutPlan plan = new DungeonLayoutPlan(
                DungeonLayoutConstants.CELL_SIZE,
                nodes,
                edges
        );
        DungeonSpatialLayoutValidator.validate(plan);
        return plan;
    }

    private static void placeTree(
            DungeonGraph graph,
            DungeonGraphAnalysis analysis,
            Draft root,
            Map<String, Draft> drafts,
            Map<String, EdgePlan> edgePlans,
            Set<GridCell> reservedCorridors,
            DungeonGenerationCatalog catalog,
            RandomSource roomRandom,
            long attemptSalt
    ) {
        Queue<String> queue = new ArrayDeque<>();
        queue.add(root.id());

        while (!queue.isEmpty()) {
            String parentId = queue.remove();
            Draft parent = requireDraft(drafts, parentId);
            List<DungeonGraphEdge> childEdges = graph.treeEdges()
                    .stream()
                    .filter(edge -> edge.sourceNodeId().equals(parentId))
                    .toList();
            Map<String, DungeonConnectorSide> sectorRootSides =
                    sectorRootSideAssignments(
                            analysis,
                            parent,
                            childEdges,
                            attemptSalt
                    );

            for (int childIndex = 0; childIndex < childEdges.size(); childIndex++) {
                DungeonGraphEdge edge = childEdges.get(childIndex);
                DungeonGraphNode childNode = graph.requireNode(edge.targetNodeId());

                if (drafts.containsKey(childNode.id())) {
                    throw new IllegalArgumentException(
                            "Tree node was placed more than once: " + childNode.id()
                    );
                }

                Identifier childDefinitionId = catalog.selectRoom(
                        childNode.type(),
                        roomRandom,
                        "embedding node=" + childNode.id()
                );
                DungeonRoomFootprint footprint =
                        footprintFor(childDefinitionId, catalog);
                PlacementPreference preference = placementPreference(
                        graph,
                        analysis,
                        parent,
                        childNode.id(),
                        childIndex,
                        sectorRootSides
                );
                PlacementCandidate candidate = chooseBestPlacement(
                        childNode.id(),
                        childDefinitionId,
                        parent,
                        footprint,
                        preference.side(),
                        preference.hard(),
                        drafts,
                        reservedCorridors,
                        attemptSalt
                );
                Integer childSector = sectorIndex(analysis, childNode.id());
                DungeonConnectorSide radialSide = childRadialSide(
                        parent,
                        childSector,
                        candidate.parentSide(),
                        sectorRootSides.containsKey(childNode.id())
                );
                Draft child = new Draft(
                        childNode.id(),
                        childNode.type(),
                        childDefinitionId,
                        candidate.origin(),
                        footprint,
                        parent.treeDepth() + 1,
                        candidate.parentSide().opposite(),
                        childSector,
                        radialSide
                );

                acceptTreePlacement(
                        parent,
                        child,
                        edge,
                        candidate,
                        drafts,
                        edgePlans,
                        reservedCorridors
                );
                queue.add(child.id());
            }
        }
    }

    private static PlacementPreference placementPreference(
            DungeonGraph graph,
            DungeonGraphAnalysis analysis,
            Draft parent,
            String childNodeId,
            int childIndex,
            Map<String, DungeonConnectorSide> sectorRootSides
    ) {
        DungeonConnectorSide sectorRootSide = sectorRootSides.get(childNodeId);
        if (sectorRootSide != null) {
            return new PlacementPreference(sectorRootSide, true);
        }

        if (parent.radialSide() != null) {
            return new PlacementPreference(parent.radialSide(), false);
        }

        if (parent.id().equals(graph.rootNodeId())) {
            return new PlacementPreference(ROOT_CHILD_SIDE_ORDER.get(
                    childIndex % ROOT_CHILD_SIDE_ORDER.size()
            ), false);
        }

        return new PlacementPreference(null, false);
    }

    private static Map<String, DungeonConnectorSide> sectorRootSideAssignments(
            DungeonGraphAnalysis analysis,
            Draft parent,
            List<DungeonGraphEdge> childEdges,
            long attemptSalt
    ) {
        if (parent.incomingSide() == null) {
            return Map.of();
        }

        List<String> sectorRoots = childEdges.stream()
                .map(DungeonGraphEdge::targetNodeId)
                .filter(childId -> isSectorRoot(analysis, parent.id(), childId))
                .toList();
        if (sectorRoots.size() < 2) {
            return Map.of();
        }

        List<DungeonConnectorSide> sideOrder = sectorSideOrder(parent.incomingSide());
        if (sectorRoots.size() > sideOrder.size()) {
            throw new DungeonLayoutGenerationException(
                    "Cannot assign distinct radial sides to sector roots: parent="
                            + parent.id()
                            + " roots="
                            + sectorRoots
                            + " freeSides="
                            + sideOrder
            );
        }

        List<String> orderedRoots = new ArrayList<>(sectorRoots);
        int rotation = Math.floorMod(
                mix(attemptSalt ^ parent.id().hashCode()),
                orderedRoots.size()
        );
        java.util.Collections.rotate(orderedRoots, -rotation);

        Map<String, DungeonConnectorSide> result = new LinkedHashMap<>();
        for (int index = 0; index < orderedRoots.size(); index++) {
            result.put(orderedRoots.get(index), sideOrder.get(index));
        }
        return Map.copyOf(result);
    }

    private static boolean isSectorRoot(
            DungeonGraphAnalysis analysis,
            String parentId,
            String childId
    ) {
        Integer childSector = sectorIndex(analysis, childId);
        if (childSector == null || childSector < 0) {
            return false;
        }

        Integer parentSector = sectorIndex(analysis, parentId);
        return parentSector == null || parentSector < 0
                || !parentSector.equals(childSector);
    }

    private static Integer sectorIndex(
            DungeonGraphAnalysis analysis,
            String nodeId
    ) {
        return analysis.requireNode(nodeId)
                .sectorIndex()
                .isPresent()
                ? analysis.requireNode(nodeId).sectorIndex().getAsInt()
                : null;
    }

    private static DungeonConnectorSide childRadialSide(
            Draft parent,
            Integer childSector,
            DungeonConnectorSide placedSide,
            boolean sectorRoot
    ) {
        if (childSector == null || childSector < 0) {
            return parent.radialSide();
        }
        if (sectorRoot) {
            return placedSide;
        }
        if (parent.radialSide() != null) {
            return parent.radialSide();
        }
        return placedSide;
    }

    private static List<DungeonConnectorSide> sectorSideOrder(
            DungeonConnectorSide incomingSide
    ) {
        DungeonConnectorSide forward = incomingSide.opposite();
        return HORIZONTAL_SIDES.stream()
                .filter(side -> side != incomingSide)
                .sorted(Comparator.comparingInt(side -> {
                    if (side == forward) {
                        return 0;
                    }
                    if (side == clockwise(forward)) {
                        return 1;
                    }
                    if (side == counterClockwise(forward)) {
                        return 2;
                    }
                    return 3;
                }))
                .toList();
    }

    private static DungeonConnectorSide clockwise(DungeonConnectorSide side) {
        return switch (side) {
            case NORTH -> DungeonConnectorSide.EAST;
            case EAST -> DungeonConnectorSide.SOUTH;
            case SOUTH -> DungeonConnectorSide.WEST;
            case WEST -> DungeonConnectorSide.NORTH;
            case UP, DOWN -> throw new UnsupportedOperationException(
                    "Vertical sector directions are not supported: " + side
            );
        };
    }

    private static DungeonConnectorSide counterClockwise(DungeonConnectorSide side) {
        return switch (side) {
            case NORTH -> DungeonConnectorSide.WEST;
            case WEST -> DungeonConnectorSide.SOUTH;
            case SOUTH -> DungeonConnectorSide.EAST;
            case EAST -> DungeonConnectorSide.NORTH;
            case UP, DOWN -> throw new UnsupportedOperationException(
                    "Vertical sector directions are not supported: " + side
            );
        };
    }

    private static int mix(long value) {
        long mixed = value;
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        mixed *= 0xc4ceb9fe1a85ec53L;
        mixed ^= mixed >>> 33;
        return (int) mixed;
    }

    private static void acceptTreePlacement(
            Draft parent,
            Draft child,
            DungeonGraphEdge edge,
            PlacementCandidate candidate,
            Map<String, Draft> drafts,
            Map<String, EdgePlan> edgePlans,
            Set<GridCell> reservedCorridors
    ) {
        parent.reserve(candidate.parentSide());
        child.reserve(candidate.parentSide().opposite());
        drafts.put(child.id(), child);
        reservedCorridors.addAll(candidate.corridorCells());
        edgePlans.put(
                edge.id(),
                new EdgePlan(
                        candidate.parentSide(),
                        candidate.parentSide().opposite(),
                        toDungeonPath(
                                candidate.corridorCells(),
                                parent.cellOrigin().y()
                        )
                )
        );
    }

    private static void planAvailableNonTreeEdges(
            DungeonGraph graph,
            Map<String, Draft> drafts,
            Map<String, EdgePlan> edgePlans,
            Set<GridCell> reservedCorridors
    ) {
        for (DungeonGraphEdge edge : graph.edges()) {
            if (edge.kind() == DungeonGraphEdgeKind.TREE
                    || edgePlans.containsKey(edge.id())
                    || !drafts.containsKey(edge.sourceNodeId())
                    || !drafts.containsKey(edge.targetNodeId())) {
                continue;
            }

            Draft source = requireDraft(drafts, edge.sourceNodeId());
            Draft target = requireDraft(drafts, edge.targetNodeId());
            EdgePlan plan = planNonTreeEdge(
                    edge,
                    source,
                    target,
                    drafts,
                    reservedCorridors
            ).orElseThrow(() -> new DungeonLayoutGenerationException(
                    "Unable to reserve a compact planned route for "
                            + edge.kind()
                            + " edge "
                            + edge.id()
            ));

            source.reserve(plan.fromSide());
            target.reserve(plan.toSide());
            edgePlans.put(edge.id(), plan);
            reservedCorridors.addAll(toGridPath(plan.path()));
        }
    }

    private static Optional<EdgePlan> planNonTreeEdge(
            DungeonGraphEdge edge,
            Draft source,
            Draft target,
            Map<String, Draft> drafts,
            Set<GridCell> reservedCorridors
    ) {
        RouteBounds bounds = routeBounds(drafts);
        EdgePlan best = null;
        int bestScore = Integer.MAX_VALUE;

        for (DungeonConnectorSide sourceSide : orderedSides(source, target)) {
            for (DungeonConnectorSide targetSide : orderedSides(target, source)) {
                for (GridCell start : exteriorCells(source.cellBox(), sourceSide, target)) {
                    for (GridCell goal : exteriorCells(target.cellBox(), targetSide, source)) {
                        List<GridCell> path = routeGridPath(
                                drafts,
                                reservedCorridors,
                                bounds,
                                edge.sourceNodeId(),
                                edge.targetNodeId(),
                                start,
                                goal
                        );

                        if (path.isEmpty()) {
                            continue;
                        }

                        int sidePenalty = 0;
                        if (sourceSide != connectorSide(source, target)) {
                            sidePenalty += PREFERRED_SIDE_WEIGHT;
                        }
                        if (targetSide != connectorSide(target, source)) {
                            sidePenalty += PREFERRED_SIDE_WEIGHT;
                        }

                        int score = path.size() * CORRIDOR_LENGTH_WEIGHT
                                + countTurns(path) * TURN_COST
                                + source.sideUseCount(sourceSide)
                                * CONNECTOR_REUSE_WEIGHT
                                + target.sideUseCount(targetSide)
                                * CONNECTOR_REUSE_WEIGHT
                                + sidePenalty;
                        EdgePlan candidate = new EdgePlan(
                                sourceSide,
                                targetSide,
                                toDungeonPath(path, source.cellOrigin().y())
                        );

                        if (best == null
                                || score < bestScore
                                || (score == bestScore
                                && edgePlanOrder(source, target)
                                .compare(candidate, best) < 0)) {
                            bestScore = score;
                            best = candidate;
                        }
                    }
                }
            }
        }

        return Optional.ofNullable(best);
    }

    private static PlacementCandidate chooseBestPlacement(
            String nodeId,
            Identifier definitionId,
            Draft parent,
            DungeonRoomFootprint footprint,
            DungeonConnectorSide preferredSide,
            boolean hardPreferredSide,
            Map<String, Draft> drafts,
            Set<GridCell> reservedCorridors,
            long attemptSalt
    ) {
        List<PlacementCandidate> roomLegalCandidates = new ArrayList<>();
        List<PlacementCandidate> straightCandidates = new ArrayList<>();

        candidateSearch:
        for (DungeonConnectorSide side : DungeonPlacementCandidateGenerator.sideOrder(
                HORIZONTAL_SIDES,
                preferredSide,
                hardPreferredSide
        )) {
            for (int gap = MIN_CORRIDOR_GAP_CELLS;
                 gap <= MAX_CORRIDOR_GAP_CELLS;
                 gap++) {
                for (int lateralOffset : DungeonPlacementCandidateGenerator.lateralOffsets(
                        MAX_LATERAL_OFFSET_CELLS
                )) {
                    PlacementCandidate candidate = candidateFor(
                            parent,
                            footprint,
                            side,
                            gap,
                            lateralOffset
                    );
                    DungeonCellBox candidateBox = footprint.toCellBox(
                            candidate.origin()
                    );

                    if (!isRoomPlacementLegal(
                            candidateBox,
                            drafts,
                            reservedCorridors
                    )) {
                        continue;
                    }

                    if (isCorridorLegal(
                            candidate.corridorCells(),
                            parent,
                            drafts,
                            reservedCorridors
                    )) {
                        straightCandidates.add(candidate.withScore(
                                placementScore(
                                        candidate,
                                        candidateBox,
                                        parent,
                                        preferredSide,
                                        drafts
                                ) + placementAttemptBias(parent, nodeId, candidate, attemptSalt)
                        ));
                        if (straightCandidates.size()
                                >= MAX_VALID_PLACEMENT_CANDIDATES) {
                            break candidateSearch;
                        }
                        continue;
                    }

                    /*
                     * Keep all room-legal fallback candidates.
                     *
                     * The preferred side is enumerated first and can produce more than
                     * MAX_VALID_PLACEMENT_CANDIDATES candidates by itself. Truncating here
                     * can prevent non-hard fallback sides from ever reaching routed
                     * pathfinding.
                     *
                     * The total search space is bounded:
                     * 4 sides * 6 gaps * 13 lateral offsets = 312 candidates.
                     */
                    roomLegalCandidates.add(candidate.withScore(
                            basePlacementScore(
                                    candidate,
                                    candidateBox,
                                    parent,
                                    preferredSide,
                                    drafts
                            ) + placementAttemptBias(
                                    parent,
                                    nodeId,
                                    candidate,
                                    attemptSalt
                            )
                    ));
                }
            }
        }

        Optional<PlacementCandidate> straight = selectBestCandidate(
                straightCandidates,
                preferredSide,
                hardPreferredSide
        );
        if (straight.isPresent()) {
            return straight.get();
        }

        List<PlacementCandidate> routedCandidates = new ArrayList<>();
        for (PlacementCandidate candidate : roomLegalCandidates) {
            Optional<PlacementCandidate> routed = routedPlacementCandidate(
                    nodeId,
                    definitionId,
                    parent,
                    footprint,
                    candidate,
                    preferredSide,
                    drafts,
                    reservedCorridors,
                    attemptSalt
            );
            routed.ifPresent(routedCandidates::add);
            if (routedCandidates.size() >= MAX_ROUTED_PLACEMENT_CANDIDATES) {
                break;
            }
        }

        Optional<PlacementCandidate> routed = selectBestCandidate(
                routedCandidates,
                preferredSide,
                hardPreferredSide
        );
        if (routed.isPresent()) {
            return routed.get();
        }

        throw new DungeonLayoutGenerationException(
                "Unable to place dungeon room with a clear straight routing gap: room="
                        + nodeId
                        + " parent="
                        + parent.id()
                        + " parentBox="
                        + parent.cellBox()
                        + " childFootprint="
                        + footprint
                        + " preferredSide="
                        + preferredSide
                        + " hardPreferredSide="
                        + hardPreferredSide
                        + " roomLegalCandidates="
                        + roomLegalCandidates.size()
                        + " straightCandidates="
                        + straightCandidates.size()
                        + " routedCandidates="
                        + routedCandidates.size()
        );
    }

    private static Optional<PlacementCandidate> routedPlacementCandidate(
            String nodeId,
            Identifier definitionId,
            Draft parent,
            DungeonRoomFootprint footprint,
            PlacementCandidate candidate,
            DungeonConnectorSide preferredSide,
            Map<String, Draft> drafts,
            Set<GridCell> reservedCorridors,
            long attemptSalt
    ) {
        Draft child = new Draft(
                nodeId,
                parent.type(),
                definitionId,
                candidate.origin(),
                footprint,
                parent.treeDepth() + 1,
                candidate.parentSide().opposite(),
                null,
                parent.radialSide()
        );
        Map<String, Draft> routedDrafts = new LinkedHashMap<>(drafts);
        routedDrafts.put(nodeId, child);
        RouteBounds bounds = routeBounds(routedDrafts);
        List<GridCell> bestPath = List.of();
        int bestScore = Integer.MAX_VALUE;

        for (GridCell start : exteriorCells(
                parent.cellBox(),
                candidate.parentSide(),
                child
        )) {
            for (GridCell goal : exteriorCells(
                    child.cellBox(),
                    candidate.parentSide().opposite(),
                    parent
            )) {
                List<GridCell> path = routeGridPath(
                        routedDrafts,
                        reservedCorridors,
                        bounds,
                        parent.id(),
                        nodeId,
                        start,
                        goal
                );
                if (path.isEmpty()) {
                    continue;
                }
                int score = path.size() * CORRIDOR_LENGTH_WEIGHT
                        + countTurns(path) * TURN_COST;
                if (bestPath.isEmpty()
                        || score < bestScore
                        || (score == bestScore
                        && gridPathOrder().compare(path, bestPath) < 0)) {
                    bestPath = path;
                    bestScore = score;
                }
            }
        }

        if (bestPath.isEmpty()) {
            return Optional.empty();
        }

        DungeonCellBox candidateBox = footprint.toCellBox(candidate.origin());
        return Optional.of(candidate
                .withCorridorCells(bestPath)
                .withScore(placementScore(
                        candidate.withCorridorCells(bestPath),
                        candidateBox,
                        parent,
                        preferredSide,
                        drafts
                ) + placementAttemptBias(parent, nodeId, candidate, attemptSalt)));
    }

    private static int placementAttemptBias(
            Draft parent,
            String nodeId,
            PlacementCandidate candidate,
            long attemptSalt
    ) {
        if (attemptSalt == 0L || parent.treeDepth() == 0) {
            return 0;
        }
        long value = attemptSalt
                ^ ((long) nodeId.hashCode() * 0x9E3779B97F4A7C15L)
                ^ ((long) candidate.parentSide().ordinal() * 0xD1B54A32D192ED03L)
                ^ ((long) candidate.gap() * 0x94D049BB133111EBL)
                ^ ((long) candidate.lateralOffset() * 0x632BE59BD9B4E019L);
        return Math.floorMod(mix(value), 768);
    }

    private static Optional<PlacementCandidate> selectBestCandidate(
            List<PlacementCandidate> candidates,
            DungeonConnectorSide preferredSide,
            boolean hardPreferredSide
    ) {
        List<PlacementCandidate> eligible = candidates;

        if (preferredSide != null && hardPreferredSide) {
            List<PlacementCandidate> preferred = candidates.stream()
                    .filter(candidate -> candidate.parentSide() == preferredSide)
                    .toList();

            eligible = preferred;
        }

        return eligible.stream().min(candidateOrder());
    }

    private static Comparator<PlacementCandidate> candidateOrder() {
        return Comparator
                .comparingInt(PlacementCandidate::score)
                .thenComparingInt(candidate -> candidate.parentSide().ordinal())
                .thenComparingInt(PlacementCandidate::gap)
                .thenComparingInt(candidate -> Math.abs(candidate.lateralOffset()))
                .thenComparingInt(PlacementCandidate::lateralOffset)
                .thenComparingInt(candidate -> candidate.origin().x())
                .thenComparingInt(candidate -> candidate.origin().z());
    }

    private static boolean isRoomPlacementLegal(
            DungeonCellBox candidateBox,
            Map<String, Draft> drafts,
            Set<GridCell> reservedCorridors
    ) {
        if (overlapsAny(candidateBox, drafts)) {
            return false;
        }

        DungeonCellBox corridorExclusionBox =
                candidateBox.expanded(PHYSICAL_CORRIDOR_CLEARANCE_CELLS);
        for (GridCell reserved : reservedCorridors) {
            if (insideBox(reserved, corridorExclusionBox)) {
                return false;
            }
        }

        return true;
    }

    private static boolean isCorridorLegal(
            List<GridCell> corridorCells,
            Draft parent,
            Map<String, Draft> drafts,
            Set<GridCell> reservedCorridors
    ) {
        if (corridorCells.isEmpty()) {
            return false;
        }

        Set<GridCell> reservedExclusion =
                expandedReservedCorridors(reservedCorridors);
        for (GridCell cell : corridorCells) {
            if (reservedExclusion.contains(cell)
                    || insideAnyRoom(
                    cell,
                    drafts,
                    parent.id(),
                    PHYSICAL_CORRIDOR_CLEARANCE_CELLS
            )) {
                return false;
            }
        }

        return true;
    }

    private static int basePlacementScore(
            PlacementCandidate candidate,
            DungeonCellBox candidateBox,
            Draft parent,
            DungeonConnectorSide preferredSide,
            Map<String, Draft> drafts
    ) {
        int preferredSidePenalty = preferredSide == null
                || preferredSide == candidate.parentSide()
                ? 0
                : 1;

        return candidate.gap() * CORRIDOR_LENGTH_WEIGHT
                + boundingAreaGrowth(candidateBox, drafts)
                * BOUNDING_GROWTH_WEIGHT
                + nearbyRoomCount(candidateBox, parent.id(), drafts)
                * CONGESTION_WEIGHT
                + parent.sideUseCount(candidate.parentSide())
                * CONNECTOR_REUSE_WEIGHT
                + Math.abs(candidate.lateralOffset())
                * LATERAL_OFFSET_WEIGHT
                + preferredSidePenalty * PREFERRED_SIDE_WEIGHT;
    }

    private static int placementScore(
            PlacementCandidate candidate,
            DungeonCellBox candidateBox,
            Draft parent,
            DungeonConnectorSide preferredSide,
            Map<String, Draft> drafts
    ) {
        int preferredSidePenalty = preferredSide == null
                || preferredSide == candidate.parentSide()
                ? 0
                : 1;

        return candidate.corridorCells().size() * CORRIDOR_LENGTH_WEIGHT
                + boundingAreaGrowth(candidateBox, drafts)
                * BOUNDING_GROWTH_WEIGHT
                + nearbyRoomCount(candidateBox, parent.id(), drafts)
                * CONGESTION_WEIGHT
                + parent.sideUseCount(candidate.parentSide())
                * CONNECTOR_REUSE_WEIGHT
                + Math.abs(candidate.lateralOffset())
                * LATERAL_OFFSET_WEIGHT
                + preferredSidePenalty * PREFERRED_SIDE_WEIGHT;
    }

    private static PlacementCandidate candidateFor(
            Draft parent,
            DungeonRoomFootprint childFootprint,
            DungeonConnectorSide side,
            int gap,
            int lateralOffset
    ) {
        DungeonCellBox parentBox = parent.cellBox();
        int childWidth = childFootprint.widthCells();
        int childDepth = childFootprint.depthCells();
        int centeredX = parentBox.minX()
                + (parentBox.sizeX() - childWidth) / 2;
        int centeredZ = parentBox.minZ()
                + (parentBox.sizeZ() - childDepth) / 2;

        DungeonCellPos origin = switch (side) {
            case NORTH -> new DungeonCellPos(
                    centeredX + lateralOffset,
                    parent.cellOrigin().y(),
                    parentBox.minZ() - gap - childDepth
            );
            case SOUTH -> new DungeonCellPos(
                    centeredX + lateralOffset,
                    parent.cellOrigin().y(),
                    parentBox.maxZExclusive() + gap
            );
            case WEST -> new DungeonCellPos(
                    parentBox.minX() - gap - childWidth,
                    parent.cellOrigin().y(),
                    centeredZ + lateralOffset
            );
            case EAST -> new DungeonCellPos(
                    parentBox.maxXExclusive() + gap,
                    parent.cellOrigin().y(),
                    centeredZ + lateralOffset
            );
            case UP, DOWN -> throw new UnsupportedOperationException(
                    "Vertical dungeon embedding is not supported: " + side
            );
        };
        DungeonCellBox childBox = childFootprint.toCellBox(origin);

        return new PlacementCandidate(
                origin,
                side,
                gap,
                lateralOffset,
                straightCorridorCells(parentBox, childBox, side),
                Integer.MAX_VALUE
        );
    }

    private static List<GridCell> straightCorridorCells(
            DungeonCellBox parent,
            DungeonCellBox child,
            DungeonConnectorSide side
    ) {
        List<GridCell> result = new ArrayList<>();

        switch (side) {
            case NORTH, SOUTH -> {
                int overlapMin = Math.max(parent.minX(), child.minX());
                int overlapMax = Math.min(
                        parent.maxXExclusive(),
                        child.maxXExclusive()
                );
                if (overlapMin >= overlapMax) {
                    return List.of();
                }

                int x = overlapMin + (overlapMax - overlapMin - 1) / 2;
                int firstZ = side == DungeonConnectorSide.NORTH
                        ? parent.minZ() - 1
                        : parent.maxZExclusive();
                int lastZ = side == DungeonConnectorSide.NORTH
                        ? child.maxZExclusive()
                        : child.minZ() - 1;
                int step = Integer.compare(lastZ, firstZ);
                for (int z = firstZ; ; z += step) {
                    result.add(new GridCell(x, z));
                    if (z == lastZ) {
                        break;
                    }
                }
            }
            case WEST, EAST -> {
                int overlapMin = Math.max(parent.minZ(), child.minZ());
                int overlapMax = Math.min(
                        parent.maxZExclusive(),
                        child.maxZExclusive()
                );
                if (overlapMin >= overlapMax) {
                    return List.of();
                }

                int z = overlapMin + (overlapMax - overlapMin - 1) / 2;
                int firstX = side == DungeonConnectorSide.WEST
                        ? parent.minX() - 1
                        : parent.maxXExclusive();
                int lastX = side == DungeonConnectorSide.WEST
                        ? child.maxXExclusive()
                        : child.minX() - 1;
                int step = Integer.compare(lastX, firstX);
                for (int x = firstX; ; x += step) {
                    result.add(new GridCell(x, z));
                    if (x == lastX) {
                        break;
                    }
                }
            }
            case UP, DOWN -> throw new UnsupportedOperationException(
                    "Vertical dungeon embedding is not supported: " + side
            );
        }

        return List.copyOf(result);
    }

    private static List<GridCell> routeGridPath(
            Map<String, Draft> drafts,
            Set<GridCell> reservedCorridors,
            RouteBounds bounds,
            String sourceRoomId,
            String targetRoomId,
            GridCell start,
            GridCell goal
    ) {
        Set<GridCell> blocked = blockedRoomCells(
                drafts,
                sourceRoomId,
                targetRoomId
        );
        blocked.addAll(expandedReservedCorridors(reservedCorridors));
        if (reservedCorridors.contains(start) || reservedCorridors.contains(goal)) {
            return List.of();
        }
        blocked.remove(start);
        blocked.remove(goal);

        SearchState startState = new SearchState(start, null);
        Map<SearchState, Integer> bestCost = new HashMap<>();
        Map<SearchState, SearchState> previous = new HashMap<>();
        PriorityQueue<SearchNode> open = new PriorityQueue<>(searchNodeOrder());
        long sequence = 0L;

        bestCost.put(startState, 0);
        open.add(new SearchNode(
                startState,
                0,
                manhattan(start, goal) * CORRIDOR_LENGTH_WEIGHT,
                sequence++
        ));

        int explored = 0;
        while (!open.isEmpty() && explored++ < MAX_ROUTE_SEARCH_STATES) {
            SearchNode currentNode = open.remove();
            SearchState current = currentNode.state();
            int knownCost = bestCost.getOrDefault(current, Integer.MAX_VALUE);
            if (currentNode.cost() != knownCost) {
                continue;
            }
            if (current.cell().equals(goal)) {
                return reconstruct(previous, current);
            }

            for (Step step : orderedSteps(current.cell(), goal)) {
                GridCell nextCell = step.cell();
                if (!bounds.contains(nextCell) || blocked.contains(nextCell)) {
                    continue;
                }

                int stepCost = CORRIDOR_LENGTH_WEIGHT
                        + turnCost(current.direction(), step.direction())
                        + routeRoomProximityPenalty(
                        nextCell,
                        drafts,
                        sourceRoomId,
                        targetRoomId
                );
                int tentativeCost = currentNode.cost() + stepCost;
                SearchState next = new SearchState(nextCell, step.direction());
                if (tentativeCost >= bestCost.getOrDefault(next, Integer.MAX_VALUE)) {
                    continue;
                }

                bestCost.put(next, tentativeCost);
                previous.put(next, current);
                open.add(new SearchNode(
                        next,
                        tentativeCost,
                        tentativeCost + manhattan(nextCell, goal) * CORRIDOR_LENGTH_WEIGHT,
                        sequence++
                ));
            }
        }

        return List.of();
    }

    private static Set<GridCell> expandedReservedCorridors(
            Set<GridCell> reservedCorridors
    ) {
        Set<GridCell> expanded = new HashSet<>();
        for (GridCell cell : reservedCorridors) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    expanded.add(new GridCell(cell.x() + dx, cell.z() + dz));
                }
            }
        }
        return expanded;
    }

    private static Set<GridCell> blockedRoomCells(
            Map<String, Draft> drafts,
            String sourceRoomId,
            String targetRoomId
    ) {
        Set<GridCell> blocked = new HashSet<>();
        for (Draft draft : drafts.values()) {
            DungeonCellBox box = draft.id().equals(sourceRoomId)
                    || draft.id().equals(targetRoomId)
                    ? draft.cellBox()
                    : draft.cellBox().expanded(PHYSICAL_CORRIDOR_CLEARANCE_CELLS);
            for (int x = box.minX(); x < box.maxXExclusive(); x++) {
                for (int z = box.minZ(); z < box.maxZExclusive(); z++) {
                    blocked.add(new GridCell(x, z));
                }
            }
        }
        return blocked;
    }

    private static int routeRoomProximityPenalty(
            GridCell cell,
            Map<String, Draft> drafts,
            String sourceRoomId,
            String targetRoomId
    ) {
        int penalty = 0;
        for (Draft draft : drafts.values()) {
            if (draft.id().equals(sourceRoomId)
                    || draft.id().equals(targetRoomId)) {
                continue;
            }
            int distance = distanceToBox(cell, draft.cellBox());
            if (distance == 1) {
                penalty += CONGESTION_WEIGHT;
            } else if (distance == 2) {
                penalty += CONGESTION_WEIGHT / 2;
            }
        }
        return penalty;
    }

    private static int distanceToBox(
            GridCell cell,
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

    private static List<GridCell> reconstruct(
            Map<SearchState, SearchState> previous,
            SearchState current
    ) {
        ArrayList<GridCell> path = new ArrayList<>();
        path.add(current.cell());
        while (previous.containsKey(current)) {
            current = previous.get(current);
            path.add(current.cell());
        }
        java.util.Collections.reverse(path);
        return path;
    }

    private static List<Step> orderedSteps(
            GridCell current,
            GridCell goal
    ) {
        List<Step> steps = new ArrayList<>(List.of(
                new Step(new GridCell(current.x(), current.z() - 1), DungeonConnectorSide.NORTH),
                new Step(new GridCell(current.x() + 1, current.z()), DungeonConnectorSide.EAST),
                new Step(new GridCell(current.x(), current.z() + 1), DungeonConnectorSide.SOUTH),
                new Step(new GridCell(current.x() - 1, current.z()), DungeonConnectorSide.WEST)
        ));
        steps.sort(Comparator
                .comparingInt((Step step) -> manhattan(step.cell(), goal))
                .thenComparingInt(step -> step.direction().ordinal()));
        return steps;
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

    private static int turnCost(
            DungeonConnectorSide previous,
            DungeonConnectorSide next
    ) {
        return previous == null || previous == next ? 0 : TURN_COST;
    }

    private static int countTurns(List<GridCell> path) {
        if (path.size() < 3) {
            return 0;
        }

        int turns = 0;
        int previousDx = Integer.compare(path.get(1).x() - path.get(0).x(), 0);
        int previousDz = Integer.compare(path.get(1).z() - path.get(0).z(), 0);

        for (int index = 2; index < path.size(); index++) {
            int dx = Integer.compare(path.get(index).x() - path.get(index - 1).x(), 0);
            int dz = Integer.compare(path.get(index).z() - path.get(index - 1).z(), 0);
            if (dx != previousDx || dz != previousDz) {
                turns++;
            }
            previousDx = dx;
            previousDz = dz;
        }

        return turns;
    }

    private static Comparator<List<GridCell>> gridPathOrder() {
        return Comparator
                .comparingInt((List<GridCell> path) -> path.size())
                .thenComparingInt(path -> countTurns(path))
                .thenComparingInt(path -> path.getFirst().x())
                .thenComparingInt(path -> path.getFirst().z())
                .thenComparingInt(path -> path.getLast().x())
                .thenComparingInt(path -> path.getLast().z());
    }

    private static int manhattan(
            GridCell first,
            GridCell second
    ) {
        return Math.abs(first.x() - second.x())
                + Math.abs(first.z() - second.z());
    }

    private static RouteBounds routeBounds(Map<String, Draft> drafts) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (Draft draft : drafts.values()) {
            DungeonCellBox box = draft.cellBox();
            minX = Math.min(minX, box.minX());
            minZ = Math.min(minZ, box.minZ());
            maxX = Math.max(maxX, box.maxXExclusive());
            maxZ = Math.max(maxZ, box.maxZExclusive());
        }

        return new RouteBounds(
                minX - ROUTE_SEARCH_PADDING_CELLS,
                minZ - ROUTE_SEARCH_PADDING_CELLS,
                maxX + ROUTE_SEARCH_PADDING_CELLS,
                maxZ + ROUTE_SEARCH_PADDING_CELLS
        );
    }

    private static List<DungeonConnectorSide> orderedSides(
            Draft source,
            Draft target
    ) {
        DungeonConnectorSide preferred = connectorSide(source, target);
        return HORIZONTAL_SIDES.stream()
                .sorted(Comparator
                        .comparingInt((DungeonConnectorSide side) -> side == preferred ? 0 : 1)
                        .thenComparingInt(source::sideUseCount)
                        .thenComparingInt(Enum::ordinal))
                .toList();
    }

    private static List<GridCell> exteriorCells(
            DungeonCellBox box,
            DungeonConnectorSide side,
            Draft target
    ) {
        List<GridCell> cells = new ArrayList<>();
        switch (side) {
            case NORTH -> {
                for (int x = box.minX(); x < box.maxXExclusive(); x++) {
                    cells.add(new GridCell(x, box.minZ() - 1));
                }
            }
            case SOUTH -> {
                for (int x = box.minX(); x < box.maxXExclusive(); x++) {
                    cells.add(new GridCell(x, box.maxZExclusive()));
                }
            }
            case WEST -> {
                for (int z = box.minZ(); z < box.maxZExclusive(); z++) {
                    cells.add(new GridCell(box.minX() - 1, z));
                }
            }
            case EAST -> {
                for (int z = box.minZ(); z < box.maxZExclusive(); z++) {
                    cells.add(new GridCell(box.maxXExclusive(), z));
                }
            }
            case UP, DOWN -> throw new UnsupportedOperationException(
                    "Vertical dungeon embedding is not supported: " + side
            );
        }

        GridCell targetCenter = center(target.cellBox());
        cells.sort(Comparator
                .comparingInt((GridCell cell) -> manhattan(cell, targetCenter))
                .thenComparingInt(GridCell::x)
                .thenComparingInt(GridCell::z));
        return cells;
    }

    private static GridCell center(DungeonCellBox box) {
        return new GridCell(
                box.minX() + box.sizeX() / 2,
                box.minZ() + box.sizeZ() / 2
        );
    }

    private static List<DungeonCellPos> toDungeonPath(
            List<GridCell> path,
            int y
    ) {
        return path.stream()
                .map(cell -> new DungeonCellPos(cell.x(), y, cell.z()))
                .toList();
    }

    private static List<GridCell> toGridPath(List<DungeonCellPos> path) {
        return path.stream()
                .map(cell -> new GridCell(cell.x(), cell.z()))
                .toList();
    }

    private static Comparator<EdgePlan> edgePlanOrder(
            Draft source,
            Draft target
    ) {
        return Comparator
                .comparingInt((EdgePlan plan) -> plan.fromSide().ordinal())
                .thenComparingInt(plan -> plan.toSide().ordinal())
                .thenComparingInt(plan -> plan.path().size())
                .thenComparingInt(plan -> plan.path().getFirst().x())
                .thenComparingInt(plan -> plan.path().getFirst().z())
                .thenComparingInt(plan -> manhattan(
                        new GridCell(
                                plan.path().getLast().x(),
                                plan.path().getLast().z()
                        ),
                        center(target.cellBox())
                ))
                .thenComparingInt(plan -> source.sideUseCount(plan.fromSide()))
                .thenComparingInt(plan -> target.sideUseCount(plan.toSide()));
    }

    private static boolean overlapsAny(
            DungeonCellBox candidate,
            Map<String, Draft> drafts
    ) {
        return drafts.values()
                .stream()
                .map(Draft::cellBox)
                .anyMatch(candidate::intersects);
    }

    private static boolean insideAnyRoom(
            GridCell cell,
            Map<String, Draft> drafts,
            String ignoredRoomId,
            int clearanceCells
    ) {
        for (Draft draft : drafts.values()) {
            DungeonCellBox box = draft.cellBox();
            if (!draft.id().equals(ignoredRoomId)
                    && insideBox(cell, box.expanded(clearanceCells))) {
                return true;
            }
        }

        return false;
    }

    private static boolean insideBox(
            GridCell cell,
            DungeonCellBox box
    ) {
        return cell.x() >= box.minX()
                && cell.x() < box.maxXExclusive()
                && cell.z() >= box.minZ()
                && cell.z() < box.maxZExclusive();
    }

    private static int boundingAreaGrowth(
            DungeonCellBox candidate,
            Map<String, Draft> drafts
    ) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (Draft draft : drafts.values()) {
            DungeonCellBox box = draft.cellBox();
            minX = Math.min(minX, box.minX());
            minZ = Math.min(minZ, box.minZ());
            maxX = Math.max(maxX, box.maxXExclusive());
            maxZ = Math.max(maxZ, box.maxZExclusive());
        }

        int oldArea = (maxX - minX) * (maxZ - minZ);
        int newMinX = Math.min(minX, candidate.minX());
        int newMinZ = Math.min(minZ, candidate.minZ());
        int newMaxX = Math.max(maxX, candidate.maxXExclusive());
        int newMaxZ = Math.max(maxZ, candidate.maxZExclusive());
        int newArea = (newMaxX - newMinX) * (newMaxZ - newMinZ);
        return newArea - oldArea;
    }

    private static int nearbyRoomCount(
            DungeonCellBox candidate,
            String parentId,
            Map<String, Draft> drafts
    ) {
        DungeonCellBox nearby = candidate.expanded(2);
        int count = 0;

        for (Draft draft : drafts.values()) {
            if (!draft.id().equals(parentId)
                    && nearby.intersects(draft.cellBox())) {
                count++;
            }
        }

        return count;
    }

    private static DungeonCellPos centeredOrigin(
            DungeonRoomFootprint footprint
    ) {
        return new DungeonCellPos(
                -footprint.widthCells() / 2,
                0,
                -footprint.depthCells() / 2
        );
    }

    private static void addEmbeddedEdge(
            List<DungeonLayoutEdge> edges,
            Map<String, EnumSet<DungeonConnectorSide>> connectors,
            Map<String, EdgePlan> edgePlans,
            DungeonGraphEdge graphEdge
    ) {
        EdgePlan plan = edgePlans.get(graphEdge.id());
        if (plan == null) {
            throw new DungeonLayoutGenerationException(
                    "Missing embedded edge plan: " + graphEdge.id()
            );
        }

        connectors.get(graphEdge.sourceNodeId()).add(plan.fromSide());
        connectors.get(graphEdge.targetNodeId()).add(plan.toSide());

        edges.add(new DungeonLayoutEdge(
                layoutEdgeIdFor(graphEdge),
                graphEdge.sourceNodeId(),
                graphEdge.targetNodeId(),
                plan.fromSide(),
                plan.toSide(),
                1,
                graphEdge.kind(),
                plan.path()
        ));
    }

    public static String layoutEdgeIdFor(DungeonGraphEdge graphEdge) {
        if (graphEdge == null) {
            throw new IllegalArgumentException("Graph edge is required");
        }
        return layoutEdgeIdFor(graphEdge.id());
    }

    public static String layoutEdgeIdFor(String graphEdgeId) {
        if (graphEdgeId == null || graphEdgeId.isBlank()) {
            throw new IllegalArgumentException("Graph edge id is required");
        }
        return graphEdgeId.replaceFirst(
                "^(tree|loop|secret)_",
                "corridor_$1_"
        );
    }

    private static DungeonConnectorSide connectorSide(
            Draft source,
            Draft target
    ) {
        int sourceCenterX = source.cellOrigin().x()
                + source.footprint().widthCells() / 2;
        int sourceCenterZ = source.cellOrigin().z()
                + source.footprint().depthCells() / 2;
        int targetCenterX = target.cellOrigin().x()
                + target.footprint().widthCells() / 2;
        int targetCenterZ = target.cellOrigin().z()
                + target.footprint().depthCells() / 2;
        int dx = targetCenterX - sourceCenterX;
        int dz = targetCenterZ - sourceCenterZ;

        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0
                    ? DungeonConnectorSide.EAST
                    : DungeonConnectorSide.WEST;
        }

        return dz >= 0
                ? DungeonConnectorSide.SOUTH
                : DungeonConnectorSide.NORTH;
    }

    private static Draft requireDraft(
            Map<String, Draft> drafts,
            String nodeId
    ) {
        Draft draft = drafts.get(nodeId);
        if (draft == null) {
            throw new IllegalArgumentException(
                    "Cannot embed graph edge before both endpoints are placed: "
                            + nodeId
            );
        }
        return draft;
    }

    private static DungeonRoomFootprint footprintFor(
            Identifier definitionId,
            DungeonGenerationCatalog catalog
    ) {
        DungeonRoomDefinition definition = catalog.requireRoom(
                definitionId,
                "embedding footprint theme=" + catalog.themeId()
        );

        DungeonRoomFootprint explicitFootprint = definition.footprint();
        DungeonCellBox routingBox;

        if (!explicitFootprint.isAuto()) {
            routingBox = explicitFootprint.toCellBox(new DungeonCellPos(0, 0, 0));
        } else {
            DungeonTemplateGeometry geometry =
                    catalog.geometry().resolve(definition.template());
            DungeonBlockBox exactTemplateBox = DungeonBlockBox.fromMinAndSize(
                    definition.templateOffset(),
                    geometry.sizeX(),
                    geometry.sizeY(),
                    geometry.sizeZ()
            );
            routingBox = exactTemplateBox.toRoutingCellBox(BlockPos.ZERO);
        }

        return DungeonRoomFootprint.rectangular(
                routingBox.sizeX(),
                routingBox.sizeY(),
                routingBox.sizeZ()
        );
    }

    private static final class Draft {
        private final String id;
        private final DungeonRoomType type;
        private final Identifier definitionId;
        private final DungeonCellPos cellOrigin;
        private final DungeonRoomFootprint footprint;
        private final int treeDepth;
        private final DungeonConnectorSide incomingSide;
        private final Integer sectorIndex;
        private final DungeonConnectorSide radialSide;
        private final EnumMap<DungeonConnectorSide, Integer> sideUseCounts =
                new EnumMap<>(DungeonConnectorSide.class);

        private Draft(
                String id,
                DungeonRoomType type,
                Identifier definitionId,
                DungeonCellPos cellOrigin,
                DungeonRoomFootprint footprint,
                int treeDepth,
                DungeonConnectorSide incomingSide,
                Integer sectorIndex,
                DungeonConnectorSide radialSide
        ) {
            this.id = id;
            this.type = type;
            this.definitionId = definitionId;
            this.cellOrigin = cellOrigin;
            this.footprint = footprint;
            this.treeDepth = treeDepth;
            this.incomingSide = incomingSide;
            this.sectorIndex = sectorIndex;
            this.radialSide = radialSide;
        }

        private String id() {
            return this.id;
        }

        private DungeonRoomType type() {
            return this.type;
        }

        private Identifier definitionId() {
            return this.definitionId;
        }

        private DungeonCellPos cellOrigin() {
            return this.cellOrigin;
        }

        private DungeonRoomFootprint footprint() {
            return this.footprint;
        }

        private DungeonCellBox cellBox() {
            return this.footprint.toCellBox(this.cellOrigin);
        }

        private int treeDepth() {
            return this.treeDepth;
        }

        private DungeonConnectorSide incomingSide() {
            return this.incomingSide;
        }

        private Integer sectorIndex() {
            return this.sectorIndex;
        }

        private DungeonConnectorSide radialSide() {
            return this.radialSide;
        }

        private void reserve(DungeonConnectorSide side) {
            this.sideUseCounts.merge(side, 1, Integer::sum);
        }

        private int sideUseCount(DungeonConnectorSide side) {
            return this.sideUseCounts.getOrDefault(side, 0);
        }
    }

    private record GridCell(
            int x,
            int z
    ) {
    }

    private record EdgePlan(
            DungeonConnectorSide fromSide,
            DungeonConnectorSide toSide,
            List<DungeonCellPos> path
    ) {
        private EdgePlan {
            path = List.copyOf(path);
        }
    }

    private record PlacementPreference(
            DungeonConnectorSide side,
            boolean hard
    ) {
    }

    private record RouteBounds(
            int minX,
            int minZ,
            int maxXExclusive,
            int maxZExclusive
    ) {
        private boolean contains(GridCell cell) {
            return cell.x() >= this.minX
                    && cell.x() < this.maxXExclusive
                    && cell.z() >= this.minZ
                    && cell.z() < this.maxZExclusive;
        }
    }

    private record PlacementCandidate(
            DungeonCellPos origin,
            DungeonConnectorSide parentSide,
            int gap,
            int lateralOffset,
            List<GridCell> corridorCells,
            int score
    ) {
        private PlacementCandidate {
            corridorCells = List.copyOf(corridorCells);
        }

        private PlacementCandidate withScore(int newScore) {
            return new PlacementCandidate(
                    this.origin,
                    this.parentSide,
                    this.gap,
                    this.lateralOffset,
                    this.corridorCells,
                    newScore
            );
        }

        private PlacementCandidate withCorridorCells(List<GridCell> newCells) {
            return new PlacementCandidate(
                    this.origin,
                    this.parentSide,
                    this.gap,
                    this.lateralOffset,
                    newCells,
                    this.score
            );
        }

    }

    private record Step(
            GridCell cell,
            DungeonConnectorSide direction
    ) {
    }

    private record SearchState(
            GridCell cell,
            DungeonConnectorSide direction
    ) {
    }

    private record SearchNode(
            SearchState state,
            int cost,
            int estimatedTotalCost,
            long sequence
    ) {
    }
}
