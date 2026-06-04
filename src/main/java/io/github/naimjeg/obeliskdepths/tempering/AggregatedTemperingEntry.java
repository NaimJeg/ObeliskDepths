package io.github.naimjeg.obeliskdepths.tempering;

import net.minecraft.resources.Identifier;

/** Client/server preview metadata carries only an Obelisk template ID. */
public record AggregatedTemperingEntry(Identifier templateId, int weight) {
    public AggregatedTemperingEntry {
        if (templateId == null) {
            throw new IllegalArgumentException("templateId must not be null");
        }
        if (weight <= 0) {
            throw new IllegalArgumentException("weight must be positive: " + templateId);
        }
    }
}
