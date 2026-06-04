package io.github.naimjeg.obeliskdepths.registry;

import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class ModWoodTypeParallelInitializationTest {
    private ModWoodTypeParallelInitializationTest() {
    }

    public static void main(String[] args) throws Exception {
        bootstrapMinecraftGate();
        ModWoodTypes.resetForTesting();

        CountDownLatch declarationsLoaded = new CountDownLatch(1);
        CountDownLatch bootstrapComplete = new CountDownLatch(1);
        AtomicReference<WoodType> workerWood = new AtomicReference<>();
        AtomicReference<BlockSetType> workerSet = new AtomicReference<>();
        AtomicReference<Throwable> workerFailure = new AtomicReference<>();
        RecordingBackend backend = new RecordingBackend();

        Thread worker = new Thread(() -> {
            try {
                Class.forName(
                        "io.github.naimjeg.obeliskdepths.registry.ModBlocks",
                        true,
                        ModWoodTypeParallelInitializationTest.class
                                .getClassLoader()
                );
                declarationsLoaded.countDown();

                if (!bootstrapComplete.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError(
                            "timed out waiting for vanilla bootstrap"
                    );
                }

                workerWood.set(ModWoodTypes.greatSwampTaxodium());
                workerSet.set(ModWoodTypes.greatSwampTaxodiumSet());
            } catch (Throwable throwable) {
                workerFailure.set(throwable);
                declarationsLoaded.countDown();
            }
        }, "modloading-worker-test");

        worker.start();

        if (!declarationsLoaded.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("mod registration declarations did not load");
        }
        if (workerFailure.get() != null) {
            throw new AssertionError(
                    "worker failed while loading mod registration declarations",
                    workerFailure.get()
            );
        }
        assertFalse(
                ModWoodTypes.isRegisteredForTesting(),
                "loading ModBlocks declarations must not register wood types"
        );
        assertEquals(
                List.of(),
                backend.events,
                "modloading worker should not reach the wood type backend"
        );

        ModWoodTypes.registerFromVanillaClinit(backend);
        bootstrapComplete.countDown();
        worker.join(TimeUnit.SECONDS.toMillis(5));

        if (worker.isAlive()) {
            throw new AssertionError("modloading worker did not finish");
        }
        if (workerFailure.get() != null) {
            throw new AssertionError(
                    "worker failed after vanilla bootstrap",
                    workerFailure.get()
            );
        }
        assertEquals(
                List.of("block_set", "wood_type"),
                backend.events,
                "vanilla bootstrap should perform exactly one ordered registration"
        );
        assertSame(
                backend.woodType,
                workerWood.get(),
                "worker should observe the completed WoodType"
        );
        assertSame(
                backend.blockSetType,
                workerSet.get(),
                "worker should observe the completed BlockSetType"
        );
    }

    private static final class RecordingBackend
            implements ModWoodTypes.RegistrationBackend {
        final List<String> events = new ArrayList<>();
        BlockSetType blockSetType;
        WoodType woodType;

        @Override
        public BlockSetType registerBlockSetType(String name) {
            events.add("block_set");
            blockSetType = new BlockSetType(name);
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

    private static void assertEquals(Object expected, Object actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(
                    label + " expected " + expected + " but was " + actual
            );
        }
    }

    private static void assertSame(Object expected, Object actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + " expected same instance");
        }
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }
}
