package io.github.naimjeg.obeliskdepths.block.multipart;

import net.minecraft.core.Vec3i;
import net.minecraft.util.StringRepresentable;

public interface MultiPartPart extends StringRepresentable {
    Vec3i canonicalOffset();

    boolean isMainPart();
}
