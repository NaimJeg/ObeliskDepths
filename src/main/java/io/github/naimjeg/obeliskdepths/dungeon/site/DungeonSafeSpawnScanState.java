package io.github.naimjeg.obeliskdepths.dungeon.site;

public enum DungeonSafeSpawnScanState {
    RUNNING,
    FOUND,
    EXHAUSTED,
    CANCELLED;

    public boolean isTerminal() {
        return this != RUNNING;
    }
}
