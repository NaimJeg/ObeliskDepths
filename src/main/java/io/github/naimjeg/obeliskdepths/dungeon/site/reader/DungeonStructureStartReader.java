package io.github.naimjeg.obeliskdepths.dungeon.site.reader;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import io.github.naimjeg.obeliskdepths.registry.ModStructures;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.Objects;
import java.util.Optional;

/**
 * Reads vanilla {@link StructureStart} instances from the server thread using
 * only already-loaded chunks.
 *
 * <p>This reader is the strict loaded-only preparation boundary. It never
 * scans disk, never blocks on futures, never installs chunk tickets, and
 * never triggers chunk generation. Any caller that needs structure data
 * for an unloaded chunk must arrange for that chunk to be loaded through
 * the asynchronous preparation system before calling this class.</p>
 *
 * <p>The only public entry point is
 * {@link #lookupLoaded(ServerLevel, DungeonSiteKey)}.</p>
 */
public final class DungeonStructureStartReader {
    private DungeonStructureStartReader() {
    }

    /**
     * Looks up an authoritative structure start only from an already-loaded
     * start chunk.
     *
     * <p>Uses {@code getChunkNow} to read the live chunk map without blocking.
     * Returns a rejected result when the chunk is absent, not yet fully
     * loaded, or contains no valid structure start.</p>
     *
     * @param level the dungeon level (must be called on the server thread)
     * @param key   the dungeon site key identifying the target structure
     * @return a result indicating success or the specific failure reason
     * @throws IllegalStateException if not called on the server thread
     */
    public static LoadedStructureStartResult lookupLoaded(
            ServerLevel level,
            DungeonSiteKey key
    ) {
        assertServerThread(level);
        ChunkPos startChunk = key.toChunkPos();

        @SuppressWarnings("deprecation")
        LevelChunk loadedChunk = level.getChunkSource().getChunkNow(
                startChunk.x(),
                startChunk.z()
        );

        if (loadedChunk == null) {
            return LoadedStructureStartResult.rejected(
                    LoadedStructureStartResult.Failure.CHUNK_NOT_LOADED
            );
        }

        return lookupInChunk(level, loadedChunk);
    }

    private static LoadedStructureStartResult lookupInChunk(
            ServerLevel level,
            LevelChunk chunk
    ) {
        Registry<Structure> structureRegistry =
                level.registryAccess().lookupOrThrow(Registries.STRUCTURE);

        Structure structure = structureRegistry.getValue(
                ModStructures.DEPTHS_SITE.identifier()
        );

        if (structure == null) {
            ObeliskDepths.LOGGER.error(
                    "Missing registered dungeon structure {} in level {}",
                    ModStructures.DEPTHS_SITE.identifier(),
                    level.dimension().identifier()
            );
            return LoadedStructureStartResult.rejected(
                    LoadedStructureStartResult.Failure.STRUCTURE_TYPE_MISSING
            );
        }

        StructureStart start = level.structureManager()
                .getStartForStructure(
                        SectionPos.bottomOf(chunk),
                        structure,
                        chunk
                );

        if (start == null) {
            return LoadedStructureStartResult.rejected(
                    LoadedStructureStartResult.Failure.STRUCTURE_START_MISSING
            );
        }

        if (!start.isValid()) {
            return LoadedStructureStartResult.rejected(
                    LoadedStructureStartResult.Failure.STRUCTURE_START_INVALID
            );
        }

        return LoadedStructureStartResult.accepted(start);
    }

    private static void assertServerThread(ServerLevel level) {
        if (!level.getServer().isSameThread()) {
            throw new IllegalStateException(
                    "DungeonStructureStartReader.lookupLoaded must be called on the server thread."
            );
        }
    }

    public record LoadedStructureStartResult(
            Optional<StructureStart> start,
            Failure failure
    ) {
        public LoadedStructureStartResult {
            start = Objects.requireNonNull(start, "start");
            failure = Objects.requireNonNull(failure, "failure");
            if (failure == Failure.NONE && start.isEmpty()) {
                throw new IllegalArgumentException(
                        "Loaded structure-start success requires a start."
                );
            }
            if (failure != Failure.NONE && start.isPresent()) {
                throw new IllegalArgumentException(
                        "Loaded structure-start failure must not carry a start."
                );
            }
        }

        static LoadedStructureStartResult accepted(StructureStart start) {
            return new LoadedStructureStartResult(
                    Optional.of(Objects.requireNonNull(start, "start")),
                    Failure.NONE
            );
        }

        static LoadedStructureStartResult rejected(Failure failure) {
            if (failure == Failure.NONE) {
                throw new IllegalArgumentException(
                        "NONE is not a rejection failure."
                );
            }
            return new LoadedStructureStartResult(Optional.empty(), failure);
        }

        public enum Failure {
            NONE,
            CHUNK_NOT_LOADED,
            STRUCTURE_TYPE_MISSING,
            STRUCTURE_START_MISSING,
            STRUCTURE_START_INVALID
        }
    }
}
