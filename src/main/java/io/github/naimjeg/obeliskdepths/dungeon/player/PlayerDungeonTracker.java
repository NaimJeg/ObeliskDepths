package io.github.naimjeg.obeliskdepths.dungeon.player;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;
import io.github.naimjeg.obeliskdepths.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Optional;

public final class PlayerDungeonTracker {
    private PlayerDungeonTracker() {
    }

    public static void bindPlayerToInstance(
            ServerPlayer player,
            DungeonInstanceId instanceId
    ) {
        bindPlayerToInstance(
                player,
                instanceId,
                player.level().dimension(),
                player.blockPosition()
        );
    }

    public static void bindPlayerToInstance(
            ServerPlayer player,
            DungeonInstanceId instanceId,
            ResourceKey<Level> returnDimension,
            BlockPos returnPos
    ) {
        player.setData(
                ModAttachments.PLAYER_DUNGEON.get(),
                PlayerDungeonData.active(
                        instanceId,
                        returnDimension,
                        returnPos
                )
        );
    }

    public static Optional<PlayerDungeonData> get(ServerPlayer player) {
        PlayerDungeonData data = player.getData(ModAttachments.PLAYER_DUNGEON.get());
        return data.isEmpty() ? Optional.empty() : Optional.of(data);
    }

    public static Optional<DungeonInstanceId> currentInstanceId(ServerPlayer player) {
        return get(player).flatMap(PlayerDungeonData::currentInstanceId);
    }

    public static Optional<DungeonRoomId> currentRoomId(ServerPlayer player) {
        return get(player).flatMap(PlayerDungeonData::currentRoomId);
    }

    public static void setCurrentRoom(
            ServerPlayer player,
            Optional<DungeonRoomId> roomId
    ) {
        PlayerDungeonData current = player.getData(ModAttachments.PLAYER_DUNGEON.get());

        if (current.isEmpty()) {
            return;
        }

        player.setData(
                ModAttachments.PLAYER_DUNGEON.get(),
                current.withCurrentRoomId(roomId)
        );
    }

    public static boolean isBoundToInstance(
            ServerPlayer player,
            DungeonInstanceId instanceId
    ) {
        return currentInstanceId(player)
                .map(instanceId::equals)
                .orElse(false);
    }

    public static boolean hasAnyDungeonBinding(ServerPlayer player) {
        return currentInstanceId(player).isPresent();
    }

    public static void set(ServerPlayer player, PlayerDungeonData data) {
        player.setData(ModAttachments.PLAYER_DUNGEON.get(), data);
    }

    public static void restore(ServerPlayer player, Optional<PlayerDungeonData> previousData) {
        previousData.ifPresentOrElse(
                data -> set(player, data),
                () -> clear(player)
        );
    }

    public static void clear(ServerPlayer player) {
        player.setData(
                ModAttachments.PLAYER_DUNGEON.get(),
                PlayerDungeonData.empty()
        );
    }
}