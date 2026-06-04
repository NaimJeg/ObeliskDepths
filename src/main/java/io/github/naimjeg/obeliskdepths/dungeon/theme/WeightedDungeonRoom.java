package io.github.naimjeg.obeliskdepths.dungeon.theme;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;

public record WeightedDungeonRoom(
        Identifier room,
        int weight
) {
    public static final Codec<WeightedDungeonRoom> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Identifier.CODEC
                            .fieldOf("room")
                            .forGetter(WeightedDungeonRoom::room),
                    ExtraCodecs.POSITIVE_INT
                            .fieldOf("weight")
                            .forGetter(WeightedDungeonRoom::weight)
            ).apply(instance, WeightedDungeonRoom::new));

    public WeightedDungeonRoom {
        if (room == null) {
            throw new IllegalArgumentException(
                    "Weighted dungeon room id must not be null"
            );
        }

        if (weight <= 0) {
            throw new IllegalArgumentException(
                    "Weighted dungeon room weight must be positive: " + room
            );
        }
    }
}
