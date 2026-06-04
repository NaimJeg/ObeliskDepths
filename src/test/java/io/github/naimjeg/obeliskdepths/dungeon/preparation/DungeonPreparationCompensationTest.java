package io.github.naimjeg.obeliskdepths.dungeon.preparation;

public final class DungeonPreparationCompensationTest {

    private DungeonPreparationCompensationTest() {
    }

    public static void main(String[] args) {
        allActionsExecuteInOrder();
        cleanupErrorIsNotSwallowed();
        multipleCleanupErrors();
        runtimeFailureAfterCleanupError();
        nullValidation();
    }

    private static void allActionsExecuteInOrder() {
        RuntimeException primary = new RuntimeException("primary");
        RuntimeException a = new RuntimeException("A");
        RuntimeException b = new RuntimeException("B");

        OrderedAction action1 = new OrderedAction(1, a);
        CountingAction action2 = new CountingAction(2);
        OrderedAction action3 = new OrderedAction(3, b);

        DungeonPreparationCompensation.runAll(primary, action1, action2, action3);

        check(action1.executed == 1, "all actions: action 1 executed");
        check(action2.executed == 1, "all actions: action 2 executed");
        check(action3.executed == 1, "all actions: action 3 executed");
        check(action1.order < action2.order
                        && action2.order < action3.order,
                "all actions: execute in order 1 < 2 < 3");

        Throwable[] suppressed = primary.getSuppressed();
        check(suppressed.length == 2, "all actions: two suppressed");
        check(suppressed[0] == a, "all actions: A first suppressed");
        check(suppressed[1] == b, "all actions: B second suppressed");
        check(primary.getMessage().equals("primary"),
                "all actions: primary unchanged");
    }

    private static void cleanupErrorIsNotSwallowed() {
        RuntimeException primary = new RuntimeException("primary");
        Error e = new AssertionError("E");

        OrderedAction errorAction = new OrderedAction(1, e);
        CountingAction safeAction = new CountingAction(2);

        try {
            DungeonPreparationCompensation.runAll(primary, errorAction, safeAction);
            check(false, "error not swallowed: should have thrown");
        } catch (Error thrown) {
            check(thrown == e, "error not swallowed: exact Error E thrown");
            check(thrown.getSuppressed().length == 1,
                    "error not swallowed: primary suppressed on E");
            check(thrown.getSuppressed()[0] == primary,
                    "error not swallowed: primary exactly suppressed");
        }

        check(errorAction.executed == 1, "error not swallowed: action 1 executed");
        check(safeAction.executed == 1,
                "error not swallowed: action 2 still executes after Error");
    }

    private static void multipleCleanupErrors() {
        RuntimeException primary = new RuntimeException("primary");
        Error e1 = new AssertionError("E1");
        Error e2 = new AssertionError("E2");

        OrderedAction errorAction1 = new OrderedAction(1, e1);
        OrderedAction errorAction2 = new OrderedAction(2, e2);

        try {
            DungeonPreparationCompensation.runAll(primary, errorAction1, errorAction2);
            check(false, "multiple errors: should have thrown");
        } catch (Error thrown) {
            check(thrown == e1, "multiple errors: first Error thrown");

            Throwable[] suppressed = thrown.getSuppressed();
            check(suppressed.length == 2,
                    "multiple errors: two suppressed on first Error");
            check(suppressed[0] == e2,
                    "multiple errors: second Error suppressed on first");
            check(suppressed[1] == primary,
                    "multiple errors: primary also suppressed on first");
        }

        check(errorAction1.executed == 1,
                "multiple errors: action 1 executed");
        check(errorAction2.executed == 1,
                "multiple errors: action 2 executed");
    }

    private static void runtimeFailureAfterCleanupError() {
        RuntimeException primary = new RuntimeException("primary");
        Error e = new AssertionError("E");
        RuntimeException r = new RuntimeException("R");

        OrderedAction errorAction = new OrderedAction(1, e);
        OrderedAction runtimeAction = new OrderedAction(2, r);

        try {
            DungeonPreparationCompensation.runAll(primary, errorAction, runtimeAction);
            check(false, "runtime after error: should have thrown");
        } catch (Error thrown) {
            check(thrown == e, "runtime after error: Error E thrown");

            Throwable[] suppressed = thrown.getSuppressed();
            check(suppressed.length == 2,
                    "runtime after error: two suppressed on Error");
        }

        check(errorAction.executed == 1, "runtime after error: action 1 executed");
        check(runtimeAction.executed == 1, "runtime after error: action 2 executed");

        check(primary.getSuppressed().length == 0,
                "runtime after error: R not attached to primary");
    }

    private static void nullValidation() {
        RuntimeException primary = new RuntimeException("primary");
        CountingAction safe = new CountingAction(1);

        try {
            DungeonPreparationCompensation.runAll(null, safe);
            check(false, "null primary: should throw NPE");
        } catch (NullPointerException e) {
            check(e.getMessage().contains("primaryFailure"),
                    "null primary: NPE message");
        }

        try {
            DungeonPreparationCompensation.runAll(primary, (Runnable[]) null);
            check(false, "null array: should throw NPE");
        } catch (NullPointerException e) {
            check(e.getMessage().contains("cleanupActions"),
                    "null array: NPE message");
        }

        try {
            DungeonPreparationCompensation.runAll(primary, safe, null);
            check(false, "null element: should throw NPE");
        } catch (NullPointerException e) {
            check(e.getMessage().contains("cleanupAction"),
                    "null element: NPE message");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class CountingAction implements Runnable {
        final int id;
        int executed;
        int order;

        CountingAction(int id) {
            this.id = id;
        }

        @Override
        public void run() {
            this.executed++;
            this.order = globalOrder++;
        }
    }

    private static final class OrderedAction implements Runnable {
        final int id;
        int executed;
        int order;
        final RuntimeException runtimeException;
        final Error error;

        OrderedAction(int id, RuntimeException runtimeException) {
            this.id = id;
            this.runtimeException = runtimeException;
            this.error = null;
        }

        OrderedAction(int id, Error error) {
            this.id = id;
            this.runtimeException = null;
            this.error = error;
        }

        @Override
        public void run() {
            this.executed++;
            this.order = globalOrder++;
            if (this.runtimeException != null) {
                throw this.runtimeException;
            }
            if (this.error != null) {
                throw this.error;
            }
        }
    }

    private static int globalOrder;
}
