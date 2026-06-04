package io.github.naimjeg.obeliskdepths.worldgen.structure.graph;

import io.github.naimjeg.obeliskdepths.dungeon.geometry.*;

import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.RandomSource;

public final class DungeonGraphGenerator {
    private static final DungeonGraphGenerationConfig CONFIG = DungeonGraphGenerationConfig.DEFAULT;
    static final String BOSS_ID = "boss";
    static final String BOSS_HUB_ID = "boss_hub";

    private DungeonGraphGenerator() {
    }

    public static DungeonGraph generate(long generationSeed) {
        Builder builder = new Builder(generationSeed);
        builder.createCore();
        builder.selectEntries();
        builder.createRadialArms();
        builder.connectRings();
        builder.addOptionalBranches();
        DungeonGraph graph = builder.buildGraph();
        DungeonGraphValidator.validate(graph);
        return graph;
    }

    private static final class Builder {
        private final RandomSource random;
        private final List<DungeonGraphNode> nodes = new ArrayList<>();
        private final List<DungeonGraphEdge> edges = new ArrayList<>();
        private final Map<String, DungeonRoomType> nodeTypes = new LinkedHashMap<>();
        private final Map<String, Integer> degreeByNode = new LinkedHashMap<>();
        private final Map<Integer, Integer> armDepthBySector = new LinkedHashMap<>();
        private final Map<Integer, String> entryIdBySector = new LinkedHashMap<>();
        private final List<Integer> entrySectors = new ArrayList<>();
        private int sectorCount;
        private int sideBranchSequence;
        private int loopEdgeCount;

        private Builder(long generationSeed) {
            this.random = RandomSource.create(generationSeed);
            this.sectorCount = choose(CONFIG.minSectorCount(), CONFIG.maxSectorCount());
        }

        private void createCore() {
            addNode(BOSS_ID, DungeonRoomType.BOSS);
            addNode(BOSS_HUB_ID, DungeonRoomType.COMBAT);
            addEdge(
                    "tree_" + BOSS_ID + "_" + BOSS_HUB_ID,
                    BOSS_ID,
                    BOSS_HUB_ID,
                    DungeonGraphEdgeKind.TREE
            );
        }

        private void selectEntries() {
            int entryCount = Math.min(
                    this.sectorCount,
                    choose(CONFIG.minEntryCount(), CONFIG.maxEntryCount())
            );
            List<Integer> candidates = new ArrayList<>();
            for (int sector = 0; sector < this.sectorCount; sector++) {
                candidates.add(sector);
            }
            shuffle(candidates);

            for (int sector : candidates) {
                if (this.entrySectors.size() >= entryCount) {
                    break;
                }
                if (separatedFromExistingEntries(sector)) {
                    this.entrySectors.add(sector);
                }
            }

            for (int sector : candidates) {
                if (this.entrySectors.size() >= entryCount) {
                    break;
                }
                if (!this.entrySectors.contains(sector)) {
                    this.entrySectors.add(sector);
                }
            }

            Collections.sort(this.entrySectors);
        }

        private void createRadialArms() {
            for (int sector = 0; sector < this.sectorCount; sector++) {
                int depth = choose(CONFIG.minArmDepth(), CONFIG.maxArmDepth());
                this.armDepthBySector.put(sector, depth);
                boolean entrySector = this.entrySectors.contains(sector);
                String previous = BOSS_HUB_ID;

                for (int band = 1; band <= depth; band++) {
                    boolean start = entrySector && band == depth;
                    String nodeId = start
                            ? "start_" + this.entrySectors.indexOf(sector)
                            : radialNodeId(sector, band);
                    addNode(nodeId, start ? DungeonRoomType.START : DungeonRoomType.COMBAT);
                    addEdge("tree_" + previous + "_" + nodeId, previous, nodeId, DungeonGraphEdgeKind.TREE);
                    previous = nodeId;
                    if (start) {
                        this.entryIdBySector.put(sector, nodeId);
                    }
                }
            }
        }

        private void connectRings() {
            int guaranteedDepth = CONFIG.guaranteedRingDepth();
            List<LoopCandidate> guaranteed = new ArrayList<>();

            for (int sector = 0; sector < this.sectorCount; sector++) {
                int nextSector = (sector + 1) % this.sectorCount;
                int maximumSharedDepth = Math.min(
                        this.armDepthBySector.get(sector),
                        this.armDepthBySector.get(nextSector)
                );

                if (maximumSharedDepth >= guaranteedDepth) {
                    guaranteed.add(new LoopCandidate(
                            sector,
                            nextSector,
                            guaranteedDepth
                    ));
                }
            }

            // A single guaranteed cross-link creates a real cycle without
            // forcing a partial pseudo-ring around the entire dungeon.
            for (LoopCandidate candidate : guaranteed) {
                int before = this.loopEdgeCount;
                maybeAddLoop(
                        nodeIdAt(candidate.sector(), candidate.nextSectorDepth()),
                        nodeIdAt(candidate.nextSector(), candidate.nextSectorDepth())
                );

                if (this.loopEdgeCount > before) {
                    break;
                }
            }

            List<LoopCandidate> optional = new ArrayList<>();
            for (int sector = 0; sector < this.sectorCount; sector++) {
                int nextSector = (sector + 1) % this.sectorCount;
                int maximumSharedDepth = Math.min(
                        this.armDepthBySector.get(sector),
                        this.armDepthBySector.get(nextSector)
                );

                for (int depth = guaranteedDepth + 1;
                     depth < maximumSharedDepth;
                     depth++) {
                    optional.add(new LoopCandidate(sector, nextSector, depth));
                }
            }

            shuffle(optional);
            int remainingBudget = Math.max(0, Math.min(
                    CONFIG.optionalOuterLoopEdges(),
                    CONFIG.maxLoopEdges() - this.loopEdgeCount
            ));

            for (LoopCandidate candidate : optional) {
                if (remainingBudget <= 0
                        || this.loopEdgeCount >= CONFIG.maxLoopEdges()) {
                    break;
                }

                int before = this.loopEdgeCount;
                maybeAddLoop(
                        nodeIdAt(candidate.sector(), candidate.nextSectorDepth()),
                        nodeIdAt(candidate.nextSector(), candidate.nextSectorDepth())
                );

                if (this.loopEdgeCount > before) {
                    remainingBudget--;
                }
            }
        }

        private void addOptionalBranches() {
            int branchCount = choose(CONFIG.minSideBranches(), CONFIG.maxSideBranches());
            List<String> eligibleParents = this.nodes.stream()
                    .map(DungeonGraphNode::id)
                    .filter(id -> !id.equals(BOSS_ID))
                    .filter(id -> !id.equals(BOSS_HUB_ID))
                    .filter(id -> this.nodeTypes.get(id) == DungeonRoomType.COMBAT)
                    .filter(id -> this.degreeByNode.getOrDefault(id, 0) <= 1)
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            shuffle(eligibleParents);
            int parentCursor = 0;

            for (int branch = 0; branch < branchCount && !eligibleParents.isEmpty(); branch++) {
                String parent = null;

                for (int attempt = 0; attempt < eligibleParents.size(); attempt++) {
                    String candidate = eligibleParents.get(parentCursor % eligibleParents.size());
                    parentCursor++;
                    if (canAcceptEdge(candidate)) {
                        parent = candidate;
                        break;
                    }
                }

                if (parent == null || this.nodes.size() >= CONFIG.maxNodeCount()) {
                    break;
                }

                int length = choose(CONFIG.minSideBranchLength(), CONFIG.maxSideBranchLength());
                String previous = parent;
                int branchSequence = this.sideBranchSequence++;

                for (int depth = 1; depth <= length && this.nodes.size() < CONFIG.maxNodeCount(); depth++) {
                    boolean terminal = depth == length;
                    String nodeId = "side_" + branchSequence + "_depth_" + depth;
                    addNode(nodeId, terminal ? DungeonRoomType.TREASURE : DungeonRoomType.COMBAT);
                    addEdge("tree_" + previous + "_" + nodeId, previous, nodeId, DungeonGraphEdgeKind.TREE);
                    previous = nodeId;
                }
            }
        }

        private DungeonGraph buildGraph() {
            Set<String> entries = new LinkedHashSet<>();
            for (int sector : this.entrySectors) {
                entries.add(this.entryIdBySector.get(sector));
            }
            String primaryEntry = entries.iterator().next();
            return new DungeonGraph(
                    BOSS_ID,
                    entries,
                    primaryEntry,
                    this.nodes,
                    this.edges
            );
        }

        private boolean separatedFromExistingEntries(int sector) {
            for (int existing : this.entrySectors) {
                int direct = Math.abs(existing - sector);
                int cyclic = Math.min(direct, this.sectorCount - direct);
                if (cyclic < CONFIG.minEntrySectorSeparation()) {
                    return false;
                }
            }
            return true;
        }

        private void maybeAddLoop(
                String first,
                String second
        ) {
            if (first.equals(second)
                    || this.nodeTypes.get(first) == DungeonRoomType.TREASURE
                    || this.nodeTypes.get(second) == DungeonRoomType.TREASURE
                    || !canAcceptEdge(first)
                    || !canAcceptEdge(second)
                    || containsPhysicalConnection(first, second)) {
                return;
            }

            addEdge("loop_" + first + "_" + second, first, second, DungeonGraphEdgeKind.LOOP);
        }

        private boolean containsPhysicalConnection(
                String first,
                String second
        ) {
            for (DungeonGraphEdge edge : this.edges) {
                if ((edge.sourceNodeId().equals(first) && edge.targetNodeId().equals(second))
                        || (edge.sourceNodeId().equals(second) && edge.targetNodeId().equals(first))) {
                    return true;
                }
            }
            return false;
        }

        private String nodeIdAt(
                int sector,
                int depth
        ) {
            Integer armDepth = this.armDepthBySector.get(sector);
            if (armDepth != null && armDepth == depth && this.entryIdBySector.containsKey(sector)) {
                return this.entryIdBySector.get(sector);
            }
            return radialNodeId(sector, depth);
        }

        private static String radialNodeId(
                int sector,
                int depth
        ) {
            return "sector_" + sector + "_depth_" + depth;
        }

        private void addNode(
                String id,
                DungeonRoomType type
        ) {
            this.nodes.add(new DungeonGraphNode(id, type));
            this.nodeTypes.put(id, type);
            this.degreeByNode.put(id, 0);
        }

        private void addEdge(
                String id,
                String source,
                String target,
                DungeonGraphEdgeKind kind
        ) {
            this.edges.add(new DungeonGraphEdge(id, source, target, kind));
            this.degreeByNode.put(source, this.degreeByNode.get(source) + 1);
            this.degreeByNode.put(target, this.degreeByNode.get(target) + 1);
            if (kind == DungeonGraphEdgeKind.LOOP) {
                this.loopEdgeCount++;
            }
        }

        private boolean canAcceptEdge(String nodeId) {
            if (nodeId.equals(BOSS_ID)) {
                return this.degreeByNode.getOrDefault(nodeId, 0) < 1;
            }
            if (nodeId.equals(BOSS_HUB_ID)) {
                return this.degreeByNode.getOrDefault(nodeId, 0) < 4;
            }
            return this.degreeByNode.getOrDefault(nodeId, 0)
                    < CONFIG.maxOrdinaryDegree();
        }

        private int choose(
                int min,
                int max
        ) {
            return min + this.random.nextInt(max - min + 1);
        }

        private <T> void shuffle(List<T> values) {
            for (int index = values.size() - 1; index > 0; index--) {
                int swapIndex = this.random.nextInt(index + 1);
                T value = values.get(index);
                values.set(index, values.get(swapIndex));
                values.set(swapIndex, value);
            }
        }

        private record LoopCandidate(
                int sector,
                int nextSector,
                int nextSectorDepth
        ) {
        }
    }
}
