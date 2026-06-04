package io.github.naimjeg.obeliskdepths.dungeon.interaction;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.access.DungeonAccessController;
import io.github.naimjeg.obeliskdepths.dungeon.access.DungeonAccessResult;
import io.github.naimjeg.obeliskdepths.dungeon.id.PortalSessionId;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonStatus;
import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonData;
import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonTracker;
import io.github.naimjeg.obeliskdepths.dungeon.portal.PortalSession;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.*;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSession;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSessionLifecycle;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import io.github.naimjeg.obeliskdepths.entity.DungeonPortalEntity;
import io.github.naimjeg.obeliskdepths.network.ClientboundOpenDungeonLoadingPayload;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import io.github.naimjeg.obeliskdepths.world.ObeliskDepthsTeleporter;
import io.github.naimjeg.obeliskdepths.world.ResolvedDungeonEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Objects;
import java.util.Optional;

/** Server-side orchestration boundary for one physical dungeon portal entry. */
public final class DungeonPortalEntryService {
    private static final DungeonPlayerTeleporter DEFAULT_TELEPORTER =
            ObeliskDepthsTeleporter::teleportToResolvedEntry;
    private static DungeonPlayerTeleporter teleporter = DEFAULT_TELEPORTER;

    private DungeonPortalEntryService() {
    }

    /**
     * Performs only cheap owner-thread admission, registers an operation, and
     * immediately asks the client to open its loading screen.
     */
    public static DungeonPortalEntryResult enter(
            ServerPlayer player,
            ServerLevel dungeonLevel,
            PortalSessionId portalSessionId,
            DungeonPortalEntity sourcePortal
    ) {
        assertOwnerThread(dungeonLevel);
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(portalSessionId, "portalSessionId");
        Objects.requireNonNull(sourcePortal, "sourcePortal");

        DungeonPortalEntryResult validation =
                DungeonPortalEntryOperationRuntime.validatePreTeleport(
                        dungeonLevel,
                        portalSessionId,
                        sourcePortal.getUUID(),
                        player
                );
        if (validation != DungeonPortalEntryResult.SUCCESS) {
            return failInitial(player, validation);
        }

        DungeonPortalEntryOperationRuntime operationRuntime =
                DungeonPortalEntryOperationRuntime.getOrCreate(dungeonLevel);
        Optional<DungeonPortalEntryOperation> existing =
                operationRuntime.activeForPlayer(player.getUUID());
        if (existing.isPresent()) {
            // Repeated collision ticks are deliberately idempotent: do not
            // reopen the UI or create/restart preparation work.
            return DungeonPortalEntryResult.OPERATION_ALREADY_ACTIVE;
        }

        DungeonPortalEntryOperation operation = operationRuntime.register(
                player,
                portalSessionId,
                sourcePortal.getUUID()
        );
        try {
            PacketDistributor.sendToPlayer(
                    player,
                    new ClientboundOpenDungeonLoadingPayload(
                            operation.id(),
                            DungeonPortalEntryOperationState
                                    .AWAITING_CLIENT_READY
                    )
            );
        } catch (RuntimeException sendFailure) {
            operationRuntime.cancelForPlayer(
                    player.getUUID(),
                    DungeonPortalEntryResult.CLIENT_NOT_READY,
                    false
            );
            ObeliskDepths.LOGGER.error(
                    "Failed to open dungeon loading UI: operation={}, player={}, session={}",
                    operation.id(),
                    player.getUUID(),
                    portalSessionId,
                    sendFailure
            );
            return failInitial(
                    player,
                    DungeonPortalEntryResult.REGISTRATION_FAILED
            );
        }

        ObeliskDepths.LOGGER.debug(
                "Dungeon portal entry operation awaiting client UI: operation={}, player={}, session={}",
                operation.id(),
                player.getUUID(),
                portalSessionId
        );
        return DungeonPortalEntryResult.OPERATION_STARTED;
    }

    /** Called by the immutable serverbound UI-ready payload on the owner thread. */
    public static void clientReady(
            ServerPlayer player,
            DungeonPortalEntryOperationId operationId
    ) {
        ServerLevel dungeonLevel = player.level().getServer()
                .getLevel(ModDimensions.OBELISK_DEPTHS_LEVEL);
        if (dungeonLevel == null) {
            return;
        }
        DungeonPortalEntryOperationRuntime runtime =
                DungeonPortalEntryOperationRuntime.get(dungeonLevel);
        if (runtime != null) {
            runtime.clientReady(player, operationId);
        }
    }

    public static void tick(ServerLevel dungeonLevel) {
        DungeonPortalEntryOperationRuntime runtime =
                DungeonPortalEntryOperationRuntime.get(dungeonLevel);
        if (runtime != null) {
            runtime.tick();
        }
    }

    public static void onPlayerLoggedOut(ServerPlayer player) {
        withRuntime(player, runtime -> runtime.cancelForPlayer(
                player.getUUID(),
                DungeonPortalEntryResult.PLAYER_UNAVAILABLE,
                false
        ));
    }

    public static void onPlayerDied(ServerPlayer player) {
        withRuntime(player, runtime -> runtime.cancelForPlayer(
                player.getUUID(),
                DungeonPortalEntryResult.PLAYER_UNAVAILABLE,
                true
        ));
    }

    public static void onPlayerChangedDimension(ServerPlayer player) {
        withRuntime(player, runtime -> runtime.onPlayerDimensionChanged(player));
    }

    private static void withRuntime(
            ServerPlayer player,
            java.util.function.Consumer<DungeonPortalEntryOperationRuntime> action
    ) {
        ServerLevel dungeonLevel = player.level().getServer()
                .getLevel(ModDimensions.OBELISK_DEPTHS_LEVEL);
        if (dungeonLevel == null) {
            return;
        }
        DungeonPortalEntryOperationRuntime runtime =
                DungeonPortalEntryOperationRuntime.get(dungeonLevel);
        if (runtime != null) {
            action.accept(runtime);
        }
    }

    /**
     * The only path that performs entry Store mutations and teleport. It is
     * invoked after the UI-ready handshake and exact prepared-entry validation.
     */
    static DungeonPortalEntryResult teleportPreparedEntry(
            ServerPlayer player,
            ServerLevel dungeonLevel,
            PortalSessionId portalSessionId,
            DungeonPreparedPortalEntry expectedPrepared
    ) {
        return DungeonPreparationProfiling.supply(
                DungeonPreparationProfiler.global(),
                DungeonPreparationProfiler.Operation.PORTAL_ENTRY,
                () -> dungeonLevel.getServer().isSameThread(),
                "Prepared portal teleport",
                () -> teleportPreparedEntryProfiled(
                        player,
                        dungeonLevel,
                        portalSessionId,
                        expectedPrepared
                )
        );
    }

    private static DungeonPortalEntryResult teleportPreparedEntryProfiled(
            ServerPlayer player,
            ServerLevel dungeonLevel,
            PortalSessionId portalSessionId,
            DungeonPreparedPortalEntry expectedPrepared
    ) {
        assertOwnerThread(dungeonLevel);
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        Optional<PortalSession> session = data.portalSessions().get(portalSessionId);
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
        Optional<PlayerDungeonData> previousPlayerData =
                PlayerDungeonTracker.get(player);
        if (previousPlayerData.flatMap(PlayerDungeonData::currentInstanceId)
                .filter(id -> !id.equals(instance.get().id()))
                .isPresent()) {
            return DungeonPortalEntryResult.PLAYER_ALREADY_BOUND_ELSEWHERE;
        }
        if (DungeonAccessController.canEnter(
                player.getUUID(),
                session.get(),
                instance.get(),
                gameTime
        ) != DungeonAccessResult.ALLOW) {
            return DungeonPortalEntryResult.ACCESS_DENIED;
        }

        DungeonPreparationRuntime preparationRuntime =
                DungeonPreparationRuntime.get(dungeonLevel);
        if (preparationRuntime == null
                || preparationRuntime.preparedPortalEntry(portalSessionId)
                .orElse(null) != expectedPrepared) {
            return DungeonPortalEntryResult.DESTINATION_UNAVAILABLE;
        }
        DungeonPreparedEntryValidation preparedValidation =
                DungeonPreparedEntryValidator.validate(
                        session.get().id(),
                        session.get().instanceId(),
                        instance.get().siteKey(),
                        expectedPrepared,
                        gameTime,
                        chunkPos -> dungeonLevel.getChunkSource().getChunkNow(
                                chunkPos.x(), chunkPos.z()
                        ) != null
                );
        if (preparedValidation != DungeonPreparedEntryValidation.VALID) {
            return DungeonPortalEntryResult.DESTINATION_UNAVAILABLE;
        }

        ResolvedDungeonEntry entry = new ResolvedDungeonEntry(
                dungeonLevel,
                expectedPrepared.destination().position(),
                player.getYRot(),
                player.getXRot()
        );
        DungeonSession dungeonSession = DungeonSessionLifecycle.acquireForPortal(
                dungeonLevel,
                instance.get(),
                session.get(),
                false
        ).session();
        if (!dungeonSession.state().acceptsPortalEntry()) {
            return DungeonPortalEntryResult.ACCESS_DENIED;
        }

        PortalEntryMutation mutation;
        try {
            mutation = PortalEntryMutation.begin(
                    player,
                    dungeonLevel,
                    data,
                    instance.get(),
                    session.get(),
                    dungeonSession,
                    previousPlayerData,
                    gameTime
            );
        } catch (RuntimeException exception) {
            ObeliskDepths.LOGGER.error(
                    "Dungeon portal pre-teleport registration failed: player={}, instance={}, portalSession={}",
                    player.getGameProfile().name(),
                    instance.get().id(),
                    session.get().id(),
                    exception
            );
            return DungeonPortalEntryResult.REGISTRATION_FAILED;
        }

        Optional<ServerPlayer> teleported;
        try {
            teleported = teleporter.teleport(player, entry);
        } catch (RuntimeException exception) {
            mutation.rollbackBeforeTeleport(exception);
            ObeliskDepths.LOGGER.error(
                    "Dungeon portal teleport threw before completion: player={}, instance={}, portalSession={}",
                    player.getGameProfile().name(),
                    instance.get().id(),
                    session.get().id(),
                    exception
            );
            return DungeonPortalEntryResult.TELEPORT_FAILED;
        } catch (Error error) {
            mutation.rollbackBeforeTeleport(error);
            throw error;
        }
        if (teleported.isEmpty()) {
            mutation.rollbackBeforeTeleport();
            return DungeonPortalEntryResult.TELEPORT_FAILED;
        }

        ServerPlayer enteredPlayer = teleported.get();
        beginPreparedEntryHandoffAfterTeleport(
                preparationRuntime,
                session.get(),
                expectedPrepared,
                enteredPlayer
        );
        finalizeAfterTeleport(
                dungeonLevel,
                dungeonSession,
                instance.get(),
                session.get(),
                enteredPlayer,
                gameTime
        );
        ObeliskDepths.LOGGER.debug(
                "Dungeon portal entry teleport succeeded: player={}, instance={}, portalSession={}",
                enteredPlayer.getGameProfile().name(),
                instance.get().id(),
                session.get().id()
        );
        return DungeonPortalEntryResult.SUCCESS;
    }

    private static DungeonPortalEntryResult failInitial(
            ServerPlayer player,
            DungeonPortalEntryResult result
    ) {
        player.sendOverlayMessage(Component.translatable(result.translationKey()));
        return result;
    }

    static void setTeleporterForTests(DungeonPlayerTeleporter replacement) {
        teleporter = Objects.requireNonNull(replacement, "replacement");
    }

    static void resetTeleporterForTests() {
        teleporter = DEFAULT_TELEPORTER;
    }

    private static void beginPreparedEntryHandoffAfterTeleport(
            DungeonPreparationRuntime runtime,
            PortalSession session,
            DungeonPreparedPortalEntry preparedEntry,
            ServerPlayer enteredPlayer
    ) {
        try {
            runtime.beginPostTeleportHandoff(
                    session.id(),
                    preparedEntry,
                    enteredPlayer
            );
        } catch (RuntimeException handoffFailure) {
            try {
                releaseRejectedHandoff(runtime, session.id(), preparedEntry);
            } catch (RuntimeException cleanupFailure) {
                if (cleanupFailure != handoffFailure) {
                    handoffFailure.addSuppressed(cleanupFailure);
                }
            } catch (Error cleanupError) {
                cleanupError.addSuppressed(handoffFailure);
                throw cleanupError;
            }
            ObeliskDepths.LOGGER.error(
                    "Post-teleport prepared-entry handoff could not start; exact entry was released: session={}, instance={}, player={}",
                    session.id(),
                    session.instanceId(),
                    enteredPlayer.getUUID(),
                    handoffFailure
            );
        } catch (Error handoffError) {
            try {
                releaseRejectedHandoff(runtime, session.id(), preparedEntry);
            } catch (RuntimeException | Error cleanupFailure) {
                if (cleanupFailure != handoffError) {
                    handoffError.addSuppressed(cleanupFailure);
                }
            }
            throw handoffError;
        }
    }

    private static void releaseRejectedHandoff(
            DungeonPreparationRuntime runtime,
            PortalSessionId sessionId,
            DungeonPreparedPortalEntry preparedEntry
    ) {
        boolean removed = runtime.removeAndClosePreparedEntryExact(
                sessionId,
                preparedEntry
        );
        if (!removed && !preparedEntry.isClosed()) {
            preparedEntry.close();
        }
    }

    /** Idempotent set-style finalization; failures are reconciled after teleport. */
    private static void finalizeAfterTeleport(
            ServerLevel dungeonLevel,
            DungeonSession dungeonSession,
            DungeonInstance instance,
            PortalSession session,
            ServerPlayer enteredPlayer,
            long gameTime
    ) {
        try {
            DungeonSessionLifecycle.markPortalEntrySucceeded(
                    dungeonLevel,
                    dungeonSession,
                    enteredPlayer.getUUID(),
                    gameTime
            );
        } catch (RuntimeException exception) {
            ObeliskDepths.LOGGER.error(
                    "Post-teleport portal-entry finalization failed; preserving dungeon binding for reconciliation: player={}, instance={}, portalSession={}",
                    enteredPlayer.getGameProfile().name(),
                    instance.id(),
                    session.id(),
                    exception
            );
            try {
                DungeonSessionLifecycle.registerParticipant(
                        dungeonLevel,
                        instance.id(),
                        enteredPlayer.getUUID()
                );
                DungeonSessionLifecycle.registerPhysicalParticipant(
                        dungeonLevel,
                        instance.id(),
                        enteredPlayer.getUUID()
                );
            } catch (RuntimeException reconciliationFailure) {
                ObeliskDepths.LOGGER.error(
                        "Post-teleport portal-entry reconciliation also failed: player={}, instance={}, portalSession={}",
                        enteredPlayer.getGameProfile().name(),
                        instance.id(),
                        session.id(),
                        reconciliationFailure
                );
            }
        }
    }

    private static void assertOwnerThread(ServerLevel dungeonLevel) {
        if (!dungeonLevel.getServer().isSameThread()) {
            throw new IllegalStateException(
                    "Dungeon portal entry must run on the owning server thread"
            );
        }
    }

    private static final class PortalEntryMutation {
        private final ServerPlayer player;
        private final ServerLevel dungeonLevel;
        private final DungeonManagerSavedData data;
        private final DungeonInstance instance;
        private final PortalSession session;
        private final Optional<PlayerDungeonData> previousPlayerData;
        private boolean instanceParticipantAdded;
        private boolean portalParticipantAdded;
        private boolean dungeonSessionParticipantAdded;
        private boolean playerBound;

        private PortalEntryMutation(
                ServerPlayer player,
                ServerLevel dungeonLevel,
                DungeonManagerSavedData data,
                DungeonInstance instance,
                PortalSession session,
                Optional<PlayerDungeonData> previousPlayerData
        ) {
            this.player = player;
            this.dungeonLevel = dungeonLevel;
            this.data = data;
            this.instance = instance;
            this.session = session;
            this.previousPlayerData = previousPlayerData;
        }

        static PortalEntryMutation begin(
                ServerPlayer player,
                ServerLevel dungeonLevel,
                DungeonManagerSavedData data,
                DungeonInstance instance,
                PortalSession session,
                DungeonSession dungeonSession,
                Optional<PlayerDungeonData> previousPlayerData,
                long gameTime
        ) {
            PortalEntryMutation mutation = new PortalEntryMutation(
                    player,
                    dungeonLevel,
                    data,
                    instance,
                    session,
                    previousPlayerData
            );
            try {
                mutation.instanceParticipantAdded =
                        !instance.participants().contains(player.getUUID());
                data.instances().addParticipant(
                        instance.id(),
                        player.getUUID(),
                        gameTime
                );
                mutation.portalParticipantAdded =
                        !session.participants().contains(player.getUUID());
                data.portalSessions().addParticipant(
                        session.id(),
                        player.getUUID()
                );
                mutation.dungeonSessionParticipantAdded =
                        !dungeonSession.participants().contains(player.getUUID());
                DungeonSessionLifecycle.registerParticipant(
                        dungeonLevel,
                        instance.id(),
                        player.getUUID()
                );

                ResourceKey<Level> returnDimension = player.level().dimension();
                BlockPos returnPos = player.blockPosition();
                mutation.playerBound = true;
                PlayerDungeonTracker.bindPlayerToInstance(
                        player,
                        instance.id(),
                        returnDimension,
                        returnPos
                );
                return mutation;
            } catch (RuntimeException | Error failure) {
                mutation.rollbackBeforeTeleport(failure);
                throw failure;
            }
        }

        void rollbackBeforeTeleport() {
            rollbackBeforeTeleport(null);
        }

        void rollbackBeforeTeleport(Throwable originalFailure) {
            PortalEntryRollback cleanup = new PortalEntryRollback();
            if (this.playerBound) {
                cleanup.attempt("player binding", () -> {
                    try {
                        PlayerDungeonTracker.restore(
                                this.player,
                                this.previousPlayerData
                        );
                    } finally {
                        this.playerBound = false;
                    }
                });
            }
            if (this.dungeonSessionParticipantAdded) {
                cleanup.attempt("dungeon-session participant", () -> {
                    try {
                        DungeonSessionLifecycle.removeParticipant(
                                this.dungeonLevel,
                                this.instance.id(),
                                this.player.getUUID()
                        );
                    } finally {
                        this.dungeonSessionParticipantAdded = false;
                    }
                });
            }
            if (this.portalParticipantAdded) {
                cleanup.attempt("portal-session participant", () -> {
                    try {
                        this.data.portalSessions().removeParticipant(
                                this.session.id(),
                                this.player.getUUID()
                        );
                    } finally {
                        this.portalParticipantAdded = false;
                    }
                });
            }
            if (this.instanceParticipantAdded) {
                cleanup.attempt("instance participant", () -> {
                    try {
                        this.data.instances().removeParticipant(
                                this.instance.id(),
                                this.player.getUUID()
                        );
                    } finally {
                        this.instanceParticipantAdded = false;
                    }
                });
            }
            cleanup.finish(originalFailure);
        }
    }
}
