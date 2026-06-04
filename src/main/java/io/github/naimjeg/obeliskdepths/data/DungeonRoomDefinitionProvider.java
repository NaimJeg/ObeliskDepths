package io.github.naimjeg.obeliskdepths.data;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import io.github.naimjeg.obeliskdepths.dungeon.room.BuiltinDungeonRoomDefinitions;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomDefinition;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class DungeonRoomDefinitionProvider implements DataProvider {
    private final PackOutput output;

    public DungeonRoomDefinitionProvider(PackOutput output) {
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        CompletableFuture<?>[] futures = BuiltinDungeonRoomDefinitions.all()
                .entrySet()
                .stream()
                .map(entry -> save(cache, entry))
                .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures);
    }

    @Override
    public String getName() {
        return "Obelisk Depths Dungeon Rooms";
    }

    private CompletableFuture<?> save(
            CachedOutput cache,
            Map.Entry<Identifier, DungeonRoomDefinition> entry
    ) {
        JsonElement json = DungeonRoomDefinition.CODEC
                .encodeStart(JsonOps.INSTANCE, entry.getValue())
                .getOrThrow();

        return DataProvider.saveStable(
                cache,
                json,
                outputPath(entry.getKey(), "dungeon_room")
        );
    }

    private Path outputPath(Identifier id, String directory) {
        return this.output.getOutputFolder(PackOutput.Target.DATA_PACK)
                .resolve(id.getNamespace())
                .resolve(directory)
                .resolve(id.getPath() + ".json");
    }
}
