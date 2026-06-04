package io.github.naimjeg.obeliskdepths.registry;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;

import java.util.Objects;

public final class ModWoodTypes {
    static final String AMPHIXYLON_NAME =
            ObeliskDepths.MOD_ID + ":amphixylon";
    private static final String ACCESS_BEFORE_REGISTRATION_MESSAGE =
            "Amphixylon wood types were accessed before "
                    + "WoodType.<clinit> registration completed";

    private static volatile BlockSetType amphixylonSet;
    private static volatile WoodType amphixylon;
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
                    "Amphixylon wood type registration previously failed",
                    registrationFailure
            );
        }
        if (registering) {
            throw new IllegalStateException(
                    "Recursive Amphixylon wood type registration"
            );
        }

        registering = true;
        try {
            BlockSetType setType = Objects.requireNonNull(
                    backend.registerBlockSetType(AMPHIXYLON_NAME),
                    "Amphixylon BlockSetType backend result"
            );
            WoodType woodType = Objects.requireNonNull(
                    backend.registerWoodType(AMPHIXYLON_NAME, setType),
                    "Amphixylon WoodType backend result"
            );

            amphixylonSet = setType;
            amphixylon = woodType;
            registered = true;
        } catch (RuntimeException | Error failure) {
            registrationFailure = failure;
            throw failure;
        } finally {
            registering = false;
        }
    }

    public static WoodType amphixylon() {
        WoodType value = amphixylon;
        if (registered && value != null) {
            return value;
        }
        throw accessFailure();
    }

    public static BlockSetType amphixylonSet() {
        BlockSetType value = amphixylonSet;
        if (registered && value != null) {
            return value;
        }
        throw accessFailure();
    }

    static boolean isRegisteredForTesting() {
        return registered;
    }

    static synchronized void resetForTesting() {
        amphixylonSet = null;
        amphixylon = null;
        registered = false;
        registering = false;
        registrationFailure = null;
    }

    private static IllegalStateException accessFailure() {
        Throwable failure = registrationFailure;
        if (failure != null) {
            return new IllegalStateException(
                    "Amphixylon wood type registration failed",
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
