package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import java.util.Objects;

final class DungeonActivationTransactionFailure extends RuntimeException {
    private final DungeonActivationCommitFailureReason reason;

    DungeonActivationTransactionFailure(
            DungeonActivationCommitFailureReason reason,
            String detail
    ) {
        super(detail == null ? "" : detail);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    DungeonActivationCommitFailureReason reason() {
        return this.reason;
    }
}
