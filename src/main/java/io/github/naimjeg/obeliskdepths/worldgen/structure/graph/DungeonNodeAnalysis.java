package io.github.naimjeg.obeliskdepths.worldgen.structure.graph;

import java.util.OptionalInt;

public record DungeonNodeAnalysis(
        int distanceToBoss,
        int minimumDistanceToAnyEntry,
        int treeDepth,
        int treeChildCount,
        int totalDegree,
        boolean entry,
        boolean deadEnd,
        boolean articulation,
        OptionalInt sectorIndex,
        OptionalInt depthBand
) {
    public DungeonNodeAnalysis {
        sectorIndex = sectorIndex == null ? OptionalInt.empty() : sectorIndex;
        depthBand = depthBand == null ? OptionalInt.empty() : depthBand;
    }
}
