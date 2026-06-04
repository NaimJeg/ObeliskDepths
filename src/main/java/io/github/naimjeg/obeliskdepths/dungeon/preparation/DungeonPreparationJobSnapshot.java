package io.github.naimjeg.obeliskdepths.dungeon.preparation;

public record DungeonPreparationJobSnapshot(
        DungeonPreparationJobId id,
        DungeonPreparationRequest request,
        long createdAtGameTime,
        long lastTransitionGameTime,
        DungeonPreparationStage stage,
        DungeonPreparationTerminalCause terminalCause
) {
    public DungeonPreparationJobSnapshot {
        if (id == null) {
            throw new IllegalArgumentException("Snapshot job id must be present.");
        }
        if (request == null) {
            throw new IllegalArgumentException("Snapshot request must be present.");
        }
        if (stage == null) {
            throw new IllegalArgumentException("Snapshot stage must be present.");
        }
        if (stage == DungeonPreparationStage.FAILED
                && !(terminalCause instanceof DungeonPreparationFailureCause)) {
            throw new IllegalArgumentException("Failed snapshot requires failure cause.");
        }
        if (stage == DungeonPreparationStage.CANCELLED
                && !(terminalCause instanceof DungeonPreparationCancellationCause)) {
            throw new IllegalArgumentException("Cancelled snapshot requires cancellation cause.");
        }
        if (stage != DungeonPreparationStage.FAILED
                && stage != DungeonPreparationStage.CANCELLED
                && terminalCause != null) {
            throw new IllegalArgumentException(
                    "Non-failed/non-cancelled snapshot must not have terminal cause."
            );
        }
    }
}
