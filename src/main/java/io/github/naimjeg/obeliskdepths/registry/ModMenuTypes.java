package io.github.naimjeg.obeliskdepths.registry;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.menu.ObeliskPortalMenu;
import io.github.naimjeg.obeliskdepths.menu.ObeliskTemperingMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, ObeliskDepths.MOD_ID);

    public static final Supplier<MenuType<ObeliskTemperingMenu>> OBELISK_TEMPERING =
            MENU_TYPES.register("obelisk_tempering", () ->
                    new MenuType<>(
                            ObeliskTemperingMenu::new,
                            FeatureFlags.DEFAULT_FLAGS
                    )
            );

    public static final Supplier<MenuType<ObeliskPortalMenu>> OBELISK_PORTAL =
            MENU_TYPES.register("obelisk_portal", () ->
                    new MenuType<>(
                            ObeliskPortalMenu::new,
                            FeatureFlags.DEFAULT_FLAGS
                    )
            );

    private ModMenuTypes() {
    }

    public static void register(IEventBus eventBus) {
        MENU_TYPES.register(eventBus);
    }
}