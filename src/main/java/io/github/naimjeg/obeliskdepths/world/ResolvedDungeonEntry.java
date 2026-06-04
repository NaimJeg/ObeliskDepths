package io.github.naimjeg.obeliskdepths.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

public record ResolvedDungeonEntry(
        ServerLevel targetLevel,
        Vec3 destination,
        float yaw,
        float pitch
) {
}
