package io.github.naimjeg.obeliskdepths.dungeon.interaction;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.block.ObeliskBlock;
import io.github.naimjeg.obeliskdepths.block.ObeliskPart;
import io.github.naimjeg.obeliskdepths.dungeon.access.DungeonAccessController;
import io.github.naimjeg.obeliskdepths.dungeon.access.DungeonAccessResult;
import io.github.naimjeg.obeliskdepths.dungeon.id.PortalSessionId;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonStatus;
import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonData;
import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonTracker;
import io.github.naimjeg.obeliskdepths.dungeon.portal.PortalSession;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.*;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import io.github.naimjeg.obeliskdepths.entity.DungeonPortalEntity;
import io.github.naimjeg.obeliskdepths.network.ClientboundDungeonLoadingFinishedPayload;
import io.github.naimjeg.obeliskdepths.network.ClientboundDungeonLoadingStatePayload;
import io.github.naimjeg.obeliskdepths.registry.ModBlocks;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/**
 * Owner-thread coordinator for portal-entry UI handshakes and execution.
 *
 * <p>The screen never drives work. It acknowledges that it is visible, after
 * which this runtime delegates destination recovery to
 * {@link DungeonPreparationRuntime} and teleport/finalization to
 * {@link DungeonPortalEntryService}.</p>
 */
@EventBusSubscriber(modid = ObeliskDepths.MOD_ID)
public final class DungeonPortalEntryOperationRuntime {
    private static final long CLIENT_READY_TIMEOUT_TICKS = 20L * 10L;
    private static final long OPERATION_TIMEOUT_TICKS = 20L * 45L;
    private static final Map<ServerLevel, DungeonPortalEntryOperationRuntime>
            LEVEL_RUNTIMES = new java.util.WeakHashMap<>();

    private final ServerLevel dungeonLevel;
    private final Map<DungeonPortalEntryOperationId, DungeonPortalEntryOperation>
            operationsById = new LinkedHashMap<>();
    private final Map<UUID, DungeonPortalEntryOperation> operationsByPlayer =
            new HashMap<>();
    private boolean cleared;

    private DungeonPortalEntryOperationRuntime(ServerLevel dungeonLevel) {
        this.dungeonLevel = Objects.requireNonNull(dungeonLevel, "dungeonLevel");
    }

    static DungeonPortalEntryOperationRuntime getOrCreate(ServerLevel level) {
        assertOwnerThread(level);
        DungeonPortalEntryOperationRuntime runtime = LEVEL_RUNTIMES.computeIfAbsent(
                level,
                DungeonPortalEntryOperationRuntime::new
        );
        if (runtime.cleared) {
            throw new IllegalStateException("Portal entry operation runtime is cleared");
        }
        return runtime;
    }

    static DungeonPortalEntryOperationRuntime get(ServerLevel level) {
        assertOwnerThread(level);
        DungeonPortalEntryOperationRuntime runtime = LEVEL_RUNTIMES.get(level);
        return runtime == null || runtime.cleared ? null : runtime;
    }

    Optional<DungeonPortalEntryOperation> activeForPlayer(UUID playerId) {
        assertOwnerThread();
        return Optional.ofNullable(this.operationsByPlayer.get(playerId));
    }

    DungeonPortalEntryOperation register(
            ServerPlayer player,
            PortalSessionId portalSessionId,
            UUID sourcePortalEntityId
    ) {
        assertOwnerThread();
        if (this.cleared) {
            throw new IllegalStateException("Portal entry operation runtime is cleared");
        }
        if (this.operationsByPlayer.containsKey(player.getUUID())) {
            throw new IllegalStateException(
                    "Player already has an active portal entry operation"
            );
        }
        DungeonPortalEntryOperation operation = new DungeonPortalEntryOperation(
                DungeonPortalEntryOperationId.create(),
                player.getUUID(),
                portalSessionId,
                sourcePortalEntityId,
                this.dungeonLevel.getGameTime()
        );
        this.operationsById.put(operation.id(), operation);
        this.operationsByPlayer.put(operation.playerId(), operation);
        return operation;
    }

    void clientReady(
            ServerPlayer player,
            DungeonPortalEntryOperationId operationId
    ) {
        assertOwnerThread();
        DungeonPortalEntryOperation operation = this.operationsById.get(operationId);
        if (operation == null
                || !operation.playerId().equals(player.getUUID())
                || operation.state()
                != DungeonPortalEntryOperationState.AWAITING_CLIENT_READY) {
            return;
        }

        DungeonPortalEntryResult validation = validatePreTeleport(
                operation,
                player
        );
        if (validation != DungeonPortalEntryResult.SUCCESS) {
            terminateForValidation(operation, validation);
            return;
        }

        transitionAndSend(operation, DungeonPortalEntryOperationState.PREPARING);
        beginOrReusePreparation(operation, player);
    }

    void tick() {
        assertOwnerThread();
        if (this.cleared || this.operationsById.isEmpty()) {
            return;
        }
        long gameTime = this.dungeonLevel.getGameTime();
        List<DungeonPortalEntryOperation> snapshot =
                new ArrayList<>(this.operationsById.values());
        for (DungeonPortalEntryOperation operation : snapshot) {
            if (this.operationsById.get(operation.id()) != operation) {
                continue;
            }
            tickOperation(operation, gameTime);
        }
    }

    private void tickOperation(
            DungeonPortalEntryOperation operation,
            long gameTime
    ) {
        switch (operation.state()) {
            case AWAITING_CLIENT_READY -> tickAwaitingClient(operation, gameTime);
            case PREPARING -> tickPreparing(operation, gameTime);
            case READY_TO_TELEPORT -> teleport(operation);
            case FINALIZING -> tickFinalizing(operation);
            case TELEPORTING -> {
                // TELEPORTING is entered and left in one owner-thread call.
            }
            case COMPLETED, FAILED, CANCELLED -> remove(operation);
        }
    }

    private void tickAwaitingClient(
            DungeonPortalEntryOperation operation,
            long gameTime
    ) {
        ServerPlayer player = onlinePlayer(operation.playerId());
        if (player == null) {
            cancel(operation, DungeonPortalEntryResult.PLAYER_UNAVAILABLE, false);
            return;
        }
        DungeonPortalEntryResult validation = validatePreTeleport(operation, player);
        if (validation != DungeonPortalEntryResult.SUCCESS) {
            terminateForValidation(operation, validation);
            return;
        }
        if (gameTime - operation.createdAtGameTime()
                >= CLIENT_READY_TIMEOUT_TICKS) {
            fail(operation, DungeonPortalEntryResult.CLIENT_NOT_READY);
        }
    }

    private void tickPreparing(
            DungeonPortalEntryOperation operation,
            long gameTime
    ) {
        ServerPlayer player = onlinePlayer(operation.playerId());
        if (player == null) {
            cancel(operation, DungeonPortalEntryResult.PLAYER_UNAVAILABLE, false);
            return;
        }
        DungeonPortalEntryResult validation = validatePreTeleport(operation, player);
        if (validation != DungeonPortalEntryResult.SUCCESS) {
            terminateForValidation(operation, validation);
            return;
        }
        if (gameTime - operation.createdAtGameTime() >= OPERATION_TIMEOUT_TICKS) {
            fail(operation, DungeonPortalEntryResult.PREPARATION_FAILED);
            return;
        }

        DungeonPreparationRuntime preparationRuntime =
                DungeonPreparationRuntime.get(this.dungeonLevel);
        if (preparationRuntime == null) {
            fail(operation, DungeonPortalEntryResult.PREPARATION_FAILED);
            return;
        }
        Optional<DungeonPreparedPortalEntry> prepared =
                preparationRuntime.preparedPortalEntry(operation.portalSessionId());
        if (prepared.isPresent()
                && validatePrepared(operation, prepared.get())
                == DungeonPreparedEntryValidation.VALID) {
            transitionAndSend(
                    operation,
                    DungeonPortalEntryOperationState.READY_TO_TELEPORT
            );
            return;
        }
        if (!preparationRuntime.isPreparedEntryRecoveryActive(
                operation.portalSessionId()
        )) {
            fail(operation, DungeonPortalEntryResult.PREPARATION_FAILED);
        }
    }

    private void beginOrReusePreparation(
            DungeonPortalEntryOperation operation,
            ServerPlayer player
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(this.dungeonLevel);
        PortalSession session = data.portalSessions()
                .get(operation.portalSessionId())
                .orElse(null);
        DungeonInstance instance = session == null
                ? null
                : data.instances().get(session.instanceId()).orElse(null);
        if (session == null || instance == null) {
            fail(
                    operation,
                    session == null
                            ? DungeonPortalEntryResult.SESSION_MISSING
                            : DungeonPortalEntryResult.INSTANCE_MISSING
            );
            return;
        }

        DungeonPreparationRuntime preparationRuntime =
                DungeonPreparationRuntime.getOrCreate(this.dungeonLevel);
        Optional<DungeonPreparedPortalEntry> existing =
                preparationRuntime.preparedPortalEntry(session.id());
        if (existing.isPresent()) {
            DungeonPreparedEntryValidation validation = validatePrepared(
                    operation,
                    existing.get()
            );
            if (validation == DungeonPreparedEntryValidation.VALID) {
                transitionAndSend(
                        operation,
                        DungeonPortalEntryOperationState.READY_TO_TELEPORT
                );
                return;
            }
            try {
                preparationRuntime.removeAndClosePreparedEntry(session.id());
            } catch (RuntimeException cleanupFailure) {
                ObeliskDepths.LOGGER.error(
                        "Failed to discard invalid prepared entry before portal operation recovery: operation={}, session={}",
                        operation.id(),
                        session.id(),
                        cleanupFailure
                );
                fail(operation, DungeonPortalEntryResult.PREPARATION_FAILED);
                return;
            }
        }

        Optional<DungeonSite> site = data.requireReservedDungeon(
                instance.id(),
                instance.siteKey()
        ).map(io.github.naimjeg.obeliskdepths.dungeon.state.ReservedDungeonAggregate::site);
        if (site.isEmpty()) {
            fail(operation, DungeonPortalEntryResult.DESTINATION_NOT_PREPARED);
            return;
        }

        DungeonPreparedEntryRecoveryStatus status =
                preparationRuntime.submitOrReusePreparedEntryRecovery(
                        session,
                        instance,
                        site.get()
                );
        if (status == DungeonPreparedEntryRecoveryStatus.STARTED) {
            operation.markRecoveryOwned();
        }
        if (status == DungeonPreparedEntryRecoveryStatus.REJECTED) {
            fail(operation, DungeonPortalEntryResult.DESTINATION_NOT_PREPARED);
            return;
        }
        if (status == DungeonPreparedEntryRecoveryStatus.ALREADY_PREPARED) {
            Optional<DungeonPreparedPortalEntry> prepared =
                    preparationRuntime.preparedPortalEntry(session.id());
            if (prepared.isPresent()
                    && validatePrepared(operation, prepared.get())
                    == DungeonPreparedEntryValidation.VALID) {
                transitionAndSend(
                        operation,
                        DungeonPortalEntryOperationState.READY_TO_TELEPORT
                );
                return;
            }
        }

        ObeliskDepths.LOGGER.debug(
                "Portal entry preparation {} after client-ready: operation={}, player={}, session={}",
                status,
                operation.id(),
                player.getUUID(),
                session.id()
        );
    }

    private void teleport(DungeonPortalEntryOperation operation) {
        ServerPlayer player = onlinePlayer(operation.playerId());
        if (player == null) {
            cancel(operation, DungeonPortalEntryResult.PLAYER_UNAVAILABLE, false);
            return;
        }
        DungeonPortalEntryResult validation = validatePreTeleport(operation, player);
        if (validation != DungeonPortalEntryResult.SUCCESS) {
            terminateForValidation(operation, validation);
            return;
        }

        DungeonPreparationRuntime preparationRuntime =
                DungeonPreparationRuntime.get(this.dungeonLevel);
        Optional<DungeonPreparedPortalEntry> prepared = preparationRuntime == null
                ? Optional.empty()
                : preparationRuntime.preparedPortalEntry(operation.portalSessionId());
        if (prepared.isEmpty()
                || validatePrepared(operation, prepared.get())
                != DungeonPreparedEntryValidation.VALID) {
            fail(operation, DungeonPortalEntryResult.DESTINATION_UNAVAILABLE);
            return;
        }

        transitionAndSend(operation, DungeonPortalEntryOperationState.TELEPORTING);
        DungeonPortalEntryResult result =
                DungeonPortalEntryService.teleportPreparedEntry(
                        player,
                        this.dungeonLevel,
                        operation.portalSessionId(),
                        prepared.get()
                );
        if (result != DungeonPortalEntryResult.SUCCESS) {
            fail(operation, result);
            return;
        }
        transitionAndSend(operation, DungeonPortalEntryOperationState.FINALIZING);
    }

    private void tickFinalizing(DungeonPortalEntryOperation operation) {
        DungeonPreparationRuntime preparationRuntime =
                DungeonPreparationRuntime.get(this.dungeonLevel);
        if (preparationRuntime != null
                && preparationRuntime.isPostTeleportHandoffActive(
                operation.portalSessionId()
        )) {
            return;
        }
        operation.transitionTo(
                DungeonPortalEntryOperationState.COMPLETED,
                this.dungeonLevel.getGameTime()
        );
        sendFinished(operation, DungeonPortalEntryResult.SUCCESS, true);
        remove(operation);
    }

    private DungeonPreparedEntryValidation validatePrepared(
            DungeonPortalEntryOperation operation,
            DungeonPreparedPortalEntry prepared
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(this.dungeonLevel);
        Optional<PortalSession> session =
                data.portalSessions().get(operation.portalSessionId());
        Optional<DungeonInstance> instance = session.flatMap(value ->
                data.instances().get(value.instanceId())
        );
        if (session.isEmpty() || instance.isEmpty()) {
            return DungeonPreparedEntryValidation.IDENTITY_MISMATCH;
        }
        return DungeonPreparedEntryValidator.validate(
                session.get().id(),
                session.get().instanceId(),
                instance.get().siteKey(),
                prepared,
                this.dungeonLevel.getGameTime(),
                chunkPos -> this.dungeonLevel.getChunkSource().getChunkNow(
                        chunkPos.x(), chunkPos.z()
                ) != null
        );
    }

    DungeonPortalEntryResult validatePreTeleport(
            DungeonPortalEntryOperation operation,
            ServerPlayer player
    ) {
        return validatePreTeleport(
                this.dungeonLevel,
                operation.portalSessionId(),
                operation.sourcePortalEntityId(),
                player
        );
    }

    static DungeonPortalEntryResult validatePreTeleport(
            ServerLevel dungeonLevel,
            PortalSessionId portalSessionId,
            UUID sourcePortalEntityId,
            ServerPlayer player
    ) {
        assertOwnerThread(dungeonLevel);
        if (player.isRemoved() || !player.isAlive() || player.isSpectator()) {
            return DungeonPortalEntryResult.PLAYER_UNAVAILABLE;
        }
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        Optional<PortalSession> session =
                data.portalSessions().get(portalSessionId);
        if (session.isEmpty()) {
            return DungeonPortalEntryResult.SESSION_MISSING;
        }
        long gameTime = dungeonLevel.getGameTime();
        if (session.get().isExpired(gameTime)) {
            return DungeonPortalEntryResult.SESSION_EXPIRED;
        }
        Optional<DungeonInstance> instance =
                data.instances().get(session.get().instanceId());
        if (instance.isEmpty()) {
            return DungeonPortalEntryResult.INSTANCE_MISSING;
        }
        if (instance.get().status() != DungeonStatus.ACTIVE) {
            return DungeonPortalEntryResult.ACCESS_DENIED;
        }
        if (!player.level().dimension().equals(session.get().sourceDimension())) {
            return DungeonPortalEntryResult.WRONG_SOURCE_DIMENSION;
        }
        Optional<PlayerDungeonData> playerData = PlayerDungeonTracker.get(player);
        if (playerData.flatMap(PlayerDungeonData::currentInstanceId)
                .filter(id -> !id.equals(instance.get().id()))
                .isPresent()) {
            return DungeonPortalEntryResult.PLAYER_ALREADY_BOUND_ELSEWHERE;
        }
        if (DungeonAccessController.canEnter(
                player.getUUID(), session.get(), instance.get(), gameTime
        ) != DungeonAccessResult.ALLOW) {
            return DungeonPortalEntryResult.ACCESS_DENIED;
        }

        ServerLevel sourceLevel = dungeonLevel.getServer()
                .getLevel(session.get().sourceDimension());
        if (sourceLevel == null
                || !sourceLevel.hasChunk(
                        SectionPos.blockToSectionCoord(session.get().obeliskPos().getX()),
                        SectionPos.blockToSectionCoord(session.get().obeliskPos().getZ())
                )
                || !(sourceLevel.getEntity(sourcePortalEntityId)
                instanceof DungeonPortalEntity portal)
                || !portal.isAlive()
                || portal.portalSessionId()
                .filter(session.get().id()::equals)
                .isEmpty()
                || !portal.isCloseToAnchor(session.get().portalAnchorPos())) {
            return DungeonPortalEntryResult.PORTAL_INVALID;
        }
        var state = sourceLevel.getBlockState(session.get().obeliskPos());
        if (!state.is(ModBlocks.OBELISK.get())
                || !state.hasProperty(ObeliskBlock.PART)
                || state.getValue(ObeliskBlock.PART) != ObeliskPart.BOTTOM) {
            return DungeonPortalEntryResult.PORTAL_INVALID;
        }
        return DungeonPortalEntryResult.SUCCESS;
    }

    void cancelForPlayer(
            UUID playerId,
            DungeonPortalEntryResult result,
            boolean notifyClient
    ) {
        assertOwnerThread();
        DungeonPortalEntryOperation operation = this.operationsByPlayer.get(playerId);
        if (operation == null || operation.state().terminal()) {
            return;
        }
        if (operation.state() == DungeonPortalEntryOperationState.TELEPORTING) {
            return;
        }
        cancel(operation, result, notifyClient);
    }

    void onPlayerDimensionChanged(ServerPlayer player) {
        assertOwnerThread();
        DungeonPortalEntryOperation operation =
                this.operationsByPlayer.get(player.getUUID());
        if (operation == null) {
            return;
        }
        if ((operation.state() == DungeonPortalEntryOperationState.TELEPORTING
                || operation.state()
                == DungeonPortalEntryOperationState.FINALIZING)
                && player.level() == this.dungeonLevel) {
            return;
        }
        if (operation.state() != DungeonPortalEntryOperationState.FINALIZING) {
            cancel(
                    operation,
                    DungeonPortalEntryResult.WRONG_SOURCE_DIMENSION,
                    true
            );
        }
    }

    private void terminateForValidation(
            DungeonPortalEntryOperation operation,
            DungeonPortalEntryResult result
    ) {
        if (result == DungeonPortalEntryResult.PLAYER_UNAVAILABLE
                || result == DungeonPortalEntryResult.WRONG_SOURCE_DIMENSION
                || result == DungeonPortalEntryResult.PORTAL_INVALID) {
            cancel(operation, result, true);
        } else {
            fail(operation, result);
        }
    }

    private void fail(
            DungeonPortalEntryOperation operation,
            DungeonPortalEntryResult result
    ) {
        if (operation.state().terminal()) {
            return;
        }
        operation.transitionTo(
                DungeonPortalEntryOperationState.FAILED,
                this.dungeonLevel.getGameTime()
        );
        stopOwnedRecovery(operation);
        sendFinished(operation, result, true);
        remove(operation);
    }

    private void cancel(
            DungeonPortalEntryOperation operation,
            DungeonPortalEntryResult result,
            boolean notifyClient
    ) {
        if (operation.state().terminal()) {
            return;
        }
        operation.transitionTo(
                DungeonPortalEntryOperationState.CANCELLED,
                this.dungeonLevel.getGameTime()
        );
        stopOwnedRecovery(operation);
        sendFinished(operation, result, notifyClient);
        remove(operation);
    }

    private void stopOwnedRecovery(DungeonPortalEntryOperation operation) {
        if (!operation.ownsRecovery()) {
            return;
        }
        boolean anotherConsumer = this.operationsById.values().stream()
                .anyMatch(candidate -> candidate != operation
                        && !candidate.state().terminal()
                        && candidate.portalSessionId().equals(
                        operation.portalSessionId()
                ));
        if (anotherConsumer) {
            return;
        }
        DungeonPreparationRuntime preparationRuntime =
                DungeonPreparationRuntime.get(this.dungeonLevel);
        if (preparationRuntime != null) {
            preparationRuntime.cancelPreparedEntryRecoveryForSession(
                    operation.portalSessionId()
            );
        }
    }

    private void transitionAndSend(
            DungeonPortalEntryOperation operation,
            DungeonPortalEntryOperationState state
    ) {
        operation.transitionTo(state, this.dungeonLevel.getGameTime());
        ServerPlayer player = onlinePlayer(operation.playerId());
        if (player != null) {
            PacketDistributor.sendToPlayer(
                    player,
                    new ClientboundDungeonLoadingStatePayload(
                            operation.id(),
                            state
                    )
            );
        }
    }

    private void sendFinished(
            DungeonPortalEntryOperation operation,
            DungeonPortalEntryResult result,
            boolean notifyClient
    ) {
        if (!notifyClient) {
            return;
        }
        ServerPlayer player = onlinePlayer(operation.playerId());
        if (player != null) {
            PacketDistributor.sendToPlayer(
                    player,
                    new ClientboundDungeonLoadingFinishedPayload(
                            operation.id(),
                            operation.state(),
                            result
                    )
            );
        }
    }

    private ServerPlayer onlinePlayer(UUID playerId) {
        return this.dungeonLevel.getServer().getPlayerList().getPlayer(playerId);
    }

    private void remove(DungeonPortalEntryOperation operation) {
        this.operationsById.remove(operation.id(), operation);
        this.operationsByPlayer.remove(operation.playerId(), operation);
    }

    private void clear(
            DungeonPortalEntryResult result,
            boolean notifyClients
    ) {
        assertOwnerThread();
        if (this.cleared) {
            return;
        }
        List<DungeonPortalEntryOperation> snapshot =
                new ArrayList<>(this.operationsById.values());
        for (DungeonPortalEntryOperation operation : snapshot) {
            if (!operation.state().terminal()
                    && operation.state()
                    != DungeonPortalEntryOperationState.TELEPORTING) {
                cancel(operation, result, notifyClients);
            } else {
                remove(operation);
            }
        }
        this.operationsById.clear();
        this.operationsByPlayer.clear();
        this.cleared = true;
        LEVEL_RUNTIMES.remove(this.dungeonLevel, this);
    }

    private void assertOwnerThread() {
        assertOwnerThread(this.dungeonLevel);
    }

    private static void assertOwnerThread(ServerLevel level) {
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException(
                    "Portal entry operations must run on the owning server thread"
            );
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level
                && level.dimension().equals(ModDimensions.OBELISK_DEPTHS_LEVEL)) {
            DungeonPortalEntryOperationRuntime runtime = get(level);
            if (runtime != null) {
                runtime.clear(DungeonPortalEntryResult.PREPARATION_FAILED, true);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        List<DungeonPortalEntryOperationRuntime> runtimes =
                new ArrayList<>(LEVEL_RUNTIMES.values());
        for (DungeonPortalEntryOperationRuntime runtime : runtimes) {
            if (runtime != null && !runtime.cleared) {
                runtime.clear(
                        DungeonPortalEntryResult.PLAYER_UNAVAILABLE,
                        false
                );
            }
        }
        LEVEL_RUNTIMES.clear();
    }
}
