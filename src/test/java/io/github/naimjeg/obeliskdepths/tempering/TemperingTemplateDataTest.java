package io.github.naimjeg.obeliskdepths.tempering;

public final class TemperingTemplateDataTest {
    private TemperingTemplateDataTest() {
    }

    public static void main(String[] args) {
        TemperingTemplateData data = new TemperingTemplateData(-3, -2.0F);

        assertEquals(1, data.tier(), "negative tier should clamp to one");
        assertEquals(0.0F, data.weight(), "negative weight should clamp to zero");
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
}
