package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.block.ObeliskBlock;
import io.github.naimjeg.obeliskdepths.block.ObeliskPart;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.id.PortalSessionId;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstanceService;
import io.github.naimjeg.obeliskdepths.dungeon.portal.DungeonPortalEntityService;
import io.github.naimjeg.obeliskdepths.dungeon.portal.DungeonPortalSessionLifecycle;
import io.github.naimjeg.obeliskdepths.dungeon.portal.PortalSession;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.chunk.DungeonChunkLeaseState;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSessionLifecycle;
import io.github.naimjeg.obeliskdepths.dungeon.session.SessionAccessPolicy;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import io.github.naimjeg.obeliskdepths.dungeon.tribute.ResolvedTribute;
import io.github.naimjeg.obeliskdepths.dungeon.tribute.TributeResolver;
import io.github.naimjeg.obeliskdepths.entity.DungeonPortalEntity;
import io.github.naimjeg.obeliskdepths.menu.ObeliskPortalMenu;
import io.github.naimjeg.obeliskdepths.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class ServerDungeonActivationTransactionBackend
        implements DungeonActivationTransactionBackend {
    private static final String CANDIDATE_ACCEPTED = "candidate_accepted";
    private static final long PORTAL_SESSION_TTL_TICKS = 20L * 60L;

    private final ServerLevel dungeonLevel;
    private final DungeonPreparationJob job;
    private final DungeonPreparationExecutionContext context;
    private final DungeonPreparedEntryRegistry preparedEntryRegistry;
    private final DungeonSiteClaimManager claimManager;

    private DungeonInstance instance;
    private PortalSession portalSession;
    private ServerLevel sourceLevel;
    private ServerPlayer player;
    private ObeliskPortalMenu menu;
    private DungeonPortalEntity createdPortalEntity;
    private DungeonPreparationLeaseTransfer leaseTransfer;
    private DungeonPreparedPortalEntry preparedEntry;
    private DungeonSiteClaim releasedClaim;

    ServerDungeonActivationTransactionBackend(
            ServerLevel dungeonLevel,
            DungeonPreparationJob job,
            DungeonPreparationExecutionContext context,
            DungeonPreparedEntryRegistry preparedEntryRegistry,
            DungeonSiteClaimManager claimManager
    ) {
        this.dungeonLevel = dungeonLevel;
        this.job = job;
        this.context = context;
        this.preparedEntryRegistry = preparedEntryRegistry;
        this.claimManager = claimManager;
    }

    @Override
    public void assertOwnerThread() {
        if (!this.dungeonLevel.getServer().isSameThread()) {
            throw new IllegalStateException(
                    "Activation transaction must run on the server thread"
            );
        }
    }

    @Override
    public Optional<DungeonActivationTransactionFailure> revalidate(
            DungeonActivationCommitPlan plan
    ) {
        assertOwnerThread();
        if (!plan.matches(this.job, this.context)) {
            return failure(
                    DungeonActivationCommitFailureReason.INTERNAL_ERROR,
                    "stored commit plan no longer matches preparation context"
            );
        }
        if (plan.accessPolicy() == SessionAccessPolicy.ALLOWLIST) {
            return failure(
                    DungeonActivationCommitFailureReason.INTERNAL_ERROR,
                    "portal activation does not support access policy: "
                            + plan.accessPolicy().getSerializedName()
            );
        }
        this.player = this.dungeonLevel.getServer().getPlayerList()
                .getPlayer(plan.playerId());
        if (this.player == null || this.dungeonLevel.getServer().getPlayerList()
                .getPlayer(this.player.getUUID()) != this.player) {
            return failure(
                    DungeonActivationCommitFailureReason.PLAYER_OFFLINE,
                    "player is no longer online"
            );
        }
        this.sourceLevel = this.dungeonLevel.getServer()
                .getLevel(plan.sourceDimension());
        if (this.sourceLevel == null
                || !this.player.level().dimension().equals(plan.sourceDimension())
                || !this.sourceLevel.dimension().equals(plan.sourceDimension())) {
            return failure(
                    DungeonActivationCommitFailureReason.WRONG_SOURCE_DIMENSION,
                    "player or source level dimension changed"
            );
        }
        if (!(this.player.containerMenu instanceof ObeliskPortalMenu liveMenu)
                || liveMenu.containerId != plan.sourceContainerId()
                || !liveMenu.matchesActivePreparation(this.job.id(), this.job.request())) {
            return failure(
                    DungeonActivationCommitFailureReason.INVALID_OBELISK,
                    "active obelisk menu identity changed"
            );
        }
        this.menu = liveMenu;
        if (!this.menu.obeliskBottomPos().equals(plan.obeliskPos())
                || !isValidBottomObelisk(this.sourceLevel, plan.obeliskPos())
                || !withinMenuDistance(this.player, plan.obeliskPos())) {
            return failure(
                    DungeonActivationCommitFailureReason.INVALID_OBELISK,
                    "source obelisk is no longer valid"
            );
        }

        ItemStack tributeStack = this.menu.tributeStack();
        if (!plan.tributeFingerprint().matches(tributeStack)) {
            return failure(
                    DungeonActivationCommitFailureReason.INVALID_TRIBUTE,
                    "tribute fingerprint changed before transaction"
            );
        }
        ResolvedTribute liveTribute = TributeResolver.resolve(tributeStack);
        if (!plan.expectedTribute().equals(liveTribute)) {
            return failure(
                    DungeonActivationCommitFailureReason.INVALID_TRIBUTE,
                    "resolved tribute changed before transaction"
            );
        }

        DungeonSiteClaim claim = this.context.currentClaim();
        if (!plan.claimIdentity().matches(claim)
                || this.claimManager.find(plan.expectedSiteKey())
                        .filter(plan.claimIdentity()::matches)
                        .isEmpty()) {
            return failure(
                    DungeonActivationCommitFailureReason.SITE_CLAIM_LOST,
                    "site claim identity changed before transaction"
            );
        }
        DungeonManagerSavedData data = DungeonManagerSavedData.get(this.dungeonLevel);
        if (data.portalSessions().findValidBySourceObelisk(
                plan.sourceDimension(),
                plan.obeliskPos(),
                this.dungeonLevel.getGameTime()
        ).isPresent()) {
            return failure(
                    DungeonActivationCommitFailureReason.EXISTING_TARGET_UNAVAILABLE,
                    "source obelisk already has an active portal session"
            );
        }
        String reservationReason = data.sites().generatedReservationRejectionReason(
                        plan.expectedSiteKey()
                );
        if (!CANDIDATE_ACCEPTED.equals(reservationReason)) {
            return failure(
                    DungeonActivationCommitFailureReason.SITE_CONFLICT,
                    reservationReason
            );
        }

        List<DungeonPreparationStartChunkLease> leases =
                this.context.entryChunkLeases();
        if (leases.size() != plan.expectedEntryChunks().size()) {
            return failure(
                    DungeonActivationCommitFailureReason.INTERNAL_ERROR,
                    "entry lease count changed before transaction"
            );
        }
        for (int index = 0; index < leases.size(); index++) {
            DungeonPreparationStartChunkLease lease = leases.get(index);
            ChunkPos expected = plan.expectedEntryChunks().get(index);
            if (!lease.chunkPos().equals(expected)) {
                return failure(
                        DungeonActivationCommitFailureReason.INTERNAL_ERROR,
                        "entry lease order changed before transaction: " + expected
                );
            }
            if (lease.state() != DungeonChunkLeaseState.READY
                    || this.dungeonLevel.getChunkSource()
                    .getChunkNow(expected.x(), expected.z()) == null) {
                return failure(
                        DungeonActivationCommitFailureReason.EXISTING_TARGET_UNAVAILABLE,
                        "entry chunk is no longer prepared: " + expected
                );
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<DungeonInstanceId> reserveSite(
            DungeonActivationCommitPlan plan
    ) {
        Optional<DungeonInstance> reserved =
                DungeonInstanceService.reserveResolvedWorldgenSite(
                        this.dungeonLevel,
                        plan.resolvedSite(),
                        plan.expectedTribute().toDifficulty()
                );
        this.instance = reserved.orElse(null);
        return reserved.map(DungeonInstance::id);
    }

    @Override
    public void releaseReservedSite(DungeonInstanceId instanceId) {
        DungeonInstanceService.releaseFailedReservation(
                this.dungeonLevel, instanceId
        );
    }

    @Override
    public PortalSessionId createPortalSession(
            DungeonActivationCommitPlan plan,
            DungeonInstanceId instanceId
    ) {
        long gameTime = this.dungeonLevel.getGameTime();
        this.portalSession = DungeonManagerSavedData.get(this.dungeonLevel)
                .portalSessions().add(new PortalSession(
                        PortalSessionId.create(),
                        instanceId,
                        plan.playerId(),
                        plan.sourceDimension(),
                        plan.obeliskPos(),
                        plan.obeliskPos(),
                        plan.accessPolicy(),
                        gameTime + PORTAL_SESSION_TTL_TICKS
                ));
        return this.portalSession.id();
    }

    @Override
    public void removeCreatedPortalSession(PortalSessionId portalSessionId) {
        if (!DungeonPortalSessionLifecycle.removeCreatedSessionRecord(
                this.dungeonLevel, portalSessionId
        )) {
            throw new IllegalStateException(
                    "Created portal session was not available for compensation"
            );
        }
    }

    @Override
    public DungeonSessionResult acquireDungeonSession(
            DungeonActivationCommitPlan plan,
            DungeonInstanceId instanceId,
            PortalSessionId portalSessionId
    ) {
        DungeonSessionLifecycle.DungeonSessionAcquisition acquisition =
                DungeonSessionLifecycle.acquireForPortal(
                        this.dungeonLevel,
                        this.instance,
                        this.portalSession,
                        plan.expectedTribute().valid()
                );
        return new DungeonSessionResult(
                acquisition.session().id(), acquisition.created()
        );
    }

    @Override
    public void removeCreatedDungeonSession(UUID sessionId) {
        DungeonSessionLifecycle.removeSession(this.dungeonLevel, sessionId);
    }

    @Override
    public PortalEntityResult ensurePortalEntity(PortalSessionId portalSessionId) {
        Optional<DungeonPortalEntityService.PortalEnsureResult> ensured =
                DungeonPortalEntityService.ensurePortalWithResult(
                        this.sourceLevel, this.portalSession
                );
        if (ensured.isEmpty()) {
            return new PortalEntityResult(false, false);
        }
        if (ensured.get().created()) {
            this.createdPortalEntity = ensured.get().entity();
        }
        return new PortalEntityResult(true, ensured.get().created());
    }

    @Override
    public void removeCreatedPortalEntity() {
        if (this.createdPortalEntity != null) {
            DungeonPortalEntityService.removeExactCreatedPortal(
                    this.sourceLevel, this.createdPortalEntity
            );
            this.createdPortalEntity = null;
        }
    }

    @Override
    public void detachEntryLeases(DungeonActivationCommitPlan plan) {
        if (this.leaseTransfer != null) {
            throw new IllegalStateException(
                    "Entry leases have already been detached"
            );
        }
        List<DungeonPreparationStartChunkLease> leases =
                this.context.entryChunkLeases();
        DungeonPreparationLeaseTransfer localTransfer =
                new DungeonPreparationLeaseTransfer(
                        this.job.detachSelectedLeases(leases)
                );
        try {
            this.context.clearEntryChunkLeases();
            this.context.resetEntryChunkRequestIndex();
            this.leaseTransfer = localTransfer;
            localTransfer = null;
        } catch (RuntimeException failure) {
            try {
                localTransfer.close();
            } catch (RuntimeException cleanupFailure) {
                if (cleanupFailure != failure) {
                    failure.addSuppressed(cleanupFailure);
                }
            } catch (Error cleanupError) {
                cleanupError.addSuppressed(failure);
                throw cleanupError;
            }
            throw failure;
        } catch (Error error) {
            try {
                localTransfer.close();
            } catch (RuntimeException | Error cleanupFailure) {
                if (cleanupFailure != error) {
                    error.addSuppressed(cleanupFailure);
                }
            }
            throw error;
        }
    }

    @Override
    public void closeDetachedEntryLeases() {
        if (this.leaseTransfer != null) {
            this.leaseTransfer.close();
            this.leaseTransfer = null;
        }
    }

    @Override
    public void registerPreparedEntry(
            DungeonActivationCommitPlan plan,
            DungeonInstanceId instanceId,
            PortalSessionId portalSessionId
    ) {
        if (this.leaseTransfer == null) {
            throw new IllegalStateException("Entry leases were not detached");
        }
        DungeonPreparationLeaseBundle bundle = this.leaseTransfer.takeBundle();
        this.leaseTransfer = null;
        DungeonPreparedPortalEntry entry = null;
        try {
            entry = new DungeonPreparedPortalEntry(
                    portalSessionId,
                    instanceId,
                    plan.expectedSiteKey(),
                    plan.preparedDestination(),
                    plan.expectedEntryChunks(),
                    bundle,
                    this.dungeonLevel.getGameTime()
            );
            this.preparedEntryRegistry.register(entry);
            this.preparedEntry = entry;
            entry = null;
            bundle = null;
        } finally {
            if (entry != null) {
                entry.close();
            } else if (bundle != null) {
                bundle.close();
            }
        }
    }

    @Override
    public void removeRegisteredPreparedEntry(PortalSessionId portalSessionId) {
        if (this.preparedEntry != null) {
            if (!this.preparedEntryRegistry.removeAndCloseExact(
                    portalSessionId, this.preparedEntry
            )) {
                throw new IllegalStateException(
                        "Registered prepared entry changed before compensation"
                );
            }
            this.preparedEntry = null;
        }
    }

    @Override
    public void releaseStartLeaseAfterPreparedEntry() {
        DungeonPreparationStartChunkLease startLease =
                this.context.currentStartLease().orElse(null);
        if (startLease != null) {
            this.job.closeAndRemoveLease(startLease);
            this.context.clearCurrentStartLease();
        }
    }

    @Override
    public boolean releaseSiteClaim(DungeonActivationCommitPlan plan) {
        DungeonSiteClaim claim = this.context.currentClaim();
        if (!plan.claimIdentity().matches(claim)
                || !this.claimManager.release(claim)) {
            return false;
        }
        this.releasedClaim = claim;
        this.context.currentClaim(null);
        return true;
    }

    @Override
    public void restoreSiteClaim(DungeonActivationCommitPlan plan) {
        if (this.releasedClaim != null) {
            if (!this.claimManager.restore(this.releasedClaim)) {
                throw new IllegalStateException("Could not restore released site claim");
            }
            this.context.currentClaim(this.releasedClaim);
            this.releasedClaim = null;
        }
    }

    @Override
    public void consumeTribute(DungeonActivationCommitPlan plan) {
        ItemStack liveStack = this.menu.tributeStack();
        if (!plan.tributeFingerprint().matches(liveStack)
                || !plan.expectedTribute().equals(TributeResolver.resolve(liveStack))
                || liveStack.getCount() < plan.tributeFingerprint().requiredCount()) {
            throw new DungeonActivationTransactionFailure(
                    DungeonActivationCommitFailureReason.INVALID_TRIBUTE,
                    "tribute changed immediately before consumption"
            );
        }
        if (!this.player.getAbilities().instabuild) {
            liveStack.shrink(plan.tributeFingerprint().requiredCount());
        }
    }

    private Optional<DungeonActivationTransactionFailure> failure(
            DungeonActivationCommitFailureReason reason,
            String detail
    ) {
        return Optional.of(new DungeonActivationTransactionFailure(reason, detail));
    }

    private static boolean isValidBottomObelisk(
            ServerLevel level,
            BlockPos pos
    ) {
        var state = level.getBlockState(pos);
        return state.is(ModBlocks.OBELISK.get())
                && state.hasProperty(ObeliskBlock.PART)
                && state.getValue(ObeliskBlock.PART) == ObeliskPart.BOTTOM;
    }

    private static boolean withinMenuDistance(
            ServerPlayer player,
            BlockPos pos
    ) {
        return player.distanceToSqr(
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D
        ) <= 64.0D;
    }
}
