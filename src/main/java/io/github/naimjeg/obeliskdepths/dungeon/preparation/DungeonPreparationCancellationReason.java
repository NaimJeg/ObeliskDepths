package io.github.naimjeg.obeliskdepths.dungeon.preparation;

public enum DungeonPreparationCancellationReason {
    USER_CANCELLED,
    PLAYER_DISCONNECTED,
    PLAYER_DIMENSION_CHANGED,
    LEVEL_UNLOADED,
    SERVER_STOPPING,
    TIMEOUT
}
