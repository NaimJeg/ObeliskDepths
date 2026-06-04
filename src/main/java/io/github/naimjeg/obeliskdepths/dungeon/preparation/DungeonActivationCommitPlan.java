package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.session.SessionAccessPolicy;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import io.github.naimjeg.obeliskdepths.dungeon.site.ResolvedDungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.tribute.ResolvedTribute;
import io.github.naimjeg.obeliskdepths.dungeon.tribute.TributeFingerprint;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable data-only plan prepared before the atomic activation tick. */
public record DungeonActivationCommitPlan(
        DungeonPreparationJobId jobId,
        UUID playerId,
        ResourceKey<Level> sourceDimension,
        BlockPos obeliskPos,
        SessionAccessPolicy accessPolicy,
        int sourceContainerId,
        TributeFingerprint tributeFingerprint,
        ResolvedTribute expectedTribute,
        ResolvedDungeonSite resolvedSite,
        PreparedDungeonDestination preparedDestination,
        DungeonEntryChunkPlan entryChunkPlan,
        DungeonSiteKey expectedSiteKey,
        DungeonSiteClaimIdentity claimIdentity,
        List<ChunkPos> expectedEntryChunks
) {
    public DungeonActivationCommitPlan {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(sourceDimension, "sourceDimension");
        obeliskPos = Objects.requireNonNull(obeliskPos, "obeliskPos").immutable();
        Objects.requireNonNull(accessPolicy, "accessPolicy");
        Objects.requireNonNull(tributeFingerprint, "tributeFingerprint");
        Objects.requireNonNull(expectedTribute, "expectedTribute");
        Objects.requireNonNull(resolvedSite, "resolvedSite");
        Objects.requireNonNull(preparedDestination, "preparedDestination");
        Objects.requireNonNull(entryChunkPlan, "entryChunkPlan");
        Objects.requireNonNull(expectedSiteKey, "expectedSiteKey");
        Objects.requireNonNull(claimIdentity, "claimIdentity");
        expectedEntryChunks = List.copyOf(expectedEntryChunks);
        if (!expectedTribute.valid()) {
            throw new IllegalArgumentException("Commit-plan tribute must be valid");
        }
        if (!resolvedSite.authoritative()
                || !resolvedSite.site().key().equals(expectedSiteKey)
                || !claimIdentity.siteKey().equals(expectedSiteKey)
                || !entryChunkPlan.chunks().equals(expectedEntryChunks)) {
            throw new IllegalArgumentException("Commit-plan identity mismatch");
        }
    }

    boolean matches(
            DungeonPreparationJob job,
            DungeonPreparationExecutionContext context
    ) {
        DungeonPreparationRequest request = job.request();
        return this.jobId.equals(job.id())
                && this.playerId.equals(request.playerId())
                && this.sourceDimension.equals(request.sourceDimension())
                && this.obeliskPos.equals(request.obeliskPos())
                && this.accessPolicy == request.accessPolicy()
                && this.sourceContainerId == request.sourceContainerId()
                && this.tributeFingerprint.equals(request.tributeFingerprint())
                && this.expectedTribute.equals(request.expectedTribute())
                && context.resolvedSite().map(this.resolvedSite.site()::equals).orElse(false)
                && context.preparedDestination().map(this.preparedDestination::equals).orElse(false)
                && context.entryChunkPlan()
                        .map(plan -> samePlan(this.entryChunkPlan, plan))
                        .orElse(false)
                && this.claimIdentity.matches(context.currentClaim());
    }

    private static boolean samePlan(
            DungeonEntryChunkPlan first,
            DungeonEntryChunkPlan second
    ) {
        return first.roomMinChunk().equals(second.roomMinChunk())
                && first.roomMaxChunk().equals(second.roomMaxChunk())
                && first.requestedMinChunk().equals(second.requestedMinChunk())
                && first.requestedMaxChunk().equals(second.requestedMaxChunk())
                && first.chunks().equals(second.chunks());
    }
}
