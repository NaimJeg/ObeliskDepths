package io.github.naimjeg.obeliskdepths.dungeon.interaction;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.Objects;
import java.util.UUID;

/** Unique identity shared by one server entry operation and its client UI. */
public record DungeonPortalEntryOperationId(UUID value) {
    public static final StreamCodec<RegistryFriendlyByteBuf, DungeonPortalEntryOperationId>
            STREAM_CODEC = UUIDUtil.STREAM_CODEC.map(
                    DungeonPortalEntryOperationId::new,
                    DungeonPortalEntryOperationId::value
            ).cast();

    public DungeonPortalEntryOperationId {
        Objects.requireNonNull(value, "value");
    }

    public static DungeonPortalEntryOperationId create() {
        return new DungeonPortalEntryOperationId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return this.value.toString();
    }
}
