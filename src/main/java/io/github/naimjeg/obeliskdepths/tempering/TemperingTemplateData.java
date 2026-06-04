package io.github.naimjeg.obeliskdepths.tempering;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record TemperingTemplateData(
        int tier,
        float weight
) {
    public static final int MIN_TIER = 1;
    public static final int MAX_TIER = 4;
    public static final float MIN_WEIGHT = 0.0F;
    public static final float MAX_WEIGHT = 1.0F;

    private static final Codec<Float> WEIGHT_CODEC = Codec.FLOAT.flatXmap(
            TemperingTemplateData::validateWeight,
            TemperingTemplateData::validateWeight
    );

    public static final Codec<TemperingTemplateData> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.intRange(MIN_TIER, MAX_TIER)
                            .optionalFieldOf("tier", 1)
                            .forGetter(TemperingTemplateData::tier),
                    WEIGHT_CODEC
                            .optionalFieldOf("weight", 0.0F)
                            .forGetter(TemperingTemplateData::weight)
            ).apply(instance, TemperingTemplateData::new));

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            TemperingTemplateData
            > STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    TemperingTemplateData::tier,
                    ByteBufCodecs.FLOAT,
                    TemperingTemplateData::weight,
                    TemperingTemplateData::new
            );

    public TemperingTemplateData {
        if (tier < MIN_TIER || tier > MAX_TIER) {
            throw new IllegalArgumentException(
                    "Tempering tier must be in " + MIN_TIER + ".." + MAX_TIER
                            + ": " + tier
            );
        }
        if (!Float.isFinite(weight)
                || weight < MIN_WEIGHT
                || weight > MAX_WEIGHT) {
            throw new IllegalArgumentException(
                    "Tempering weight must be finite and in "
                            + MIN_WEIGHT + ".." + MAX_WEIGHT + ": " + weight
            );
        }
    }

    private static DataResult<Float> validateWeight(float value) {
        if (!Float.isFinite(value)
                || value < MIN_WEIGHT
                || value > MAX_WEIGHT) {
            return DataResult.error(() ->
                    "Tempering weight must be finite and in "
                            + MIN_WEIGHT + ".." + MAX_WEIGHT + ": " + value
            );
        }
        return DataResult.success(value);
    }
}
