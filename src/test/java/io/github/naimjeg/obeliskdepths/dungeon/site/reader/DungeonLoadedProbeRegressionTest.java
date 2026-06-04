package io.github.naimjeg.obeliskdepths.dungeon.site.reader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Regression test verifying that structure-start readers use only
 * loaded-chunk APIs and never perform synchronous disk I/O, blocking
 * future operations, or managed blocking.
 *
 * <p>The legacy {@code lookup} and {@code lookupExistingChunk} methods
 * have been removed. Only {@code lookupLoaded} remains as the runtime
 * entry point.</p>
 */
public final class DungeonLoadedProbeRegressionTest {
    private static final Path MAIN = Path.of("src/main/java");

    private DungeonLoadedProbeRegressionTest() {
    }

    public static void main(String[] args) throws IOException {
        probeLoadedChunkUsesGetChunkNow();
        lookupLoadedUsesGetChunkNow();
        loadedResultContainsNoPersistedScanState();
    }

    private static void probeLoadedChunkUsesGetChunkNow() throws IOException {
        String path = "src/main/java/io/github/naimjeg/obeliskdepths/dungeon/site/reader/ServerPersistedChunkProbeBackend.java";
        String text = Files.readString(Path.of(path));

        check(text.contains("getChunkNow"), "ServerPersistedChunkProbeBackend.probeLoadedChunk: must use getChunkNow");
        check(!text.contains("level.getChunk(\n"), "ServerPersistedChunkProbeBackend.probeLoadedChunk: must not use level.getChunk");
        check(!text.contains("getChunkFuture("), "ServerPersistedChunkProbeBackend: must not use getChunkFuture");
        check(!text.contains(".join("), "ServerPersistedChunkProbeBackend: must not use .join");
        check(!text.contains(".get("), "ServerPersistedChunkProbeBackend: must not use .get");
        check(!text.contains("managedBlock("), "ServerPersistedChunkProbeBackend: must not use managedBlock");
    }

    private static void lookupLoadedUsesGetChunkNow() throws IOException {
        String path = "src/main/java/io/github/naimjeg/obeliskdepths/dungeon/site/reader/DungeonStructureStartReader.java";
        String text = Files.readString(Path.of(path));

        String methodBody = extractMethodBody(text, "lookupLoaded");
        check(methodBody.contains("getChunkNow"), "DungeonStructureStartReader.lookupLoaded: must use getChunkNow");
        check(!methodBody.contains("level.getChunk("), "DungeonStructureStartReader.lookupLoaded: must not use level.getChunk");
        check(!methodBody.contains("getChunkFuture("), "DungeonStructureStartReader.lookupLoaded: must not use getChunkFuture");
        check(!methodBody.contains(".join("), "DungeonStructureStartReader.lookupLoaded: must not use .join");
        check(!methodBody.contains(".get("), "DungeonStructureStartReader.lookupLoaded: must not use .get");
        check(!methodBody.contains("managedBlock("), "DungeonStructureStartReader.lookupLoaded: must not use managedBlock");
        check(!methodBody.contains("scanChunk"), "DungeonStructureStartReader.lookupLoaded: must not use scanChunk");
    }

    private static void loadedResultContainsNoPersistedScanState()
            throws IOException {
        String path = "src/main/java/io/github/naimjeg/obeliskdepths/dungeon/site/reader/DungeonStructureStartReader.java";
        String text = Files.readString(Path.of(path));

        check(text.contains("record LoadedStructureStartResult"), "loaded result type exists");
        check(text.contains("CHUNK_NOT_LOADED"), "loaded result exposes unloaded chunk failure");
        check(text.contains("STRUCTURE_TYPE_MISSING"), "loaded result exposes missing structure type failure");
        check(!text.contains("LookupMechanism"), "loaded result must not retain lookup mechanisms");
        check(!text.contains("currentlyLoaded"), "loaded result must not retain currentlyLoaded state");
        check(!text.contains("persistedStatus"), "loaded result must not retain persisted status");
        check(!text.contains("returnedStatus"), "loaded result must not retain returned status");
        check(!text.contains("CANDIDATE_NOT" + "_PERSISTED"), "loaded result must not retain persisted rejection reasons");
        check(!text.contains("EXISTING_CHUNK" + "_LOOKUP_FAILED"), "loaded result must not retain impossible lookup failure");
    }

    private static String extractMethodBody(String source, String methodName) {
        int methodStart = source.indexOf("static " + methodName);
        if (methodStart < 0) {
            methodStart = source.indexOf(" " + methodName + "(");
        }
        if (methodStart < 0) {
            throw new AssertionError("Method " + methodName + " not found in source");
        }

        int bodyStart = source.indexOf('{', methodStart);
        if (bodyStart < 0) {
            throw new AssertionError("Method body start not found for " + methodName);
        }

        int depth = 0;
        int i = bodyStart;
        for (; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0) break;
            }
        }
        return source.substring(bodyStart, i + 1);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
