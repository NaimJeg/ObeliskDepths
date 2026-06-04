package io.github.naimjeg.obeliskdepths.dungeon.preparation;

public record DungeonPreparationCancellationCause(
        DungeonPreparationCancellationReason reason,
        String detail
) implements DungeonPreparationTerminalCause {
    public DungeonPreparationCancellationCause {
        if (reason == null) {
            throw new IllegalArgumentException("Cancellation reason must be present.");
        }
        detail = detail == null ? "" : detail;
    }
}
