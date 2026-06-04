package io.github.naimjeg.obeliskdepths.worldgen.structure.tree;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable local-coordinate description of one Amphixylon.
 *
 * <p>The canonical half is generated once above local {@code Y = 0}. The
 * opposite half is derived only through {@code (x, y, z) -> (x, -y, z)}.
 * Material roles are deliberately absent from this model.</p>
 */
public final class AmphixylonGeometry {
    public static final int HORIZONTAL_RADIUS_BUDGET = 58;
    public static final int MAX_HORIZONTAL_SPAN = HORIZONTAL_RADIUS_BUDGET * 2 + 1;
    public static final int MIN_ROOT_DEPTH = 45;
    public static final int MAX_ROOT_DEPTH = 49;
    public static final int MAX_ROOT_OCCUPIED_RADIUS = 55;
    public static final int MAX_ROOT_OCCUPIED_DEPTH = 55;
    public static final int MAX_ROOT_BLOCK_BUDGET = 70_000;
    public static final double MIN_ROOT_TIP_RADIUS = 2.8D;
    public static final double MAX_ROOT_TIP_RADIUS = 4.9D;
    public static final double MIN_LEAF_HORIZONTAL_RADIUS = 10.0D;
    public static final double MAX_LEAF_HORIZONTAL_RADIUS = 13.0D;
    public static final double MIN_LEAF_VERTICAL_RADIUS = 5.0D;
    public static final double MAX_LEAF_VERTICAL_RADIUS = 7.5D;

    private static final double TWO_PI = Math.PI * 2.0D;
    private static final int ARM_COUNT = 8;
    private static final int LEAF_UNITS_PER_ARM = 3;
    private static final int MIN_PATH_SAMPLE_COUNT = 69;
    private static final int PATH_SAMPLE_VARIATION = 7;
    private static final int TRUNK_HALF_HEIGHT = 50;

    private final AmphixylonSite site;
    private final List<TrunkSlice> trunkSlices;
    private final HalfGeometry canonicalHalf;
    private final HalfGeometry mirroredHalf;
    private final int canonicalRandomSampleCount;

    private AmphixylonGeometry(
            AmphixylonSite site,
            List<TrunkSlice> trunkSlices,
            HalfGeometry canonicalHalf,
            HalfGeometry mirroredHalf,
            int canonicalRandomSampleCount
    ) {
        this.site = site;
        this.trunkSlices = List.copyOf(trunkSlices);
        this.canonicalHalf = canonicalHalf;
        this.mirroredHalf = mirroredHalf;
        this.canonicalRandomSampleCount = canonicalRandomSampleCount;
    }

    public static AmphixylonGeometry create(AmphixylonSite site) {
        CanonicalRandom random = new CanonicalRandom(site.treeSeed());
        List<TrunkSlice> trunk = createTrunk(random);
        HalfGeometry canonical = createCanonicalHalf(site, trunk, random);
        int samplesBeforeReflection = random.sampleCount();
        HalfGeometry mirrored = canonical.mirror();
        if (random.sampleCount() != samplesBeforeReflection) {
            throw new IllegalStateException("Amphixylon reflection consumed random samples");
        }
        return new AmphixylonGeometry(site, trunk, canonical, mirrored, samplesBeforeReflection);
    }

    public AmphixylonSite site() {
        return this.site;
    }

    public int symmetryY() {
        return this.site.symmetryY();
    }

    public List<TrunkSlice> trunkSlices() {
        return this.trunkSlices;
    }

    public HalfGeometry canonicalHalf() {
        return this.canonicalHalf;
    }

    public HalfGeometry mirroredHalf() {
        return this.mirroredHalf;
    }

    public int canonicalRandomSampleCount() {
        return this.canonicalRandomSampleCount;
    }

    public int mirroredRandomSampleCount() {
        return 0;
    }

    public boolean isExactlyReflected() {
        if (this.canonicalHalf.paths().size() != this.mirroredHalf.paths().size()
                || this.canonicalHalf.leafUnits().size() != this.mirroredHalf.leafUnits().size()) {
            return false;
        }
        for (int i = 0; i < this.canonicalHalf.paths().size(); i++) {
            if (!this.canonicalHalf.paths().get(i).isReflectionOf(this.mirroredHalf.paths().get(i))) {
                return false;
            }
        }
        for (int i = 0; i < this.canonicalHalf.leafUnits().size(); i++) {
            if (!this.canonicalHalf.leafUnits().get(i).isReflectionOf(this.mirroredHalf.leafUnits().get(i))) {
                return false;
            }
        }
        return true;
    }

    private static List<TrunkSlice> createTrunk(CanonicalRandom random) {
        double phaseX = random.nextDouble() * TWO_PI;
        double phaseZ = random.nextDouble() * TWO_PI;
        double amplitudeX = 2.5D + random.nextDouble() * 3.5D;
        double amplitudeZ = 2.5D + random.nextDouble() * 3.5D;
        double waistRadius = 8.0D + random.nextDouble() * 8.0D;
        double crownRadius = 24.0D + random.nextDouble() * 8.0D;
        double irregularityPhase = random.nextDouble() * TWO_PI;
        List<TrunkSlice> canonicalSlices = new ArrayList<>(TRUNK_HALF_HEIGHT + 1);
        for (int localY = 0; localY <= TRUNK_HALF_HEIGHT; localY++) {
            double t = localY / (double) TRUNK_HALF_HEIGHT;
            double centerScale = smooth(t);
            double centerX = (Math.sin(localY * 0.081D + phaseX) - Math.sin(phaseX))
                    * amplitudeX * centerScale;
            double centerZ = (Math.cos(localY * 0.073D + phaseZ) - Math.cos(phaseZ))
                    * amplitudeZ * centerScale;
            double radius = lerp(waistRadius, crownRadius, smooth(t));
            radius += Math.sin(smooth((t - 0.58D) / 0.32D) * Math.PI) * 5.0D;
            radius += smooth((t - 0.82D) / 0.18D) * 5.0D;
            canonicalSlices.add(new TrunkSlice(localY, centerX, centerZ, radius, irregularityPhase));
        }
        List<TrunkSlice> slices = new ArrayList<>(TRUNK_HALF_HEIGHT * 2 + 1);
        for (int index = canonicalSlices.size() - 1; index >= 1; index--) {
            slices.add(canonicalSlices.get(index).mirror());
        }
        slices.addAll(canonicalSlices);
        return slices;
    }

    private static HalfGeometry createCanonicalHalf(
            AmphixylonSite site,
            List<TrunkSlice> trunkSlices,
            CanonicalRandom random
    ) {
        double angularPhase = random.nextDouble() * TWO_PI;
        List<TaperedPath> paths = new ArrayList<>(ARM_COUNT);
        List<LeafUnit> leaves = new ArrayList<>(ARM_COUNT * LEAF_UNITS_PER_ARM);

        for (int index = 0; index < ARM_COUNT; index++) {
            double baseAngle = angularPhase + index * TWO_PI / ARM_COUNT;
            double angle = baseAngle + random.nextSignedDouble() * 0.18D;
            double bend = random.nextSignedDouble() * 0.25D;
            double horizontalLength = site.horizontalReach() * (0.72D + random.nextDouble() * 0.14D);
            double verticalLength = MIN_ROOT_DEPTH
                    + random.nextDouble() * (MAX_ROOT_DEPTH - MIN_ROOT_DEPTH);
            int attachmentY = 18 + random.nextInt(9);
            TrunkSlice attachmentSlice = trunkSlices.get(TRUNK_HALF_HEIGHT + attachmentY);
            double startDistance = attachmentSlice.radius() * 0.62D;
            double startRadius = 6.0D + random.nextDouble() * 4.0D;
            double tipRadius = MIN_ROOT_TIP_RADIUS
                    + random.nextDouble() * (MAX_ROOT_TIP_RADIUS - MIN_ROOT_TIP_RADIUS);
            int sampleCount = MIN_PATH_SAMPLE_COUNT + random.nextInt(PATH_SAMPLE_VARIATION);

            List<LocalPoint> controls = List.of(
                    radialPoint(
                            angle,
                            startDistance,
                            attachmentY,
                            attachmentSlice.centerX(),
                            attachmentSlice.centerZ()
                    ),
                    radialPoint(angle + bend * 0.45D, horizontalLength * 0.35D, attachmentY + 4.0D),
                    radialPoint(angle + bend * 0.82D, horizontalLength * 0.72D, verticalLength - 5.0D),
                    radialPoint(angle + bend, horizontalLength, verticalLength)
            );
            List<PathSample> samples = sampleBezier(controls, sampleCount, startRadius, tipRadius);
            TaperedPath path = new TaperedPath(
                    index,
                    angle,
                    horizontalLength,
                    startRadius,
                    tipRadius,
                    controls,
                    samples
            );
            paths.add(path);

            for (int unitIndex = 0; unitIndex < LEAF_UNITS_PER_ARM; unitIndex++) {
                double progress = unitIndex == 0 ? 0.56D : unitIndex == 1 ? 0.78D : 0.96D;
                int attachmentIndex = Math.min(
                        samples.size() - 1,
                        (int) Math.round(progress * (samples.size() - 1))
                );
                LocalPoint attachment = samples.get(attachmentIndex).point();
                double side = unitIndex - 1.0D;
                double tangentialDistance = side * (2.0D + random.nextDouble() * 3.0D);
                double centerX = attachment.x() + Math.cos(angle + Math.PI / 2.0D) * tangentialDistance;
                double centerY = Math.min(
                        TRUNK_HALF_HEIGHT + 2.0D,
                        attachment.y() + random.nextSignedDouble() * 4.0D
                );
                double centerZ = attachment.z() + Math.sin(angle + Math.PI / 2.0D) * tangentialDistance;
                double radiusX = MIN_LEAF_HORIZONTAL_RADIUS
                        + random.nextDouble() * (MAX_LEAF_HORIZONTAL_RADIUS - MIN_LEAF_HORIZONTAL_RADIUS);
                double radiusY = MIN_LEAF_VERTICAL_RADIUS
                        + random.nextDouble() * (MAX_LEAF_VERTICAL_RADIUS - MIN_LEAF_VERTICAL_RADIUS);
                double radiusZ = radiusX;
                int vineLength = random.nextDouble() < 0.45D ? 5 + random.nextInt(14) : 0;
                List<LocalBlock> mask = createLeafMask(radiusX, radiusY, radiusZ, random);
                leaves.add(new LeafUnit(
                        index * LEAF_UNITS_PER_ARM + unitIndex,
                        index,
                        attachmentIndex,
                        new LocalPoint(centerX, centerY, centerZ),
                        radiusX,
                        radiusY,
                        radiusZ,
                        vineLength,
                        mask
                ));
            }
        }
        return new HalfGeometry(paths, leaves);
    }

    private static List<PathSample> sampleBezier(
            List<LocalPoint> controls,
            int sampleCount,
            double startRadius,
            double tipRadius
    ) {
        List<PathSample> samples = new ArrayList<>(sampleCount);
        for (int index = 0; index < sampleCount; index++) {
            double t = index / (double) Math.max(1, sampleCount - 1);
            double inverse = 1.0D - t;
            double a = inverse * inverse * inverse;
            double b = 3.0D * inverse * inverse * t;
            double c = 3.0D * inverse * t * t;
            double d = t * t * t;
            LocalPoint point = new LocalPoint(
                    controls.get(0).x() * a + controls.get(1).x() * b
                            + controls.get(2).x() * c + controls.get(3).x() * d,
                    controls.get(0).y() * a + controls.get(1).y() * b
                            + controls.get(2).y() * c + controls.get(3).y() * d,
                    controls.get(0).z() * a + controls.get(1).z() * b
                            + controls.get(2).z() * c + controls.get(3).z() * d
            );
            double radius = tipRadius
                    + (startRadius - tipRadius) * Math.pow(1.0D - t, 1.35D);
            samples.add(new PathSample(point, t, radius));
        }
        return samples;
    }

    private static List<LocalBlock> createLeafMask(
            double radiusX,
            double radiusY,
            double radiusZ,
            CanonicalRandom random
    ) {
        List<LocalBlock> mask = new ArrayList<>();
        int maxX = (int) Math.ceil(radiusX);
        int maxY = (int) Math.ceil(radiusY);
        int maxZ = (int) Math.ceil(radiusZ);
        for (int y = -maxY; y <= maxY; y++) {
            for (int x = -maxX; x <= maxX; x++) {
                for (int z = -maxZ; z <= maxZ; z++) {
                    double nx = x / radiusX;
                    double ny = y / radiusY;
                    double nz = z / radiusZ;
                    double distance = nx * nx + ny * ny + nz * nz;
                    if (distance <= 0.72D
                            || (distance <= 1.0D && random.nextDouble() >= 0.30D + distance * 0.24D)) {
                        mask.add(new LocalBlock(x, y, z));
                    }
                }
            }
        }
        return mask;
    }

    private static LocalPoint radialPoint(double angle, double distance, double y) {
        return new LocalPoint(Math.cos(angle) * distance, y, Math.sin(angle) * distance);
    }

    private static LocalPoint radialPoint(
            double angle,
            double distance,
            double y,
            double centerX,
            double centerZ
    ) {
        return new LocalPoint(
                centerX + Math.cos(angle) * distance,
                y,
                centerZ + Math.sin(angle) * distance
        );
    }

    private static double lerp(double start, double end, double progress) {
        return start + (end - start) * progress;
    }

    private static double smooth(double value) {
        double x = Math.max(0.0D, Math.min(1.0D, value));
        return x * x * (3.0D - 2.0D * x);
    }

    public record HalfGeometry(
            List<TaperedPath> paths,
            List<LeafUnit> leafUnits
    ) {
        public HalfGeometry {
            paths = List.copyOf(paths);
            leafUnits = List.copyOf(leafUnits);
        }

        HalfGeometry mirror() {
            List<TaperedPath> mirroredPaths = new ArrayList<>(this.paths.size());
            for (TaperedPath path : this.paths) {
                mirroredPaths.add(path.mirror());
            }
            List<LeafUnit> mirroredLeaves = new ArrayList<>(this.leafUnits.size());
            for (LeafUnit leaf : this.leafUnits) {
                mirroredLeaves.add(leaf.mirror());
            }
            return new HalfGeometry(mirroredPaths, mirroredLeaves);
        }
    }

    public record TaperedPath(
            int index,
            double radialAngle,
            double horizontalLength,
            double startRadius,
            double tipRadius,
            List<LocalPoint> controlPoints,
            List<PathSample> samples
    ) {
        public TaperedPath {
            controlPoints = List.copyOf(controlPoints);
            samples = List.copyOf(samples);
        }

        TaperedPath mirror() {
            List<LocalPoint> mirroredControls = new ArrayList<>(this.controlPoints.size());
            for (LocalPoint point : this.controlPoints) {
                mirroredControls.add(point.mirror());
            }
            List<PathSample> mirroredSamples = new ArrayList<>(this.samples.size());
            for (PathSample sample : this.samples) {
                mirroredSamples.add(sample.mirror());
            }
            return new TaperedPath(
                    this.index,
                    this.radialAngle,
                    this.horizontalLength,
                    this.startRadius,
                    this.tipRadius,
                    mirroredControls,
                    mirroredSamples
            );
        }

        boolean isReflectionOf(TaperedPath other) {
            if (this.index != other.index
                    || Double.compare(this.radialAngle, other.radialAngle) != 0
                    || Double.compare(this.horizontalLength, other.horizontalLength) != 0
                    || Double.compare(this.startRadius, other.startRadius) != 0
                    || Double.compare(this.tipRadius, other.tipRadius) != 0
                    || this.controlPoints.size() != other.controlPoints.size()
                    || this.samples.size() != other.samples.size()) {
                return false;
            }
            for (int i = 0; i < this.controlPoints.size(); i++) {
                if (!this.controlPoints.get(i).isReflectionOf(other.controlPoints.get(i))) {
                    return false;
                }
            }
            for (int i = 0; i < this.samples.size(); i++) {
                PathSample first = this.samples.get(i);
                PathSample second = other.samples.get(i);
                if (!first.point().isReflectionOf(second.point())
                        || Double.compare(first.t(), second.t()) != 0
                        || Double.compare(first.radius(), second.radius()) != 0) {
                    return false;
                }
            }
            return true;
        }
    }

    public record PathSample(
            LocalPoint point,
            double t,
            double radius
    ) {
        PathSample mirror() {
            return new PathSample(this.point.mirror(), this.t, this.radius);
        }
    }

    public record LeafUnit(
            int index,
            int attachmentPathIndex,
            int attachmentSampleIndex,
            LocalPoint center,
            double radiusX,
            double radiusY,
            double radiusZ,
            int vineLength,
            List<LocalBlock> maskOffsets
    ) {
        public LeafUnit {
            maskOffsets = List.copyOf(maskOffsets);
        }

        LeafUnit mirror() {
            List<LocalBlock> mirroredMask = new ArrayList<>(this.maskOffsets.size());
            for (LocalBlock block : this.maskOffsets) {
                mirroredMask.add(block.mirror());
            }
            return new LeafUnit(
                    this.index,
                    this.attachmentPathIndex,
                    this.attachmentSampleIndex,
                    this.center.mirror(),
                    this.radiusX,
                    this.radiusY,
                    this.radiusZ,
                    this.vineLength,
                    mirroredMask
            );
        }

        boolean isReflectionOf(LeafUnit other) {
            if (this.index != other.index
                    || this.attachmentPathIndex != other.attachmentPathIndex
                    || this.attachmentSampleIndex != other.attachmentSampleIndex
                    || !this.center.isReflectionOf(other.center)
                    || Double.compare(this.radiusX, other.radiusX) != 0
                    || Double.compare(this.radiusY, other.radiusY) != 0
                    || Double.compare(this.radiusZ, other.radiusZ) != 0
                    || this.vineLength != other.vineLength
                    || this.maskOffsets.size() != other.maskOffsets.size()) {
                return false;
            }
            for (int i = 0; i < this.maskOffsets.size(); i++) {
                if (!this.maskOffsets.get(i).isReflectionOf(other.maskOffsets.get(i))) {
                    return false;
                }
            }
            return true;
        }
    }

    public record TrunkSlice(
            int localY,
            double centerX,
            double centerZ,
            double radius,
            double irregularityPhase
    ) {
        TrunkSlice mirror() {
            return new TrunkSlice(
                    -this.localY,
                    this.centerX,
                    this.centerZ,
                    this.radius,
                    this.irregularityPhase
            );
        }
    }

    public record LocalPoint(
            double x,
            double y,
            double z
    ) {
        LocalPoint mirror() {
            return new LocalPoint(this.x, this.y == 0.0D ? 0.0D : -this.y, this.z);
        }

        boolean isReflectionOf(LocalPoint other) {
            return Double.compare(this.x, other.x) == 0
                    && this.y == -other.y
                    && Double.compare(this.z, other.z) == 0;
        }
    }

    public record LocalBlock(
            int x,
            int y,
            int z
    ) {
        LocalBlock mirror() {
            return new LocalBlock(this.x, -this.y, this.z);
        }

        boolean isReflectionOf(LocalBlock other) {
            return this.x == other.x && this.y == -other.y && this.z == other.z;
        }
    }

    private static final class CanonicalRandom {
        private long state;
        private int sampleCount;

        private CanonicalRandom(long seed) {
            this.state = seed;
        }

        private double nextDouble() {
            this.state += 0x9E3779B97F4A7C15L;
            long value = this.state;
            value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
            value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
            value ^= value >>> 31;
            this.sampleCount++;
            return (double) (value >>> 11) * 0x1.0p-53D;
        }

        private double nextSignedDouble() {
            return this.nextDouble() * 2.0D - 1.0D;
        }

        private int nextInt(int bound) {
            return Math.min(bound - 1, (int) Math.floor(this.nextDouble() * bound));
        }

        private int sampleCount() {
            return this.sampleCount;
        }
    }
}
