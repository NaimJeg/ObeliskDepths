package io.github.naimjeg.obeliskdepths.tempering;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.List;
import java.util.Map;

public final class ObeliskTemperingPoolReloadListener
        extends SimpleJsonResourceReloadListener<ObeliskTemperingPoolDefinition> {
    private static final FileToIdConverter POOL_LISTER =
            FileToIdConverter.json("obelisk_tempering_pool");

    public ObeliskTemperingPoolReloadListener() {
        super(ObeliskTemperingPoolDefinition.CODEC, POOL_LISTER);
    }

    @Override
    protected void apply(
            Map<Identifier, ObeliskTemperingPoolDefinition> preparations,
            ResourceManager manager,
            ProfilerFiller profiler
    ) {
        ObeliskTemperingPoolRegistry.clear();
        ObeliskTemperingBootstrap.registerBuiltInPools();

        int loaded = 0;

        for (Map.Entry<Identifier, ObeliskTemperingPoolDefinition> entry
                : preparations.entrySet()) {
            List<ObeliskTemperingPoolRegistry.WeightedEntry> resolved =
                    entry.getValue().resolveEntries(entry.getKey());

            if (resolved.isEmpty()) {
                ObeliskDepths.LOGGER.warn(
                        "Skipping empty Obelisk tempering pool {}",
                        entry.getKey()
                );
                continue;
            }

            ObeliskTemperingPoolRegistry.register(entry.getKey(), resolved);
            loaded++;
        }

        ObeliskDepths.LOGGER.info(
                "Loaded {} datapack Obelisk tempering pools",
                loaded
        );
    }
}
