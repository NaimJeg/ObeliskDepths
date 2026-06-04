package io.github.naimjeg.obeliskdepths.dungeon.reward;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomId;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;

public record DungeonRewardContext(
        ServerLevel level,
        DungeonInstance instance,
        DungeonInstanceId instanceId,
        Optional<DungeonRoomId> roomId,
        DungeonRoomType roomType,
        long rewardSeed,
        int participantCount
) {
}
