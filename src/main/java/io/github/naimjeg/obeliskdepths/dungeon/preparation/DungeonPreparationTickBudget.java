package io.github.naimjeg.obeliskdepths.dungeon.preparation;

/** Shared count and wall-clock budget for all preparation and recovery work in one level tick. */
public final class DungeonPreparationTickBudget {
    private static final NanoClock SYSTEM_CLOCK = System::nanoTime;

    private final NanoClock clock;
    private final long startNanos;
    private final long maxPreparationNanos;
    private final boolean unbounded;
    private final int maxActivePersistedScannersPerLevel;
    private final int maxInFlightPersistedProbesPerLevel;
    private final int maxGenerationAttempts;

    private int remainingStartChunkRequests;
    private int remainingEntryChunkRequests;
    private int remainingCandidateKeysEnumerated;
    private int remainingLoadedFastPathProbes;
    private int remainingPersistedScannerStarts;
    private int remainingPersistedProbeSubmissions;
    private int remainingPersistedProbeCompletionDrains;
    private int remainingPersistedProbeResultsClassified;
    private int remainingSafeSpawnCandidates;

    private DungeonPreparationTickBudget(
            NanoClock clock,
            long startNanos,
            long maxPreparationNanos,
            int remainingStartChunkRequests,
            int remainingEntryChunkRequests,
            int remainingCandidateKeysEnumerated,
            int remainingLoadedFastPathProbes,
            int remainingPersistedScannerStarts,
            int remainingPersistedProbeSubmissions,
            int remainingPersistedProbeCompletionDrains,
            int remainingPersistedProbeResultsClassified,
            int remainingSafeSpawnCandidates,
            int maxActivePersistedScannersPerLevel,
            int maxInFlightPersistedProbesPerLevel,
            int maxGenerationAttempts,
            boolean unbounded
    ) {
        this.clock = requireClock(clock);
        this.startNanos = startNanos;
        this.maxPreparationNanos = requireNonNegative(
                maxPreparationNanos,
                "maxPreparationNanos"
        );
        this.remainingStartChunkRequests = requireNonNegative(
                remainingStartChunkRequests,
                "remainingStartChunkRequests"
        );
        this.remainingEntryChunkRequests = requireNonNegative(
                remainingEntryChunkRequests,
                "remainingEntryChunkRequests"
        );
        this.remainingCandidateKeysEnumerated = requireNonNegative(
                remainingCandidateKeysEnumerated,
                "remainingCandidateKeysEnumerated"
        );
        this.remainingLoadedFastPathProbes = requireNonNegative(
                remainingLoadedFastPathProbes,
                "remainingLoadedFastPathProbes"
        );
        this.remainingPersistedScannerStarts = requireNonNegative(
                remainingPersistedScannerStarts,
                "remainingPersistedScannerStarts"
        );
        this.remainingPersistedProbeSubmissions = requireNonNegative(
                remainingPersistedProbeSubmissions,
                "remainingPersistedProbeSubmissions"
        );
        this.remainingPersistedProbeCompletionDrains = requireNonNegative(
                remainingPersistedProbeCompletionDrains,
                "remainingPersistedProbeCompletionDrains"
        );
        this.remainingPersistedProbeResultsClassified = requireNonNegative(
                remainingPersistedProbeResultsClassified,
                "remainingPersistedProbeResultsClassified"
        );
        this.remainingSafeSpawnCandidates = requireNonNegative(
                remainingSafeSpawnCandidates,
                "remainingSafeSpawnCandidates"
        );
        this.maxActivePersistedScannersPerLevel = requireNonNegative(
                maxActivePersistedScannersPerLevel,
                "maxActivePersistedScannersPerLevel"
        );
        this.maxInFlightPersistedProbesPerLevel = requireNonNegative(
                maxInFlightPersistedProbesPerLevel,
                "maxInFlightPersistedProbesPerLevel"
        );
        this.maxGenerationAttempts = requireNonNegative(
                maxGenerationAttempts,
                "maxGenerationAttempts"
        );
        this.unbounded = unbounded;
    }

    public static DungeonPreparationTickBudget perLevelTick() {
        return perLevelTick(SYSTEM_CLOCK);
    }

    static DungeonPreparationTickBudget perLevelTick(NanoClock clock) {
        return bounded(
                clock,
                DungeonPreparationLimits.MAX_PREPARATION_NANOS_PER_LEVEL_TICK,
                DungeonPreparationLimits.START_CHUNK_REQUESTS_PER_LEVEL_TICK,
                DungeonPreparationLimits.ENTRY_CHUNK_REQUESTS_PER_LEVEL_TICK,
                DungeonPreparationLimits.CANDIDATE_KEYS_ENUMERATED_PER_LEVEL_TICK,
                DungeonPreparationLimits.LOADED_FAST_PATH_PROBES_PER_LEVEL_TICK,
                DungeonPreparationLimits.PERSISTED_SCANNER_STARTS_PER_LEVEL_TICK,
                DungeonPreparationLimits.PERSISTED_PROBE_SUBMISSIONS_PER_LEVEL_TICK,
                DungeonPreparationLimits.PERSISTED_PROBE_COMPLETION_DRAINS_PER_LEVEL_TICK,
                DungeonPreparationLimits.PERSISTED_PROBE_RESULTS_CLASSIFIED_PER_LEVEL_TICK,
                DungeonPreparationLimits.SAFE_SPAWN_CANDIDATES_PER_LEVEL_TICK,
                DungeonPreparationLimits.MAX_ACTIVE_PERSISTED_SCANNERS_PER_LEVEL,
                DungeonPreparationLimits.MAX_IN_FLIGHT_PERSISTED_PROBES_PER_LEVEL,
                DungeonPreparationLimits.MAX_GENERATION_ATTEMPTS
        );
    }

    public static DungeonPreparationTickBudget boundedForTests(
            NanoClock clock,
            long maxPreparationNanos,
            int startChunkRequests,
            int entryChunkRequests,
            int candidateKeysEnumerated,
            int loadedFastPathProbes,
            int persistedScannerStarts,
            int persistedProbeSubmissions,
            int persistedProbeCompletionDrains,
            int persistedProbeResultsClassified,
            int safeSpawnCandidates,
            int maxActivePersistedScannersPerLevel,
            int maxInFlightPersistedProbesPerLevel,
            int maxGenerationAttempts
    ) {
        return bounded(
                clock,
                maxPreparationNanos,
                startChunkRequests,
                entryChunkRequests,
                candidateKeysEnumerated,
                loadedFastPathProbes,
                persistedScannerStarts,
                persistedProbeSubmissions,
                persistedProbeCompletionDrains,
                persistedProbeResultsClassified,
                safeSpawnCandidates,
                maxActivePersistedScannersPerLevel,
                maxInFlightPersistedProbesPerLevel,
                maxGenerationAttempts
        );
    }

    private static DungeonPreparationTickBudget bounded(
            NanoClock clock,
            long maxPreparationNanos,
            int startChunkRequests,
            int entryChunkRequests,
            int candidateKeysEnumerated,
            int loadedFastPathProbes,
            int persistedScannerStarts,
            int persistedProbeSubmissions,
            int persistedProbeCompletionDrains,
            int persistedProbeResultsClassified,
            int safeSpawnCandidates,
            int maxActivePersistedScannersPerLevel,
            int maxInFlightPersistedProbesPerLevel,
            int maxGenerationAttempts
    ) {
        NanoClock checkedClock = requireClock(clock);
        return new DungeonPreparationTickBudget(
                checkedClock,
                checkedClock.nanoTime(),
                maxPreparationNanos,
                startChunkRequests,
                entryChunkRequests,
                candidateKeysEnumerated,
                loadedFastPathProbes,
                persistedScannerStarts,
                persistedProbeSubmissions,
                persistedProbeCompletionDrains,
                persistedProbeResultsClassified,
                safeSpawnCandidates,
                maxActivePersistedScannersPerLevel,
                maxInFlightPersistedProbesPerLevel,
                maxGenerationAttempts,
                false
        );
    }

    public static DungeonPreparationTickBudget unlimitedForTests() {
        return new DungeonPreparationTickBudget(
                SYSTEM_CLOCK,
                SYSTEM_CLOCK.nanoTime(),
                Long.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                true
        );
    }

    public boolean hasTimeRemaining() {
        return this.unbounded || elapsedNanos() < this.maxPreparationNanos;
    }

    public long elapsedNanos() {
        if (this.unbounded) {
            return 0L;
        }
        return Math.max(0L, this.clock.nanoTime() - this.startNanos);
    }

    public long remainingNanos() {
        if (this.unbounded) {
            return Long.MAX_VALUE;
        }
        long elapsed = elapsedNanos();
        if (elapsed >= this.maxPreparationNanos) {
            return 0L;
        }
        return this.maxPreparationNanos - elapsed;
    }

    boolean tryConsumeStartChunkRequest() {
        if (this.remainingStartChunkRequests <= 0) {
            return false;
        }
        this.remainingStartChunkRequests--;
        return true;
    }

    boolean tryConsumeEntryChunkRequest() {
        if (this.remainingEntryChunkRequests <= 0) {
            return false;
        }
        this.remainingEntryChunkRequests--;
        return true;
    }

    int claimCandidateKeysEnumerated(int requested) {
        if (requested < 0) {
            throw new IllegalArgumentException("requested must be non-negative");
        }
        if (this.unbounded) {
            return requested;
        }
        int granted = Math.min(requested, this.remainingCandidateKeysEnumerated);
        this.remainingCandidateKeysEnumerated -= granted;
        return granted;
    }

    int claimLoadedFastPathProbes(int requested) {
        if (requested < 0) {
            throw new IllegalArgumentException("requested must be non-negative");
        }
        if (this.unbounded) {
            return requested;
        }
        int granted = Math.min(requested, this.remainingLoadedFastPathProbes);
        this.remainingLoadedFastPathProbes -= granted;
        return granted;
    }

    void consumeLoadedFastPathProbes(int consumed) {
        this.remainingLoadedFastPathProbes = consumeExact(
                this.remainingLoadedFastPathProbes,
                consumed,
                "loadedFastPathProbes"
        );
    }

    boolean tryStartPersistedScanner(int activePersistedScanners) {
        if (activePersistedScanners < 0) {
            throw new IllegalArgumentException(
                    "activePersistedScanners must be non-negative"
            );
        }
        if (!this.unbounded
                && activePersistedScanners >= this.maxActivePersistedScannersPerLevel) {
            return false;
        }
        if (this.remainingPersistedScannerStarts <= 0) {
            return false;
        }
        this.remainingPersistedScannerStarts--;
        return true;
    }

    int claimPersistedProbeSubmissions(
            int requested,
            int globallyInFlight
    ) {
        if (requested < 0) {
            throw new IllegalArgumentException("requested must be non-negative");
        }
        if (globallyInFlight < 0) {
            throw new IllegalArgumentException(
                    "globallyInFlight must be non-negative"
            );
        }
        if (this.unbounded) {
            return requested;
        }
        int globalCapacity = Math.max(
                0,
                this.maxInFlightPersistedProbesPerLevel - globallyInFlight
        );
        int granted = Math.min(
                requested,
                Math.min(this.remainingPersistedProbeSubmissions, globalCapacity)
        );
        this.remainingPersistedProbeSubmissions -= granted;
        return granted;
    }

    void consumePersistedProbeSubmissions(int consumed) {
        this.remainingPersistedProbeSubmissions = consumeExact(
                this.remainingPersistedProbeSubmissions,
                consumed,
                "persistedProbeSubmissions"
        );
    }

    int claimPersistedProbeCompletionDrains(int requested) {
        if (requested < 0) {
            throw new IllegalArgumentException("requested must be non-negative");
        }
        if (this.unbounded) {
            return requested;
        }
        int granted = Math.min(
                requested,
                this.remainingPersistedProbeCompletionDrains
        );
        this.remainingPersistedProbeCompletionDrains -= granted;
        return granted;
    }

    void consumePersistedProbeCompletionDrains(int consumed) {
        this.remainingPersistedProbeCompletionDrains = consumeExact(
                this.remainingPersistedProbeCompletionDrains,
                consumed,
                "persistedProbeCompletionDrains"
        );
    }

    int claimPersistedProbeResultsClassified(int requested) {
        if (requested < 0) {
            throw new IllegalArgumentException("requested must be non-negative");
        }
        if (this.unbounded) {
            return requested;
        }
        int granted = Math.min(
                requested,
                this.remainingPersistedProbeResultsClassified
        );
        this.remainingPersistedProbeResultsClassified -= granted;
        return granted;
    }

    void consumePersistedProbeResultsClassified(int consumed) {
        this.remainingPersistedProbeResultsClassified = consumeExact(
                this.remainingPersistedProbeResultsClassified,
                consumed,
                "persistedProbeResultsClassified"
        );
    }

    public boolean tryConsumeSafeSpawnCandidate() {
        if (this.unbounded) {
            return true;
        }
        if (this.remainingSafeSpawnCandidates <= 0) {
            return false;
        }
        this.remainingSafeSpawnCandidates--;
        return true;
    }

    int remainingStartChunkRequests() {
        return this.remainingStartChunkRequests;
    }

    int remainingEntryChunkRequests() {
        return this.remainingEntryChunkRequests;
    }

    int remainingCandidateKeysEnumerated() {
        return this.remainingCandidateKeysEnumerated;
    }

    int remainingLoadedFastPathProbes() {
        return this.remainingLoadedFastPathProbes;
    }

    int remainingPersistedScannerStarts() {
        return this.remainingPersistedScannerStarts;
    }

    int remainingPersistedProbeSubmissions() {
        return this.remainingPersistedProbeSubmissions;
    }

    int remainingPersistedProbeCompletionDrains() {
        return this.remainingPersistedProbeCompletionDrains;
    }

    int remainingPersistedProbeResultsClassified() {
        return this.remainingPersistedProbeResultsClassified;
    }

    public int remainingSafeSpawnCandidates() {
        return this.remainingSafeSpawnCandidates;
    }

    int maxActivePersistedScannersPerLevel() {
        return this.maxActivePersistedScannersPerLevel;
    }

    int maxInFlightPersistedProbesPerLevel() {
        return this.maxInFlightPersistedProbesPerLevel;
    }

    int maxGenerationAttempts() {
        return this.maxGenerationAttempts;
    }

    private int consumeExact(
            int remaining,
            int consumed,
            String name
    ) {
        if (consumed < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
        if (this.unbounded) {
            return remaining;
        }
        if (consumed > remaining) {
            throw new IllegalStateException(
                    "Consumed " + consumed + " " + name
                            + " with only " + remaining + " remaining"
            );
        }
        return remaining - consumed;
    }

    private static NanoClock requireClock(NanoClock clock) {
        if (clock == null) {
            throw new NullPointerException("clock");
        }
        return clock;
    }

    private static int requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
        return value;
    }

    private static long requireNonNegative(long value, String name) {
        if (value < 0L) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
        return value;
    }

    @FunctionalInterface
    public interface NanoClock {
        long nanoTime();
    }
}

