package io.github.naimjeg.obeliskdepths.dungeon.entity;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.encounter.DungeonEncounterDirector;
import io.github.naimjeg.obeliskdepths.dungeon.encounter.DungeonMobResolution;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonStatus;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidInstance;
import io.github.naimjeg.obeliskdepths.dungeon.spatial.DungeonSpatialValidation;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.Optional;

/*
 * Identity rule:
 * DungeonEntityData.instanceId is authoritative.
 *
 * Spatial rule:
 * Territory bounds are only a physical sanity check. They are used to detect
 * escaped mobs, corrupted teleports, or stale entities after cleanup.
 *
 * Do not infer entity ownership from position here.
 */

public final class DungeonEntityCleanupService {
    private static final int MAX_TICKS_OUTSIDE_DUNGEON = 20 * 30;

    private DungeonEntityCleanupService() {
    }

    public static void tickEntity(
            ServerLevel level,
            Entity entity
    ) {
        if (!level.dimension().equals(ModDimensions.OBELISK_DEPTHS_LEVEL)) {
            return;
        }

        if (!(entity instanceof Mob)) {
            return;
        }

        Optional<DungeonEntityData> data = DungeonEntityTracker.get(entity);

        if (data.isEmpty()) {
            return;
        }

        Optional<DungeonInstanceId> identity = data.get().instanceId();

        if (identity.isEmpty()) {
            DungeonEntityTracker.clear(entity);
            return;
        }

        DungeonManagerSavedData savedData = DungeonManagerSavedData.get(level);
        if (data.get().raidId().isPresent()) {
            Optional<DungeonRaidInstance> encounter =
                    savedData.raids().get(data.get().raidId().get());
            if (encounter.isPresent()) {
                if (encounter.get().cleanupPendingMobIds().contains(entity.getUUID())) {
                    DungeonEntityTracker.clear(entity);
                    entity.discard();
                    savedData.raids().clearCleanupPending(encounter.get(), entity.getUUID());
                    return;
                }

                if (!encounter.get().trackedMobIds().contains(entity.getUUID())) {
                    DungeonEntityTracker.clear(entity);
                    entity.discard();
                    return;
                }
            }
        }

        Optional<DungeonInstance> instance = savedData.instances().get(identity.get());

        if (instance.isEmpty()
                || instance.get().status() != DungeonStatus.ACTIVE) {
            data.get().raidId().ifPresent(raidId ->
                    DungeonEncounterDirector.resolveControlledMob(
                            level,
                            raidId,
                            entity.getUUID(),
                            DungeonMobResolution.INVALIDATED
                    )
            );
            entity.discard();
            return;
        }

        /*
         * Position is not identity.
         * This is only an escape/stale-entity sanity check.
         */
        boolean physicallyValid =
                DungeonSpatialValidation.entityIsPhysicallyInsideInstance(
                        level,
                        entity,
                        identity.get()
                );

        if (physicallyValid) {
            if (data.get().ticksOutsideDungeon() != 0) {
                DungeonEntityTracker.set(
                        entity,
                        data.get().withTicksOutsideDungeon(0)
                );
            }

            return;
        }

        int ticksOutside = data.get().ticksOutsideDungeon() + 1;

        if (ticksOutside >= MAX_TICKS_OUTSIDE_DUNGEON) {
            data.get().raidId().ifPresent(raidId ->
                    DungeonEncounterDirector.resolveControlledMob(
                            level,
                            raidId,
                            entity.getUUID(),
                            DungeonMobResolution.ESCAPED
                    )
            );
            entity.discard();
            return;
        }

        DungeonEntityTracker.set(
                entity,
                data.get().withTicksOutsideDungeon(ticksOutside)
        );
    }
}
