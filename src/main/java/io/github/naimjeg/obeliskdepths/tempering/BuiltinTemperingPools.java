package io.github.naimjeg.obeliskdepths.tempering;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import net.minecraft.resources.Identifier;

public final class BuiltinTemperingPools {
    public static final Identifier BALANCE_TIER_1 =
            id("balance/tier_1");

    public static final Identifier EDGE_TIER_1 =
            id("edge/tier_1");

    public static final Identifier FLAME_TIER_1 =
            id("flame/tier_1");

    public static final Identifier FROST_TIER_1 =
            id("frost/tier_1");

    public static final Identifier STORM_TIER_1 =
            id("storm/tier_1");

    public static final Identifier ARCANE_TIER_1 =
            id("arcane/tier_1");

    public static final Identifier VENOM_TIER_1 =
            id("venom/tier_1");

    public static final Identifier PRECISION_TIER_1 =
            id("precision/tier_1");

    public static final Identifier HUNT_TIER_1 =
            id("hunt/tier_1");

    private BuiltinTemperingPools() {
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ObeliskDepths.MOD_ID, path);
    }
}
