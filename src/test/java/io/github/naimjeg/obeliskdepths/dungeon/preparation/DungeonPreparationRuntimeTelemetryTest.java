package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import java.util.concurrent.atomic.AtomicInteger;

public final class DungeonPreparationRuntimeTelemetryTest {
    private DungeonPreparationRuntimeTelemetryTest() {
    }

    public static void main(String[] args) {
        enabledFailureDoesNotSkipTickBody();
        startFailureDoesNotSkipTickBody();
        recordFailureDoesNotEscapeAfterTickBody();
        loggingFailureWhileReportingRecordFailureDoesNotEscape();
        businessRuntimeFailurePropagates();
        businessErrorPropagates();
        disabledAndEnabledPathsExecuteExactlyOnce();
    }

    private static void enabledFailureDoesNotSkipTickBody() {
        FakeProfiler profiler = new FakeProfiler();
        profiler.enabledFailure = new IllegalStateException("enabled");
        AtomicInteger ticks = new AtomicInteger();

        run(profiler, ticks::incrementAndGet);

        check(ticks.get() == 1, "enabled failure: body once");
        check(profiler.recordCalls == 0, "enabled failure: no record");
    }

    private static void startFailureDoesNotSkipTickBody() {
        FakeProfiler profiler = new FakeProfiler();
        profiler.startFailure = new IllegalStateException("start");
        AtomicInteger ticks = new AtomicInteger();

        run(profiler, ticks::incrementAndGet);

        check(ticks.get() == 1, "start failure: body once");
        check(profiler.recordCalls == 0, "start failure: no record");
    }

    private static void recordFailureDoesNotEscapeAfterTickBody() {
        FakeProfiler profiler = new FakeProfiler();
        profiler.recordFailure = new IllegalStateException("record");
        AtomicInteger ticks = new AtomicInteger();

        run(profiler, ticks::incrementAndGet);

        check(ticks.get() == 1, "record failure: body once");
        check(profiler.recordCalls == 1, "record failure: attempted once");
    }

    private static void loggingFailureWhileReportingRecordFailureDoesNotEscape() {
        FakeProfiler profiler = new FakeProfiler();
        profiler.recordFailure = new IllegalStateException("record");
        AtomicInteger ticks = new AtomicInteger();
        AtomicInteger loggingAttempts = new AtomicInteger();

        DungeonPreparationProfiling.supply(
                profiler,
                DungeonPreparationProfiler.Operation.RUNTIME_TICK,
                () -> true,
                "Runtime telemetry test",
                (name, failure) -> {
                    loggingAttempts.incrementAndGet();
                    throw new IllegalStateException("logger");
                },
                () -> {
                    ticks.incrementAndGet();
                    return null;
                }
        );

        check(ticks.get() == 1, "logger failure: body once");
        check(loggingAttempts.get() == 1, "logger failure: logging attempted once");
    }

    private static void businessRuntimeFailurePropagates() {
        FakeProfiler profiler = new FakeProfiler();
        RuntimeException business = new IllegalArgumentException("business");
        try {
            run(profiler, () -> {
                throw business;
            });
            check(false, "business runtime: should propagate");
        } catch (RuntimeException observed) {
            check(observed == business, "business runtime: identity preserved");
        }
        check(profiler.recordCalls == 1,
                "business runtime: observational record still attempted");
    }

    private static void businessErrorPropagates() {
        FakeProfiler profiler = new FakeProfiler();
        Error business = new AssertionError("business error");
        try {
            run(profiler, () -> {
                throw business;
            });
            check(false, "business Error: should propagate");
        } catch (Error observed) {
            check(observed == business, "business Error: identity preserved");
        }
        check(profiler.recordCalls == 1,
                "business Error: observational record still attempted");
    }

    private static void disabledAndEnabledPathsExecuteExactlyOnce() {
        FakeProfiler disabled = new FakeProfiler();
        disabled.enabled = false;
        AtomicInteger disabledTicks = new AtomicInteger();
        run(disabled, disabledTicks::incrementAndGet);
        check(disabledTicks.get() == 1, "disabled: body once");
        check(disabled.recordCalls == 0, "disabled: no record");

        FakeProfiler enabled = new FakeProfiler();
        AtomicInteger enabledTicks = new AtomicInteger();
        run(enabled, enabledTicks::incrementAndGet);
        check(enabledTicks.get() == 1, "enabled: body once");
        check(enabled.recordCalls == 1, "enabled: record once");
    }

    private static void run(FakeProfiler profiler, Runnable body) {
        DungeonPreparationProfiling.supply(
                profiler,
                DungeonPreparationProfiler.Operation.RUNTIME_TICK,
                () -> true,
                "Runtime telemetry test",
                () -> {
                    body.run();
                    return null;
                }
        );
    }

    private static final class FakeProfiler
            implements DungeonPreparationProfiling.ProfilerAccess {
        boolean enabled = true;
        RuntimeException enabledFailure;
        RuntimeException startFailure;
        RuntimeException recordFailure;
        int recordCalls;

        @Override
        public boolean enabled() {
            if (this.enabledFailure != null) {
                throw this.enabledFailure;
            }
            return this.enabled;
        }

        @Override
        public long start() {
            if (this.startFailure != null) {
                throw this.startFailure;
            }
            return 17L;
        }

        @Override
        public void record(
                DungeonPreparationProfiler.Operation operation,
                long startNanos,
                boolean ownerThread
        ) {
            this.recordCalls++;
            if (this.recordFailure != null) {
                throw this.recordFailure;
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
