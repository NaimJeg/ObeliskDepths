package io.github.naimjeg.obeliskdepths.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class DungeonArchitectureEnforcementTest {
    private static final Path MAIN = Path.of("src/main/java");
    private static final String ROOT =
            "src/main/java/io/github/naimjeg/obeliskdepths/";

    private DungeonArchitectureEnforcementTest() {
    }

    public static void main(String[] args) throws IOException {
        domainPackagesDoNotImportMenuOrNetwork();
        domainDefinitionsDoNotImportWorldgenGeometry();
        productionRuntimeDoesNotImportBuiltinDefinitionFactories();
        plannersDoNotAccessGlobalContent();
        obsoleteAccessModeIsGone();
        trivialForwardingServicesAreGone();
        obsoleteFacadeAndBroadEmitterApisAreGone();
        compatibilityWrappersAreGone();
        dungeonSessionCompatibilityCodecFieldsAreGone();
        productionWorldgenWiringIsComplete();
        malformedAggregateDriverCallsAreAbsent();
        reservedSiteEscapeHatchesAreAbsent();
        gameTestRegistryCodecTypesAreSound();
        integrationCoverageIsRegistered();
    }

    private static void domainPackagesDoNotImportMenuOrNetwork()
            throws IOException {
        assertNoSourceUnderContains(
                List.of("dungeon/session", "dungeon/room"),
                List.of(
                        "import io.github.naimjeg.obeliskdepths.menu.",
                        "import io.github.naimjeg.obeliskdepths.network."
                ),
                "session and room domain packages must not depend on menu/network"
        );
    }

    private static void domainDefinitionsDoNotImportWorldgenGeometry()
            throws IOException {
        assertNoSourceUnderContains(
                List.of(
                        "dungeon/room",
                        "dungeon/corridor",
                        "dungeon/layout",
                        "dungeon/theme"
                ),
                List.of(
                        "import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.",
                        "import io.github.naimjeg.obeliskdepths.worldgen.structure.piece."
                ),
                "domain definitions must use neutral dungeon geometry packages"
        );
    }

    private static void productionRuntimeDoesNotImportBuiltinDefinitionFactories()
            throws IOException {
        List<String> forbidden = List.of(
                "BuiltinDungeonRoomDefinitions",
                "BuiltinDungeonCorridorDefinitions",
                "BuiltinDungeonThemeDefinitions"
        );
        List<String> violations = new ArrayList<>();
        for (Path source : javaSources()) {
            String normalized = normalize(source);
            if (allowedBuiltinDefinitionFactoryUse(normalized)) {
                continue;
            }
            String text = Files.readString(source);
            for (String token : forbidden) {
                if (text.contains(token)) {
                    violations.add(normalized + " contains " + token);
                }
            }
        }
        assertNoViolations(
                violations,
                "production runtime must not use datagen-only built-in definition factories"
        );
    }

    private static void plannersDoNotAccessGlobalContent()
            throws IOException {
        List<String> forbidden = List.of(
                "DungeonContent.active(",
                "DungeonContentResolver",
                "BuiltinDungeonRoomDefinitions",
                "BuiltinDungeonCorridorDefinitions",
                "BuiltinDungeonThemeDefinitions",
                "BuiltinDungeonRooms",
                "BuiltinDungeonCorridors",
                "BuiltinDungeonThemes"
        );
        List<String> violations = new ArrayList<>();
        for (Path source : javaSources()) {
            String normalized = normalize(source);
            if (!isWorldgenPlannerSource(normalized)) {
                continue;
            }
            String text = Files.readString(source);
            for (String token : forbidden) {
                if (text.contains(token)) {
                    violations.add(normalized + " contains " + token);
                }
            }
        }
        assertNoViolations(
                violations,
                "worldgen planners must consume the supplied generation catalog"
        );
    }

    private static void obsoleteAccessModeIsGone() throws IOException {
        assertNoMainSourceContains(
                "DungeonAccessMode",
                "obsolete dungeon access mode must not remain"
        );
    }

    private static void gameTestRegistryCodecTypesAreSound()
            throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : javaSources()) {
            String text = Files.readString(source);
            String normalized = normalize(source);
            if (text.contains("extends GameTestInstance")) {
                violations.add(normalized
                        + " declares a custom synchronized GameTestInstance");
            }
            if (text.contains("FunctionGameTestInstance.CODEC")) {
                violations.add(normalized
                        + " borrows FunctionGameTestInstance.CODEC");
            }
        }
        assertNoViolations(
                violations,
                "custom GameTestInstance implementations must not advertise "
                        + "another concrete instance type's registry codec"
        );

        String modEntryPoint = readMain("ObeliskDepths.java");
        if (modEntryPoint.contains("DungeonPreparationFeatureGameTests::register")
                || modEntryPoint.contains(
                "DungeonPreparationMenuFeatureGameTests::register")) {
            throw new AssertionError(
                    "optional feature tests must not populate synchronized "
                            + "registries with invalid custom instances"
            );
        }
    }

    private static void trivialForwardingServicesAreGone()
            throws IOException {
        assertMissing(
                ROOT + "dungeon/reward/DungeonRewardService.java",
                "old reward facade must be deleted"
        );
        assertMissing(
                ROOT + "dungeon/session/DungeonSessionManager.java",
                "old session manager must be deleted"
        );
        assertMissing(
                ROOT + "dungeon/portal/PortalSessionManager.java",
                "old portal-session manager must be deleted"
        );
        assertMissing(
                ROOT + "dungeon/content/BuiltinDungeonContentResolver.java",
                "runtime built-in content fallback must be deleted"
        );
        assertMissing(
                ROOT + "dungeon/content/DungeonContentResolver.java",
                "old global content resolver API must be deleted"
        );

        String instanceService = readMain(
                "dungeon/instance/DungeonInstanceService.java"
        );
        assertNotContains(
                instanceService,
                "findOrGenerateReservableSite",
                "instance service must not own site discovery"
        );
        assertMissing(
                ROOT + "dungeon/preparation/DungeonActivationPreparationService.java",
                "legacy synchronous activation preparation service must be deleted"
        );
        assertMissing(
                ROOT + "dungeon/site/WorldgenDungeonSiteProvisioner.java",
                "legacy synchronous worldgen provisioner must be deleted"
        );
        assertNoMainSourceContains(
                "WorldgenDungeonSiteProvisioner",
                "normal runtime must not reference the synchronous worldgen provisioner"
        );
        assertContains(
                instanceService,
                "DungeonRuntimeArtifactCleanupService.reconcileStaleRewardArtifactsForSite",
                "remaining instance service must coordinate artifact reconciliation"
        );
        assertContains(
                instanceService,
                "data.sites().reserve",
                "remaining instance service must coordinate multi-store reservation"
        );
    }

    private static void compatibilityWrappersAreGone() throws IOException {
        assertNoMainSourceContains(
                "@Deprecated",
                "deprecated compatibility wrappers must not remain"
        );
        assertNoMainSourceContains(
                "DefinitionRegistry",
                "old content registry wrapper classes must not remain"
        );

        List<String> oldGeometryTypes = List.of(
                "DungeonCellPos.java",
                "DungeonCellBox.java",
                "DungeonBlockBox.java",
                "DungeonPortReference.java",
                "DungeonConnectorSide.java",
                "DungeonRoomFootprint.java",
                "DungeonConnectorShapeType.java",
                "DungeonLayoutConstants.java",
                "DungeonLayoutCodecs.java"
        );
        for (String filename : oldGeometryTypes) {
            assertMissing(
                    ROOT + "worldgen/structure/layout/" + filename,
                    "neutral geometry type must not remain in worldgen layout: "
                            + filename
            );
        }
    }

    private static void dungeonSessionCompatibilityCodecFieldsAreGone()
            throws IOException {
        String session = readMain("dungeon/session/DungeonSession.java");
        List<String> forbidden = List.of(
                "DungeonRewardState",
                "DungeonRewardChestState",
                "DungeonKillProgress",
                "\"reward_state\"",
                "\"progress\"",
                "rewardState",
                "markRewardChestOpened",
                "initializeFixedKillQuota",
                "creditNormalCombatKill"
        );
        List<String> violations = new ArrayList<>();
        for (String token : forbidden) {
            if (session.contains(token)) {
                violations.add("DungeonSession.java contains " + token);
            }
        }
        assertNoViolations(
                violations,
                "DungeonSession must not retain old compatibility codec fields"
        );
    }

    private static void productionWorldgenWiringIsComplete()
            throws IOException {
        String structureSource = readMain(
                "worldgen/structure/ObeliskDungeonStructure.java"
        );

        // 1. imports or references compliance type
        assertContains(
                structureSource,
                "DungeonStructureReferenceCompliance",
                "worldgen: must import DungeonStructureReferenceCompliance"
        );

        // 2. calls analyze
        assertContains(
                structureSource,
                "DungeonStructureReferenceCompliance.analyze(",
                "worldgen: must call analyze() on the final piece plan"
        );

        // 3. checks compliant()
        assertContains(
                structureSource,
                ".compliant()",
                "worldgen: must check compliance.compliant()"
        );

        // 4. checks effective bounds
        assertContains(
                structureSource,
                "effectiveStartBoundsCompliant",
                "worldgen: must check effectiveStartBoundsCompliant"
        );

        // 5. emits through DungeonPiecePlanEmitter
        assertContains(
                structureSource,
                "DungeonPiecePlanEmitter.emit",
                "worldgen: must emit the accepted plan"
        );

        // 6. compliance check textually before emission
        int complianceCheckIndex = structureSource.indexOf(".compliant()");
        int emitIndex = structureSource.indexOf(
                "DungeonPiecePlanEmitter.emit"
        );
        if (complianceCheckIndex < 0 || emitIndex < 0
                || complianceCheckIndex >= emitIndex) {
            throw new AssertionError(
                    "worldgen: compliance check must appear before "
                            + "DungeonPiecePlanEmitter.emit call"
            );
        }
    }

    private static void malformedAggregateDriverCallsAreAbsent()
            throws IOException {
        List<String> forbiddenPatterns = List.of(
                ".id().map(ReservedDungeonAggregate::site",
                ".siteKey().map(ReservedDungeonAggregate::site",
                ".key().map(ReservedDungeonAggregate::site",
                "session.instanceId().map(",
                ".isPresent().map("
        );
        for (Path source : javaSources()) {
            String normalized = normalize(source);
            if (normalized.contains("DungeonArchitectureEnforcementTest.java")) {
                continue;
            }
            String text = Files.readString(source);
            for (String pattern : forbiddenPatterns) {
                if (text.contains(pattern)) {
                    throw new AssertionError(
                            normalized
                                    + " contains malformed aggregate driver call: "
                                    + pattern
                    );
                }
            }
        }

        // Guard against Logger.warn(...).map(...) corruption.
        // Detect ".map(" immediately after any log level closing parenthesised
        // argument list or terminal call.
        for (Path source : javaSources()) {
            String normalized = normalize(source);
            if (normalized.contains("DungeonArchitectureEnforcementTest.java")) {
                continue;
            }
            String text = Files.readString(source);
            // Search for chained .map() calls that follow a logger call ending
            // Patterns like: LOGGER.warn(...\n            ).map(
            int loggerIndex = -1;
            while ((loggerIndex = text.indexOf("LOGGER.", loggerIndex + 1)) >= 0) {
                int closeIndex = text.indexOf(';', loggerIndex);
                if (closeIndex < 0) {
                    closeIndex = text.length();
                }
                String segment = text.substring(loggerIndex, closeIndex);
                // Find the first ';' or end of line after the close paren, see if .map appears
                int lastParen = segment.lastIndexOf(')');
                while (lastParen > 0
                        && lastParen < segment.length() - 1) {
                    char after = segment.charAt(lastParen + 1);
                    if (after == '.' || after == '\n' || after == '\r') {
                        // Could be a chain — check if .map( follows
                        int mapPos = segment.indexOf(".map(", lastParen + 1);
                        if (mapPos > lastParen && mapPos < closeIndex) {
                            // Verify it's not inside a comment or string
                            if (!isInsideCommentOrString(segment, mapPos)) {
                                throw new AssertionError(
                                        normalized
                                                + " contains chained .map() after logger call"
                                );
                            }
                        }
                    }
                    break;
                }
            }
        }
    }

    private static void reservedSiteEscapeHatchesAreAbsent()
            throws IOException {
        assertNoMainSourceContains(
                "requireReservedSnapshot",
                "requireReservedSnapshot escape hatch must not exist"
        );
        assertNoMainSourceContains(
                "DuprunInstanceId",
                "DuprunInstanceId must not exist"
        );
    }

    private static boolean isInsideCommentOrString(String text, int pos) {
        int lineStart = text.lastIndexOf('\n', pos);
        if (lineStart < 0) {
            lineStart = 0;
        }
        String line = text.substring(lineStart, pos);
        int commentStart = line.indexOf("//");
        if (commentStart >= 0 && commentStart < pos - lineStart) {
            return true;
        }
        int quoteCount = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '"') {
                quoteCount++;
            }
        }
        return (quoteCount % 2) != 0;
    }

    private static void integrationCoverageIsRegistered() throws IOException {
        String sessionTest = readTest(
                "dungeon/session/DungeonSessionResponsibilitySplitTest.java"
        );
        assertContains(
                sessionTest,
                "SessionAccessPolicy.STARTER_ONLY",
                "portal sessions must create starter-only dungeon sessions"
        );
        assertNotContains(
                sessionTest,
                "PortalAdmission" + "Mode",
                "portal admission modes must not remain in session tests"
        );
        assertContains(
                sessionTest,
                "starter is implicitly allowlisted",
                "ALLOWLIST starter authorization must be covered"
        );
        assertContains(
                sessionTest,
                "idempotent cleanup does not mark dirty again",
                "session cleanup idempotency must be covered"
        );

        String contentTest = readTest(
                "dungeon/content/DungeonContentDefinitionTest.java"
        );
        assertContains(
                contentTest,
                "DungeonContentSnapshot before = DungeonContent.active()",
                "atomic content reload failure must be covered"
        );
        assertContains(
                contentTest,
                "assertEquals(before, DungeonContent.active()",
                "invalid content must retain previous snapshot"
        );
        assertContains(
                contentTest,
                "testGenerationCatalogScopesSelectedTheme",
                "selected-theme catalog scoping must be covered"
        );
        assertContains(
                contentTest,
                "testGenerationCatalogCacheClearsOnInstall",
                "catalog cache reload invalidation must be covered"
        );

        String embeddingTest = readTest(
                "worldgen/structure/layout/DungeonGraphEmbeddingPlannerTest.java"
        );
        assertContains(
                embeddingTest,
                "arbitraryLoadedThemeCatalogCanDriveEmbedding",
                "arbitrary loaded theme generation must be covered"
        );

        String rewardTest = readTest(
                "dungeon/reward/DungeonRewardSystemSourceTest.java"
        );
        assertContains(
                rewardTest,
                "duplicate creation is checked before creating a reward",
                "reward duplicate creation protection must be covered"
        );
        assertContains(
                rewardTest,
                "testOrdinalVirtualDeliveryLifecycle",
                "ordinal reward claim recovery must be covered"
        );

        String multipartTest = readTest(
                "block/multipart/MultipartRemovalGuardsTest.java"
        );
        assertContains(
                multipartTest,
                "testSeparateNestedStructuresBothRunHooks",
                "multipart nested removal must be covered"
        );
        assertContains(
                multipartTest,
                "testHookMayRemoveAnotherStructure",
                "multipart hook-triggered removal must be covered"
        );
        assertContains(
                multipartTest,
                "testPreRemovedMarkerIsBounded",
                "multipart stale pre-removal marker bound must be covered"
        );
    }

    private static void assertNoSourceUnderContains(
            List<String> packageRoots,
            List<String> forbidden,
            String message
    ) throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : javaSources()) {
            String normalized = normalize(source);
            boolean applies = packageRoots.stream()
                    .anyMatch(root -> normalized.startsWith(ROOT + root + "/"));
            if (!applies) {
                continue;
            }
            String text = Files.readString(source);
            for (String token : forbidden) {
                if (text.contains(token)) {
                    violations.add(normalized + " contains " + token);
                }
            }
        }
        assertNoViolations(violations, message);
    }

    private static boolean allowedBuiltinDefinitionFactoryUse(String path) {
        return path.startsWith(ROOT + "data/")
                || path.equals(ROOT + "dungeon/content/DevelopmentDungeonContent.java")
                || path.equals(ROOT + "dungeon/room/BuiltinDungeonRoomDefinitions.java")
                || path.equals(ROOT + "dungeon/corridor/BuiltinDungeonCorridorDefinitions.java")
                || path.equals(ROOT + "dungeon/theme/BuiltinDungeonThemeDefinitions.java");
    }

    private static boolean isWorldgenPlannerSource(String path) {
        if (!path.startsWith(ROOT + "worldgen/structure/")) {
            return false;
        }
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        return fileName.contains("Planner")
                || fileName.contains("Router")
                || fileName.contains("Compiler")
                || fileName.contains("Resolver")
                || fileName.contains("Emitter");
    }

    private static void assertNoMainSourceContains(
            String token,
            String message
    ) throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : javaSources()) {
            if (Files.readString(source).contains(token)) {
                violations.add(normalize(source) + " contains " + token);
            }
        }
        assertNoViolations(violations, message);
    }

    private static List<Path> javaSources() throws IOException {
        try (Stream<Path> paths = Files.walk(MAIN)) {
            return paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }
    }

    private static String readMain(String relativePath) throws IOException {
        return Files.readString(MAIN.resolve("io/github/naimjeg/obeliskdepths")
                .resolve(relativePath));
    }

    private static String readTest(String relativePath) throws IOException {
        return Files.readString(Path.of(
                "src/test/java/io/github/naimjeg/obeliskdepths",
                relativePath
        ));
    }

    private static void assertMissing(String path, String message) {
        if (Files.exists(Path.of(path))) {
            throw new AssertionError(message + ": " + path);
        }
    }

    private static void assertContains(
            String text,
            String token,
            String message
    ) {
        if (!text.contains(token)) {
            throw new AssertionError(message + ": missing " + token);
        }
    }

    private static void assertNotContains(
            String text,
            String token,
            String message
    ) {
        if (text.contains(token)) {
            throw new AssertionError(message + ": found " + token);
        }
    }

    private static void obsoleteFacadeAndBroadEmitterApisAreGone()
            throws IOException {
        assertMissing(
                ROOT + "worldgen/structure/generation/DungeonGenerationPlanEmitter.java",
                "old broad emitter must be deleted"
        );

        String planner = readMain("worldgen/structure/generation/DungeonGenerationPlanner.java");
        assertNotContains(
                planner,
                "public static DungeonGenerationPlan plan(",
                "generation planner facade must be removed"
        );

        String compiler = readMain("worldgen/structure/piece/DungeonPiecePlanCompiler.java");
        assertNotContains(
                compiler,
                "DungeonLayoutPlan",
                "compiler must not retain obsolete layout-plan overload"
        );
        assertNotContains(
                compiler,
                "DungeonLayoutResolver",
                "compiler must not retain obsolete layout-plan overload"
        );
        assertNotContains(
                compiler,
                "DungeonGenerationCatalog",
                "compiler must not retain obsolete layout-plan overload"
        );
        assertNotContains(
                compiler,
                "ObeliskDungeonPieceRole.SITE",
                "compiler must not produce legacy SITE role"
        );

        String instanceService = readMain("dungeon/instance/DungeonInstanceService.java");
        assertNotContains(
                instanceService,
                "reserveNearestUnreachedWorldgenSite",
                "obsolete reservation facade must be removed"
        );
        assertNotContains(
                instanceService,
                "findOrGenerateReservableWorldgenSite",
                "obsolete reservation facade must be removed"
        );

        String handler = readMain("dungeon/interaction/ObeliskInteractionHandler.java");
        assertNotContains(
                handler,
                "player.getMainHandItem()",
                "three-argument interaction handler activate must be removed"
        );

        assertNoMainSourceContains(
                "DungeonGenerationPlanEmitter",
                "old broad emitter must not be referenced"
        );
        assertNoMainSourceContains(
                "corridor_grid",
                "old corridor grid piece must not be emitted"
        );

        String emitter = readMain("worldgen/structure/piece/DungeonPiecePlanEmitter.java");
        assertNotContains(
                emitter,
                "ObeliskDungeonPieceRole.SITE",
                "piece emitter must not produce legacy SITE role"
        );
    }

    private static void assertNoViolations(
            List<String> violations,
            String message
    ) {
        if (!violations.isEmpty()) {
            throw new AssertionError(
                    message + System.lineSeparator()
                            + String.join(System.lineSeparator(), violations)
            );
        }
    }

    private static String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }
}
