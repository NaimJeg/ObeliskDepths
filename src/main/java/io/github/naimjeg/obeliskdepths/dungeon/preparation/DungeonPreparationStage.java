package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import java.util.Optional;

public enum DungeonPreparationStage {
    QUEUED(0),
    VALIDATING(1),
    SCANNING_EXISTING_SITES(2),
    SELECTING_CANDIDATE(3),
    WAITING_FOR_START_CHUNK(4),
    READING_STRUCTURE_START(5),
    WAITING_FOR_ENTRY_CHUNKS(6),
    VALIDATING_ENTRY(7),
    READY_TO_COMMIT(8),
    COMMITTING(9),
    READY(10),
    FAILED(11),
    CANCELLED(12),
    REQUESTING_START_CHUNK(13),

    // Wire code 14 is permanently reserved and intentionally unmapped.
    PLANNING_ENTRY_CHUNKS(15),
    REQUESTING_ENTRY_CHUNKS(16),
    VALIDATING_ENTRY_CHUNKS(17);

    private final int wireCode;

    DungeonPreparationStage(int wireCode) {
        this.wireCode = wireCode;
    }

    public int wireCode() {
        return this.wireCode;
    }

    public static Optional<DungeonPreparationStage> fromWireCode(int code) {
        for (DungeonPreparationStage stage : values()) {
            if (stage.wireCode == code) {
                return Optional.of(stage);
            }
        }
        return Optional.empty();
    }

    public boolean isTerminal() {
        return this == READY || this == FAILED || this == CANCELLED;
    }
}
