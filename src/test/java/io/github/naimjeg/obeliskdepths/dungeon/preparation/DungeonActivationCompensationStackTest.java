package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import java.util.ArrayList;
import java.util.List;

public final class DungeonActivationCompensationStackTest {
    private DungeonActivationCompensationStackTest() {
    }

    public static void main(String[] args) {
        rollbackIsStrictlyReverseAndContinuesAfterFailures();
        successfulCommitDisarmsCompensation();
        secondRollbackIsIdempotent();
        offOwnerOperationsAreRejected();
        rollbackErrorIsRethrownAfterRemainingSteps();
    }

    private static void rollbackIsStrictlyReverseAndContinuesAfterFailures() {
        ArrayList<String> order = new ArrayList<>();
        DungeonActivationCompensationStack stack =
                new DungeonActivationCompensationStack(() -> true);
        stack.register("first", () -> order.add("first"));
        stack.register("second", () -> {
            order.add("second");
            throw new IllegalStateException("second failed");
        });
        stack.register("third", () -> {
            order.add("third");
            throw new IllegalStateException("third failed");
        });
        RuntimeException original = new RuntimeException("original");

        DungeonActivationCompensationStack.RollbackReport report =
                stack.rollback(original);

        check(order.equals(List.of("third", "second", "first")),
                "rollback order is strict reverse registration order");
        check(report.stepsExecuted() == 3 && report.failures() == 2,
                "rollback report retains every step and failure");
        check(original.getSuppressed().length == 2,
                "every rollback failure is suppressed on the original");
        check(((DungeonActivationCompensationStack.CompensationFailure)
                        original.getSuppressed()[0]).stepName().equals("third"),
                "rollback failure retains its step name");
    }

    private static void successfulCommitDisarmsCompensation() {
        int[] calls = {0};
        DungeonActivationCompensationStack stack =
                new DungeonActivationCompensationStack(() -> true);
        stack.register("inverse", () -> calls[0]++);
        stack.commit();
        stack.rollback(new RuntimeException("ignored"));
        check(calls[0] == 0, "committed stack cannot compensate");
    }

    private static void secondRollbackIsIdempotent() {
        int[] calls = {0};
        DungeonActivationCompensationStack stack =
                new DungeonActivationCompensationStack(() -> true);
        stack.register("inverse", () -> calls[0]++);
        stack.rollback(new RuntimeException("first"));
        DungeonActivationCompensationStack.RollbackReport second =
                stack.rollback(new RuntimeException("second"));
        check(calls[0] == 1 && second.stepsExecuted() == 0,
                "second rollback performs no work");
    }

    private static void offOwnerOperationsAreRejected() {
        boolean[] owner = {true};
        DungeonActivationCompensationStack stack =
                new DungeonActivationCompensationStack(() -> owner[0]);
        owner[0] = false;
        expectFailure(() -> stack.register("bad", () -> { }),
                "off-owner registration rejected");
        expectFailure(() -> stack.rollback(new RuntimeException("bad")),
                "off-owner rollback rejected");
        expectFailure(stack::commit, "off-owner commit rejected");
    }

    private static void rollbackErrorIsRethrownAfterRemainingSteps() {
        ArrayList<String> order = new ArrayList<>();
        DungeonActivationCompensationStack stack =
                new DungeonActivationCompensationStack(() -> true);
        stack.register("first", () -> order.add("first"));
        LinkageError laterError = new LinkageError("later rollback error");
        stack.register("earlier-fatal", () -> {
            order.add("earlier-fatal");
            throw laterError;
        });
        stack.register("ordinary", () -> {
            order.add("ordinary");
            throw new IllegalStateException("later ordinary failure");
        });
        AssertionError rollbackError = new AssertionError("rollback error");
        stack.register("fatal", () -> {
            order.add("fatal");
            throw rollbackError;
        });
        stack.register("last", () -> order.add("last"));
        RuntimeException original = new RuntimeException("original transaction");

        try {
            stack.rollback(original);
            throw new AssertionError("rollback Error must be rethrown");
        } catch (AssertionError thrown) {
            check(thrown == rollbackError, "first rollback Error identity retained");
        }
        check(order.equals(List.of(
                        "last", "fatal", "ordinary", "earlier-fatal", "first"
                )),
                "all cleanup runs after rollback Error");
        check(List.of(rollbackError.getSuppressed()).contains(original),
                "original transaction failure attached to rollback Error");
        check(java.util.Arrays.stream(rollbackError.getSuppressed())
                        .anyMatch(failure -> failure instanceof
                                DungeonActivationCompensationStack.CompensationFailure
                                compensationFailure
                                && compensationFailure.stepName().equals("ordinary")),
                "later ordinary failure attached with step name");
        check(java.util.Arrays.stream(rollbackError.getSuppressed())
                        .anyMatch(failure -> failure instanceof
                                DungeonActivationCompensationStack.CompensationFailure
                                compensationFailure
                                && compensationFailure.stepName().equals("earlier-fatal")
                                && compensationFailure.getCause() == laterError),
                "later rollback Error attached with step name");
        check(java.util.Arrays.stream(rollbackError.getSuppressed())
                        .anyMatch(failure -> failure instanceof
                                DungeonActivationCompensationStack.CompensationErrorStep
                                errorStep
                                && errorStep.stepName().equals("fatal")),
                "fatal compensation step name retained");
    }

    private static void expectFailure(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
