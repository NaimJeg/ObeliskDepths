package io.github.naimjeg.obeliskdepths.dungeon.preparation;

public enum DungeonPreparationStage {
    QUEUED,
    VALIDATING,
    SCANNING_EXISTING_SITES,
    SELECTING_CANDIDATE,
    WAITING_FOR_START_CHUNK,
    READING_STRUCTURE_START,
    WAITING_FOR_ENTRY_CHUNKS,
    VALIDATING_ENTRY,
    READY_TO_COMMIT,
    COMMITTING,
    READY,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == READY || this == FAILED || this == CANCELLED;
    }
}
