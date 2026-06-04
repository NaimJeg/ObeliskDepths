package io.github.naimjeg.obeliskdepths.dungeon.preparation;

public record DungeonPreparationFailure(
        DungeonPreparationRequest request,
        DungeonPreparationFailureReason reason,
        String detail
) implements DungeonPreparationResult {
    public DungeonPreparationFailure {
        if (request == null) {
            throw new IllegalArgumentException("Preparation failure request must be present.");
        }
        if (reason == null) {
            throw new IllegalArgumentException("Preparation failure reason must be present.");
        }
        detail = detail == null ? "" : detail;
    }
}
