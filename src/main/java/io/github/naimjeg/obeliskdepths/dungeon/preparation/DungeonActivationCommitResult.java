package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.id.PortalSessionId;
import java.util.Optional;

public record DungeonActivationCommitResult(
        boolean success,
        Optional<DungeonInstanceId> instanceId,
        Optional<PortalSessionId> portalSessionId,
        Optional<DungeonActivationCommitFailureReason> failureReason,
        String detail
) {
    public DungeonActivationCommitResult {
        instanceId = instanceId == null ? Optional.empty() : instanceId;
        portalSessionId = portalSessionId == null ? Optional.empty() : portalSessionId;
        failureReason = failureReason == null ? Optional.empty() : failureReason;
        detail = detail == null ? "" : detail;
        if (success && failureReason.isPresent()) {
            throw new IllegalArgumentException("Successful commit cannot carry a failure reason.");
        }
        if (!success && failureReason.isEmpty()) {
            throw new IllegalArgumentException("Failed commit must carry a failure reason.");
        }
    }

    public static DungeonActivationCommitResult success(
            DungeonInstanceId instanceId,
            PortalSessionId portalSessionId
    ) {
        return new DungeonActivationCommitResult(
                true,
                Optional.of(instanceId),
                Optional.of(portalSessionId),
                Optional.empty(),
                ""
        );
    }

    public static DungeonActivationCommitResult failure(
            DungeonActivationCommitFailureReason reason,
            String detail
    ) {
        return new DungeonActivationCommitResult(
                false,
                Optional.empty(),
                Optional.empty(),
                Optional.of(reason),
                detail
        );
    }
}
