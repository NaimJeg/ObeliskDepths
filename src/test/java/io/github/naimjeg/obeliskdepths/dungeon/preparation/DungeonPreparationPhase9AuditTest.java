package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class DungeonPreparationPhase9AuditTest {
    private static final Path MAIN = Path.of("src", "main", "java");

    private DungeonPreparationPhase9AuditTest() {
    }

    public static void main(String[] args) throws Exception {
        cancellationMatrixHasAuthoritativeHooks();
        recoveryAndPreparedEntryLifecycleIsBounded();
        ticketDebtRemainsObservable();
        periodicWorkHasHardBoundsAndFairScheduling();
        preparationCallGraphHasNoForbiddenOperations();
    }

    private static void cancellationMatrixHasAuthoritativeHooks()
            throws IOException {
        String runtime = read("dungeon/preparation/DungeonPreparationRuntime.java");
        String playerEvents = read("event/DungeonPlayerEvents.java");
        String menu = read("menu/ObeliskPortalMenu.java");
        String executor = read("dungeon/preparation/DungeonPreparationJobExecutor.java");
        String serverBackend = read(
                "dungeon/preparation/ServerDungeonPreparationExecutionBackend.java"
        );
        String failurePaths = executor + serverBackend;

        for (String token : List.of(
                "PLAYER_DISCONNECTED", "PLAYER_DIED", "PLAYER_DIMENSION_CHANGED",
                "MENU_CLOSED", "OBELISK_INVALID", "PLAYER_MOVED_TOO_FAR", "TIMEOUT"
        )) {
            check(runtime.contains(token) || playerEvents.contains(token),
                    "cancellation hook: " + token);
        }
        check(menu.contains("DungeonPreparationCancellationReason.MENU_CLOSED"),
                "menu close cancellation");
        check(runtime.contains("MAX_ACTIVE_PREPARATION_JOBS_PER_LEVEL"),
                "duplicate/admission limit");
        for (String failure : List.of(
                "CHUNK_LOAD_FAILED", "STRUCTURE_START_MISSING",
                "STRUCTURE_START_INVALID",
                "SITE_CLAIM_LOST"
        )) {
            check(failurePaths.contains(failure),
                    "preparation terminal failure: " + failure);
        }
        check(executor.contains("no safe spawn position")
                        && executor.contains("retryOrFail(job, context"),
                "safe-spawn exhaustion retries or terminates");
        check(runtime.contains("clearAllActive"), "level/server clear terminates jobs");
        check(runtime.contains("cancelAllContexts"), "clear invalidates logical work");
        check(runtime.contains("claimManager.clearAll"), "clear releases claims");
        check(runtime.contains("leaseManager.clear"), "clear releases tickets");
    }

    private static void recoveryAndPreparedEntryLifecycleIsBounded()
            throws IOException {
        String runtime = read("dungeon/preparation/DungeonPreparationRuntime.java");
        String registry = read("dungeon/preparation/DungeonPreparedEntryRegistry.java");
        String portal = read("dungeon/portal/DungeonPortalSessionLifecycle.java");
        String entry = read("dungeon/interaction/DungeonPortalEntryService.java");

        check(runtime.contains("recoveryJobs.containsKey(session.id())"),
                "one recovery per portal session");
        check(runtime.contains("MAX_ACTIVE_RECOVERY_JOBS_PER_LEVEL"),
                "recovery admission bound");
        check(runtime.contains("recoveryRoundRobinCursor"),
                "recovery fairness cursor");
        check(runtime.contains("persistentStateStillValid"),
                "recovery revalidates persistent state");
        check(registry.contains("removeAndCloseExact"),
                "exact removal protects replacement entries");
        check(registry.contains("nextMaintenanceBatch"),
                "prepared reconciliation obtains bounded batch");
        check(portal.contains("closePreparedEntryAndReport"),
                "portal/session teardown closes prepared entry");
        check(entry.contains("beginPreparedEntryHandoffAfterTeleport")
                        && runtime.contains("chunkMap.isChunkTracked"),
                "successful entry retains leases through player tracking handoff");
        check(runtime.contains("clearRecoveryJobs")
                        && runtime.contains("preparedEntryRegistry.clearAll"),
                "level/server clear closes recovery and prepared leases");
    }

    private static void ticketDebtRemainsObservable() throws IOException {
        String manager = read(
                "dungeon/preparation/chunk/DungeonChunkLeaseManager.java"
        );
        String metrics = read(
                "dungeon/preparation/DungeonPreparationRuntimeMetrics.java"
        );
        check(manager.contains("pendingReleaseFailureCount"),
                "ticket cleanup failure counter");
        check(manager.contains("TERMINAL_RELEASE_DRAIN_PASSES")
                        && manager.contains("TERMINAL_RELEASE_ATTEMPTS_PER_PASS"),
                "terminal cleanup has fixed pass and attempt caps");
        check(manager.contains("TerminalCleanupResult")
                        && manager.contains("unresolvedPositions"),
                "terminal cleanup explicitly reports bounded debt");
        check(metrics.contains("pendingPhysicalTicketReleases"),
                "metrics distinguish pending physical releases");
        check(metrics.contains("physicalTicketReleaseFailures"),
                "metrics expose cleanup failures");
        check(metrics.contains("terminalUnresolvedPhysicalTicketDebt"),
                "metrics do not hide discarded terminal debt");
        String runtime = read("dungeon/preparation/DungeonPreparationRuntime.java");
        int report = runtime.indexOf("reportTerminalTicketDebt(ticketResult[0])");
        int remove = runtime.indexOf("LEVEL_RUNTIMES.remove(this.level, this)");
        check(report >= 0 && remove > report,
                "runtime reports terminal debt before registry removal");
    }

    private static void periodicWorkHasHardBoundsAndFairScheduling()
            throws IOException {
        String limits = read("dungeon/preparation/DungeonPreparationLimits.java");
        String tick = read("dungeon/runtime/DungeonTickHandler.java");
        String portalStore = read("dungeon/state/store/PortalSessionStore.java");

        for (String token : List.of(
                "MAX_ACTIVE_PREPARATION_JOBS_PER_LEVEL",
                "MAX_ACTIVE_RECOVERY_JOBS_PER_LEVEL",
                "PERSISTED_PROBE_SUBMISSIONS_PER_LEVEL_TICK",
                "PERSISTED_PROBE_COMPLETION_DRAINS_PER_LEVEL_TICK",
                "ENTRY_CHUNK_REQUESTS_PER_LEVEL_TICK",
                "SAFE_SPAWN_CANDIDATES_PER_LEVEL_TICK",
                "PENDING_TICKET_RELEASE_RETRIES_PER_LEVEL_TICK",
                "MAX_PREPARATION_NANOS_PER_LEVEL_TICK",
                "JOB_TIMEOUT_TICKS"
        )) {
            check(limits.contains(token), "hard bound: " + token);
        }
        check(tick.contains("PREPARED_ENTRY_RECONCILIATION_BATCH"),
                "reconciliation batch bound");
        check(tick.contains("PORTAL_EXPIRY_MAINTENANCE_BATCH"),
                "expiry batch bound");
        check(tick.contains("INACTIVE_PORTAL_MAINTENANCE_BATCH"),
                "inactive portal batch bound");
        check(portalStore.contains("nextMaintenanceBatch"),
                "portal maintenance rotates fairly");
    }

    private static void preparationCallGraphHasNoForbiddenOperations()
            throws IOException {
        List<Path> roots = List.of(
                MAIN.resolve("io/github/naimjeg/obeliskdepths/dungeon/preparation"),
                MAIN.resolve("io/github/naimjeg/obeliskdepths/dungeon/interaction"),
                MAIN.resolve("io/github/naimjeg/obeliskdepths/dungeon/portal"),
                MAIN.resolve("io/github/naimjeg/obeliskdepths/dungeon/runtime/DungeonTickHandler.java"),
                MAIN.resolve("io/github/naimjeg/obeliskdepths/entity/DungeonPortalEntity.java"),
                MAIN.resolve("io/github/naimjeg/obeliskdepths/menu/ObeliskPortalMenu.java")
        );
        List<String> forbidden = List.of(
                ".join(", "Future.get(", "managedBlock(", "getChunkFuture(",
                "CompletableFuture.runAsync", "values()[", "generateSiteBounds(",
                "Thread.sleep(", "LevelLoadingScreen"
        );
        ArrayList<String> violations = new ArrayList<>();
        for (Path root : roots) {
            if (Files.isDirectory(root)) {
                try (var stream = Files.walk(root)) {
                    for (Path source : stream
                            .filter(path -> path.toString().endsWith(".java"))
                            .toList()) {
                        scan(source, forbidden, violations);
                    }
                }
            } else {
                scan(root, forbidden, violations);
            }
        }
        String portalEntry = Files.readString(
                MAIN.resolve("io/github/naimjeg/obeliskdepths/dungeon/interaction/DungeonPortalEntryService.java")
        );
        for (String forbiddenPortalOperation : List.of(
                "scanChunk(", "DungeonSafeSpawnResolver", "getChunk("
        )) {
            if (portalEntry.contains(forbiddenPortalOperation)) {
                violations.add("portal entry contains " + forbiddenPortalOperation);
            }
        }
        for (Path futureOwner : List.of(
                MAIN.resolve("io/github/naimjeg/obeliskdepths/dungeon/preparation/AsyncDungeonSiteProbe.java"),
                MAIN.resolve("io/github/naimjeg/obeliskdepths/dungeon/preparation/chunk/DungeonChunkLeaseManager.java")
        )) {
            String text = Files.readString(futureOwner);
            java.util.regex.Matcher blockingGet = java.util.regex.Pattern.compile(
                    "(?:completionFuture|future|raw|normalized)\\s*\\.get\\s*\\("
            ).matcher(text);
            if (blockingGet.find()) {
                violations.add(futureOwner + " contains blocking future get");
            }
        }
        for (Path root : roots) {
            List<Path> sources;
            if (Files.isDirectory(root)) {
                try (var stream = Files.walk(root)) {
                    sources = stream.filter(path -> path.toString().endsWith(".java"))
                            .toList();
                }
            } else {
                sources = List.of(root);
            }
            for (Path source : sources) {
                String text = Files.readString(source);
                if (java.util.regex.Pattern.compile(
                        "getChunk\\s*\\([^;\\n]{0,300},\\s*true\\s*\\)"
                ).matcher(text).find()) {
                    violations.add(source + " contains generating getChunk(..., true)");
                }
            }
        }
        for (Path protocolSource : List.of(
                MAIN.resolve("io/github/naimjeg/obeliskdepths/menu/ObeliskPortalMenu.java"),
                MAIN.resolve("io/github/naimjeg/obeliskdepths/menu/DungeonPreparationMenuState.java"),
                MAIN.resolve("io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonPreparationStage.java"),
                MAIN.resolve("io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonPreparationJobFailureReason.java"),
                MAIN.resolve("io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonPreparationCancellationReason.java")
        )) {
            String text = Files.readString(protocolSource);
            if (text.contains(".ordinal()") || text.contains("values()[")) {
                violations.add(protocolSource + " contains ordinal protocol serialization");
            }
        }
        if (!violations.isEmpty()) {
            throw new AssertionError("Forbidden preparation operations: " + violations);
        }
    }

    private static void scan(
            Path source,
            List<String> forbidden,
            List<String> violations
    ) throws IOException {
        String text = Files.readString(source);
        for (String token : forbidden) {
            if (text.contains(token)) {
                violations.add(source + " contains " + token);
            }
        }
    }

    private static String read(String relative) throws IOException {
        return Files.readString(
                MAIN.resolve("io/github/naimjeg/obeliskdepths").resolve(relative)
        );
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
