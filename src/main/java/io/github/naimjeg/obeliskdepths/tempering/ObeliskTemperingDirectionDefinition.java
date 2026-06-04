package io.github.naimjeg.obeliskdepths.tempering;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;

/**
 * Reloadable display metadata for a data-driven tempering direction.
 * Direction identity is the resource location of the JSON file itself.
 */
public record ObeliskTemperingDirectionDefinition(
        Component displayName,
        Component description,
        int order
) {
    public static final Codec<ObeliskTemperingDirectionDefinition> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    ComponentSerialization.CODEC
                            .fieldOf("display_name")
                            .forGetter(ObeliskTemperingDirectionDefinition::displayName),
                    ComponentSerialization.CODEC
                            .fieldOf("description")
                            .forGetter(ObeliskTemperingDirectionDefinition::description),
                    Codec.INT
                            .optionalFieldOf("order", 0)
                            .forGetter(ObeliskTemperingDirectionDefinition::order)
            ).apply(instance, ObeliskTemperingDirectionDefinition::new));

    public ObeliskTemperingDirectionDefinition {
        displayName = displayName == null ? Component.empty() : displayName;
        description = description == null ? Component.empty() : description;
    }
}
