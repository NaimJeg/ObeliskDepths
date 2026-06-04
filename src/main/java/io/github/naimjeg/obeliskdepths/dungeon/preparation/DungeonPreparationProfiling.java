package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Strictly observational profiling around preparation runtime operations. */
public final class DungeonPreparationProfiling {
    private DungeonPreparationProfiling() {
    }

    public static void run(
            DungeonPreparationProfiler profiler,
            DungeonPreparationProfiler.Operation operation,
            BooleanSupplier ownerThread,
            String diagnosticName,
            Runnable body
    ) {
        supply(
                access(profiler),
                operation,
                ownerThread,
                diagnosticName,
                DungeonPreparationProfiling::logWarning,
                () -> {
                    body.run();
                    return null;
                }
        );
    }

    public static <T> T supply(
            DungeonPreparationProfiler profiler,
            DungeonPreparationProfiler.Operation operation,
            BooleanSupplier ownerThread,
            String diagnosticName,
            Supplier<T> body
    ) {
        return supply(
                access(profiler),
                operation,
                ownerThread,
                diagnosticName,
                DungeonPreparationProfiling::logWarning,
                body
        );
    }

    static <T> T supply(
            ProfilerAccess profiler,
            DungeonPreparationProfiler.Operation operation,
            BooleanSupplier ownerThread,
            String diagnosticName,
            Supplier<T> body
    ) {
        return supply(
                profiler,
                operation,
                ownerThread,
                diagnosticName,
                DungeonPreparationProfiling::logWarning,
                body
        );
    }

    static <T> T supply(
            ProfilerAccess profiler,
            DungeonPreparationProfiler.Operation operation,
            BooleanSupplier ownerThread,
            String diagnosticName,
            DiagnosticLogger diagnosticLogger,
            Supplier<T> body
    ) {
        Objects.requireNonNull(profiler, "profiler");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(ownerThread, "ownerThread");
        Objects.requireNonNull(diagnosticName, "diagnosticName");
        Objects.requireNonNull(diagnosticLogger, "diagnosticLogger");
        Objects.requireNonNull(body, "body");

        boolean enabled;
        try {
            enabled = profiler.enabled();
        } catch (RuntimeException telemetryFailure) {
            return body.get();
        }
        if (!enabled) {
            return body.get();
        }

        long startNanos;
        try {
            startNanos = profiler.start();
        } catch (RuntimeException telemetryFailure) {
            return body.get();
        }

        try {
            return body.get();
        } finally {
            try {
                profiler.record(operation, startNanos, ownerThread.getAsBoolean());
            } catch (RuntimeException telemetryFailure) {
                logSafely(diagnosticLogger, diagnosticName, telemetryFailure);
            }
        }
    }

    private static void logSafely(
            DiagnosticLogger diagnosticLogger,
            String diagnosticName,
            RuntimeException telemetryFailure
    ) {
        try {
            diagnosticLogger.warn(diagnosticName, telemetryFailure);
        } catch (RuntimeException loggingFailure) {
            // Diagnostics are observational even when the logging backend fails.
        }
    }

    private static void logWarning(
            String diagnosticName,
            RuntimeException telemetryFailure
    ) {
        ObeliskDepths.LOGGER.warn(
                "{} profiler record failed",
                diagnosticName,
                telemetryFailure
        );
    }

    private static ProfilerAccess access(DungeonPreparationProfiler profiler) {
        Objects.requireNonNull(profiler, "profiler");
        return new ProfilerAccess() {
            @Override
            public boolean enabled() {
                return profiler.enabled();
            }

            @Override
            public long start() {
                return profiler.start();
            }

            @Override
            public void record(
                    DungeonPreparationProfiler.Operation operation,
                    long startNanos,
                    boolean ownerThread
            ) {
                profiler.record(operation, startNanos, ownerThread);
            }
        };
    }

    interface ProfilerAccess {
        boolean enabled();

        long start();

        void record(
                DungeonPreparationProfiler.Operation operation,
                long startNanos,
                boolean ownerThread
        );
    }

    @FunctionalInterface
    interface DiagnosticLogger {
        void warn(String diagnosticName, RuntimeException telemetryFailure);
    }
}
