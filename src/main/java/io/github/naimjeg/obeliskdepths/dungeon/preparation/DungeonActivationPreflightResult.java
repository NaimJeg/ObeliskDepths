package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import java.util.Objects;
import java.util.Optional;

public record DungeonActivationPreflightResult(
        Optional<DungeonActivationCommitPlan> plan,
        Optional<DungeonActivationCommitFailureReason> failureReason,
        String detail
) {
    public DungeonActivationPreflightResult {
        plan = Objects.requireNonNull(plan, "plan");
        failureReason = Objects.requireNonNull(failureReason, "failureReason");
        detail = detail == null ? "" : detail;
        if (plan.isPresent() == failureReason.isPresent()) {
            throw new IllegalArgumentException(
                    "Preflight must contain either a plan or a failure"
            );
        }
    }

    static DungeonActivationPreflightResult success(
            DungeonActivationCommitPlan plan
    ) {
        return new DungeonActivationPreflightResult(
                Optional.of(plan), Optional.empty(), ""
        );
    }

    static DungeonActivationPreflightResult failure(
            DungeonActivationCommitFailureReason reason,
            String detail
    ) {
        return new DungeonActivationPreflightResult(
                Optional.empty(), Optional.of(reason), detail
        );
    }
}
