package io.github.naimjeg.obeliskdepths.dungeon.id;

import com.mojang.serialization.Codec;
import io.github.naimjeg.obeliskdepths.dungeon.serialization.DungeonCodecs;

import java.util.UUID;

public record PortalSessionId(UUID value) {
    public static final Codec<PortalSessionId> CODEC =
            DungeonCodecs.UUID_CODEC.xmap(PortalSessionId::new, PortalSessionId::value);

    public static PortalSessionId create() {
        return new PortalSessionId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return this.value.toString();
    }
}