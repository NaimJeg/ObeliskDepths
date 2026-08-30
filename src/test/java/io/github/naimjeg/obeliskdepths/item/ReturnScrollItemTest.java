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
        testRunePulse();
        testStackConsumptionSemantics();
        testTranslationCoverage();
        testReturnServiceTransactionalSourceInvariants();
        testItemStartsUseAndFinishesThroughReturnService();
        testFirstPersonPresentationSource();
        testGeneratedItemModelRemainsStatic();
        testCommonCodeHasNoClientImports();
    }

    private static void testActivationMath() {
        assertEquals(0.0F, ReturnScrollUseMath.smoothStep(-1.0F), "smoothstep clamps low");
        assertEquals(1.0F, ReturnScrollUseMath.smoothStep(2.0F), "smoothstep clamps high");
        assertEquals(0.0F, ReturnScrollUseMath.activationProgress(60, 61, 0.0F), "activation clamps before start");
        assertEquals(1.0F, ReturnScrollUseMath.activationProgress(60, 0, 1.0F), "activation clamps at completion");

        assertEquals(1.0F, ReturnScrollUseMath.raiseProgress(0.20F), "raise reaches boundary");
        assertEquals(0.0F, ReturnScrollUseMath.attunementProgress(0.15F), "attunement starts during raise");
        assertEquals(1.0F, ReturnScrollUseMath.attunementProgress(0.82F), "attunement completes before finale");
        assertEquals(0.0F, ReturnScrollUseMath.finalShakeProgress(0.81F), "final shake starts after attunement");
        assertEquals(1.0F, ReturnScrollUseMath.finalShakeProgress(1.0F), "final shake reaches completion");

        for (float progress : new float[] {-1.0F, 0.0F, 0.1F, 0.35F, 0.67F, 0.9F, 1.0F, 2.0F}) {
            assertFinite(ReturnScrollUseMath.raiseProgress(progress), "raise finite " + progress);
            assertFinite(ReturnScrollUseMath.attunementProgress(progress), "attunement finite " + progress);
            assertFinite(ReturnScrollUseMath.finalShakeProgress(progress), "final shake finite " + progress);
        }
    }

    private static void testRunePulse() {
        for (float age : new float[] {-100.0F, -1.0F, 0.0F, 0.25F, 1.0F, 17.5F, 100.0F}) {
            float pulse = ReturnScrollUseMath.runePulse(age);
            assertTrue(pulse >= 0.0F && pulse <= 1.0F, "pulse stays in range at " + age);
            assertFinite(pulse, "pulse finite " + age);
        }

        float midpoint = ReturnScrollUseMath.runePulse(0.0F);
        float high = ReturnScrollUseMath.runePulse(
                (float) (Math.PI / 2.0D / ReturnScrollUseMath.RUNE_PULSE_SPEED)
        );
        float low = ReturnScrollUseMath.runePulse(
                (float) (Math.PI * 3.0D / 2.0D / ReturnScrollUseMath.RUNE_PULSE_SPEED)
        );
        assertTrue(low < midpoint && midpoint < high, "pulse has distinct low, mid, and high intensity");

        float period = (float) (Math.PI * 2.0D / ReturnScrollUseMath.RUNE_PULSE_SPEED);
        for (float age : new float[] {0.0F, 0.25F, 4.5F, 12.75F}) {
            assertApproximately(
                    ReturnScrollUseMath.runePulse(age),
                    ReturnScrollUseMath.runePulse(age + period),
                    0.0001F,
                    "pulse repeats after one period at " + age
            );
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
        assertTrue(source.contains("returnPlayerFromScroll"), "scroll path has separate execution");
        assertTrue(source.contains("SAVED_DESTINATION_WITHOUT_INSTANCE"), "scroll can use saved destination without instance id");
        assertTrue(source.contains("PLAYER_RESPAWN"), "scroll can use player respawn fallback");
        assertTrue(source.contains("OVERWORLD_SPAWN"), "scroll can use overworld spawn fallback");
        assertFalse(source.contains("checkScrollReturn"), "obsolete start-validation query is removed");

        String scrollReturn = source.substring(source.indexOf("public static PlayerDungeonReturnResult returnPlayerFromScroll"));
        assertOrder(scrollReturn, "resolveScrollReturn(player)", "teleportToLevel", "scroll resolves current state before teleport");
        assertOrder(scrollReturn, "if (resolvedReturn.result() != PlayerDungeonReturnResult.SUCCESS)", "teleportToLevel", "failed resolution returns before teleport");
        assertOrder(scrollReturn, "return PlayerDungeonReturnResult.TELEPORT_FAILED", "PlayerDungeonTracker.clear(effectivePlayer)", "failed teleport returns before cleanup");
    }

    private static void testItemStartsUseAndFinishesThroughReturnService() throws IOException {
        String itemSource = read("src/main/java/io/github/naimjeg/obeliskdepths/item/ReturnScrollItem.java");
        String commandSource = read("src/main/java/io/github/naimjeg/obeliskdepths/command/DungeonDebugTravelCommands.java");

        assertTrue(itemSource.contains("player.startUsingItem(hand)"), "all sides start using immediately");
        assertTrue(itemSource.contains("return InteractionResult.CONSUME"), "use consumes the interaction after starting");
        assertTrue(itemSource.contains("PlayerDungeonReturnService.returnPlayerFromScroll(player)"), "item executes through scroll return service");
        assertTrue(commandSource.contains("PlayerDungeonReturnService.returnPlayer"), "command continues to use return service");
        assertFalse(itemSource.contains("validateCanStart"), "item has no start-time validation method");
        assertFalse(itemSource.contains("checkScrollReturn"), "item does not preflight scroll return state");
        assertFalse(itemSource.contains("InteractionResult.FAIL"), "invalid return state does not reject use start");
        assertFalse(itemSource.contains("ModDimensions"), "item does not inspect dimensions before use starts");
        assertFalse(itemSource.contains("ObeliskDepthsTeleporter"), "item must not duplicate teleport logic");
        assertFalse(itemSource.contains("performCommand"), "item must not execute a command string");
        assertFalse(itemSource.contains("Level.OVERWORLD"), "item must not hardcode overworld return");
        assertFalse(itemSource.contains("extends MapItem"), "return scroll must not extend MapItem");
        assertFalse(itemSource.contains("releaseUsing"), "early release should use vanilla cancellation");
    }

    private static void testFirstPersonPresentationSource() throws IOException {
        String renderer = read("src/main/java/io/github/naimjeg/obeliskdepths/client/render/ReturnScrollFirstPersonRenderer.java");

        assertFalse(renderer.contains("unfoldProgress"), "first-person scroll has no unfold phase");
        assertFalse(renderer.contains("widthScale"), "scroll quad always uses its full width");
        assertTrue(renderer.contains("ReturnScrollUseMath.runePulse(player.tickCount + frameInterp)"),
                "rune pulse uses interpolated presentation time");
        assertTrue(renderer.contains("FULL_BRIGHT_LIGHT"), "runes remain full bright");
        assertFalse(renderer.contains("Math.random"), "rune pulse is deterministic");
        assertFalse(renderer.contains("ThreadLocalRandom"), "rune pulse is deterministic");
    }

    private static void testGeneratedItemModelRemainsStatic() throws IOException {
        String model = read("src/generated/resources/assets/obeliskdepths/models/item/return_scroll.json");
        String item = read("src/generated/resources/assets/obeliskdepths/items/return_scroll.json");

        assertTrue(model.contains("minecraft:item/generated"), "inventory model stays a static generated item");
        assertTrue(model.contains("obeliskdepths:item/return_scroll"), "inventory model uses the scroll icon");
        assertTrue(item.contains("obeliskdepths:item/return_scroll"), "item definition points to the static model");
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

    private static void assertApproximately(
            float expected,
            float actual,
            float epsilon,
            String message
    ) {
        if (Math.abs(expected - actual) > epsilon) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertOrder(
            String source,
            String first,
            String second,
            String message
    ) {
        int firstIndex = source.indexOf(first);
        int secondIndex = source.indexOf(second);
        if (firstIndex < 0 || secondIndex < 0 || firstIndex >= secondIndex) {
            throw new AssertionError(message + ": expected '" + first + "' before '" + second + "'");
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
