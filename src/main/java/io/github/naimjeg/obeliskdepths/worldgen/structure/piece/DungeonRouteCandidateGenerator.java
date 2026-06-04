package io.github.naimjeg.obeliskdepths.worldgen.structure.piece;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class DungeonRouteCandidateGenerator {
    private DungeonRouteCandidateGenerator() {
    }

    static List<List<DungeonRouteCell>> manhattanCandidates(
            DungeonRouteCell start,
            DungeonRouteCell goal
    ) {
        List<List<DungeonRouteCell>> candidates = new ArrayList<>();
        addUnique(candidates, manhattanPath(start, goal, true));
        addUnique(candidates, manhattanPath(start, goal, false));
        return candidates;
    }

    static List<DungeonRouteCell> manhattanPath(
            DungeonRouteCell start,
            DungeonRouteCell goal,
            boolean xFirst
    ) {
        if (start.equals(goal)) {
            return List.of(start);
        }

        List<DungeonRouteCell> path = new ArrayList<>();
        path.add(start);
        if (xFirst) {
            appendAxis(path, start.x(), goal.x(), start.z(), true);
            appendAxis(path, start.z(), goal.z(), goal.x(), false);
        } else {
            appendAxis(path, start.z(), goal.z(), start.x(), false);
            appendAxis(path, start.x(), goal.x(), goal.z(), true);
        }
        return path;
    }

    static List<DungeonRouteCell> axisAlignedPath(
            DungeonRouteCell start,
            DungeonRouteCell goal
    ) {
        List<DungeonRouteCell> path = new ArrayList<>();

        if (start.x() == goal.x()) {
            for (int z = Math.min(start.z(), goal.z());
                 z <= Math.max(start.z(), goal.z());
                 z++) {
                path.add(new DungeonRouteCell(start.x(), z));
            }
        } else if (start.z() == goal.z()) {
            for (int x = Math.min(start.x(), goal.x());
                 x <= Math.max(start.x(), goal.x());
                 x++) {
                path.add(new DungeonRouteCell(x, start.z()));
            }
        }

        if (!path.isEmpty() && !path.get(0).equals(start)) {
            java.util.Collections.reverse(path);
        }

        return path;
    }

    static List<DungeonRouteCell> combine(
            List<DungeonRouteCell> prefix,
            List<DungeonRouteCell> body,
            List<DungeonRouteCell> suffix
    ) {
        ArrayList<DungeonRouteCell> combined = new ArrayList<>();
        append(combined, prefix);
        append(combined, body);
        append(combined, suffix);
        return combined;
    }

    static void addUnique(
            List<List<DungeonRouteCell>> candidates,
            List<DungeonRouteCell> route
    ) {
        if (!route.isEmpty() && !candidates.contains(route)) {
            candidates.add(route);
        }
    }

    static List<DungeonRouteCell> shortest(
            List<List<DungeonRouteCell>> candidates
    ) {
        return candidates.stream()
                .min(Comparator
                        .comparingInt((List<DungeonRouteCell> route) ->
                                route.size())
                        .thenComparingInt(DungeonRouteScorer::countTurns)
                        .thenComparing(DungeonRouteCandidateGenerator::pathKey))
                .orElse(List.of());
    }

    private static void appendAxis(
            List<DungeonRouteCell> path,
            int from,
            int to,
            int fixed,
            boolean xAxis
    ) {
        if (from == to) {
            return;
        }
        int step = Integer.compare(to, from);
        for (int value = from + step; value != to + step; value += step) {
            path.add(xAxis
                    ? new DungeonRouteCell(value, fixed)
                    : new DungeonRouteCell(fixed, value));
        }
    }

    private static void append(
            List<DungeonRouteCell> target,
            List<DungeonRouteCell> next
    ) {
        for (DungeonRouteCell cell : next) {
            if (!target.isEmpty() && target.getLast().equals(cell)) {
                continue;
            }
            target.add(cell);
        }
    }

    private static String pathKey(List<DungeonRouteCell> route) {
        StringBuilder builder = new StringBuilder();
        for (DungeonRouteCell cell : route) {
            builder.append(cell.x()).append(',').append(cell.z()).append(';');
        }
        return builder.toString();
    }
}
