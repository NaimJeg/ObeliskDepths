package io.github.naimjeg.obeliskdepths.worldgen.structure.tree;

import net.minecraft.core.BlockPos;

public record GreatSwampHourglassTreeSite(
        int centerX,
        int centerZ,
        int minY,
        int maxY,
        int maxRadius,
        long treeSeed
) {
    public static final int MIN_RADIUS = 72;
    public static final int MAX_RADIUS = 96;
    public static final int LOWER_MARGIN = 4;
    public static final int UPPER_MARGIN = 5;

    public GreatSwampHourglassTreeSite {
        if (minY >= maxY) {
            throw new IllegalArgumentException("Tree minY must be below maxY");
        }
        if (maxRadius < 1) {
            throw new IllegalArgumentException("Tree maxRadius must be positive");
        }
    }

    public int height() {
        return this.maxY - this.minY;
    }

    public int waistY() {
        return this.minY + this.height() / 2;
    }

    public BlockPos centerPosAtWaist() {
        return new BlockPos(this.centerX, this.waistY(), this.centerZ);
    }
}
