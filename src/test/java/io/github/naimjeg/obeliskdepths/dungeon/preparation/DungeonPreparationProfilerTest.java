package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import java.util.List;
import java.util.concurrent.CountDownLatch;

public final class DungeonPreparationProfilerTest {
    private DungeonPreparationProfilerTest() {
    }

    public static void main(String[] args) throws Exception {
        disabledByDefaultPropertyIsFalse();
        disabledProfilerDoesNotRecord();
        enabledProfilerUsesInjectedClock();
        negativeElapsedTimeIsClamped();
        concurrentRecordingIsAggregated();
    }

    private static void disabledByDefaultPropertyIsFalse() {
        check(
                !Boolean.getBoolean(DungeonPreparationProfiler.ENABLED_PROPERTY),
                "property: disabled by default"
        );
    }

    private static void disabledProfilerDoesNotRecord() {
        MutableClock clock = new MutableClock();
        DungeonPreparationProfiler profiler =
                new DungeonPreparationProfiler(false, clock);
        long start = profiler.start();
        clock.advance(10L);
        profiler.record(
                DungeonPreparationProfiler.Operation.RUNTIME_TICK,
                start,
                true
        );

        DungeonPreparationProfiler.Snapshot snapshot = snapshot(
                profiler,
                DungeonPreparationProfiler.Operation.RUNTIME_TICK
        );
        check(snapshot.count() == 0L, "disabled: count remains zero");
        check(snapshot.totalNanos() == 0L, "disabled: total remains zero");
        check(snapshot.maxNanos() == 0L, "disabled: max remains zero");
    }

    private static void enabledProfilerUsesInjectedClock() {
        MutableClock clock = new MutableClock();
        DungeonPreparationProfiler profiler =
                new DungeonPreparationProfiler(true, clock);

        long first = profiler.start();
        clock.advance(25L);
        profiler.record(
                DungeonPreparationProfiler.Operation.JOB_EXECUTOR_TICK,
                first,
                true
        );

        long second = profiler.start();
        clock.advance(10L);
        profiler.record(
                DungeonPreparationProfiler.Operation.JOB_EXECUTOR_TICK,
                second,
                false
        );

        DungeonPreparationProfiler.Snapshot snapshot = snapshot(
                profiler,
                DungeonPreparationProfiler.Operation.JOB_EXECUTOR_TICK
        );
        check(snapshot.count() == 2L, "enabled: count");
        check(snapshot.totalNanos() == 35L, "enabled: total");
        check(snapshot.maxNanos() == 25L, "enabled: max");
        check(snapshot.ownerThreadCount() == 1L, "enabled: owner count");
        check(snapshot.offThreadCount() == 1L, "enabled: off-thread count");
        check(snapshot.averageNanos() == 17.5D, "enabled: average");
    }

    private static void negativeElapsedTimeIsClamped() {
        MutableClock clock = new MutableClock();
        DungeonPreparationProfiler profiler =
                new DungeonPreparationProfiler(true, clock);
        clock.set(100L);
        profiler.record(
                DungeonPreparationProfiler.Operation.PREPARATION_SAFE_SPAWN_ADVANCE,
                120L,
                true
        );

        DungeonPreparationProfiler.Snapshot snapshot = snapshot(
                profiler,
                DungeonPreparationProfiler.Operation.PREPARATION_SAFE_SPAWN_ADVANCE
        );
        check(snapshot.count() == 1L, "clamp: count");
        check(snapshot.totalNanos() == 0L, "clamp: total");
        check(snapshot.maxNanos() == 0L, "clamp: max");
    }

    private static void concurrentRecordingIsAggregated() throws Exception {
        DungeonPreparationProfiler profiler =
                new DungeonPreparationProfiler(true, () -> 0L);
        int threadCount = 4;
        int iterations = 1_000;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        for (int i = 0; i < threadCount; i++) {
            final boolean ownerThread = i % 2 == 0;
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < iterations; j++) {
                        profiler.recordElapsed(
                                DungeonPreparationProfiler.Operation.PORTAL_ENTRY,
                                3L,
                                ownerThread
                        );
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                } finally {
                    done.countDown();
                }
            });
            thread.start();
        }
        start.countDown();
        done.await();

        DungeonPreparationProfiler.Snapshot snapshot = snapshot(
                profiler,
                DungeonPreparationProfiler.Operation.PORTAL_ENTRY
        );
        check(
                snapshot.count() == (long)threadCount * iterations,
                "concurrent: count"
        );
        check(
                snapshot.totalNanos() == (long)threadCount * iterations * 3L,
                "concurrent: total"
        );
        check(snapshot.maxNanos() == 3L, "concurrent: max");
        check(snapshot.ownerThreadCount() == 2_000L, "concurrent: owner");
        check(snapshot.offThreadCount() == 2_000L, "concurrent: off-thread");
    }

    private static DungeonPreparationProfiler.Snapshot snapshot(
            DungeonPreparationProfiler profiler,
            DungeonPreparationProfiler.Operation operation
    ) {
        List<DungeonPreparationProfiler.Snapshot> snapshots = profiler.snapshots();
        return snapshots.stream()
                .filter(snapshot -> snapshot.operation() == operation)
                .findFirst()
                .orElseThrow();
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class MutableClock
            implements DungeonPreparationProfiler.NanoClock {
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
