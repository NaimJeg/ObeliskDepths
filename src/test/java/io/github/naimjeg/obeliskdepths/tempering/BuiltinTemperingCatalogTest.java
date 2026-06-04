package io.github.naimjeg.obeliskdepths.tempering;

import io.github.naimjeg.damagenexus.api.rule.entry.DamageEntryDefinition;
import io.github.naimjeg.damagenexus.api.rule.entry.DamageEntrySlot;
import io.github.naimjeg.damagenexus.api.rule.entry.DamageEntryStacking;
import io.github.naimjeg.damagenexus.api.rule.entry.DamageEntryValidator;
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
            ObeliskTemperingDirectionRegistry.HUNT,
            ObeliskTemperingDirectionRegistry.GUARD,
            ObeliskTemperingDirectionRegistry.ECHO
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
            ObeliskTemperingEntryFactory.TEMPERED,
            ObeliskTemperingEntryFactory.BRUTAL,
            ObeliskTemperingEntryFactory.RAZOR_EDGED,
            ObeliskTemperingEntryFactory.PIERCING,
            ObeliskTemperingEntryFactory.SUNDERING,
            ObeliskTemperingEntryFactory.EXECUTIONERS,
            ObeliskTemperingEntryFactory.FLAMING,
            ObeliskTemperingEntryFactory.FLAMEFORGED,
            ObeliskTemperingEntryFactory.SMOLDERING,
            ObeliskTemperingEntryFactory.FROSTBOUND,
            ObeliskTemperingEntryFactory.FROSTFORGED,
            ObeliskTemperingEntryFactory.STORMCHARGED,
            ObeliskTemperingEntryFactory.STORMFORGED,
            ObeliskTemperingEntryFactory.IMPACTING,
            ObeliskTemperingEntryFactory.ARCANE,
            ObeliskTemperingEntryFactory.SPELLBLADE,
            ObeliskTemperingEntryFactory.VENOMOUS,
            ObeliskTemperingEntryFactory.TOXIC_EDGE,
            ObeliskTemperingEntryFactory.WITHERING,
            ObeliskTemperingEntryFactory.DEADLY,
            ObeliskTemperingEntryFactory.AMBUSHERS,
            ObeliskTemperingEntryFactory.GIANT_SLAYERS
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
                        entry.entry() != null && entry.entry().id() != null,
                        "pool entry should resolve: " + poolId
                );
            }
        }

        assertPoolWeights(BuiltinTemperingPools.BALANCE_TIER_1, Map.of(
                ObeliskTemperingEntryFactory.TEMPERED, 10,
                ObeliskTemperingEntryFactory.BRUTAL, 6,
                ObeliskTemperingEntryFactory.RAZOR_EDGED, 5,
                ObeliskTemperingEntryFactory.DEADLY, 4
        ));
        assertPoolWeights(BuiltinTemperingPools.EDGE_TIER_1, Map.of(
                ObeliskTemperingEntryFactory.RAZOR_EDGED, 10,
                ObeliskTemperingEntryFactory.TEMPERED, 8,
                ObeliskTemperingEntryFactory.PIERCING, 4,
                ObeliskTemperingEntryFactory.SUNDERING, 4,
                ObeliskTemperingEntryFactory.EXECUTIONERS, 5
        ));
        assertPoolWeights(BuiltinTemperingPools.FLAME_TIER_1, Map.of(
                ObeliskTemperingEntryFactory.FLAMING, 10,
                ObeliskTemperingEntryFactory.FLAMEFORGED, 6,
                ObeliskTemperingEntryFactory.SMOLDERING, 5
        ));
        assertPoolWeights(BuiltinTemperingPools.FROST_TIER_1, Map.of(
                ObeliskTemperingEntryFactory.FROSTBOUND, 10,
                ObeliskTemperingEntryFactory.FROSTFORGED, 6
        ));
        assertPoolWeights(BuiltinTemperingPools.STORM_TIER_1, Map.of(
                ObeliskTemperingEntryFactory.STORMCHARGED, 9,
                ObeliskTemperingEntryFactory.STORMFORGED, 5,
                ObeliskTemperingEntryFactory.IMPACTING, 7
        ));
        assertPoolWeights(BuiltinTemperingPools.ARCANE_TIER_1, Map.of(
                ObeliskTemperingEntryFactory.ARCANE, 10,
                ObeliskTemperingEntryFactory.SPELLBLADE, 6
        ));
        assertPoolWeights(BuiltinTemperingPools.VENOM_TIER_1, Map.of(
                ObeliskTemperingEntryFactory.VENOMOUS, 10,
                ObeliskTemperingEntryFactory.TOXIC_EDGE, 6,
                ObeliskTemperingEntryFactory.WITHERING, 4
        ));
        assertPoolWeights(BuiltinTemperingPools.PRECISION_TIER_1, Map.of(
                ObeliskTemperingEntryFactory.DEADLY, 10,
                ObeliskTemperingEntryFactory.AMBUSHERS, 6,
                ObeliskTemperingEntryFactory.RAZOR_EDGED, 4
        ));
        assertPoolWeights(BuiltinTemperingPools.HUNT_TIER_1, Map.of(
                ObeliskTemperingEntryFactory.GIANT_SLAYERS, 8,
                ObeliskTemperingEntryFactory.EXECUTIONERS, 5,
                ObeliskTemperingEntryFactory.AMBUSHERS, 5,
                ObeliskTemperingEntryFactory.TEMPERED, 4
        ));
    }

    private static void assertEntriesValid() {
        List<Identifier> builtInIds =
                ObeliskTemperingEntryFactory.builtInEntryIds();
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
        assertTrue(
                builtInIds.contains(ObeliskTemperingEntryFactory.FIRE_TEMPERING_ENTRY),
                "legacy fire entry id should remain resolvable"
        );
        assertTrue(
                builtInIds.contains(ObeliskTemperingEntryFactory.CRIT_TEMPERING_ENTRY),
                "legacy critical entry id should remain resolvable"
        );

        for (Identifier entryId : builtInIds) {
            DamageEntryDefinition entry =
                    ObeliskTemperingEntryFactory.createById(entryId)
                            .orElseThrow();

            assertEquals(entryId, entry.id(), "factory should preserve entry id");
            assertEquals(
                    DamageEntrySlot.WEAPON,
                    entry.slot(),
                    "entry slot should be weapon: " + entryId
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
                        ObeliskTemperingEntryFactory.RAZOR_EDGED),
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
                weight(edge, ObeliskTemperingEntryFactory.TEMPERED),
                "shared tempered weight should aggregate"
        );
        assertEquals(
                15,
                weight(edge, ObeliskTemperingEntryFactory.RAZOR_EDGED),
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
                        "tempering/sword_edge_tier_1",
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
            actual.put(entry.entry().id(), entry.weight());
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
                .filter(entry -> entry.entry().id().equals(entryId))
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
