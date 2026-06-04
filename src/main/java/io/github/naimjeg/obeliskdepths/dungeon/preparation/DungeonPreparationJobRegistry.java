package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class DungeonPreparationJobRegistry {
    static final long TERMINAL_RETENTION_TICKS = 100L;

    private final Map<DungeonPreparationJobId, DungeonPreparationJob> jobsById = new LinkedHashMap<>();
    private final Map<UUID, DungeonPreparationJob> activeJobsByPlayer = new LinkedHashMap<>();
    private final Map<SourceObeliskKey, DungeonPreparationJob> activeJobsByObelisk = new LinkedHashMap<>();

    SubmissionResult submit(DungeonPreparationJob job) {
        if (job.isTerminal()) {
            throw new IllegalArgumentException(
                    "Cannot submit terminal job: " + job.id()
            );
        }
        if (jobsById.containsKey(job.id())) {
            return SubmissionResult.duplicateJobId(job.id());
        }

        UUID playerId = job.request().playerId();
        Optional<DungeonPreparationJob> existingByPlayer = findActiveByPlayer(playerId);
        if (existingByPlayer.isPresent()) {
            return SubmissionResult.duplicatePlayer(existingByPlayer.get().id());
        }

        SourceObeliskKey obeliskKey = new SourceObeliskKey(
                job.request().sourceDimension(),
                job.request().obeliskPos()
        );
        Optional<DungeonPreparationJob> existingByObelisk =
                findActiveByObelisk(job.request().sourceDimension(), job.request().obeliskPos());
        if (existingByObelisk.isPresent()) {
            return SubmissionResult.duplicateObelisk(existingByObelisk.get().id());
        }

        jobsById.put(job.id(), job);
        activeJobsByPlayer.put(playerId, job);
        activeJobsByObelisk.put(obeliskKey, job);
        return SubmissionResult.accepted();
    }

    Optional<DungeonPreparationJob> findActiveByPlayer(UUID playerId) {
        DungeonPreparationJob job = activeJobsByPlayer.get(playerId);
        if (job == null) {
            return Optional.empty();
        }
        if (job.isTerminal()) {
            activeJobsByPlayer.remove(playerId, job);
            removeFromActiveIndexes(job);
            return Optional.empty();
        }
        return Optional.of(job);
    }

    Optional<DungeonPreparationJob> findActiveByObelisk(
            ResourceKey<Level> sourceDimension,
            BlockPos obeliskPos
    ) {
        SourceObeliskKey key = new SourceObeliskKey(sourceDimension, obeliskPos);
        DungeonPreparationJob job = activeJobsByObelisk.get(key);
        if (job == null) {
            return Optional.empty();
        }
        if (job.isTerminal()) {
            activeJobsByObelisk.remove(key, job);
            removeFromActiveIndexes(job);
            return Optional.empty();
        }
        return Optional.of(job);
    }

    Optional<DungeonPreparationJob> findById(DungeonPreparationJobId id) {
        return Optional.ofNullable(jobsById.get(id));
    }

    Optional<DungeonPreparationJob> findActiveById(DungeonPreparationJobId id) {
        DungeonPreparationJob job = jobsById.get(id);
        if (job == null || job.isTerminal()) {
            return Optional.empty();
        }
        return Optional.of(job);
    }

    void advance(
            DungeonPreparationJobId id,
            DungeonPreparationStage stage,
            long gameTime
    ) {
        DungeonPreparationJob job = requireActive(id);
        job.advanceTo(stage, gameTime);
    }

    void fail(
            DungeonPreparationJobId id,
            DungeonPreparationJobFailureReason reason,
            String detail,
            long gameTime
    ) {
        DungeonPreparationJob job = requireActive(id);
        RuntimeException cleanupFailure = null;
        try {
            job.fail(reason, detail, gameTime);
        } catch (RuntimeException exception) {
            cleanupFailure = exception;
        } finally {
            if (job.isTerminal()) {
                removeFromActiveIndexes(job);
            }
        }
        if (cleanupFailure != null) {
            throw cleanupFailure;
        }
    }

    void cancel(
            DungeonPreparationJobId id,
            DungeonPreparationCancellationReason reason,
            String detail,
            long gameTime
    ) {
        DungeonPreparationJob job = jobsById.get(id);
        if (job == null || job.isTerminal()) {
            return;
        }
        RuntimeException cleanupFailure = null;
        try {
            job.cancel(reason, detail, gameTime);
        } catch (RuntimeException exception) {
            cleanupFailure = exception;
        } finally {
            if (job.isTerminal()) {
                removeFromActiveIndexes(job);
            }
        }
        if (cleanupFailure != null) {
            throw cleanupFailure;
        }
    }

    void complete(
            DungeonPreparationJobId id,
            long gameTime
    ) {
        DungeonPreparationJob job = requireActive(id);
        RuntimeException completionFailure = null;
        try {
            job.complete(gameTime);
        } catch (RuntimeException exception) {
            completionFailure = exception;
        } finally {
            if (job.isTerminal()) {
                removeFromActiveIndexes(job);
            }
        }
        if (completionFailure != null) {
            throw completionFailure;
        }
    }

    void purgeTerminal(long currentGameTime, long retentionTicks) {
        List<DungeonPreparationJobId> toRemove = new ArrayList<>();
        for (Map.Entry<DungeonPreparationJobId, DungeonPreparationJob> entry : jobsById.entrySet()) {
            DungeonPreparationJob job = entry.getValue();
            if (job.isTerminal()
                    && currentGameTime - job.lastTransitionGameTime() >= retentionTicks) {
                removeFromActiveIndexes(job);
                toRemove.add(entry.getKey());
            }
        }
        for (DungeonPreparationJobId id : toRemove) {
            jobsById.remove(id);
        }
    }

    void clearAllActive(
            DungeonPreparationCancellationReason reason,
            String detail,
            long gameTime
    ) {
        Set<DungeonPreparationJob> active =
                new LinkedHashSet<>(activeJobsByPlayer.values());
        RuntimeException aggregateFailure = null;
        for (DungeonPreparationJob job : active) {
            if (!job.isTerminal()) {
                try {
                    job.cancel(reason, detail, gameTime);
                } catch (RuntimeException exception) {
                    if (aggregateFailure == null) {
                        aggregateFailure = new IllegalStateException(
                                "Failed to cancel one or more active preparation jobs"
                        );
                    }
                    aggregateFailure.addSuppressed(exception);
                } finally {
                    if (job.isTerminal()) {
                        removeFromActiveIndexes(job);
                    }
                }
            }
        }
        activeJobsByPlayer.clear();
        activeJobsByObelisk.clear();
        if (aggregateFailure != null) {
            throw aggregateFailure;
        }
    }

    List<DungeonPreparationJobSnapshot> snapshots() {
        List<DungeonPreparationJobSnapshot> result = new ArrayList<>();
        for (DungeonPreparationJob job : jobsById.values()) {
            result.add(job.snapshot());
        }
        return Collections.unmodifiableList(result);
    }

    int activeCount() {
        int count = 0;
        for (DungeonPreparationJob job : List.copyOf(activeJobsByPlayer.values())) {
            if (job.isTerminal()) {
                removeFromActiveIndexes(job);
            } else {
                count++;
            }
        }
        return count;
    }

    void removeFromActiveIndexes(DungeonPreparationJob job) {
        activeJobsByPlayer.remove(job.request().playerId(), job);
        activeJobsByObelisk.remove(sourceObeliskKey(job), job);
    }

    private DungeonPreparationJob requireActive(DungeonPreparationJobId id) {
        DungeonPreparationJob job = jobsById.get(id);
        if (job == null || job.isTerminal()) {
            throw new IllegalStateException("No active preparation job for id " + id);
        }
        return job;
    }

    private static SourceObeliskKey sourceObeliskKey(DungeonPreparationJob job) {
        return new SourceObeliskKey(
                job.request().sourceDimension(),
                job.request().obeliskPos()
        );
    }

    static final class SubmissionResult {
        private final boolean accepted;
        private final SubmissionRejectionReason rejectionReason;
        private final DungeonPreparationJobId conflictingJobId;

        private SubmissionResult(
                boolean accepted,
                SubmissionRejectionReason rejectionReason,
                DungeonPreparationJobId conflictingJobId
        ) {
            this.accepted = accepted;
            this.rejectionReason = rejectionReason;
            this.conflictingJobId = conflictingJobId;
        }

        static SubmissionResult accepted() {
            return new SubmissionResult(true, null, null);
        }

        static SubmissionResult duplicatePlayer(DungeonPreparationJobId conflictingId) {
            return new SubmissionResult(
                    false,
                    SubmissionRejectionReason.DUPLICATE_PLAYER,
                    conflictingId
            );
        }

        static SubmissionResult duplicateObelisk(DungeonPreparationJobId conflictingId) {
            return new SubmissionResult(
                    false,
                    SubmissionRejectionReason.DUPLICATE_OBELISK,
                    conflictingId
            );
        }

        static SubmissionResult duplicateJobId(DungeonPreparationJobId conflictingId) {
            return new SubmissionResult(
                    false,
                    SubmissionRejectionReason.DUPLICATE_JOB_ID,
                    conflictingId
            );
        }

        boolean isAccepted() {
            return this.accepted;
        }

        SubmissionRejectionReason rejectionReason() {
            return this.rejectionReason;
        }

        DungeonPreparationJobId conflictingJobId() {
            return this.conflictingJobId;
        }
    }

    enum SubmissionRejectionReason {
        DUPLICATE_JOB_ID,
        DUPLICATE_PLAYER,
        DUPLICATE_OBELISK
    }

    static final class SourceObeliskKey {
        private final ResourceKey<Level> sourceDimension;
        private final BlockPos obeliskPos;

        SourceObeliskKey(ResourceKey<Level> sourceDimension, BlockPos obeliskPos) {
            this.sourceDimension = sourceDimension;
            this.obeliskPos = obeliskPos.immutable();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SourceObeliskKey that)) return false;
            return this.sourceDimension.equals(that.sourceDimension)
                    && this.obeliskPos.equals(that.obeliskPos);
        }

        @Override
        public int hashCode() {
            return 31 * this.sourceDimension.hashCode() + this.obeliskPos.hashCode();
        }

        @Override
        public String toString() {
            return this.sourceDimension.identifier().toString()
                    + "/" + this.obeliskPos.toShortString();
        }
    }
}
