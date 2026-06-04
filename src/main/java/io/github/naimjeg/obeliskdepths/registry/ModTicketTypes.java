package io.github.naimjeg.obeliskdepths.registry;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.TicketType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModTicketTypes {
    private ModTicketTypes() {
    }

    private static final DeferredRegister<TicketType> TICKET_TYPES =
            DeferredRegister.create(Registries.TICKET_TYPE, ObeliskDepths.MOD_ID);

    public static final DeferredHolder<TicketType, TicketType> DUNGEON_PREPARATION =
            TICKET_TYPES.register(
                    "dungeon_preparation",
                    () -> new TicketType(
                            TicketType.NO_TIMEOUT,
                            TicketType.FLAG_LOADING
                    )
            );

    public static void register(IEventBus eventBus) {
        TICKET_TYPES.register(eventBus);
    }
}
