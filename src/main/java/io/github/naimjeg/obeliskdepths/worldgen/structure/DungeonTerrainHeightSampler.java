package io.github.naimjeg.obeliskdepths.worldgen.structure;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;

public final class DungeonTerrainHeightSampler {
    public static final int SAMPLE_RADIUS_BLOCKS = 32;
    public static final int SAMPLE_GRID_SIZE = 3;
    public static final int SURFACE_OFFSET = 1;

    private DungeonTerrainHeightSampler() {
    }

    public static Result sample(
            Structure.GenerationContext context,
            BlockPos center,
            BoundingBox plannedBounds
    ) {
        List<HeightSample> samples = new ArrayList<>(
                SAMPLE_GRID_SIZE * SAMPLE_GRID_SIZE
        );
        int half = SAMPLE_GRID_SIZE / 2;

        for (int gridX = -half; gridX <= half; gridX++) {
            for (int gridZ = -half; gridZ <= half; gridZ++) {
                int x = center.getX() + gridX * SAMPLE_RADIUS_BLOCKS;
                int z = center.getZ() + gridZ * SAMPLE_RADIUS_BLOCKS;
                int height = context.chunkGenerator().getFirstOccupiedHeight(
                        x,
                        z,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        context.heightAccessor(),
                        context.randomState()
                );
                samples.add(new HeightSample(x, z, height));
            }
        }

        return calculate(samples, plannedBounds, context.heightAccessor());
    }

    public static Result calculate(
            List<HeightSample> samples,
            BoundingBox plannedBounds,
            LevelHeightAccessor heightAccessor
    ) {
        if (samples == null || samples.size() != SAMPLE_GRID_SIZE * SAMPLE_GRID_SIZE) {
            throw new IllegalArgumentException(
                    "Dungeon terrain sampler requires exactly "
                            + (SAMPLE_GRID_SIZE * SAMPLE_GRID_SIZE)
                            + " height samples"
            );
        }
        if (plannedBounds == null || heightAccessor == null) {
            throw new IllegalArgumentException(
                    "Dungeon terrain sampler requires bounds and height accessor"
            );
        }

        List<Integer> heights = samples.stream()
                .map(HeightSample::height)
                .sorted()
                .toList();
        int medianHeight = heights.get(heights.size() / 2);
        int requestedBaseY = Math.addExact(medianHeight, SURFACE_OFFSET);
        int baseY = clampBaseY(requestedBaseY, plannedBounds, heightAccessor);
        int verticalOffset = Math.subtractExact(baseY, plannedBounds.minY());
        BoundingBox translatedBounds = translateY(plannedBounds, verticalOffset);

        return new Result(
                samples,
                medianHeight,
                requestedBaseY,
                baseY,
                verticalOffset,
                plannedBounds,
                translatedBounds
        );
    }

    public static int clampBaseY(
            int requestedBaseY,
            BoundingBox plannedBounds,
            LevelHeightAccessor heightAccessor
    ) {
        int verticalSpan = Math.subtractExact(
                plannedBounds.maxY(),
                plannedBounds.minY()
        );
        int minBaseY = heightAccessor.getMinY();
        int maxBaseY = Math.subtractExact(
                heightAccessor.getMaxY(),
                verticalSpan
        );
        if (maxBaseY < minBaseY) {
            throw new IllegalArgumentException(
                    "Dungeon vertical bounds exceed dimension build height: "
                            + "planned="
                            + plannedBounds
                            + " dimensionMinY="
                            + heightAccessor.getMinY()
                            + " dimensionMaxY="
                            + heightAccessor.getMaxY()
            );
        }

        return Math.max(minBaseY, Math.min(maxBaseY, requestedBaseY));
    }

    public static String describeSamples(List<HeightSample> samples) {
        if (samples == null || samples.isEmpty()) {
            return "[]";
        }
        List<HeightSample> ordered = samples.stream()
                .sorted(Comparator
                        .comparingInt(HeightSample::x)
                        .thenComparingInt(HeightSample::z))
                .toList();
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < ordered.size(); index++) {
            HeightSample sample = ordered.get(index);
            if (index > 0) {
                builder.append(';');
            }
            builder.append(sample.x())
                    .append(',')
                    .append(sample.z())
                    .append('=')
                    .append(sample.height());
        }
        return builder.append(']').toString();
    }

    private static BoundingBox translateY(
            BoundingBox bounds,
            int offset
    ) {
        return new BoundingBox(
                bounds.minX(),
                Math.addExact(bounds.minY(), offset),
                bounds.minZ(),
                bounds.maxX(),
                Math.addExact(bounds.maxY(), offset),
                bounds.maxZ()
        );
    }

    public record HeightSample(
            int x,
            int z,
            int height
    ) {
    }

    public record Result(
            List<HeightSample> samples,
            int medianHeight,
            int requestedBaseY,
            int baseY,
            int verticalOffset,
            BoundingBox plannedBounds,
            BoundingBox translatedBounds
    ) {
        public Result {
            samples = samples == null ? List.of() : List.copyOf(samples);
        }
    }
}
