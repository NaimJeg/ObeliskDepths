package io.github.naimjeg.obeliskdepths.tempering;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Atomic immutable snapshot registry for data-driven tempering directions.
 * No numeric protocol ids are assigned; indices are only a transient UI view.
 */
public final class ObeliskTemperingDirectionRegistry {
    public static final Identifier BALANCE =
            id("balance");
    public static final Identifier EDGE =
            id("edge");
    public static final Identifier FLAME =
            id("flame");
    public static final Identifier FROST =
            id("frost");
    public static final Identifier STORM =
            id("storm");
    public static final Identifier ARCANE =
            id("arcane");
    public static final Identifier VENOM =
            id("venom");
    public static final Identifier PRECISION =
            id("precision");
    public static final Identifier HUNT =
            id("hunt");
    public static final Identifier GUARD =
            id("guard");
    public static final Identifier ECHO =
            id("echo");

    private static final Comparator<Map.Entry<Identifier, ObeliskTemperingDirectionDefinition>>
            ORDERING = Comparator
            .comparingInt((Map.Entry<Identifier, ObeliskTemperingDirectionDefinition> entry) ->
                    entry.getValue().order())
            .thenComparing(entry -> entry.getKey().toString());

    private static volatile Map<Identifier, ObeliskTemperingDirectionDefinition>
            DEFINITIONS = Map.of();

    private ObeliskTemperingDirectionRegistry() {
    }

    public static void bootstrapBuiltIns() {
        replace(withBuiltIns(Map.of()));
    }

    public static Map<Identifier, ObeliskTemperingDirectionDefinition>
    withBuiltIns(Map<Identifier, ObeliskTemperingDirectionDefinition> datapack) {
        Map<Identifier, ObeliskTemperingDirectionDefinition> definitions =
                new LinkedHashMap<>();

        registerBuiltIn(definitions, BALANCE, 0);
        registerBuiltIn(definitions, EDGE, 100);
        registerBuiltIn(definitions, FLAME, 200);
        registerBuiltIn(definitions, FROST, 300);
        registerBuiltIn(definitions, STORM, 400);
        registerBuiltIn(definitions, ARCANE, 500);
        registerBuiltIn(definitions, VENOM, 600);
        registerBuiltIn(definitions, PRECISION, 700);
        registerBuiltIn(definitions, HUNT, 800);
        registerBuiltIn(definitions, GUARD, 900);
        registerBuiltIn(definitions, ECHO, 1000);

        if (datapack != null) {
            definitions.putAll(datapack);
        }

        return orderedCopy(definitions);
    }

    public static void replace(
            Map<Identifier, ObeliskTemperingDirectionDefinition> definitions
    ) {
        DEFINITIONS = orderedCopy(definitions);
    }

    public static Map<Identifier, ObeliskTemperingDirectionDefinition> snapshot() {
        return DEFINITIONS;
    }

    public static Optional<ObeliskTemperingDirectionDefinition> definition(
            Identifier id
    ) {
        return Optional.ofNullable(DEFINITIONS.get(id));
    }

    public static boolean contains(Identifier id) {
        return DEFINITIONS.containsKey(id);
    }

    public static List<Identifier> orderedDirectionIds() {
        return List.copyOf(DEFINITIONS.keySet());
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ObeliskDepths.MOD_ID, path);
    }

    private static void registerBuiltIn(
            Map<Identifier, ObeliskTemperingDirectionDefinition> definitions,
            Identifier id,
            int order
    ) {
        String key = "tempering_direction."
                + id.getNamespace()
                + "."
                + id.getPath();
        definitions.put(id, new ObeliskTemperingDirectionDefinition(
                Component.translatable(key),
                Component.translatable(key + ".description"),
                order
        ));
    }

    private static Map<Identifier, ObeliskTemperingDirectionDefinition> orderedCopy(
            Map<Identifier, ObeliskTemperingDirectionDefinition> definitions
    ) {
        if (definitions == null || definitions.isEmpty()) {
            return Map.of();
        }

        List<Map.Entry<Identifier, ObeliskTemperingDirectionDefinition>> entries =
                new ArrayList<>();

        for (Map.Entry<Identifier, ObeliskTemperingDirectionDefinition> entry
                : definitions.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }

            entries.add(Map.entry(entry.getKey(), entry.getValue()));
        }

        entries.sort(ORDERING);

        Map<Identifier, ObeliskTemperingDirectionDefinition> ordered =
                new LinkedHashMap<>();

        for (Map.Entry<Identifier, ObeliskTemperingDirectionDefinition> entry
                : entries) {
            ordered.put(entry.getKey(), entry.getValue());
        }

        return Collections.unmodifiableMap(ordered);
    }
}
