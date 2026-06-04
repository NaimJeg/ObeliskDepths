package io.github.naimjeg.obeliskdepths.dungeon.preparation;

public record DungeonPreparationRuntimeMetrics(
        int activePreparationJobs,
        int activeChunkLeaseRecords,
        int pendingPhysicalTicketReleases,
        long physicalTicketReleaseFailures,
        long terminalUnresolvedPhysicalTicketDebt,
        int preparedEntryCount,
        int preparedEntryChunkCount,
        long preparedEntriesRemovedAsStale,
        long preparedEntriesRemovedForMissingSession,
        long preparedEntriesRemovedForMissingInstance,
        long preparedEntriesRemovedForInactiveInstance,
        long portalSessionsRemovedDuringReconciliation,
        long preparedEntryCloseFailures,
        long claimReleaseInvariantFailures,
        long committedPublicationFailures,
        int activePostTeleportHandoffs,
        long completedPostTeleportHandoffs,
        long timedOutPostTeleportHandoffs,
        long abortedPostTeleportHandoffs,
        int activeRecoveryJobs,
        int activePersistedScanners,
        int pendingScannerCompletions,
        int highWaterActiveJobs,
        int highWaterActiveLeases
) {
}
