package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.id.PortalSessionId;

import java.util.Optional;
import java.util.UUID;

interface DungeonActivationTransactionBackend {
    void assertOwnerThread();

    Optional<DungeonActivationTransactionFailure> revalidate(
            DungeonActivationCommitPlan plan
    );

    Optional<DungeonInstanceId> reserveSite(DungeonActivationCommitPlan plan);

    void releaseReservedSite(DungeonInstanceId instanceId);

    PortalSessionId createPortalSession(
            DungeonActivationCommitPlan plan,
            DungeonInstanceId instanceId
    );

    void removeCreatedPortalSession(PortalSessionId portalSessionId);

    DungeonSessionResult acquireDungeonSession(
            DungeonActivationCommitPlan plan,
            DungeonInstanceId instanceId,
            PortalSessionId portalSessionId
    );

    void removeCreatedDungeonSession(UUID sessionId);

    PortalEntityResult ensurePortalEntity(PortalSessionId portalSessionId);

    void removeCreatedPortalEntity();

    void detachEntryLeases(DungeonActivationCommitPlan plan);

    void closeDetachedEntryLeases();

    void registerPreparedEntry(
            DungeonActivationCommitPlan plan,
            DungeonInstanceId instanceId,
            PortalSessionId portalSessionId
    );

    void removeRegisteredPreparedEntry(PortalSessionId portalSessionId);

    void releaseStartLeaseAfterPreparedEntry();

    boolean releaseSiteClaim(DungeonActivationCommitPlan plan);

    void restoreSiteClaim(DungeonActivationCommitPlan plan);

    void consumeTribute(DungeonActivationCommitPlan plan);

    record DungeonSessionResult(UUID sessionId, boolean created) {
    }

    record PortalEntityResult(boolean success, boolean created) {
    }
}
