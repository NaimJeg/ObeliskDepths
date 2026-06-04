package io.github.naimjeg.obeliskdepths.dungeon.interaction;

import java.util.Optional;

public enum DungeonPortalEntryResult {
    OPERATION_STARTED(0, true,
            "message.obeliskdepths.portal.entry.operation_started"),
    OPERATION_ALREADY_ACTIVE(1, true,
            "message.obeliskdepths.portal.entry.operation_active"),
    SUCCESS(2, true, "message.obeliskdepths.portal.entry.success"),
    SESSION_MISSING(3, false,
            "message.obeliskdepths.portal.entry.session_missing"),
    SESSION_EXPIRED(4, false,
            "message.obeliskdepths.portal.entry.session_expired"),
    INSTANCE_MISSING(5, false,
            "message.obeliskdepths.portal.entry.instance_missing"),
    ACCESS_DENIED(6, false,
            "message.obeliskdepths.portal.entry.access_denied"),
    PLAYER_ALREADY_BOUND_ELSEWHERE(7, false,
            "message.obeliskdepths.portal.entry.bound_elsewhere"),
    DESTINATION_NOT_PREPARED(8, false,
            "message.obeliskdepths.portal.entry.destination_not_prepared"),
    DESTINATION_STABILIZING(9, false,
            "message.obeliskdepths.portal.entry.destination_stabilizing"),
    DESTINATION_UNAVAILABLE(10, false,
            "message.obeliskdepths.portal.entry.destination_unavailable"),
    REGISTRATION_FAILED(11, false,
            "message.obeliskdepths.portal.entry.registration_failed"),
    TELEPORT_FAILED(12, false,
            "message.obeliskdepths.portal.entry.teleport_failed"),
    WRONG_SOURCE_DIMENSION(13, false,
            "message.obeliskdepths.portal.entry.wrong_source_dimension"),
    PORTAL_INVALID(14, false,
            "message.obeliskdepths.portal.entry.portal_invalid"),
    CLIENT_NOT_READY(15, false,
            "message.obeliskdepths.portal.entry.client_not_ready"),
    PREPARATION_FAILED(16, false,
            "message.obeliskdepths.portal.entry.preparation_failed"),
    PLAYER_UNAVAILABLE(17, false,
            "message.obeliskdepths.portal.entry.player_unavailable");

    private final int wireCode;
    private final boolean accepted;
    private final String translationKey;

    DungeonPortalEntryResult(
            int wireCode,
            boolean accepted,
            String translationKey
    ) {
        this.wireCode = wireCode;
        this.accepted = accepted;
        this.translationKey = translationKey;
    }

    public int wireCode() {
        return this.wireCode;
    }

    public boolean accepted() {
        return this.accepted;
    }

    public String translationKey() {
        return this.translationKey;
    }

    public static Optional<DungeonPortalEntryResult> fromWireCode(int wireCode) {
        for (DungeonPortalEntryResult result : values()) {
            if (result.wireCode == wireCode) {
                return Optional.of(result);
            }
        }
        return Optional.empty();
    }
}
