package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.BooleanSupplier;

final class DungeonCandidateClassificationState {
    private final List<DungeonSiteKey> persistedCandidates = new ArrayList<>();
    private final List<DungeonSiteKey> generationCandidates = new ArrayList<>();
    private int nextIndex;

    int advance(
            List<DungeonPersistedChunkProbeResult> results,
            List<DungeonSiteKey> candidates,
            int allowance,
            Function<DungeonSiteKey, String> reservationCheck,
            BooleanSupplier continueWork
    ) {
        Objects.requireNonNull(results, "results");
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(reservationCheck, "reservationCheck");
        Objects.requireNonNull(continueWork, "continueWork");
        if (allowance < 0) {
            throw new IllegalArgumentException("allowance must be non-negative");
        }
        if (results.size() != candidates.size()) {
            throw new IllegalArgumentException(
                    "results and candidates must have the same size"
            );
        }

        int processed = 0;
        while (processed < allowance && this.nextIndex < results.size()
                && continueWork.getAsBoolean()) {
            DungeonSiteKey key = candidates.get(this.nextIndex);
            DungeonPersistedChunkProbeResult result = Objects.requireNonNull(
                    results.get(this.nextIndex),
                    "result"
            );
            if (DungeonPreparationJobExecutor.CANDIDATE_ACCEPTED.equals(
                    reservationCheck.apply(key))) {
                switch (result.classification()) {
                    case AVAILABLE_AT_REQUIRED_STATUS ->
                            this.persistedCandidates.add(key);
                    case NOT_PERSISTED, BELOW_REQUIRED_STATUS -> {
                        if (this.generationCandidates.size()
                                < DungeonPreparationLimits.MAX_GENERATION_ATTEMPTS) {
                            this.generationCandidates.add(key);
                        }
                    }
                    case SCAN_FAILED, MALFORMED_STATUS, CANCELLED -> {
                        // Candidate data records the failure; later candidates continue.
                    }
                }
            }
            this.nextIndex++;
            processed++;
        }
        return processed;
    }

    boolean complete(int resultCount) {
        return this.nextIndex >= resultCount;
    }

    int nextIndex() {
        return this.nextIndex;
    }

    List<DungeonSiteKey> persistedCandidates() {
        return List.copyOf(this.persistedCandidates);
    }

    List<DungeonSiteKey> generationCandidates() {
        return List.copyOf(this.generationCandidates);
    }
}
