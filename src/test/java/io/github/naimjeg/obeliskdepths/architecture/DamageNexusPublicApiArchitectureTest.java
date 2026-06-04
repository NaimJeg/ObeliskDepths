package io.github.naimjeg.obeliskdepths.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DamageNexusPublicApiArchitectureTest {
    private static final Path MAIN_JAVA = Path.of("src/main/java");
    private static final List<String> FORBIDDEN_PACKAGES = List.of(
            ".core.",
            ".internal.",
            ".registry.",
            ".diagnostics.",
            ".debug.",
            ".test."
    );

    @Test
    void damageNexusImportsUseOnlyThePublicApi() throws IOException {
        for (Path file : javaFiles()) {
            String source = Files.readString(file);
            for (String line : source.lines().toList()) {
                if (!line.startsWith("import io.github.naimjeg.damagenexus")) {
                    continue;
                }
                assertTrue(
                        line.startsWith("import io.github.naimjeg.damagenexus.api.")
                                || line.startsWith(
                                "import io.github.naimjeg.damagenexus.client.tooltip."
                        )
                                || line.equals(
                                "import io.github.naimjeg.damagenexus.config.TooltipDebugLevel;"
                        ),
                        () -> "non-public DamageNexus import in " + file + ": " + line
                );
                for (String forbidden : FORBIDDEN_PACKAGES) {
                    assertFalse(line.contains(forbidden), () ->
                            "forbidden DamageNexus import in " + file + ": " + line);
                }
            }
        }
    }

    @Test
    void productionContainsNoMaterializedEntryMutationOrLookup() throws IOException {
        for (Path file : javaFiles()) {
            String source = Files.readString(file);
            assertFalse(source.contains("DamageNexusItemApi.addEntry("), file.toString());
            assertFalse(source.contains("DamageNexusItemApi.setEntries("), file.toString());
            assertFalse(source.contains("DamageNexusItemApi.getEntries("), file.toString());
            assertFalse(source.contains("DamageNexusItemApi.getResolvedEntries("), file.toString());
            assertFalse(source.contains("DamageNexusItemApi.removeEntries("), file.toString());
        }
    }

    @Test
    void damageNexusPersistentIdsAreNotHandWritten() throws IOException {
        for (Path file : javaFiles()) {
            String source = Files.readString(file);
            assertFalse(
                    source.contains("Identifier.fromNamespaceAndPath(\"damagenexus\""),
                    file.toString()
            );
            assertFalse(
                    source.contains("ResourceLocation.fromNamespaceAndPath(\"damagenexus\""),
                    file.toString()
            );
        }
    }

    @Test
    void uniqueTooltipHandlerIsAppendOnlyAndDoesNotRewriteReferences()
            throws IOException {
        String handler = Files.readString(MAIN_JAVA.resolve(
                "io/github/naimjeg/obeliskdepths/client/tooltip/"
                        + "ObeliskUniqueEquipmentTooltipHandler.java"
        ));
        List<String> mutatingApis = List.of(
                "DamageNexusItemApi.setTemplateReferences(",
                "DamageNexusItemApi.addEntryTemplateReference(",
                "DamageNexusItemApi.addAffixTemplateReference(",
                "DamageNexusItemApi.removeEntryTemplateReferences(",
                "DamageNexusItemApi.removeAffixTemplateReferences(",
                "DamageNexusItemApi.clear("
        );
        for (String api : mutatingApis) {
            assertFalse(handler.contains(api), api);
        }
        assertTrue(handler.contains("DamageItemTemplateReferences.EMPTY"));
        assertTrue(handler.contains("EventPriority.LOWEST"));
    }

    @Test
    void clientTooltipPackageIsNotImportedFromServerReachableSources()
            throws IOException {
        Path clientRoot = MAIN_JAVA.resolve(
                "io/github/naimjeg/obeliskdepths/client"
        ).toAbsolutePath().normalize();
        for (Path file : javaFiles()) {
            if (file.toAbsolutePath().normalize().startsWith(clientRoot)) {
                continue;
            }
            assertFalse(
                    Files.readString(file).contains(
                            "import io.github.naimjeg.obeliskdepths.client.tooltip."
                    ),
                    file.toString()
            );
        }
    }

    @Test
    void displayTextUsesTheCurrentSealedApi() throws IOException {
        String previewResolver = Files.readString(MAIN_JAVA.resolve(
                "io/github/naimjeg/obeliskdepths/tempering/"
                        + "ObeliskTemperingPreviewResolver.java"
        ));

        assertTrue(previewResolver.contains("case DisplayText.Literal"));
        assertTrue(previewResolver.contains("case DisplayText.Translatable"));
        assertFalse(previewResolver.contains("text.translate()"));
        assertFalse(previewResolver.contains("text.text()"));
        assertFalse(previewResolver.contains("text.isBlank()"));
    }

    private static List<Path> javaFiles() throws IOException {
        try (var files = Files.walk(MAIN_JAVA)) {
            return files.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }
}
