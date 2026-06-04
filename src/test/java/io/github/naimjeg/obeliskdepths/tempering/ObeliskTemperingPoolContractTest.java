package io.github.naimjeg.obeliskdepths.tempering;

import io.github.naimjeg.obeliskdepths.equipment.ObeliskEquipmentIds;
import io.github.naimjeg.obeliskdepths.equipment.ObeliskEquipmentSlot;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class ObeliskTemperingPoolContractTest {
    @BeforeEach
    void setUp() {
        ObeliskTemperingDirectionRegistry.bootstrapBuiltIns();
        ObeliskTemperingPoolRegistry.clear();
        ObeliskTemperingBootstrap.registerBuiltInPools();
    }

    @AfterEach
    void restore() {
        ObeliskTemperingPoolRegistry.clear();
        ObeliskTemperingBootstrap.registerBuiltInPools();
    }

    @Test
    void poolsUseExactFinalWeights() {
        assertPool(BuiltinTemperingPools.BALANCE_TIER_1, Map.of(
                ObeliskEquipmentIds.TEMPERED, 10,
                ObeliskEquipmentIds.BRUTAL, 6,
                ObeliskEquipmentIds.RAZOR_EDGED, 5,
                ObeliskEquipmentIds.DEADLY, 4
        ));
        assertPool(BuiltinTemperingPools.EDGE_TIER_1, Map.of(
                ObeliskEquipmentIds.RAZOR_EDGED, 10,
                ObeliskEquipmentIds.TEMPERED, 8,
                ObeliskEquipmentIds.PIERCING, 4,
                ObeliskEquipmentIds.SUNDERING, 4,
                ObeliskEquipmentIds.EXECUTIONERS, 5
        ));
        assertPool(BuiltinTemperingPools.FLAME_TIER_1, Map.of(
                ObeliskEquipmentIds.FLAMING, 10,
                ObeliskEquipmentIds.FIRE_EDGE, 5,
                ObeliskEquipmentIds.FLAMEFORGED, 6,
                ObeliskEquipmentIds.SMOLDERING, 5
        ));
        assertPool(BuiltinTemperingPools.FROST_TIER_1, Map.of(
                ObeliskEquipmentIds.FROSTBOUND, 10,
                ObeliskEquipmentIds.FROSTFORGED, 6
        ));
        assertPool(BuiltinTemperingPools.STORM_TIER_1, Map.of(
                ObeliskEquipmentIds.STORMCHARGED, 9,
                ObeliskEquipmentIds.STORMFORGED, 5,
                ObeliskEquipmentIds.IMPACTING, 7
        ));
        assertPool(BuiltinTemperingPools.ARCANE_TIER_1, Map.of(
                ObeliskEquipmentIds.ARCANE, 10,
                ObeliskEquipmentIds.SPELLBLADE, 6
        ));
        assertPool(BuiltinTemperingPools.VENOM_TIER_1, Map.of(
                ObeliskEquipmentIds.VENOMOUS, 10,
                ObeliskEquipmentIds.TOXIC_EDGE, 6,
                ObeliskEquipmentIds.WITHERING, 4
        ));
        assertPool(BuiltinTemperingPools.PRECISION_TIER_1, Map.of(
                ObeliskEquipmentIds.DEADLY, 10,
                ObeliskEquipmentIds.CRITICAL_EDGE, 4,
                ObeliskEquipmentIds.AMBUSHERS, 6,
                ObeliskEquipmentIds.RAZOR_EDGED, 4
        ));
        assertPool(BuiltinTemperingPools.HUNT_TIER_1, Map.of(
                ObeliskEquipmentIds.GIANT_SLAYERS, 8,
                ObeliskEquipmentIds.EXECUTIONERS, 5,
                ObeliskEquipmentIds.AMBUSHERS, 5,
                ObeliskEquipmentIds.TEMPERED, 4
        ));
    }

    @Test
    void snapshotsAndCandidateOrderAreImmutableAndDeterministic() {
        Map<net.minecraft.resources.Identifier, List<ObeliskTemperingPoolRegistry.WeightedEntry>>
                first = ObeliskTemperingPoolRegistry.snapshot();
        Map<net.minecraft.resources.Identifier, List<ObeliskTemperingPoolRegistry.WeightedEntry>>
                second = ObeliskTemperingPoolRegistry.snapshot();

        assertEquals(first, second);
        assertThrows(UnsupportedOperationException.class, first::clear);
        assertThrows(
                UnsupportedOperationException.class,
                () -> first.get(BuiltinTemperingPools.FLAME_TIER_1).clear()
        );
        assertEquals(
                List.of(
                        ObeliskEquipmentIds.FLAMING,
                        ObeliskEquipmentIds.FIRE_EDGE,
                        ObeliskEquipmentIds.FLAMEFORGED,
                        ObeliskEquipmentIds.SMOLDERING
                ),
                first.get(BuiltinTemperingPools.FLAME_TIER_1).stream()
                        .map(ObeliskTemperingPoolRegistry.WeightedEntry::templateId)
                        .toList()
        );
    }

    @Test
    void illegalWeightsFailInsteadOfClamping() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ObeliskTemperingPoolRegistry.WeightedEntry(
                        ObeliskEquipmentIds.TEMPERED,
                        0
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new ObeliskTemperingPoolDefinition.Entry(
                        ObeliskEquipmentIds.TEMPERED,
                        -1
                )
        );
    }

    @Test
    void fixedRandomSourceProducesTheSameWeightedSelection() {
        List<ObeliskTemperingPoolRegistry.WeightedEntry> pool =
                ObeliskTemperingPoolRegistry.entries(
                        BuiltinTemperingPools.BALANCE_TIER_1);

        var first = ObeliskTemperingRoller.chooseForSlot(
                pool,
                ObeliskEquipmentSlot.WEAPON,
                new HashSet<>(),
                new HashSet<>(),
                RandomSource.create(88776655L)
        );
        var second = ObeliskTemperingRoller.chooseForSlot(
                pool,
                ObeliskEquipmentSlot.WEAPON,
                new HashSet<>(),
                new HashSet<>(),
                RandomSource.create(88776655L)
        );

        assertNotNull(first);
        assertEquals(first.templateId(), second.templateId());
    }

    private static void assertPool(
            net.minecraft.resources.Identifier pool,
            Map<net.minecraft.resources.Identifier, Integer> expected
    ) {
        Map<net.minecraft.resources.Identifier, Integer> actual = new LinkedHashMap<>();
        for (ObeliskTemperingPoolRegistry.WeightedEntry entry
                : ObeliskTemperingPoolRegistry.entries(pool)) {
            actual.put(entry.templateId(), entry.weight());
        }
        assertEquals(expected, actual);
    }
}
