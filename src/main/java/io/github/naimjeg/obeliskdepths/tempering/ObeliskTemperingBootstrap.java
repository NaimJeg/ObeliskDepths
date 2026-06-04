package io.github.naimjeg.obeliskdepths.tempering;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.equipment.ObeliskEquipmentIds;
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
                        weighted(ObeliskEquipmentIds.TEMPERED, 10),
                        weighted(ObeliskEquipmentIds.BRUTAL, 6),
                        weighted(ObeliskEquipmentIds.RAZOR_EDGED, 5),
                        weighted(ObeliskEquipmentIds.DEADLY, 4)
                )
        );

        register(
                BuiltinTemperingPools.EDGE_TIER_1,
                List.of(
                        weighted(ObeliskEquipmentIds.RAZOR_EDGED, 10),
                        weighted(ObeliskEquipmentIds.TEMPERED, 8),
                        weighted(ObeliskEquipmentIds.PIERCING, 4),
                        weighted(ObeliskEquipmentIds.SUNDERING, 4),
                        weighted(ObeliskEquipmentIds.EXECUTIONERS, 5)
                )
        );

        register(
                BuiltinTemperingPools.FLAME_TIER_1,
                List.of(
                        weighted(ObeliskEquipmentIds.FLAMING, 10),
                        weighted(ObeliskEquipmentIds.FIRE_EDGE, 5),
                        weighted(ObeliskEquipmentIds.FLAMEFORGED, 6),
                        weighted(ObeliskEquipmentIds.SMOLDERING, 5)
                )
        );

        register(
                BuiltinTemperingPools.FROST_TIER_1,
                List.of(
                        weighted(ObeliskEquipmentIds.FROSTBOUND, 10),
                        weighted(ObeliskEquipmentIds.FROSTFORGED, 6)
                )
        );

        register(
                BuiltinTemperingPools.STORM_TIER_1,
                List.of(
                        weighted(ObeliskEquipmentIds.STORMCHARGED, 9),
                        weighted(ObeliskEquipmentIds.STORMFORGED, 5),
                        weighted(ObeliskEquipmentIds.IMPACTING, 7)
                )
        );

        register(
                BuiltinTemperingPools.ARCANE_TIER_1,
                List.of(
                        weighted(ObeliskEquipmentIds.ARCANE, 10),
                        weighted(ObeliskEquipmentIds.SPELLBLADE, 6)
                )
        );

        register(
                BuiltinTemperingPools.VENOM_TIER_1,
                List.of(
                        weighted(ObeliskEquipmentIds.VENOMOUS, 10),
                        weighted(ObeliskEquipmentIds.TOXIC_EDGE, 6),
                        weighted(ObeliskEquipmentIds.WITHERING, 4)
                )
        );

        register(
                BuiltinTemperingPools.PRECISION_TIER_1,
                List.of(
                        weighted(ObeliskEquipmentIds.DEADLY, 10),
                        weighted(ObeliskEquipmentIds.CRITICAL_EDGE, 4),
                        weighted(ObeliskEquipmentIds.AMBUSHERS, 6),
                        weighted(ObeliskEquipmentIds.RAZOR_EDGED, 4)
                )
        );

        register(
                BuiltinTemperingPools.HUNT_TIER_1,
                List.of(
                        weighted(ObeliskEquipmentIds.GIANT_SLAYERS, 8),
                        weighted(ObeliskEquipmentIds.EXECUTIONERS, 5),
                        weighted(ObeliskEquipmentIds.AMBUSHERS, 5),
                        weighted(ObeliskEquipmentIds.TEMPERED, 4)
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
        return new ObeliskTemperingPoolRegistry.WeightedEntry(entryId, weight);
    }
}
