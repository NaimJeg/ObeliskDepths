package io.github.naimjeg.obeliskdepths.tempering;

import io.github.naimjeg.damagenexus.api.rule.entry.DamageEntryDefinition;

public record AggregatedTemperingEntry(
        DamageEntryDefinition entry,
        int weight
) {
    public AggregatedTemperingEntry {
        if (entry == null || entry.id() == null) {
            throw new IllegalArgumentException(
                    "Aggregated tempering entry must have an entry id"
            );
        }

        weight = Math.max(0, weight);
    }
}
