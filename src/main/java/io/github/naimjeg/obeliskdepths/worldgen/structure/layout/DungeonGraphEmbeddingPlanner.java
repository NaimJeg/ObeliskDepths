package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraph;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphEdge;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphNode;
import io.github.naimjeg.obeliskdepths.worldgen.structure.graph.DungeonGraphValidator;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;

/**
 * Thin spatial adapter from pure graph topology to debug cell/block placement.
 * The graph remains authoritative; this layer assigns temporary footprints and
 * connector sides for StructurePiece metadata and floor-only visualization.
 */
public final class DungeonGraphEmbeddingPlanner {
    private DungeonGraphEmbeddingPlanner() {
    }

    public static DungeonLayoutPlan embed(
            DungeonGraph graph,
            BlockPos layoutOrigin
    ) {
        DungeonGraphValidator.validate(graph);

        Map<String, Draft> drafts = new LinkedHashMap<>();
        Map<String, EnumSet<DungeonConnectorSide>> connectors = new LinkedHashMap<>();
        List<DungeonLayoutEdge> edges = new ArrayList<>();
        int cursorX = 0;

        for (DungeonGraphNode node : graph.criticalPathNodes()) {
            DungeonRoomFootprint footprint = footprintFor(node.type());
            int z = node.type() == DungeonRoomType.BOSS ? -1 : 0;
            DungeonCellPos cellOrigin = new DungeonCellPos(cursorX, 0, z);

            addDraft(drafts, connectors, new Draft(node, cellOrigin, footprint));
            cursorX += footprint.widthCells() + spacingAfter(node.type());
        }

        int branchIndex = 0;
        Map<String, Integer> branchSideCounts = new LinkedHashMap<>();

        for (DungeonGraphEdge graphEdge : graph.edges()) {
            DungeonGraphNode target = graph.requireNode(graphEdge.targetNodeId());

            if (target.criticalPath()) {
                addEmbeddedEdge(edges, connectors, drafts, graphEdge);
                continue;
            }

            Draft parent = drafts.get(graphEdge.sourceNodeId());
            if (parent == null) {
                throw new IllegalArgumentException(
                        "Branch parent has not been spatially embedded: " + graphEdge.sourceNodeId()
                );
            }

            DungeonRoomFootprint footprint = footprintFor(target.type());
            boolean south = branchIndex % 2 == 0;
            String sideKey = graphEdge.sourceNodeId() + ":" + (south ? "south" : "north");
            int sideCount = branchSideCounts.getOrDefault(sideKey, 0);
            branchSideCounts.put(sideKey, sideCount + 1);
            int branchZ = south
                    ? parent.cellOrigin.z()
                    + parent.footprint.depthCells()
                    + 2
                    + sideCount * (footprint.depthCells() + 2)
                    : parent.cellOrigin.z()
                    - footprint.depthCells()
                    - 2
                    - sideCount * (footprint.depthCells() + 2);

            addDraft(drafts, connectors, new Draft(
                    target,
                    new DungeonCellPos(parent.cellOrigin.x(), 0, branchZ),
                    footprint
            ));
            addEmbeddedEdge(edges, connectors, drafts, graphEdge);
            branchIndex++;
        }

        List<DungeonLayoutNode> nodes = drafts.values()
                .stream()
                .map(draft -> new DungeonLayoutNode(
                        draft.node.id(),
                        draft.node.type(),
                        draft.cellOrigin,
                        draft.footprint,
                        connectors.get(draft.node.id()),
                        draft.node.criticalPath(),
                        draft.node.branchCap()
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

    private static void addDraft(
            Map<String, Draft> drafts,
            Map<String, EnumSet<DungeonConnectorSide>> connectors,
            Draft draft
    ) {
        drafts.put(draft.node.id(), draft);
        connectors.put(draft.node.id(), EnumSet.noneOf(DungeonConnectorSide.class));
    }

    private static void addEmbeddedEdge(
            List<DungeonLayoutEdge> edges,
            Map<String, EnumSet<DungeonConnectorSide>> connectors,
            Map<String, Draft> drafts,
            DungeonGraphEdge graphEdge
    ) {
        Draft source = drafts.get(graphEdge.sourceNodeId());
        Draft target = drafts.get(graphEdge.targetNodeId());

        if (source == null || target == null) {
            throw new IllegalArgumentException(
                    "Cannot embed graph edge before both endpoints are placed: " + graphEdge.id()
            );
        }

        DungeonConnectorSide sourceSide = connectorSide(source, target);
        DungeonConnectorSide targetSide = sourceSide.opposite();
        connectors.get(graphEdge.sourceNodeId()).add(sourceSide);
        connectors.get(graphEdge.targetNodeId()).add(targetSide);

        edges.add(new DungeonLayoutEdge(
                graphEdge.id().replaceFirst("^edge_", "corridor_"),
                graphEdge.sourceNodeId(),
                graphEdge.targetNodeId(),
                sourceSide,
                targetSide,
                1
        ));
    }

    private static DungeonConnectorSide connectorSide(
            Draft source,
            Draft target
    ) {
        int sourceCenterX = source.cellOrigin.x() + source.footprint.widthCells() / 2;
        int sourceCenterZ = source.cellOrigin.z() + source.footprint.depthCells() / 2;
        int targetCenterX = target.cellOrigin.x() + target.footprint.widthCells() / 2;
        int targetCenterZ = target.cellOrigin.z() + target.footprint.depthCells() / 2;
        int dx = targetCenterX - sourceCenterX;
        int dz = targetCenterZ - sourceCenterZ;

        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? DungeonConnectorSide.EAST : DungeonConnectorSide.WEST;
        }

        return dz >= 0 ? DungeonConnectorSide.SOUTH : DungeonConnectorSide.NORTH;
    }

    private static DungeonRoomFootprint footprintFor(DungeonRoomType type) {
        return switch (type) {
            case START, EXIT, TREASURE -> new DungeonRoomFootprint(2, 1, 2);
            case COMBAT -> new DungeonRoomFootprint(3, 1, 3);
            case BOSS -> new DungeonRoomFootprint(5, 1, 5);
        };
    }

    private static int spacingAfter(DungeonRoomType type) {
        return switch (type) {
            case START -> 2;
            case BOSS -> 2;
            case EXIT -> 0;
            case COMBAT, TREASURE -> 2;
        };
    }

    private record Draft(
            DungeonGraphNode node,
            DungeonCellPos cellOrigin,
            DungeonRoomFootprint footprint
    ) {
    }
}
