package io.github.naimjeg.obeliskdepths.dungeon.preparation;

/**
 * Immutable snapshot of aggregate scan progress.
 *
 * <p>For a cancelled terminal scan, submitted and completed candidates include
 * candidates resolved directly by cancellation even if no backend probe was
 * submitted for them.
 */
public record DungeonSiteProbeProgress(
        int totalCandidates,
        int submittedCandidates,
        int completedCandidates,
        int currentlyInFlight,
        int availableCandidates,
        int notPersistedCandidates,
        int belowStatusCandidates,
        int failedCandidates,
        int malformedCandidates,
        int cancelledCandidates,
        AsyncDungeonSiteProbeState state
) {
    public DungeonSiteProbeProgress {
        if (totalCandidates < 0) {
            throw new IllegalArgumentException(
                    "totalCandidates must be non-negative");
        }
        requireNonNegative(submittedCandidates, "submittedCandidates");
        requireNonNegative(completedCandidates, "completedCandidates");
        requireNonNegative(currentlyInFlight, "currentlyInFlight");
        requireNonNegative(availableCandidates, "availableCandidates");
        requireNonNegative(notPersistedCandidates, "notPersistedCandidates");
        requireNonNegative(belowStatusCandidates, "belowStatusCandidates");
        requireNonNegative(failedCandidates, "failedCandidates");
        requireNonNegative(malformedCandidates, "malformedCandidates");
        requireNonNegative(cancelledCandidates, "cancelledCandidates");
        if (completedCandidates > submittedCandidates
                || submittedCandidates > totalCandidates) {
            throw new IllegalArgumentException(
                    "progress must satisfy completed <= submitted <= total");
        }
        if (currentlyInFlight > submittedCandidates - completedCandidates) {
            throw new IllegalArgumentException(
                    "currentlyInFlight exceeds submitted minus completed");
        }
        int classified = availableCandidates
                + notPersistedCandidates
                + belowStatusCandidates
                + failedCandidates
                + malformedCandidates
                + cancelledCandidates;
        if (classified > completedCandidates) {
            throw new IllegalArgumentException(
                    "classification counts exceed completed candidates");
        }
        if (state == null) {
            throw new IllegalArgumentException("state must be present");
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
