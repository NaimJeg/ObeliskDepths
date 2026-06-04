package io.github.naimjeg.obeliskdepths.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LanguageResourceParityTest {
    private static final Path LANGUAGE_DIRECTORY = Path.of(
            "src/generated/resources/assets/obeliskdepths/lang"
    );
    private static final Pattern FORMAT_ARGUMENT =
            Pattern.compile("%(?:\\d+\\$)?[a-zA-Z]");

    private LanguageResourceParityTest() {
    }

    public static void main(String[] args) throws Exception {
        JsonObject english = readLanguage("en_us");
        JsonObject simplifiedChinese = readLanguage("zh_cn");

        assertEquals(
                english.keySet(),
                simplifiedChinese.keySet(),
                "Simplified Chinese translation keys"
        );
        assertTrue(!simplifiedChinese.keySet().isEmpty(),
                "Simplified Chinese language file should not be empty");

        for (String key : english.keySet()) {
            String englishValue = stringValue(english, key);
            String chineseValue = stringValue(simplifiedChinese, key);

            assertTrue(!chineseValue.isBlank(),
                    "Simplified Chinese translation should not be blank: " + key);
            assertTrue(containsHanCharacter(chineseValue),
                    "Simplified Chinese translation should contain Chinese text: " + key);
            assertEquals(
                    formatArguments(englishValue),
                    formatArguments(chineseValue),
                    "Formatting arguments for " + key
            );
        }

        assertProviderRegistered();
    }

    private static JsonObject readLanguage(String locale) throws IOException {
        Path path = LANGUAGE_DIRECTORY.resolve(locale + ".json");
        assertTrue(Files.isRegularFile(path),
                "Generated language file should exist: " + path);
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static String stringValue(JsonObject language, String key) {
        JsonElement value = language.get(key);
        assertTrue(value != null && value.isJsonPrimitive()
                        && value.getAsJsonPrimitive().isString(),
                "Translation should be a string: " + key);
        return value.getAsString();
    }

    private static boolean containsHanCharacter(String value) {
        return value.codePoints().anyMatch(codePoint ->
                Character.UnicodeScript.of(codePoint)
                        == Character.UnicodeScript.HAN
        );
    }

    private static List<String> formatArguments(String value) {
        Matcher matcher = FORMAT_ARGUMENT.matcher(value);
        List<String> arguments = new ArrayList<>();
        while (matcher.find()) {
            arguments.add(matcher.group());
        }
        return List.copyOf(arguments);
    }

    private static void assertProviderRegistered() throws IOException {
        String generators = Files.readString(Path.of(
                "src/main/java/io/github/naimjeg/obeliskdepths/data/"
                        + "ModDataGenerators.java"
        ));
        assertTrue(
                generators.contains("event.createProvider(LangZhCnProvider::new)"),
                "Simplified Chinese language provider should be registered"
        );
    }

    private static void assertEquals(
            Object expected,
            Object actual,
            String label
    ) {
        if (!expected.equals(actual)) {
            Set<?> missing = expected instanceof Set<?> expectedSet
                    && actual instanceof Set<?> actualSet
                    ? difference(expectedSet, actualSet)
                    : Set.of();
            Set<?> extra = expected instanceof Set<?> expectedSet
                    && actual instanceof Set<?> actualSet
                    ? difference(actualSet, expectedSet)
                    : Set.of();
            throw new AssertionError(
                    label + " expected " + expected + " but was " + actual
                            + "; missing=" + missing + "; extra=" + extra
            );
        }
    }

    private static Set<?> difference(Set<?> left, Set<?> right) {
        java.util.LinkedHashSet<Object> result =
                new java.util.LinkedHashSet<>(left);
        result.removeAll(right);
        return Set.copyOf(result);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
