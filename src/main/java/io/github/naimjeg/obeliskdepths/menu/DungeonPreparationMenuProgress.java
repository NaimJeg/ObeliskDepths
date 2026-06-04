package io.github.naimjeg.obeliskdepths.menu;

import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationProgressSnapshot;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationStage;

import java.util.Objects;

/** Selects the unit meaningful to the current preparation stage. */
final class DungeonPreparationMenuProgress {
    private DungeonPreparationMenuProgress() {
    }

    static Progress normalize(DungeonPreparationProgressSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        DungeonPreparationStage stage = snapshot.stage();
        long completed;
        long total;
        switch (stage) {
            case SCANNING_EXISTING_SITES -> {
                completed = snapshot.completedCandidateChunks();
                total = snapshot.totalCandidateChunks();
            }
            case REQUESTING_ENTRY_CHUNKS -> {
                completed = snapshot.requestedEntryChunks();
                total = snapshot.totalEntryChunks();
            }
            case WAITING_FOR_ENTRY_CHUNKS, VALIDATING_ENTRY_CHUNKS -> {
                completed = snapshot.readyEntryChunks();
                total = snapshot.totalEntryChunks();
            }
            case VALIDATING_ENTRY -> {
                completed = snapshot.checkedSafeSpawnCandidates();
                total = snapshot.totalSafeSpawnCandidates();
            }
            case SELECTING_CANDIDATE, REQUESTING_START_CHUNK,
                    WAITING_FOR_START_CHUNK, READING_STRUCTURE_START -> {
                completed = snapshot.currentGenerationAttempt();
                total = snapshot.maximumGenerationAttempts();
            }
            default -> {
                completed = 0L;
                total = 0L;
            }
        }
        int boundedTotal = ObeliskPortalMenu.menuDataValue(total);
        int boundedCompleted = Math.min(
                ObeliskPortalMenu.menuDataValue(completed),
                boundedTotal
        );
        return new Progress(boundedCompleted, boundedTotal);
    }

    record Progress(int completed, int total) {
    }
}
