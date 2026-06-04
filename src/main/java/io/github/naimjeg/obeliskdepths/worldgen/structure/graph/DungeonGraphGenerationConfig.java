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
                    4, 4,   // vanilla reference envelope requires compact arms
                    2,      // guaranteed inner loop depth
                    1,      // keep the required loop only
                    0,      // optional outer loops exceed compact layouts
                    2, 2,   // required compact side branches
                    1, 1,   // side branches must fit the reference envelope
                    48,     // maximum graph nodes
                    3,      // preserve room connector limit
                    1       // entrance-sector separation
            );
}
