package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import java.util.concurrent.atomic.AtomicLong;

public final class DungeonActivationTransactionMetrics {
    private static final DungeonActivationTransactionMetrics GLOBAL =
            new DungeonActivationTransactionMetrics();

    private final AtomicLong attempts = new AtomicLong();
    private final AtomicLong successes = new AtomicLong();
    private final AtomicLong rollbackAttempts = new AtomicLong();
    private final AtomicLong rollbackFailures = new AtomicLong();
    private final AtomicLong rollbackSteps = new AtomicLong();

    public static DungeonActivationTransactionMetrics global() {
        return GLOBAL;
    }

    void recordAttempt() {
        this.attempts.incrementAndGet();
    }

    void recordSuccess() {
        this.successes.incrementAndGet();
    }

    void recordRollback(
            DungeonActivationCompensationStack.RollbackReport report
    ) {
        this.rollbackAttempts.incrementAndGet();
        this.rollbackFailures.addAndGet(report.failures());
        this.rollbackSteps.addAndGet(report.stepsExecuted());
    }

    public Snapshot snapshot() {
        return new Snapshot(
                this.attempts.get(),
                this.successes.get(),
                this.rollbackAttempts.get(),
                this.rollbackFailures.get(),
                this.rollbackSteps.get()
        );
    }

    public record Snapshot(
            long attempts,
            long successes,
            long rollbackAttempts,
            long rollbackFailures,
            long rollbackStepsExecuted
    ) {
    }
}
