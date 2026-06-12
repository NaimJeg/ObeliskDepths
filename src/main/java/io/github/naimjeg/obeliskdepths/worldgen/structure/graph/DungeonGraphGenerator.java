package io.github.naimjeg.obeliskdepths.worldgen.structure.graph;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutGenerationProfile;
import java.util.ArrayList;
import java.util.List;

public final class DungeonGraphGenerator {
    private DungeonGraphGenerator() {
    }

    public static DungeonGraph generate(
            long generationSeed,
            DungeonLayoutGenerationProfile profile
    ) {
        if (profile == null) {
            throw new IllegalArgumentException("Dungeon graph generation profile must be present");
        }

        int criticalPathLength = profile.criticalPathLength(generationSeed);
        int combatCount = Math.max(1, criticalPathLength - 3);
        int branchCount = Math.min(profile.branches(generationSeed), combatCount);

        List<DungeonGraphNode> nodes = new ArrayList<>();
        List<DungeonGraphEdge> edges = new ArrayList<>();
        int criticalIndex = 0;

        nodes.add(DungeonGraphNode.critical("start", DungeonRoomType.START, criticalIndex++));
        String previous = "start";

        for (int index = 1; index <= combatCount; index++) {
            String combatId = String.format("combat_%02d", index);
            nodes.add(DungeonGraphNode.critical(combatId, DungeonRoomType.COMBAT, criticalIndex++));
            edges.add(new DungeonGraphEdge(edgeId(previous, combatId), previous, combatId));
            previous = combatId;
        }

        nodes.add(DungeonGraphNode.critical("boss", DungeonRoomType.BOSS, criticalIndex++));
        edges.add(new DungeonGraphEdge(edgeId(previous, "boss"), previous, "boss"));

        nodes.add(DungeonGraphNode.critical("exit", DungeonRoomType.EXIT, criticalIndex));
        edges.add(new DungeonGraphEdge(edgeId("boss", "exit"), "boss", "exit"));

        for (int branch = 1; branch <= branchCount; branch++) {
            int combatIndex = 1 + Math.floorMod(branch * 2 - 1, combatCount);
            String combatId = String.format("combat_%02d", combatIndex);
            String branchId = String.format("treasure_%02d", branch);

            nodes.add(DungeonGraphNode.branchCap(branchId, DungeonRoomType.TREASURE));
            edges.add(new DungeonGraphEdge(edgeId(combatId, branchId), combatId, branchId));
        }

        DungeonGraph graph = new DungeonGraph(nodes, edges);
        DungeonGraphValidator.validate(graph);
        return graph;
    }

    private static String edgeId(
            String sourceNodeId,
            String targetNodeId
    ) {
        return "edge_" + sourceNodeId + "_" + targetNodeId;
    }
}
