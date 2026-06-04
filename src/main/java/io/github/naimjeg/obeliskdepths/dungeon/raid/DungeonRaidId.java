package io.github.naimjeg.obeliskdepths.dungeon.raid;

import com.mojang.serialization.Codec;
import io.github.naimjeg.obeliskdepths.dungeon.serialization.DungeonCodecs;

import java.util.UUID;

public record DungeonRaidId(UUID value) {
    public static final Codec<DungeonRaidId> CODEC =
            DungeonCodecs.UUID_CODEC.xmap(DungeonRaidId::new, DungeonRaidId::value);

    public static DungeonRaidId create() {
        return new DungeonRaidId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return this.value.toString();
    }
}