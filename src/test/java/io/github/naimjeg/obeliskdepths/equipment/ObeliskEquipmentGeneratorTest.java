package io.github.naimjeg.obeliskdepths.equipment;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

final class ObeliskEquipmentGeneratorTest {
    private static final UUID REWARD_ID =
            UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID INSTANCE_ID =
            UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Test
    void fixedInputsProduceTheSameRoll() {
        ObeliskEquipmentRoll first = ObeliskEquipmentGenerator.roll(
                ObeliskEquipmentSlot.WEAPON,
                123456789L,
                REWARD_ID,
                INSTANCE_ID,
                3
        ).orElseThrow();
        ObeliskEquipmentRoll second = ObeliskEquipmentGenerator.roll(
                ObeliskEquipmentSlot.WEAPON,
                123456789L,
                REWARD_ID,
                INSTANCE_ID,
                3
        ).orElseThrow();

        assertEquals(first, second);
        assertTrue(ObeliskEquipmentTemplateCatalog.find(first.templateId()).isPresent());
    }

    @Test
    void rewardAndInstanceIdentityAndOrdinalParticipateInTheSeed() {
        long base = ObeliskEquipmentGenerator.rewardEquipmentSeed(
                7L, REWARD_ID, INSTANCE_ID, 0
        );
        long nextOrdinal = ObeliskEquipmentGenerator.rewardEquipmentSeed(
                7L, REWARD_ID, INSTANCE_ID, 1
        );
        long nextReward = ObeliskEquipmentGenerator.rewardEquipmentSeed(
                7L,
                UUID.fromString("11111111-2222-3333-4444-555555555556"),
                INSTANCE_ID,
                0
        );
        long nextInstance = ObeliskEquipmentGenerator.rewardEquipmentSeed(
                7L,
                REWARD_ID,
                UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeef"),
                0
        );

        assertNotEquals(base, nextOrdinal);
        assertNotEquals(base, nextReward);
        assertNotEquals(base, nextInstance);
    }

    @Test
    void everySupportedSlotHasARewardCandidate() {
        assertEquals(
                24,
                ObeliskEquipmentTemplateCatalog.all().stream()
                        .filter(ObeliskEquipmentContent::temperingEligible)
                        .count()
        );
        for (ObeliskEquipmentSlot slot : ObeliskEquipmentSlot.values()) {
            assertTrue(ObeliskEquipmentGenerator.roll(
                    slot, 99L, REWARD_ID, INSTANCE_ID, 0
            ).isPresent());
        }
    }
}
