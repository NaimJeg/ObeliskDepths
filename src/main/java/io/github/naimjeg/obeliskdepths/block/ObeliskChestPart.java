package io.github.naimjeg.obeliskdepths.block;

import io.github.naimjeg.obeliskdepths.block.multipart.MultiPartPart;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.util.StringRepresentable;

public enum ObeliskChestPart implements StringRepresentable, MultiPartPart {
    BOTTOM_FRONT_LEFT("bottom_front_left", 0, 0, 0, true),
    BOTTOM_FRONT_CENTER("bottom_front_center", 1, 0, 0, false),
    BOTTOM_FRONT_RIGHT("bottom_front_right", 2, 0, 0, false),
    BOTTOM_BACK_LEFT("bottom_back_left", 0, 0, 1, false),
    BOTTOM_BACK_CENTER("bottom_back_center", 1, 0, 1, false),
    BOTTOM_BACK_RIGHT("bottom_back_right", 2, 0, 1, false),
    TOP_FRONT_LEFT("top_front_left", 0, 1, 0, false),
    TOP_FRONT_CENTER("top_front_center", 1, 1, 0, false),
    TOP_FRONT_RIGHT("top_front_right", 2, 1, 0, false),
    TOP_BACK_LEFT("top_back_left", 0, 1, 1, false),
    TOP_BACK_CENTER("top_back_center", 1, 1, 1, false),
    TOP_BACK_RIGHT("top_back_right", 2, 1, 1, false);

    private final String name;
    private final Vec3i canonicalOffset;
    private final boolean mainPart;

    ObeliskChestPart(String name, int x, int y, int z, boolean mainPart) {
        this.name = name;
        this.canonicalOffset = new Vec3i(x, y, z);
        this.mainPart = mainPart;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    @Override
    public Vec3i canonicalOffset() {
        return this.canonicalOffset;
    }

    @Override
    public boolean isMainPart() {
        return this.mainPart;
    }

    public static Vec3i transformCanonicalOffset(Direction facing, Vec3i canonicalOffset) {
        Direction right = facing.getClockWise();
        Direction back = facing.getOpposite();

        int x = right.getStepX() * canonicalOffset.getX()
                + back.getStepX() * canonicalOffset.getZ();
        int y = canonicalOffset.getY();
        int z = right.getStepZ() * canonicalOffset.getX()
                + back.getStepZ() * canonicalOffset.getZ();

        return new Vec3i(x, y, z);
    }
}
