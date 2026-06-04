package io.github.naimjeg.obeliskdepths.tempering;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

public record TemperingAffixPreview(
        Identifier entryId,
        Component displayName,
        Component description,
        int weight
) {
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            TemperingAffixPreview
            > STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC,
            TemperingAffixPreview::entryId,
            ComponentSerialization.STREAM_CODEC,
            TemperingAffixPreview::displayName,
            ComponentSerialization.STREAM_CODEC,
            TemperingAffixPreview::description,
            ByteBufCodecs.VAR_INT,
            TemperingAffixPreview::weight,
            TemperingAffixPreview::new
    );

    public TemperingAffixPreview {
        if (entryId == null) {
            throw new IllegalArgumentException("Entry id must not be null");
        }

        displayName = displayName == null
                ? Component.literal(entryId.toString())
                : displayName;
        description = description == null
                ? Component.empty()
                : description;
        weight = Math.max(0, weight);
    }
}
