package io.github.naimjeg.obeliskdepths.dungeon.geometry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record DungeonPortReference(
        String roomId,
        String portId
) {
    public static final Codec<DungeonPortReference> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.STRING
                            .fieldOf("room")
                            .forGetter(DungeonPortReference::roomId),
                    Codec.STRING
                            .fieldOf("port")
                            .forGetter(DungeonPortReference::portId)
            ).apply(instance, DungeonPortReference::new));

    public DungeonPortReference {
        if (roomId == null || roomId.isBlank()) {
            throw new IllegalArgumentException(
                    "Dungeon port reference room id must be non-empty"
            );
        }
        if (portId == null || portId.isBlank()) {
            throw new IllegalArgumentException(
                    "Dungeon port reference port id must be non-empty"
            );
        }
    }
}
