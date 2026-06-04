package io.github.naimjeg.obeliskdepths.registry;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;

import java.util.Objects;

public final class ModWoodTypes {
    static final String GREAT_SWAMP_TAXODIUM_NAME =
            ObeliskDepths.MOD_ID + ":great_swamp_taxodium";
    private static final String ACCESS_BEFORE_REGISTRATION_MESSAGE =
            "Great Swamp Taxodium wood types were accessed before "
                    + "WoodType.<clinit> registration completed";

    private static volatile BlockSetType greatSwampTaxodiumSet;
    private static volatile WoodType greatSwampTaxodium;
    private static volatile boolean registered;
    private static volatile Throwable registrationFailure;
    private static boolean registering;

    private ModWoodTypes() {
    }

    public static synchronized void registerFromVanillaClinit() {
        registerFromVanillaClinit(VanillaRegistrationBackend.INSTANCE);
    }

    static synchronized void registerFromVanillaClinit(
            RegistrationBackend backend
    ) {
        if (registered) {
            return;
        }
        if (registrationFailure != null) {
            throw new IllegalStateException(
                    "Great Swamp Taxodium wood type registration previously failed",
                    registrationFailure
            );
        }
        if (registering) {
            throw new IllegalStateException(
                    "Recursive Great Swamp Taxodium wood type registration"
            );
        }

        registering = true;
        try {
            BlockSetType setType = Objects.requireNonNull(
                    backend.registerBlockSetType(GREAT_SWAMP_TAXODIUM_NAME),
                    "Great Swamp Taxodium BlockSetType backend result"
            );
            WoodType woodType = Objects.requireNonNull(
                    backend.registerWoodType(GREAT_SWAMP_TAXODIUM_NAME, setType),
                    "Great Swamp Taxodium WoodType backend result"
            );

            greatSwampTaxodiumSet = setType;
            greatSwampTaxodium = woodType;
            registered = true;
        } catch (RuntimeException | Error failure) {
            registrationFailure = failure;
            throw failure;
        } finally {
            registering = false;
        }
    }

    public static WoodType greatSwampTaxodium() {
        WoodType value = greatSwampTaxodium;
        if (registered && value != null) {
            return value;
        }
        throw accessFailure();
    }

    public static BlockSetType greatSwampTaxodiumSet() {
        BlockSetType value = greatSwampTaxodiumSet;
        if (registered && value != null) {
            return value;
        }
        throw accessFailure();
    }

    static boolean isRegisteredForTesting() {
        return registered;
    }

    static synchronized void resetForTesting() {
        greatSwampTaxodiumSet = null;
        greatSwampTaxodium = null;
        registered = false;
        registering = false;
        registrationFailure = null;
    }

    private static IllegalStateException accessFailure() {
        Throwable failure = registrationFailure;
        if (failure != null) {
            return new IllegalStateException(
                    "Great Swamp Taxodium wood type registration failed",
                    failure
            );
        }
        return new IllegalStateException(ACCESS_BEFORE_REGISTRATION_MESSAGE);
    }

    interface RegistrationBackend {
        BlockSetType registerBlockSetType(String name);

        WoodType registerWoodType(String name, BlockSetType setType);
    }

    private enum VanillaRegistrationBackend implements RegistrationBackend {
        INSTANCE;

        @Override
        public BlockSetType registerBlockSetType(String name) {
            return BlockSetType.register(new BlockSetType(name));
        }

        @Override
        public WoodType registerWoodType(String name, BlockSetType setType) {
            return WoodType.register(new WoodType(name, setType));
        }
    }
}
