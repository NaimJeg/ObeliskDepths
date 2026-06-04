package io.github.naimjeg.obeliskdepths.equipment;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/** Deterministic result of one reward-equipment template selection. */
public record ObeliskEquipmentRoll(
        Identifier templateId,
        long seed,
        int ordinal
) {
    public ObeliskEquipmentRoll {
        Objects.requireNonNull(templateId, "templateId");
        if (ordinal < 0) {
            throw new IllegalArgumentException("ordinal must be non-negative");
        }
    }
}
