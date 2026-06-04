package io.github.naimjeg.obeliskdepths.tempering;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ObeliskTemperingDirectionRegistryTest {
    private ObeliskTemperingDirectionRegistryTest() {
    }

    public static void main(String[] args) {
        Identifier frost = Identifier.fromNamespaceAndPath(
                "thirdpartymod",
                "frost"
        );
        Identifier edge = ObeliskTemperingDirectionRegistry.EDGE;
        Identifier balance = ObeliskTemperingDirectionRegistry.BALANCE;

        Map<Identifier, ObeliskTemperingDirectionDefinition> definitions =
                new LinkedHashMap<>();
        definitions.put(frost, definition("Frost", 100));
        definitions.put(edge, definition("Edge", 100));
        definitions.put(balance, definition("Balance", 0));

        ObeliskTemperingDirectionRegistry.replace(definitions);

        assertEquals(
                List.of(balance, edge, frost),
                ObeliskTemperingDirectionRegistry.orderedDirectionIds(),
                "directions should sort by order then identifier"
        );
        ObeliskTemperingDirectionRegistry.bootstrapBuiltIns();
        assertEquals(
                List.of(
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
                ),
                ObeliskTemperingDirectionRegistry.orderedDirectionIds(),
                "built-in directions should use the production order"
        );
    }

    private static ObeliskTemperingDirectionDefinition definition(
            String name,
            int order
    ) {
        return new ObeliskTemperingDirectionDefinition(
                Component.literal(name),
                Component.literal(name + " description"),
                order
        );
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
}
