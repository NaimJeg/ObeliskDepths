package io.github.naimjeg.obeliskdepths.dungeon.lifecycle;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.artifact.DungeonRuntimeArtifactCleanupService;
import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonInstance;
import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonReturnResult;
import io.github.naimjeg.obeliskdepths.dungeon.player.PlayerDungeonReturnService;
import io.github.naimjeg.obeliskdepths.dungeon.raid.DungeonRaidInstance;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSession;
import io.github.naimjeg.obeliskdepths.dungeon.session.DungeonSessionProgressBarService;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class DungeonEncounterFailureService {
    private static final String FAILURE_MESSAGE_KEY =
            "message.obeliskdepths.dungeon.encounter_failed";

    private DungeonEncounterFailureService() {
    }

    public static boolean failInstanceForEncounter(
            ServerLevel dungeonLevel,
            DungeonManagerSavedData data,
            DungeonRaidInstance encounter,
            String reason
    ) {
        boolean changed = data.raids().markEncounterFailed(encounter);
        Optional<DungeonInstance> instance = data.instances().get(encounter.dungeonInstanceId());

        if (instance.isPresent()) {
            changed |= data.instances().fail(encounter.dungeonInstanceId(), dungeonLevel.getGameTime());
        }

        Optional<DungeonSession> session = data.sessions().findByInstance(encounter.dungeonInstanceId());

        if (session.isPresent()) {
            if (data.sessions().markFailed(session.get())) {
                changed = true;
            }

            DungeonSessionProgressBarService.removeSession(session.get().id());
        }

        notifyAndReturnPlayers(dungeonLevel, instance, session, encounter, reason);
        DungeonRuntimeArtifactCleanupService.cleanupInstanceArtifacts(
                dungeonLevel,
                encounter.dungeonInstanceId()
        );

        ObeliskDepths.LOGGER.warn(
                "Dungeon encounter failure lifecycle applied: instance={}, encounter={}, room={}, reason={}, changed={}",
                encounter.dungeonInstanceId(),
                encounter.id(),
                encounter.roomId().map(Object::toString).orElse("<instance>"),
                reason,
                changed
        );

        return changed;
    }

    private static void notifyAndReturnPlayers(
            ServerLevel dungeonLevel,
            Optional<DungeonInstance> instance,
            Optional<DungeonSession> session,
            DungeonRaidInstance encounter,
            String reason
    ) {
        Set<UUID> playerIds = new LinkedHashSet<>();
        instance.ifPresent(value -> playerIds.addAll(value.participants()));
        session.ifPresent(value -> {
            playerIds.addAll(value.participants());
            playerIds.addAll(value.physicalParticipants());
        });

        for (UUID playerId : playerIds) {
            ServerPlayer player = dungeonLevel.getServer().getPlayerList().getPlayer(playerId);

            if (player == null) {
                continue;
            }

            player.sendSystemMessage(Component.translatable(FAILURE_MESSAGE_KEY));
            PlayerDungeonReturnResult result = PlayerDungeonReturnService.returnPlayer(player);

            if (result != PlayerDungeonReturnResult.SUCCESS
                    && result != PlayerDungeonReturnResult.NO_DUNGEON_BINDING) {
                ObeliskDepths.LOGGER.warn(
                        "Failed to return player after encounter failure: instance={}, encounter={}, player={}, reason={}, returnResult={}",
                        encounter.dungeonInstanceId(),
                        encounter.id(),
                        player.getGameProfile().name(),
                        reason,
                        result
                );
            }
        }
    }
}
