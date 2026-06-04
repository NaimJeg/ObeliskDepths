package io.github.naimjeg.obeliskdepths.dungeon.site;

import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.Optional;

public record DungeonSafeSpawnScanResult(
        DungeonSafeSpawnScanState state,
        long candidatesChecked,
        long totalCandidates,
        Optional<Vec3> resolvedPosition
) {
    public DungeonSafeSpawnScanResult {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(resolvedPosition, "resolvedPosition");
        if (candidatesChecked < 0L || totalCandidates < 0L
                || candidatesChecked > totalCandidates) {
            throw new IllegalArgumentException(
                    "safe-spawn progress must satisfy 0 <= checked <= total"
            );
        }
        if ((state == DungeonSafeSpawnScanState.FOUND)
                != resolvedPosition.isPresent()) {
            throw new IllegalArgumentException(
                    "only a FOUND scan may contain a resolved position"
            );
        }
    }

    public boolean running() {
        return this.state == DungeonSafeSpawnScanState.RUNNING;
    }
}
