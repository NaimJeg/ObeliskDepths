package io.github.naimjeg.obeliskdepths.tempering;

import net.minecraft.resources.Identifier;

import java.util.List;

public record AggregatedTemperingDirection(
        Identifier directionId,
        ObeliskTemperingDirectionDefinition definition,
        List<AggregatedTemperingEntry> entries
) {
    public AggregatedTemperingDirection {
        if (directionId == null) {
            throw new IllegalArgumentException("directionId must not be null");
        }

        if (definition == null) {
            throw new IllegalArgumentException("definition must not be null");
        }

        entries = entries == null ? List.of() : List.copyOf(entries);
    }
}
