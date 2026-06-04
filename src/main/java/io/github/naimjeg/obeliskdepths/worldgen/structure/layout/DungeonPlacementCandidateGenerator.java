package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

import io.github.naimjeg.obeliskdepths.dungeon.geometry.DungeonConnectorSide;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class DungeonPlacementCandidateGenerator {
    private DungeonPlacementCandidateGenerator() {
    }

    static List<DungeonConnectorSide> sideOrder(
            List<DungeonConnectorSide> sides,
            DungeonConnectorSide preferredSide,
            boolean hardPreferredSide
    ) {
        Objects.requireNonNull(sides, "sides");
        for (DungeonConnectorSide side : sides) {
            Objects.requireNonNull(side, "side");
        }

        if (preferredSide == null) {
            return List.copyOf(sides);
        }
        if (!sides.contains(preferredSide)) {
            throw new IllegalArgumentException(
                    "Preferred side is not a candidate side: " + preferredSide
            );
        }
        if (hardPreferredSide) {
            return List.of(preferredSide);
        }

        List<DungeonConnectorSide> ordered = new ArrayList<>();
        ordered.add(preferredSide);
        for (DungeonConnectorSide side : sides) {
            if (side != preferredSide) {
                ordered.add(side);
            }
        }
        return List.copyOf(ordered);
    }

    static List<Integer> lateralOffsets(int maximumMagnitude) {
        if (maximumMagnitude < 0) {
            throw new IllegalArgumentException(
                    "Maximum lateral offset must not be negative: "
                            + maximumMagnitude
            );
        }

        List<Integer> offsets = new ArrayList<>();
        offsets.add(0);
        for (int magnitude = 1;
             magnitude <= maximumMagnitude;
             magnitude++) {
            offsets.add(-magnitude);
            offsets.add(magnitude);
        }
        return List.copyOf(offsets);
    }
}
