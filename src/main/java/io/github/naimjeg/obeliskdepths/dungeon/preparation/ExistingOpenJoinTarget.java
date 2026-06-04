package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.id.PortalSessionId;

public record ExistingOpenJoinTarget(
        DungeonPreparationRequest request,
        DungeonInstanceId instanceId,
        PortalSessionId portalSessionId
) implements DungeonPreparedTarget {
    public ExistingOpenJoinTarget {
        if (request == null) {
            throw new IllegalArgumentException("Existing target request must be present.");
        }
        if (instanceId == null) {
            throw new IllegalArgumentException("Existing target instance id must be present.");
        }
        if (portalSessionId == null) {
            throw new IllegalArgumentException("Existing target portal session id must be present.");
        }
    }
}
