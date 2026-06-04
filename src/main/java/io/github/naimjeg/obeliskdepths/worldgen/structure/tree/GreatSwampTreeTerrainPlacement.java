package io.github.naimjeg.obeliskdepths.worldgen.structure.tree;

public final class GreatSwampTreeTerrainPlacement {
    public static final int LOWER_MARGIN_BLOCKS = GreatSwampHourglassTreeSite.LOWER_MARGIN;
    public static final int UPPER_MARGIN_BLOCKS = GreatSwampHourglassTreeSite.UPPER_MARGIN;
    public static final int TREE_VERTICAL_SPAN = 119;
    public static final int SURFACE_OFFSET = 1;

    private GreatSwampTreeTerrainPlacement() {
    }

    public static int terrainRelativeMinY(int surfaceY, int dimensionMinY, int dimensionMaxY) {
        int requestedMinY = surfaceY + SURFACE_OFFSET;
        int minAllowedY = dimensionMinY + LOWER_MARGIN_BLOCKS;
        int maxAllowedY = dimensionMaxY - UPPER_MARGIN_BLOCKS - TREE_VERTICAL_SPAN;
        if (maxAllowedY < minAllowedY) {
            throw new IllegalArgumentException(
                    "Great Swamp tree vertical span does not fit dimension height: minY="
                            + dimensionMinY
                            + " maxY="
                            + dimensionMaxY
                            + " span="
                            + TREE_VERTICAL_SPAN);
        }
        return Math.max(minAllowedY, Math.min(requestedMinY, maxAllowedY));
    }
}
