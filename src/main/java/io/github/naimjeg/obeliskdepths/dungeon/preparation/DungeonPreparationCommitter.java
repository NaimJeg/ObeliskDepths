package io.github.naimjeg.obeliskdepths.dungeon.preparation;

interface DungeonPreparationCommitter {
    default DungeonActivationPreflightResult preflight(
            DungeonPreparationJob job,
            DungeonPreparationExecutionContext context
    ) {
        return DungeonActivationPreflight.prepare(job, context);
    }

    DungeonActivationCommitResult commit(
            DungeonPreparationJob job,
            DungeonPreparationExecutionContext context
    );

    default void afterCommitReady(
            DungeonPreparationJob job,
            DungeonActivationCommitResult result
    ) {
    }
}
