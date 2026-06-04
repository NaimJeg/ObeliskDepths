package io.github.naimjeg.obeliskdepths.data;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.github.naimjeg.obeliskdepths.dungeon.corridor.BuiltinDungeonCorridorDefinitions;
import io.github.naimjeg.obeliskdepths.dungeon.corridor.DungeonCorridorDefinition;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class DungeonCorridorDefinitionProvider implements DataProvider {
    private final PackOutput output;

    public DungeonCorridorDefinitionProvider(PackOutput output) {
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        CompletableFuture<?>[] futures =
                BuiltinDungeonCorridorDefinitions.all()
                        .entrySet()
                        .stream()
                        .map(entry -> save(cache, entry))
                        .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures);
    }

    @Override
    public String getName() {
        return "Obelisk Depths Dungeon Corridors";
    }

    private CompletableFuture<?> save(
            CachedOutput cache,
            Map.Entry<Identifier, DungeonCorridorDefinition> entry
    ) {
        JsonElement json = DungeonCorridorDefinition.CODEC
                .encodeStart(JsonOps.INSTANCE, entry.getValue())
                .getOrThrow();

        return DataProvider.saveStable(
                cache,
                json,
                outputPath(entry.getKey())
        );
    }

    private Path outputPath(Identifier id) {
        return this.output.getOutputFolder(PackOutput.Target.DATA_PACK)
                .resolve(id.getNamespace())
                .resolve("dungeon_corridor")
                .resolve(id.getPath() + ".json");
    }
}
