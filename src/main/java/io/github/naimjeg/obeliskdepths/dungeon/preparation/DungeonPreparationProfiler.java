package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;

public final class DungeonPreparationProfiler {
    public static final String ENABLED_PROPERTY =
            "obeliskdepths.profilePreparation";

    private static final DungeonPreparationProfiler GLOBAL =
            new DungeonPreparationProfiler(
                    Boolean.getBoolean(ENABLED_PROPERTY),
                    System::nanoTime
            );

    private final boolean enabled;
    private final NanoClock clock;
    private final OperationStats[] stats;

    public DungeonPreparationProfiler(boolean enabled, NanoClock clock) {
        this.enabled = enabled;
        this.clock = Objects.requireNonNull(clock, "clock");
        Operation[] operations = Operation.values();
        this.stats = new OperationStats[operations.length];
        for (Operation operation : operations) {
            this.stats[operation.ordinal()] = new OperationStats(operation);
        }
    }

    public static DungeonPreparationProfiler global() {
        return GLOBAL;
    }

    public boolean enabled() {
        return this.enabled;
    }

    public long nanoTime() {
        return this.clock.nanoTime();
    }

    public long start() {
        return this.enabled ? this.clock.nanoTime() : 0L;
    }

    public void record(Operation operation, long startNanos, boolean ownerThread) {
        if (!this.enabled) {
            return;
        }
        long elapsed = Math.max(0L, this.clock.nanoTime() - startNanos);
        this.recordElapsed(operation, elapsed, ownerThread);
    }

    public void recordElapsed(
            Operation operation,
            long elapsedNanos,
            boolean ownerThread
    ) {
        if (!this.enabled) {
            return;
        }
        Objects.requireNonNull(operation, "operation");
        this.stats[operation.ordinal()].record(
                Math.max(0L, elapsedNanos),
                ownerThread
        );
    }

    public List<Snapshot> snapshots() {
        ArrayList<Snapshot> snapshots = new ArrayList<>(this.stats.length);
        for (OperationStats stat : this.stats) {
            snapshots.add(stat.snapshot());
        }
        return List.copyOf(snapshots);
    }

    public void flushToLog() {
        if (!this.enabled) {
            return;
        }
        for (Snapshot snapshot : snapshots()) {
            if (snapshot.count() == 0L) {
                continue;
            }
            ObeliskDepths.LOGGER.info(
                    "Dungeon preparation profile: operation={}, count={}, totalNanos={}, maxNanos={}, ownerThreadCount={}, offThreadCount={}",
                    snapshot.operation().label(),
                    snapshot.count(),
                    snapshot.totalNanos(),
                    snapshot.maxNanos(),
                    snapshot.ownerThreadCount(),
                    snapshot.offThreadCount()
            );
        }
    }

    @FunctionalInterface
    public interface NanoClock {
        long nanoTime();
    }

    public enum Operation {
        RUNTIME_TICK("DungeonPreparationRuntime.tick"),
        JOB_EXECUTOR_TICK("DungeonPreparationJobExecutor.tick"),
        CREATE_CANDIDATE_CURSOR("DungeonStructureLocator.candidateCursor"),
        SCAN_CHUNK_SUBMISSION("scanChunk submission"),
        SCAN_CHUNK_COMPLETION_LATENCY("scanChunk completion latency"),
        ADD_TICKET_SUBMISSION("addTicketAndLoadWithRadius submission"),
        CHUNK_LOAD_COMPLETION_LATENCY("chunk load completion latency"),
        READ_LOADED_SITE("readLoadedSite"),
        STRUCTURE_SITE_PROJECTION("structure-site projection"),
        PREPARATION_SAFE_SPAWN_ADVANCE("preparation safe-spawn advance"),
        RECOVERY_SAFE_SPAWN_ADVANCE("recovery safe-spawn advance"),
        PREPARATION_SAFE_SPAWN_CANDIDATE("preparation safe-spawn candidate"),
        RECOVERY_SAFE_SPAWN_CANDIDATE("recovery safe-spawn candidate"),
        PREPARATION_SAFE_SPAWN_BUDGET_EXHAUSTED("preparation safe-spawn budget exhausted"),
        RECOVERY_SAFE_SPAWN_BUDGET_EXHAUSTED("recovery safe-spawn budget exhausted"),
        PREPARATION_SAFE_SPAWN_FOUND("preparation safe-spawn found"),
        RECOVERY_SAFE_SPAWN_FOUND("recovery safe-spawn found"),
        PREPARATION_SAFE_SPAWN_EXHAUSTED("preparation safe-spawn exhausted"),
        RECOVERY_SAFE_SPAWN_EXHAUSTED("recovery safe-spawn exhausted"),
        ACTIVATION_COMMIT("activation commit"),
        COMMIT_PREFLIGHT("commit preflight"),
        COMMIT_REVALIDATE("commit revalidate"),
        RESERVE_SITE("commit reserve site"),
        CREATE_PORTAL_SESSION("commit create portal session"),
        ACQUIRE_DUNGEON_SESSION("commit acquire dungeon session"),
        ENSURE_PORTAL_ENTITY("commit ensure portal entity"),
        REGISTER_PREPARED_ENTRY("commit register prepared entry"),
        RELEASE_SITE_CLAIM("commit release site claim"),
        CONSUME_TRIBUTE("commit consume tribute"),
        COMMIT_ROLLBACK("commit rollback"),
        PORTAL_ENTRY("portal entry"),
        RECOVERY_JOB_TICK("recovery-job tick");

        private final String label;

        Operation(String label) {
            this.label = label;
        }

        public String label() {
            return this.label;
        }
    }

    public record Snapshot(
            Operation operation,
            long count,
            long totalNanos,
            long maxNanos,
            long ownerThreadCount,
            long offThreadCount
    ) {
        public double averageNanos() {
            return this.count == 0L
                    ? 0.0D
                    : (double)this.totalNanos / (double)this.count;
        }

        @Override
        public String toString() {
            return String.format(
                    Locale.ROOT,
                    "%s count=%d totalNanos=%d maxNanos=%d ownerThreadCount=%d offThreadCount=%d",
                    this.operation.label(),
                    this.count,
                    this.totalNanos,
                    this.maxNanos,
                    this.ownerThreadCount,
                    this.offThreadCount
            );
        }
    }

    private static final class OperationStats {
        private final Operation operation;
        private final LongAdder count = new LongAdder();
        private final LongAdder totalNanos = new LongAdder();
        private final LongAccumulator maxNanos =
                new LongAccumulator(Long::max, 0L);
        private final AtomicLong ownerThreadCount = new AtomicLong();
        private final AtomicLong offThreadCount = new AtomicLong();

        private OperationStats(Operation operation) {
            this.operation = operation;
        }

        private void record(long elapsedNanos, boolean ownerThread) {
            this.count.increment();
            this.totalNanos.add(elapsedNanos);
            this.maxNanos.accumulate(elapsedNanos);
            if (ownerThread) {
                this.ownerThreadCount.incrementAndGet();
            } else {
                this.offThreadCount.incrementAndGet();
            }
        }

        private Snapshot snapshot() {
            return new Snapshot(
                    this.operation,
                    this.count.sum(),
                    this.totalNanos.sum(),
                    this.maxNanos.get(),
                    this.ownerThreadCount.get(),
                    this.offThreadCount.get()
            );
        }
    }
}
