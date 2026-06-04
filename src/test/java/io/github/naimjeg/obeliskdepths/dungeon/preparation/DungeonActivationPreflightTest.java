package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonTerritoryId;
import io.github.naimjeg.obeliskdepths.dungeon.id.PortalSessionId;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonDifficulty;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.portal.PortalSession;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSession;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.chunk.DungeonChunkLeaseState;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.chunk.DungeonChunkLoadOutcome;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.session.SessionAccessPolicy;
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
    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(
                    Registries.DIMENSION,
                    Identifier.fromNamespaceAndPath("minecraft", "overworld")
            );

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
        accessPolicyIsFrozenIntoCommitPlan();
        policyIsPartOfCommitIdentity();
        openPlanPropagatesThroughPortalAndDungeonSession();
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

    private static void accessPolicyIsFrozenIntoCommitPlan() {
        Fixture starter = fixture();
        DungeonActivationCommitPlan starterPlan =
                DungeonActivationPreflight.prepare(
                        starter.job,
                        starter.context
                ).plan().orElseThrow();
        check(
                starterPlan.accessPolicy() == SessionAccessPolicy.STARTER_ONLY,
                "starter-only fixture freezes starter-only policy"
        );
        check(
                starterPlan.matches(starter.job, starter.context),
                "starter-only plan matches its source request"
        );

        Fixture open = fixture(SessionAccessPolicy.OPEN);
        DungeonActivationCommitPlan openPlan =
                DungeonActivationPreflight.prepare(
                        open.job,
                        open.context
                ).plan().orElseThrow();
        check(
                openPlan.accessPolicy() == SessionAccessPolicy.OPEN,
                "preflight preserves request access policy"
        );
        check(
                openPlan.matches(open.job, open.context),
                "plan with preserved access policy matches source request"
        );
    }

    private static void policyIsPartOfCommitIdentity() {
        Fixture fixture = fixture();
        DungeonActivationCommitPlan plan =
                DungeonActivationPreflight.prepare(
                        fixture.job,
                        fixture.context
                ).plan().orElseThrow();
        DungeonPreparationRequest request = fixture.job.request();
        DungeonPreparationRequest differentPolicy =
                DungeonPreparationRequest.forTests(
                        request.playerId(),
                        request.sourceDimension(),
                        request.obeliskPos(),
                        SessionAccessPolicy.OPEN,
                        request.expectedTribute(),
                        request.sourceContainerId()
                );
        DungeonPreparationJob otherJob =
                new DungeonPreparationJob(
                        fixture.job.id(),
                        differentPolicy,
                        fixture.job.createdAtGameTime()
                );
        check(
                !plan.matches(otherJob, fixture.context),
                "commit plan policy is part of immutable commit identity"
        );
    }

    private static void openPlanPropagatesThroughPortalAndDungeonSession() {
        Fixture open = fixture(SessionAccessPolicy.OPEN);
        DungeonActivationCommitPlan plan =
                DungeonActivationPreflight.prepare(
                        open.job,
                        open.context
                ).plan().orElseThrow();
        check(plan.accessPolicy() == SessionAccessPolicy.OPEN,
                "open commit plan carries OPEN");

        UUID opener = UUID.randomUUID();
        DungeonInstanceId instanceId = new DungeonInstanceId(UUID.randomUUID());
        DungeonSiteKey siteKey = new DungeonSiteKey(7, 7);
        DungeonInstance instance = new DungeonInstance(
                instanceId,
                siteKey,
                new DungeonDifficulty(1, 0.0F, 1.0F, 1),
                new DungeonTerritoryId(UUID.randomUUID()),
                new BlockPos(0, 64, 0),
                0L
        );
        PortalSession portal = new PortalSession(
                new PortalSessionId(UUID.randomUUID()),
                instanceId,
                opener,
                OVERWORLD,
                new BlockPos(10, 64, 10),
                new BlockPos(12, 64, 10),
                plan.accessPolicy(),
                1000L
        );
        check(portal.accessPolicy() == SessionAccessPolicy.OPEN,
                "backend plan policy persists into PortalSession");

        DungeonSession session = DungeonSession.createActive(
                instance,
                opener,
                portal.accessPolicy(),
                false,
                1L
        );
        check(session.accessPolicy() == SessionAccessPolicy.OPEN,
                "PortalSession policy propagates to DungeonSession");
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
        return fixture(SessionAccessPolicy.STARTER_ONLY);
    }

    private static Fixture fixture(SessionAccessPolicy accessPolicy) {
        DungeonPreparationJobId jobId = DungeonPreparationJobId.create();
        DungeonPreparationRequest request = DungeonPreparationRequest.forTests(
                UUID.randomUUID(),
                OVERWORLD,
                new BlockPos(0, 64, 0),
                accessPolicy,
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
