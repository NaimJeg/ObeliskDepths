package io.github.naimjeg.obeliskdepths.tempering;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ObeliskTemperingDirectionReloadListener
        extends SimpleJsonResourceReloadListener<ObeliskTemperingDirectionDefinition> {
    private static final FileToIdConverter DIRECTION_LISTER =
            FileToIdConverter.json("obelisk_tempering_direction");

    public ObeliskTemperingDirectionReloadListener() {
        super(ObeliskTemperingDirectionDefinition.CODEC, DIRECTION_LISTER);
    }

    @Override
    protected void apply(
            Map<Identifier, ObeliskTemperingDirectionDefinition> preparations,
            ResourceManager manager,
            ProfilerFiller profiler
    ) {
        Map<Identifier, ObeliskTemperingDirectionDefinition> valid =
                new LinkedHashMap<>();

        for (Map.Entry<Identifier, ObeliskTemperingDirectionDefinition> entry
                : preparations.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                ObeliskDepths.LOGGER.warn(
                        "Skipping invalid Obelisk tempering direction {}",
                        entry.getKey()
                );
                continue;
            }

            valid.put(entry.getKey(), entry.getValue());
        }

        ObeliskTemperingDirectionRegistry.replace(
                ObeliskTemperingDirectionRegistry.withBuiltIns(valid)
        );

        ObeliskDepths.LOGGER.info(
                "Loaded {} datapack Obelisk tempering directions",
                valid.size()
        );
    }
}
