package io.github.naimjeg.obeliskdepths.dungeon.preparation;

public sealed interface DungeonPreparationTerminalCause
        permits DungeonPreparationFailureCause,
                DungeonPreparationCancellationCause {
}
