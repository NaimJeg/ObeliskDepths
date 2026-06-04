package io.github.naimjeg.obeliskdepths.registry;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.entity.DungeonPortalEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntityTypes {
    private ModEntityTypes() {
    }

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, ObeliskDepths.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<DungeonPortalEntity>> DUNGEON_PORTAL =
            ENTITY_TYPES.register(
                    "dungeon_portal",
                    registryName -> EntityType.Builder
                            .<DungeonPortalEntity>of(DungeonPortalEntity::new, MobCategory.MISC)
                            .sized(1.5F, 2.5F)
                            .fireImmune()
                            .clientTrackingRange(8)
                            .updateInterval(10)
                            .build(ResourceKey.create(Registries.ENTITY_TYPE, registryName))
            );

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}
