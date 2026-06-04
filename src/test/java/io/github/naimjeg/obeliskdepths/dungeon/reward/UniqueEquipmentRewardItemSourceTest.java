package io.github.naimjeg.obeliskdepths.dungeon.reward;

import io.github.naimjeg.obeliskdepths.equipment.ObeliskUniqueEquipmentCatalog;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class UniqueEquipmentRewardItemSourceTest {
    @Test
    void fixedTierAndRandomInputSelectTheSameUniqueIdentity() {
        UniqueEquipmentRewardItemSource source =
                new UniqueEquipmentRewardItemSource((context, random) -> 4);

        var first = source.select(4, new Random(99887766L)).orElseThrow();
        var second = source.select(4, new Random(99887766L)).orElseThrow();

        assertEquals(first.templateId(), second.templateId());
        assertTrue(ObeliskUniqueEquipmentCatalog.find(first.templateId()).isPresent());
    }

    @Test
    void rewardTierGateExcludesAllUniquesBelowTierFour() {
        UniqueEquipmentRewardItemSource source =
                new UniqueEquipmentRewardItemSource((context, random) -> 3);

        assertTrue(source.select(1, new Random(1L)).isEmpty());
        assertTrue(source.select(2, new Random(1L)).isEmpty());
        assertTrue(source.select(3, new Random(1L)).isEmpty());
        assertEquals(6, ObeliskUniqueEquipmentCatalog.availableAtTier(4).size());
    }

    @Test
    void scalablePoolUsesTheLowWeightUniqueSource() throws IOException {
        String factory = Files.readString(Path.of(
                "src/main/java/io/github/naimjeg/obeliskdepths/dungeon/reward/"
                        + "ScalableRewardPoolFactory.java"
        ));
        assertTrue(factory.contains("UniqueEquipmentRewardItemSource"));
        assertTrue(factory.contains(
                "ObeliskUniqueEquipmentCatalog.REWARD_POOL_WEIGHT"));
        assertEquals(3, ObeliskUniqueEquipmentCatalog.REWARD_POOL_WEIGHT);
    }
}
