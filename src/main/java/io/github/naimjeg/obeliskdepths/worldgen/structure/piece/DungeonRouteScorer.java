package io.github.naimjeg.obeliskdepths.worldgen.structure.piece;

import java.util.List;

final class DungeonRouteScorer {
    private DungeonRouteScorer() {
    }

    static int manhattan(
            DungeonRouteCell first,
            DungeonRouteCell second
    ) {
        return Math.abs(first.x() - second.x())
                + Math.abs(first.z() - second.z());
    }

    static int scorePath(
            List<DungeonRouteCell> path,
            int moveCost,
            int turnCost,
            int sidePenalty
    ) {
        return path.size() * moveCost
                + countTurns(path) * turnCost
                + sidePenalty;
    }

    static int countTurns(List<DungeonRouteCell> path) {
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
}
