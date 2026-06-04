package io.github.naimjeg.obeliskdepths.tempering;

import io.github.naimjeg.damagenexus.api.rule.entry.DamageEntryDefinition;
import io.github.naimjeg.damagenexus.api.rule.entry.DamageEntrySlot;
import io.github.naimjeg.damagenexus.api.rule.entry.DamageEntryStacking;
import io.github.naimjeg.damagenexus.api.rule.entry.DamageEntryValidator;
import io.github.naimjeg.obeliskdepths.equipment.ObeliskEquipmentIds;
import io.github.naimjeg.obeliskdepths.equipment.ObeliskEquipmentTemplateCatalog;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BuiltinTemperingCatalogTest {
    private static final List<Identifier> EXPECTED_DIRECTIONS = List.of(
            ObeliskTemperingDirectionRegistry.BALANCE,
            ObeliskTemperingDirectionRegistry.EDGE,
            ObeliskTemperingDirectionRegistry.FLAME,
            ObeliskTemperingDirectionRegistry.FROST,
            ObeliskTemperingDirectionRegistry.STORM,
            ObeliskTemperingDirectionRegistry.ARCANE,
            ObeliskTemperingDirectionRegistry.VENOM,
            ObeliskTemperingDirectionRegistry.PRECISION,
            ObeliskTemperingDirectionRegistry.HUNT
    );

    private static final List<Identifier> EXPECTED_POOLS = List.of(
            BuiltinTemperingPools.BALANCE_TIER_1,
            BuiltinTemperingPools.EDGE_TIER_1,
            BuiltinTemperingPools.FLAME_TIER_1,
            BuiltinTemperingPools.FROST_TIER_1,
            BuiltinTemperingPools.STORM_TIER_1,
            BuiltinTemperingPools.ARCANE_TIER_1,
            BuiltinTemperingPools.VENOM_TIER_1,
            BuiltinTemperingPools.PRECISION_TIER_1,
            BuiltinTemperingPools.HUNT_TIER_1
    );

    private static final List<Identifier> REQUIRED_ENTRIES = List.of(
            ObeliskEquipmentIds.TEMPERED,
            ObeliskEquipmentIds.BRUTAL,
            ObeliskEquipmentIds.RAZOR_EDGED,
            ObeliskEquipmentIds.PIERCING,
            ObeliskEquipmentIds.SUNDERING,
            ObeliskEquipmentIds.EXECUTIONERS,
            ObeliskEquipmentIds.FLAMING,
            ObeliskEquipmentIds.FLAMEFORGED,
            ObeliskEquipmentIds.SMOLDERING,
            ObeliskEquipmentIds.FROSTBOUND,
            ObeliskEquipmentIds.FROSTFORGED,
            ObeliskEquipmentIds.STORMCHARGED,
            ObeliskEquipmentIds.STORMFORGED,
            ObeliskEquipmentIds.IMPACTING,
            ObeliskEquipmentIds.ARCANE,
            ObeliskEquipmentIds.SPELLBLADE,
            ObeliskEquipmentIds.VENOMOUS,
            ObeliskEquipmentIds.TOXIC_EDGE,
            ObeliskEquipmentIds.WITHERING,
            ObeliskEquipmentIds.DEADLY,
            ObeliskEquipmentIds.CRITICAL_EDGE,
            ObeliskEquipmentIds.AMBUSHERS,
            ObeliskEquipmentIds.GIANT_SLAYERS,
            ObeliskEquipmentIds.FIRE_EDGE
    );

    private BuiltinTemperingCatalogTest() {
    }

    public static void main(String[] args) {
        ObeliskTemperingDirectionRegistry.bootstrapBuiltIns();
        ObeliskTemperingPoolRegistry.clear();
        ObeliskTemperingBootstrap.registerBuiltInPools();

        assertEquals(
                EXPECTED_DIRECTIONS,
                ObeliskTemperingDirectionRegistry.orderedDirectionIds(),
                "built-in direction order"
        );

        assertPoolsRegistered();
        assertEntriesValid();
        assertProductionAggregation();
        assertSharedEntryAggregation();

        ObeliskTemperingPoolRegistry.clear();
        ObeliskTemperingBootstrap.registerBuiltInPools();
    }

    private static void assertPoolsRegistered() {
        for (Identifier poolId : EXPECTED_POOLS) {
            List<ObeliskTemperingPoolRegistry.WeightedEntry> entries =
                    ObeliskTemperingPoolRegistry.entries(poolId);

            assertTrue(!entries.isEmpty(), "pool should not be empty: " + poolId);

            for (ObeliskTemperingPoolRegistry.WeightedEntry entry : entries) {
                assertTrue(
                        entry.weight() > 0,
                        "pool weight should be positive: " + poolId
                );
                assertTrue(
                        entry.templateId() != null && entry.templateId().getNamespace().equals("obeliskdepths"),
                        "pool entry should resolve: " + poolId
                );
            }
        }

        assertPoolWeights(BuiltinTemperingPools.BALANCE_TIER_1, Map.of(
                ObeliskEquipmentIds.TEMPERED, 10,
                ObeliskEquipmentIds.BRUTAL, 6,
                ObeliskEquipmentIds.RAZOR_EDGED, 5,
                ObeliskEquipmentIds.DEADLY, 4
        ));
        assertPoolWeights(BuiltinTemperingPools.EDGE_TIER_1, Map.of(
                ObeliskEquipmentIds.RAZOR_EDGED, 10,
                ObeliskEquipmentIds.TEMPERED, 8,
                ObeliskEquipmentIds.PIERCING, 4,
                ObeliskEquipmentIds.SUNDERING, 4,
                ObeliskEquipmentIds.EXECUTIONERS, 5
        ));
        assertPoolWeights(BuiltinTemperingPools.FLAME_TIER_1, Map.of(
                ObeliskEquipmentIds.FLAMING, 10,
                ObeliskEquipmentIds.FIRE_EDGE, 5,
                ObeliskEquipmentIds.FLAMEFORGED, 6,
                ObeliskEquipmentIds.SMOLDERING, 5
        ));
        assertPoolWeights(BuiltinTemperingPools.FROST_TIER_1, Map.of(
                ObeliskEquipmentIds.FROSTBOUND, 10,
                ObeliskEquipmentIds.FROSTFORGED, 6
        ));
        assertPoolWeights(BuiltinTemperingPools.STORM_TIER_1, Map.of(
                ObeliskEquipmentIds.STORMCHARGED, 9,
                ObeliskEquipmentIds.STORMFORGED, 5,
                ObeliskEquipmentIds.IMPACTING, 7
        ));
        assertPoolWeights(BuiltinTemperingPools.ARCANE_TIER_1, Map.of(
                ObeliskEquipmentIds.ARCANE, 10,
                ObeliskEquipmentIds.SPELLBLADE, 6
        ));
        assertPoolWeights(BuiltinTemperingPools.VENOM_TIER_1, Map.of(
                ObeliskEquipmentIds.VENOMOUS, 10,
                ObeliskEquipmentIds.TOXIC_EDGE, 6,
                ObeliskEquipmentIds.WITHERING, 4
        ));
        assertPoolWeights(BuiltinTemperingPools.PRECISION_TIER_1, Map.of(
                ObeliskEquipmentIds.DEADLY, 10,
                ObeliskEquipmentIds.CRITICAL_EDGE, 4,
                ObeliskEquipmentIds.AMBUSHERS, 6,
                ObeliskEquipmentIds.RAZOR_EDGED, 4
        ));
        assertPoolWeights(BuiltinTemperingPools.HUNT_TIER_1, Map.of(
                ObeliskEquipmentIds.GIANT_SLAYERS, 8,
                ObeliskEquipmentIds.EXECUTIONERS, 5,
                ObeliskEquipmentIds.AMBUSHERS, 5,
                ObeliskEquipmentIds.TEMPERED, 4
        ));
    }

    private static void assertEntriesValid() {
        List<Identifier> builtInIds =
                ObeliskEquipmentTemplateCatalog.all().stream()
                        .map(content -> content.templateId())
                        .toList();
        Set<Identifier> unique = new HashSet<>(builtInIds);

        assertEquals(
                builtInIds.size(),
                unique.size(),
                "built-in entry ids should be unique"
        );
        assertTrue(
                builtInIds.containsAll(REQUIRED_ENTRIES),
                "all required production entries should be registered"
        );

        for (Identifier entryId : builtInIds) {
            DamageEntryDefinition entry =
                    ObeliskEquipmentTemplateCatalog.find(entryId)
                            .map(content -> content.definition())
                            .orElseThrow();

            assertEquals(entryId, entry.id(), "factory should preserve entry id");
            assertEquals(
                    DamageEntrySlot.ITEM,
                    entry.slot(),
                    "shared weapon/armor entry slot should be ITEM: " + entryId
            );
            assertEquals(
                    DamageEntryStacking.UNIQUE_GROUP,
                    entry.stacking(),
                    "entry stacking should be group-unique: " + entryId
            );
            assertTrue(
                    entry.stackingGroup().isPresent(),
                    "entry stacking group should be explicit: " + entryId
            );
            assertTrue(!entry.rules().isEmpty(), "entry should have rules: " + entryId);
            assertEquals(
                    1,
                    DamageEntryValidator
                            .filterValid(List.of(entry), "obeliskdepths/test")
                            .size(),
                    "DamageNexus should accept entry: " + entryId
            );

            entry.rules().forEach(rule -> rule.operations().forEach(operation ->
                    assertTrue(
                            operation.supportsPhase(rule.phase()),
                            "operation phase mismatch entry="
                                    + entryId
                                    + " rule="
                                    + rule.id()
                                    + " phase="
                                    + rule.phase()
                    )
            ));
        }
    }

    private static void assertProductionAggregation() {
        Map<Identifier, AggregatedTemperingDirection> resolved =
                ObeliskTemperingDirectionPoolResolver.resolveContributions(
                        productionContributions()
                );

        List<Identifier> expectedAvailable = List.of(
                ObeliskTemperingDirectionRegistry.BALANCE,
                ObeliskTemperingDirectionRegistry.EDGE,
                ObeliskTemperingDirectionRegistry.FLAME,
                ObeliskTemperingDirectionRegistry.FROST,
                ObeliskTemperingDirectionRegistry.STORM,
                ObeliskTemperingDirectionRegistry.ARCANE,
                ObeliskTemperingDirectionRegistry.VENOM,
                ObeliskTemperingDirectionRegistry.PRECISION,
                ObeliskTemperingDirectionRegistry.HUNT
        );

        assertEquals(
                expectedAvailable,
                List.copyOf(resolved.keySet()),
                "tier-1 matching inputs should expose implemented directions"
        );
        assertEquals(
                10,
                weight(resolved.get(ObeliskTemperingDirectionRegistry.EDGE),
                        ObeliskEquipmentIds.RAZOR_EDGED),
                "edge recipe should not be registered twice"
        );
        assertTrue(
                !resolved.containsKey(ObeliskTemperingDirectionRegistry.GUARD),
                "guard should not be available without a tier-1 pool"
        );
        assertTrue(
                !resolved.containsKey(ObeliskTemperingDirectionRegistry.ECHO),
                "echo should not be available without a tier-1 pool"
        );
    }

    private static void assertSharedEntryAggregation() {
        Map<Identifier, AggregatedTemperingDirection> resolved =
                ObeliskTemperingDirectionPoolResolver.resolveContributions(
                        List.of(
                                contribution(
                                        "a",
                                        BuiltinTemperingPools.BALANCE_TIER_1,
                                        ObeliskTemperingDirectionRegistry.EDGE
                                ),
                                contribution(
                                        "b",
                                        BuiltinTemperingPools.EDGE_TIER_1,
                                        ObeliskTemperingDirectionRegistry.EDGE
                                )
                        )
                );

        AggregatedTemperingDirection edge =
                resolved.get(ObeliskTemperingDirectionRegistry.EDGE);

        assertEquals(
                18,
                weight(edge, ObeliskEquipmentIds.TEMPERED),
                "shared tempered weight should aggregate"
        );
        assertEquals(
                15,
                weight(edge, ObeliskEquipmentIds.RAZOR_EDGED),
                "shared razor weight should aggregate"
        );
    }

    private static List<ObeliskTemperingDirectionPoolResolver.RecipeContribution>
    productionContributions() {
        return List.of(
                contribution(
                        "tempering/balance_tier_1",
                        BuiltinTemperingPools.BALANCE_TIER_1,
                        ObeliskTemperingDirectionRegistry.BALANCE
                ),
                contribution(
                        "tempering/edge_tier_1",
                        BuiltinTemperingPools.EDGE_TIER_1,
                        ObeliskTemperingDirectionRegistry.EDGE
                ),
                contribution(
                        "tempering/flame_tier_1",
                        BuiltinTemperingPools.FLAME_TIER_1,
                        ObeliskTemperingDirectionRegistry.FLAME
                ),
                contribution(
                        "tempering/frost_tier_1",
                        BuiltinTemperingPools.FROST_TIER_1,
                        ObeliskTemperingDirectionRegistry.FROST
                ),
                contribution(
                        "tempering/storm_tier_1",
                        BuiltinTemperingPools.STORM_TIER_1,
                        ObeliskTemperingDirectionRegistry.STORM
                ),
                contribution(
                        "tempering/arcane_tier_1",
                        BuiltinTemperingPools.ARCANE_TIER_1,
                        ObeliskTemperingDirectionRegistry.ARCANE
                ),
                contribution(
                        "tempering/venom_tier_1",
                        BuiltinTemperingPools.VENOM_TIER_1,
                        ObeliskTemperingDirectionRegistry.VENOM
                ),
                contribution(
                        "tempering/precision_tier_1",
                        BuiltinTemperingPools.PRECISION_TIER_1,
                        ObeliskTemperingDirectionRegistry.PRECISION
                ),
                contribution(
                        "tempering/hunt_tier_1",
                        BuiltinTemperingPools.HUNT_TIER_1,
                        ObeliskTemperingDirectionRegistry.HUNT
                )
        );
    }

    private static ObeliskTemperingDirectionPoolResolver.RecipeContribution
    contribution(String path, Identifier poolId, Identifier directionId) {
        return new ObeliskTemperingDirectionPoolResolver.RecipeContribution(
                Identifier.fromNamespaceAndPath("obeliskdepths", path),
                poolId,
                List.of(directionId)
        );
    }

    private static void assertPoolWeights(
            Identifier poolId,
            Map<Identifier, Integer> expected
    ) {
        Map<Identifier, Integer> actual = new LinkedHashMap<>();

        for (ObeliskTemperingPoolRegistry.WeightedEntry entry
                : ObeliskTemperingPoolRegistry.entries(poolId)) {
            actual.put(entry.templateId(), entry.weight());
        }

        assertEquals(expected, actual, "pool weights: " + poolId);
    }

    private static int weight(
            AggregatedTemperingDirection direction,
            Identifier entryId
    ) {
        if (direction == null) {
            return 0;
        }

        return direction.entries()
                .stream()
                .filter(entry -> entry.templateId().equals(entryId))
                .findFirst()
                .map(AggregatedTemperingEntry::weight)
                .orElse(0);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static <T> void assertEquals(
            T expected,
            T actual,
            String message
    ) {
        if (!expected.equals(actual)) {
            throw new AssertionError(
                    message + " expected=" + expected + " actual=" + actual
            );
        }
    }

    private static void assertEquals(
            int expected,
            int actual,
            String message
    ) {
        if (expected != actual) {
            throw new AssertionError(
                    message + " expected=" + expected + " actual=" + actual
            );
        }
    }
}
