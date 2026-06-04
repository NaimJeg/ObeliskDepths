package io.github.naimjeg.obeliskdepths.worldgen.structure.graph;

import java.util.List;

public record DungeonDepthBand(
        int depth,
        List<String> nodeIds
) {
    public DungeonDepthBand {
        nodeIds = List.copyOf(nodeIds);
    }
}
