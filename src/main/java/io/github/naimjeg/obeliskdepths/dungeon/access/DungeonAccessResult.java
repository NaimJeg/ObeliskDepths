package io.github.naimjeg.obeliskdepths.dungeon.access;

public enum DungeonAccessResult {
    ALLOW,
    DENY_PORTAL_EXPIRED,
    DENY_SOLO_NOT_OPENER,
    DENY_MAX_PARTICIPANTS,
    DENY_INSTANCE_CLOSED
}