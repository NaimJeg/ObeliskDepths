package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * An immutable safe-spawn position prepared for a future portal entry.
 *
 * <p>The destination is transient and never serialized. Player orientation
 * is deliberately excluded: portal entry combines this position with the
 * entering player's current orientation when it creates the final world
 * teleport resolution.</p>
 */
public record PreparedDungeonDestination(Vec3 position) {
    public PreparedDungeonDestination {
        Objects.requireNonNull(position, "position");
        if (!Double.isFinite(position.x())
                || !Double.isFinite(position.y())
                || !Double.isFinite(position.z())) {
            throw new IllegalArgumentException(
                    "destination coordinates must be finite: " + position
            );
        }
    }
}
