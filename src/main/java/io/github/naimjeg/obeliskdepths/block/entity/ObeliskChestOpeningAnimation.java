package io.github.naimjeg.obeliskdepths.block.entity;

/** Client-visible transient state for the permanent Obelisk Chest opening transition. */
public final class ObeliskChestOpeningAnimation {
    private boolean opened;
    private int previousTicks;
    private int ticks;

    public ObeliskChestOpeningAnimation(boolean openedFromLoadedState) {
        this.opened = openedFromLoadedState;
        if (openedFromLoadedState) {
            this.previousTicks = ObeliskChestBlockEntity.OPEN_DURATION_TICKS;
            this.ticks = ObeliskChestBlockEntity.OPEN_DURATION_TICKS;
        }
    }

    public void clientTick(boolean openedState) {
        this.previousTicks = this.ticks;
        if (openedState) {
            this.opened = true;
        }
        if (this.opened && this.ticks < ObeliskChestBlockEntity.OPEN_DURATION_TICKS) {
            this.ticks++;
        }
    }

    public boolean opened() {
        return this.opened;
    }

    public int previousTicks() {
        return this.previousTicks;
    }

    public int ticks() {
        return this.ticks;
    }
}
