package io.github.naimjeg.obeliskdepths.dungeon.site.reader;

import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPersistedChunkProbeResult;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPreparationProfiler;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.visitors.CollectFields;
import net.minecraft.nbt.visitors.FieldSelector;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Production adapter and the only production caller of vanilla
 * {@code scanChunk}. Submission is owner-thread-only and does not load,
 * generate, or ticket the chunk.
 *
 * <p>The resolved concrete scanner is {@code IOWorker}. Its public call
 * enqueues a foreground task on a {@code PriorityConsecutiveExecutor} backed
 * by {@code Util.ioPool()} and returns before physical I/O. That task checks
 * pending writes or calls {@code RegionFileStorage.scanChunk}, mutates the
 * {@link CollectFields} visitor, and only then completes the scan future.
 * CompletableFuture publication plus the owner-executor handoff makes those
 * visitor writes visible before owner-thread extraction.</p>
 *
 * <p>Cancelling the returned vanilla future does not remove an already queued
 * I/O task. During {@code IOWorker.close()}, shutdown is marked before its
 * foreground barrier runs, so a task that has not entered its body may also
 * decline to complete its future. Runtime lifecycle cancellation therefore
 * treats logical scanner state separately from physical-operation ownership;
 * a permit is released only by a real terminal backend completion.</p>
 *
 * <p>Normal results, physical scan failures, and decoder failures reach the
 * owner boundary. Owner-executor rejection is the documented escape path: the
 * returned future completes exceptionally on the rejecting thread without
 * touching the visitor, registry, world, or scanner state.</p>
 */
final class ServerPersistedChunkProbeBackend
        implements DungeonPersistedChunkProbeBackend {
    private final ServerLevel level;
    private final PersistedProbeCompletionBoundary completionBoundary;

    ServerPersistedChunkProbeBackend(ServerLevel level) {
        this.level = Objects.requireNonNull(level, "level");
        this.completionBoundary = new PersistedProbeCompletionBoundary(
                this::executeOnOwnerThread,
                this::isOwnerThread,
                DungeonPersistedChunkStatusDecoder::decode
        );
    }

    @Override
    public CompletableFuture<DungeonPersistedChunkProbeResult> probe(
            ChunkPos chunkPos,
            ChunkStatus requiredStatus
    ) {
        Objects.requireNonNull(chunkPos, "chunkPos");
        Objects.requireNonNull(requiredStatus, "requiredStatus");
        if (!isOwnerThread()) {
            throw new IllegalStateException(
                    "Persisted chunk probe submission must run on the owner thread"
            );
        }

        CollectFields scanner = new CollectFields(
                new FieldSelector(StringTag.TYPE, "Status")
        );

        CompletableFuture<Void> scanFuture;
        DungeonPreparationProfiler profiler = DungeonPreparationProfiler.global();
        try {
            long submissionStartNanos = profiler.start();
            scanFuture = this.level.getChunkSource()
                    .chunkScanner()
                    .scanChunk(chunkPos, scanner);
            profiler.record(
                    DungeonPreparationProfiler.Operation.SCAN_CHUNK_SUBMISSION,
                    submissionStartNanos,
                    isOwnerThread()
            );
        } catch (RuntimeException exception) {
            return CompletableFuture.completedFuture(
                    DungeonPersistedChunkStatusDecoder.decode(
                            chunkPos,
                            requiredStatus,
                            0,
                            null,
                            exception
                    )
            );
        }

        if (scanFuture == null) {
            IllegalStateException failure = new IllegalStateException(
                    "scanChunk returned a null future"
            );
            return CompletableFuture.completedFuture(
                    DungeonPersistedChunkStatusDecoder.decode(
                            chunkPos,
                            requiredStatus,
                            0,
                            null,
                            failure
                    )
            );
        }

        long completionStartNanos = profiler.start();
        CompletableFuture<Void> measuredScanFuture = new CompletableFuture<>();
        scanFuture.whenComplete((ignored, throwable) -> {
            profiler.record(
                    DungeonPreparationProfiler.Operation.SCAN_CHUNK_COMPLETION_LATENCY,
                    completionStartNanos,
                    isOwnerThread()
            );
            if (throwable == null) {
                measuredScanFuture.complete(null);
            } else {
                measuredScanFuture.completeExceptionally(throwable);
            }
        });
        return this.completionBoundary.attach(
                measuredScanFuture,
                chunkPos,
                requiredStatus,
                (rawChunkPos, rawRequiredStatus, throwable) ->
                        new RawPersistedChunkProbeResult(
                    rawChunkPos,
                    rawRequiredStatus,
                    scanner.getMissingFieldCount(),
                    scanner.getResult(),
                    throwable
            )
        );
    }

    @Override
    public Optional<DungeonPersistedChunkProbeResult> probeLoadedChunk(
            ChunkPos chunkPos,
            ChunkStatus requiredStatus
    ) {
        Objects.requireNonNull(chunkPos, "chunkPos");
        Objects.requireNonNull(requiredStatus, "requiredStatus");
        if (!isOwnerThread()) {
            throw new IllegalStateException(
                    "Loaded chunk probe must run on the owning server thread"
            );
        }

        LevelChunk chunk = this.level.getChunkSource().getChunkNow(
                chunkPos.x(),
                chunkPos.z()
        );
        if (chunk == null) {
            return Optional.empty();
        }

        return Optional.of(new DungeonPersistedChunkProbeResult(
                chunkPos,
                DungeonPersistedChunkProbeResult.Classification
                        .AVAILABLE_AT_REQUIRED_STATUS,
                Optional.of(ChunkStatus.FULL),
                "chunk already loaded",
                Optional.empty()
        ));
    }

    private void executeOnOwnerThread(Runnable task) {
        this.level.getServer().execute(task);
    }

    @Override
    public boolean isOwnerThread() {
        return this.level.getServer().isSameThread();
    }
}
