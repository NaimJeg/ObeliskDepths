package io.github.naimjeg.obeliskdepths.dungeon.player;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSessionLifecycle;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSessionPresence;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import io.github.naimjeg.obeliskdepths.world.ObeliskDepthsTeleporter;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public final class PlayerDungeonReturnService {
    private PlayerDungeonReturnService() {
    }

    public static PlayerDungeonReturnResult checkReturn(ServerPlayer player) {
        return resolveReturn(player).result();
    }

    public static PlayerDungeonReturnResult checkScrollReturn(ServerPlayer player) {
        return resolveScrollReturn(player).result();
    }

    public static PlayerDungeonReturnResult returnPlayer(ServerPlayer player) {
        ResolvedReturn resolvedReturn = resolveReturn(player);
        if (resolvedReturn.result() != PlayerDungeonReturnResult.SUCCESS) {
            return resolvedReturn.result();
        }

        DungeonInstanceId instanceId = resolvedReturn.instanceId().orElseThrow();
        ServerLevel returnLevel = resolvedReturn.returnLevel().orElseThrow();
        BlockPos returnPos = resolvedReturn.returnPos().orElseThrow();
        ServerLevel dungeonLevel = resolvedReturn.dungeonLevel().orElseThrow();

        Optional<ServerPlayer> returnedPlayer =
                ObeliskDepthsTeleporter.teleportToLevel(
                        player,
                        returnLevel,
                        returnPos
                );

        if (returnedPlayer.isEmpty()) {
            return PlayerDungeonReturnResult.TELEPORT_FAILED;
        }

        ServerPlayer effectivePlayer = returnedPlayer.get();

        DungeonSessionLifecycle.unregisterPhysicalParticipant(
                dungeonLevel,
                instanceId,
                effectivePlayer.getUUID()
        );

        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        data.instances().removeParticipant(
                instanceId,
                effectivePlayer.getUUID()
        );

        data.portalSessions().removeParticipantFromInstanceSessions(
                instanceId,
                effectivePlayer.getUUID()
        );

        PlayerDungeonTracker.clear(effectivePlayer);

        return PlayerDungeonReturnResult.SUCCESS;
    }

    public static PlayerDungeonReturnResult returnPlayerFromScroll(ServerPlayer player) {
        ResolvedScrollReturn resolvedReturn = resolveScrollReturn(player);
        if (resolvedReturn.result() != PlayerDungeonReturnResult.SUCCESS) {
            return resolvedReturn.result();
        }

        Optional<DungeonInstanceId> boundInstanceId =
                PlayerDungeonTracker.currentInstanceId(player);
        Optional<DungeonInstanceId> physicalInstanceId =
                resolvedReturn.dungeonLevel()
                        .flatMap(level -> DungeonSessionPresence.findCurrentPhysicalInstance(
                                level,
                                player
                        ));

        ServerLevel returnLevel = resolvedReturn.returnLevel().orElseThrow();
        Vec3 returnPos = resolvedReturn.returnPos().orElseThrow();

        Optional<ServerPlayer> returnedPlayer =
                ObeliskDepthsTeleporter.teleportToLevel(
                        player,
                        returnLevel,
                        returnPos
                );

        if (returnedPlayer.isEmpty()) {
            return PlayerDungeonReturnResult.TELEPORT_FAILED;
        }

        ServerPlayer effectivePlayer = returnedPlayer.get();
        ServerLevel dungeonLevel = resolvedReturn.dungeonLevel().orElse(null);
        if (dungeonLevel != null) {
            DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
            Set<DungeonInstanceId> cleanupTargets = new LinkedHashSet<>();
            boundInstanceId.ifPresent(cleanupTargets::add);
            physicalInstanceId.ifPresent(cleanupTargets::add);

            for (DungeonInstanceId instanceId : cleanupTargets) {
                try {
                    DungeonSessionLifecycle.unregisterPhysicalParticipant(
                            dungeonLevel,
                            instanceId,
                            effectivePlayer.getUUID()
                    );
                } catch (RuntimeException exception) {
                    io.github.naimjeg.obeliskdepths.ObeliskDepths.LOGGER.warn(
                            "Return Scroll physical cleanup failed after successful teleport: player={}, instance={}",
                            effectivePlayer.getUUID(),
                            instanceId,
                            exception
                    );
                }
            }

            boundInstanceId.ifPresent(instanceId -> {
                try {
                    data.instances().removeParticipant(
                            instanceId,
                            effectivePlayer.getUUID()
                    );
                    data.portalSessions().removeParticipantFromInstanceSessions(
                            instanceId,
                            effectivePlayer.getUUID()
                    );
                } catch (RuntimeException exception) {
                    io.github.naimjeg.obeliskdepths.ObeliskDepths.LOGGER.warn(
                            "Return Scroll bound-instance cleanup failed after successful teleport: player={}, instance={}",
                            effectivePlayer.getUUID(),
                            instanceId,
                            exception
                    );
                }
            });
        }

        PlayerDungeonTracker.clear(effectivePlayer);

        return resolvedReturn.mode().fallback()
                ? PlayerDungeonReturnResult.SUCCESS_EMERGENCY_FALLBACK
                : PlayerDungeonReturnResult.SUCCESS;
    }

    private static ResolvedReturn resolveReturn(ServerPlayer player) {
        Optional<PlayerDungeonData> optionalData = PlayerDungeonTracker.get(player);

        if (optionalData.isEmpty()) {
            return ResolvedReturn.failure(PlayerDungeonReturnResult.NO_DUNGEON_BINDING);
        }

        PlayerDungeonData data = optionalData.get();

        Optional<DungeonInstanceId> optionalInstanceId = data.currentInstanceId();
        Optional<ResourceKey<Level>> optionalReturnDimension = data.returnDimension();
        Optional<BlockPos> optionalReturnPos = data.returnPos();

        if (optionalInstanceId.isEmpty()
                || optionalReturnDimension.isEmpty()
                || optionalReturnPos.isEmpty()) {
            return ResolvedReturn.failure(PlayerDungeonReturnResult.INCOMPLETE_RETURN_DATA);
        }

        MinecraftServer server = player.level().getServer();

        ServerLevel returnLevel = server.getLevel(optionalReturnDimension.get());

        if (returnLevel == null) {
            return ResolvedReturn.failure(PlayerDungeonReturnResult.RETURN_LEVEL_MISSING);
        }

        ServerLevel dungeonLevel = server.getLevel(ModDimensions.OBELISK_DEPTHS_LEVEL);
        if (dungeonLevel == null) {
            return ResolvedReturn.failure(PlayerDungeonReturnResult.DUNGEON_LEVEL_MISSING);
        }

        return new ResolvedReturn(
                PlayerDungeonReturnResult.SUCCESS,
                optionalInstanceId,
                Optional.of(returnLevel),
                optionalReturnPos,
                Optional.of(dungeonLevel)
        );
    }

    private static ResolvedScrollReturn resolveScrollReturn(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()) {
            return ResolvedScrollReturn.failure(PlayerDungeonReturnResult.NO_DUNGEON_BINDING);
        }

        if (!player.level().dimension().equals(ModDimensions.OBELISK_DEPTHS_LEVEL)) {
            return ResolvedScrollReturn.failure(PlayerDungeonReturnResult.NOT_IN_DUNGEON_DIMENSION);
        }

        MinecraftServer server = player.level().getServer();
        ServerLevel dungeonLevel = server.getLevel(ModDimensions.OBELISK_DEPTHS_LEVEL);
        if (dungeonLevel == null) {
            return ResolvedScrollReturn.failure(PlayerDungeonReturnResult.DUNGEON_LEVEL_MISSING);
        }

        Optional<PlayerDungeonData> optionalData = PlayerDungeonTracker.get(player);
        if (optionalData.isPresent()) {
            PlayerDungeonData data = optionalData.get();
            if (data.returnDimension().isPresent() && data.returnPos().isPresent()) {
                ServerLevel returnLevel = server.getLevel(data.returnDimension().get());
                if (returnLevel != null && isInBuildHeight(returnLevel, data.returnPos().get())) {
                    ScrollReturnMode mode = data.currentInstanceId().isPresent()
                            ? ScrollReturnMode.SAVED_PORTAL_DESTINATION
                            : ScrollReturnMode.SAVED_DESTINATION_WITHOUT_INSTANCE;
                    return ResolvedScrollReturn.success(
                            returnLevel,
                            Vec3.atCenterOf(data.returnPos().get()),
                            dungeonLevel,
                            mode
                    );
                }
            }
        }

        TeleportTransition respawn =
                player.findRespawnPositionAndUseSpawnBlock(
                        false,
                        TeleportTransition.DO_NOTHING
                );
        if (player.getRespawnConfig() != null
                && !respawn.missingRespawnBlock()
                && isInBuildHeight(respawn.newLevel(), BlockPos.containing(respawn.position()))) {
            return ResolvedScrollReturn.success(
                    respawn.newLevel(),
                    respawn.position(),
                    dungeonLevel,
                    ScrollReturnMode.PLAYER_RESPAWN
            );
        }

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld != null && isInBuildHeight(overworld, overworld.getRespawnData().pos())) {
            return ResolvedScrollReturn.success(
                    overworld,
                    Vec3.atCenterOf(overworld.getRespawnData().pos()),
                    dungeonLevel,
                    ScrollReturnMode.OVERWORLD_SPAWN
            );
        }

        return ResolvedScrollReturn.failure(PlayerDungeonReturnResult.NO_SAFE_RETURN_DESTINATION);
    }

    private static boolean isInBuildHeight(
            ServerLevel level,
            BlockPos pos
    ) {
        return pos.getY() >= level.getMinY() && pos.getY() < level.getMaxY();
    }

    private record ResolvedReturn(
            PlayerDungeonReturnResult result,
            Optional<DungeonInstanceId> instanceId,
            Optional<ServerLevel> returnLevel,
            Optional<BlockPos> returnPos,
            Optional<ServerLevel> dungeonLevel
    ) {
        private static ResolvedReturn failure(PlayerDungeonReturnResult result) {
            return new ResolvedReturn(
                    result,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty()
            );
        }
    }

    private record ResolvedScrollReturn(
            PlayerDungeonReturnResult result,
            Optional<ServerLevel> returnLevel,
            Optional<Vec3> returnPos,
            Optional<ServerLevel> dungeonLevel,
            ScrollReturnMode mode
    ) {
        private static ResolvedScrollReturn success(
                ServerLevel returnLevel,
                Vec3 returnPos,
                ServerLevel dungeonLevel,
                ScrollReturnMode mode
        ) {
            return new ResolvedScrollReturn(
                    PlayerDungeonReturnResult.SUCCESS,
                    Optional.of(returnLevel),
                    Optional.of(returnPos),
                    Optional.of(dungeonLevel),
                    mode
            );
        }

        private static ResolvedScrollReturn failure(PlayerDungeonReturnResult result) {
            return new ResolvedScrollReturn(
                    result,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    ScrollReturnMode.NONE
            );
        }
    }

    private enum ScrollReturnMode {
        NONE(false),
        SAVED_PORTAL_DESTINATION(false),
        SAVED_DESTINATION_WITHOUT_INSTANCE(false),
        PLAYER_RESPAWN(true),
        OVERWORLD_SPAWN(true);

        private final boolean fallback;

        ScrollReturnMode(boolean fallback) {
            this.fallback = fallback;
        }

        private boolean fallback() {
            return this.fallback;
        }
    }
}
