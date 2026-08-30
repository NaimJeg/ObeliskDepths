package io.github.naimjeg.obeliskdepths.item;

import net.minecraft.util.Mth;

public final class ReturnScrollUseMath {
    public static final float RUNE_PULSE_SPEED = 0.35F;

    private ReturnScrollUseMath() {
    }

    public static float smoothStep(float value) {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    public static float activationProgress(int useDurationTicks, int remainingUseTicks, float partialTick) {
        if (useDurationTicks <= 0) {
            return 0.0F;
        }

        float used = useDurationTicks - (remainingUseTicks - partialTick + 1.0F);
        return Mth.clamp(used / useDurationTicks, 0.0F, 1.0F);
    }

    public static float raiseProgress(float progress) {
        return smoothStep(remap(progress, 0.0F, 0.20F));
    }

    public static float attunementProgress(float progress) {
        return smoothStep(remap(progress, 0.15F, 0.82F));
    }

    public static float finalShakeProgress(float progress) {
        return smoothStep(remap(progress, 0.82F, 1.0F));
    }

    public static float runePulse(float ageInTicks) {
        float wave = 0.5F + 0.5F * Mth.sin(ageInTicks * RUNE_PULSE_SPEED);
        return smoothStep(wave);
    }

    private static float remap(float value, float min, float max) {
        if (max <= min) {
            return 0.0F;
        }

        return Mth.clamp((value - min) / (max - min), 0.0F, 1.0F);
    }
}
