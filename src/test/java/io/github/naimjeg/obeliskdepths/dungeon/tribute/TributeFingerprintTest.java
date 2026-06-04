package io.github.naimjeg.obeliskdepths.dungeon.tribute;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TributeFingerprintTest {
    private static final Path MAIN = Path.of("src", "main", "java");

    private TributeFingerprintTest() {
    }

    public static void main(String[] args) throws IOException {
        fingerprintDoesNotStoreMutableItemStack();
        fingerprintCarriesRequiredCommitIdentity();
        fingerprintUsesStableComponentSnapshot();
        stableSnapshotCopiesComponentPatch();
        matchesRechecksCurrentResolvedTribute();
    }

    private static void fingerprintDoesNotStoreMutableItemStack()
            throws IOException {
        String source = read("io/github/naimjeg/obeliskdepths/dungeon/tribute/TributeFingerprint.java");
        String header = source.substring(
                source.indexOf("public record TributeFingerprint("),
                source.indexOf(") {", source.indexOf("public record TributeFingerprint("))
        );
        assertNotContains(header, "ItemStack", "fingerprint fields must not store ItemStack");
        assertNotContains(header, "DataComponentMap", "fingerprint fields must not store live component map");
    }

    private static void fingerprintCarriesRequiredCommitIdentity()
            throws IOException {
        String source = read("io/github/naimjeg/obeliskdepths/dungeon/tribute/TributeFingerprint.java");
        assertContains(source, "Identifier itemId", "fingerprint should store registry id");
        assertContains(source, "int requiredCount", "fingerprint should store required count");
        assertContains(source, "StableComponentSnapshot components", "fingerprint should store component snapshot");
        assertContains(source, "ResolvedTribute resolvedTribute", "fingerprint should store resolved tribute");
        assertContains(source, "resolvedTribute.amount() != requiredCount",
                "required count should equal the submitted resolved tribute amount");
    }

    private static void fingerprintUsesStableComponentSnapshot()
            throws IOException {
        String source = read("io/github/naimjeg/obeliskdepths/dungeon/tribute/TributeFingerprint.java");
        assertContains(source, "StableComponentSnapshot.from(stack)",
                "factory should snapshot components");
        assertContains(source, "this.components.matches(currentStack)",
                "matches should compare stable component snapshot");
        assertNotContains(source, "stack.getComponents()",
                "fingerprint must not store ItemStack.getComponents live view");
    }

    private static void stableSnapshotCopiesComponentPatch()
            throws IOException {
        String source = read("io/github/naimjeg/obeliskdepths/dungeon/tribute/StableComponentSnapshot.java");
        assertContains(source, "DataComponentPatch.builder()",
                "stable snapshot should rebuild a patch");
        assertContains(source, "source.entrySet()",
                "stable snapshot should copy patch entries");
        assertContains(source, "stack.getComponentsPatch()",
                "stable snapshot should compare against the current patch");
    }

    private static void matchesRechecksCurrentResolvedTribute()
            throws IOException {
        String source = read("io/github/naimjeg/obeliskdepths/dungeon/tribute/TributeFingerprint.java");
        assertContains(source, "currentStack.getCount() < this.requiredCount",
                "matches should reject reduced count");
        assertContains(source, "TributeResolver.resolve(currentStack).equals(this.resolvedTribute)",
                "matches should verify the newly resolved tribute");
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
}
