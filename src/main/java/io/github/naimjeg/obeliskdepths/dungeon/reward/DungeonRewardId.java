package io.github.naimjeg.obeliskdepths.dungeon.reward;

import com.mojang.serialization.Codec;
import io.github.naimjeg.obeliskdepths.dungeon.serialization.DungeonCodecs;
import java.util.UUID;

public record DungeonRewardId(UUID value) {
    public static final Codec<DungeonRewardId> CODEC =
            DungeonCodecs.UUID_CODEC.xmap(DungeonRewardId::new, DungeonRewardId::value);

    public static DungeonRewardId create() {
        return new DungeonRewardId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return this.value.toString();
    }
}
