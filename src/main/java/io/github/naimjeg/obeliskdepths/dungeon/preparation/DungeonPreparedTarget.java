package io.github.naimjeg.obeliskdepths.dungeon.preparation;

public sealed interface DungeonPreparedTarget extends DungeonPreparationResult
        permits ExistingOpenJoinTarget, NewAuthoritativeSiteTarget {
    DungeonPreparationRequest request();
}
