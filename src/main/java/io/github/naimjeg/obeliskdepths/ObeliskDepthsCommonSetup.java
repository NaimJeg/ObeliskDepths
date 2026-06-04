package io.github.naimjeg.obeliskdepths;

import io.github.naimjeg.obeliskdepths.tempering.ObeliskTemperingBootstrap;

import java.util.concurrent.atomic.AtomicBoolean;

public final class ObeliskDepthsCommonSetup {

    private static final AtomicBoolean BOOTSTRAPPED =
            new AtomicBoolean(false);

    private ObeliskDepthsCommonSetup() {
    }

    public static void bootstrap() {
        if (!BOOTSTRAPPED.compareAndSet(false, true)) {
            ObeliskDepths.LOGGER.warn(
                    "Skipped duplicate common setup bootstrap for {}",
                    ObeliskDepths.MOD_ID
            );
            return;
        }

        ObeliskDepths.LOGGER.info(
                "Starting common setup for {}",
                ObeliskDepths.MOD_ID
        );

        ObeliskTemperingBootstrap.bootstrap();

        ObeliskDepths.LOGGER.info(
                "Finished common setup for {}",
                ObeliskDepths.MOD_ID
        );
    }
}