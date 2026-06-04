package io.github.naimjeg.obeliskdepths.dungeon.raid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.serialization.DungeonCodecs;
import java.util.UUID;

public record DungeonMissingMobRecord(
        UUID entityId,
        long missingSinceGameTime
) {
    public static final Codec<DungeonMissingMobRecord> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    DungeonCodecs.UUID_CODEC.fieldOf("entity_id")
                            .forGetter(DungeonMissingMobRecord::entityId),
                    Codec.LONG.fieldOf("missing_since_game_time")
                            .forGetter(DungeonMissingMobRecord::missingSinceGameTime)
            ).apply(instance, DungeonMissingMobRecord::new));
}
