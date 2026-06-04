package io.github.naimjeg.obeliskdepths.dungeon.completion;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonStatus;
import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonReturnResult;
import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonReturnService;
import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonTracker;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomState;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomStatus;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomType;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSessionCompletion;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import io.github.naimjeg.obeliskdepths.registry.ModDimensions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public final class DungeonCompletionService {
    private DungeonCompletionService() {
    }

    public static boolean canComplete(
            DungeonManagerSavedData data,
            DungeonInstance instance
    ) {
        for (DungeonRoomState room : data.roomStates().allForInstance(instance.id())) {
            if (!blocksCompletion(room)) {
                continue;
            }

            if (room.status() != DungeonRoomStatus.CLEARED) {
                ObeliskDepths.LOGGER.debug(
                        "Dungeon completion blocked: instance={}, room={}, type={}, status={}",
                        instance.id(),
                        room.roomId(),
                        room.type().getSerializedName(),
                        room.status().getSerializedName()
                );
                return false;
            }
        }

        return true;
    }

    public static DungeonCompletionResult completeAndReturnPlayer(
            ServerPlayer player
    ) {
        /*
         * Legacy/debug helper. Core gameplay should prefer enterRewardPhase(...):
         * boss completion exposes reward chest gameplay first instead of
         * returning only the triggering player.
         */
        DungeonCompletionResult phaseResult = enterRewardPhase(player);

        if (phaseResult != DungeonCompletionResult.SUCCESS) {
            return phaseResult;
        }

        PlayerDungeonReturnResult returnResult =
                PlayerDungeonReturnService.returnPlayer(player);

        if (returnResult != PlayerDungeonReturnResult.SUCCESS) {
            return DungeonCompletionResult.RETURN_FAILED;
        }

        return DungeonCompletionResult.SUCCESS;
    }

    public static DungeonCompletionResult enterRewardPhase(
            ServerPlayer player
    ) {
        Optional<io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId> instanceId =
                PlayerDungeonTracker.currentInstanceId(player);

        if (instanceId.isEmpty()) {
            return DungeonCompletionResult.NO_DUNGEON_BINDING;
        }

        MinecraftServer server = player.level().getServer();

        ServerLevel dungeonLevel = server.getLevel(ModDimensions.OBELISK_DEPTHS_LEVEL);

        if (dungeonLevel == null) {
            return DungeonCompletionResult.DUNGEON_LEVEL_MISSING;
        }

        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        Optional<DungeonInstance> instance = data.instances().get(instanceId.get());

        if (instance.isEmpty()) {
            return DungeonCompletionResult.INSTANCE_MISSING;
        }

        if (!canComplete(data, instance.get())) {
            return DungeonCompletionResult.NOT_COMPLETE;
        }

        if (!data.instances().setStatus(instanceId.get(), DungeonStatus.REWARD_PHASE)) {
            return DungeonCompletionResult.INSTANCE_MISSING;
        }

        DungeonSessionCompletion.completeSession(dungeonLevel, instanceId.get());

        ObeliskDepths.LOGGER.debug(
                "Dungeon instance entered reward phase: instance={}",
                instanceId.get()
        );

        return DungeonCompletionResult.SUCCESS;
    }

    private static boolean blocksCompletion(DungeonRoomState room) {
        return room.type() == DungeonRoomType.COMBAT
                || room.type() == DungeonRoomType.BOSS;
    }
}
