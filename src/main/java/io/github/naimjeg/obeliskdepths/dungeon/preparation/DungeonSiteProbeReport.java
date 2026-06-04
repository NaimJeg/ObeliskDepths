package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;

import java.util.List;

/**
 * Immutable final report produced when a scan reaches a terminal state.
 *
 * <p>Results and available keys are ordered by the input candidate order.
 */
public record DungeonSiteProbeReport(
        List<DungeonPersistedChunkProbeResult> results,
        List<DungeonSiteKey> availableKeys,
        int totalCandidates,
        int availableCount,
        int notPersistedCount,
        int belowStatusCount,
        int failedCount,
        int malformedCount,
        int cancelledCount,
        int peakInFlight,
        boolean wasCancelled
) {
    public DungeonSiteProbeReport {
        results = List.copyOf(results);
        availableKeys = List.copyOf(availableKeys);
        requireNonNegative(totalCandidates, "totalCandidates");
        requireNonNegative(availableCount, "availableCount");
        requireNonNegative(notPersistedCount, "notPersistedCount");
        requireNonNegative(belowStatusCount, "belowStatusCount");
        requireNonNegative(failedCount, "failedCount");
        requireNonNegative(malformedCount, "malformedCount");
        requireNonNegative(cancelledCount, "cancelledCount");
        requireNonNegative(peakInFlight, "peakInFlight");

        if (results.size() != totalCandidates) {
            throw new IllegalArgumentException(
                    "terminal results size must equal totalCandidates"
            );
        }

        int computedAvailable = 0;
        int computedNotPersisted = 0;
        int computedBelowStatus = 0;
        int computedFailed = 0;
        int computedMalformed = 0;
        int computedCancelled = 0;
        for (DungeonPersistedChunkProbeResult result : results) {
            switch (result.classification()) {
                case AVAILABLE_AT_REQUIRED_STATUS -> computedAvailable++;
                case NOT_PERSISTED -> computedNotPersisted++;
                case BELOW_REQUIRED_STATUS -> computedBelowStatus++;
                case SCAN_FAILED -> computedFailed++;
                case MALFORMED_STATUS -> computedMalformed++;
                case CANCELLED -> computedCancelled++;
            }
        }

        if (computedAvailable != availableCount
                || computedNotPersisted != notPersistedCount
                || computedBelowStatus != belowStatusCount
                || computedFailed != failedCount
                || computedMalformed != malformedCount
                || computedCancelled != cancelledCount) {
            throw new IllegalArgumentException(
                    "classification counts must match terminal results"
            );
        }

        int totalClassified = availableCount
                + notPersistedCount
                + belowStatusCount
                + failedCount
                + malformedCount
                + cancelledCount;
        if (totalClassified != totalCandidates) {
            throw new IllegalArgumentException(
                    "classification counts must equal totalCandidates"
            );
        }

        if (availableKeys.size() != availableCount) {
            throw new IllegalArgumentException(
                    "availableKeys size must equal availableCount"
            );
        }
    }

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be non-negative"
            );
        }
    }
}
