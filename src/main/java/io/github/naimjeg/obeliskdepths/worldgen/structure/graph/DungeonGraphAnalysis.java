package io.github.naimjeg.obeliskdepths.worldgen.structure.graph;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record DungeonGraphAnalysis(
        Map<String, DungeonNodeAnalysis> nodes,
        Map<String, String> treeParentByNode,
        Map<String, List<String>> treeChildrenByNode,
        List<DungeonDepthBand> depthBands,
        List<DungeonSector> sectors
) {
    public DungeonGraphAnalysis {
        nodes = Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
        treeParentByNode = Collections.unmodifiableMap(new LinkedHashMap<>(treeParentByNode));
        Map<String, List<String>> copiedChildren = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : treeChildrenByNode.entrySet()) {
            copiedChildren.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        treeChildrenByNode = Collections.unmodifiableMap(copiedChildren);
        depthBands = List.copyOf(depthBands);
        sectors = List.copyOf(sectors);
    }

    public DungeonNodeAnalysis requireNode(String nodeId) {
        DungeonNodeAnalysis analysis = this.nodes.get(nodeId);
        if (analysis == null) {
            throw new IllegalArgumentException("Missing graph analysis for node: " + nodeId);
        }
        return analysis;
    }

    public int maxDistanceToBoss() {
        return this.nodes.values().stream()
                .mapToInt(DungeonNodeAnalysis::distanceToBoss)
                .max()
                .orElse(0);
    }

    public int maxDegree() {
        return this.nodes.values().stream()
                .mapToInt(DungeonNodeAnalysis::totalDegree)
                .max()
                .orElse(0);
    }

    public long deadEndCount() {
        return this.nodes.values().stream()
                .filter(DungeonNodeAnalysis::deadEnd)
                .count();
    }
}
