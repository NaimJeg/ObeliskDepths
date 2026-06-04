package io.github.naimjeg.obeliskdepths.dungeon.player;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Optional;

public record PlayerDungeonData(
        Optional<DungeonInstanceId> currentInstanceId,
        Optional<ResourceKey<Level>> returnDimension,
        Optional<BlockPos> returnPos,
        Optional<DungeonRoomId> currentRoomId
) {
    public static final MapCodec<PlayerDungeonData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DungeonInstanceId.CODEC.optionalFieldOf("current_instance_id")
                    .forGetter(PlayerDungeonData::currentInstanceId),
            ResourceKey.codec(Registries.DIMENSION).optionalFieldOf("return_dimension")
                    .forGetter(PlayerDungeonData::returnDimension),
            BlockPos.CODEC.optionalFieldOf("return_pos")
                    .forGetter(PlayerDungeonData::returnPos),
            DungeonRoomId.CODEC.optionalFieldOf("current_room_id")
                    .forGetter(PlayerDungeonData::currentRoomId)
    ).apply(instance, PlayerDungeonData::new));

    public static PlayerDungeonData empty() {
        return new PlayerDungeonData(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }

    public static PlayerDungeonData active(
            DungeonInstanceId instanceId,
            ResourceKey<Level> returnDimension,
            BlockPos returnPos
    ) {
        return new PlayerDungeonData(
                Optional.of(instanceId),
                Optional.of(returnDimension),
                Optional.of(returnPos),
                Optional.empty()
        );
    }

    public PlayerDungeonData withCurrentRoomId(Optional<DungeonRoomId> roomId) {
        return new PlayerDungeonData(
                this.currentInstanceId,
                this.returnDimension,
                this.returnPos,
                roomId
        );
    }

    public boolean isEmpty() {
        return this.currentInstanceId.isEmpty()
                && this.returnDimension.isEmpty()
                && this.returnPos.isEmpty()
                && this.currentRoomId.isEmpty();
    }
}