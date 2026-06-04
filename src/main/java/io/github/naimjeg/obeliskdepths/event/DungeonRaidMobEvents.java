package io.github.naimjeg.obeliskdepths.event;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.encounter.DungeonEncounterDirector;
import io.github.naimjeg.obeliskdepths.dungeon.encounter.DungeonMobResolution;
import io.github.naimjeg.obeliskdepths.dungeon.entity.DungeonEntityData;
import io.github.naimjeg.obeliskdepths.dungeon.entity.DungeonEntityCleanupService;
import io.github.naimjeg.obeliskdepths.dungeon.entity.DungeonEntityTracker;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.Optional;

@EventBusSubscriber(modid = ObeliskDepths.MOD_ID)
public final class DungeonRaidMobEvents {
    private DungeonRaidMobEvents() {
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        DungeonEntityCleanupService.tickEntity(level, event.getEntity());
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }

        if (!level.dimension().equals(ModDimensions.OBELISK_DEPTHS_LEVEL)) {
            return;
        }

        Optional<DungeonEntityData> data =
                DungeonEntityTracker.get(event.getEntity());

        if (data.isEmpty()) {
            return;
        }

        DungeonEntityData value = data.get();

        value.raidId().ifPresent(raidId ->
                DungeonEncounterDirector.resolveControlledMob(
                        level,
                        raidId,
                        event.getEntity().getUUID(),
                        DungeonMobResolution.KILLED
                )
        );
    }
}
