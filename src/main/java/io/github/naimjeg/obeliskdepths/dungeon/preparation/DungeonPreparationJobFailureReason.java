package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import java.util.Optional;

public enum DungeonPreparationJobFailureReason {
    INVALID_TRIBUTE(1),
    NO_SITE_AVAILABLE(2),
    NON_AUTHORITATIVE_SITE(3),
    SITE_CONFLICT(4),
    CHUNK_LOAD_FAILED(5),
    STRUCTURE_START_MISSING(6),
    STRUCTURE_START_INVALID(7),
    ENTRY_VALIDATION_FAILED(8),
    COMMIT_VALIDATION_FAILED(9),
    PORTAL_SPAWN_FAILED(10),
    PREPARED_ENTRY_REGISTRATION_FAILED(11),
    SITE_CLAIM_LOST(12),
    INTERNAL_ERROR(13),
    // Wire code 14 is intentionally unused alongside the reserved stage code.
    AUTHORITATIVE_RUNTIME_UNAVAILABLE(15),
    AUTHORITATIVE_JOB_MISSING(16),
    SUBMISSION_REJECTED(17);

    private final int wireCode;

    DungeonPreparationJobFailureReason(int wireCode) {
        this.wireCode = wireCode;
    }

    public int wireCode() {
        return this.wireCode;
    }

    public static Optional<DungeonPreparationJobFailureReason> fromWireCode(
            int wireCode
    ) {
        for (DungeonPreparationJobFailureReason reason : values()) {
            if (reason.wireCode == wireCode) {
                return Optional.of(reason);
            }
        }
        return Optional.empty();
    }
}
