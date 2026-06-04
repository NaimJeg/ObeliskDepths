package io.github.naimjeg.obeliskdepths.worldgen.structure.tree;

import io.github.naimjeg.obeliskdepths.worldgen.GreatSwampCavernProfile;

public final class GreatSwampTreeTerrainPlacement {
    public static final int LOWER_MARGIN_BLOCKS = GreatSwampCavernProfile.TREE_LOWER_MARGIN;
    public static final int UPPER_MARGIN_BLOCKS = GreatSwampCavernProfile.TREE_UPPER_MARGIN;
    public static final int TREE_VERTICAL_SPAN = GreatSwampCavernProfile.TREE_VERTICAL_SPAN;
    public static final int SURFACE_OFFSET = GreatSwampCavernProfile.TREE_SURFACE_OFFSET;

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
