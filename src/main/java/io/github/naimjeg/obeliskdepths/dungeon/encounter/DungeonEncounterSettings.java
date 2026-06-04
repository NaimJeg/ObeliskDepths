package io.github.naimjeg.obeliskdepths.dungeon.encounter;

public record DungeonEncounterSettings(
        int normalKillQuota,
        int desiredLivingMobCount
) {
    public DungeonEncounterSettings {
        normalKillQuota = Math.max(0, normalKillQuota);
        desiredLivingMobCount = Math.max(0, desiredLivingMobCount);
    }
}
