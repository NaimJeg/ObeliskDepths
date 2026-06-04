package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Transient server-thread-confined claim manager for dungeon site candidates.
 *
 * <p>Each claim is owned by a preparation job and carries a unique
 * monotonically increasing token.  Releasing a claim succeeds only when
 * key, owner, and token still match, preventing stale releases from
 * removing a replacement claim.</p>
 *
 * <p>Claims are never persisted across server restart.</p>
 */
public final class DungeonSiteClaimManager {
    private final Map<DungeonSiteKey, DungeonSiteClaim> claims = new HashMap<>();
    private final DungeonSiteClaimBackend backend;
    private final AtomicLong nextToken = new AtomicLong(0L);
    private boolean cleared;

    DungeonSiteClaimManager(DungeonSiteClaimBackend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.cleared = false;
    }

    public static DungeonSiteClaimManager createForLevel(ServerLevel level) {
        return new DungeonSiteClaimManager(new ServerClaimThreadBackend(level));
    }

    public Optional<DungeonSiteClaim> tryClaim(
            DungeonSiteKey key,
            DungeonPreparationJobId ownerJobId,
            long gameTime
    ) {
        assertOwnerThread();
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(ownerJobId, "ownerJobId");
        if (this.cleared) {
            throw new IllegalStateException("Claim manager has been cleared");
        }
        if (this.claims.containsKey(key)) {
            return Optional.empty();
        }
        long token = this.nextToken.incrementAndGet();
        DungeonSiteClaim claim = new DungeonSiteClaim(key, ownerJobId, token, gameTime);
        this.claims.put(key, claim);
        return Optional.of(claim);
    }

    public boolean release(DungeonSiteClaim claim) {
        assertOwnerThread();
        Objects.requireNonNull(claim, "claim");
        DungeonSiteClaim current = this.claims.get(claim.key());
        if (current == null
                || !current.ownerJobId().equals(claim.ownerJobId())
                || current.token() != claim.token()) {
            return false;
        }
        this.claims.remove(claim.key(), current);
        return true;
    }

    public boolean restore(DungeonSiteClaim claim) {
        assertOwnerThread();
        Objects.requireNonNull(claim, "claim");
        if (this.cleared || this.claims.containsKey(claim.key())) {
            return false;
        }
        this.claims.put(claim.key(), claim);
        return true;
    }

    public boolean isOwnedBy(
            DungeonSiteKey key,
            DungeonPreparationJobId ownerJobId
    ) {
        assertOwnerThread();
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(ownerJobId, "ownerJobId");
        DungeonSiteClaim claim = this.claims.get(key);
        return claim != null && claim.ownerJobId().equals(ownerJobId);
    }

    public Optional<DungeonSiteClaim> find(DungeonSiteKey key) {
        assertOwnerThread();
        Objects.requireNonNull(key, "key");
        return Optional.ofNullable(this.claims.get(key));
    }

    public void clearAll() {
        assertOwnerThread();
        this.claims.clear();
        this.cleared = true;
    }

    public int activeClaimCount() {
        assertOwnerThread();
        return this.claims.size();
    }

    void assertOwnerThread() {
        if (!this.backend.isOwnerThread()) {
            throw new IllegalStateException(
                    "Claim manager must be accessed on the owning server thread"
            );
        }
    }

    private static final class ServerClaimThreadBackend
            implements DungeonSiteClaimBackend {
        private final ServerLevel level;

        ServerClaimThreadBackend(ServerLevel level) {
            this.level = Objects.requireNonNull(level, "level");
        }

        @Override
        public boolean isOwnerThread() {
            return this.level.getServer().isSameThread();
        }
    }
}
