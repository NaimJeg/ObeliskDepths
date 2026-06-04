package io.github.naimjeg.obeliskdepths.dungeon.preparation;

public record DungeonPreparationFailureCause(
        DungeonPreparationJobFailureReason reason,
        String detail
) implements DungeonPreparationTerminalCause {
    public DungeonPreparationFailureCause {
        if (reason == null) {
            throw new IllegalArgumentException("Failure reason must be present.");
        }
        detail = detail == null ? "" : detail;
    }
}
