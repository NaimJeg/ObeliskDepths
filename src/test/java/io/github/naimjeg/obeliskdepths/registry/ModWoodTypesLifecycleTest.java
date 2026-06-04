package io.github.naimjeg.obeliskdepths.registry;

import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class ModWoodTypesLifecycleTest {
    private ModWoodTypesLifecycleTest() {
    }

    public static void main(String[] args) throws Exception {
        testClassInitializationDoesNotRegister();
        testAccessorFailsBeforeRegistration();
        bootstrapMinecraftGate();
        testRegistrationOrderIdempotencyAndStableAccessors();
        testRecursiveRegistrationFails();
        testPartialFailureIsNotSuccessful();
        testCrossThreadPublication();
    }

    private static void testClassInitializationDoesNotRegister() {
        ModWoodTypes.resetForTesting();

        assertFalse(
                ModWoodTypes.isRegisteredForTesting(),
                "ModWoodTypes class initialization must not mark wood types registered"
        );
    }

    private static void testAccessorFailsBeforeRegistration() {
        ModWoodTypes.resetForTesting();

        IllegalStateException woodFailure =
                assertThrows(ModWoodTypes::amphixylon);
        assertContains(
                woodFailure.getMessage(),
                "before WoodType.<clinit> registration completed",
                "wood accessor failure should identify the bootstrap boundary"
        );

        IllegalStateException setFailure =
                assertThrows(ModWoodTypes::amphixylonSet);
        assertContains(
                setFailure.getMessage(),
                "before WoodType.<clinit> registration completed",
                "block set accessor failure should identify the bootstrap boundary"
        );
    }

    private static void testRegistrationOrderIdempotencyAndStableAccessors() {
        ModWoodTypes.resetForTesting();
        RecordingBackend backend = new RecordingBackend();

        ModWoodTypes.registerFromVanillaClinit(backend);

        assertEquals(
                List.of("block_set", "wood_type"),
                backend.events,
                "BlockSetType must register before WoodType"
        );
        assertSame(
                backend.blockSetType,
                ModWoodTypes.amphixylonSet(),
                "block set accessor should publish the registered instance"
        );
        assertSame(
                backend.woodType,
                ModWoodTypes.amphixylon(),
                "wood type accessor should publish the registered instance"
        );

        ModWoodTypes.registerFromVanillaClinit(backend);

        assertEquals(
                1,
                backend.invocations,
                "repeated bootstrap must not invoke registration again"
        );
        assertSame(
                backend.blockSetType,
                ModWoodTypes.amphixylonSet(),
                "block set accessor should remain stable after idempotent bootstrap"
        );
        assertSame(
                backend.woodType,
                ModWoodTypes.amphixylon(),
                "wood type accessor should remain stable after idempotent bootstrap"
        );
    }

    private static void testRecursiveRegistrationFails() {
        ModWoodTypes.resetForTesting();
        RecursiveBackend backend = new RecursiveBackend();

        IllegalStateException failure = assertThrows(() ->
                ModWoodTypes.registerFromVanillaClinit(backend)
        );

        assertContains(
                failure.getMessage(),
                "Recursive Amphixylon wood type registration",
                "recursive bootstrap should fail clearly"
        );
        assertFalse(
                ModWoodTypes.isRegisteredForTesting(),
                "recursive bootstrap failure must not mark registration complete"
        );
    }

    private static void testPartialFailureIsNotSuccessful() {
        ModWoodTypes.resetForTesting();
        RecordingBackend backend = new RecordingBackend();
        backend.failAfterBlockSet = true;

        IllegalStateException failure = assertThrows(() ->
                ModWoodTypes.registerFromVanillaClinit(backend)
        );
        assertContains(
                failure.getMessage(),
                "forced failure after block set",
                "first failure should be propagated"
        );
        assertFalse(
                ModWoodTypes.isRegisteredForTesting(),
                "partial failure must not mark registration complete"
        );

        IllegalStateException accessorFailure =
                assertThrows(ModWoodTypes::amphixylon);
        assertContains(
                accessorFailure.getMessage(),
                "registration failed",
                "accessor should report failed bootstrap state"
        );
        assertSame(
                failure,
                accessorFailure.getCause(),
                "accessor failure should retain the original registration cause"
        );

        IllegalStateException retryFailure = assertThrows(() ->
                ModWoodTypes.registerFromVanillaClinit(backend)
        );
        assertContains(
                retryFailure.getMessage(),
                "previously failed",
                "retry after partial failure should not perform another registration"
        );
        assertEquals(
                1,
                backend.invocations,
                "failed bootstrap must not be retried against the backend"
        );
    }

    private static void testCrossThreadPublication() throws Exception {
        ModWoodTypes.resetForTesting();
        RecordingBackend backend = new RecordingBackend();
        CountDownLatch read = new CountDownLatch(1);
        AtomicReference<WoodType> seenWood = new AtomicReference<>();
        AtomicReference<BlockSetType> seenSet = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread reader = new Thread(() -> {
            try {
                read.await();
                seenWood.set(ModWoodTypes.amphixylon());
                seenSet.set(ModWoodTypes.amphixylonSet());
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        }, "wood-type-reader-test");

        reader.start();
        ModWoodTypes.registerFromVanillaClinit(backend);
        read.countDown();
        reader.join(TimeUnit.SECONDS.toMillis(5));

        if (reader.isAlive()) {
            throw new AssertionError("reader thread did not finish");
        }
        if (failure.get() != null) {
            throw new AssertionError(
                    "reader thread failed",
                    failure.get()
            );
        }
        assertSame(
                backend.woodType,
                seenWood.get(),
                "reader thread should observe the published WoodType"
        );
        assertSame(
                backend.blockSetType,
                seenSet.get(),
                "reader thread should observe the published BlockSetType"
        );
    }

    private static final class RecordingBackend
            implements ModWoodTypes.RegistrationBackend {
        final List<String> events = new ArrayList<>();
        int invocations;
        boolean failAfterBlockSet;
        BlockSetType blockSetType;
        WoodType woodType;

        @Override
        public BlockSetType registerBlockSetType(String name) {
            invocations++;
            events.add("block_set");
            blockSetType = new BlockSetType(name);
            if (failAfterBlockSet) {
                throw new IllegalStateException("forced failure after block set");
            }
            return blockSetType;
        }

        @Override
        public WoodType registerWoodType(
                String name,
                BlockSetType setType
        ) {
            events.add("wood_type");
            woodType = new WoodType(name, setType);
            return woodType;
        }
    }

    private static final class RecursiveBackend
            implements ModWoodTypes.RegistrationBackend {
        @Override
        public BlockSetType registerBlockSetType(String name) {
            ModWoodTypes.registerFromVanillaClinit(this);
            throw new AssertionError("recursive call should fail first");
        }

        @Override
        public WoodType registerWoodType(
                String name,
                BlockSetType setType
        ) {
            throw new AssertionError("recursive test must fail before wood type");
        }
    }

    private static void bootstrapMinecraftGate() {
        try {
            Class<?> bootstrap = Class.forName("net.minecraft.server.Bootstrap");
            Field bootstrappedField =
                    bootstrap.getDeclaredField("isBootstrapped");
            bootstrappedField.setAccessible(true);
            bootstrappedField.setBoolean(null, true);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(
                    "Minecraft test bootstrap gate setup failed",
                    exception
            );
        }
    }

    private static IllegalStateException assertThrows(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return expected;
        }
        throw new AssertionError("Expected IllegalStateException");
    }

    private static void assertContains(
            String actual,
            String expected,
            String label
    ) {
        if (actual == null || !actual.contains(expected)) {
            throw new AssertionError(
                    label + " expected to contain " + expected + " but was "
                            + actual
            );
        }
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(
                    label + " expected " + expected + " but was " + actual
            );
        }
    }

    private static void assertSame(Object expected, Object actual, String label) {
        if (expected != actual) {
            throw new AssertionError(
                    label + " expected same instance"
            );
        }
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }
}
