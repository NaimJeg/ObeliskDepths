package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Level-owned permit pool for actual outstanding persisted-chunk probe futures.
 *
 * <p>A permit remains occupied until the backend future completes, even when
 * the owning logical scanner is cancelled. This prevents cancelled scanners
 * from temporarily multiplying the real storage-I/O concurrency limit.</p>
 */
final class DungeonPersistedProbePermitPool {
    private final int maximumOutstanding;
    private final AtomicInteger outstanding = new AtomicInteger();

    DungeonPersistedProbePermitPool(int maximumOutstanding) {
        if (maximumOutstanding < 0) {
            throw new IllegalArgumentException(
                    "maximumOutstanding must be non-negative"
            );
        }
        this.maximumOutstanding = maximumOutstanding;
    }

    Permit tryAcquire() {
        while (true) {
            int current = this.outstanding.get();
            if (current >= this.maximumOutstanding) {
                return null;
            }
            if (this.outstanding.compareAndSet(current, current + 1)) {
                return new Permit(this);
            }
        }
    }

    int outstandingCount() {
        return this.outstanding.get();
    }

    int maximumOutstanding() {
        return this.maximumOutstanding;
    }

    int remainingCapacity() {
        return Math.max(0, this.maximumOutstanding - outstandingCount());
    }

    private void release() {
        int remaining = this.outstanding.decrementAndGet();
        if (remaining < 0) {
            this.outstanding.incrementAndGet();
            throw new IllegalStateException(
                    "Persisted-probe permit count became negative"
            );
        }
    }

    static final class Permit implements AutoCloseable {
        private final DungeonPersistedProbePermitPool owner;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Permit(DungeonPersistedProbePermitPool owner) {
            this.owner = owner;
        }

        @Override
        public void close() {
            if (this.closed.compareAndSet(false, true)) {
                this.owner.release();
            }
        }
    }
}
