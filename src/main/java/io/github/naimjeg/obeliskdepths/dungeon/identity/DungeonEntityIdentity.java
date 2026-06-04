package io.github.naimjeg.obeliskdepths.dungeon.identity;

import io.github.naimjeg.obeliskdepths.dungeon.entity.DungeonEntityTracker;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidId;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

public final class DungeonEntityIdentity {
    private DungeonEntityIdentity() {
    }

    public static Optional<DungeonInstanceId> instanceId(Entity entity) {
        return DungeonEntityTracker.get(entity)
                .flatMap(data -> data.instanceId());
    }

    public static Optional<DungeonRaidId> raidId(Entity entity) {
        return DungeonEntityTracker.get(entity)
                .flatMap(data -> data.raidId());
    }

    public static boolean belongsToInstance(
            Entity entity,
            DungeonInstanceId instanceId
    ) {
        return instanceId(entity)
                .map(instanceId::equals)
                .orElse(false);
    }

    public static boolean isDungeonEntity(Entity entity) {
        return instanceId(entity).isPresent();
    }
}