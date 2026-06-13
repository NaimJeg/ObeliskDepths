package io.github.naimjeg.obeliskdepths.dungeon.session;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record DungeonKillProgress(
        int requiredKillScore,
        int currentKillScore,
        float completionThreshold
) {
    public static final float DEFAULT_COMPLETION_THRESHOLD = 0.95F;

    public static final Codec<DungeonKillProgress> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.INT.optionalFieldOf("required_kill_score", 0)
                            .forGetter(DungeonKillProgress::requiredKillScore),
                    Codec.INT.optionalFieldOf("current_kill_score", 0)
                            .forGetter(DungeonKillProgress::currentKillScore),
                    Codec.FLOAT.optionalFieldOf(
                                    "completion_threshold",
                                    DEFAULT_COMPLETION_THRESHOLD
                            )
                            .forGetter(DungeonKillProgress::completionThreshold)
            ).apply(instance, DungeonKillProgress::new));

    public DungeonKillProgress {
        requiredKillScore = Math.max(0, requiredKillScore);
        currentKillScore = Math.max(0, currentKillScore);

        if (!Float.isFinite(completionThreshold)) {
            completionThreshold = DEFAULT_COMPLETION_THRESHOLD;
        }

        completionThreshold = Math.max(0.0F, Math.min(1.0F, completionThreshold));
    }

    public static DungeonKillProgress empty() {
        return new DungeonKillProgress(
                0,
                0,
                DEFAULT_COMPLETION_THRESHOLD
        );
    }

    public boolean isComplete() {
        int target = this.targetKillScore();

        if (target <= 0) {
            return false;
        }

        return this.currentKillScore >= target;
    }

    /*
     * Progress intentionally uses a threshold below 100% so players are not
     * forced to hunt down one remaining hidden, stuck, or pathfinding-broken mob.
     */
    public int targetKillScore() {
        if (this.requiredKillScore <= 0) {
            return 0;
        }

        return (int) Math.ceil(this.requiredKillScore * this.completionThreshold);
    }

    public int clampedCurrentKillScore() {
        int target = this.targetKillScore();

        if (target <= 0) {
            return 0;
        }

        return Math.min(this.currentKillScore, target);
    }

    public float remainingProgress() {
        int target = this.targetKillScore();

        if (target <= 0) {
            return 0.0F;
        }

        float remaining = (target - this.clampedCurrentKillScore()) / (float) target;
        return Math.max(0.0F, Math.min(1.0F, remaining));
    }

    public DungeonKillProgress withAdditionalRequiredKillScore(int score) {
        return new DungeonKillProgress(
                this.requiredKillScore + Math.max(0, score),
                this.currentKillScore,
                this.completionThreshold
        );
    }

    public DungeonKillProgress withAddedKillScore(int score) {
        return new DungeonKillProgress(
                this.requiredKillScore,
                this.currentKillScore + Math.max(0, score),
                this.completionThreshold
        );
    }
}
