package io.github.naimjeg.obeliskdepths.tempering;

import io.github.naimjeg.damagenexus.api.rule.entry.DamageEntryDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class ObeliskTemperingPoolRegistry {

    private static final Map<Identifier, List<WeightedEntry>> POOLS =
            new ConcurrentHashMap<>();

    private ObeliskTemperingPoolRegistry() {
    }

    public static void clear() {
        POOLS.clear();
    }

    public static void register(
            Identifier poolId,
            List<WeightedEntry> entries
    ) {
        if (poolId == null) {
            throw new IllegalArgumentException("poolId must not be null");
        }

        if (entries == null || entries.isEmpty()) {
            POOLS.remove(poolId);
            return;
        }

        List<WeightedEntry> normalized = new ArrayList<>();

        for (WeightedEntry entry : entries) {
            if (entry == null || entry.entry() == null) {
                continue;
            }

            if (entry.weight() <= 0) {
                continue;
            }

            normalized.add(entry);
        }

        if (normalized.isEmpty()) {
            POOLS.remove(poolId);
            return;
        }

        POOLS.put(poolId, List.copyOf(normalized));
    }

    public static List<WeightedEntry> entries(Identifier poolId) {
        if (poolId == null) {
            return List.of();
        }

        return POOLS.getOrDefault(poolId, List.of());
    }

    public static Map<Identifier, List<WeightedEntry>> snapshot() {
        Map<Identifier, List<WeightedEntry>> snapshot = new LinkedHashMap<>();

        POOLS.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey(
                        java.util.Comparator.comparing(Identifier::toString)
                ))
                .forEach(entry -> snapshot.put(
                        entry.getKey(),
                        List.copyOf(entry.getValue())
                ));

        return Collections.unmodifiableMap(snapshot);
    }

    public record WeightedEntry(
            DamageEntryDefinition entry,
            int weight
    ) {
        public WeightedEntry {
            if (weight < 0) {
                weight = 0;
            }
        }
    }
}
