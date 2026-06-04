package io.github.naimjeg.obeliskdepths.dungeon.interaction;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.access.DungeonAccessController;
import io.github.naimjeg.obeliskdepths.dungeon.access.DungeonAccessResult;
import io.github.naimjeg.obeliskdepths.dungeon.id.PortalSessionId;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonData;
import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonTracker;
import io.github.naimjeg.obeliskdepths.dungeon.portal.PortalSession;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSession;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSessionLifecycle;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import io.github.naimjeg.obeliskdepths.world.ObeliskDepthsTeleporter;
import io.github.naimjeg.obeliskdepths.world.ResolvedDungeonEntry;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

public final class DungeonPortalEntryService {
    private DungeonPortalEntryService() {
    }

    public static DungeonPortalEntryResult enter(
            ServerPlayer player,
            ServerLevel dungeonLevel,
            PortalSessionId portalSessionId
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        Optional<PortalSession> session = data.portalSessions().get(portalSessionId);

        if (session.isEmpty()) {
            return fail(player, DungeonPortalEntryResult.SESSION_MISSING);
        }

        long gameTime = dungeonLevel.getGameTime();

        if (session.get().isExpired(gameTime)) {
            data.portalSessions().remove(portalSessionId);
            return fail(player, DungeonPortalEntryResult.SESSION_EXPIRED);
        }

        Optional<DungeonInstance> instance =
                data.instances().get(session.get().instanceId());

        if (instance.isEmpty()) {
            data.portalSessions().remove(portalSessionId);
            return fail(player, DungeonPortalEntryResult.INSTANCE_MISSING);
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

        Optional<ResolvedDungeonEntry> entry = ObeliskDepthsTeleporter.resolveInstanceStart(
                dungeonLevel,
                instance.get(),
                player.getYRot(),
                player.getXRot()
        );

        if (entry.isEmpty()) {
            return fail(player, DungeonPortalEntryResult.DESTINATION_UNAVAILABLE);
        }

        DungeonSession dungeonSession =
                DungeonSessionLifecycle.getOrCreateForPortal(
                        dungeonLevel,
                        instance.get(),
                        session.get(),
                        false
                );

        if (!dungeonSession.state().acceptsPortalEntry()) {
            return fail(player, DungeonPortalEntryResult.ACCESS_DENIED);
        }

        ResourceKey<Level> returnDimension = player.level().dimension();
        BlockPos returnPos = player.blockPosition();
        boolean instanceParticipantAdded = false;
        boolean portalParticipantAdded = false;
        boolean dungeonSessionParticipantAdded = false;
        boolean playerBound = false;

        try {
            instanceParticipantAdded = data.instances().addParticipant(
                    instance.get().id(),
                    player.getUUID(),
                    gameTime
            );
            portalParticipantAdded = data.portalSessions().addParticipant(
                    session.get().id(),
                    player.getUUID()
            );
            dungeonSessionParticipantAdded = DungeonSessionLifecycle.registerParticipant(
                    dungeonLevel,
                    instance.get().id(),
                    player.getUUID()
            );

            PlayerDungeonTracker.bindPlayerToInstance(
                    player,
                    instance.get().id(),
                    returnDimension,
                    returnPos
            );
            playerBound = true;

            Optional<ServerPlayer> teleported =
                    ObeliskDepthsTeleporter.teleportToResolvedEntry(player, entry.get());

            if (teleported.isEmpty()) {
                rollback(
                        player,
                        dungeonLevel,
                        instance.get(),
                        session.get(),
                        previousPlayerData,
                        playerBound,
                        dungeonSessionParticipantAdded,
                        portalParticipantAdded,
                        instanceParticipantAdded
                );
                return fail(player, DungeonPortalEntryResult.TELEPORT_FAILED);
            }

            DungeonSessionLifecycle.markPortalEntrySucceeded(
                    dungeonLevel,
                    dungeonSession,
                    teleported.get().getUUID(),
                    gameTime
            );

            ObeliskDepths.LOGGER.debug(
                    "Dungeon portal entry succeeded: player={}, instance={}, portalSession={}",
                    player.getGameProfile().name(),
                    instance.get().id(),
                    session.get().id()
            );
            return DungeonPortalEntryResult.SUCCESS;
        } catch (RuntimeException exception) {
            rollback(
                    player,
                    dungeonLevel,
                    instance.get(),
                    session.get(),
                    previousPlayerData,
                    playerBound,
                    dungeonSessionParticipantAdded,
                    portalParticipantAdded,
                    instanceParticipantAdded
            );
            ObeliskDepths.LOGGER.error(
                    "Dungeon portal entry failed unexpectedly: player={}, instance={}, portalSession={}",
                    player.getGameProfile().name(),
                    instance.get().id(),
                    session.get().id(),
                    exception
            );
            return fail(player, DungeonPortalEntryResult.REGISTRATION_FAILED);
        }
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

    private static void rollback(
            ServerPlayer player,
            ServerLevel dungeonLevel,
            DungeonInstance instance,
            PortalSession session,
            Optional<PlayerDungeonData> previousPlayerData,
            boolean playerBound,
            boolean dungeonSessionParticipantAdded,
            boolean portalParticipantAdded,
            boolean instanceParticipantAdded
    ) {
        if (playerBound) {
            PlayerDungeonTracker.restore(player, previousPlayerData);
        }

        if (dungeonSessionParticipantAdded) {
            DungeonSessionLifecycle.removeParticipant(
                    dungeonLevel,
                    instance.id(),
                    player.getUUID()
            );
        }

        if (portalParticipantAdded) {
            DungeonManagerSavedData.get(dungeonLevel).portalSessions().removeParticipant(
                    session.id(),
                    player.getUUID()
            );
        }

        if (instanceParticipantAdded) {
            DungeonManagerSavedData.get(dungeonLevel).instances().removeParticipant(
                    instance.id(),
                    player.getUUID()
            );
        }
    }
}
