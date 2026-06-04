package io.github.naimjeg.obeliskdepths.worldgen.structure.graph;

record DungeonGraphGenerationConfig(
        int minSectorCount,
        int maxSectorCount,
        int minEntryCount,
        int maxEntryCount,
        int minArmDepth,
        int maxArmDepth,
        int guaranteedRingDepth,
        int maxLoopEdges,
        int optionalOuterLoopEdges,
        int minSideBranches,
        int maxSideBranches,
        int minSideBranchLength,
        int maxSideBranchLength,
        int maxNodeCount,
        int maxOrdinaryDegree,
        int minEntrySectorSeparation
) {
    static final DungeonGraphGenerationConfig DEFAULT =
            new DungeonGraphGenerationConfig(
                    3, 3,   // keep three sectors with the current boss hub
                    2, 3,   // two or three entrances
                    4, 7,   // deeper radial arms
                    2,      // guaranteed inner loop depth
                    3,      // allow more loop edges
                    2,      // optional outer loops
                    2, 5,   // side-branch count
                    1, 3,   // side-branch length
                    48,     // maximum graph nodes
                    3,      // preserve room connector limit
                    1       // entrance-sector separation
            );
}
