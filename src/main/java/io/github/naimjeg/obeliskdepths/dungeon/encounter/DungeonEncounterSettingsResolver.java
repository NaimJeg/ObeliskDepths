package io.github.naimjeg.obeliskdepths.dungeon.encounter;

import io.github.naimjeg.obeliskdepths.dungeon.instance.DungeonDifficulty;

/*
 * Centralizes the authored encounter tuning hook while the active runtime
 * still owns population, kill credit, and boss transitions in
 * DungeonEncounterDirector.
 */
public final class DungeonEncounterSettingsResolver {
    private DungeonEncounterSettingsResolver() {
    }

    public static DungeonEncounterSettings resolve(DungeonDifficulty difficulty) {
        return new DungeonEncounterSettings(
                fixedNormalKillQuota(difficulty),
                desiredLivingMobCount(difficulty)
        );
    }

    static int fixedNormalKillQuota(DungeonDifficulty difficulty) {
        int tierBonus = Math.max(0, difficulty.tier()) * 2;
        int amountBonus = Math.max(0, Math.round(difficulty.amountIntensity() * 3.0F));
        return 12 + tierBonus + amountBonus;
    }

    static int desiredLivingMobCount(DungeonDifficulty difficulty) {
        int tierBonus = Math.min(3, Math.max(0, difficulty.tier() / 2));
        int amountBonus = Math.min(
                2,
                Math.max(0, Math.round(difficulty.amountIntensity()))
        );
        return Math.max(1, 3 + tierBonus + amountBonus);
    }
}
