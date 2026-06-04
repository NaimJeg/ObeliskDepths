package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import java.util.Objects;

/**
 * Small owner-thread cleanup helper for preparation acquisitions made before
 * activation preflight (for example start-chunk and entry-lease acquisition).
 * It is not the activation transaction mechanism; committed activation uses
 * {@link DungeonActivationCompensationStack}.
 *
 * <p>Every supplied cleanup action is attempted. Runtime cleanup failures are
 * attached to the primary failure. An {@link Error} is never swallowed: the
 * first cleanup Error is rethrown after all actions have run, with later
 * cleanup failures and the primary failure attached as suppressed context.</p>
 */
final class DungeonPreparationCompensation {
    private DungeonPreparationCompensation() {
    }

    static void runAll(Throwable primaryFailure, Runnable... cleanupActions) {
        Objects.requireNonNull(primaryFailure, "primaryFailure");
        Objects.requireNonNull(cleanupActions, "cleanupActions");

        Error firstCleanupError = null;
        for (Runnable cleanupAction : cleanupActions) {
            Objects.requireNonNull(cleanupAction, "cleanupAction");
            try {
                cleanupAction.run();
            } catch (RuntimeException cleanupFailure) {
                if (firstCleanupError == null) {
                    primaryFailure.addSuppressed(cleanupFailure);
                } else {
                    firstCleanupError.addSuppressed(cleanupFailure);
                }
            } catch (Error cleanupError) {
                if (firstCleanupError == null) {
                    firstCleanupError = cleanupError;
                } else {
                    firstCleanupError.addSuppressed(cleanupError);
                }
            }
        }

        if (firstCleanupError != null) {
            firstCleanupError.addSuppressed(primaryFailure);
            throw firstCleanupError;
        }
    }
}
