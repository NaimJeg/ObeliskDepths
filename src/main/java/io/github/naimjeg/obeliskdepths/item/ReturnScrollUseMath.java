package io.github.naimjeg.obeliskdepths.item;

import net.minecraft.util.Mth;

public final class ReturnScrollUseMath {
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

    public static float unfoldProgress(float progress) {
        return smoothStep(remap(progress, 0.20F, 0.50F));
    }

    public static float attunementProgress(float progress) {
        return smoothStep(remap(progress, 0.50F, 0.85F));
    }

    public static float finalShakeProgress(float progress) {
        return smoothStep(remap(progress, 0.85F, 1.0F));
    }

    private static float remap(float value, float min, float max) {
        if (max <= min) {
            return 0.0F;
        }

        return Mth.clamp((value - min) / (max - min), 0.0F, 1.0F);
    }
}
