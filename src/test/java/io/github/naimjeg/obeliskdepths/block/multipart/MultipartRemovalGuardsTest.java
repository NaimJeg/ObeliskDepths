package io.github.naimjeg.obeliskdepths.block.multipart;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;

public final class MultipartRemovalGuardsTest {
    private MultipartRemovalGuardsTest() {
    }

    public static void main(String[] args) {
        runIsolated(MultipartRemovalGuardsTest::testSeparateNestedStructuresBothRunHooks);
        runIsolated(MultipartRemovalGuardsTest::testHookMayRemoveAnotherStructure);
        runIsolated(MultipartRemovalGuardsTest::testSameStructureRecursiveHookRunsOnce);
        runIsolated(MultipartRemovalGuardsTest::testPreRemovedIsStructureSpecific);
        runIsolated(MultipartRemovalGuardsTest::testThreadStateClearsAfterHookException);
        runIsolated(MultipartRemovalGuardsTest::testPreRemovedCanBeCleanedAfterException);
        runIsolated(MultipartRemovalGuardsTest::testPreRemovedMarkerIsBounded);
    }

    private static void testSeparateNestedStructuresBothRunHooks() {
        MultipartRemovalGuards.MultipartStructureKey first =
                key(new Object(), new Object(), 0);
        MultipartRemovalGuards.MultipartStructureKey second =
                key(new Object(), new Object(), 10);
        AtomicInteger firstHooks = new AtomicInteger();
        AtomicInteger secondHooks = new AtomicInteger();

        MultipartRemovalGuards.runRemoving(first, () -> {
            MultipartRemovalGuards.runHookOnce(
                    first,
                    firstHooks::incrementAndGet
            );
            MultipartRemovalGuards.runRemoving(
                    second,
                    () -> MultipartRemovalGuards.runHookOnce(
                            second,
                            secondHooks::incrementAndGet
                    )
            );
        });

        assertEquals(1, firstHooks.get(), "first structure hook count");
        assertEquals(1, secondHooks.get(), "second structure hook count");
        assertTrue(
                MultipartRemovalGuards.isThreadStateClearForTests(),
                "nested separate structures leave no thread state"
        );
    }

    private static void testHookMayRemoveAnotherStructure() {
        Object level = new Object();
        MultipartRemovalGuards.MultipartStructureKey first =
                key(level, new Object(), 0);
        MultipartRemovalGuards.MultipartStructureKey second =
                key(level, new Object(), 10);
        AtomicInteger firstHooks = new AtomicInteger();
        AtomicInteger secondHooks = new AtomicInteger();

        MultipartRemovalGuards.runRemoving(first, () ->
                MultipartRemovalGuards.runHookOnce(first, () -> {
                    firstHooks.incrementAndGet();
                    MultipartRemovalGuards.runRemoving(
                            second,
                            () -> MultipartRemovalGuards.runHookOnce(
                                    second,
                                    secondHooks::incrementAndGet
                            )
                    );
                })
        );

        assertEquals(1, firstHooks.get(), "first hook count");
        assertEquals(1, secondHooks.get(), "hook-triggered second count");
        assertTrue(
                MultipartRemovalGuards.isThreadStateClearForTests(),
                "hook-triggered removal leaves no thread state"
        );
    }

    private static void testSameStructureRecursiveHookRunsOnce() {
        MultipartRemovalGuards.MultipartStructureKey key =
                key(new Object(), new Object(), 0);
        AtomicInteger hooks = new AtomicInteger();

        MultipartRemovalGuards.runRemoving(key, () -> {
            boolean firstRan = MultipartRemovalGuards.runHookOnce(key, () -> {
                hooks.incrementAndGet();
                MultipartRemovalGuards.runRemoving(key, () -> {
                    boolean secondRan =
                            MultipartRemovalGuards.runHookOnce(
                                    key,
                                    hooks::incrementAndGet
                            );
                    assertFalse(
                            secondRan,
                            "recursive same-structure hook is suppressed"
                    );
                });
            });
            assertTrue(firstRan, "first same-structure hook runs");
        });

        assertEquals(1, hooks.get(), "same structure hook count");
        assertTrue(
                MultipartRemovalGuards.isThreadStateClearForTests(),
                "same-structure recursion leaves no thread state"
        );
    }

    private static void testPreRemovedIsStructureSpecific() {
        Object level = new Object();
        MultipartRemovalGuards.MultipartStructureKey first =
                key(level, new Object(), 0);
        MultipartRemovalGuards.MultipartStructureKey second =
                key(level, new Object(), 10);

        MultipartRemovalGuards.markPreRemoved(first);
        MultipartRemovalGuards.markPreRemoved(second);

        assertTrue(
                MultipartRemovalGuards.consumePreRemoved(first),
                "first pre-removed structure is consumed"
        );
        assertFalse(
                MultipartRemovalGuards.consumePreRemoved(first),
                "first pre-removed structure is consumed exactly once"
        );
        assertTrue(
                MultipartRemovalGuards.consumePreRemoved(second),
                "second pre-removed structure is still available"
        );
        assertTrue(
                MultipartRemovalGuards.isThreadStateClearForTests(),
                "pre-removed consumption leaves no thread state"
        );
    }

    private static void testThreadStateClearsAfterHookException() {
        MultipartRemovalGuards.MultipartStructureKey key =
                key(new Object(), new Object(), 0);

        try {
            MultipartRemovalGuards.runRemoving(key, () ->
                    MultipartRemovalGuards.runHookOnce(key, () -> {
                        throw new HookFailure();
                    })
            );
            throw new AssertionError("expected hook failure");
        } catch (HookFailure expected) {
            assertTrue(
                    MultipartRemovalGuards.isThreadStateClearForTests(),
                    "hook exception clears thread state"
            );
        }
    }

    private static void testPreRemovedCanBeCleanedAfterException() {
        MultipartRemovalGuards.MultipartStructureKey key =
                key(new Object(), new Object(), 0);

        try {
            MultipartRemovalGuards.runRemoving(key, () -> {
                boolean marked = false;
                try {
                    MultipartRemovalGuards.markPreRemoved(key);
                    marked = true;
                    throw new HookFailure();
                } catch (RuntimeException exception) {
                    if (marked) {
                        MultipartRemovalGuards.unmarkPreRemoved(key);
                    }
                    throw exception;
                }
            });
            throw new AssertionError("expected removal failure");
        } catch (HookFailure expected) {
            assertTrue(
                    MultipartRemovalGuards.isThreadStateClearForTests(),
                    "pre-removed exception cleanup clears thread state"
            );
        }
    }

    private static void testPreRemovedMarkerIsBounded() {
        Object level = new Object();
        Object block = new Object();
        MultipartRemovalGuards.MultipartStructureKey first =
                key(level, block, 0);
        MultipartRemovalGuards.MultipartStructureKey last = first;
        int max = MultipartRemovalGuards.maxPreRemovedMarkersForTests();

        MultipartRemovalGuards.markPreRemoved(first);
        for (int i = 1; i < max + 8; i++) {
            last = key(level, block, i);
            MultipartRemovalGuards.markPreRemoved(last);
        }

        assertEquals(
                max,
                MultipartRemovalGuards.preRemovedMarkerCountForTests(),
                "stale pre-removal marker count is bounded"
        );
        assertFalse(
                MultipartRemovalGuards.consumePreRemoved(first),
                "oldest stale pre-removal marker should be pruned"
        );
        assertTrue(
                MultipartRemovalGuards.consumePreRemoved(last),
                "newest pre-removal marker should remain available"
        );
    }

    private static void runIsolated(Runnable test) {
        MultipartRemovalGuards.clearForTests();
        try {
            test.run();
        } finally {
            MultipartRemovalGuards.clearForTests();
        }
    }

    private static MultipartRemovalGuards.MultipartStructureKey key(
            Object level,
            Object block,
            int x
    ) {
        return new MultipartRemovalGuards.MultipartStructureKey(
                level,
                block,
                new BlockPos(x, 64, 0)
        );
    }

    private static void assertEquals(
            int expected,
            int actual,
            String message
    ) {
        if (expected != actual) {
            throw new AssertionError(message
                    + ": expected="
                    + expected
                    + ", actual="
                    + actual);
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

    private static final class HookFailure extends RuntimeException {
    }
}
