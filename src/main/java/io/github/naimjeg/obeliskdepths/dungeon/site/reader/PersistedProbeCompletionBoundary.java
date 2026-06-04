package io.github.naimjeg.obeliskdepths.dungeon.site.reader;

import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPersistedChunkProbeResult;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Single worker-to-owner thread transition for persisted-chunk probe
 * completions.
 *
 * <p><b>Contracted behavior:</b>
 * <ul>
 * <li>Raw {@code scanChunk} completion may happen on any thread.</li>
 * <li>This boundary dispatches exactly once to the owner thread:
 *     <ul>
 *     <li>If the raw completion already occurs on the owner thread,
 *         the dispatch runs inline (zero queued dispatches).</li>
 *     <li>If the raw completion occurs on a worker thread, exactly
 *         one {@code ownerExecutor.execute} call is made.</li>
 *     </ul>
 * </li>
 * <li>Scanner extraction ({@link CollectFields#getMissingFieldCount},
 *     {@link CollectFields#getResult}) and registry decoding
 *     ({@link BuiltInRegistries#CHUNK_STATUS}) occur exclusively on
 *     the owner thread.</li>
 * <li>No mutable scanner state is accessed from the raw completion
 *     thread.</li>
 * <li>Normal results, scan failures, and decoder failures complete on the
 *     owner thread. If owner-executor submission is rejected, the result
 *     future completes exceptionally on the rejecting thread; this preserves
 *     the terminal signal without accessing the mutable visitor or registry.</li>
 * </ul>
 *
 * <p><b>Resolved sources (Minecraft 26.1.2):</b>
 * <ul>
 * <li>{@code ChunkScanAccess.scanChunk} is concretely implemented by
 *     {@code IOWorker.scanChunk}. It queues a foreground task on a
 *     {@code PriorityConsecutiveExecutor} backed by {@code Util.ioPool()}.
 *     Physical {@code RegionFileStorage.scanChunk} I/O and visitor mutation
 *     occur in that task, followed by future completion on the same worker.</li>
 * <li>{@code CollectFields}: mutable NBT visitor whose
 *     {@code getMissingFieldCount()} and {@code getResult()} become
 *     valid only after the scan future completes.</li>
 * <li>{@code BuiltInRegistries.CHUNK_STATUS} is a bootstrapped
 *     {@code DefaultedMappedRegistry}; built-in registries are frozen after
 *     bootstrap. The source does not impose an owner-thread read requirement,
 *     but this subsystem deliberately decodes only on owner.</li>
 * </ul>
 */
final class PersistedProbeCompletionBoundary {
    private final OwnerExecutor ownerExecutor;
    private final OwnerThreadCheck ownerThreadCheck;
    private final Decoder decoder;

    PersistedProbeCompletionBoundary(
            OwnerExecutor ownerExecutor,
            OwnerThreadCheck ownerThreadCheck,
            Decoder decoder
    ) {
        this.ownerExecutor = Objects.requireNonNull(
                ownerExecutor,
                "ownerExecutor"
        );
        this.ownerThreadCheck = Objects.requireNonNull(
                ownerThreadCheck,
                "ownerThreadCheck"
        );
        this.decoder = Objects.requireNonNull(decoder, "decoder");
    }

    CompletableFuture<DungeonPersistedChunkProbeResult> attach(
            CompletableFuture<Void> scanFuture,
            ChunkPos chunkPos,
            ChunkStatus requiredStatus,
            ScannerExtractor extractor
    ) {
        Objects.requireNonNull(scanFuture, "scanFuture");
        Objects.requireNonNull(chunkPos, "chunkPos");
        Objects.requireNonNull(requiredStatus, "requiredStatus");
        Objects.requireNonNull(extractor, "extractor");

        CompletableFuture<DungeonPersistedChunkProbeResult> resultFuture =
                new CompletableFuture<>();
        scanFuture.whenComplete((ignored, throwable) -> {
            try {
                if (this.ownerThreadCheck.isOwnerThread()) {
                    // Already on owner thread: complete inline (zero dispatches).
                    completeOnOwnerThread(
                            resultFuture,
                            chunkPos,
                            requiredStatus,
                            extractor,
                            throwable
                    );
                } else {
                    this.ownerExecutor.execute(() -> completeOnOwnerThread(
                            resultFuture,
                            chunkPos,
                            requiredStatus,
                            extractor,
                            throwable
                    ));
                }
            } catch (RuntimeException exception) {
                resultFuture.completeExceptionally(exception);
            }
        });
        return resultFuture;
    }

    private void completeOnOwnerThread(
            CompletableFuture<DungeonPersistedChunkProbeResult> resultFuture,
            ChunkPos chunkPos,
            ChunkStatus requiredStatus,
            ScannerExtractor extractor,
            Throwable throwable
    ) {
        if (resultFuture.isDone()) {
            return;
        }
        if (!this.ownerThreadCheck.isOwnerThread()) {
            resultFuture.completeExceptionally(new IllegalStateException(
                    "Persisted chunk probe completion must run on the owner thread"
            ));
            return;
        }
        try {
            RawPersistedChunkProbeResult raw = extractor.extract(
                    chunkPos,
                    requiredStatus,
                    throwable
            );
            resultFuture.complete(this.decoder.decode(raw));
        } catch (Throwable failure) {
            resultFuture.completeExceptionally(failure);
        }
    }

    @FunctionalInterface
    interface OwnerExecutor {
        void execute(Runnable task);
    }

    @FunctionalInterface
    interface OwnerThreadCheck {
        boolean isOwnerThread();
    }

    @FunctionalInterface
    interface ScannerExtractor {
        RawPersistedChunkProbeResult extract(
                ChunkPos chunkPos,
                ChunkStatus requiredStatus,
                Throwable throwable
        );
    }

    @FunctionalInterface
    interface Decoder {
        DungeonPersistedChunkProbeResult decode(RawPersistedChunkProbeResult raw);
    }
}
