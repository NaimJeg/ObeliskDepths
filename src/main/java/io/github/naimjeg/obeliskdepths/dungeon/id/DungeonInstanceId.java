package io.github.naimjeg.obeliskdepths.dungeon.id;

import com.mojang.serialization.Codec;
import io.github.naimjeg.obeliskdepths.dungeon.serialization.DungeonCodecs;

import java.util.UUID;

public record DungeonInstanceId(UUID value) {
    public static final Codec<DungeonInstanceId> CODEC =
            DungeonCodecs.UUID_CODEC.xmap(DungeonInstanceId::new, DungeonInstanceId::value);

    public static DungeonInstanceId create() {
        return new DungeonInstanceId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return this.value.toString();
    }
}