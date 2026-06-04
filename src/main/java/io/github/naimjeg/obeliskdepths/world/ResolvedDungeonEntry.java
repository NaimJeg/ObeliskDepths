package io.github.naimjeg.obeliskdepths.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

public record ResolvedDungeonEntry(
        ServerLevel targetLevel,
        Vec3 destination,
        float yaw,
        float pitch
) {
    public ResolvedDungeonEntry {
        Objects.requireNonNull(targetLevel, "targetLevel");
        Objects.requireNonNull(destination, "destination");
        if (!Double.isFinite(destination.x())
                || !Double.isFinite(destination.y())
                || !Double.isFinite(destination.z())) {
            throw new IllegalArgumentException("destination must be finite");
        }
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("rotation must be finite");
        }
        BlockPos blockPos = BlockPos.containing(destination);
        if (targetLevel.isOutsideBuildHeight(blockPos.getY())) {
            throw new IllegalArgumentException(
                    "destination is outside the target level build height"
            );
        }
    }
}
