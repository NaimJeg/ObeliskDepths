package io.github.naimjeg.obeliskdepths.data;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.github.naimjeg.obeliskdepths.dungeon.theme.BuiltinDungeonThemeDefinitions;
import io.github.naimjeg.obeliskdepths.dungeon.theme.DungeonThemeDefinition;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class DungeonThemeDefinitionProvider implements DataProvider {
    private final PackOutput output;

    public DungeonThemeDefinitionProvider(PackOutput output) {
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        CompletableFuture<?>[] futures = BuiltinDungeonThemeDefinitions.all()
                .entrySet()
                .stream()
                .map(entry -> save(cache, entry))
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures);
    }

    @Override
    public String getName() {
        return "Obelisk Depths Dungeon Themes";
    }

    private CompletableFuture<?> save(
            CachedOutput cache,
            Map.Entry<Identifier, DungeonThemeDefinition> entry
    ) {
        JsonElement json = DungeonThemeDefinition.CODEC
                .encodeStart(JsonOps.INSTANCE, entry.getValue())
                .getOrThrow();

        return DataProvider.saveStable(
                cache,
                json,
                outputPath(entry.getKey(), "dungeon_theme")
        );
    }

    private Path outputPath(Identifier id, String directory) {
        return this.output.getOutputFolder(PackOutput.Target.DATA_PACK)
                .resolve(id.getNamespace())
                .resolve(directory)
                .resolve(id.getPath() + ".json");
    }
}
