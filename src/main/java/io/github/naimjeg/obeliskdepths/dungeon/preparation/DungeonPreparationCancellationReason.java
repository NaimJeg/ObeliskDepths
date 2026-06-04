package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import java.util.Optional;

public enum DungeonPreparationCancellationReason {
    USER_CANCELLED(1),
    PLAYER_DISCONNECTED(2),
    PLAYER_DIMENSION_CHANGED(3),
    PLAYER_MOVED_TOO_FAR(4),
    PLAYER_DIED(5),
    OBELISK_INVALID(6),
    MENU_CLOSED(7),
    LEVEL_UNLOADED(8),
    SERVER_STOPPING(9),
    TIMEOUT(10);

    private final int wireCode;

    DungeonPreparationCancellationReason(int wireCode) {
        this.wireCode = wireCode;
    }

    public int wireCode() {
        return this.wireCode;
    }

    public static Optional<DungeonPreparationCancellationReason> fromWireCode(
            int wireCode
    ) {
        for (DungeonPreparationCancellationReason reason : values()) {
            if (reason.wireCode == wireCode) {
                return Optional.of(reason);
            }
        }
        return Optional.empty();
    }
}
