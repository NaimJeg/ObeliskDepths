package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import java.util.Optional;

public record DungeonPreparationSubmission(
        boolean accepted,
        Optional<DungeonPreparationJobId> jobId,
        Optional<DungeonPreparationSubmissionRejectionReason> rejectionReason,
        Optional<DungeonPreparationJobId> conflictingJobId,
        String detail
) {
    public DungeonPreparationSubmission {
        jobId = jobId == null ? Optional.empty() : jobId;
        rejectionReason = rejectionReason == null
                ? Optional.empty() : rejectionReason;
        conflictingJobId = conflictingJobId == null
                ? Optional.empty() : conflictingJobId;
        detail = detail == null ? "" : detail;

        if (accepted && jobId.isEmpty()) {
            throw new IllegalArgumentException(
                    "Accepted preparation submission requires a job id."
            );
        }
        if (accepted && rejectionReason.isPresent()) {
            throw new IllegalArgumentException(
                    "Accepted preparation submission must not have a rejection reason."
            );
        }
        if (!accepted && rejectionReason.isEmpty()) {
            throw new IllegalArgumentException(
                    "Rejected preparation submission requires a rejection reason."
            );
        }
    }

    static DungeonPreparationSubmission accepted(DungeonPreparationJobId jobId) {
        return new DungeonPreparationSubmission(
                true,
                Optional.of(jobId),
                Optional.empty(),
                Optional.empty(),
                ""
        );
    }

    public static DungeonPreparationSubmission rejected(
            DungeonPreparationSubmissionRejectionReason reason,
            DungeonPreparationJobId conflictingJobId,
            String detail
    ) {
        return new DungeonPreparationSubmission(
                false,
                Optional.empty(),
                Optional.of(reason),
                Optional.ofNullable(conflictingJobId),
                detail
        );
    }
}
