package io.github.naimjeg.obeliskdepths.tempering;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.naimjeg.damagenexus.api.rule.entry.DamageEntryDefinition;
import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record ObeliskTemperingPoolDefinition(
        List<Entry> entries
) {
    public static final Codec<ObeliskTemperingPoolDefinition> CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Entry.CODEC
                            .listOf()
                            .fieldOf("entries")
                            .forGetter(ObeliskTemperingPoolDefinition::entries)
            ).apply(instance, ObeliskTemperingPoolDefinition::new));

    public ObeliskTemperingPoolDefinition {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public List<ObeliskTemperingPoolRegistry.WeightedEntry> resolveEntries(
            Identifier poolId
    ) {
        List<ObeliskTemperingPoolRegistry.WeightedEntry> resolved =
                new ArrayList<>();

        for (Entry entry : this.entries) {
            Optional<DamageEntryDefinition> definition =
                    ObeliskTemperingEntryFactory.createById(entry.entry());

            if (definition.isEmpty()) {
                ObeliskDepths.LOGGER.warn(
                        "Skipping unknown Obelisk tempering entry {} in pool {}",
                        entry.entry(),
                        poolId
                );
                continue;
            }

            resolved.add(new ObeliskTemperingPoolRegistry.WeightedEntry(
                    definition.get(),
                    entry.weight()
            ));
        }

        return List.copyOf(resolved);
    }

    public record Entry(
            Identifier entry,
            int weight
    ) {
        public static final Codec<Entry> CODEC =
                RecordCodecBuilder.create(instance -> instance.group(
                        Identifier.CODEC
                                .fieldOf("entry")
                                .forGetter(Entry::entry),
                        ExtraCodecs.POSITIVE_INT
                                .optionalFieldOf("weight", 1)
                                .forGetter(Entry::weight)
                ).apply(instance, Entry::new));

        public Entry {
            if (entry == null) {
                throw new IllegalArgumentException(
                        "Tempering pool entry id must not be null"
                );
            }

            weight = Math.max(1, weight);
        }
    }
}
