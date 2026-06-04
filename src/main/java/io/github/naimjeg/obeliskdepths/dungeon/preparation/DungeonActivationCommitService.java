package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.block.ObeliskBlock;
import io.github.naimjeg.obeliskdepths.block.ObeliskPart;
import io.github.naimjeg.obeliskdepths.dungeon.id.PortalSessionId;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstanceService;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonStatus;
import io.github.naimjeg.obeliskdepths.dungeon.portal.DungeonPortalEntityService;
import io.github.naimjeg.obeliskdepths.dungeon.portal.PortalAdmissionMode;
import io.github.naimjeg.obeliskdepths.dungeon.portal.PortalSession;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSession;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSessionLifecycle.DungeonSessionAcquisition;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSessionLifecycle;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import io.github.naimjeg.obeliskdepths.dungeon.tribute.ResolvedTribute;
import io.github.naimjeg.obeliskdepths.dungeon.tribute.TributeResolver;
import io.github.naimjeg.obeliskdepths.registry.ModBlocks;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class DungeonActivationCommitService {
    private DungeonActivationCommitService() {
    }

    public static DungeonActivationCommitResult commit(
            ServerPlayer player,
            ServerLevel sourceLevel,
            ServerLevel dungeonLevel,
            DungeonPreparedTarget target,
            ItemStack currentTributeStack
    ) {
        if (target instanceof ExistingOpenJoinTarget existingTarget) {
            return commitExistingOpenJoin(
                    player,
                    sourceLevel,
                    dungeonLevel,
                    existingTarget
            );
        }
        if (target instanceof NewAuthoritativeSiteTarget newTarget) {
            return commitNewTarget(
                    player,
                    sourceLevel,
                    dungeonLevel,
                    newTarget,
                    currentTributeStack
            );
        }

        return DungeonActivationCommitResult.failure(
                DungeonActivationCommitFailureReason.INTERNAL_ERROR,
                "unknown prepared target"
        );
    }

    private static DungeonActivationCommitResult commitExistingOpenJoin(
            ServerPlayer player,
            ServerLevel sourceLevel,
            ServerLevel dungeonLevel,
            ExistingOpenJoinTarget target
    ) {
        Optional<DungeonActivationCommitFailureReason> commonFailure =
                validateCommon(player, sourceLevel, target.request());
        if (commonFailure.isPresent()) {
            return DungeonActivationCommitResult.failure(
                    commonFailure.get(),
                    target.request().toString()
            );
        }

        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        Optional<PortalSession> session =
                data.portalSessions().get(target.portalSessionId());
        if (session.isEmpty()
                || session.get().isExpired(dungeonLevel.getGameTime())
                || session.get().admissionMode() != PortalAdmissionMode.OPEN_JOIN
                || !session.get().sourceDimension().equals(target.request().sourceDimension())
                || !session.get().obeliskPos().equals(target.request().obeliskPos())
                || !session.get().instanceId().equals(target.instanceId())) {
            return DungeonActivationCommitResult.failure(
                    DungeonActivationCommitFailureReason.EXISTING_TARGET_UNAVAILABLE,
                    "portal session unavailable"
            );
        }

        Optional<DungeonInstance> instance = data.instances().get(target.instanceId());
        if (instance.isEmpty() || instance.get().status() != DungeonStatus.ACTIVE) {
            return DungeonActivationCommitResult.failure(
                    DungeonActivationCommitFailureReason.EXISTING_TARGET_UNAVAILABLE,
                    "instance unavailable"
            );
        }

        DungeonSession dungeonSession = null;
        boolean createdDungeonSession = false;

        try {
            DungeonSessionAcquisition acquisition = DungeonSessionLifecycle.acquireForPortal(
                    dungeonLevel,
                    instance.get(),
                    session.get(),
                    false
            );
            dungeonSession = acquisition.session();
            createdDungeonSession = acquisition.created();

            if (DungeonPortalEntityService.ensurePortal(sourceLevel, session.get()).isEmpty()) {
                RuntimeException rollbackFailure = rollbackExistingOpenJoinSessionSafely(
                        dungeonLevel,
                        dungeonSession,
                        createdDungeonSession
                );
                if (rollbackFailure != null) {
                    ObeliskDepths.LOGGER.error(
                            "Failed to rollback recreated existing-open-join dungeon session after portal spawn failure: session={}, instance={}, portalSession={}",
                            dungeonSession.id(),
                            instance.get().id(),
                            session.get().id(),
                            rollbackFailure
                    );
                    return DungeonActivationCommitResult.failure(
                            DungeonActivationCommitFailureReason.INTERNAL_ERROR,
                            "portal spawn failed and recreated dungeon session rollback failed"
                    );
                }
                return DungeonActivationCommitResult.failure(
                        DungeonActivationCommitFailureReason.PORTAL_SPAWN_FAILED,
                        "portal spawn failed"
                );
            }

            return DungeonActivationCommitResult.success(
                    instance.get().id(),
                    session.get().id()
            );
        } catch (RuntimeException originalException) {
            RuntimeException rollbackFailure = rollbackExistingOpenJoinSessionSafely(
                    dungeonLevel,
                    dungeonSession,
                    createdDungeonSession
            );
            if (rollbackFailure != null) {
                originalException.addSuppressed(rollbackFailure);
            }
            ObeliskDepths.LOGGER.error(
                    "Failed to commit existing open-join dungeon activation at {} for player {}",
                    target.request().obeliskPos(),
                    player.getGameProfile().name(),
                    originalException
            );
            return DungeonActivationCommitResult.failure(
                    DungeonActivationCommitFailureReason.INTERNAL_ERROR,
                    safeFailureDetail(originalException)
            );
        }
    }

    private static DungeonActivationCommitResult commitNewTarget(
            ServerPlayer player,
            ServerLevel sourceLevel,
            ServerLevel dungeonLevel,
            NewAuthoritativeSiteTarget target,
            ItemStack currentTributeStack
    ) {
        Optional<DungeonActivationCommitFailureReason> commonFailure =
                validateCommon(player, sourceLevel, target.request());
        if (commonFailure.isPresent()) {
            return DungeonActivationCommitResult.failure(
                    commonFailure.get(),
                    target.request().toString()
            );
        }

        ResolvedTribute currentTribute = TributeResolver.resolve(currentTributeStack);
        if (!currentTribute.equals(target.tribute())) {
            return DungeonActivationCommitResult.failure(
                    DungeonActivationCommitFailureReason.INVALID_TRIBUTE,
                    "tribute changed before commit"
            );
        }

        if (!target.resolvedSite().authoritative()) {
            return DungeonActivationCommitResult.failure(
                    DungeonActivationCommitFailureReason.NON_AUTHORITATIVE_SITE,
                    target.resolvedSite().source().name()
            );
        }

        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        if (!"candidate_accepted".equals(data.sites()
                .generatedReservationRejectionReason(target.resolvedSite().site().key()))) {
            return DungeonActivationCommitResult.failure(
                    DungeonActivationCommitFailureReason.SITE_CONFLICT,
                    data.sites().generatedReservationRejectionReason(
                            target.resolvedSite().site().key()
                    )
            );
        }

        DungeonInstance instance = null;
        PortalSession portalSession = null;
        CreatedDungeonSession createdDungeonSession = null;

        try {
            Optional<DungeonInstance> reserved =
                    DungeonInstanceService.reserveResolvedWorldgenSite(
                            dungeonLevel,
                            target.resolvedSite(),
                            target.tribute().toDifficulty()
                    );
            if (reserved.isEmpty()) {
                return DungeonActivationCommitResult.failure(
                        DungeonActivationCommitFailureReason.SITE_CONFLICT,
                        "site no longer reservable"
                );
            }

            instance = reserved.get();
            long gameTime = dungeonLevel.getGameTime();
            data.portalSessions().purgeExpired(gameTime);
            portalSession = data.portalSessions().add(new PortalSession(
                    PortalSessionId.create(),
                    instance.id(),
                    player.getUUID(),
                    sourceLevel.dimension(),
                    target.request().obeliskPos(),
                    target.request().obeliskPos(),
                    target.request().requestedMode(),
                    gameTime + 20L * 60L
            ));

            DungeonSessionAcquisition acquisition =
                    DungeonSessionLifecycle.acquireForPortal(
                            dungeonLevel,
                            instance,
                            portalSession,
                            target.tribute().valid()
                    );
            DungeonSession dungeonSession = acquisition.session();

            if (acquisition.created()) {
                createdDungeonSession = new CreatedDungeonSession(dungeonSession.id());
            }

            if (DungeonPortalEntityService.ensurePortal(sourceLevel, portalSession).isEmpty()) {
                RuntimeException rollbackFailure = rollbackCreatedTargetSafely(
                        sourceLevel,
                        dungeonLevel,
                        instance,
                        portalSession,
                        createdDungeonSession
                );
                if (rollbackFailure != null) {
                    ObeliskDepths.LOGGER.error(
                            "Failed to rollback new dungeon activation after portal spawn failure: instance={}, portalSession={}",
                            instance.id(),
                            portalSession.id(),
                            rollbackFailure
                    );
                    return DungeonActivationCommitResult.failure(
                            DungeonActivationCommitFailureReason.INTERNAL_ERROR,
                            "portal spawn failed and created target rollback failed"
                    );
                }
                return DungeonActivationCommitResult.failure(
                        DungeonActivationCommitFailureReason.PORTAL_SPAWN_FAILED,
                        "portal spawn failed"
                );
            }

            consumeTributeIfNeeded(player, currentTributeStack, target.tribute());
            return DungeonActivationCommitResult.success(
                    instance.id(),
                    portalSession.id()
            );
        } catch (RuntimeException originalException) {
            RuntimeException rollbackFailure = rollbackCreatedTargetSafely(
                    sourceLevel,
                    dungeonLevel,
                    instance,
                    portalSession,
                    createdDungeonSession
            );
            if (rollbackFailure != null) {
                originalException.addSuppressed(rollbackFailure);
            }
            ObeliskDepths.LOGGER.error(
                    "Failed to commit obelisk dungeon activation at {} for player {}",
                    target.request().obeliskPos(),
                    player.getGameProfile().name(),
                    originalException
            );
            return DungeonActivationCommitResult.failure(
                    DungeonActivationCommitFailureReason.INTERNAL_ERROR,
                    safeFailureDetail(originalException)
            );
        }
    }

    private static Optional<DungeonActivationCommitFailureReason> validateCommon(
            ServerPlayer player,
            ServerLevel sourceLevel,
            DungeonPreparationRequest request
    ) {
        if (sourceLevel.getServer().getPlayerList().getPlayer(player.getUUID()) != player) {
            return Optional.of(DungeonActivationCommitFailureReason.PLAYER_OFFLINE);
        }
        if (!player.level().dimension().equals(request.sourceDimension())
                || !sourceLevel.dimension().equals(request.sourceDimension())) {
            return Optional.of(DungeonActivationCommitFailureReason.WRONG_SOURCE_DIMENSION);
        }
        if (request.requestedMode() == null || !isValidBottomObelisk(
                sourceLevel,
                request.obeliskPos()
        ) || !withinMenuDistance(player, request.obeliskPos())) {
            return Optional.of(DungeonActivationCommitFailureReason.INVALID_OBELISK);
        }
        return Optional.empty();
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

    private static RuntimeException rollbackCreatedTargetSafely(
            ServerLevel sourceLevel,
            ServerLevel dungeonLevel,
            DungeonInstance instance,
            PortalSession session,
            CreatedDungeonSession createdDungeonSession
    ) {
        RuntimeException aggregate = null;
        if (session != null) {
            try {
                DungeonPortalEntityService.removePortalsForSession(
                        sourceLevel,
                        session.id(),
                        session.portalAnchorPos()
                );
            } catch (RuntimeException exception) {
                aggregate = appendCleanupFailure(
                        aggregate,
                        "Failed to rollback created dungeon activation target",
                        exception
                );
            }
            try {
                DungeonManagerSavedData.get(dungeonLevel).portalSessions().remove(session.id());
            } catch (RuntimeException exception) {
                aggregate = appendCleanupFailure(
                        aggregate,
                        "Failed to rollback created dungeon activation target",
                        exception
                );
            }
        }

        if (createdDungeonSession != null) {
            try {
                DungeonSessionLifecycle.removeSession(
                        dungeonLevel,
                        createdDungeonSession.id()
                );
            } catch (RuntimeException exception) {
                aggregate = appendCleanupFailure(
                        aggregate,
                        "Failed to rollback created dungeon activation target",
                        exception
                );
            }
        }

        if (instance != null) {
            try {
                DungeonInstanceService.releaseFailedReservation(dungeonLevel, instance.id());
            } catch (RuntimeException exception) {
                aggregate = appendCleanupFailure(
                        aggregate,
                        "Failed to rollback created dungeon activation target",
                        exception
                );
            }
        }

        return aggregate;
    }

    private static RuntimeException rollbackExistingOpenJoinSessionSafely(
            ServerLevel dungeonLevel,
            DungeonSession dungeonSession,
            boolean createdByCommit
    ) {
        if (!createdByCommit || dungeonSession == null) {
            return null;
        }

        try {
            DungeonSessionLifecycle.removeSession(
                    dungeonLevel,
                    dungeonSession.id()
            );
            return null;
        } catch (RuntimeException exception) {
            return exception;
        }
    }

    private static RuntimeException appendCleanupFailure(
            RuntimeException aggregate,
            String message,
            RuntimeException exception
    ) {
        RuntimeException result = aggregate;
        if (result == null) {
            result = new IllegalStateException(message);
        }
        result.addSuppressed(exception);
        return result;
    }

    static RuntimeException appendCleanupFailureForTests(
            RuntimeException aggregate,
            String message,
            RuntimeException exception
    ) {
        return appendCleanupFailure(aggregate, message, exception);
    }

    static String safeFailureDetail(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }

    private static void consumeTributeIfNeeded(
            ServerPlayer player,
            ItemStack tributeStack,
            ResolvedTribute tribute
    ) {
        if (player.getAbilities().instabuild) {
            return;
        }

        tributeStack.shrink(tribute.amount());
    }

    private record CreatedDungeonSession(UUID id) {
    }
}
