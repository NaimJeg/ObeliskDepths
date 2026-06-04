package io.github.naimjeg.obeliskdepths.dungeon.preparation;

public final class DungeonPreparationTickBudgetTest {
    private DungeonPreparationTickBudgetTest() {
    }

    public static void main(String[] args) {
        negativeLimitsAreRejected();
        requestCountersExhaustWithoutGoingNegative();
        persistedScannerStartsAreSeparateFromProbeSubmissions();
        persistedProbeSubmissionsRespectGlobalInFlight();
        completionDrainsAreSharedAndBounded();
        safeSpawnAllowanceIsClaimedDeterministically();
        zeroAllowancePerformsNoClaims();
        unboundedBudgetGrantsRequestedAllowances();
        deadlineArithmeticSurvivesNanoOverflow();
        elapsedAndRemainingTimeAreDeterministic();
    }

    private static void negativeLimitsAreRejected() {
        MutableClock clock = new MutableClock();
        expectIllegalArgument(() -> budget(clock, -1L, 1, 1, 1, 1, 1, 1, 1, 8, 4),
                "negative max nanos");
        expectIllegalArgument(() -> budget(clock, 1L, -1, 1, 1, 1, 1, 1, 1, 8, 4),
                "negative start requests");
        expectIllegalArgument(() -> budget(clock, 1L, 1, -1, 1, 1, 1, 1, 1, 8, 4),
                "negative entry requests");
        expectIllegalArgument(() -> budget(clock, 1L, 1, 1, -1, 1, 1, 1, 1, 8, 4),
                "negative scanner starts");
        expectIllegalArgument(() -> budget(clock, 1L, 1, 1, 1, -1, 1, 1, 1, 8, 4),
                "negative probe submissions");
        expectIllegalArgument(() -> budget(clock, 1L, 1, 1, 1, 1, -1, 1, 1, 8, 4),
                "negative completion drains");
        expectIllegalArgument(() -> budget(clock, 1L, 1, 1, 1, 1, 1, -1, 1, 8, 4),
                "negative safe-spawn allowance");
        expectIllegalArgument(() -> budget(clock, 1L, 1, 1, 1, 1, 1, 1, -1, 8, 4),
                "negative active scanner limit");
        expectIllegalArgument(() -> budget(clock, 1L, 1, 1, 1, 1, 1, 1, 1, -1, 4),
                "negative in-flight limit");
        expectIllegalArgument(() -> budget(clock, 1L, 1, 1, 1, 1, 1, 1, 1, 8, -1),
                "negative generation attempts");
    }

    private static void requestCountersExhaustWithoutGoingNegative() {
        DungeonPreparationTickBudget budget =
                budget(new MutableClock(), 100L, 1, 2, 0, 0, 0, 0, 1, 8, 4);

        check(budget.tryConsumeStartChunkRequest(), "start: first");
        check(!budget.tryConsumeStartChunkRequest(), "start: exhausted");
        check(budget.remainingStartChunkRequests() == 0, "start: non-negative");

        check(budget.tryConsumeEntryChunkRequest(), "entry: first");
        check(budget.tryConsumeEntryChunkRequest(), "entry: second");
        check(!budget.tryConsumeEntryChunkRequest(), "entry: exhausted");
        check(budget.remainingEntryChunkRequests() == 0, "entry: non-negative");
    }

    private static void persistedScannerStartsAreSeparateFromProbeSubmissions() {
        DungeonPreparationTickBudget budget =
                budget(new MutableClock(), 100L, 0, 0, 1, 8, 0, 0, 1, 8, 4);

        check(budget.tryStartPersistedScanner(0), "scanner: first starts");
        check(!budget.tryStartPersistedScanner(0), "scanner: start counter exhausted");
        check(budget.remainingPersistedScannerStarts() == 0,
                "scanner: starts non-negative");
        check(budget.claimPersistedProbeSubmissions(8, 0) == 8,
                "scanner: probe submissions remain independent");
        check(budget.remainingPersistedProbeSubmissions() == 0,
                "scanner: submissions exhausted");

        DungeonPreparationTickBudget activeLimitBudget =
                budget(new MutableClock(), 100L, 0, 0, 1, 8, 0, 0, 1, 8, 4);
        check(!activeLimitBudget.tryStartPersistedScanner(1),
                "scanner: active limit blocks second scanner");
    }

    private static void persistedProbeSubmissionsRespectGlobalInFlight() {
        DungeonPreparationTickBudget budget =
                budget(new MutableClock(), 100L, 0, 0, 0, 8, 0, 0, 1, 8, 4);

        check(budget.claimPersistedProbeSubmissions(8, 0) == 8,
                "submissions: one scanner may claim eight");
        check(budget.remainingPersistedProbeSubmissions() == 0,
                "submissions: counter exhausted");

        DungeonPreparationTickBudget constrained =
                budget(new MutableClock(), 100L, 0, 0, 0, 8, 0, 0, 1, 8, 4);
        check(constrained.claimPersistedProbeSubmissions(8, 6) == 2,
                "submissions: global in-flight capacity caps grant");
        check(constrained.remainingPersistedProbeSubmissions() == 6,
                "submissions: ungranted starts remain");
        check(constrained.claimPersistedProbeSubmissions(1, 8) == 0,
                "submissions: no capacity at global limit");
    }

    private static void completionDrainsAreSharedAndBounded() {
        DungeonPreparationTickBudget budget =
                budget(new MutableClock(), 100L, 0, 0, 0, 0, 4, 0, 1, 8, 4);

        check(budget.claimPersistedProbeCompletionDrains(3) == 3,
                "drains: first claim");
        check(budget.remainingPersistedProbeCompletionDrains() == 1,
                "drains: shared remaining");
        check(budget.claimPersistedProbeCompletionDrains(3) == 1,
                "drains: second claim gets only shared remainder");
        check(budget.claimPersistedProbeCompletionDrains(1) == 0,
                "drains: exhausted");
        check(budget.remainingPersistedProbeCompletionDrains() == 0,
                "drains: non-negative");
    }

    private static void safeSpawnAllowanceIsClaimedDeterministically() {
        DungeonPreparationTickBudget budget =
                budget(new MutableClock(), 100L, 0, 0, 0, 0, 0, 5, 1, 8, 4);
        check(budget.tryConsumeSafeSpawnCandidate(), "safe-spawn: first");
        check(budget.tryConsumeSafeSpawnCandidate(), "safe-spawn: second");
        check(budget.remainingSafeSpawnCandidates() == 3, "safe-spawn: remaining");
        check(budget.tryConsumeSafeSpawnCandidate(), "safe-spawn: third");
        check(budget.tryConsumeSafeSpawnCandidate(), "safe-spawn: fourth");
        check(budget.tryConsumeSafeSpawnCandidate(), "safe-spawn: fifth");
        check(!budget.tryConsumeSafeSpawnCandidate(), "safe-spawn: exhausted");
        check(budget.remainingSafeSpawnCandidates() == 0,
                "safe-spawn: non-negative");
    }

    private static void zeroAllowancePerformsNoClaims() {
        DungeonPreparationTickBudget budget =
                budget(new MutableClock(), 100L, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        check(!budget.tryConsumeStartChunkRequest(), "zero: start");
        check(!budget.tryConsumeEntryChunkRequest(), "zero: entry");
        check(!budget.tryStartPersistedScanner(0), "zero: scanner");
        check(budget.claimPersistedProbeSubmissions(10, 0) == 0,
                "zero: submissions");
        check(budget.claimPersistedProbeCompletionDrains(10) == 0,
                "zero: drains");
        check(!budget.tryConsumeSafeSpawnCandidate(), "zero: safe-spawn");
        check(budget.maxActivePersistedScannersPerLevel() == 0,
                "zero: scanner limit");
        check(budget.maxInFlightPersistedProbesPerLevel() == 0,
                "zero: in-flight limit");
        check(budget.maxGenerationAttempts() == 0, "zero: generation");
    }

    private static void unboundedBudgetGrantsRequestedAllowances() {
        DungeonPreparationTickBudget budget =
                DungeonPreparationTickBudget.unlimitedForTests();
        check(budget.hasTimeRemaining(), "unbounded: time");
        check(budget.elapsedNanos() == 0L, "unbounded: elapsed");
        check(budget.remainingNanos() == Long.MAX_VALUE, "unbounded: remaining");
        check(budget.tryConsumeStartChunkRequest(), "unbounded: start");
        check(budget.tryConsumeEntryChunkRequest(), "unbounded: entry");
        check(budget.tryStartPersistedScanner(Integer.MAX_VALUE),
                "unbounded: scanner");
        check(budget.claimPersistedProbeSubmissions(32, Integer.MAX_VALUE) == 32,
                "unbounded: submissions");
        check(budget.claimPersistedProbeCompletionDrains(32) == 32,
                "unbounded: drains");
        check(budget.tryConsumeSafeSpawnCandidate(), "unbounded: safe");
    }

    private static void deadlineArithmeticSurvivesNanoOverflow() {
        MutableClock clock = new MutableClock();
        clock.set(Long.MAX_VALUE - 5L);
        DungeonPreparationTickBudget budget =
                budget(clock, 20L, 0, 0, 0, 0, 0, 0, 1, 8, 4);
        clock.set(Long.MIN_VALUE + 10L);
        check(budget.elapsedNanos() == 16L, "overflow: elapsed");
        check(budget.remainingNanos() == 4L, "overflow: remaining");
        check(budget.hasTimeRemaining(), "overflow: time remains");
        clock.set(Long.MIN_VALUE + 20L);
        check(budget.elapsedNanos() == 26L, "overflow: elapsed exhausted");
        check(budget.remainingNanos() == 0L, "overflow: no remaining");
        check(!budget.hasTimeRemaining(), "overflow: exhausted");
    }

    private static void elapsedAndRemainingTimeAreDeterministic() {
        MutableClock clock = new MutableClock();
        DungeonPreparationTickBudget budget =
                budget(clock, 50L, 0, 0, 0, 0, 0, 0, 1, 8, 4);
        check(budget.elapsedNanos() == 0L, "time: initial elapsed");
        check(budget.remainingNanos() == 50L, "time: initial remaining");
        clock.advance(20L);
        check(budget.elapsedNanos() == 20L, "time: elapsed");
        check(budget.remainingNanos() == 30L, "time: remaining");
        check(budget.hasTimeRemaining(), "time: remains");
        clock.advance(30L);
        check(budget.elapsedNanos() == 50L, "time: exact elapsed");
        check(budget.remainingNanos() == 0L, "time: exact remaining");
        check(!budget.hasTimeRemaining(), "time: exhausted at limit");
        clock.set(10L);
        check(budget.elapsedNanos() == 10L, "time: backward clock clamps");
    }

    private static DungeonPreparationTickBudget budget(
            MutableClock clock,
            long maxPreparationNanos,
            int startChunkRequests,
            int entryChunkRequests,
            int persistedScannerStarts,
            int persistedProbeSubmissions,
            int persistedProbeCompletionDrains,
            int safeSpawnCandidates,
            int maxActivePersistedScannersPerLevel,
            int maxInFlightPersistedProbesPerLevel,
            int maxGenerationAttempts
    ) {
        return DungeonPreparationTickBudget.boundedForTests(
                clock,
                maxPreparationNanos,
                startChunkRequests,
                entryChunkRequests,
                100,
                100,
                persistedScannerStarts,
                persistedProbeSubmissions,
                persistedProbeCompletionDrains,
                100,
                safeSpawnCandidates,
                maxActivePersistedScannersPerLevel,
                maxInFlightPersistedProbesPerLevel,
                maxGenerationAttempts
        );
    }

    private static void expectIllegalArgument(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class MutableClock
            implements DungeonPreparationTickBudget.NanoClock {
        private long nanos;

        @Override
        public long nanoTime() {
            return this.nanos;
        }

        private void set(long nanos) {
            this.nanos = nanos;
        }

        private void advance(long nanos) {
            this.nanos += nanos;
        }
    }
}
