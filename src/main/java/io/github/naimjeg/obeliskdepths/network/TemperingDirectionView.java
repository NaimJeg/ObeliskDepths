package io.github.naimjeg.obeliskdepths.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

public record TemperingDirectionView(
        Identifier id,
        Component displayName,
        Component description
) {
    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            TemperingDirectionView
            > STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC,
            TemperingDirectionView::id,
            ComponentSerialization.STREAM_CODEC,
            TemperingDirectionView::displayName,
            ComponentSerialization.STREAM_CODEC,
            TemperingDirectionView::description,
            TemperingDirectionView::new
    );

    public TemperingDirectionView {
        if (id == null) {
            throw new IllegalArgumentException("Direction id must not be null");
        }

        displayName = displayName == null
                ? Component.literal(id.toString())
                : displayName;
        description = description == null
                ? Component.empty()
                : description;
    }
}
