package io.github.naimjeg.obeliskdepths.dungeon.id;

import com.mojang.serialization.Codec;
import io.github.naimjeg.obeliskdepths.dungeon.serialization.DungeonCodecs;

import java.util.UUID;

public record DungeonTerritoryId(UUID value) {
    public static final Codec<DungeonTerritoryId> CODEC =
            DungeonCodecs.UUID_CODEC.xmap(DungeonTerritoryId::new, DungeonTerritoryId::value);

    public static DungeonTerritoryId create() {
        return new DungeonTerritoryId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return this.value.toString();
    }
}