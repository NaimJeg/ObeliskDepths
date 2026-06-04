package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import java.util.Objects;
import java.util.Optional;

public record DungeonPreparationProgressSnapshot(
        DungeonPreparationStage stage,
        int totalCandidateChunks,
        int submittedCandidateChunks,
        int completedCandidateChunks,
        int inFlightCandidateChunks,
        int totalEntryChunks,
        int requestedEntryChunks,
        int readyEntryChunks,
        long totalSafeSpawnCandidates,
        long checkedSafeSpawnCandidates,
        int currentGenerationAttempt,
        int maximumGenerationAttempts,
        Optional<DungeonPreparationTerminalCause> terminalCause
) {
    public static final int MAX_MENU_DATA_VALUE = Short.MAX_VALUE;

    public DungeonPreparationProgressSnapshot {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(terminalCause, "terminalCause");
        requireMenuDataValue(totalCandidateChunks, "totalCandidateChunks");
        requireMenuDataValue(submittedCandidateChunks, "submittedCandidateChunks");
        requireMenuDataValue(completedCandidateChunks, "completedCandidateChunks");
        requireMenuDataValue(inFlightCandidateChunks, "inFlightCandidateChunks");
        requireMenuDataValue(totalEntryChunks, "totalEntryChunks");
        requireMenuDataValue(requestedEntryChunks, "requestedEntryChunks");
        requireMenuDataValue(readyEntryChunks, "readyEntryChunks");
        requireNonNegative(totalSafeSpawnCandidates, "totalSafeSpawnCandidates");
        requireNonNegative(checkedSafeSpawnCandidates, "checkedSafeSpawnCandidates");
        requireMenuDataValue(currentGenerationAttempt, "currentGenerationAttempt");
        requireMenuDataValue(maximumGenerationAttempts, "maximumGenerationAttempts");
        if (completedCandidateChunks > submittedCandidateChunks
                || submittedCandidateChunks > totalCandidateChunks) {
            throw new IllegalArgumentException(
                    "candidate progress must satisfy completed <= submitted <= total"
            );
        }
        if (inFlightCandidateChunks > submittedCandidateChunks - completedCandidateChunks) {
            throw new IllegalArgumentException(
                    "in-flight candidate chunks exceed submitted minus completed"
            );
        }
        if (readyEntryChunks > requestedEntryChunks
                || requestedEntryChunks > totalEntryChunks) {
            throw new IllegalArgumentException(
                    "entry progress must satisfy ready <= requested <= total"
            );
        }
        if (currentGenerationAttempt > maximumGenerationAttempts) {
            throw new IllegalArgumentException(
                    "currentGenerationAttempt exceeds maximumGenerationAttempts"
            );
        }
        if (checkedSafeSpawnCandidates > totalSafeSpawnCandidates) {
            throw new IllegalArgumentException(
                    "safe-spawn progress must satisfy checked <= total"
            );
        }
    }

    public static DungeonPreparationProgressSnapshot queued() {
        return empty(DungeonPreparationStage.QUEUED, Optional.empty());
    }

    public static DungeonPreparationProgressSnapshot empty(
            DungeonPreparationStage stage,
            Optional<DungeonPreparationTerminalCause> terminalCause
    ) {
        return new DungeonPreparationProgressSnapshot(
                stage,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0L,
                0L,
                0,
                DungeonPreparationLimits.MAX_GENERATION_ATTEMPTS,
                terminalCause
        );
    }

    private static void requireMenuDataValue(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
        if (value > MAX_MENU_DATA_VALUE) {
            throw new IllegalArgumentException(name + " exceeds menu data slot bounds");
        }
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }
}
