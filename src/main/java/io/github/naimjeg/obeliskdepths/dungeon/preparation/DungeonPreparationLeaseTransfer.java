package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import java.util.Objects;

final class DungeonPreparationLeaseTransfer implements AutoCloseable {
    private DungeonPreparationLeaseBundle bundle;

    DungeonPreparationLeaseTransfer(DungeonPreparationLeaseBundle bundle) {
        this.bundle = Objects.requireNonNull(bundle, "bundle");
    }

    DungeonPreparationLeaseBundle takeBundle() {
        if (this.bundle == null) {
            throw new IllegalStateException("Lease transfer has already been consumed.");
        }
        DungeonPreparationLeaseBundle result = this.bundle;
        this.bundle = null;
        return result;
    }

    @Override
    public void close() {
        DungeonPreparationLeaseBundle owned = this.bundle;
        if (owned == null) {
            return;
        }
        this.bundle = null;
        owned.close();
    }
}
