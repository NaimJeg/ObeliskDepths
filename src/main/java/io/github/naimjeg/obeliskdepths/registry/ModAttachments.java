package io.github.naimjeg.obeliskdepths.registry;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.entity.DungeonEntityData;
import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    private ModAttachments() {
    }

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(
                    NeoForgeRegistries.Keys.ATTACHMENT_TYPES,
                    ObeliskDepths.MOD_ID
            );

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<DungeonEntityData>> DUNGEON_ENTITY =
            ATTACHMENT_TYPES.register(
                    "dungeon_entity",
                    () -> AttachmentType.builder(DungeonEntityData::empty)
                            .serialize(
                                    DungeonEntityData.MAP_CODEC,
                                    data -> !data.isEmpty()
                            )
                            .build()
            );

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerDungeonData>> PLAYER_DUNGEON =
            ATTACHMENT_TYPES.register(
                    "player_dungeon",
                    () -> AttachmentType.builder(PlayerDungeonData::empty)
                            .serialize(PlayerDungeonData.MAP_CODEC, data -> !data.isEmpty())
                            .build()
            );

    public static void register(IEventBus eventBus) {
        ATTACHMENT_TYPES.register(eventBus);
    }
}
