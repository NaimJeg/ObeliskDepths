//package io.github.naimjeg.obeliskdepths.worldgen.structure.placement;
//
//import io.github.naimjeg.obeliskdepths.worldgen.structure.generation.DungeonGenerationPlan;
//import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonLayoutGenerationException;
//import net.minecraft.world.level.ChunkPos;
//import net.minecraft.world.level.LevelHeightAccessor;
//import net.minecraft.world.level.levelgen.structure.BoundingBox;
//
//public final class DungeonSiteEnvelopeValidator {
//    public static final int MAX_SITE_RADIUS_BLOCKS = 256;
//    public static final int MAX_SITE_VERTICAL_SPAN_BLOCKS = 96;
//    public static final int BUILD_HEIGHT_MARGIN_BLOCKS = 8;
//    public static final long MAX_SITE_FILL_VOLUME_BLOCKS = 24_000_000L;
//
//    private DungeonSiteEnvelopeValidator() {
//    }
//
//    public static Report validate(
//            DungeonGenerationPlan plan,
//            ChunkPos startChunk,
//            LevelHeightAccessor heightAccessor
//    ) {
//        if (startChunk == null) {
//            throw new IllegalArgumentException("Dungeon envelope validation requires start chunk");
//        }
//        return validate(
//                plan,
//                startChunk.getMiddleBlockX(),
//                startChunk.getMiddleBlockZ(),
//                heightAccessor
//        );
//    }
//
//    public static Report validate(
//            DungeonGenerationPlan plan,
//            int centerX,
//            int centerZ,
//            LevelHeightAccessor heightAccessor
//    ) {
//        if (plan == null || heightAccessor == null) {
//            throw new IllegalArgumentException(
//                    "Dungeon envelope validation requires plan and height accessor"
//            );
//        }
//
//        BoundingBox bounds = plan.siteBounds();
//        int radius = Math.max(
//                Math.max(Math.abs(bounds.minX() - centerX), Math.abs(bounds.maxX() - centerX)),
//                Math.max(Math.abs(bounds.minZ() - centerZ), Math.abs(bounds.maxZ() - centerZ))
//        );
//        int verticalSpan = bounds.getYSpan();
//        long fillVolume = fillVolume(bounds);
//        int minAllowedY = heightAccessor.getMinY() + BUILD_HEIGHT_MARGIN_BLOCKS;
//        int maxAllowedY = heightAccessor.getMaxY() - 1 - BUILD_HEIGHT_MARGIN_BLOCKS;
//
//        if (radius > MAX_SITE_RADIUS_BLOCKS) {
//            throw failure(
//                    "site horizontal radius exceeds envelope",
//                    bounds,
//                    radius,
//                    verticalSpan,
//                    fillVolume
//            );
//        }
//        if (verticalSpan > MAX_SITE_VERTICAL_SPAN_BLOCKS) {
//            throw failure(
//                    "site vertical span exceeds envelope",
//                    bounds,
//                    radius,
//                    verticalSpan,
//                    fillVolume
//            );
//        }
//        if (bounds.minY() < minAllowedY || bounds.maxY() > maxAllowedY) {
//            throw failure(
//                    "site bounds violate build-height safety margin minAllowedY="
//                            + minAllowedY
//                            + " maxAllowedY="
//                            + maxAllowedY,
//                    bounds,
//                    radius,
//                    verticalSpan,
//                    fillVolume
//            );
//        }
//        if (fillVolume > MAX_SITE_FILL_VOLUME_BLOCKS) {
//            throw failure(
//                    "site fill volume exceeds envelope",
//                    bounds,
//                    radius,
//                    verticalSpan,
//                    fillVolume
//            );
//        }
//
//        return new Report(radius, verticalSpan, fillVolume);
//    }
//
//    public static long fillVolume(BoundingBox bounds) {
//        return (long) bounds.getXSpan()
//                * (long) bounds.getYSpan()
//                * (long) bounds.getZSpan();
//    }
//
//    private static DungeonLayoutGenerationException failure(
//            String reason,
//            BoundingBox bounds,
//            int radius,
//            int verticalSpan,
//            long fillVolume
//    ) {
//        return new DungeonLayoutGenerationException(
//                reason
//                        + ": bounds="
//                        + bounds
//                        + " radius="
//                        + radius
//                        + " maxRadius="
//                        + MAX_SITE_RADIUS_BLOCKS
//                        + " verticalSpan="
//                        + verticalSpan
//                        + " maxVerticalSpan="
//                        + MAX_SITE_VERTICAL_SPAN_BLOCKS
//                        + " fillVolume="
//                        + fillVolume
//                        + " maxFillVolume="
//                        + MAX_SITE_FILL_VOLUME_BLOCKS
//        );
//    }
//
//    public record Report(
//            int horizontalRadiusBlocks,
//            int verticalSpanBlocks,
//            long fillVolumeBlocks
//    ) {
//    }
//}