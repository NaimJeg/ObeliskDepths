package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DungeonPreparationLifecycleTest {
    private static final Path MAIN = Path.of("src", "main", "java");

    private DungeonPreparationLifecycleTest() {}

    public static void main(String[] args) throws IOException {
        logoutReason();
        dimensionChangeReason();
        unrelatedDimensionChangeDoesNotCancel();
        levelUnloadClearsTicketsAndRuntime();
        serverStopClearsEveryRuntime();
        runtimeClearAggregatesJobAndLeaseFailures();
        serverStopAggregatesAfterEveryRuntime();
        repeatedCleanupIsHarmless();
        lateCompletionAfterCleanupIsIgnored();
        cleanupLookupDoesNotCreateRuntime();
        wrongThreadRuntimeAccessRejected();
        clearedRuntimeCannotBeReused();
    }

    private static void logoutReason() throws IOException {
        String source = read("io/github/naimjeg/obeliskdepths/event/DungeonPlayerEvents.java");
        assertContains(source, "PLAYER_DISCONNECTED", "logout should use disconnected reason");
        assertContains(source, "cancelPreparationJobOnLogout", "logout path should be distinct");
    }

    private static void dimensionChangeReason() throws IOException {
        String source = read("io/github/naimjeg/obeliskdepths/event/DungeonPlayerEvents.java");
        assertContains(source, "PLAYER_DIMENSION_CHANGED", "dimension change reason");
        assertContains(source, "cancelJobsForPlayerOutsideSourceDimension",
                "dimension change should check expected source dimension");
    }

    private static void unrelatedDimensionChangeDoesNotCancel() throws IOException {
        String source = read(
                "io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonPreparationRuntime.java"
        );
        assertContains(source, "job.request().sourceDimension().equals(currentDimension)",
                "same source dimension should not cancel");
    }

    private static void levelUnloadClearsTicketsAndRuntime() throws IOException {
        String source = read(
                "io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonPreparationRuntime.java"
        );
        assertContains(source, "LevelEvent.Unload", "level unload subscriber");
        assertContains(source, "LEVEL_UNLOADED", "level unload cancellation reason");
        assertContains(source, "this.leaseManager.clear()", "clear should release tickets");
        assertContains(source, "LEVEL_RUNTIMES.remove(this.level, this)",
                "clear should remove runtime index");
    }

    private static void serverStopClearsEveryRuntime() throws IOException {
        String source = read(
                "io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonPreparationRuntime.java"
        );
        assertContains(source, "ServerStoppingEvent", "server stop subscriber");
        assertContains(source, "clearAllOnServerStopping()", "server stop entrypoint");
        assertContains(source, "SERVER_STOPPING", "server stop cancellation reason");
        assertContains(source, "runtime.level.getGameTime()", "owning level game time");
    }

    private static void runtimeClearAggregatesJobAndLeaseFailures() throws IOException {
        String source = read(
                "io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonPreparationRuntime.java"
        );
        assertContains(source, "Failed to cancel preparation jobs",
                "job cleanup failure should be aggregated");
        assertContains(source, "Failed to clear preparation chunk leases",
                "lease cleanup failure should be aggregated");
        assertContains(source, "finally", "runtime clear should have final cleanup");
        assertOrder(source, "this.jobRegistry.clearAllActive", "this.leaseManager.clear",
                "runtime should attempt lease clear after job clear");
        assertOrder(source, "this.leaseManager.clear", "LEVEL_RUNTIMES.remove(this.level, this)",
                "runtime index removal should follow lease clear in finally");
    }

    private static void serverStopAggregatesAfterEveryRuntime() throws IOException {
        String source = read(
                "io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonPreparationRuntime.java"
        );
        assertContains(source, "new LinkedHashSet<>(new ArrayList<>(LEVEL_RUNTIMES.values()))",
                "server stop should snapshot runtimes");
        assertContains(source, "LEVEL_RUNTIMES.clear()",
                "server stop should clear lookup before per-runtime cleanup");
        assertContains(source, "Failed to clear preparation runtime during server stop",
                "server stop should aggregate runtime failures");
        assertContains(source, "ObeliskDepths.LOGGER.error",
                "server stop should log aggregate failure");
    }

    private static void repeatedCleanupIsHarmless() throws IOException {
        String source = read(
                "io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonPreparationRuntime.java"
        );
        assertContains(source, "if (this.cleared)", "clear should be idempotent");
        assertContains(source, "return;", "idempotent clear should return");
    }

    private static void lateCompletionAfterCleanupIsIgnored() throws IOException {
        String source = read(
                "io/github/naimjeg/obeliskdepths/dungeon/preparation/chunk/DungeonChunkLeaseManager.java"
        );
        assertContains(source, "this.cleared || current != capturedEntry",
                "late callbacks should be ignored");
    }

    private static void cleanupLookupDoesNotCreateRuntime() throws IOException {
        String source = read(
                "io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonPreparationRuntime.java"
        );
        String unloadMethod = source.substring(source.indexOf("onLevelUnload"));
        assertContains(unloadMethod, "DungeonPreparationRuntime.get(level)",
                "unload should look up existing runtime");
        assertNotContains(unloadMethod, "getOrCreate", "unload must not create runtime");
    }

    private static void wrongThreadRuntimeAccessRejected() throws IOException {
        String source = read(
                "io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonPreparationRuntime.java"
        );
        assertContains(source, "assertServerThread(level)", "static access thread check");
        assertContains(source, "level.getServer().isSameThread()", "owner thread predicate");
        assertContains(source, "throw new IllegalStateException", "wrong thread rejected");
    }

    private static void clearedRuntimeCannotBeReused() throws IOException {
        String source = read(
                "io/github/naimjeg/obeliskdepths/dungeon/preparation/DungeonPreparationRuntime.java"
        );
        assertContains(source, "DungeonPreparationRuntime has been cleared",
                "cleared runtime should reject use");
        assertContains(source, "runtime.assertUsable()", "getOrCreate should reject cleared runtime");
    }

    private static String read(String relative) throws IOException {
        return Files.readString(MAIN.resolve(relative));
    }

    private static void assertContains(
            String source,
            String expected,
            String message
    ) {
        if (!source.contains(expected)) {
            throw new AssertionError(message + ": missing '" + expected + "'");
        }
    }

    private static void assertNotContains(
            String source,
            String forbidden,
            String message
    ) {
        if (source.contains(forbidden)) {
            throw new AssertionError(message + ": found '" + forbidden + "'");
        }
    }

    private static void assertOrder(
            String source,
            String before,
            String after,
            String message
    ) {
        int beforeIndex = source.indexOf(before);
        int afterIndex = source.indexOf(after);
        if (beforeIndex < 0 || afterIndex < 0 || beforeIndex >= afterIndex) {
            throw new AssertionError(
                    message + ": expected '" + before + "' before '" + after + "'"
            );
        }
    }
}
