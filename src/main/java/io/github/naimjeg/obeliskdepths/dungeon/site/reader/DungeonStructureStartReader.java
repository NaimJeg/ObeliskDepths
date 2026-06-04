package io.github.naimjeg.obeliskdepths.dungeon.site.reader;

import io.github.naimjeg.obeliskdepths.ObeliskDepths;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import io.github.naimjeg.obeliskdepths.registry.ModStructures;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.visitors.CollectFields;
import net.minecraft.nbt.visitors.FieldSelector;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.Optional;
import java.util.concurrent.CompletionException;

public final class DungeonStructureStartReader {
    private DungeonStructureStartReader() {
    }

    public static Optional<StructureStart> read(
            ServerLevel level,
            DungeonSiteKey key
    ) {
        return lookup(level, key).start();
    }

    public static LookupResult lookup(
            ServerLevel level,
            DungeonSiteKey key
    ) {
        ChunkPos startChunk = key.toChunkPos();
        ChunkAccess loadedChunk = level.getChunk(
                startChunk.x(),
                startChunk.z(),
                ChunkStatus.STRUCTURE_STARTS,
                false
        );

        if (loadedChunk != null) {
            return lookupInChunk(level, key, loadedChunk, LookupMechanism.LOADED_CHUNK, true, true);
        }

        PersistedChunkProbe persisted = persistedChunkProbe(level, startChunk);

        if (!persisted.available()) {
            return LookupResult.rejected(
                    key,
                    LookupMechanism.PERSISTED_SCAN,
                    false,
                    false,
                    Optional.empty(),
                    Optional.empty(),
                    persisted.reason(),
                    Optional.empty()
            );
        }

        if (persisted.status().isEmpty()
                || !persisted.status().get().isOrAfter(ChunkStatus.STRUCTURE_STARTS)) {
            return LookupResult.rejected(
                    key,
                    LookupMechanism.PERSISTED_SCAN,
                    false,
                    true,
                    persisted.status(),
                    Optional.empty(),
                    LookupRejectionReason.CANDIDATE_NOT_PERSISTED_TO_STRUCTURE_STARTS,
                    Optional.empty()
            );
        }

        ChunkAccess persistedChunk = level.getChunk(
                startChunk.x(),
                startChunk.z(),
                ChunkStatus.STRUCTURE_STARTS,
                true
        );

        if (persistedChunk == null) {
            return LookupResult.rejected(
                    key,
                    LookupMechanism.PERSISTED_CHUNK_LOAD,
                    false,
                    true,
                    persisted.status(),
                    Optional.empty(),
                    LookupRejectionReason.EXISTING_CHUNK_LOOKUP_UNAVAILABLE,
                    Optional.empty()
            );
        }

        return lookupInChunk(level, key, persistedChunk, LookupMechanism.PERSISTED_CHUNK_LOAD, false, true);
    }

    public static ExistingChunkLookupResult lookupExistingChunk(
            ServerLevel level,
            ChunkPos chunkPos,
            ChunkStatus requiredStatus
    ) {
        ChunkAccess loadedChunk = level.getChunk(
                chunkPos.x(),
                chunkPos.z(),
                requiredStatus,
                false
        );

        if (loadedChunk != null) {
            return ExistingChunkLookupResult.accepted(
                    chunkPos,
                    LookupMechanism.LOADED_CHUNK,
                    true,
                    true,
                    Optional.of(loadedChunk.getPersistedStatus()),
                    loadedChunk
            );
        }

        PersistedChunkProbe persisted = persistedChunkProbe(level, chunkPos);

        if (!persisted.available()) {
            return ExistingChunkLookupResult.rejected(
                    chunkPos,
                    LookupMechanism.PERSISTED_SCAN,
                    false,
                    false,
                    Optional.empty(),
                    Optional.empty(),
                    persisted.reason(),
                    persisted.failure()
            );
        }

        if (persisted.status().isEmpty()
                || !persisted.status().get().isOrAfter(requiredStatus)) {
            return ExistingChunkLookupResult.rejected(
                    chunkPos,
                    LookupMechanism.PERSISTED_SCAN,
                    false,
                    true,
                    persisted.status(),
                    Optional.empty(),
                    LookupRejectionReason.CANDIDATE_NOT_PERSISTED_TO_REQUIRED_STATUS,
                    Optional.empty()
            );
        }

        ChunkAccess persistedChunk = level.getChunk(
                chunkPos.x(),
                chunkPos.z(),
                requiredStatus,
                true
        );

        if (persistedChunk == null) {
            return ExistingChunkLookupResult.rejected(
                    chunkPos,
                    LookupMechanism.PERSISTED_CHUNK_LOAD,
                    false,
                    true,
                    persisted.status(),
                    Optional.empty(),
                    LookupRejectionReason.EXISTING_CHUNK_LOOKUP_UNAVAILABLE,
                    Optional.empty()
            );
        }

        return ExistingChunkLookupResult.accepted(
                chunkPos,
                LookupMechanism.PERSISTED_CHUNK_LOAD,
                false,
                true,
                Optional.of(persistedChunk.getPersistedStatus()),
                persistedChunk
        );
    }

    private static LookupResult lookupInChunk(
            ServerLevel level,
            DungeonSiteKey key,
            ChunkAccess chunk,
            LookupMechanism mechanism,
            boolean currentlyLoaded,
            boolean persisted
    ) {
        ChunkPos startChunk = key.toChunkPos();

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
            return LookupResult.rejected(
                    key,
                    mechanism,
                    currentlyLoaded,
                    persisted,
                    Optional.of(chunk.getPersistedStatus()),
                    Optional.of(chunk.getPersistedStatus()),
                    LookupRejectionReason.STRUCTURE_KEY_MISSING,
                    Optional.empty()
            );
        }

        StructureStart start = level.structureManager()
                .getStartForStructure(
                        SectionPos.bottomOf(chunk),
                        structure,
                        chunk
                );

        if (start == null) {
            return LookupResult.rejected(
                    key,
                    mechanism,
                    currentlyLoaded,
                    persisted,
                    Optional.of(chunk.getPersistedStatus()),
                    Optional.of(chunk.getPersistedStatus()),
                    LookupRejectionReason.STRUCTURE_START_MISSING,
                    Optional.empty()
            );
        }

        if (!start.isValid()) {
            return LookupResult.rejected(
                    key,
                    mechanism,
                    currentlyLoaded,
                    persisted,
                    Optional.of(chunk.getPersistedStatus()),
                    Optional.of(chunk.getPersistedStatus()),
                    LookupRejectionReason.STRUCTURE_START_INVALID,
                    Optional.empty()
            );
        }

        return LookupResult.accepted(
                key,
                mechanism,
                currentlyLoaded,
                persisted,
                Optional.of(chunk.getPersistedStatus()),
                start
        );
    }

    private static PersistedChunkProbe persistedChunkProbe(
            ServerLevel level,
            ChunkPos pos
    ) {
        CollectFields scanner = new CollectFields(
                new FieldSelector(StringTag.TYPE, "Status")
        );

        try {
            level.getChunkSource()
                    .chunkScanner()
                    .scanChunk(pos, scanner)
                    .join();
        } catch (CompletionException exception) {
            return new PersistedChunkProbe(
                    false,
                    Optional.empty(),
                    LookupRejectionReason.EXISTING_CHUNK_LOOKUP_FAILED,
                    Optional.of(exception)
            );
        } catch (RuntimeException exception) {
            return new PersistedChunkProbe(
                    false,
                    Optional.empty(),
                    LookupRejectionReason.EXISTING_CHUNK_LOOKUP_FAILED,
                    Optional.of(exception)
            );
        }

        Tag result = scanner.getResult();

        if (scanner.getMissingFieldCount() > 0 || !(result instanceof CompoundTag tag)) {
            return new PersistedChunkProbe(
                    false,
                    Optional.empty(),
                    LookupRejectionReason.CANDIDATE_NOT_PERSISTED,
                    Optional.empty()
            );
        }

        Optional<String> statusName = tag.getString("Status");

        if (statusName.isEmpty()) {
            return new PersistedChunkProbe(
                    false,
                    Optional.empty(),
                    LookupRejectionReason.CANDIDATE_NOT_PERSISTED,
                    Optional.empty()
            );
        }

        ChunkStatus status = ChunkStatus.byName(statusName.get());

        if (status == null) {
            return new PersistedChunkProbe(
                    false,
                    Optional.empty(),
                    LookupRejectionReason.EXISTING_CHUNK_LOOKUP_FAILED,
                    Optional.of(new IllegalStateException("Unknown persisted chunk status: " + statusName.get()))
            );
        }

        return new PersistedChunkProbe(
                true,
                Optional.of(status),
                LookupRejectionReason.CANDIDATE_ACCEPTED,
                Optional.empty()
        );
    }

    public enum LookupMechanism {
        LOADED_CHUNK,
        PERSISTED_SCAN,
        PERSISTED_CHUNK_LOAD
    }

    public enum LookupRejectionReason {
        CANDIDATE_ACCEPTED,
        CANDIDATE_NOT_PERSISTED,
        CANDIDATE_NOT_PERSISTED_TO_STRUCTURE_STARTS,
        CANDIDATE_NOT_PERSISTED_TO_REQUIRED_STATUS,
        EXISTING_CHUNK_LOOKUP_UNAVAILABLE,
        EXISTING_CHUNK_LOOKUP_FAILED,
        STRUCTURE_START_MISSING,
        STRUCTURE_START_INVALID,
        STRUCTURE_KEY_MISSING
    }

    public record LookupResult(
            DungeonSiteKey key,
            LookupMechanism mechanism,
            boolean currentlyLoaded,
            boolean persisted,
            Optional<ChunkStatus> persistedStatus,
            Optional<ChunkStatus> returnedStatus,
            Optional<StructureStart> start,
            LookupRejectionReason rejectionReason,
            Optional<Throwable> failure
    ) {
        static LookupResult accepted(
                DungeonSiteKey key,
                LookupMechanism mechanism,
                boolean currentlyLoaded,
                boolean persisted,
                Optional<ChunkStatus> returnedStatus,
                StructureStart start
        ) {
            return new LookupResult(
                    key,
                    mechanism,
                    currentlyLoaded,
                    persisted,
                    returnedStatus,
                    returnedStatus,
                    Optional.of(start),
                    LookupRejectionReason.CANDIDATE_ACCEPTED,
                    Optional.empty()
            );
        }

        static LookupResult rejected(
                DungeonSiteKey key,
                LookupMechanism mechanism,
                boolean currentlyLoaded,
                boolean persisted,
                Optional<ChunkStatus> persistedStatus,
                Optional<ChunkStatus> returnedStatus,
                LookupRejectionReason reason,
                Optional<Throwable> failure
        ) {
            return new LookupResult(
                    key,
                    mechanism,
                    currentlyLoaded,
                    persisted,
                    persistedStatus,
                    returnedStatus,
                    Optional.empty(),
                    reason,
                    failure
            );
        }
    }

    public record ExistingChunkLookupResult(
            ChunkPos chunkPos,
            LookupMechanism mechanism,
            boolean currentlyLoaded,
            boolean persisted,
            Optional<ChunkStatus> persistedStatus,
            Optional<ChunkStatus> returnedStatus,
            Optional<ChunkAccess> chunk,
            LookupRejectionReason rejectionReason,
            Optional<Throwable> failure
    ) {
        static ExistingChunkLookupResult accepted(
                ChunkPos chunkPos,
                LookupMechanism mechanism,
                boolean currentlyLoaded,
                boolean persisted,
                Optional<ChunkStatus> returnedStatus,
                ChunkAccess chunk
        ) {
            return new ExistingChunkLookupResult(
                    chunkPos,
                    mechanism,
                    currentlyLoaded,
                    persisted,
                    returnedStatus,
                    returnedStatus,
                    Optional.of(chunk),
                    LookupRejectionReason.CANDIDATE_ACCEPTED,
                    Optional.empty()
            );
        }

        static ExistingChunkLookupResult rejected(
                ChunkPos chunkPos,
                LookupMechanism mechanism,
                boolean currentlyLoaded,
                boolean persisted,
                Optional<ChunkStatus> persistedStatus,
                Optional<ChunkStatus> returnedStatus,
                LookupRejectionReason reason,
                Optional<Throwable> failure
        ) {
            return new ExistingChunkLookupResult(
                    chunkPos,
                    mechanism,
                    currentlyLoaded,
                    persisted,
                    persistedStatus,
                    returnedStatus,
                    Optional.empty(),
                    reason,
                    failure
            );
        }
    }

    private record PersistedChunkProbe(
            boolean available,
            Optional<ChunkStatus> status,
            LookupRejectionReason reason,
            Optional<Throwable> failure
    ) {
    }
}
