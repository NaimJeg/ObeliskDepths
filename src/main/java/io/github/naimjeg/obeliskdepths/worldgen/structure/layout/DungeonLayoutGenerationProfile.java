package io.github.naimjeg.obeliskdepths.worldgen.structure.layout;

public enum DungeonLayoutGenerationProfile {
    SMALL_TEST(4, 6, 1, 2),
    MEDIUM_TEST(7, 10, 3, 5),
    LARGE_TEST(12, 18, 6, 10);

    private final int minCriticalPathLength;
    private final int maxCriticalPathLength;
    private final int minBranches;
    private final int maxBranches;

    DungeonLayoutGenerationProfile(
            int minCriticalPathLength,
            int maxCriticalPathLength,
            int minBranches,
            int maxBranches
    ) {
        this.minCriticalPathLength = minCriticalPathLength;
        this.maxCriticalPathLength = maxCriticalPathLength;
        this.minBranches = minBranches;
        this.maxBranches = maxBranches;
    }

    public int criticalPathLength(long seed) {
        return choose(seed, this.minCriticalPathLength, this.maxCriticalPathLength);
    }

    public int branches(long seed) {
        return choose(seed >> 16, this.minBranches, this.maxBranches);
    }

    public int minCriticalPathLength() {
        return this.minCriticalPathLength;
    }

    public int maxCriticalPathLength() {
        return this.maxCriticalPathLength;
    }

    public int minBranches() {
        return this.minBranches;
    }

    public int maxBranches() {
        return this.maxBranches;
    }

    private static int choose(
            long seed,
            int min,
            int max
    ) {
        int range = max - min + 1;
        return min + Math.floorMod(seed, range);
    }
}
