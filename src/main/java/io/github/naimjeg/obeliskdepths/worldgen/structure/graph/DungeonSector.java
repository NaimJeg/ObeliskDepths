package io.github.naimjeg.obeliskdepths.worldgen.structure.graph;

import java.util.List;

public record DungeonSector(
        int index,
        List<String> nodeIds
) {
    public DungeonSector {
        nodeIds = List.copyOf(nodeIds);
    }
}
