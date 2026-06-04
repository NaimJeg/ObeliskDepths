package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;

import java.util.Objects;

public record DungeonSiteClaimIdentity(
        DungeonSiteKey siteKey,
        DungeonPreparationJobId ownerJobId,
        long token
) {
    public DungeonSiteClaimIdentity {
        Objects.requireNonNull(siteKey, "siteKey");
        Objects.requireNonNull(ownerJobId, "ownerJobId");
    }

    static DungeonSiteClaimIdentity from(DungeonSiteClaim claim) {
        return new DungeonSiteClaimIdentity(
                claim.key(),
                claim.ownerJobId(),
                claim.token()
        );
    }

    boolean matches(DungeonSiteClaim claim) {
        return claim != null
                && this.siteKey.equals(claim.key())
                && this.ownerJobId.equals(claim.ownerJobId())
                && this.token == claim.token();
    }
}
