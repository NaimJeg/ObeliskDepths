package io.github.naimjeg.obeliskdepths.dungeon.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class DungeonRuntimeBoundaryTest {
    private static final Path MAIN = Path.of("src", "main", "java");

    private DungeonRuntimeBoundaryTest() {
    }

    public static void main(String[] args) throws IOException {
        assertRuntimeReservationDoesNotManuallyMaterialize();
        assertTeleportDoesNotMaterialize();
        assertStructureLookupDoesNotGenerateCandidates();
        assertRuntimePackagesDoNotWriteDungeonBlocks();
        assertRuntimeGeometryPackageIsAbsent();
        assertGeneratedPiecesComeFromWorldgenBuilder();
        assertOverlapDiagnosticsDoNotRejectVanillaStarts();
    }

    private static void assertRuntimeReservationDoesNotManuallyMaterialize() throws IOException {
        String source = read(
                "io/github/naimjeg/obeliskdepths/dungeon/instance/DungeonInstanceService.java"
        );

        assertNotContains(
                source,
                "DungeonSitePlanner" + ".plan(",
                "runtime reservation must not plan dungeon geometry"
        );
        assertNotContains(
                source,
                "DungeonMaterialization" + "Service",
                "runtime reservation must not manually materialize geometry"
        );
        assertNotContains(
                source,
                "reserveSitePlanForNewInstance",
                "runtime reservation must not reserve planned sites"
        );
        assertNotContains(
                source,
                "PLANNED" + "_PROTOTYPE",
                "runtime reservation must not use prototype projections"
        );
        assertContains(
                source,
                "findOrGenerateReservableSite",
                "runtime reservation should use the vanilla-worldgen provisioner"
        );
        assertContains(
                source,
                "VANILLA WORLDGEN REMAINS AUTHORITATIVE",
                "runtime worldgen invariant comment should be present"
        );
    }

    private static void assertTeleportDoesNotMaterialize() throws IOException {
        String source = read("io/github/naimjeg/obeliskdepths/world/ObeliskDepthsTeleporter.java");

        assertNotContains(source, "DungeonMaterialization" + "Service", "teleportation must not materialize geometry");
        assertNotContains(source, "DungeonSite" + "Plan", "teleportation must not use planned site metadata");
        assertNotContains(source, "set" + "Block(", "teleportation must not write blocks");
        assertContains(source, "readGeneratedSite", "teleportation should resolve generated structure metadata");
        assertContains(source, "lookupExistingChunk", "teleportation should load persisted entry chunks without generation");
        assertContains(source, "must not create", "teleport invariant comment should be present");
    }

    private static void assertRuntimePackagesDoNotWriteDungeonBlocks() throws IOException {
        for (Path file : List.of(
                path("io/github/naimjeg/obeliskdepths/dungeon/instance/DungeonInstanceService.java"),
                path("io/github/naimjeg/obeliskdepths/dungeon/interaction/ObeliskInteractionHandler.java"),
                path("io/github/naimjeg/obeliskdepths/dungeon/session/DungeonSessionLifecycle.java"),
                path("io/github/naimjeg/obeliskdepths/dungeon/session/DungeonSessionCleanup.java"),
                path("io/github/naimjeg/obeliskdepths/dungeon/session/DungeonSessionPresence.java"),
                path("io/github/naimjeg/obeliskdepths/world/ObeliskDepthsTeleporter.java"),
                path("io/github/naimjeg/obeliskdepths/world/ObeliskDepthsChunkHooks.java")
        )) {
            String source = Files.readString(file);

            assertNotContains(source, ".set" + "Block(", file + " must not write dungeon geometry");
            assertNotContains(source, "set" + "BlockAndUpdate(", file + " must not write dungeon geometry");
            assertNotContains(source, "place" + "InWorld(", file + " must not place templates");
            assertNotContains(source, "Structure" + "Template", file + " must not place templates");
            assertNotContains(source, "dungeon." + "materialization", file + " must not import geometry APIs");
        }
    }

    private static void assertStructureLookupDoesNotGenerateCandidates() throws IOException {
        String source = read("io/github/naimjeg/obeliskdepths/dungeon/site/reader/DungeonStructureStartReader.java");

        assertNotContains(source, "read" + "OrGenerate", "structure lookup must not expose runtime generation fallback");
        assertNotContains(source, "STRUCTURE_STARTS, true)", "structure lookup must not generate unpersisted candidates");
        assertContains(source, "chunkScanner()", "structure lookup should probe persisted chunk data before loading");
    }

    private static void assertRuntimeGeometryPackageIsAbsent() throws IOException {
        Path materializationPackage = path("io/github/naimjeg/obeliskdepths/dungeon/" + "materialization");

        if (!Files.exists(materializationPackage)) {
            return;
        }

        try (var stream = Files.walk(materializationPackage)) {
            boolean hasJavaFiles = stream.anyMatch(path -> path.toString().endsWith(".java"));

            if (hasJavaFiles) {
                throw new AssertionError("runtime materialization package must not expose geometry-writing APIs");
            }
        }
    }

    private static void assertGeneratedPiecesComeFromWorldgenBuilder() throws IOException {
        String structure = read("io/github/naimjeg/obeliskdepths/worldgen/structure/ObeliskDungeonStructure.java");
        String emitter = read("io/github/naimjeg/obeliskdepths/worldgen/structure/generation/DungeonGenerationPlanEmitter.java");

        assertContains(structure, "DungeonGenerationPlanEmitter.emit", "structure generation should emit generated plan pieces");
        assertContains(emitter, "StructurePiecesBuilder", "piece emission should use vanilla structure builder");
        assertContains(emitter, "builder.addPiece", "rooms and corridors should be structure pieces");
    }

    private static void assertOverlapDiagnosticsDoNotRejectVanillaStarts() throws IOException {
        String guard = read("io/github/naimjeg/obeliskdepths/worldgen/structure/placement/ObeliskDungeonSiteOverlapGuard.java");

        assertContains(guard, "placementDecision=vanilla_random_spread", "overlap guard should be diagnostic only");
        assertNotContains(guard, "rejecting candidate chunk", "overlap diagnostics must not reject all vanilla starts");
        assertNotContains(guard, "return Optional.of(new Rejection", "overlap diagnostics must not veto structure generation");
    }

    private static String read(String relative) throws IOException {
        return Files.readString(path(relative));
    }

    private static Path path(String relative) {
        return MAIN.resolve(relative);
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
}
