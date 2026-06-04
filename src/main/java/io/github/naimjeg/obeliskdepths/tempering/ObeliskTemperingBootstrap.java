package io.github.naimjeg.obeliskdepths.tempering;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import net.minecraft.resources.Identifier;

import java.util.List;

public final class ObeliskTemperingBootstrap {

    private ObeliskTemperingBootstrap() {
    }

    public static void bootstrap() {
        ObeliskTemperingPoolRegistry.clear();
        ObeliskTemperingDirectionRegistry.bootstrapBuiltIns();

        registerBuiltInPools();

        ObeliskDepths.LOGGER.info(
                "Registered built-in Obelisk tempering pools and directions"
        );
    }

    static void registerBuiltInPools() {
        register(
                BuiltinTemperingPools.BALANCE_TIER_1,
                List.of(
                        weighted(ObeliskTemperingEntryFactory.TEMPERED, 10),
                        weighted(ObeliskTemperingEntryFactory.BRUTAL, 6),
                        weighted(ObeliskTemperingEntryFactory.RAZOR_EDGED, 5),
                        weighted(ObeliskTemperingEntryFactory.DEADLY, 4)
                )
        );

        register(
                BuiltinTemperingPools.EDGE_TIER_1,
                List.of(
                        weighted(ObeliskTemperingEntryFactory.RAZOR_EDGED, 10),
                        weighted(ObeliskTemperingEntryFactory.TEMPERED, 8),
                        weighted(ObeliskTemperingEntryFactory.PIERCING, 4),
                        weighted(ObeliskTemperingEntryFactory.SUNDERING, 4),
                        weighted(ObeliskTemperingEntryFactory.EXECUTIONERS, 5)
                )
        );

        register(
                BuiltinTemperingPools.FLAME_TIER_1,
                List.of(
                        weighted(ObeliskTemperingEntryFactory.FLAMING, 10),
                        weighted(ObeliskTemperingEntryFactory.FLAMEFORGED, 6),
                        weighted(ObeliskTemperingEntryFactory.SMOLDERING, 5)
                )
        );

        register(
                BuiltinTemperingPools.FROST_TIER_1,
                List.of(
                        weighted(ObeliskTemperingEntryFactory.FROSTBOUND, 10),
                        weighted(ObeliskTemperingEntryFactory.FROSTFORGED, 6)
                )
        );

        register(
                BuiltinTemperingPools.STORM_TIER_1,
                List.of(
                        weighted(ObeliskTemperingEntryFactory.STORMCHARGED, 9),
                        weighted(ObeliskTemperingEntryFactory.STORMFORGED, 5),
                        weighted(ObeliskTemperingEntryFactory.IMPACTING, 7)
                )
        );

        register(
                BuiltinTemperingPools.ARCANE_TIER_1,
                List.of(
                        weighted(ObeliskTemperingEntryFactory.ARCANE, 10),
                        weighted(ObeliskTemperingEntryFactory.SPELLBLADE, 6)
                )
        );

        register(
                BuiltinTemperingPools.VENOM_TIER_1,
                List.of(
                        weighted(ObeliskTemperingEntryFactory.VENOMOUS, 10),
                        weighted(ObeliskTemperingEntryFactory.TOXIC_EDGE, 6),
                        weighted(ObeliskTemperingEntryFactory.WITHERING, 4)
                )
        );

        register(
                BuiltinTemperingPools.PRECISION_TIER_1,
                List.of(
                        weighted(ObeliskTemperingEntryFactory.DEADLY, 10),
                        weighted(ObeliskTemperingEntryFactory.AMBUSHERS, 6),
                        weighted(ObeliskTemperingEntryFactory.RAZOR_EDGED, 4)
                )
        );

        register(
                BuiltinTemperingPools.HUNT_TIER_1,
                List.of(
                        weighted(ObeliskTemperingEntryFactory.GIANT_SLAYERS, 8),
                        weighted(ObeliskTemperingEntryFactory.EXECUTIONERS, 5),
                        weighted(ObeliskTemperingEntryFactory.AMBUSHERS, 5),
                        weighted(ObeliskTemperingEntryFactory.TEMPERED, 4)
                )
        );
    }

    private static void register(
            Identifier poolId,
            List<ObeliskTemperingPoolRegistry.WeightedEntry> entries
    ) {
        ObeliskTemperingPoolRegistry.register(poolId, entries);
    }

    private static ObeliskTemperingPoolRegistry.WeightedEntry weighted(
            Identifier entryId,
            int weight
    ) {
        return new ObeliskTemperingPoolRegistry.WeightedEntry(
                ObeliskTemperingEntryFactory.createById(entryId).orElseThrow(),
                weight
        );
    }
}
