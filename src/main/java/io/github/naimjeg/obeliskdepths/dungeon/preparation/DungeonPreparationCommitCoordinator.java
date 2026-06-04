package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import net.minecraft.server.level.ServerLevel;

import java.util.Objects;

final class DungeonPreparationCommitCoordinator implements DungeonPreparationCommitter {
    private final ServerLevel dungeonLevel;
    private final DungeonPreparedEntryRegistry preparedEntryRegistry;
    private final DungeonSiteClaimManager claimManager;

    DungeonPreparationCommitCoordinator(
            ServerLevel dungeonLevel,
            DungeonPreparedEntryRegistry preparedEntryRegistry,
            DungeonSiteClaimManager claimManager
    ) {
        this.dungeonLevel = Objects.requireNonNull(dungeonLevel, "dungeonLevel");
        this.preparedEntryRegistry = Objects.requireNonNull(
                preparedEntryRegistry,
                "preparedEntryRegistry"
        );
        this.claimManager = Objects.requireNonNull(claimManager, "claimManager");
    }

    @Override
    public DungeonActivationCommitResult commit(
            DungeonPreparationJob job,
            DungeonPreparationExecutionContext context
    ) {
        return DungeonActivationCommitService.commitPreparedSolo(
                this.dungeonLevel,
                job,
                context,
                this.preparedEntryRegistry,
                this.claimManager
        );
    }

    @Override
    public void afterCommitReady(
            DungeonPreparationJob job,
            DungeonActivationCommitResult result
    ) {
        DungeonActivationCommitService.finishSuccessfulPreparedSolo(
                this.dungeonLevel,
                job,
                result
        );
    }
}
