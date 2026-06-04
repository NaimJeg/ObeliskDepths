package io.github.naimjeg.obeliskdepths.block;

import io.github.naimjeg.obeliskdepths.block.multipart.MultiPartPart;
import net.minecraft.util.StringRepresentable;
import net.minecraft.core.Vec3i;

public enum ObeliskPart implements StringRepresentable, MultiPartPart {
    BOTTOM("bottom", new Vec3i(0, 0, 0), true),
    MIDDLE("middle", new Vec3i(0, 1, 0), false),
    TOP("top", new Vec3i(0, 2, 0), false);

    private final String name;
    private final Vec3i canonicalOffset;
    private final boolean mainPart;

    ObeliskPart(String name, Vec3i canonicalOffset, boolean mainPart) {
        this.name = name;
        this.canonicalOffset = canonicalOffset;
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
}
