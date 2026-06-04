package io.github.naimjeg.obeliskdepths.dungeon.theme;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;

public record WeightedDungeonCorridor(
        Identifier corridor,
        int weight
) {
    public static final Codec<WeightedDungeonCorridor> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Identifier.CODEC
                            .fieldOf("corridor")
                            .forGetter(WeightedDungeonCorridor::corridor),
                    ExtraCodecs.POSITIVE_INT
                            .fieldOf("weight")
                            .forGetter(WeightedDungeonCorridor::weight)
            ).apply(instance, WeightedDungeonCorridor::new));

    public WeightedDungeonCorridor {
        if (corridor == null) {
            throw new IllegalArgumentException(
                    "Weighted dungeon corridor id must not be null"
            );
        }

        if (weight <= 0) {
            throw new IllegalArgumentException(
                    "Weighted dungeon corridor weight must be positive: "
                            + corridor
            );
        }
    }
}
