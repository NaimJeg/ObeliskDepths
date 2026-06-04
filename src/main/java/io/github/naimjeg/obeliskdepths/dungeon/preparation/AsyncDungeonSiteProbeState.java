package io.github.naimjeg.obeliskdepths.dungeon.preparation;

/**
 * Lifecycle state of an {@link AsyncDungeonSiteProbe} scan session.
 */
public enum AsyncDungeonSiteProbeState {
    CREATED,
    RUNNING,
    COMPLETED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }
}
