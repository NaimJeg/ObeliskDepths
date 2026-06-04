package io.github.naimjeg.obeliskdepths.dungeon.preparation;

@FunctionalInterface
interface DungeonActivationFailureInjector {
    DungeonActivationFailureInjector NONE = point -> { };

    void at(FailurePoint point);

    enum FailurePoint {
        BEFORE_RESERVATION,
        AFTER_RESERVATION,
        AFTER_PORTAL_SESSION,
        AFTER_DUNGEON_SESSION,
        AFTER_PORTAL_ENTITY,
        AFTER_LEASE_DETACHMENT,
        AFTER_PREPARED_ENTRY,
        AFTER_START_LEASE_RELEASE,
        BEFORE_CLAIM_RELEASE,
        AFTER_CLAIM_RELEASE,
        BEFORE_TRIBUTE_CONSUMPTION
    }
}
