package io.github.naimjeg.obeliskdepths.tempering;

import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;

public final class ObeliskTemperingDirectionPoolResolverTest {
    private static final Identifier EDGE =
            ObeliskTemperingDirectionRegistry.EDGE;
    private static final Identifier BALANCE =
            ObeliskTemperingDirectionRegistry.BALANCE;
    private static final Identifier UNKNOWN =
            Identifier.fromNamespaceAndPath("thirdpartymod", "missing");
    private static final Identifier POOL_A =
            Identifier.fromNamespaceAndPath("obeliskdepths", "test_a");
    private static final Identifier POOL_B =
            Identifier.fromNamespaceAndPath("obeliskdepths", "test_b");

    private ObeliskTemperingDirectionPoolResolverTest() {
    }

    public static void main(String[] args) {
        ObeliskTemperingDirectionRegistry.bootstrapBuiltIns();
        ObeliskTemperingPoolRegistry.clear();
        ObeliskTemperingBootstrap.registerBuiltInPools();

        ObeliskTemperingPoolRegistry.register(
                POOL_A,
                List.of(
                        new ObeliskTemperingPoolRegistry.WeightedEntry(
                                ObeliskTemperingEntryFactory
                                        .createFireTemperingEntry(),
                                10
                        )
                )
        );
        ObeliskTemperingPoolRegistry.register(
                POOL_B,
                List.of(
                        new ObeliskTemperingPoolRegistry.WeightedEntry(
                                ObeliskTemperingEntryFactory
                                        .createFireTemperingEntry(),
                                4
                        ),
                        new ObeliskTemperingPoolRegistry.WeightedEntry(
                                ObeliskTemperingEntryFactory
                                        .createCritTemperingEntry(),
                                6
                        )
                )
        );

        List<ObeliskTemperingDirectionPoolResolver.RecipeContribution>
                contributions = List.of(
                contribution("a", POOL_A, List.of(EDGE)),
                contribution("b", POOL_B, List.of(EDGE, BALANCE, EDGE)),
                contribution("unknown", POOL_A, List.of(UNKNOWN))
        );

        Map<Identifier, AggregatedTemperingDirection> resolved =
                ObeliskTemperingDirectionPoolResolver
                        .resolveContributions(contributions);

        AggregatedTemperingDirection edge = resolved.get(EDGE);
        AggregatedTemperingDirection balance = resolved.get(BALANCE);

        assertTrue(edge != null, "edge direction should be available");
        assertTrue(balance != null, "balance direction should be available");
        assertTrue(
                !resolved.containsKey(UNKNOWN),
                "unknown direction should not be available"
        );
        assertEquals(
                2,
                edge.entries().size(),
                "edge should merge duplicate fire entry and include crit"
        );
        assertEquals(
                14,
                weight(edge, "obeliskdepths:tempering/fire_edge"),
                "duplicate fire weights should be summed"
        );
        assertEquals(
                6,
                weight(edge, "obeliskdepths:tempering/critical_edge"),
                "critical weight should be contributed"
        );
        assertEquals(
                4,
                weight(balance, "obeliskdepths:tempering/fire_edge"),
                "multi-direction recipe should contribute once to balance"
        );
        ObeliskTemperingPoolRegistry.clear();
        ObeliskTemperingBootstrap.registerBuiltInPools();
    }

    private static ObeliskTemperingDirectionPoolResolver.RecipeContribution
    contribution(
            String id,
            Identifier pool,
            List<Identifier> directions
    ) {
        return new ObeliskTemperingDirectionPoolResolver.RecipeContribution(
                Identifier.fromNamespaceAndPath("obeliskdepths", id),
                pool,
                directions
        );
    }

    private static int weight(
            AggregatedTemperingDirection direction,
            String entryId
    ) {
        return direction.entries()
                .stream()
                .filter(entry -> entry.entry().id().toString().equals(entryId))
                .findFirst()
                .map(AggregatedTemperingEntry::weight)
                .orElse(0);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
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
