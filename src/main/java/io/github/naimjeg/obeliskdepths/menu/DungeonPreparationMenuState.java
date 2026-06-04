package io.github.naimjeg.obeliskdepths.menu;

import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationStage;

import java.util.Optional;

/**
 * Immutable bounded client-facing view of one authoritative menu submission.
 * The token identifies the submission; it is not an atomic packet-batch marker.
 */
public record DungeonPreparationMenuState(
        boolean active,
        int synchronizationToken,
        int stageWireCode,
        int completed,
        int total,
        int terminalStatus,
        int terminalReasonWireCode
) {
    public DungeonPreparationMenuState {
        synchronizationToken = bounded(synchronizationToken);
        completed = bounded(completed);
        total = bounded(total);
        completed = Math.min(completed, total);
    }

    public Optional<DungeonPreparationStage> stage() {
        return DungeonPreparationStage.fromWireCode(this.stageWireCode);
    }

    public boolean determinate() {
        return this.total > 0;
    }

    public boolean inputLocked() {
        return this.active;
    }

    public boolean matchesSubmissionToken(int updateToken) {
        return updateToken > 0 && updateToken == this.synchronizationToken;
    }

    private static int bounded(int value) {
        return Math.min(Short.MAX_VALUE, Math.max(0, value));
    }
}
