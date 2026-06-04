package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns zero or more {@link AutoCloseable} preparation leases.
 *
 * <p>Membership is immutable after construction.  Closing the bundle
 * closes every owned lease exactly once.  The bundle cannot be copied
 * into two independent owners.
 */
public final class DungeonPreparationLeaseBundle implements AutoCloseable {
    private final List<AutoCloseable> leases;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    DungeonPreparationLeaseBundle(List<AutoCloseable> leases) {
        Objects.requireNonNull(leases, "leases");
        ArrayList<AutoCloseable> copy = new ArrayList<>(leases.size());
        for (AutoCloseable lease : leases) {
            copy.add(Objects.requireNonNull(lease, "lease"));
        }
        this.leases = Collections.unmodifiableList(copy);
    }

    /**
     * Returns an unmodifiable view of the contained leases.
     */
    public List<AutoCloseable> leases() {
        return this.leases;
    }

    /**
     * Closes every owned lease exactly once.
     *
     * <p>Ordinary {@link Exception} failures from individual lease
     * {@code close()} calls are aggregated and reported as a single
     * {@link RuntimeException}. If one or more leases throw an {@link Error},
     * every remaining lease is still attempted before the first Error is
     * rethrown with the other failures suppressed.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        RuntimeException aggregateFailure = null;
        Error firstError = null;
        for (AutoCloseable lease : this.leases) {
            try {
                lease.close();
            } catch (Exception exception) {
                if (firstError != null) {
                    addSuppressed(firstError, exception);
                } else {
                    if (aggregateFailure == null) {
                        aggregateFailure = new IllegalStateException(
                                "Failed to close one or more preparation leases"
                        );
                    }
                    aggregateFailure.addSuppressed(exception);
                }
            } catch (Error error) {
                if (firstError == null) {
                    firstError = error;
                    if (aggregateFailure != null) {
                        addSuppressed(firstError, aggregateFailure);
                        aggregateFailure = null;
                    }
                } else {
                    addSuppressed(firstError, error);
                }
            }
        }

        if (firstError != null) {
            throw firstError;
        }
        if (aggregateFailure != null) {
            throw aggregateFailure;
        }
    }

    private static void addSuppressed(Throwable target, Throwable failure) {
        if (target != failure) {
            target.addSuppressed(failure);
        }
    }
}
