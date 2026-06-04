package io.github.naimjeg.obeliskdepths.dungeon.preparation;

public final class DungeonPreparationLimits {
    public static final int MAX_ACTIVE_PREPARATION_JOBS_PER_LEVEL = 4;
    public static final int MAX_ACTIVE_RECOVERY_JOBS_PER_LEVEL = 4;

    public static final int START_CHUNK_REQUESTS_PER_LEVEL_TICK = 1;
    public static final int ENTRY_CHUNK_REQUESTS_PER_LEVEL_TICK = 4;
    public static final int CANDIDATE_KEYS_ENUMERATED_PER_LEVEL_TICK = 16;
    public static final int LOADED_FAST_PATH_PROBES_PER_LEVEL_TICK = 8;
    public static final int MAX_ACTIVE_PERSISTED_SCANNERS_PER_LEVEL = 1;
    public static final int MAX_IN_FLIGHT_PERSISTED_PROBES_PER_LEVEL = 8;
    public static final int PERSISTED_SCANNER_STARTS_PER_LEVEL_TICK = 1;
    public static final int PERSISTED_PROBE_SUBMISSIONS_PER_LEVEL_TICK = 8;
    public static final int PERSISTED_PROBE_COMPLETION_DRAINS_PER_LEVEL_TICK = 4;
    public static final int PERSISTED_PROBE_RESULTS_CLASSIFIED_PER_LEVEL_TICK = 16;
    public static final int PENDING_TICKET_RELEASE_RETRIES_PER_LEVEL_TICK = 4;
    public static final int POST_TELEPORT_HANDOFFS_PER_LEVEL_TICK = 4;

    public static final int MAX_GENERATION_ATTEMPTS = 4;

    public static final int SAFE_SPAWN_CANDIDATES_PER_LEVEL_TICK = 64;
    public static final long MAX_PREPARATION_NANOS_PER_LEVEL_TICK =
            2_000_000L;

    public static final long JOB_TIMEOUT_TICKS = 20L * 30L;
    public static final long POST_TELEPORT_HANDOFF_TIMEOUT_TICKS = 20L * 10L;

    private DungeonPreparationLimits() {
    }
}
