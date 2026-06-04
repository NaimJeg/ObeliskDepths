
package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.id.PortalSessionId;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import net.minecraft.world.level.ChunkPos;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A prepared portal entry that owns exactly one
 * {@link DungeonPreparationLeaseBundle} and a prepared destination.
 *
 * <p>The entry is {@link AutoCloseable} so that lease bundles can be
 * cleaned up when entries are evicted from the registry.  The lease
 * bundle is closed exactly once; repeated calls to {@link #close()} are
 * harmless.</p>
 *
 * <p>This type is transient and never serialized.</p>
 */
public final class DungeonPreparedPortalEntry implements AutoCloseable {
    private static final long STALE_AFTER_TICKS = 20L * 60L;

    private final PortalSessionId portalSessionId;
    private final DungeonInstanceId instanceId;
    private final DungeonSiteKey siteKey;
    private final PreparedDungeonDestination destination;
    private final List<ChunkPos> entryChunks;
    private final DungeonPreparationLeaseBundle leaseBundle;
    private final long preparedAtGameTime;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public DungeonPreparedPortalEntry(
            PortalSessionId portalSessionId,
            DungeonInstanceId instanceId,
            DungeonSiteKey siteKey,
            PreparedDungeonDestination destination,
            List<ChunkPos> entryChunks,
            DungeonPreparationLeaseBundle leaseBundle,
            long preparedAtGameTime
    ) {
        this.portalSessionId = Objects.requireNonNull(portalSessionId, "portalSessionId");
        this.instanceId = Objects.requireNonNull(instanceId, "instanceId");
        this.siteKey = Objects.requireNonNull(siteKey, "siteKey");
        this.destination = Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(entryChunks, "entryChunks");
        this.leaseBundle = Objects.requireNonNull(leaseBundle, "leaseBundle");

        for (ChunkPos chunkPos : entryChunks) {
            Objects.requireNonNull(chunkPos, "entryChunks element");
        }
        Set<ChunkPos> unique = new HashSet<>(entryChunks);
        if (unique.size() != entryChunks.size()) {
            throw new IllegalArgumentException("entry chunks must be unique");
        }
        List<ChunkPos> sorted = new ArrayList<>(entryChunks);
        sorted.sort(Comparator.comparingInt(ChunkPos::x)
                .thenComparingInt(ChunkPos::z));
        this.entryChunks = List.copyOf(sorted);
        this.preparedAtGameTime = preparedAtGameTime;
    }

    public PortalSessionId portalSessionId() {
        return this.portalSessionId;
    }

    public DungeonInstanceId instanceId() {
        return this.instanceId;
    }

    public DungeonSiteKey siteKey() {
        return this.siteKey;
    }

    public PreparedDungeonDestination destination() {
        return this.destination;
    }

    public List<ChunkPos> entryChunks() {
        return this.entryChunks;
    }

    public long preparedAtGameTime() {
        return this.preparedAtGameTime;
    }

    public boolean isClosed() {
        return this.closed.get();
    }

    public boolean isStale(long gameTime) {
        return gameTime - this.preparedAtGameTime >= STALE_AFTER_TICKS;
    }

    /**
     * Closes the owned lease bundle exactly once.
     */
    @Override
    public void close() {
        if (this.closed.compareAndSet(false, true)) {
            this.leaseBundle.close();
        }
    }
}
