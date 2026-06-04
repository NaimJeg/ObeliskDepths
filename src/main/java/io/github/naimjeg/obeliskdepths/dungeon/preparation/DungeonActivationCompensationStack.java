package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/** Owner-thread reverse compensation stack scoped to one activation tick. */
public final class DungeonActivationCompensationStack {
    private final BooleanSupplier ownerThread;
    private final ArrayDeque<Step> steps = new ArrayDeque<>();
    private boolean committed;
    private boolean rolledBack;

    public DungeonActivationCompensationStack(BooleanSupplier ownerThread) {
        this.ownerThread = Objects.requireNonNull(ownerThread, "ownerThread");
        assertOwnerThread();
    }

    public void register(String name, ThrowingCompensation compensation) {
        assertOwnerThread();
        if (this.committed || this.rolledBack) {
            throw new IllegalStateException("Compensation stack is terminal");
        }
        this.steps.addLast(new Step(
                Objects.requireNonNull(name, "name"),
                Objects.requireNonNull(compensation, "compensation")
        ));
    }

    public void commit() {
        assertOwnerThread();
        if (this.rolledBack) {
            throw new IllegalStateException("Rolled-back stack cannot commit");
        }
        this.committed = true;
        this.steps.clear();
    }

    void prepareCommit() {
        assertOwnerThread();
        if (this.committed || this.rolledBack) {
            throw new IllegalStateException("Compensation stack is terminal");
        }
    }

    /** Non-throwing seal used immediately after the irreversible mutation. */
    void completePreparedCommit() {
        this.committed = true;
        this.steps.clear();
    }

    public RollbackReport rollback(Throwable originalFailure) {
        assertOwnerThread();
        Objects.requireNonNull(originalFailure, "originalFailure");
        if (this.committed || this.rolledBack) {
            return new RollbackReport(0, 0, List.of());
        }
        this.rolledBack = true;
        int executed = 0;
        int failures = 0;
        ArrayList<String> executedNames = new ArrayList<>();
        ArrayList<CompensationFailure> ordinaryFailures = new ArrayList<>();
        Error firstRollbackError = null;
        String firstRollbackErrorStep = null;
        while (!this.steps.isEmpty()) {
            Step step = this.steps.removeLast();
            executed++;
            executedNames.add(step.name());
            try {
                step.compensation().run();
            } catch (Exception rollbackFailure) {
                failures++;
                ordinaryFailures.add(new CompensationFailure(
                        step.name(), rollbackFailure
                ));
            } catch (Error rollbackError) {
                failures++;
                if (firstRollbackError == null) {
                    firstRollbackError = rollbackError;
                    firstRollbackErrorStep = step.name();
                } else {
                    ordinaryFailures.add(new CompensationFailure(
                            step.name(), rollbackError
                    ));
                }
            }
        }
        if (firstRollbackError != null) {
            addSuppressedIfDistinct(
                    firstRollbackError,
                    new CompensationErrorStep(firstRollbackErrorStep)
            );
            for (CompensationFailure failure : ordinaryFailures) {
                addSuppressedIfDistinct(firstRollbackError, failure);
            }
            addSuppressedIfDistinct(firstRollbackError, originalFailure);
            throw firstRollbackError;
        }
        for (CompensationFailure failure : ordinaryFailures) {
            addSuppressedIfDistinct(originalFailure, failure);
        }
        return new RollbackReport(executed, failures, executedNames);
    }

    private static void addSuppressedIfDistinct(
            Throwable target,
            Throwable suppressed
    ) {
        if (target != suppressed) {
            target.addSuppressed(suppressed);
        }
    }

    int pendingStepCount() {
        assertOwnerThread();
        return this.steps.size();
    }

    private void assertOwnerThread() {
        if (!this.ownerThread.getAsBoolean()) {
            throw new IllegalStateException(
                    "Activation compensation must run on the owner thread"
            );
        }
    }

    @FunctionalInterface
    public interface ThrowingCompensation {
        void run() throws Exception;
    }

    public record RollbackReport(
            int stepsExecuted,
            int failures,
            List<String> stepNames
    ) {
        public RollbackReport {
            stepNames = List.copyOf(stepNames);
        }
    }

    public static final class CompensationFailure extends RuntimeException {
        private final String stepName;

        private CompensationFailure(String stepName, Throwable cause) {
            super("Activation compensation failed: " + stepName, cause);
            this.stepName = stepName;
        }

        public String stepName() {
            return this.stepName;
        }
    }

    public static final class CompensationErrorStep extends RuntimeException {
        private final String stepName;

        private CompensationErrorStep(String stepName) {
            super("Activation compensation Error at step: " + stepName);
            this.stepName = stepName;
        }

        public String stepName() {
            return this.stepName;
        }
    }

    private record Step(String name, ThrowingCompensation compensation) {
    }
}
