package io.github.naimjeg.obeliskdepths.item;

import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonReturnResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ReturnScrollItemTest {
    private ReturnScrollItemTest() {
    }

    public static void main(String[] args) throws IOException {
        testActivationMath();
        testStackConsumptionSemantics();
        testTranslationCoverage();
        testReturnServiceTransactionalSourceInvariants();
        testItemUsesReturnServiceOnly();
        testCommonCodeHasNoClientImports();
    }

    private static void testActivationMath() {
        assertEquals(0.0F, ReturnScrollUseMath.smoothStep(-1.0F), "smoothstep clamps low");
        assertEquals(1.0F, ReturnScrollUseMath.smoothStep(2.0F), "smoothstep clamps high");
        assertEquals(0.0F, ReturnScrollUseMath.activationProgress(60, 61, 0.0F), "activation clamps before start");
        assertEquals(1.0F, ReturnScrollUseMath.activationProgress(60, 0, 1.0F), "activation clamps at completion");

        assertEquals(1.0F, ReturnScrollUseMath.raiseProgress(0.20F), "raise reaches boundary");
        assertEquals(0.0F, ReturnScrollUseMath.unfoldProgress(0.20F), "unfold starts at boundary");
        assertEquals(1.0F, ReturnScrollUseMath.unfoldProgress(0.50F), "unfold ends at boundary");
        assertEquals(0.0F, ReturnScrollUseMath.finalShakeProgress(0.84F), "final shake before phase");

        for (float progress : new float[] {-1.0F, 0.0F, 0.1F, 0.35F, 0.67F, 0.9F, 1.0F, 2.0F}) {
            assertFinite(ReturnScrollUseMath.raiseProgress(progress), "raise finite " + progress);
            assertFinite(ReturnScrollUseMath.unfoldProgress(progress), "unfold finite " + progress);
            assertFinite(ReturnScrollUseMath.attunementProgress(progress), "attunement finite " + progress);
            assertFinite(ReturnScrollUseMath.finalShakeProgress(progress), "final shake finite " + progress);
        }
    }

    private static void testStackConsumptionSemantics() {
        assertEquals(
                2,
                ReturnScrollUseRules.resultingStackCountAfterFinish(3, false, PlayerDungeonReturnResult.SUCCESS),
                "survival success consumes one"
        );
        assertEquals(
                3,
                ReturnScrollUseRules.resultingStackCountAfterFinish(3, true, PlayerDungeonReturnResult.SUCCESS),
                "instabuild success consumes none"
        );
        assertEquals(
                2,
                ReturnScrollUseRules.resultingStackCountAfterFinish(3, false, PlayerDungeonReturnResult.SUCCESS_EMERGENCY_FALLBACK),
                "survival emergency fallback success consumes one"
        );

        for (PlayerDungeonReturnResult result : PlayerDungeonReturnResult.values()) {
            if (!ReturnScrollUseRules.isSuccessful(result)) {
                assertEquals(
                        3,
                        ReturnScrollUseRules.resultingStackCountAfterFinish(3, false, result),
                        result + " consumes none"
                );
            }
        }

        assertEquals(
                0,
                ReturnScrollUseRules.resultingStackCountAfterFinish(0, false, PlayerDungeonReturnResult.SUCCESS),
                "empty stack stays non-negative"
        );
    }

    private static void testTranslationCoverage() {
        for (PlayerDungeonReturnResult result : PlayerDungeonReturnResult.values()) {
            String key = ReturnScrollUseRules.translationKey(result);
            assertTrue(key.startsWith("message.obeliskdepths.return_scroll."), result + " translation namespace");
            assertFalse(key.contains("literal"), result + " translation key is not a literal fallback");
        }
    }

    private static void testReturnServiceTransactionalSourceInvariants() throws IOException {
        String source = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/player/PlayerDungeonReturnService.java");

        String preflight = source.substring(
                source.indexOf("public static PlayerDungeonReturnResult checkReturn"),
                source.indexOf("public static PlayerDungeonReturnResult returnPlayer")
        );
        assertTrue(preflight.contains("resolveReturn(player).result()"), "preflight reuses shared resolver");
        assertFalse(preflight.contains("PlayerDungeonTracker.clear"), "preflight must not clear binding");
        assertFalse(preflight.contains("teleportToLevel"), "preflight must not teleport");

        assertTrue(
                source.indexOf("return PlayerDungeonReturnResult.TELEPORT_FAILED")
                        < source.indexOf("PlayerDungeonTracker.clear"),
                "teleport failure must return before clearing binding"
        );
        assertEquals(2, countOccurrences(source, "PlayerDungeonTracker.clear("), "normal and scroll success clear binding");
        assertTrue(source.contains("DUNGEON_LEVEL_MISSING"), "missing dungeon level is validated");
        assertTrue(source.contains("checkScrollReturn"), "scroll path has separate validation");
        assertTrue(source.contains("returnPlayerFromScroll"), "scroll path has separate execution");
        assertTrue(source.contains("SAVED_DESTINATION_WITHOUT_INSTANCE"), "scroll can use saved destination without instance id");
        assertTrue(source.contains("PLAYER_RESPAWN"), "scroll can use player respawn fallback");
        assertTrue(source.contains("OVERWORLD_SPAWN"), "scroll can use overworld spawn fallback");
    }

    private static void testItemUsesReturnServiceOnly() throws IOException {
        String itemSource = read("src/main/java/io/github/naimjeg/obeliskdepths/item/ReturnScrollItem.java");
        String commandSource = read("src/main/java/io/github/naimjeg/obeliskdepths/command/DungeonDebugTravelCommands.java");

        assertTrue(itemSource.contains("PlayerDungeonReturnService.checkScrollReturn(player)"), "item preflights through scroll return service");
        assertTrue(itemSource.contains("PlayerDungeonReturnService.returnPlayerFromScroll(player)"), "item executes through scroll return service");
        assertTrue(commandSource.contains("PlayerDungeonReturnService.returnPlayer"), "command continues to use return service");
        assertFalse(itemSource.contains("ObeliskDepthsTeleporter"), "item must not duplicate teleport logic");
        assertFalse(itemSource.contains("performCommand"), "item must not execute a command string");
        assertFalse(itemSource.contains("Level.OVERWORLD"), "item must not hardcode overworld return");
        assertFalse(itemSource.contains("extends MapItem"), "return scroll must not extend MapItem");
        assertFalse(itemSource.contains("releaseUsing"), "early release should use vanilla cancellation");
    }

    private static void testCommonCodeHasNoClientImports() throws IOException {
        assertNoClientImport("src/main/java/io/github/naimjeg/obeliskdepths/item/ReturnScrollItem.java");
        assertNoClientImport("src/main/java/io/github/naimjeg/obeliskdepths/registry/ModItems.java");
        assertNoClientImport("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/player/PlayerDungeonReturnService.java");
    }

    private static void assertNoClientImport(String file) throws IOException {
        String source = read(file);
        assertFalse(source.contains("net.minecraft.client"), file + " must stay common-side");
        assertFalse(source.contains("SubmitNodeCollector"), file + " must not reference client rendering");
        assertFalse(source.contains("LocalPlayer"), file + " must not reference client player");
    }

    private static String read(String file) throws IOException {
        return Files.readString(Path.of(file));
    }

    private static void assertFinite(float value, String message) {
        if (!Float.isFinite(value)) {
            throw new AssertionError(message + ": expected finite, actual=" + value);
        }
    }

    private static void assertEquals(float expected, float actual, String message) {
        if (Float.compare(expected, actual) != 0) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }

    private static int countOccurrences(String text, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) >= 0) {
            count++;
            index += pattern.length();
        }

        return count;
    }
}
