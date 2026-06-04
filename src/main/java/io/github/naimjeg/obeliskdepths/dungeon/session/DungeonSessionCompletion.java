package io.github.naimjeg.obeliskdepths.dungeon.session;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.state.DungeonManagerSavedData;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

public final class DungeonSessionCompletion {
    private DungeonSessionCompletion() {
    }

    public static boolean completeSession(
            ServerLevel dungeonLevel,
            DungeonInstanceId instanceId
    ) {
        DungeonManagerSavedData data = DungeonManagerSavedData.get(dungeonLevel);
        Optional<DungeonSession> session =
                data.sessions().findByInstance(instanceId);

        if (session.isEmpty()) {
            return false;
        }

        boolean changed = data.sessions().markCompleted(session.get());

        if (changed) {
            DungeonSessionProgressBarService.removeSession(session.get().id());
        }

        return changed;
    }
}
