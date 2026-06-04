package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import java.util.Objects;

/**
 * An ownership-safe transient claim on a dungeon site candidate.
 *
 * <p>Each claim is identified by its owning job, a unique monotonically
 * increasing token, and the claimed site key.  The token prevents a
 * stale claim release from removing a replacement claim.</p>
 *
 * <p>Claims are never persisted across server restart.</p>
 */
public final class DungeonSiteClaim {
    private final DungeonSiteKey key;
    private final DungeonPreparationJobId ownerJobId;
    private final long token;
    private final long acquiredAtGameTime;

    DungeonSiteClaim(
            DungeonSiteKey key,
            DungeonPreparationJobId ownerJobId,
            long token,
            long acquiredAtGameTime
    ) {
        this.key = Objects.requireNonNull(key, "key");
        this.ownerJobId = Objects.requireNonNull(ownerJobId, "ownerJobId");
        this.token = token;
        this.acquiredAtGameTime = acquiredAtGameTime;
    }

    public DungeonSiteKey key() { return this.key; }
    public DungeonPreparationJobId ownerJobId() { return this.ownerJobId; }
    public long token() { return this.token; }
    public long acquiredAtGameTime() { return this.acquiredAtGameTime; }
}
