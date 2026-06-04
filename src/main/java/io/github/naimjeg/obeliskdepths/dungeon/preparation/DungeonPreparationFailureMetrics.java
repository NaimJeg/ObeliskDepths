package io.github.naimjeg.obeliskdepths.dungeon.preparation;

interface DungeonPreparationFailureMetrics {
    DungeonPreparationFailureMetrics NO_OP = new DungeonPreparationFailureMetrics() {
        @Override
        public void recordClaimReleaseInvariantFailure() {
        }

        @Override
        public void recordCommittedPublicationFailure() {
        }
    };

    void recordClaimReleaseInvariantFailure();

    void recordCommittedPublicationFailure();
}
