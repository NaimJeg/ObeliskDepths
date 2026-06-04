package io.github.naimjeg.obeliskdepths.worldgen.structure.tree;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

public final class GreatSwampHourglassTreeField {
    public static final int MIN_SHELL_THICKNESS = 4;
    public static final int MAX_VINE_LENGTH = 18;

    private static final double TWO_PI = Math.PI * 2.0D;

    private final GreatSwampHourglassTreeSite site;
    private final double phaseA;
    private final double phaseB;
    private final double phaseC;
    private final double phaseD;
    private final double centerAmplitudeA;
    private final double centerAmplitudeB;
    private final double waistRadius;
    private final double lowerTrunkRadius;
    private final double upperTrunkRadius;
    private final RootDescriptor[] lowerRoots;
    private final RootDescriptor[] upperRoots;
    private final BranchDescriptor[] branches;
    private final BoundingBox trunkBounds;
    private final BoundingBox completeBounds;
    private final List<BlockPos> candidateDungeonBasinCenters;

    public GreatSwampHourglassTreeField(GreatSwampHourglassTreeSite site) {
        this.site = site;
        long seed = site.treeSeed();
        this.phaseA = unit(seed, 11) * TWO_PI;
        this.phaseB = unit(seed, 12) * TWO_PI;
        this.phaseC = unit(seed, 13) * TWO_PI;
        this.phaseD = unit(seed, 14) * TWO_PI;
        this.centerAmplitudeA = 4.0D + unit(seed, 15) * 5.0D;
        this.centerAmplitudeB = 2.0D + unit(seed, 16) * 3.5D;
        this.waistRadius = 8.0D + unit(seed, 17) * 8.0D;
        this.lowerTrunkRadius = 24.0D + unit(seed, 18) * 12.0D;
        this.upperTrunkRadius = this.lowerTrunkRadius
                * (0.95D + unit(seed, 19) * 0.10D);
        this.lowerRoots = createLowerRoots();
        this.upperRoots = createUpperRoots();
        this.branches = createBranches();
        this.trunkBounds = computeTrunkBounds();
        this.completeBounds = computeCompleteBounds();
        this.candidateDungeonBasinCenters = List.copyOf(computeCandidateDungeonBasinCenters());
    }

    public GreatSwampHourglassTreeSite site() {
        return this.site;
    }

    public int lowerRootCount() {
        return this.lowerRoots.length;
    }

    public int upperRootCount() {
        return this.upperRoots.length;
    }

    public int branchCount() {
        return this.branches.length;
    }

    public BoundingBox trunkBounds() {
        return this.trunkBounds;
    }

    public BoundingBox lowerRootBounds(int index) {
        return this.lowerRoots[index].bounds();
    }

    public BoundingBox upperRootBounds(int index) {
        return this.upperRoots[index].bounds();
    }

    public BoundingBox branchCanopyBounds(int index) {
        return this.branches[index].bounds();
    }

    public BoundingBox completeBounds() {
        return this.completeBounds;
    }

    public TrunkSlice trunkSlice(int y) {
        double radius = radiusAtY(y);
        return new TrunkSlice(
                y,
                centerXAtY(y),
                centerZAtY(y),
                radius,
                radius * 1.24D + 3.0D
        );
    }

    public TrunkSlice[] trunkSlices(int minY, int maxY) {
        TrunkSlice[] slices = new TrunkSlice[maxY - minY + 1];
        for (int y = minY; y <= maxY; y++) {
            slices[y - minY] = trunkSlice(y);
        }
        return slices;
    }

    public double centerXAtY(int y) {
        double dy = y - this.site.minY();
        return this.site.centerX()
                + Math.sin(dy * 0.037D + this.phaseA) * this.centerAmplitudeA
                + Math.sin(dy * 0.091D + this.phaseB) * this.centerAmplitudeB;
    }

    public double centerZAtY(int y) {
        double dy = y - this.site.minY();
        return this.site.centerZ()
                + Math.cos(dy * 0.033D + this.phaseC) * this.centerAmplitudeA
                + Math.sin(dy * 0.077D + this.phaseD) * this.centerAmplitudeB;
    }

    public double radiusAtY(int y) {
        double t = normalizedY(y);
        double radius;

        if (t < 0.12D) {
            radius = lerp(this.lowerTrunkRadius * 0.78D, this.lowerTrunkRadius * 1.10D, smooth(t / 0.12D));
        } else if (t < 0.48D) {
            radius = lerp(this.lowerTrunkRadius * 1.08D, this.waistRadius, smooth((t - 0.12D) / 0.36D));
        } else if (t < 0.56D) {
            radius = this.waistRadius + Math.sin((t - 0.48D) / 0.08D * Math.PI) * 1.5D;
        } else if (t < 0.78D) {
            radius = lerp(this.waistRadius, this.upperTrunkRadius, smooth((t - 0.56D) / 0.22D));
        } else if (t < 0.90D) {
            radius = this.upperTrunkRadius
                    + Math.sin(smooth((t - 0.78D) / 0.12D) * Math.PI) * 7.0D;
        } else {
            /*
             * Keep the upper cap broad instead of tapering sharply.
             *
             * Old final radius:
             *     upperTrunkRadius * 0.58 + 4.5
             *
             * New final radius:
             *     upperTrunkRadius * 0.82 + 7.0
             */
            radius = lerp(
                    this.upperTrunkRadius * 0.96D,
                    this.upperTrunkRadius * 0.82D,
                    smooth((t - 0.90D) / 0.10D)
            );
        }

        if (t < 0.18D) {
            radius += (1.0D - smooth(t / 0.18D)) * 9.0D;
        }
        if (t > 0.84D) {
            radius += smooth((t - 0.84D) / 0.16D) * 7.0D;
        }

        return Math.max(this.waistRadius, radius);
    }

    public boolean isTrunk(int x, int y, int z) {
        return isTrunk(trunkSlice(y), x, z);
    }

    public boolean isTrunk(TrunkSlice slice, int x, int z) {
        return trunkMargin(slice, x, z) >= 0.0D;
    }

    public boolean isTrunkSurface(TrunkSlice slice, int x, int z) {
        double margin = trunkMargin(slice, x, z);
        return margin >= 0.0D && margin <= 2.1D;
    }

    public boolean isLowerRoot(int x, int y, int z) {
        for (RootDescriptor root : this.lowerRoots) {
            if (containsRoot(root, x, y, z)) {
                return true;
            }
        }
        return false;
    }

    public boolean isLowerRoot(int index, int x, int y, int z) {
        return containsRoot(this.lowerRoots[index], x, y, z);
    }

    public boolean isUpperRoot(int x, int y, int z) {
        for (RootDescriptor root : this.upperRoots) {
            if (containsRoot(root, x, y, z)) {
                return true;
            }
        }
        return false;
    }

    public boolean isUpperRoot(int index, int x, int y, int z) {
        return containsRoot(this.upperRoots[index], x, y, z);
    }

    public boolean isUpperBranch(int x, int y, int z) {
        for (BranchDescriptor branch : this.branches) {
            if (containsPath(branch.path(), x, y, z)) {
                return true;
            }
        }
        return false;
    }

    public boolean isUpperBranch(int index, int x, int y, int z) {
        return containsPath(this.branches[index].path(), x, y, z);
    }

    public boolean isLeaf(int x, int y, int z) {
        for (BranchDescriptor branch : this.branches) {
            for (LeafCluster cluster : branch.clusters()) {
                if (clusterContainsLeaf(cluster, x, y, z)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isLeafForBranch(int index, int x, int y, int z) {
        for (LeafCluster cluster : this.branches[index].clusters()) {
            if (clusterContainsLeaf(cluster, x, y, z)) {
                return true;
            }
        }
        return false;
    }

    public boolean isRootSurface(int x, int y, int z) {
        for (RootDescriptor root : this.lowerRoots) {
            if (isRootSurface(root, x, y, z)) {
                return true;
            }
        }
        for (RootDescriptor root : this.upperRoots) {
            if (isRootSurface(root, x, y, z)) {
                return true;
            }
        }
        return false;
    }

    public boolean isLowerRootSurface(int index, int x, int y, int z) {
        return isRootSurface(this.lowerRoots[index], x, y, z);
    }

    public boolean isUpperRootSurface(int index, int x, int y, int z) {
        return isRootSurface(this.upperRoots[index], x, y, z);
    }

    public boolean isBranchSurface(int index, int x, int y, int z) {
        PathHit hit = closestPathHit(this.branches[index].path(), x, y, z);
        return hit.inside() && hit.margin() <= 1.6D;
    }

    public boolean isLowerRootHollow(int index, int x, int y, int z) {
        RootDescriptor root = this.lowerRoots[index];
        if (!root.hasTunnel()) {
            return false;
        }
        return isRootTunnel(root, x, y, z);
    }

    public boolean isInsideHollow(BlockPos pos) {
        return isInsideHollow(pos.getX(), pos.getY(), pos.getZ());
    }

    public boolean isInsideHollow(int x, int y, int z) {
        if (y < this.site.minY() || y > this.site.maxY()) {
            return false;
        }

        TrunkSlice slice = trunkSlice(y);
        if (isTrunk(slice, x, z) && isTrunkHollow(slice, x, z)) {
            return true;
        }

        for (RootDescriptor root : this.lowerRoots) {
            if (root.hasTunnel() && isRootTunnel(root, x, y, z)) {
                return true;
            }
        }

        return false;
    }

    public boolean isInsideWood(BlockPos pos) {
        return isInsideWood(pos.getX(), pos.getY(), pos.getZ());
    }

    public boolean isInsideWood(int x, int y, int z) {
        if (isInsideHollow(x, y, z)) {
            return false;
        }

        if (isTrunk(x, y, z) || isLowerRoot(x, y, z) || isUpperRoot(x, y, z)) {
            return true;
        }

        return isUpperBranch(x, y, z);
    }

    public boolean isNearLowerRootContact(int index, int x, int y, int z) {
        if (y < this.site.minY() || y > this.site.minY() + 7) {
            return false;
        }
        if (containsRoot(this.lowerRoots[index], x, y, z)) {
            return false;
        }

        PathHit hit = closestRootHit(this.lowerRoots[index], x, y, z);
        return hit.distance() <= hit.radius() + 2.25D;
    }

    public Optional<VineColumn> vineAt(int x, int z) {
        for (int i = 0; i < this.branches.length; i++) {
            Optional<VineColumn> vine = vineAtForBranch(i, x, z);
            if (vine.isPresent()) {
                return vine;
            }
        }
        return Optional.empty();
    }

    public Optional<VineColumn> vineAtForBranch(int index, int x, int z) {
        BranchDescriptor branch = this.branches[index];
        for (LeafCluster cluster : branch.clusters()) {
            double nx = (x + 0.5D - cluster.x()) / cluster.radiusX();
            double nz = (z + 0.5D - cluster.z()) / cluster.radiusZ();
            double projected = nx * nx + nz * nz;
            if (projected > 0.74D) {
                continue;
            }

            double density = 0.025D + 0.015D * (1.0D - projected);
            if (unit(this.site.treeSeed(), x, z, cluster.clusterIndex(), 301) > density) {
                continue;
            }

            int bottomLeafY = Integer.MAX_VALUE;
            int min = Math.max(this.site.minY() + 2, floor(cluster.y() - cluster.radiusY()));
            int max = Math.min(this.site.maxY() - 2, ceil(cluster.y() + cluster.radiusY()));
            for (int y = min; y <= max; y++) {
                if (clusterContainsLeaf(cluster, x, y, z)
                        && !clusterContainsLeaf(cluster, x, y - 1, z)) {
                    bottomLeafY = y;
                    break;
                }
            }

            if (bottomLeafY == Integer.MAX_VALUE) {
                continue;
            }

            int topY = bottomLeafY - 1;
            int length = 5 + intRange(this.site.treeSeed(), x, z, cluster.clusterIndex(), 302, MAX_VINE_LENGTH - 4);
            int bottomY = Math.max(this.site.minY() + 1, topY - length + 1);
            if (bottomY > topY) {
                continue;
            }

            return Optional.of(new VineColumn(topY, bottomY));
        }

        return Optional.empty();
    }

    public BlockPos lowerRootBasinCenter() {
        return this.candidateDungeonBasinCenters.getFirst();
    }

    public List<BlockPos> candidateDungeonBasinCenters() {
        return this.candidateDungeonBasinCenters;
    }

    public boolean validatePlan() {
        if (this.site.maxY() < this.site.minY()) {
            return false;
        }
        if (radiusAtY(this.site.waistY()) >= radiusAtY(this.site.minY() + this.site.height() / 5)) {
            return false;
        }
        if (radiusAtY(this.site.waistY()) >= radiusAtY(this.site.minY() + this.site.height() * 4 / 5)) {
            return false;
        }
        for (RootDescriptor root : this.lowerRoots) {
            if (!contains(this.completeBounds, root.bounds())) {
                return false;
            }
            if (!rootTouchesTrunk(root)) {
                return false;
            }
        }
        for (RootDescriptor root : this.upperRoots) {
            if (!contains(this.completeBounds, root.bounds())) {
                return false;
            }
            if (!rootTouchesTrunk(root)) {
                return false;
            }
        }
        for (BranchDescriptor branch : this.branches) {
            if (!contains(this.completeBounds, branch.bounds())) {
                return false;
            }
            Point start = branch.path().points()[0];
            if (!isTrunk((int) Math.round(start.x()), (int) Math.round(start.y()), (int) Math.round(start.z()))) {
                return false;
            }
        }
        return true;
    }

    private RootDescriptor[] createLowerRoots() {
        int count = 8 + intRange(this.site.treeSeed(), 101, 7);
        RootDescriptor[] roots = new RootDescriptor[count];

        for (int i = 0; i < count; i++) {
            double angle = i * TWO_PI / count + signedUnit(this.site.treeSeed(), i, 102) * 0.22D;
            double length = this.site.maxRadius() * (0.72D + unit(this.site.treeSeed(), i, 103) * 0.34D);
            int startY = this.site.minY() + floor(this.site.height() * (0.15D + unit(this.site.treeSeed(), i, 104) * 0.08D));
            double startDist = radiusAtY(startY) * 0.62D;
            Point start = radialPoint(startY, angle, startDist);
            Point midA = radialPoint(
                    startY - 3.0D + signedUnit(this.site.treeSeed(), i, 105) * 3.0D,
                    angle + signedUnit(this.site.treeSeed(), i, 106) * 0.18D,
                    length * 0.34D
            );
            Point midB = radialPoint(
                    this.site.minY() + 8.0D + unit(this.site.treeSeed(), i, 107) * 7.0D,
                    angle + signedUnit(this.site.treeSeed(), i, 108) * 0.25D,
                    length * 0.70D
            );
            Point end = radialPoint(
                    this.site.minY() + 2.0D + unit(this.site.treeSeed(), i, 109) * 5.0D,
                    angle + signedUnit(this.site.treeSeed(), i, 110) * 0.30D,
                    length
            );

            PathDescriptor main = new PathDescriptor(
                    new Point[]{start, midA, midB, end},
                    8.0D + unit(this.site.treeSeed(), i, 111) * 5.0D,
                    2.8D + unit(this.site.treeSeed(), i, 112) * 2.2D,
                    mix(this.site.treeSeed() ^ (long) i * 0x70A582D7L ^ 0x4C4F574552L)
            );

            int secondaryCount = intRange(this.site.treeSeed(), i, 113, 3);
            PathDescriptor[] paths = new PathDescriptor[1 + secondaryCount];
            paths[0] = main;
            for (int j = 0; j < secondaryCount; j++) {
                double splitProgress = 0.50D + unit(this.site.treeSeed(), i, j, 114) * 0.24D;
                Point split = pointOnPath(main, splitProgress);
                double side = j == 0 ? -1.0D : 1.0D;
                double splitAngle = angle + side * (0.35D + unit(this.site.treeSeed(), i, j, 115) * 0.35D);
                double splitLength = length * (0.22D + unit(this.site.treeSeed(), i, j, 116) * 0.22D);
                Point splitMid = offsetPoint(
                        split,
                        splitAngle,
                        splitLength * 0.52D,
                        -2.0D + signedUnit(this.site.treeSeed(), i, j, 117) * 2.0D
                );
                Point splitEnd = offsetPoint(
                        split,
                        splitAngle,
                        splitLength,
                        -4.0D - unit(this.site.treeSeed(), i, j, 118) * 4.0D
                );
                paths[j + 1] = new PathDescriptor(
                        new Point[]{split, splitMid, splitEnd},
                        Math.max(3.8D, main.startRadius() * 0.55D),
                        1.8D + unit(this.site.treeSeed(), i, j, 119) * 1.5D,
                        mix(this.site.treeSeed() ^ (long) i * 0x9E3779B9L ^ (long) j * 0x632BE5ABL)
                );
            }

            roots[i] = new RootDescriptor(
                    i,
                    false,
                    paths,
                    computePathBounds(paths, 2),
                    unit(this.site.treeSeed(), i, 120) < 0.55D
            );
        }

        return roots;
    }

    private RootDescriptor[] createUpperRoots() {
        int count = 7 + intRange(this.site.treeSeed(), 201, 6);
        RootDescriptor[] roots = new RootDescriptor[count];

        for (int i = 0; i < count; i++) {
            double angle = i * TWO_PI / count + signedUnit(this.site.treeSeed(), i, 202) * 0.28D;
            double length = this.site.maxRadius() * (0.52D + unit(this.site.treeSeed(), i, 203) * 0.35D);
            int startY = this.site.minY() + floor(this.site.height() * (0.84D + unit(this.site.treeSeed(), i, 204) * 0.06D));
            double startDist = radiusAtY(startY) * 0.58D;
            Point start = radialPoint(startY, angle, startDist);
            Point midA = radialPoint(
                    startY - 7.0D - unit(this.site.treeSeed(), i, 205) * 8.0D,
                    angle + signedUnit(this.site.treeSeed(), i, 206) * 0.25D,
                    length * 0.34D
            );
            Point midB = radialPoint(
                    this.site.maxY() - 8.0D - unit(this.site.treeSeed(), i, 207) * 8.0D,
                    angle + signedUnit(this.site.treeSeed(), i, 208) * 0.35D,
                    length * 0.72D
            );
            Point end = radialPoint(
                    this.site.maxY() - 1.0D - unit(this.site.treeSeed(), i, 209) * 3.0D,
                    angle + signedUnit(this.site.treeSeed(), i, 210) * 0.35D,
                    length
            );

            PathDescriptor main = new PathDescriptor(
                    new Point[]{start, midA, midB, end},
                    5.4D + unit(this.site.treeSeed(), i, 211) * 3.8D,
                    2.0D + unit(this.site.treeSeed(), i, 212) * 1.8D,
                    mix(this.site.treeSeed() ^ (long) i * 0x55555555L ^ 0x5550504552L)
            );

            int secondaryCount = unit(this.site.treeSeed(), i, 213) < 0.45D ? 1 : 0;
            PathDescriptor[] paths = new PathDescriptor[1 + secondaryCount];
            paths[0] = main;
            if (secondaryCount == 1) {
                double splitProgress = 0.48D + unit(this.site.treeSeed(), i, 214) * 0.26D;
                Point split = pointOnPath(main, splitProgress);
                double splitAngle = angle + signedUnit(this.site.treeSeed(), i, 215) * 0.75D;
                double splitLength = length * (0.16D + unit(this.site.treeSeed(), i, 216) * 0.18D);
                Point splitEnd = offsetPoint(
                        split,
                        splitAngle,
                        splitLength,
                        4.0D + unit(this.site.treeSeed(), i, 217) * 6.0D
                );
                paths[1] = new PathDescriptor(
                        new Point[]{split, offsetPoint(split, splitAngle, splitLength * 0.55D, 1.5D), splitEnd},
                        Math.max(2.8D, main.startRadius() * 0.48D),
                        1.5D + unit(this.site.treeSeed(), i, 218) * 1.0D,
                        mix(this.site.treeSeed() ^ (long) i * 0xD1B54A32D192ED03L)
                );
            }

            roots[i] = new RootDescriptor(
                    i,
                    true,
                    paths,
                    computePathBounds(paths, 2),
                    false
            );
        }

        return roots;
    }

    private BranchDescriptor[] createBranches() {
        int count = 12;
        BranchDescriptor[] result = new BranchDescriptor[count];

        for (int i = 0; i < count; i++) {

            double angle = i * TWO_PI / count
                    + signedUnit(this.site.treeSeed(), i, 402) * 0.08D;

            double length = this.site.maxRadius()
                    * (0.56D + unit(this.site.treeSeed(), i, 403) * 0.12D);

            int startY = this.site.minY()
                    + floor(this.site.height()
                    * (0.68D + unit(this.site.treeSeed(), i, 404) * 0.10D));

            double startDist = radiusAtY(startY) * 0.64D;
            Point start = radialPoint(startY, angle, startDist);
            Point mid = radialPoint(
                    startY + signedUnit(this.site.treeSeed(), i, 405) * 3.0D,
                    angle + signedUnit(this.site.treeSeed(), i, 406) * 0.10D,
                    length * 0.50D
            );

            Point end = radialPoint(
                    startY + signedUnit(this.site.treeSeed(), i, 407) * 5.0D,
                    angle + signedUnit(this.site.treeSeed(), i, 408) * 0.14D,
                    length
            );
            PathDescriptor path = new PathDescriptor(
                    new Point[]{start, mid, end},
                    6.0D + unit(this.site.treeSeed(), i, 409) * 4.0D,
                    2.8D + unit(this.site.treeSeed(), i, 410) * 2.1D,
                    mix(this.site.treeSeed() ^ (long) i * 0x4252414E43484L)
            );

            int clusterCount = 2 + intRange(
                    this.site.treeSeed(),
                    i,
                    411,
                    2
            );

            LeafCluster[] clusters = new LeafCluster[clusterCount];

            for (int j = 0; j < clusterCount; j++) {
                double progress = clusterCount == 2
                        ? (j == 0 ? 0.62D : 0.94D)
                        : (j == 0 ? 0.50D : j == 1 ? 0.76D : 0.96D);

                Point base = pointOnPath(path, progress);

                double sideAngle = angle
                        + Math.PI / 2.0D
                        * signedUnit(this.site.treeSeed(), i, j, 412);

                Point center = offsetPoint(
                        base,
                        sideAngle,

                        signedUnit(this.site.treeSeed(), i, j, 413) * 5.0D,

                        -1.0D + signedUnit(this.site.treeSeed(), i, j, 414) * 4.0D
                );

                double horizontalRadius =
                        13.0D + unit(this.site.treeSeed(), i, j, 415) * 8.0D;

                double verticalRadius =
                        6.5D + unit(this.site.treeSeed(), i, j, 416) * 4.5D;

                clusters[j] = new LeafCluster(
                        i,
                        i * 4 + j,
                        center.x(),
                        clamp(
                                center.y(),
                                this.site.minY() + this.site.height() * 0.60D,
                                this.site.maxY() - 14.0D
                        ),
                        center.z(),
                        
                        horizontalRadius,
                        verticalRadius,
                        horizontalRadius,

                        mix(
                                this.site.treeSeed()
                                        ^ (long) i * 0xA0761D6478BD642FL
                                        ^ (long) j * 0xE7037ED1A0B428DBL
                        )
                );
            }

            result[i] = new BranchDescriptor(
                    i,
                    path,
                    clusters,
                    computeBranchBounds(path, clusters)
            );
        }

        return result;
    }

    private BoundingBox computeTrunkBounds() {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (int y = this.site.minY(); y <= this.site.maxY(); y += 2) {
            TrunkSlice slice = trunkSlice(y);
            int radius = ceil(slice.outerRadius());
            minX = Math.min(minX, floor(slice.centerX()) - radius);
            minZ = Math.min(minZ, floor(slice.centerZ()) - radius);
            maxX = Math.max(maxX, ceil(slice.centerX()) + radius);
            maxZ = Math.max(maxZ, ceil(slice.centerZ()) + radius);
        }

        return new BoundingBox(minX, this.site.minY(), minZ, maxX, this.site.maxY(), maxZ);
    }

    private BoundingBox computeCompleteBounds() {
        BoundingBox bounds = this.trunkBounds;
        for (RootDescriptor root : this.lowerRoots) {
            bounds = union(bounds, root.bounds());
        }
        for (RootDescriptor root : this.upperRoots) {
            bounds = union(bounds, root.bounds());
        }
        for (BranchDescriptor branch : this.branches) {
            bounds = union(bounds, branch.bounds());
        }
        return bounds;
    }

    private List<BlockPos> computeCandidateDungeonBasinCenters() {
        List<BlockPos> centers = new ArrayList<>();
        double phase = unit(this.site.treeSeed(), 501) * TWO_PI;
        int y = this.site.minY() + 10;
        double centerX = centerXAtY(y);
        double centerZ = centerZAtY(y);
        double radius = this.site.maxRadius() * 0.42D;

        for (int i = 0; i < 6; i++) {
            double angle = phase + i * TWO_PI / 6.0D;
            BlockPos candidate = new BlockPos(
                    floor(centerX + Math.cos(angle) * radius),
                    y,
                    floor(centerZ + Math.sin(angle) * radius)
            );
            if (!isLowerRoot(candidate.getX(), candidate.getY(), candidate.getZ())) {
                centers.add(candidate);
            }
        }

        if (centers.isEmpty()) {
            centers.add(new BlockPos(floor(centerX), y, floor(centerZ)));
        }

        return centers;
    }

    private double trunkMargin(TrunkSlice slice, int x, int z) {
        double dx = x + 0.5D - slice.centerX();
        double dz = z + 0.5D - slice.centerZ();
        if (dx * dx + dz * dz > slice.outerRadius() * slice.outerRadius()) {
            return -1000.0D;
        }

        double angle = Math.atan2(dz, dx);
        double y = slice.y() - this.site.minY();
        double stretchX = 1.0D + Math.sin(y * 0.029D + this.phaseA) * 0.055D;
        double stretchZ = 1.0D + Math.cos(y * 0.031D + this.phaseC) * 0.055D;
        double distance = Math.sqrt((dx / stretchX) * (dx / stretchX) + (dz / stretchZ) * (dz / stretchZ));
        double cellNoise = signedUnit(this.site.treeSeed(), floor(x / 5.0D), slice.y() / 4, floor(z / 5.0D), 601);
        double irregularity = 1.0D
                + Math.sin(angle * 5.0D + y * 0.083D + this.phaseB) * 0.105D
                + Math.sin(angle * 9.0D - y * 0.041D + this.phaseD) * 0.055D
                + cellNoise * 0.045D;

        return slice.radius() * irregularity - distance;
    }

    private boolean isTrunkHollow(TrunkSlice slice, int x, int z) {
        double t = normalizedY(slice.y());
        if (t < 0.15D || t > 0.88D) {
            return false;
        }

        double dx = x + 0.5D - slice.centerX();
        double dz = z + 0.5D - slice.centerZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        double innerRadius = Math.min(
                slice.radius() - MIN_SHELL_THICKNESS,
                Math.max(3.4D, slice.radius() * 0.43D)
        );

        if (innerRadius >= 3.0D && distance < innerRadius) {
            double angle = Math.atan2(dz, dx);
            if (!isButtressColumn(angle, distance, innerRadius, slice.y())) {
                return true;
            }
        }

        double waistBand = 1.0D - Math.abs(t - 0.505D) / 0.085D;
        if (waistBand <= 0.0D) {
            return false;
        }

        double doorwayWidth = 0.15D + waistBand * 0.08D;
        double phase = unit(this.site.treeSeed(), 701) * TWO_PI;
        for (int i = 0; i < 4; i++) {
            double openingAngle = phase + i * Math.PI / 2.0D;
            if (angleDistance(Math.atan2(dz, dx), openingAngle) < doorwayWidth
                    && distance < slice.radius() - 1.3D
                    && distance > innerRadius * 0.45D) {
                return true;
            }
        }

        return false;
    }

    private boolean isButtressColumn(double angle, double distance, double innerRadius, int y) {
        if (distance < innerRadius * 0.42D) {
            return false;
        }

        double phase = unit(this.site.treeSeed(), 702) * TWO_PI;
        for (int i = 0; i < 3; i++) {
            double columnAngle = phase + i * TWO_PI / 3.0D + Math.sin((y - this.site.minY()) * 0.027D + i) * 0.09D;
            if (angleDistance(angle, columnAngle) < 0.18D) {
                return true;
            }
        }
        return false;
    }

    private boolean containsRoot(RootDescriptor root, int x, int y, int z) {
        if (!contains(root.bounds(), x, y, z)) {
            return false;
        }

        for (PathDescriptor path : root.paths()) {
            if (containsPath(path, x, y, z)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsPath(PathDescriptor path, int x, int y, int z) {
        PathHit hit = closestPathHit(path, x, y, z);
        return hit.inside();
    }

    private boolean isRootSurface(RootDescriptor root, int x, int y, int z) {
        if (!contains(root.bounds(), x, y, z)) {
            return false;
        }

        for (PathDescriptor path : root.paths()) {
            PathHit hit = closestPathHit(path, x, y, z);
            if (hit.inside() && hit.margin() <= 1.55D) {
                return true;
            }
        }
        return false;
    }

    private boolean isRootTunnel(RootDescriptor root, int x, int y, int z) {
        for (PathDescriptor path : root.paths()) {
            PathHit hit = closestPathHit(path, x, y, z);
            if (!hit.inside()) {
                continue;
            }
            if (hit.progress() < 0.20D || hit.progress() > 0.74D || hit.radius() < 5.8D) {
                continue;
            }
            double tunnelRadius = Math.min(hit.radius() - 2.8D, hit.radius() * 0.33D);
            if (tunnelRadius >= 1.8D && hit.distance() <= tunnelRadius) {
                return true;
            }
        }
        return false;
    }

    private PathHit closestRootHit(RootDescriptor root, int x, int y, int z) {
        PathHit best = PathHit.miss();
        for (PathDescriptor path : root.paths()) {
            PathHit hit = closestPathHit(path, x, y, z);
            if (hit.distance() < best.distance()) {
                best = hit;
            }
        }
        return best;
    }

    private PathHit closestPathHit(PathDescriptor path, int x, int y, int z) {
        Point[] points = path.points();
        double px = x + 0.5D;
        double py = y + 0.5D;
        double pz = z + 0.5D;
        double bestDistance = Double.MAX_VALUE;
        double bestRadius = 0.0D;
        double bestProgress = 0.0D;

        for (int i = 0; i < points.length - 1; i++) {
            Point a = points[i];
            Point b = points[i + 1];
            double abx = b.x() - a.x();
            double aby = b.y() - a.y();
            double abz = b.z() - a.z();
            double lengthSq = abx * abx + aby * aby + abz * abz;
            if (lengthSq <= 0.0001D) {
                continue;
            }

            double t = ((px - a.x()) * abx + (py - a.y()) * aby + (pz - a.z()) * abz) / lengthSq;
            t = clamp(t, 0.0D, 1.0D);
            double closestX = a.x() + abx * t;
            double closestY = a.y() + aby * t;
            double closestZ = a.z() + abz * t;
            double dx = px - closestX;
            double dy = py - closestY;
            double dz = pz - closestZ;
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double progress = (i + t) / (points.length - 1.0D);
            double radius = lerp(path.startRadius(), path.endRadius(), smooth(progress));
            radius *= 1.0D + Math.sin(progress * Math.PI) * 0.16D;
            radius *= 1.0D + signedUnit(path.salt(), floor(closestX / 5.0D), floor(closestY / 4.0D), floor(closestZ / 5.0D), 801) * 0.08D;

            if (distance < bestDistance) {
                bestDistance = distance;
                bestRadius = radius;
                bestProgress = progress;
            }
        }

        return new PathHit(bestDistance, bestRadius, bestProgress);
    }

    private boolean clusterContainsLeaf(LeafCluster cluster, int x, int y, int z) {
        double nx = (x + 0.5D - cluster.x()) / cluster.radiusX();
        double ny = (y + 0.5D - cluster.y()) / cluster.radiusY();
        double nz = (z + 0.5D - cluster.z()) / cluster.radiusZ();
        double distance = nx * nx + ny * ny + nz * nz;
        if (distance > 1.30D) {
            return false;
        }

        double edgeNoise = signedUnit(
                cluster.salt(),
                floor(x / 3.0D),
                floor(y / 3.0D),
                floor(z / 3.0D),
                901
        );
        double edge = 1.0D + edgeNoise * 0.26D;
        if (distance > edge) {
            return false;
        }

        double holeNoise = unit(
                cluster.salt(),
                floor(x / 5.0D),
                floor(y / 4.0D),
                floor(z / 5.0D),
                902
        );
        return !(distance > 0.28D && holeNoise < 0.105D);
    }

    private boolean rootTouchesTrunk(RootDescriptor root) {
        Point start = root.paths()[0].points()[0];
        return isTrunk(
                (int) Math.round(start.x()),
                (int) Math.round(start.y()),
                (int) Math.round(start.z())
        );
    }

    private Point radialPoint(double y, double angle, double distance) {
        double centerX = centerXAtY((int) Math.round(y));
        double centerZ = centerZAtY((int) Math.round(y));
        return new Point(
                centerX + Math.cos(angle) * distance,
                clamp(y, this.site.minY(), this.site.maxY()),
                centerZ + Math.sin(angle) * distance
        );
    }

    private static Point offsetPoint(Point origin, double angle, double distance, double deltaY) {
        return new Point(
                origin.x() + Math.cos(angle) * distance,
                origin.y() + deltaY,
                origin.z() + Math.sin(angle) * distance
        );
    }

    private static Point pointOnPath(PathDescriptor path, double progress) {
        Point[] points = path.points();
        double scaled = clamp(progress, 0.0D, 1.0D) * (points.length - 1);
        int index = Math.min(points.length - 2, floor(scaled));
        double local = scaled - index;
        Point a = points[index];
        Point b = points[index + 1];
        return new Point(
                lerp(a.x(), b.x(), local),
                lerp(a.y(), b.y(), local),
                lerp(a.z(), b.z(), local)
        );
    }

    private BoundingBox computePathBounds(PathDescriptor[] paths, int padding) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (PathDescriptor path : paths) {
            int inflate = ceil(Math.max(path.startRadius(), path.endRadius()) + padding);
            for (Point point : path.points()) {
                minX = Math.min(minX, floor(point.x()) - inflate);
                minY = Math.min(minY, floor(point.y()) - inflate);
                minZ = Math.min(minZ, floor(point.z()) - inflate);
                maxX = Math.max(maxX, ceil(point.x()) + inflate);
                maxY = Math.max(maxY, ceil(point.y()) + inflate);
                maxZ = Math.max(maxZ, ceil(point.z()) + inflate);
            }
        }

        return new BoundingBox(
                minX,
                Math.max(this.site.minY(), minY),
                minZ,
                maxX,
                Math.min(this.site.maxY(), maxY),
                maxZ
        );
    }

    private BoundingBox computeBranchBounds(PathDescriptor path, LeafCluster[] clusters) {
        BoundingBox bounds = computePathBounds(new PathDescriptor[]{path}, 2);
        for (LeafCluster cluster : clusters) {
            BoundingBox leafBounds = new BoundingBox(
                    floor(cluster.x() - cluster.radiusX()) - 2,
                    Math.max(this.site.minY(), floor(cluster.y() - cluster.radiusY()) - MAX_VINE_LENGTH - 2),
                    floor(cluster.z() - cluster.radiusZ()) - 2,
                    ceil(cluster.x() + cluster.radiusX()) + 2,
                    Math.min(this.site.maxY(), ceil(cluster.y() + cluster.radiusY()) + 2),
                    ceil(cluster.z() + cluster.radiusZ()) + 2
            );
            bounds = union(bounds, leafBounds);
        }
        return bounds;
    }

    private static BoundingBox union(BoundingBox first, BoundingBox second) {
        return new BoundingBox(
                Math.min(first.minX(), second.minX()),
                Math.min(first.minY(), second.minY()),
                Math.min(first.minZ(), second.minZ()),
                Math.max(first.maxX(), second.maxX()),
                Math.max(first.maxY(), second.maxY()),
                Math.max(first.maxZ(), second.maxZ())
        );
    }

    private static boolean contains(BoundingBox outer, BoundingBox inner) {
        return inner.minX() >= outer.minX()
                && inner.maxX() <= outer.maxX()
                && inner.minY() >= outer.minY()
                && inner.maxY() <= outer.maxY()
                && inner.minZ() >= outer.minZ()
                && inner.maxZ() <= outer.maxZ();
    }

    private static boolean contains(BoundingBox box, int x, int y, int z) {
        return x >= box.minX()
                && x <= box.maxX()
                && y >= box.minY()
                && y <= box.maxY()
                && z >= box.minZ()
                && z <= box.maxZ();
    }

    private double normalizedY(int y) {
        return clamp((y - this.site.minY()) / (double) this.site.height(), 0.0D, 1.0D);
    }

    private static double angleDistance(double a, double b) {
        double diff = Math.abs(a - b) % TWO_PI;
        return diff > Math.PI ? TWO_PI - diff : diff;
    }

    private static double smooth(double value) {
        double x = clamp(value, 0.0D, 1.0D);
        return x * x * (3.0D - 2.0D * x);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static int ceil(double value) {
        return (int) Math.ceil(value);
    }

    private static int intRange(long seed, int salt, int bound) {
        return (int) Math.floor(unit(seed, salt) * bound);
    }

    private static int intRange(long seed, int a, int salt, int bound) {
        return (int) Math.floor(unit(seed, a, salt) * bound);
    }

    private static int intRange(long seed, int a, int b, int salt, int bound) {
        return (int) Math.floor(unit(seed, a, b, salt) * bound);
    }

    private static int intRange(long seed, int a, int b, int c, int salt, int bound) {
        return (int) Math.floor(unit(seed, a, b, c, salt) * bound);
    }

    private static double signedUnit(long seed, int salt) {
        return unit(seed, salt) * 2.0D - 1.0D;
    }

    private static double signedUnit(long seed, int a, int salt) {
        return unit(seed, a, salt) * 2.0D - 1.0D;
    }

    private static double signedUnit(long seed, int a, int b, int salt) {
        return unit(seed, a, b, salt) * 2.0D - 1.0D;
    }

    private static double signedUnit(long seed, int a, int b, int c, int salt) {
        return unit(seed, a, b, c, salt) * 2.0D - 1.0D;
    }

    private static double unit(long seed, int salt) {
        return toUnit(mix(seed ^ (long) salt * 0x9E3779B97F4A7C15L));
    }

    private static double unit(long seed, int a, int salt) {
        long value = seed;
        value ^= (long) a * 0x632BE59BD9B4E019L;
        value ^= (long) salt * 0x9E3779B97F4A7C15L;
        return toUnit(mix(value));
    }

    private static double unit(long seed, int a, int b, int salt) {
        long value = seed;
        value ^= (long) a * 0x632BE59BD9B4E019L;
        value ^= (long) b * 0x85157AF5L;
        value ^= (long) salt * 0x9E3779B97F4A7C15L;
        return toUnit(mix(value));
    }

    private static double unit(long seed, int a, int b, int c, int salt) {
        long value = seed;
        value ^= (long) a * 0x632BE59BD9B4E019L;
        value ^= (long) b * 0x85157AF5L;
        value ^= (long) c * 0x94D049BB133111EBL;
        value ^= (long) salt * 0x9E3779B97F4A7C15L;
        return toUnit(mix(value));
    }

    private static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        value ^= value >>> 33;
        return value;
    }

    private static double toUnit(long value) {
        return (double) (value >>> 11) * 0x1.0p-53D;
    }

    public record TrunkSlice(
            int y,
            double centerX,
            double centerZ,
            double radius,
            double outerRadius
    ) {
    }

    public record VineColumn(
            int topY,
            int bottomY
    ) {
        public int length() {
            return this.topY - this.bottomY + 1;
        }
    }

    private record RootDescriptor(
            int index,
            boolean upper,
            PathDescriptor[] paths,
            BoundingBox bounds,
            boolean hasTunnel
    ) {
    }

    private record BranchDescriptor(
            int index,
            PathDescriptor path,
            LeafCluster[] clusters,
            BoundingBox bounds
    ) {
    }

    private record LeafCluster(
            int branchIndex,
            int clusterIndex,
            double x,
            double y,
            double z,
            double radiusX,
            double radiusY,
            double radiusZ,
            long salt
    ) {
    }

    private record PathDescriptor(
            Point[] points,
            double startRadius,
            double endRadius,
            long salt
    ) {
    }

    private record Point(
            double x,
            double y,
            double z
    ) {
    }

    private record PathHit(
            double distance,
            double radius,
            double progress
    ) {
        static PathHit miss() {
            return new PathHit(Double.MAX_VALUE, 0.0D, 0.0D);
        }

        boolean inside() {
            return this.distance <= this.radius;
        }

        double margin() {
            return this.radius - this.distance;
        }
    }
}
