package io.github.naimjeg.obeliskdepths.dungeon.entity;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.encounter.DungeonEncounterMobRole;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidId;
import io.github.naimjeg.obeliskdepths.registry.ModAttachments;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

public final class DungeonEntityTracker {
    private DungeonEntityTracker() {
    }

    public static void bindRaidMob(
            Entity entity,
            DungeonInstanceId instanceId,
            DungeonRaidId raidId,
            int wave
    ) {
        entity.setData(
                ModAttachments.DUNGEON_ENTITY.get(),
                DungeonEntityData.raidMob(instanceId, raidId, wave)
        );
    }

    public static void bindControlledMob(
            Entity entity,
            DungeonInstanceId instanceId,
            DungeonRaidId encounterId,
            DungeonEncounterMobRole mobRole
    ) {
        entity.setData(
                ModAttachments.DUNGEON_ENTITY.get(),
                DungeonEntityData.controlledMob(
                        instanceId,
                        encounterId,
                        mobRole,
                        0
                )
        );
    }

    public static Optional<DungeonEntityData> get(Entity entity) {
        DungeonEntityData data = entity.getData(ModAttachments.DUNGEON_ENTITY.get());
        return data.isEmpty() ? Optional.empty() : Optional.of(data);
    }

    public static void set(Entity entity, DungeonEntityData data) {
        entity.setData(ModAttachments.DUNGEON_ENTITY.get(), data);
    }

    public static void clear(Entity entity) {
        entity.setData(
                ModAttachments.DUNGEON_ENTITY.get(),
                DungeonEntityData.empty()
        );
    }
}
