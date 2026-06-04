package io.github.naimjeg.obeliskdepths.dungeon.interaction;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.access.DungeonAccessController;
import io.github.naimjeg.obeliskdepths.dungeon.access.DungeonAccessResult;
import io.github.naimjeg.obeliskdepths.dungeon.id.PortalSessionId;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonStatus;
import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonData;
import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonTracker;
import io.github.naimjeg.obeliskdepths.dungeon.portal.DungeonPortalSessionLifecycle;
import io.github.naimjeg.obeliskdepths.dungeon.portal.PortalSession;
import io.github.naimjeg.obeliskdepths.dungeon.portal.PortalSessionRemovalReason;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationProfiler;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationProfiling;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationRuntime;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparedEntryValidation;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparedEntryRecoveryStatus;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparedEntryValidator;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparedPortalEntry;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSession;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSessionLifecycle;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSite;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import io.github.naimjeg.obeliskdepths.world.ObeliskDepthsTeleporter;
import io.github.naimjeg.obeliskdepths.world.ResolvedDungeonEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Objects;
import java.util.Optional;

public final class DungeonPortalEntryService {
    private static final DungeonPlayerTeleporter DEFAULT_TELEPORTER =
            ObeliskDepthsTeleporter::teleportToResolvedEntry;
    private static DungeonPlayerTeleporter teleporter = DEFAULT_TELEPORTER;

    private DungeonPortalEntryService() {
    }

    public static DungeonPortalEntryResult enter(
            ServerPlayer player,
            ServerLevel dungeonLevel,
            PortalSessionId portalSessionId
    ) {
        return DungeonPreparationProfiling.supply(
                DungeonPreparationProfiler.global(),
                DungeonPreparationProfiler.Operation.PORTAL_ENTRY,
                () -> dungeonLevel.getServer().isSameThread(),
                "Portal entry",
                () -> enterProfiled(player, dungeonLevel, portalSessionId)
        );
    }

    private static DungeonPortalEntryResult enterProfiled(
            ServerPlayer player,
            ServerLevel dungeonLevel,
            PortalSessionId portalSessionId
    ) {
        if (!dungeonLevel.getServer().isSameThread()) {
            throw new IllegalStateException(
                    "Dungeon portal entry must run on the owning server thread"
            );
        }
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        Optional<PortalSession> session = data.portalSessions().get(portalSessionId);

        if (session.isEmpty()) {
            closePreparedEntryIfPresent(dungeonLevel, portalSessionId, "session_missing");
            return fail(player, DungeonPortalEntryResult.SESSION_MISSING);
        }

        long gameTime = dungeonLevel.getGameTime();

        if (session.get().isExpired(gameTime)) {
            DungeonPortalSessionLifecycle.remove(
                    dungeonLevel,
                    portalSessionId,
                    PortalSessionRemovalReason.EXPIRED
            );
            return fail(player, DungeonPortalEntryResult.SESSION_EXPIRED);
        }

        Optional<DungeonInstance> instance =
                data.instances().get(session.get().instanceId());

        if (instance.isEmpty()) {
            DungeonPortalSessionLifecycle.remove(
                    dungeonLevel,
                    portalSessionId,
                    PortalSessionRemovalReason.INSTANCE_MISSING
            );
            return fail(player, DungeonPortalEntryResult.INSTANCE_MISSING);
        }

        if (instance.get().status() != DungeonStatus.ACTIVE) {
            DungeonPortalSessionLifecycle.remove(
                    dungeonLevel,
                    portalSessionId,
                    PortalSessionRemovalReason.INSTANCE_INACTIVE
            );
            return fail(player, DungeonPortalEntryResult.ACCESS_DENIED);
        }

        if (!player.level().dimension().equals(session.get().sourceDimension())) {
            return fail(player, DungeonPortalEntryResult.WRONG_SOURCE_DIMENSION);
        }

        Optional<PlayerDungeonData> previousPlayerData = PlayerDungeonTracker.get(player);

        if (previousPlayerData.flatMap(PlayerDungeonData::currentInstanceId)
                .filter(id -> !id.equals(instance.get().id()))
                .isPresent()) {
            return fail(player, DungeonPortalEntryResult.PLAYER_ALREADY_BOUND_ELSEWHERE);
        }

        DungeonAccessResult access = DungeonAccessController.canEnter(
                player.getUUID(),
                session.get(),
                instance.get(),
                gameTime
        );

        if (access != DungeonAccessResult.ALLOW) {
            return fail(player, DungeonPortalEntryResult.ACCESS_DENIED);
        }

        DungeonPreparationRuntime runtime =
                DungeonPreparationRuntime.getOrCreate(dungeonLevel);

        Optional<DungeonPreparedPortalEntry> preparedEntry =
                runtime.preparedPortalEntry(session.get().id());
        if (preparedEntry.isEmpty()) {
            return stabilizeDestination(
                    player,
                    data,
                    session.get(),
                    instance.get(),
                    runtime,
                    gameTime,
                    "missing_prepared_entry"
            );
        }

        DungeonPreparedPortalEntry prepared = preparedEntry.get();
        DungeonPreparedEntryValidation validation =
                DungeonPreparedEntryValidator.validate(
                        session.get().id(),
                        session.get().instanceId(),
                        instance.get().siteKey(),
                        prepared,
                        gameTime,
                        chunkPos -> dungeonLevel.getChunkSource().getChunkNow(
                                chunkPos.x(), chunkPos.z()
                        ) != null
                );
        if (validation != DungeonPreparedEntryValidation.VALID) {
            String reason = switch (validation) {
                case IDENTITY_MISMATCH -> "identity_mismatch";
                case CLOSED_OR_STALE -> "stale_or_closed";
                case CHUNK_UNAVAILABLE -> "entry_chunk_unloaded";
                case VALID -> throw new IllegalStateException(
                        "VALID prepared entry reached failure branch"
                );
            };
            closePreparedEntry(runtime, session.get(), reason);
            return stabilizeDestination(
                    player,
                    data,
                    session.get(),
                    instance.get(),
                    runtime,
                    gameTime,
                    reason
            );
        }

        ResolvedDungeonEntry entry = new ResolvedDungeonEntry(
                dungeonLevel,
                prepared.destination().position(),
                player.getYRot(),
                player.getXRot()
        );

        DungeonSession dungeonSession =
                DungeonSessionLifecycle.acquireForPortal(
                        dungeonLevel,
                        instance.get(),
                        session.get(),
                        false
                ).session();

        if (!dungeonSession.state().acceptsPortalEntry()) {
            return fail(player, DungeonPortalEntryResult.ACCESS_DENIED);
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
            return fail(player, DungeonPortalEntryResult.REGISTRATION_FAILED);
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
            return fail(player, DungeonPortalEntryResult.TELEPORT_FAILED);
        } catch (Error error) {
            mutation.rollbackBeforeTeleport(error);
            throw error;
        }

        if (teleported.isEmpty()) {
            mutation.rollbackBeforeTeleport();
            return fail(player, DungeonPortalEntryResult.TELEPORT_FAILED);
        }

        ServerPlayer enteredPlayer = teleported.get();
        beginPreparedEntryHandoffAfterTeleport(
                runtime,
                session.get(),
                prepared,
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

        try {
            ObeliskDepths.LOGGER.debug(
                    "Dungeon portal entry succeeded: player={}, instance={}, portalSession={}",
                    enteredPlayer.getGameProfile().name(),
                    instance.get().id(),
                    session.get().id()
            );
        } catch (RuntimeException observationalFailure) {
            // Successful teleport is authoritative; diagnostic logging is not.
        }
        return DungeonPortalEntryResult.SUCCESS;
    }

    private static DungeonPortalEntryResult fail(
            ServerPlayer player,
            DungeonPortalEntryResult result
    ) {
        if (result != DungeonPortalEntryResult.SUCCESS) {
            player.sendOverlayMessage(Component.translatable(result.translationKey()));
        }

        return result;
    }

    private static DungeonPortalEntryResult stabilizeDestination(
            ServerPlayer player,
            DungeonManagerSavedData data,
            PortalSession session,
            DungeonInstance instance,
            DungeonPreparationRuntime runtime,
            long gameTime,
            String reason
    ) {
        if (session.isExpired(gameTime)) {
            return fail(player, DungeonPortalEntryResult.SESSION_EXPIRED);
        }
        Optional<DungeonSite> site = data.requireReservedDungeon(
                instance.id(),
                instance.siteKey()
        ).map(io.github.naimjeg.obeliskdepths.dungeon.state.ReservedDungeonAggregate::site);
        if (instance.status() != DungeonStatus.ACTIVE || site.isEmpty()) {
            ObeliskDepths.LOGGER.error(
                    "Dungeon portal recovery metadata invariant violation: instance={}, site={}, session={}, reason={}, action=deny_recovery",
                    instance.id(),
                    instance.siteKey(),
                    session.id(),
                    reason
            );
            return fail(player, DungeonPortalEntryResult.DESTINATION_NOT_PREPARED);
        }

        DungeonPreparedEntryRecoveryStatus status =
                runtime.submitOrReusePreparedEntryRecovery(
                        session,
                        instance,
                        site.get()
                );
        if (status == DungeonPreparedEntryRecoveryStatus.REJECTED) {
            return fail(player, DungeonPortalEntryResult.DESTINATION_NOT_PREPARED);
        }

        ObeliskDepths.LOGGER.debug(
                "Prepared entry recovery {} for session={}, instance={}, reason={}",
                status,
                session.id(),
                instance.id(),
                reason
        );
        return fail(player, DungeonPortalEntryResult.DESTINATION_STABILIZING);
    }

    static void setTeleporterForTests(DungeonPlayerTeleporter replacement) {
        teleporter = Objects.requireNonNull(replacement, "replacement");
    }

    static void resetTeleporterForTests() {
        teleporter = DEFAULT_TELEPORTER;
    }

    private static void closePreparedEntryIfPresent(
            ServerLevel dungeonLevel,
            PortalSessionId sessionId,
            String reason
    ) {
        DungeonPreparationRuntime runtime = DungeonPreparationRuntime.get(dungeonLevel);
        if (runtime == null) {
            return;
        }
        try {
            runtime.removeAndClosePreparedEntry(sessionId);
        } catch (RuntimeException exception) {
            ObeliskDepths.LOGGER.error(
                    "Prepared entry close failed during portal-entry validation: session={}, reason={}",
                    sessionId,
                    reason,
                    exception
            );
        }
    }

    private static void closePreparedEntry(
            DungeonPreparationRuntime runtime,
            PortalSession session,
            String reason
    ) {
        try {
            runtime.removeAndClosePreparedEntry(session.id());
        } catch (RuntimeException exception) {
            ObeliskDepths.LOGGER.error(
                    "Prepared entry close failed during portal-entry validation: session={}, instance={}, reason={}",
                    session.id(),
                    session.instanceId(),
                    reason,
                    exception
            );
        }
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
            try {
                ObeliskDepths.LOGGER.error(
                        "Post-teleport prepared-entry handoff could not start; exact entry was released: session={}, instance={}, player={}",
                        session.id(),
                        session.instanceId(),
                        enteredPlayer.getUUID(),
                        handoffFailure
                );
            } catch (RuntimeException ignoredDiagnosticFailure) {
                // Teleport success and exact cleanup do not depend on logging.
            }
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
                sessionId, preparedEntry
        );
        if (!removed && !preparedEntry.isClosed()) {
            preparedEntry.close();
        }
    }

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
