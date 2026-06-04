package io.github.naimjeg.obeliskdepths.worldgen.structure.piece;

import java.util.List;

public record DungeonRoutingResult(
        List<DungeonRoutedCorridor> corridors
) {
    public DungeonRoutingResult {
        corridors = List.copyOf(corridors);
    }

    public int totalLengthCells() {
        return this.corridors.stream()
                .mapToInt(DungeonRoutedCorridor::lengthCells)
                .sum();
    }

    public int maxLengthCells() {
        return this.corridors.stream()
                .mapToInt(DungeonRoutedCorridor::lengthCells)
                .max()
                .orElse(0);
    }
}
