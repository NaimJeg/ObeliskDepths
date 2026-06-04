package io.github.naimjeg.obeliskdepths.tempering;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Immutable-snapshot registry of template ID weights, never definitions. */
public final class ObeliskTemperingPoolRegistry {
    private static final Map<Identifier, List<WeightedEntry>> POOLS = new ConcurrentHashMap<>();

    private ObeliskTemperingPoolRegistry() {
    }

    public static void clear() {
        POOLS.clear();
    }

    public static void register(Identifier poolId, List<WeightedEntry> entries) {
        if (poolId == null) {
            throw new IllegalArgumentException("poolId must not be null");
        }
        if (entries == null || entries.isEmpty()) {
            POOLS.remove(poolId);
            return;
        }
        List<WeightedEntry> normalized = new ArrayList<>();
        for (WeightedEntry entry : entries) {
            if (entry != null && entry.templateId() != null && entry.weight() > 0) {
                normalized.add(entry);
            }
        }
        if (normalized.isEmpty()) {
            POOLS.remove(poolId);
            return;
        }
        POOLS.put(poolId, List.copyOf(normalized));
    }

    public static List<WeightedEntry> entries(Identifier poolId) {
        return poolId == null ? List.of() : POOLS.getOrDefault(poolId, List.of());
    }

    public static Map<Identifier, List<WeightedEntry>> snapshot() {
        Map<Identifier, List<WeightedEntry>> result = new LinkedHashMap<>();
        POOLS.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(java.util.Comparator.comparing(Identifier::toString)))
                .forEach(entry -> result.put(entry.getKey(), List.copyOf(entry.getValue())));
        return Collections.unmodifiableMap(result);
    }

    public record WeightedEntry(Identifier templateId, int weight) {
        public WeightedEntry {
            if (templateId == null) {
                throw new IllegalArgumentException("templateId must not be null");
            }
            if (weight <= 0) {
                throw new IllegalArgumentException("weight must be positive: " + templateId);
            }
        }
    }
}
