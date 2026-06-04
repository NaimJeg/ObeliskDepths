package io.github.naimjeg.obeliskdepths.worldgen.structure.tree;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AmphixylonField {
    public static final int MIN_SHELL_THICKNESS = 4;
    public static final int MAX_VINE_LENGTH = 18;

    private static final double TWO_PI = Math.PI * 2.0D;

    /* Secondary roots in both halves use a full, low rounded section. */
    private static final double LOWER_SECONDARY_TOP_SCALE = 0.78D;
    private static final double LOWER_SECONDARY_BOTTOM_SCALE = 0.88D;
    private static final double ROOT_THICKNESS_SCALE = 0.65D;
    private static final double ROOT_DENSITY_SCALE = 1.50D;

    /*
     * Main-root buttress profile, mirrored vertically for the upper half.
     *
     * The root is tall and laterally narrow beside the trunk. Toward the tip,
     * its height falls rapidly while its lateral width gradually recovers.
     */
    private static final double BUTTRESS_BASE_TOP_SCALE = 1.72D;
    private static final double BUTTRESS_TIP_TOP_SCALE = 0.68D;
    private static final double BUTTRESS_BOTTOM_SCALE = 0.46D;
    private static final double BUTTRESS_DECAY_EXPONENT = 1.60D;

    private static final double BUTTRESS_BASE_SIDE_SCALE = 0.68D;
    private static final double BUTTRESS_TIP_SIDE_SCALE = 0.94D;
    private static final double BUTTRESS_ROUND_BLEND_START = 0.42D;
    private static final double BUTTRESS_ROUND_BLEND_END = 0.80D;
    private static final double ROOT_RADIUS_NOISE_ALLOWANCE = 1.06D;
    private static final double ROOT_TUNNEL_MIN_SHELL = 3.0D;
    private static final double TRUNK_HOLLOW_RADIUS_SCALE = 0.50D;
    private static final double TRUNK_HOLLOW_MIN_RADIUS = 3.8D;
    private static final double TRUNK_HOLLOW_LOWER_CAP_START = 0.07D;
    private static final double TRUNK_HOLLOW_LOWER_CAP_END = 0.15D;
    private static final double TRUNK_HOLLOW_UPPER_CAP_START = 0.85D;
    private static final double TRUNK_HOLLOW_UPPER_CAP_END = 0.93D;
    private static final double ROOT_TUNNEL_START_PROGRESS = 0.02D;
    private static final double ROOT_TUNNEL_FULL_PROGRESS = 0.10D;
    private static final double ROOT_TUNNEL_FADE_PROGRESS = 0.56D;
    private static final double ROOT_TUNNEL_END_PROGRESS = 0.72D;

    private final AmphixylonSite site;
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

    public AmphixylonField(AmphixylonSite site) {
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
        // Temporary reference-image crown. Restore the original crown with:
        // this.branches = createBranches();
        this.branches = createWorldTreeCrownBranches();
        this.trunkBounds = computeTrunkBounds();
        this.completeBounds = computeCompleteBounds();
        this.candidateDungeonBasinCenters = List.copyOf(computeCandidateDungeonBasinCenters());
    }

    public AmphixylonSite site() {
        return this.site;
    }

    public int lowerRootCount() {
        return this.lowerRoots.length;
    }

    static double rootThicknessScale() {
        return ROOT_THICKNESS_SCALE;
    }

    int lowerRootPathCount(int index) {
        return this.lowerRoots[index].paths().length;
    }

    int upperRootPathCount(int index) {
        return this.upperRoots[index].paths().length;
    }

    int lowerRootMainPointCount(int index) {
        return this.lowerRoots[index].paths()[0].points().length;
    }

    int upperRootMainPointCount(int index) {
        return this.upperRoots[index].paths()[0].points().length;
    }

    RootTopology lowerRootTopology(int index) {
        return rootTopology(this.lowerRoots[index]);
    }

    RootTopology upperRootTopology(int index) {
        return rootTopology(this.upperRoots[index]);
    }

    boolean lowerRootHasTunnel(int index) {
        return this.lowerRoots[index].hasTunnel();
    }

    double trunkHollowRadiusAtY(int y) {
        if (y < this.site.minY() || y > this.site.maxY()) {
            return 0.0D;
        }
        return trunkHollowRadius(trunkSlice(y));
    }

    boolean isTrunkHollowGeometry(int x, int y, int z) {
        if (y < this.site.minY() || y > this.site.maxY()) {
            return false;
        }
        TrunkSlice slice = trunkSlice(y);
        return isTrunk(slice, x, z) && isTrunkHollow(slice, x, z);
    }

    boolean isLowerRootGeometry(int index, int x, int y, int z) {
        return containsRootGeometry(this.lowerRoots[index], x, y, z);
    }

    boolean isUpperRootGeometry(int index, int x, int y, int z) {
        return containsRootGeometry(this.upperRoots[index], x, y, z);
    }

    double lowerRootMargin(int index, int x, int y, int z) {
        return closestRootHit(this.lowerRoots[index], x, y, z).margin();
    }

    RootCrossSection lowerMainRootCrossSection(int index, double visibleProgress) {
        return mainRootCrossSection(this.lowerRoots[index], visibleProgress);
    }

    RootCrossSection lowerMainRootPathCrossSection(int index, double pathProgress) {
        RootDescriptor root = this.lowerRoots[index];
        PathDescriptor path = root.paths()[0];
        double clampedPath = clamp(pathProgress, 0.0D, 1.0D);
        double arcLength = path.totalLength() * clampedPath;
        double visibleProgress = clamp(
                (arcLength - root.visibleStartArcLength())
                        / Math.max(0.0001D, path.totalLength() - root.visibleStartArcLength()),
                0.0D,
                1.0D
        );
        PathLocation location = path.locationAtArc(arcLength);
        double radius = rootRadiusAtProgress(path, visibleProgress);
        ButtressProfile profile = buttressProfile(path, radius, visibleProgress);
        return RootCrossSection.buttress(
                location,
                radius,
                profile,
                visibleProgress,
                false
        );
    }

    RootCrossSection upperMainRootCrossSection(int index, double visibleProgress) {
        return mainRootCrossSection(this.upperRoots[index], visibleProgress);
    }

    private RootCrossSection mainRootCrossSection(
            RootDescriptor root,
            double visibleProgress
    ) {
        PathDescriptor path = root.paths()[0];
        double clampedVisible = clamp(visibleProgress, 0.0D, 1.0D);
        double arcLength = lerp(
                root.visibleStartArcLength(),
                path.totalLength(),
                clampedVisible
        );
        PathLocation location = path.locationAtArc(arcLength);
        double radius = rootRadiusAtProgress(path, clampedVisible);
        ButtressProfile profile = buttressProfile(path, radius, clampedVisible);
        return RootCrossSection.buttress(
                location,
                radius,
                profile,
                clampedVisible,
                root.upper()
        );
    }

    RootCrossSection lowerSecondaryRootCrossSection(
            int index,
            int pathIndex,
            double progress
    ) {
        return secondaryRootCrossSection(
                this.lowerRoots[index],
                pathIndex,
                progress
        );
    }

    RootCrossSection upperSecondaryRootCrossSection(
            int index,
            int pathIndex,
            double progress
    ) {
        return secondaryRootCrossSection(
                this.upperRoots[index],
                pathIndex,
                progress
        );
    }

    private RootCrossSection secondaryRootCrossSection(
            RootDescriptor root,
            int pathIndex,
            double progress
    ) {
        if (pathIndex <= 0) {
            throw new IllegalArgumentException("pathIndex must identify a secondary root");
        }
        PathDescriptor path = root.paths()[pathIndex];
        double clampedProgress = clamp(progress, 0.0D, 1.0D);
        PathLocation location = path.locationAtArc(path.totalLength() * clampedProgress);
        double radius = rootRadiusAtProgress(path, clampedProgress);
        return RootCrossSection.rounded(
                location,
                radius,
                radius * LOWER_SECONDARY_TOP_SCALE,
                radius * LOWER_SECONDARY_BOTTOM_SCALE,
                clampedProgress,
                root.upper()
        );
    }

    private RootTopology rootTopology(RootDescriptor root) {
        PathDescriptor main = root.paths()[0];
        Point start = main.points()[0];
        Point end = main.points()[main.points().length - 1];
        double startBoundaryDepth = root.upper()
                ? this.site.maxY() - start.y()
                : start.y() - this.site.minY();
        double endBoundaryDepth = root.upper()
                ? this.site.maxY() - end.y()
                : end.y() - this.site.minY();
        double outwardVerticalTravel = root.upper()
                ? end.y() - start.y()
                : start.y() - end.y();
        return new RootTopology(
                root.paths().length,
                main.points().length,
                startBoundaryDepth,
                endBoundaryDepth,
                outwardVerticalTravel,
                main.startRadius(),
                main.endRadius()
        );
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
        return hit.margin() >= -2.25D;
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
            if (!contains(this.completeBounds, root.bounds())
                     || !root.bounds().equals(computeRootBounds(
                     root.paths(),
                     root.visibleStartArcLength(),
                    3
            ))) {
                return false;
            }
            if (!rootTouchesTrunk(root)) {
                return false;
            }
        }
        for (RootDescriptor root : this.upperRoots) {
            if (!contains(this.completeBounds, root.bounds())
                     || !root.bounds().equals(computeRootBounds(
                     root.paths(),
                     root.visibleStartArcLength(),
                    3
            ))) {
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
        return createRoots(false);
    }

    private RootDescriptor[] createUpperRoots() {
        return createRoots(true);
    }

    /**
     * Generates both root halves through the same deterministic noise pipeline.
     *
     * <p>Both halves use the same five-stage root-collar topology. Each root begins
     * inside the trunk, crosses its corresponding outer shell through a shoulder,
     * and only then becomes a free root. Independent hash namespaces keep the two
     * halves naturally varied instead of block-for-block mirrored.</p>
     */
    private RootDescriptor[] createRoots(boolean upper) {
        long seed = this.site.treeSeed();
        int namespace = upper ? 200 : 100;
        int verticalDirection = upper ? 1 : -1;
        int boundaryY = upper
                ? this.site.maxY()
                : this.site.minY();

        double angularPhase =
                unit(seed, namespace) * TWO_PI;

        double angleJitter = 0.22D;
        double minimumLengthScale = 0.72D;
        double lengthVariation = 0.34D;

        double attachmentBase = 0.15D;
        double attachmentVariation = 0.08D;

        double minimumStartRadius = upper
                ? 6.8D
                : 8.0D;

        double startRadiusVariation = upper
                ? 4.2D
                : 5.0D;

        double minimumTipRadius = upper
                ? 2.3D
                : 2.8D;

        double tipRadiusVariation = upper
                ? 1.9D
                : 2.2D;

        double minimumSecondaryStartRadius = upper
                ? 3.2D
                : 3.8D;

        double minimumSecondaryTipRadius = upper
                ? 1.5D
                : 1.8D;

        double secondaryTipVariation = upper
                ? 1.2D
                : 1.5D;

        long mainHalfSalt = upper
                ? 0x5550504552L
                : 0x4C4F574552L;

        long secondaryHalfSalt = upper
                ? 0xD1B54A32D192ED03L
                : 0x632BE5ABL;

        int baseCount = 8 + intRange(seed, namespace + 1, 7);
        int count = (int) Math.round(baseCount * ROOT_DENSITY_SCALE);

        RootDescriptor[] roots =
                new RootDescriptor[count];

        for (int i = 0; i < count; i++) {
            double angle = angularPhase
                    + i * TWO_PI / count
                    + signedUnit(
                    seed,
                    i,
                    namespace + 2
            ) * angleJitter;

            double length = this.site.maxRadius()
                    * (
                    minimumLengthScale
                            + unit(
                            seed,
                            i,
                            namespace + 3
                    ) * lengthVariation
            );

            double attachmentDistanceFromBoundary =
                    this.site.height()
                            * (
                            attachmentBase
                                    + unit(
                                    seed,
                                    i,
                                    namespace + 4
                            ) * attachmentVariation
                    );

            int startY = boundaryY
                    - verticalDirection
                    * floor(attachmentDistanceFromBoundary);

            double trunkRadiusAtStart = radiusAtY(startY);

            /* Both halves begin equally deep inside the trunk collar. */
            double startDistance = trunkRadiusAtStart * 0.32D;

            Point start = radialPoint(
                    startY,
                    angle,
                    startDistance
            );

            double mainStartRadius =
                    minimumStartRadius
                            + unit(
                            seed,
                            i,
                            namespace + 11
                    ) * startRadiusVariation;

            /* Keep the mirrored collars equally embedded in the trunk shell. */
            mainStartRadius = Math.max(
                    mainStartRadius,
                    trunkRadiusAtStart * 0.42D
            );
            mainStartRadius *= ROOT_THICKNESS_SCALE;

            Point[] mainPoints;

            if (upper) {
                /*
                 * Mirror the lower five-stage root-collar logic toward the upper
                 * boundary. Hash namespaces remain independent, so this is not a
                 * block-for-block reflection.
                 */
                double shoulderY = clamp(
                        startY
                                + 4.0D
                                + unit(seed, i, namespace + 5) * 4.0D,
                        startY + 1.0D,
                        this.site.maxY() - 10.0D
                );
                double shoulderAngle = angle
                        + signedUnit(seed, i, namespace + 6) * 0.07D;
                double shoulderDistance = radiusAtY((int) Math.round(shoulderY)) * 0.88D;
                Point shoulder = radialPoint(
                        shoulderY,
                        shoulderAngle,
                        shoulderDistance
                );

                double desiredFootY = this.site.maxY()
                        - 8.0D
                        - unit(seed, i, namespace + 7) * 4.0D;
                double footY = clamp(
                        Math.max(shoulderY + 2.0D, desiredFootY),
                        shoulderY + 2.0D,
                        this.site.maxY() - 6.0D
                );
                double footAngle = angle
                        + signedUnit(seed, i, namespace + 8) * 0.13D;
                double footDistance = Math.max(
                        radiusAtY((int) Math.round(footY)) * 0.98D,
                        length * 0.34D
                );
                Point foot = radialPoint(footY, footAngle, footDistance);

                double desiredMidY = this.site.maxY()
                        - 5.0D
                        - unit(seed, i, namespace + 21) * 4.0D;
                double midY = clamp(
                        Math.max(footY + 1.0D, desiredMidY),
                        footY + 1.0D,
                        this.site.maxY() - 4.0D
                );
                double midAngle = angle
                        + signedUnit(seed, i, namespace + 22) * 0.24D;
                double midDistance = Math.max(
                        footDistance + 4.0D,
                        length * 0.70D
                );
                Point mid = radialPoint(midY, midAngle, midDistance);

                double desiredEndY = this.site.maxY()
                        - 2.0D
                        - unit(seed, i, namespace + 9) * 3.0D;
                double endY = clamp(
                        Math.max(midY + 1.0D, desiredEndY),
                        midY + 1.0D,
                        this.site.maxY() - 2.0D
                );
                Point end = radialPoint(
                        endY,
                        angle + signedUnit(seed, i, namespace + 10) * 0.30D,
                        length
                );

                mainPoints = new Point[]{
                        start,
                        shoulder,
                        foot,
                        mid,
                        end
                };
            } else {
                /*
                 * Root-collar sequence:
                 *
                 * start    — deep inside the lower trunk
                 * shoulder — reaches the trunk shell while descending
                 * foot     — exits the shell close to the ground
                 * mid      — continues outward as a free root
                 * end      — terminates as a thin ground-level root
                 */

                double shoulderY = clamp(
                        startY
                                - (
                                4.0D
                                        + unit(
                                        seed,
                                        i,
                                        namespace + 5
                                ) * 4.0D
                        ),
                        this.site.minY() + 10.0D,
                        startY - 1.0D
                );

                double shoulderAngle = angle
                        + signedUnit(
                        seed,
                        i,
                        namespace + 6
                ) * 0.07D;

                double shoulderDistance =
                        radiusAtY((int) Math.round(shoulderY))
                                * 0.88D;

                Point shoulder = radialPoint(
                        shoulderY,
                        shoulderAngle,
                        shoulderDistance
                );

                double desiredFootY =
                        this.site.minY()
                                + 8.0D
                                + unit(
                                seed,
                                i,
                                namespace + 7
                        ) * 4.0D;

                double footY = clamp(
                        Math.min(
                                shoulderY - 2.0D,
                                desiredFootY
                        ),
                        this.site.minY() + 6.0D,
                        shoulderY - 2.0D
                );

                double footAngle = angle
                        + signedUnit(
                        seed,
                        i,
                        namespace + 8
                ) * 0.13D;

                double footDistance = Math.max(
                        radiusAtY((int) Math.round(footY))
                                * 0.98D,
                        length * 0.34D
                );

                Point foot = radialPoint(
                        footY,
                        footAngle,
                        footDistance
                );

                double desiredMidY =
                        this.site.minY()
                                + 5.0D
                                + unit(
                                seed,
                                i,
                                namespace + 21
                        ) * 4.0D;

                double midY = clamp(
                        Math.min(
                                footY - 1.0D,
                                desiredMidY
                        ),
                        this.site.minY() + 4.0D,
                        footY - 1.0D
                );

                double midAngle = angle
                        + signedUnit(
                        seed,
                        i,
                        namespace + 22
                ) * 0.24D;

                double midDistance = Math.max(
                        footDistance + 4.0D,
                        length * 0.70D
                );

                Point mid = radialPoint(
                        midY,
                        midAngle,
                        midDistance
                );

                double desiredEndY =
                        this.site.minY()
                                + 2.0D
                                + unit(
                                seed,
                                i,
                                namespace + 9
                        ) * 3.0D;

                double endY = clamp(
                        Math.min(
                                midY - 1.0D,
                                desiredEndY
                        ),
                        this.site.minY() + 2.0D,
                        midY - 1.0D
                );

                Point end = radialPoint(
                        endY,
                        angle
                                + signedUnit(
                                seed,
                                i,
                                namespace + 10
                        ) * 0.30D,
                        length
                );

                mainPoints = new Point[]{
                        start,
                        shoulder,
                        foot,
                        mid,
                        end
                };
            }

            PathDescriptor main = new PathDescriptor(
                    mainPoints,
                    mainStartRadius,
                    (minimumTipRadius
                            + unit(
                            seed,
                            i,
                            namespace + 12
                    ) * tipRadiusVariation) * ROOT_THICKNESS_SCALE,
                    mix(
                            seed
                                    ^ (long) i
                                    * 0x70A582D7L
                                    ^ mainHalfSalt
                    )
            );

            int secondaryCount = intRange(
                    seed,
                    i,
                    namespace + 13,
                    3
            );

            PathDescriptor[] paths =
                    new PathDescriptor[1 + secondaryCount];

            paths[0] = main;

            for (int j = 0; j < secondaryCount; j++) {
                double splitProgress = 0.50D
                        + unit(
                        seed,
                        i,
                        j,
                        namespace + 14
                ) * 0.24D;

                Point split = pointOnPath(
                        main,
                        splitProgress
                );

                double side = j == 0
                        ? -1.0D
                        : 1.0D;

                double splitAngle = angle
                        + side
                        * (
                        0.35D
                                + unit(
                                seed,
                                i,
                                j,
                                namespace + 15
                        ) * 0.35D
                );

                double splitLength = length
                        * (
                        0.22D
                                + unit(
                                seed,
                                i,
                                j,
                                namespace + 16
                        ) * 0.22D
                );

                /*
                 * Restore the common secondary-path behavior for both halves.
                 * The upper half is no longer treated as a crown.
                 */
                double splitMidDeltaY =
                        verticalDirection
                                * (
                                2.0D
                                        + signedUnit(
                                        seed,
                                        i,
                                        j,
                                        namespace + 17
                                ) * 2.0D
                        );

                double splitEndDeltaY =
                        verticalDirection
                                * (
                                4.0D
                                        + unit(
                                        seed,
                                        i,
                                        j,
                                        namespace + 18
                                ) * 4.0D
                        );

                Point splitMid = offsetPoint(
                        split,
                        splitAngle,
                        splitLength * 0.52D,
                        splitMidDeltaY
                );

                Point splitEnd = offsetPoint(
                        split,
                        splitAngle,
                        splitLength,
                        splitEndDeltaY
                );

                paths[j + 1] = new PathDescriptor(
                        new Point[]{
                                split,
                                splitMid,
                                splitEnd
                        },
                        Math.max(
                                minimumSecondaryStartRadius * ROOT_THICKNESS_SCALE,
                                main.startRadius() * 0.55D
                        ),
                        (minimumSecondaryTipRadius
                                + unit(
                                seed,
                                i,
                                j,
                                namespace + 19
                        ) * secondaryTipVariation) * ROOT_THICKNESS_SCALE,
                        mix(
                                seed
                                        ^ (long) i
                                        * 0x9E3779B9L
                                        ^ (long) j
                                        * 0x632BE5ABL
                                        ^ secondaryHalfSalt
                        )
                );
            }

            double visibleStartArcLength = findVisibleRootExitArc(main);

            roots[i] = new RootDescriptor(
                    i,
                    upper,
                    paths,
                    computeRootBounds(
                            paths,
                            visibleStartArcLength,
                            3
                    ),
                    visibleStartArcLength,
                    !upper
                            && unit(
                            seed,
                            i,
                            namespace + 20
                    ) < 0.55D
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

            int startY = this.site.waistY();

            double startDist = radiusAtY(startY) * 0.64D;
            Point start = radialPoint(startY, angle, startDist);
            Point mid = radialPoint(
                    startY,
                    angle + signedUnit(this.site.treeSeed(), i, 406) * 0.10D,
                    length * 0.50D
            );

            Point end = radialPoint(
                    startY,
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

                        signedUnit(this.site.treeSeed(), i, j, 414) * 1.5D
                );

                /*
                 * Several clusters collectively form the canopy. Keeping each unit compact
                 * prevents two or three neighboring clusters from merging into one giant mass.
                 */
                double horizontalRadius =
                        9.0D + unit(this.site.treeSeed(), i, j, 415) * 4.0D;

                double verticalRadius =
                        4.5D + unit(this.site.treeSeed(), i, j, 416) * 2.5D;

                clusters[j] = new LeafCluster(
                        i,
                        i * 4 + j,
                        center.x(),
                        clamp(
                                center.y(),
                                startY - 2.0D,
                                startY + 2.0D
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

    /**
     * Temporary broad, domed world-tree crown based on the supplied visual reference.
     *
     * <p>Overlapping foliage tiers build one continuous silhouette: a compact high
     * dome around the trunk, increasingly broad middle shoulders, and low outer
     * skirts. The supporting limbs rise before sweeping back down toward their
     * tips, while the normal leaf-edge noise and hanging-vine logic remain in use.</p>
     */
    private BranchDescriptor[] createWorldTreeCrownBranches() {
        int count = 12;
        BranchDescriptor[] result = new BranchDescriptor[count];
        int crownBaseY = this.site.waistY();
        double crownHeight = this.site.height();

        double[] clusterProgress = {0.18D, 0.38D, 0.60D, 0.81D, 0.98D};
        double[] clusterRise = {0.32D, 0.27D, 0.20D, 0.11D, 0.035D};
        double[] clusterHorizontalRadius = {16.5D, 17.5D, 17.0D, 15.0D, 12.5D};
        double[] clusterVerticalRadius = {11.0D, 11.0D, 10.0D, 8.0D, 6.0D};

        for (int i = 0; i < count; i++) {
            double angle = i * TWO_PI / count
                    + signedUnit(this.site.treeSeed(), i, 1402) * 0.08D;
            double length = this.site.maxRadius()
                    * (0.56D + unit(this.site.treeSeed(), i, 1403) * 0.12D);

            double startDistance = radiusAtY(crownBaseY) * 0.64D;
            Point start = radialPoint(crownBaseY, angle, startDistance);
            Point inner = radialPoint(
                    crownBaseY + crownHeight * 0.12D,
                    angle + signedUnit(this.site.treeSeed(), i, 1404) * 0.08D,
                    length * 0.30D
            );
            Point shoulder = radialPoint(
                    crownBaseY + crownHeight * 0.17D,
                    angle + signedUnit(this.site.treeSeed(), i, 1406) * 0.10D,
                    length * 0.62D
            );
            Point end = radialPoint(
                    crownBaseY + crownHeight * 0.04D,
                    angle + signedUnit(this.site.treeSeed(), i, 1408) * 0.14D,
                    length
            );
            PathDescriptor path = new PathDescriptor(
                    new Point[]{start, inner, shoulder, end},
                    6.0D + unit(this.site.treeSeed(), i, 1409) * 4.0D,
                    2.8D + unit(this.site.treeSeed(), i, 1410) * 2.1D,
                    mix(this.site.treeSeed() ^ (long) i * 0x574F524C44545245L)
            );

            LeafCluster[] clusters = new LeafCluster[clusterProgress.length];
            for (int j = 0; j < clusters.length; j++) {
                double layerAngle = angle
                        + signedUnit(this.site.treeSeed(), i, j, 1411) * 0.09D;
                double layerY = crownBaseY
                        + crownHeight * clusterRise[j]
                        + signedUnit(this.site.treeSeed(), i, j, 1412) * 1.5D;
                Point radialCenter = radialPoint(
                        layerY,
                        layerAngle,
                        length * clusterProgress[j]
                );
                Point center = offsetPoint(
                        radialCenter,
                        layerAngle + Math.PI / 2.0D,
                        signedUnit(this.site.treeSeed(), i, j, 1413) * 3.5D,
                        0.0D
                );

                double horizontalRadius = clusterHorizontalRadius[j]
                        + unit(this.site.treeSeed(), i, j, 1415) * 3.0D;
                double verticalRadius = clusterVerticalRadius[j]
                        + unit(this.site.treeSeed(), i, j, 1416) * 2.5D;

                clusters[j] = new LeafCluster(
                        i,
                        i * 8 + j,
                        center.x(),
                        clamp(
                                center.y(),
                                crownBaseY - 2.0D,
                                this.site.maxY() - verticalRadius - 2.0D
                        ),
                        center.z(),
                        horizontalRadius,
                        verticalRadius,
                        horizontalRadius,
                        mix(
                                this.site.treeSeed()
                                        ^ (long) i * 0xA0761D6478BD642FL
                                        ^ (long) j * 0xE7037ED1A0B428DBL
                                        ^ 0x574F524C44545245L
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
        double dx = x + 0.5D - slice.centerX();
        double dz = z + 0.5D - slice.centerZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        double innerRadius = trunkHollowRadius(slice);

        if (innerRadius >= 0.75D && distance <= innerRadius) {
            return true;
        }

        return isRoundedHollowDoorway(slice, dx, dz, innerRadius);
    }

    private double trunkHollowRadius(TrunkSlice slice) {
        double t = normalizedY(slice.y());
        double lowerCap = smooth(
                (t - TRUNK_HOLLOW_LOWER_CAP_START)
                        / (TRUNK_HOLLOW_LOWER_CAP_END - TRUNK_HOLLOW_LOWER_CAP_START)
        );
        double upperCap = 1.0D - smooth(
                (t - TRUNK_HOLLOW_UPPER_CAP_START)
                        / (TRUNK_HOLLOW_UPPER_CAP_END - TRUNK_HOLLOW_UPPER_CAP_START)
        );
        double capScale = Math.min(lowerCap, upperCap);
        if (capScale <= 0.0D) {
            return 0.0D;
        }

        double fullRadius = Math.min(
                slice.radius() - MIN_SHELL_THICKNESS,
                Math.max(TRUNK_HOLLOW_MIN_RADIUS, slice.radius() * TRUNK_HOLLOW_RADIUS_SCALE)
        );
        return Math.max(0.0D, fullRadius * capScale);
    }

    private boolean isRoundedHollowDoorway(
            TrunkSlice slice,
            double dx,
            double dz,
            double innerRadius
    ) {
        double halfHeight = this.site.height() * 0.072D;
        double vertical = (slice.y() + 0.5D - this.site.waistY()) / halfHeight;
        if (Math.abs(vertical) >= 1.0D) {
            return false;
        }

        double verticalWidthScale = Math.sqrt(Math.max(0.0D, 1.0D - vertical * vertical));
        double halfWidth = (2.2D + slice.radius() * 0.035D) * verticalWidthScale;
        if (halfWidth < 0.65D) {
            return false;
        }

        double phase = unit(this.site.treeSeed(), 701) * TWO_PI;
        for (int i = 0; i < 4; i++) {
            double openingAngle = phase + i * Math.PI / 2.0D;
            double cos = Math.cos(openingAngle);
            double sin = Math.sin(openingAngle);
            double radial = dx * cos + dz * sin;
            double lateral = -dx * sin + dz * cos;
            double radialStart = Math.max(0.0D, innerRadius * 0.55D);
            double radialEnd = slice.radius() - 1.3D;
            if (radial >= radialStart
                    && radial <= radialEnd
                    && Math.abs(lateral) <= halfWidth) {
                return true;
            }
        }

        return false;
    }

    private boolean containsRoot(
            RootDescriptor root,
            int x,
            int y,
            int z
    ) {
        if (!contains(root.bounds(), x, y, z)) {
            return false;
        }

        return containsRootGeometry(root, x, y, z);
    }

    private boolean containsRootGeometry(
            RootDescriptor root,
            int x,
            int y,
            int z
    ) {

        PathDescriptor[] paths = root.paths();

        for (int pathIndex = 0;
             pathIndex < paths.length;
             pathIndex++) {
            if (closestRootPathHit(
                    root,
                    pathIndex,
                    x,
                    y,
                    z
            ).inside()) {
                return true;
            }
        }

        return false;
    }

    private boolean isRootOrTrunk(
            RootDescriptor root,
            int x,
            int y,
            int z
    ) {
        if (containsRoot(root, x, y, z)) {
            return true;
        }
        return y >= this.site.minY()
                && y <= this.site.maxY()
                && isTrunk(x, y, z);
    }

    private boolean containsPath(PathDescriptor path, int x, int y, int z) {
        PathHit hit = closestPathHit(path, x, y, z);
        return hit.inside();
    }

    private boolean isRootSurface(
            RootDescriptor root,
            int x,
            int y,
            int z
    ) {
        if (!containsRoot(root, x, y, z)) {
            return false;
        }

        return !isRootOrTrunk(root, x + 1, y, z)
                || !isRootOrTrunk(root, x - 1, y, z)
                || !isRootOrTrunk(root, x, y + 1, z)
                || !isRootOrTrunk(root, x, y - 1, z)
                || !isRootOrTrunk(root, x, y, z + 1)
                || !isRootOrTrunk(root, x, y, z - 1);
    }

    private boolean isRootTunnel(RootDescriptor root, int x, int y, int z) {
        for (int pathIndex = 0; pathIndex < root.paths().length; pathIndex++) {
            PathHit hit = closestRootPathHit(root, pathIndex, x, y, z);
            if (!hit.inside()) {
                continue;
            }

            double startProgress = hit.progress();
            double endProgress = hit.mainButtress()
                    ? hit.visibleProgress()
                    : hit.progress();
            if (startProgress < ROOT_TUNNEL_START_PROGRESS
                    || endProgress > ROOT_TUNNEL_END_PROGRESS) {
                continue;
            }

            double entranceScale = smooth(
                    (startProgress - ROOT_TUNNEL_START_PROGRESS)
                            / (ROOT_TUNNEL_FULL_PROGRESS - ROOT_TUNNEL_START_PROGRESS)
            );
            double endScale = 1.0D - smooth(
                    (endProgress - ROOT_TUNNEL_FADE_PROGRESS)
                            / (ROOT_TUNNEL_END_PROGRESS - ROOT_TUNNEL_FADE_PROGRESS)
            );
            double longitudinalScale = Math.sqrt(Math.max(0.0D, Math.min(entranceScale, endScale)));
            if (longitudinalScale <= 0.0D) {
                continue;
            }

            double tunnelCenterVertical = Math.min(
                    1.35D,
                    hit.bottomDepth() * 0.28D
            );
            double sideAtTunnel = hit.tunnelSideHalfWidth();
            double tunnelRadius = Math.min(
                    sideAtTunnel - ROOT_TUNNEL_MIN_SHELL,
                    Math.min(
                            hit.bottomDepth() + tunnelCenterVertical - ROOT_TUNNEL_MIN_SHELL,
                            hit.topHeight() - tunnelCenterVertical - ROOT_TUNNEL_MIN_SHELL
                    )
            );
            tunnelRadius = Math.min(tunnelRadius, hit.radius() * 0.30D);
            tunnelRadius *= longitudinalScale;

            if (tunnelRadius < 0.75D || hit.margin() < ROOT_TUNNEL_MIN_SHELL) {
                continue;
            }

            double tunnelVertical = hit.verticalOffset() - tunnelCenterVertical;
            double tunnelDistance = Math.sqrt(
                    hit.sideOffset() * hit.sideOffset()
                            + tunnelVertical * tunnelVertical
            );
            if (tunnelDistance <= tunnelRadius) {
                return true;
            }
        }
        return false;
    }

    private PathHit closestRootHit(
            RootDescriptor root,
            int x,
            int y,
            int z
    ) {
        PathHit best = PathHit.miss();
        PathDescriptor[] paths = root.paths();

        for (int pathIndex = 0;
             pathIndex < paths.length;
             pathIndex++) {
            PathHit hit = closestRootPathHit(
                    root,
                    pathIndex,
                    x,
                    y,
                    z
            );

            if (hit.margin() > best.margin()) {
                best = hit;
            }
        }

        return best;
    }

    private static double buttressTopScale(double progress) {
        double p = clamp(progress, 0.0D, 1.0D);

        double remaining = Math.pow(
                1.0D - p,
                BUTTRESS_DECAY_EXPONENT
        );

        return lerp(
                BUTTRESS_TIP_TOP_SCALE,
                BUTTRESS_BASE_TOP_SCALE,
                remaining
        );
    }

    /**
     * Buttress roots begin as narrow vertical fins and gradually recover their
     * ordinary root width toward the outer half.
     */
    private static double buttressSideScale(double progress) {
        double p = smooth(
                clamp(progress, 0.0D, 1.0D)
        );

        return lerp(
                BUTTRESS_BASE_SIDE_SCALE,
                BUTTRESS_TIP_SIDE_SCALE,
                p
        );
    }

    private static double buttressRoundBlend(double visibleProgress) {
        return smooth(
                (visibleProgress - BUTTRESS_ROUND_BLEND_START)
                        / (BUTTRESS_ROUND_BLEND_END - BUTTRESS_ROUND_BLEND_START)
        );
    }

    private static ButtressProfile buttressProfile(
            PathDescriptor path,
            double radius,
            double visibleProgress
    ) {
        double baseHalfWidth = radius * buttressSideScale(visibleProgress);
        double topHeight = radius * buttressTopScale(visibleProgress);
        double bottomDepth = radius * BUTTRESS_BOTTOM_SCALE;
        double roundBlend = buttressRoundBlend(visibleProgress);
        double ridgeHalfWidth = Math.max(
                0.82D,
                baseHalfWidth * lerp(0.17D, 0.62D, roundBlend)
        );
        double capDepth = Math.min(
                topHeight * 0.22D,
                Math.max(0.82D, ridgeHalfWidth * 0.88D)
        );
        double ridgeLean = signedUnit(path.salt(), 802)
                * baseHalfWidth * 0.10D * (1.0D - roundBlend);
        return new ButtressProfile(
                baseHalfWidth,
                ridgeHalfWidth,
                topHeight,
                bottomDepth,
                capDepth,
                roundBlend,
                ridgeLean,
                radius
        );
    }

    /**
     * Base radius shared by root containment and conservative bound calculation.
     */
    private static double rootRadiusAtProgress(
            PathDescriptor path,
            double progress
    ) {
        double p = clamp(progress, 0.0D, 1.0D);

        double radius = lerp(
                path.startRadius(),
                path.endRadius(),
                smooth(p)
        );

        radius *= 1.0D
                + Math.sin(p * Math.PI) * 0.05D;

        return radius;
    }

    private PathHit closestRootPathHit(
            RootDescriptor root,
            int pathIndex,
            int x,
            int y,
            int z
    ) {
        PathDescriptor path =
                root.paths()[pathIndex];

        boolean mainButtress = pathIndex == 0;

        Point[] points = path.points();

        double px = x + 0.5D;
        double py = y + 0.5D;
        double pz = z + 0.5D;

        PathHit best = PathHit.miss();

        for (int i = 0;
             i < points.length - 1;
             i++) {
            Point a = points[i];
            Point b = points[i + 1];

            double abx = b.x() - a.x();
            double aby = b.y() - a.y();
            double abz = b.z() - a.z();

            double lengthSq =
                    abx * abx
                            + aby * aby
                            + abz * abz;

            if (lengthSq <= 0.0001D) {
                continue;
            }

            double segmentProgress = (
                    (px - a.x()) * abx
                            + (py - a.y()) * aby
                            + (pz - a.z()) * abz
            ) / lengthSq;

            segmentProgress = clamp(
                    segmentProgress,
                    0.0D,
                    1.0D
            );

            double closestX =
                    a.x() + abx * segmentProgress;

            double closestY =
                    a.y() + aby * segmentProgress;

            double closestZ =
                    a.z() + abz * segmentProgress;

            double arcLength = path.arcLengthAt(i, segmentProgress);
            double arcProgress = path.arcProgress(arcLength);
            double visibleProgress = mainButtress
                    ? clamp(
                    (arcLength - root.visibleStartArcLength())
                            / Math.max(
                            0.0001D,
                            path.totalLength() - root.visibleStartArcLength()
                    ),
                    0.0D,
                    1.0D
            )
                    : arcProgress;
            double progress = arcProgress;

            double radius = rootRadiusAtProgress(
                    path,
                    mainButtress ? visibleProgress : progress
            );

            radius *= 1.0D + signedUnit(
                    path.salt(),
                    floor(closestX / 5.0D),
                    floor(closestY / 4.0D),
                    floor(closestZ / 5.0D),
                    801
            ) * 0.055D;

            double dx = px - closestX;
            double dy = py - closestY;
            double dz = pz - closestZ;

            double margin;
            double sideOffset = 0.0D;
            double verticalOffset = dy;
            double topHeight;
            double bottomDepth;
            double baseHalfWidth;
            double tunnelSideHalfWidth;

            if (mainButtress) {
                /*
                 * Build a local coordinate frame around the current root segment:
                 *
                 * tangent  — direction along the root
                 * side     — horizontal direction perpendicular to the root
                 * vertical — upward direction perpendicular to the root
                 *
                 * The previous implementation scaled global Y only, leaving the
                 * side direction fully round. That produced overlapping domes.
                 */
                RootFrame frame = rootFrame(abx, aby, abz, dx, dy, dz);
                sideOffset = frame.sideOffset();
                verticalOffset = root.upper()
                        ? -frame.verticalOffset()
                        : frame.verticalOffset();
                ButtressProfile profile = buttressProfile(
                        path,
                        radius,
                        visibleProgress
                );
                margin = profile.margin(
                        sideOffset,
                        verticalOffset,
                        frame.alongOffset()
                );
                topHeight = profile.topHeight();
                bottomDepth = profile.bottomDepth();
                baseHalfWidth = profile.baseHalfWidth();
                double tunnelCenter = Math.min(1.35D, bottomDepth * 0.28D);
                tunnelSideHalfWidth = profile.halfWidthAt(tunnelCenter);
            } else {
                RootFrame frame = rootFrame(abx, aby, abz, dx, dy, dz);
                sideOffset = frame.sideOffset();
                verticalOffset = root.upper()
                        ? -frame.verticalOffset()
                        : frame.verticalOffset();
                topHeight = radius * LOWER_SECONDARY_TOP_SCALE;
                bottomDepth = radius * LOWER_SECONDARY_BOTTOM_SCALE;
                baseHalfWidth = radius;
                margin = roundedRootMargin(
                        sideOffset,
                        verticalOffset,
                        frame.alongOffset(),
                        radius,
                        topHeight,
                        bottomDepth
                );
                double tunnelCenter = Math.min(1.35D, bottomDepth * 0.28D);
                tunnelSideHalfWidth = ellipseHalfWidth(
                        radius,
                        topHeight,
                        tunnelCenter
                );
            }

            PathHit candidate = new PathHit(
                    margin,
                    radius,
                    progress,
                    visibleProgress,
                    sideOffset,
                    verticalOffset,
                    topHeight,
                    bottomDepth,
                    baseHalfWidth,
                    tunnelSideHalfWidth,
                    mainButtress
            );
            if (candidate.margin() > best.margin()) {
                best = candidate;
            }
        }

        return best;
    }

    private static double roundedRootMargin(
            double sideOffset,
            double verticalOffset,
            double alongOffset,
            double sideHalfWidth,
            double topHeight,
            double bottomDepth
    ) {
        double verticalExtent = verticalOffset >= 0.0D
                ? topHeight
                : bottomDepth;
        double normalized = Math.sqrt(
                square(sideOffset / sideHalfWidth)
                        + square(verticalOffset / verticalExtent)
                        + square(alongOffset / sideHalfWidth)
        );
        return (1.0D - normalized)
                * Math.min(sideHalfWidth, verticalExtent);
    }

    private static double ellipseHalfWidth(
            double baseHalfWidth,
            double topHeight,
            double verticalOffset
    ) {
        double normalized = clamp(verticalOffset / topHeight, 0.0D, 1.0D);
        return baseHalfWidth * Math.sqrt(Math.max(0.0D, 1.0D - normalized * normalized));
    }

    private static RootFrame rootFrame(
            double abx,
            double aby,
            double abz,
            double dx,
            double dy,
            double dz
    ) {
        double segmentLength = Math.sqrt(abx * abx + aby * aby + abz * abz);
        double horizontalLength = Math.sqrt(abx * abx + abz * abz);
        double tangentX = abx / segmentLength;
        double tangentY = aby / segmentLength;
        double tangentZ = abz / segmentLength;
        double sideX;
        double sideZ;
        double verticalX;
        double verticalY;
        double verticalZ;

        if (horizontalLength <= 0.0001D) {
            sideX = 1.0D;
            sideZ = 0.0D;
            verticalX = 0.0D;
            verticalY = 0.0D;
            verticalZ = tangentY >= 0.0D ? -1.0D : 1.0D;
        } else {
            sideX = -abz / horizontalLength;
            sideZ = abx / horizontalLength;
            verticalX = -abx * aby / (horizontalLength * segmentLength);
            verticalY = horizontalLength / segmentLength;
            verticalZ = -abz * aby / (horizontalLength * segmentLength);
        }

        return new RootFrame(
                tangentX,
                tangentY,
                tangentZ,
                sideX,
                0.0D,
                sideZ,
                verticalX,
                verticalY,
                verticalZ,
                dx * sideX + dz * sideZ,
                dx * verticalX + dy * verticalY + dz * verticalZ,
                dx * tangentX + dy * tangentY + dz * tangentZ
        );
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

        return new PathHit(
                bestRadius - bestDistance,
                bestRadius,
                bestProgress,
                bestProgress,
                0.0D,
                0.0D,
                bestRadius,
                bestRadius,
                bestRadius,
                bestRadius,
                false
        );
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

    private double findVisibleRootExitArc(PathDescriptor path) {
        Point[] points = path.points();
        if (trunkClearance(points[0]) >= 0.0D) {
            return 0.0D;
        }

        for (int segmentIndex = 0; segmentIndex < points.length - 1; segmentIndex++) {
            Point a = points[segmentIndex];
            Point b = points[segmentIndex + 1];
            double previousT = 0.0D;
            double previousClearance = trunkClearance(a);
            for (int sampleIndex = 1; sampleIndex <= 16; sampleIndex++) {
                double sampleT = sampleIndex / 16.0D;
                Point sample = lerpPoint(a, b, sampleT);
                double clearance = trunkClearance(sample);
                if (previousClearance < 0.0D && clearance >= 0.0D) {
                    double low = previousT;
                    double high = sampleT;
                    for (int iteration = 0; iteration < 24; iteration++) {
                        double middle = (low + high) * 0.5D;
                        if (trunkClearance(lerpPoint(a, b, middle)) >= 0.0D) {
                            high = middle;
                        } else {
                            low = middle;
                        }
                    }
                    return path.arcLengthAt(segmentIndex, high);
                }
                previousT = sampleT;
                previousClearance = clearance;
            }
        }

        return path.totalLength() * 0.25D;
    }

    private double trunkClearance(Point point) {
        int y = (int) Math.round(point.y());
        double dx = point.x() - centerXAtY(y);
        double dz = point.z() - centerZAtY(y);
        return Math.sqrt(dx * dx + dz * dz) - radiusAtY(y);
    }

    private static Point lerpPoint(Point a, Point b, double progress) {
        return new Point(
                lerp(a.x(), b.x(), progress),
                lerp(a.y(), b.y(), progress),
                lerp(a.z(), b.z(), progress)
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

    private BoundingBox computeRootBounds(
            PathDescriptor[] paths,
            double visibleStartArcLength,
            int padding
    ) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (int pathIndex = 0;
             pathIndex < paths.length;
             pathIndex++) {
            PathDescriptor path = paths[pathIndex];
            Point[] points = path.points();

            boolean mainButtress = pathIndex == 0;

            for (int segmentIndex = 0;
                 segmentIndex < points.length - 1;
                 segmentIndex++) {
                Point a = points[segmentIndex];
                Point b = points[segmentIndex + 1];
                double arcStart = path.arcLengthAt(segmentIndex, 0.0D);
                double arcEnd = path.arcLengthAt(segmentIndex, 1.0D);
                double progressStart = path.arcProgress(arcStart);
                double progressEnd = path.arcProgress(arcEnd);
                double visibleStart = mainButtress
                        ? clamp(
                        (arcStart - visibleStartArcLength)
                                / Math.max(0.0001D, path.totalLength() - visibleStartArcLength),
                        0.0D,
                        1.0D
                )
                        : progressStart;
                double visibleEnd = mainButtress
                        ? clamp(
                        (arcEnd - visibleStartArcLength)
                                / Math.max(0.0001D, path.totalLength() - visibleStartArcLength),
                        0.0D,
                        1.0D
                )
                        : progressEnd;
                double radiusProgressStart = mainButtress
                        ? visibleStart
                        : progressStart;
                double radiusProgressEnd = mainButtress
                        ? visibleEnd
                        : progressEnd;
                double baseRadiusStart = lerp(
                        path.startRadius(),
                        path.endRadius(),
                        smooth(radiusProgressStart)
                );
                double baseRadiusEnd = lerp(
                        path.startRadius(),
                        path.endRadius(),
                        smooth(radiusProgressEnd)
                );
                double radiusLimit = Math.max(baseRadiusStart, baseRadiusEnd)
                        * 1.05D * ROOT_RADIUS_NOISE_ALLOWANCE;
                double profileScale = mainButtress
                        ? Math.max(1.0D, buttressTopScale(Math.min(visibleStart, visibleEnd)))
                        : 1.0D;

                /*
                 * Expand every segment AABB by the segment's worst profile axis.
                 * Using the tall axis in X/Z is intentionally conservative and
                 * covers its projection when a descending path tilts local up.
                 */
                int inflate = ceil(radiusLimit * profileScale + padding);
                minX = Math.min(minX, floor(Math.min(a.x(), b.x())) - inflate);
                minY = Math.min(minY, floor(Math.min(a.y(), b.y())) - inflate);
                minZ = Math.min(minZ, floor(Math.min(a.z(), b.z())) - inflate);
                maxX = Math.max(maxX, ceil(Math.max(a.x(), b.x())) + inflate);
                maxY = Math.max(maxY, ceil(Math.max(a.y(), b.y())) + inflate);
                maxZ = Math.max(maxZ, ceil(Math.max(a.z(), b.z())) + inflate);
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

    private static double square(double value) {
        return value * value;
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
            double visibleStartArcLength,
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

    private static final class PathDescriptor {
        private final Point[] points;
        private final double startRadius;
        private final double endRadius;
        private final long salt;
        private final double[] cumulativeLengths;
        private final double totalLength;

        private PathDescriptor(
                Point[] points,
                double startRadius,
                double endRadius,
                long salt
        ) {
            this.points = points;
            this.startRadius = startRadius;
            this.endRadius = endRadius;
            this.salt = salt;
            this.cumulativeLengths = new double[points.length];
            double length = 0.0D;
            for (int i = 0; i < points.length - 1; i++) {
                Point a = points[i];
                Point b = points[i + 1];
                length += Math.sqrt(
                        square(b.x() - a.x())
                                + square(b.y() - a.y())
                                + square(b.z() - a.z())
                );
                this.cumulativeLengths[i + 1] = length;
            }
            this.totalLength = length;
        }

        private Point[] points() {
            return this.points;
        }

        private double startRadius() {
            return this.startRadius;
        }

        private double endRadius() {
            return this.endRadius;
        }

        private long salt() {
            return this.salt;
        }

        private double totalLength() {
            return this.totalLength;
        }

        private double arcLengthAt(int segmentIndex, double segmentProgress) {
            double segmentLength = this.cumulativeLengths[segmentIndex + 1]
                    - this.cumulativeLengths[segmentIndex];
            return this.cumulativeLengths[segmentIndex]
                    + segmentLength * clamp(segmentProgress, 0.0D, 1.0D);
        }

        private double arcProgress(double arcLength) {
            if (this.totalLength <= 0.0001D) {
                return 0.0D;
            }
            return clamp(arcLength / this.totalLength, 0.0D, 1.0D);
        }

        private PathLocation locationAtArc(double arcLength) {
            double target = clamp(arcLength, 0.0D, this.totalLength);
            int segmentIndex = this.points.length - 2;
            for (int i = 0; i < this.points.length - 1; i++) {
                if (target <= this.cumulativeLengths[i + 1]) {
                    segmentIndex = i;
                    break;
                }
            }
            double segmentLength = this.cumulativeLengths[segmentIndex + 1]
                    - this.cumulativeLengths[segmentIndex];
            double segmentProgress = segmentLength <= 0.0001D
                    ? 0.0D
                    : (target - this.cumulativeLengths[segmentIndex]) / segmentLength;
            Point a = this.points[segmentIndex];
            Point b = this.points[segmentIndex + 1];
            RootFrame frame = rootFrame(
                    b.x() - a.x(),
                    b.y() - a.y(),
                    b.z() - a.z(),
                    0.0D,
                    0.0D,
                    0.0D
            );
            return new PathLocation(
                    lerpPoint(a, b, segmentProgress),
                    frame,
                    arcProgress(target)
            );
        }
    }

    private record Point(
            double x,
            double y,
            double z
    ) {
    }

    private record RootFrame(
            double tangentX,
            double tangentY,
            double tangentZ,
            double sideX,
            double sideY,
            double sideZ,
            double verticalX,
            double verticalY,
            double verticalZ,
            double sideOffset,
            double verticalOffset,
            double alongOffset
    ) {
    }

    private record PathLocation(
            Point point,
            RootFrame frame,
            double arcProgress
    ) {
    }

    static record RootTopology(
            int pathCount,
            int mainPointCount,
            double startBoundaryDepth,
            double endBoundaryDepth,
            double outwardVerticalTravel,
            double startRadius,
            double endRadius
    ) {
    }

    static record RootCrossSection(
            double centerX,
            double centerY,
            double centerZ,
            double tangentX,
            double tangentY,
            double tangentZ,
            double sideX,
            double sideY,
            double sideZ,
            double verticalX,
            double verticalY,
            double verticalZ,
            double radius,
            double topHeight,
            double bottomDepth,
            double baseHalfWidth,
            double ridgeHalfWidth,
            double capDepth,
            double roundBlend,
            double ridgeLean,
            double progress,
            double arcProgress,
            boolean buttress
    ) {
        private static RootCrossSection buttress(
                PathLocation location,
                double radius,
                ButtressProfile profile,
                double visibleProgress,
                boolean upper
        ) {
            return create(
                    location,
                    radius,
                    profile.topHeight(),
                    profile.bottomDepth(),
                    profile.baseHalfWidth(),
                    profile.ridgeHalfWidth(),
                    profile.capDepth(),
                    profile.roundBlend(),
                    profile.ridgeLean(),
                    visibleProgress,
                    true,
                    upper
            );
        }

        private static RootCrossSection rounded(
                PathLocation location,
                double radius,
                double topHeight,
                double bottomDepth,
                double progress,
                boolean upper
        ) {
            return create(
                    location,
                    radius,
                    topHeight,
                    bottomDepth,
                    radius,
                    0.0D,
                    0.0D,
                    1.0D,
                    0.0D,
                    progress,
                    false,
                    upper
            );
        }

        private static RootCrossSection create(
                PathLocation location,
                double radius,
                double topHeight,
                double bottomDepth,
                double baseHalfWidth,
                double ridgeHalfWidth,
                double capDepth,
                double roundBlend,
                double ridgeLean,
                double progress,
                boolean buttress,
                boolean mirrorVertical
        ) {
            Point point = location.point();
            RootFrame frame = location.frame();
            double verticalSign = mirrorVertical ? -1.0D : 1.0D;
            return new RootCrossSection(
                    point.x(),
                    point.y(),
                    point.z(),
                    frame.tangentX(),
                    frame.tangentY(),
                    frame.tangentZ(),
                    frame.sideX(),
                    frame.sideY(),
                    frame.sideZ(),
                    frame.verticalX() * verticalSign,
                    frame.verticalY() * verticalSign,
                    frame.verticalZ() * verticalSign,
                    radius,
                    topHeight,
                    bottomDepth,
                    baseHalfWidth,
                    ridgeHalfWidth,
                    capDepth,
                    roundBlend,
                    ridgeLean,
                    progress,
                    location.arcProgress(),
                    buttress
            );
        }

        double halfWidthAt(double verticalOffset) {
            if (!this.buttress) {
                return ellipseHalfWidth(
                        this.baseHalfWidth,
                        this.topHeight,
                        verticalOffset
                );
            }
            return new ButtressProfile(
                    this.baseHalfWidth,
                    this.ridgeHalfWidth,
                    this.topHeight,
                    this.bottomDepth,
                    this.capDepth,
                    this.roundBlend,
                    this.ridgeLean,
                    this.radius
            ).halfWidthAt(verticalOffset);
        }
    }

    private record ButtressProfile(
            double baseHalfWidth,
            double ridgeHalfWidth,
            double topHeight,
            double bottomDepth,
            double capDepth,
            double roundBlend,
            double ridgeLean,
            double alongHalfLength
    ) {
        private double halfWidthAt(double verticalOffset) {
            if (verticalOffset < 0.0D || verticalOffset > this.topHeight) {
                return 0.0D;
            }
            double bodyHeight = Math.max(0.0001D, this.topHeight - this.capDepth);
            double wedgeWidth;
            if (verticalOffset <= bodyHeight) {
                double normalized = clamp(verticalOffset / bodyHeight, 0.0D, 1.0D);
                wedgeWidth = lerp(
                        this.baseHalfWidth,
                        this.ridgeHalfWidth,
                        Math.pow(smooth(normalized), 0.72D)
                );
            } else {
                double capProgress = clamp(
                        (verticalOffset - bodyHeight) / this.capDepth,
                        0.0D,
                        1.0D
                );
                wedgeWidth = this.ridgeHalfWidth
                        * Math.sqrt(Math.max(0.0D, 1.0D - capProgress * capProgress));
            }
            double ellipseWidth = ellipseHalfWidth(
                    this.baseHalfWidth,
                    this.topHeight,
                    verticalOffset
            );
            return lerp(wedgeWidth, ellipseWidth, this.roundBlend);
        }

        private double sideCenterAt(double verticalOffset) {
            double normalized = clamp(verticalOffset / this.topHeight, 0.0D, 1.0D);
            return this.ridgeLean * Math.sin(normalized * Math.PI * 0.92D);
        }

        private double margin(
                double sideOffset,
                double verticalOffset,
                double alongOffset
        ) {
            double alongNormalized = Math.abs(alongOffset) / this.alongHalfLength;
            if (alongNormalized >= 1.0D) {
                return (1.0D - alongNormalized) * this.alongHalfLength;
            }
            double capScale = Math.sqrt(Math.max(
                    0.0D,
                    1.0D - alongNormalized * alongNormalized
            ));
            double alongMargin = (1.0D - alongNormalized) * this.alongHalfLength;

            if (verticalOffset >= 0.0D) {
                double unscaledVertical = verticalOffset / Math.max(0.0001D, capScale);
                double allowedHalfWidth = halfWidthAt(unscaledVertical) * capScale;
                double sideCenter = sideCenterAt(unscaledVertical) * capScale;
                double sideMargin = allowedHalfWidth - Math.abs(sideOffset - sideCenter);
                double topMargin = this.topHeight * capScale - verticalOffset;
                return Math.min(sideMargin, Math.min(topMargin, alongMargin));
            }

            double base = this.baseHalfWidth * capScale;
            double bottom = this.bottomDepth * capScale;
            double normalized = Math.sqrt(
                    square(sideOffset / Math.max(0.0001D, base))
                            + square(verticalOffset / Math.max(0.0001D, bottom))
            );
            return Math.min(
                    (1.0D - normalized) * Math.min(base, bottom),
                    alongMargin
            );
        }
    }

    private record PathHit(
            double margin,
            double radius,
            double progress,
            double visibleProgress,
            double sideOffset,
            double verticalOffset,
            double topHeight,
            double bottomDepth,
            double baseHalfWidth,
            double tunnelSideHalfWidth,
            boolean mainButtress
    ) {
        static PathHit miss() {
            return new PathHit(
                    -Double.MAX_VALUE,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D,
                    false
            );
        }

        boolean inside() {
            return this.margin >= 0.0D;
        }
    }
}
