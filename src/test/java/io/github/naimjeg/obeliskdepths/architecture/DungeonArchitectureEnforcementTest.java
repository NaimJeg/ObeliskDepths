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
        compatibilityWrappersAreGone();
        dungeonSessionCompatibilityCodecFieldsAreGone();
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
        assertContains(
                instanceService,
                "WorldgenDungeonSiteProvisioner.findOrGenerateReservableSite",
                "remaining instance service must coordinate site provisioning"
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

    private static void integrationCoverageIsRegistered() throws IOException {
        String sessionTest = readTest(
                "dungeon/session/DungeonSessionResponsibilitySplitTest.java"
        );
        assertContains(
                sessionTest,
                "case SOLO -> SessionAccessPolicy.STARTER_ONLY",
                "portal admission to starter-only policy must be covered"
        );
        assertContains(
                sessionTest,
                "case OPEN_JOIN -> SessionAccessPolicy.OPEN",
                "portal admission to open policy must be covered"
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
