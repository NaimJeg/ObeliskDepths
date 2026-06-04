package io.github.naimjeg.obeliskdepths.dungeon.portal;

public enum PortalSessionRemovalReason {
    EXPIRED,
    INSTANCE_MISSING,
    INSTANCE_INACTIVE,
    SOURCE_PORTAL_INVALID,
    SOURCE_OBELISK_INVALID,
    SOURCE_OBELISK_CLOSED,
    ACTIVATION_ROLLBACK,
    DEBUG_CLEANUP,
    INACTIVE_INSTANCE_CLEANUP
}
