package io.github.naimjeg.obeliskdepths.dungeon.interaction;

public enum DungeonPortalEntryResult {
    SUCCESS("message.obeliskdepths.portal.entry.success"),
    SESSION_MISSING("message.obeliskdepths.portal.entry.session_missing"),
    SESSION_EXPIRED("message.obeliskdepths.portal.entry.session_expired"),
    INSTANCE_MISSING("message.obeliskdepths.portal.entry.instance_missing"),
    ACCESS_DENIED("message.obeliskdepths.portal.entry.access_denied"),
    PLAYER_ALREADY_BOUND_ELSEWHERE("message.obeliskdepths.portal.entry.bound_elsewhere"),
    DESTINATION_UNAVAILABLE("message.obeliskdepths.portal.entry.destination_unavailable"),
    REGISTRATION_FAILED("message.obeliskdepths.portal.entry.registration_failed"),
    TELEPORT_FAILED("message.obeliskdepths.portal.entry.teleport_failed"),
    WRONG_SOURCE_DIMENSION("message.obeliskdepths.portal.entry.wrong_source_dimension");

    private final String translationKey;

    DungeonPortalEntryResult(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return this.translationKey;
    }
}
