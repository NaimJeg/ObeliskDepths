package io.github.naimjeg.obeliskdepths.block.entity;

public final class ObeliskChestOpeningAnimationTest {
    private ObeliskChestOpeningAnimationTest() {
    }

    public static void main(String[] args) {
        closedStateStartsAtZero();
        openingStopsAtDurationAndIsIdempotent();
        openedStateNeverReturnsToClosed();
        loadedOpenedStateStartsAtFinalPose();
        loadedClosedStateStartsClosed();
    }

    private static void closedStateStartsAtZero() {
        ObeliskChestOpeningAnimation animation = new ObeliskChestOpeningAnimation(false);
        assertEquals(0, animation.previousTicks(), "closed previous tick");
        assertEquals(0, animation.ticks(), "closed current tick");
    }

    private static void openingStopsAtDurationAndIsIdempotent() {
        ObeliskChestOpeningAnimation animation = new ObeliskChestOpeningAnimation(false);
        for (int tick = 0; tick < ObeliskChestBlockEntity.OPEN_DURATION_TICKS + 20; tick++) {
            animation.clientTick(true);
        }
        assertEquals(
                ObeliskChestBlockEntity.OPEN_DURATION_TICKS,
                animation.ticks(),
                "opening duration clamp"
        );

        animation.clientTick(true);
        assertEquals(
                ObeliskChestBlockEntity.OPEN_DURATION_TICKS,
                animation.ticks(),
                "repeated open request"
        );
    }

    private static void openedStateNeverReturnsToClosed() {
        ObeliskChestOpeningAnimation animation = new ObeliskChestOpeningAnimation(false);
        animation.clientTick(true);
        animation.clientTick(false);
        assertTrue(animation.opened(), "live opened state is permanent");
        assertEquals(2, animation.ticks(), "false state cannot reverse animation");
    }

    private static void loadedOpenedStateStartsAtFinalPose() {
        ObeliskChestOpeningAnimation animation = new ObeliskChestOpeningAnimation(true);
        assertTrue(animation.opened(), "loaded opened flag");
        assertEquals(
                ObeliskChestBlockEntity.OPEN_DURATION_TICKS,
                animation.previousTicks(),
                "loaded opened previous tick"
        );
        assertEquals(
                ObeliskChestBlockEntity.OPEN_DURATION_TICKS,
                animation.ticks(),
                "loaded opened current tick"
        );
    }

    private static void loadedClosedStateStartsClosed() {
        ObeliskChestOpeningAnimation animation = new ObeliskChestOpeningAnimation(false);
        assertTrue(!animation.opened(), "loaded closed flag");
        assertEquals(0, animation.ticks(), "loaded closed current tick");
    }

    private static void assertEquals(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertTrue(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
