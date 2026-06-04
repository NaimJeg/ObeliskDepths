package io.github.naimjeg.obeliskdepths.tempering;

public final class TemperingTemplateDataTest {
    private TemperingTemplateDataTest() {
    }

    public static void main(String[] args) {
        TemperingTemplateData data = new TemperingTemplateData(4, 1.0F);

        assertEquals(4, data.tier(), "maximum tier should remain valid");
        assertEquals(1.0F, data.weight(), "maximum weight should remain valid");
        assertThrows(() -> new TemperingTemplateData(-3, 0.0F));
        assertThrows(() -> new TemperingTemplateData(1, -2.0F));
        assertThrows(() -> new TemperingTemplateData(1, Float.NaN));
    }

    private static void assertEquals(
            int expected,
            int actual,
            String message
    ) {
        if (expected != actual) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(
            float expected,
            float actual,
            String message
    ) {
        if (Float.compare(expected, actual) != 0) {
            throw new AssertionError(message);
        }
    }

    private static void assertThrows(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }
}
