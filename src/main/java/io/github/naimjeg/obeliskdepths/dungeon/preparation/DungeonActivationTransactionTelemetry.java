package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import java.util.Objects;

/** Observational transaction telemetry; implementations must not mutate business state. */
interface DungeonActivationTransactionTelemetry {
    long profilerStart();

    void profilerRecord(
            DungeonPreparationProfiler.Operation operation,
            long startNanos
    );

    void recordAttempt();

    void recordSuccess();

    void recordRollback(
            DungeonActivationCompensationStack.RollbackReport report
    );

    static DungeonActivationTransactionTelemetry production(
            DungeonActivationTransactionMetrics metrics
    ) {
        return new Production(Objects.requireNonNull(metrics, "metrics"));
    }

    final class Production implements DungeonActivationTransactionTelemetry {
        private final DungeonActivationTransactionMetrics metrics;

        private Production(DungeonActivationTransactionMetrics metrics) {
            this.metrics = metrics;
        }

        @Override
        public long profilerStart() {
            return DungeonPreparationProfiler.global().start();
        }

        @Override
        public void profilerRecord(
                DungeonPreparationProfiler.Operation operation,
                long startNanos
        ) {
            DungeonPreparationProfiler.global().record(
                    operation, startNanos, true
            );
        }

        @Override
        public void recordAttempt() {
            this.metrics.recordAttempt();
        }

        @Override
        public void recordSuccess() {
            this.metrics.recordSuccess();
        }

        @Override
        public void recordRollback(
                DungeonActivationCompensationStack.RollbackReport report
        ) {
            this.metrics.recordRollback(report);
        }
    }
}
