package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.preparation.chunk.DungeonChunkLeaseState;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.chunk.DungeonChunkLoadOutcome;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonGeneratedRoom;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteProjectionSource;
import io.github.naimjeg.obeliskdepths.dungeon.territory.DungeonBounds;
import io.github.naimjeg.obeliskdepths.dungeon.tribute.ResolvedTribute;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class DungeonActivationPreflightTest {
    private DungeonActivationPreflightTest() {
    }

    static {
        DungeonAsyncTestSupport.bootstrapMinecraft();
    }

    public static void main(String[] args) {
        missingRequiredContextIsRejected();
        mismatchedClaimIdentityIsRejected();
        leaseCountOrderAndStateAreRejected();
        nonAuthoritativeProjectionIsRejected();
        successfulPlanIsImmutableAndMatchesSnapshot();
    }

    private static void missingRequiredContextIsRejected() {
        Fixture fixture = fixture();
        fixture.context.resolvedSite(null);
        checkFailure(fixture, DungeonActivationCommitFailureReason.INTERNAL_ERROR);

        fixture = fixture();
        fixture.context.preparedDestination(null);
        checkFailure(fixture, DungeonActivationCommitFailureReason.INTERNAL_ERROR);

        fixture = fixture();
        fixture.context.entryChunkPlan(null);
        checkFailure(fixture, DungeonActivationCommitFailureReason.INTERNAL_ERROR);

        fixture = fixture();
        fixture.context.currentClaim(null);
        checkFailure(fixture, DungeonActivationCommitFailureReason.INTERNAL_ERROR);
    }

    private static void mismatchedClaimIdentityIsRejected() {
        Fixture fixture = fixture();
        fixture.context.currentClaim(new DungeonSiteClaim(
                fixture.site.key(), DungeonPreparationJobId.create(), 2L, 0L
        ));
        checkFailure(fixture, DungeonActivationCommitFailureReason.SITE_CLAIM_LOST);

        fixture = fixture();
        fixture.context.currentClaim(new DungeonSiteClaim(
                new DungeonSiteKey(9, 9), fixture.job.id(), 2L, 0L
        ));
        checkFailure(fixture, DungeonActivationCommitFailureReason.SITE_CLAIM_LOST);

        fixture = fixture();
        fixture.context.currentClaim(new DungeonSiteClaim(
                fixture.site.key(), fixture.job.id(), 0L, 0L
        ));
        checkFailure(fixture, DungeonActivationCommitFailureReason.SITE_CLAIM_LOST);
    }

    private static void leaseCountOrderAndStateAreRejected() {
        Fixture fixture = fixture();
        fixture.context.clearEntryChunkLeases();
        checkFailure(fixture, DungeonActivationCommitFailureReason.INTERNAL_ERROR);

        fixture = fixture();
        fixture.context.clearEntryChunkLeases();
        fixture.context.addEntryChunkLease(new FakeLease(
                fixture.plan.chunks().get(1), DungeonChunkLeaseState.READY
        ));
        fixture.context.addEntryChunkLease(new FakeLease(
                fixture.plan.chunks().get(0), DungeonChunkLeaseState.READY
        ));
        checkFailure(fixture, DungeonActivationCommitFailureReason.INTERNAL_ERROR);

        fixture = fixture();
        fixture.context.clearEntryChunkLeases();
        fixture.context.addEntryChunkLease(new FakeLease(
                fixture.plan.chunks().get(0), DungeonChunkLeaseState.READY
        ));
        fixture.context.addEntryChunkLease(new FakeLease(
                fixture.plan.chunks().get(1), DungeonChunkLeaseState.PENDING
        ));
        checkFailure(fixture, DungeonActivationCommitFailureReason.INTERNAL_ERROR);
    }

    private static void nonAuthoritativeProjectionIsRejected() {
        Fixture fixture = fixture();
        DungeonActivationPreflightResult result = DungeonActivationPreflight.prepare(
                fixture.job,
                fixture.context,
                DungeonSiteProjectionSource.PLANNED_PROTOTYPE
        );
        check(result.failureReason().orElseThrow()
                        == DungeonActivationCommitFailureReason.NON_AUTHORITATIVE_SITE,
                "non-authoritative projection rejected");
    }

    private static void successfulPlanIsImmutableAndMatchesSnapshot() {
        Fixture fixture = fixture();
        DungeonActivationCommitPlan plan = DungeonActivationPreflight.prepare(
                fixture.job, fixture.context
        ).plan().orElseThrow();

        check(plan.matches(fixture.job, fixture.context),
                "successful plan matches job/context snapshot");
        check(plan.resolvedSite().authoritative(), "successful plan is authoritative");
        check(plan.claimIdentity().token() == 1L, "claim token frozen");
        try {
            plan.expectedEntryChunks().add(new ChunkPos(2, 0));
            throw new AssertionError("plan chunk list must be immutable");
        } catch (UnsupportedOperationException expected) {
        }
        fixture.context.clearEntryChunkLeases();
        check(plan.expectedEntryChunks().equals(fixture.plan.chunks()),
                "plan retains immutable chunk snapshot after context mutation");
    }

    private static void checkFailure(
            Fixture fixture,
            DungeonActivationCommitFailureReason reason
    ) {
        DungeonActivationPreflightResult result = DungeonActivationPreflight.prepare(
                fixture.job, fixture.context
        );
        check(result.plan().isEmpty() && result.failureReason().orElseThrow() == reason,
                "preflight failure reason " + reason);
    }

    private static Fixture fixture() {
        DungeonPreparationJobId jobId = DungeonPreparationJobId.create();
        DungeonPreparationRequest request = DungeonPreparationRequest.forTests(
                UUID.randomUUID(),
                ResourceKey.create(
                        Registries.DIMENSION,
                        Identifier.fromNamespaceAndPath("minecraft", "overworld")
                ),
                new BlockPos(0, 64, 0),
                new ResolvedTribute(true, 1, 1, 0.0F, 1.0F, 1),
                7
        );
        DungeonPreparationJob job = new DungeonPreparationJob(jobId, request, 0L);
        DungeonSite site = site();
        ChunkPos first = new ChunkPos(0, 0);
        ChunkPos second = new ChunkPos(1, 0);
        DungeonEntryChunkPlan plan = new DungeonEntryChunkPlan(
                first, second, first, second, List.of(first, second)
        );
        DungeonPreparationExecutionContext context =
                new DungeonPreparationExecutionContext(jobId);
        context.resolvedSite(site);
        context.preparedDestination(new PreparedDungeonDestination(
                new Vec3(1.5D, 1.0D, 1.5D)
        ));
        context.entryChunkPlan(plan);
        context.currentClaim(new DungeonSiteClaim(
                site.key(), jobId, 1L, 0L
        ));
        context.addEntryChunkLease(new FakeLease(first, DungeonChunkLeaseState.READY));
        context.addEntryChunkLease(new FakeLease(second, DungeonChunkLeaseState.READY));
        return new Fixture(job, context, site, plan);
    }

    private static DungeonSite site() {
        DungeonSiteKey key = new DungeonSiteKey(0, 0);
        DungeonRoomId roomId = DungeonRoomId.of("start");
        DungeonBounds bounds = new DungeonBounds(0, 0, 0, 20, 4, 4);
        BlockPos anchor = new BlockPos(1, 1, 1);
        return new DungeonSite(
                key,
                bounds,
                roomId,
                anchor,
                List.of(new DungeonGeneratedRoom(
                        roomId, DungeonRoomType.START, bounds, anchor
                ))
        );
    }

    private record Fixture(
            DungeonPreparationJob job,
            DungeonPreparationExecutionContext context,
            DungeonSite site,
            DungeonEntryChunkPlan plan
    ) {
    }

    private static final class FakeLease implements DungeonPreparationStartChunkLease {
        private final ChunkPos chunkPos;
        private final DungeonChunkLeaseState state;

        private FakeLease(ChunkPos chunkPos, DungeonChunkLeaseState state) {
            this.chunkPos = chunkPos;
            this.state = state;
        }

        @Override
        public ChunkPos chunkPos() {
            return this.chunkPos;
        }

        @Override
        public DungeonChunkLeaseState state() {
            return this.state;
        }

        @Override
        public Optional<DungeonChunkLoadOutcome> outcome() {
            return Optional.empty();
        }

        @Override
        public void close() {
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
