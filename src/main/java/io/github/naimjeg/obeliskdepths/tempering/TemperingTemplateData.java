package io.github.naimjeg.obeliskdepths.tempering;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record TemperingTemplateData(
        int tier,
        float weight
) {
    public static final Codec<TemperingTemplateData> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT
                            .optionalFieldOf("tier", 1)
                            .forGetter(TemperingTemplateData::tier),
                    Codec.FLOAT
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
        tier = Math.max(1, tier);
        weight = Math.max(0.0F, weight);
    }
}
