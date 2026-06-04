package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import java.util.UUID;

public record DungeonPreparationJobId(UUID value) {
    public DungeonPreparationJobId {
        if (value == null) {
            throw new IllegalArgumentException("Job ID value must be present.");
        }
    }

    public static DungeonPreparationJobId create() {
        return new DungeonPreparationJobId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return this.value.toString();
    }
}
