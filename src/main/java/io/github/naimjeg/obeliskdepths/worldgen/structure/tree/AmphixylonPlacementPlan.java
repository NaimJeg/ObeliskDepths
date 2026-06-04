package io.github.naimjeg.obeliskdepths.worldgen.structure.tree;

import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.*;

/** Exact, immutable set of all blocks an Amphixylon piece may emit. */
public final class AmphixylonPlacementPlan {
    private static final Comparator<AmphixylonGeometry.LocalBlock> LOCAL_ORDER = Comparator
            .comparingInt(AmphixylonGeometry.LocalBlock::y)
            .thenComparingInt(AmphixylonGeometry.LocalBlock::x)
            .thenComparingInt(AmphixylonGeometry.LocalBlock::z);
    private static final Comparator<BlockPlacement> EMISSION_ORDER = Comparator
            .comparingInt((BlockPlacement placement) -> placement.kind().priority()).reversed()
            .thenComparingInt(BlockPlacement::y)
            .thenComparingInt(BlockPlacement::x)
            .thenComparingInt(BlockPlacement::z)
            .thenComparingInt(BlockPlacement::sourceIndex);

    private final AmphixylonSite site;
    private final AmphixylonGeometry geometry;
    private final List<BlockPlacement> placements;
    private final List<VineColumn> vines;
    private final List<List<AmphixylonGeometry.LocalBlock>> upperArmBlocks;
    private final List<List<AmphixylonGeometry.LocalBlock>> lowerRootBlocks;
    private final BoundingBox completeBounds;
    private final int duplicateResolutionCount;
    private final long stableHash;

    private AmphixylonPlacementPlan(
            AmphixylonSite site,
            AmphixylonGeometry geometry,
            List<BlockPlacement> placements,
            List<VineColumn> vines,
            List<List<AmphixylonGeometry.LocalBlock>> upperArmBlocks,
            List<List<AmphixylonGeometry.LocalBlock>> lowerRootBlocks,
            BoundingBox completeBounds,
            int duplicateResolutionCount,
            long stableHash
    ) {
        this.site = site;
        this.geometry = geometry;
        this.placements = List.copyOf(placements);
        this.vines = List.copyOf(vines);
        this.upperArmBlocks = copyNested(upperArmBlocks);
        this.lowerRootBlocks = copyNested(lowerRootBlocks);
        this.completeBounds = completeBounds;
        this.duplicateResolutionCount = duplicateResolutionCount;
        this.stableHash = stableHash;
    }

    public static AmphixylonPlacementPlan create(AmphixylonSite site, AmphixylonGeometry geometry) {
        Builder builder = new Builder(site, geometry);
        builder.addTrunk();
        builder.addStructuralPaths();
        builder.addLeavesAndVines();
        return builder.build();
    }

    public AmphixylonSite site() {
        return this.site;
    }

    public AmphixylonGeometry geometry() {
        return this.geometry;
    }

    public List<BlockPlacement> placements() {
        return this.placements;
    }

    public List<VineColumn> vines() {
        return this.vines;
    }

    public List<AmphixylonGeometry.LocalBlock> upperArmBlocks(int index) {
        return this.upperArmBlocks.get(index);
    }

    public List<AmphixylonGeometry.LocalBlock> lowerRootBlocks(int index) {
        return this.lowerRootBlocks.get(index);
    }

    public BoundingBox completeBounds() {
        return this.completeBounds;
    }

    public int xSpan() {
        return this.completeBounds.maxX() - this.completeBounds.minX() + 1;
    }

    public int zSpan() {
        return this.completeBounds.maxZ() - this.completeBounds.minZ() + 1;
    }

    public int duplicateResolutionCount() {
        return this.duplicateResolutionCount;
    }

    public long stableHash() {
        return this.stableHash;
    }

    public boolean isValid() {
        if (!this.geometry.isExactlyReflected()
                || this.placements.isEmpty()
                || this.xSpan() > AmphixylonGeometry.MAX_HORIZONTAL_SPAN
                || this.zSpan() > AmphixylonGeometry.MAX_HORIZONTAL_SPAN
                || this.xSpan() > 127
                || this.zSpan() > 127
                || this.completeBounds.minY() < this.site.minY()
                || this.completeBounds.maxY() > this.site.maxY()) {
            return false;
        }
        for (BlockPlacement placement : this.placements) {
            if (!contains(this.completeBounds, placement.x(), placement.y(), placement.z())) {
                return false;
            }
        }
        for (VineColumn vine : this.vines) {
            if (!contains(this.completeBounds, vine.x(), vine.supportY(), vine.z())
                    || !contains(this.completeBounds, vine.x(), vine.topY(), vine.z())
                    || !contains(this.completeBounds, vine.x(), vine.bottomY(), vine.z())) {
                return false;
            }
        }
        return true;
    }

    public boolean containsKindAt(int x, int y, int z, PlacementKind kind) {
        for (BlockPlacement placement : this.placements) {
            if (placement.x() == x && placement.y() == y && placement.z() == z) {
                return placement.kind() == kind;
            }
        }
        return false;
    }

    public BoundingBox boundsFor(EnumSet<PlacementKind> kinds) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPlacement placement : this.placements) {
            if (!kinds.contains(placement.kind())) {
                continue;
            }
            minX = Math.min(minX, placement.x());
            minY = Math.min(minY, placement.y());
            minZ = Math.min(minZ, placement.z());
            maxX = Math.max(maxX, placement.x());
            maxY = Math.max(maxY, placement.y());
            maxZ = Math.max(maxZ, placement.z());
        }
        if (minX == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("No Amphixylon placements for requested kinds");
        }
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    static List<PathVoxel> voxelizePath(AmphixylonGeometry.TaperedPath path) {
        TreeMap<AmphixylonGeometry.LocalBlock, PathVoxel> blocks = new TreeMap<>(LOCAL_ORDER);
        for (AmphixylonGeometry.PathSample sample : path.samples()) {
            int radius = (int) Math.ceil(sample.radius() + 0.35D);
            int centerX = (int) Math.round(sample.point().x());
            int centerY = (int) Math.round(sample.point().y());
            int centerZ = (int) Math.round(sample.point().z());
            for (int y = centerY - radius; y <= centerY + radius; y++) {
                for (int x = centerX - radius; x <= centerX + radius; x++) {
                    for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                        double dx = x - sample.point().x();
                        double dy = y - sample.point().y();
                        double dz = z - sample.point().z();
                        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (distance > sample.radius() + 0.35D) {
                            continue;
                        }
                        AmphixylonGeometry.LocalBlock block = new AmphixylonGeometry.LocalBlock(x, y, z);
                        PathVoxel candidate = new PathVoxel(block, sample.t(), sample.radius(), distance);
                        PathVoxel existing = blocks.get(block);
                        if (existing == null
                                || candidate.t() > existing.t()
                                || (candidate.t() == existing.t() && candidate.distance() < existing.distance())) {
                            blocks.put(block, candidate);
                        }
                    }
                }
            }
        }
        return List.copyOf(blocks.values());
    }

    static List<PathVoxel> mirrorPathVoxels(List<PathVoxel> canonicalVoxels) {
        List<PathVoxel> mirrored = new ArrayList<>(canonicalVoxels.size());
        for (PathVoxel canonical : canonicalVoxels) {
            mirrored.add(new PathVoxel(
                    canonical.block().mirror(),
                    canonical.t(),
                    canonical.radius(),
                    canonical.distance()
            ));
        }
        mirrored.sort(Comparator.comparing(PathVoxel::block, LOCAL_ORDER));
        return List.copyOf(mirrored);
    }

    private static List<List<AmphixylonGeometry.LocalBlock>> copyNested(
            List<List<AmphixylonGeometry.LocalBlock>> source
    ) {
        List<List<AmphixylonGeometry.LocalBlock>> result = new ArrayList<>(source.size());
        for (List<AmphixylonGeometry.LocalBlock> blocks : source) {
            result.add(List.copyOf(blocks));
        }
        return List.copyOf(result);
    }

    private static boolean contains(BoundingBox box, int x, int y, int z) {
        return x >= box.minX() && x <= box.maxX()
                && y >= box.minY() && y <= box.maxY()
                && z >= box.minZ() && z <= box.maxZ();
    }

    private static final class Builder {
        private final AmphixylonSite site;
        private final AmphixylonGeometry geometry;
        private final TreeMap<AmphixylonGeometry.LocalBlock, LocalPlacement> resolved =
                new TreeMap<>(LOCAL_ORDER);
        private final List<VineColumn> vines = new ArrayList<>();
        private final List<List<AmphixylonGeometry.LocalBlock>> upperArmBlocks = new ArrayList<>();
        private final List<List<AmphixylonGeometry.LocalBlock>> lowerRootBlocks = new ArrayList<>();
        private int duplicateResolutionCount;

        private Builder(AmphixylonSite site, AmphixylonGeometry geometry) {
            this.site = site;
            this.geometry = geometry;
        }

        private void addTrunk() {
            for (AmphixylonGeometry.TrunkSlice slice : this.geometry.trunkSlices()) {
                int radius = (int) Math.ceil(slice.radius() * 1.22D);
                for (int x = (int) Math.floor(slice.centerX()) - radius;
                     x <= (int) Math.ceil(slice.centerX()) + radius; x++) {
                    for (int z = (int) Math.floor(slice.centerZ()) - radius;
                         z <= (int) Math.ceil(slice.centerZ()) + radius; z++) {
                        int absoluteY = Math.abs(slice.localY());
                        double dx = x - slice.centerX();
                        double dz = z - slice.centerZ();
                        double distance = Math.sqrt(dx * dx + dz * dz);
                        double angle = Math.atan2(dz, dx);
                        double irregularity = 1.0D
                                + Math.sin(
                                        angle * 5.0D + absoluteY * 0.083D + slice.irregularityPhase()
                                ) * 0.105D
                                + Math.sin(
                                        angle * 9.0D
                                                - absoluteY * 0.041D
                                                - slice.irregularityPhase() * 0.5D
                                ) * 0.055D;
                        double effectiveRadius = slice.radius() * irregularity;
                        if (distance > effectiveRadius) {
                            continue;
                        }
                        double innerRadius = Math.min(
                                slice.radius() - 4.0D,
                                Math.max(3.4D, slice.radius() * 0.43D)
                        );
                        PlacementKind kind;
                        if (innerRadius >= 2.5D
                                && Math.abs(slice.localY()) <= 36
                                && distance < innerRadius) {
                            kind = PlacementKind.HOLLOW;
                        } else if (effectiveRadius - distance <= 2.1D) {
                            kind = PlacementKind.TRUNK_LOG;
                        } else {
                            kind = PlacementKind.TRUNK_WOOD;
                        }
                        put(new AmphixylonGeometry.LocalBlock(x, slice.localY(), z), kind, -1);
                    }
                }
            }
        }

        private void addStructuralPaths() {
            List<AmphixylonGeometry.TaperedPath> upperPaths = this.geometry.canonicalHalf().paths();
            for (int index = 0; index < upperPaths.size(); index++) {
                List<PathVoxel> upperVoxels = voxelizePath(upperPaths.get(index));
                List<PathVoxel> lowerVoxels = mirrorPathVoxels(upperVoxels);
                List<AmphixylonGeometry.LocalBlock> upperBlocks = new ArrayList<>(upperVoxels.size());
                List<AmphixylonGeometry.LocalBlock> lowerBlocks = new ArrayList<>(lowerVoxels.size());
                for (PathVoxel voxel : upperVoxels) {
                    upperBlocks.add(voxel.block());
                    put(voxel.block(), PlacementKind.UPPER_ARM, index);
                }
                for (PathVoxel voxel : lowerVoxels) {
                    lowerBlocks.add(voxel.block());
                    PlacementKind kind = voxel.t() >= 0.88D || voxel.radius() <= 1.20D
                            ? PlacementKind.LOWER_ROOT_TIP
                            : PlacementKind.LOWER_ROOT;
                    put(voxel.block(), kind, index);
                }
                this.upperArmBlocks.add(List.copyOf(upperBlocks));
                this.lowerRootBlocks.add(List.copyOf(lowerBlocks));
            }
        }

        private void addLeavesAndVines() {
            for (AmphixylonGeometry.LeafUnit leaf : this.geometry.canonicalHalf().leafUnits()) {
                int centerX = (int) Math.round(leaf.center().x());
                int centerY = (int) Math.round(leaf.center().y());
                int centerZ = (int) Math.round(leaf.center().z());
                int bottomCenterOffset = Integer.MAX_VALUE;
                for (AmphixylonGeometry.LocalBlock offset : leaf.maskOffsets()) {
                    AmphixylonGeometry.LocalBlock block = new AmphixylonGeometry.LocalBlock(
                            centerX + offset.x(),
                            centerY + offset.y(),
                            centerZ + offset.z()
                    );
                    put(block, PlacementKind.LEAF, leaf.index());
                    if (offset.x() == 0 && offset.z() == 0) {
                        bottomCenterOffset = Math.min(bottomCenterOffset, offset.y());
                    }
                }
                if (leaf.vineLength() <= 0 || bottomCenterOffset == Integer.MAX_VALUE) {
                    continue;
                }
                int supportY = centerY + bottomCenterOffset;
                int topY = supportY - 1;
                int bottomY = topY - leaf.vineLength() + 1;
                put(new AmphixylonGeometry.LocalBlock(centerX, supportY, centerZ),
                        PlacementKind.VINE_SUPPORT, leaf.index());
                for (int y = topY; y >= bottomY; y--) {
                    put(new AmphixylonGeometry.LocalBlock(centerX, y, centerZ),
                            y == bottomY ? PlacementKind.VINE_HEAD : PlacementKind.VINE_BODY,
                            leaf.index());
                }
                this.vines.add(new VineColumn(
                        this.site.centerX() + centerX,
                        this.site.centerZ() + centerZ,
                        this.site.symmetryY() + supportY,
                        this.site.symmetryY() + topY,
                        this.site.symmetryY() + bottomY,
                        leaf.index()
                ));
            }
        }

        private void put(
                AmphixylonGeometry.LocalBlock block,
                PlacementKind kind,
                int sourceIndex
        ) {
            LocalPlacement candidate = new LocalPlacement(block, kind, sourceIndex);
            LocalPlacement existing = this.resolved.get(block);
            if (existing == null) {
                this.resolved.put(block, candidate);
                return;
            }
            this.duplicateResolutionCount++;
            if (candidate.kind().priority() > existing.kind().priority()
                    || (candidate.kind().priority() == existing.kind().priority()
                    && candidate.sourceIndex() < existing.sourceIndex())) {
                this.resolved.put(block, candidate);
            }
        }

        private AmphixylonPlacementPlan build() {
            if (this.resolved.isEmpty()) {
                throw new IllegalStateException("Amphixylon placement plan is empty");
            }
            List<BlockPlacement> placements = new ArrayList<>(this.resolved.size());
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (LocalPlacement local : this.resolved.values()) {
                int worldX = this.site.centerX() + local.block().x();
                int worldY = this.site.symmetryY() + local.block().y();
                int worldZ = this.site.centerZ() + local.block().z();
                placements.add(new BlockPlacement(worldX, worldY, worldZ, local.kind(), local.sourceIndex()));
                minX = Math.min(minX, worldX);
                minY = Math.min(minY, worldY);
                minZ = Math.min(minZ, worldZ);
                maxX = Math.max(maxX, worldX);
                maxY = Math.max(maxY, worldY);
                maxZ = Math.max(maxZ, worldZ);
            }
            placements.sort(EMISSION_ORDER);
            BoundingBox bounds = new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
            long hash = stableHash(placements);
            return new AmphixylonPlacementPlan(
                    this.site,
                    this.geometry,
                    placements,
                    this.vines,
                    this.upperArmBlocks,
                    this.lowerRootBlocks,
                    bounds,
                    this.duplicateResolutionCount,
                    hash
            );
        }
    }

    private static long stableHash(List<BlockPlacement> placements) {
        long hash = 0xcbf29ce484222325L;
        for (BlockPlacement placement : placements) {
            hash ^= placement.x();
            hash *= 0x100000001b3L;
            hash ^= placement.y();
            hash *= 0x100000001b3L;
            hash ^= placement.z();
            hash *= 0x100000001b3L;
            hash ^= placement.kind().ordinal();
            hash *= 0x100000001b3L;
            hash ^= placement.sourceIndex();
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    public enum PlacementKind {
        TRUNK_LOG(710),
        TRUNK_WOOD(700),
        UPPER_ARM(600),
        LOWER_ROOT_TIP(595),
        LOWER_ROOT(590),
        VINE_SUPPORT(310),
        LEAF(300),
        VINE_HEAD(205),
        VINE_BODY(200),
        HOLLOW(100);

        private final int priority;

        PlacementKind(int priority) {
            this.priority = priority;
        }

        public int priority() {
            return this.priority;
        }

        public boolean isVine() {
            return this == VINE_SUPPORT || this == VINE_HEAD || this == VINE_BODY;
        }

        public boolean isStructural() {
            return this == TRUNK_LOG || this == TRUNK_WOOD || this == UPPER_ARM
                    || this == LOWER_ROOT || this == LOWER_ROOT_TIP;
        }
    }

    public record BlockPlacement(
            int x,
            int y,
            int z,
            PlacementKind kind,
            int sourceIndex
    ) {
    }

    public record VineColumn(
            int x,
            int z,
            int supportY,
            int topY,
            int bottomY,
            int sourceIndex
    ) {
    }

    record PathVoxel(
            AmphixylonGeometry.LocalBlock block,
            double t,
            double radius,
            double distance
    ) {
    }

    private record LocalPlacement(
            AmphixylonGeometry.LocalBlock block,
            PlacementKind kind,
            int sourceIndex
    ) {
    }
}
