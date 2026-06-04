package io.github.naimjeg.obeliskdepths.dungeon.interaction;

import java.util.ArrayList;
import java.util.List;

/** Failure accumulator for the independent pre-teleport rollback actions. */
final class PortalEntryRollback {
    private final List<CleanupFailure> ordinaryFailures = new ArrayList<>();
    private Error firstError;

    void attempt(String step, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            this.ordinaryFailures.add(new CleanupFailure(step, failure));
        } catch (Error failure) {
            if (this.firstError == null) {
                this.firstError = failure;
                addSuppressedIfDistinct(
                        failure,
                        new CleanupErrorStep(step)
                );
            } else {
                addSuppressedIfDistinct(
                        this.firstError,
                        new CleanupFailure(step, failure)
                );
            }
        }
    }

    void finish(Throwable originalFailure) {
        if (this.firstError != null) {
            for (CleanupFailure failure : this.ordinaryFailures) {
                addSuppressedIfDistinct(this.firstError, failure);
            }
            if (originalFailure != null) {
                addSuppressedIfDistinct(this.firstError, originalFailure);
            }
            throw this.firstError;
        }
        if (originalFailure != null) {
            for (CleanupFailure failure : this.ordinaryFailures) {
                addSuppressedIfDistinct(originalFailure, failure);
            }
            return;
        }
        if (!this.ordinaryFailures.isEmpty()) {
            IllegalStateException aggregate = new IllegalStateException(
                    "One or more portal-entry rollback steps failed"
            );
            for (CleanupFailure failure : this.ordinaryFailures) {
                aggregate.addSuppressed(failure);
            }
            throw aggregate;
        }
    }

    private static void addSuppressedIfDistinct(
            Throwable target,
            Throwable suppressed
    ) {
        if (target != suppressed) {
            target.addSuppressed(suppressed);
        }
    }

    static final class CleanupFailure extends RuntimeException {
        private final String step;

        private CleanupFailure(String step, Throwable cause) {
            super("Portal-entry rollback failed: " + step, cause);
            this.step = step;
        }

        String step() {
            return this.step;
        }
    }

    static final class CleanupErrorStep extends RuntimeException {
        private final String step;

        private CleanupErrorStep(String step) {
            super("Portal-entry rollback Error at step: " + step);
            this.step = step;
        }

        String step() {
            return this.step;
        }
    }
}
