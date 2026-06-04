package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.preparation.chunk.DungeonChunkLeaseState;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteProjectionSource;
import io.github.naimjeg.obeliskdepths.dungeon.site.ResolvedDungeonSite;

import java.util.List;
import java.util.Objects;

public final class DungeonActivationPreflight {
    private DungeonActivationPreflight() {
    }

    public static DungeonActivationPreflightResult prepare(
            DungeonPreparationJob job,
            DungeonPreparationExecutionContext context
    ) {
        return prepare(
                job,
                context,
                DungeonSiteProjectionSource.GENERATED_STRUCTURE_START
        );
    }

    static DungeonActivationPreflightResult prepare(
            DungeonPreparationJob job,
            DungeonPreparationExecutionContext context,
            DungeonSiteProjectionSource projectionSource
    ) {
        Objects.requireNonNull(projectionSource, "projectionSource");
        DungeonSite site = context.resolvedSite().orElse(null);
        PreparedDungeonDestination destination =
                context.preparedDestination().orElse(null);
        DungeonEntryChunkPlan entryPlan = context.entryChunkPlan().orElse(null);
        DungeonSiteClaim claim = context.currentClaim();
        if (site == null || destination == null || entryPlan == null || claim == null) {
            return DungeonActivationPreflightResult.failure(
                    DungeonActivationCommitFailureReason.INTERNAL_ERROR,
                    "preflight requires site, destination, entry plan, and claim"
            );
        }
        if (!claim.key().equals(site.key())
                || !claim.ownerJobId().equals(job.id())
                || claim.token() <= 0L) {
            return DungeonActivationPreflightResult.failure(
                    DungeonActivationCommitFailureReason.SITE_CLAIM_LOST,
                    "preflight claim identity mismatch"
            );
        }
        List<DungeonPreparationStartChunkLease> leases = context.entryChunkLeases();
        if (leases.size() != entryPlan.chunks().size()) {
            return DungeonActivationPreflightResult.failure(
                    DungeonActivationCommitFailureReason.INTERNAL_ERROR,
                    "preflight entry lease count mismatch"
            );
        }
        for (int index = 0; index < leases.size(); index++) {
            DungeonPreparationStartChunkLease lease = leases.get(index);
            if (!lease.chunkPos().equals(entryPlan.chunks().get(index))
                    || lease.state() != DungeonChunkLeaseState.READY) {
                return DungeonActivationPreflightResult.failure(
                        DungeonActivationCommitFailureReason.INTERNAL_ERROR,
                        "preflight entry lease is not ready at index " + index
                );
            }
        }

        DungeonPreparationRequest request = job.request();
        ResolvedDungeonSite resolvedSite = new ResolvedDungeonSite(
                site,
                projectionSource
        );
        if (!resolvedSite.authoritative()) {
            return DungeonActivationPreflightResult.failure(
                    DungeonActivationCommitFailureReason.NON_AUTHORITATIVE_SITE,
                    resolvedSite.source().name()
            );
        }
        return DungeonActivationPreflightResult.success(
                new DungeonActivationCommitPlan(
                        job.id(),
                        request.playerId(),
                        request.sourceDimension(),
                        request.obeliskPos(),
                        request.sourceContainerId(),
                        request.tributeFingerprint(),
                        request.expectedTribute(),
                        resolvedSite,
                        destination,
                        entryPlan,
                        site.key(),
                        DungeonSiteClaimIdentity.from(claim),
                        entryPlan.chunks()
                )
        );
    }
}
