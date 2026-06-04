package io.github.naimjeg.obeliskdepths.dungeon.site.reader;

import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonAsyncTestSupport;
import io.github.naimjeg.obeliskdepths.dungeon.preparation.DungeonPersistedChunkProbeResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class PersistedProbeCompletionBoundaryTest {
    private PersistedProbeCompletionBoundaryTest() {
    }

    public static void main(String[] args) throws IOException {
        DungeonAsyncTestSupport.bootstrapMinecraft();

        rawResultCopiesNbt();
        rawDecoderPreservesSuccess();
        rawDecoderPreservesMissingStatus();
        rawDecoderPreservesExceptionalCompletion();
        dynamicBoundaryDispatchesBeforeExtractionAndDecode();
        serverBackendDispatchesBeforeScannerExtraction();
        executorRejectionCompletesWithoutExtraction();
        ownerThreadRawCompletionUsesZeroDispatches();
        cancelledRawCompletionTransfersToOwner();
        decoderFailureOnOwnerThread();
    }

    private static void dynamicBoundaryDispatchesBeforeExtractionAndDecode() {
        DungeonAsyncTestSupport.ControlledOwnerExecutor ownerExecutor =
                new DungeonAsyncTestSupport.ControlledOwnerExecutor();
        AtomicInteger extractions = new AtomicInteger();
        AtomicInteger decodes = new AtomicInteger();
        AtomicBoolean extractionWasOwner = new AtomicBoolean();
        AtomicBoolean decodeWasOwner = new AtomicBoolean();
        PersistedProbeCompletionBoundary boundary =
                new PersistedProbeCompletionBoundary(
                        ownerExecutor::execute,
                        ownerExecutor::isOwnerThread,
                        raw -> {
                            decodes.incrementAndGet();
                            decodeWasOwner.set(ownerExecutor.isOwnerThread());
                            return DungeonPersistedChunkStatusDecoder.decode(raw);
                        }
                );

        CompletableFuture<Void> storageFuture = new CompletableFuture<>();
        CompletableFuture<DungeonPersistedChunkProbeResult> resultFuture =
                boundary.attach(
                        storageFuture,
                        pos(),
                        ChunkStatus.STRUCTURE_STARTS,
                        (chunkPos, requiredStatus, throwable) -> {
                            extractions.incrementAndGet();
                            extractionWasOwner.set(ownerExecutor.isOwnerThread());
                            return new RawPersistedChunkProbeResult(
                                    chunkPos,
                                    requiredStatus,
                                    0,
                                    statusTag(ChunkStatus.FULL),
                                    throwable
                            );
                        }
                );

        ownerExecutor.setOwnerThread(false);
        storageFuture.complete(null);

        check(extractions.get() == 0, "dynamic: extraction deferred");
        check(decodes.get() == 0, "dynamic: decode deferred");
        check(ownerExecutor.pendingTaskCount() == 1,
                "dynamic: owner task pending");
        check(!resultFuture.isDone(), "dynamic: result incomplete before drain");

        ownerExecutor.drain();

        check(extractions.get() == 1, "dynamic: extracted once");
        check(decodes.get() == 1, "dynamic: decoded once");
        check(extractionWasOwner.get(), "dynamic: extraction on owner");
        check(decodeWasOwner.get(), "dynamic: decode on owner");
        check(resultFuture.isDone(), "dynamic: result complete after drain");
        check(resultFuture.getNow(null).classification()
                        == DungeonPersistedChunkProbeResult.Classification
                        .AVAILABLE_AT_REQUIRED_STATUS,
                "dynamic: classification");
    }

    private static void rawResultCopiesNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Status", ChunkStatus.FULL.getName());

        RawPersistedChunkProbeResult raw = new RawPersistedChunkProbeResult(
                pos(),
                ChunkStatus.STRUCTURE_STARTS,
                0,
                tag,
                null
        );
        tag.putString("Status", "obeliskdepths:not_a_status");

        DungeonPersistedChunkProbeResult result =
                DungeonPersistedChunkStatusDecoder.decode(raw);
        check(result.classification()
                        == DungeonPersistedChunkProbeResult.Classification
                        .AVAILABLE_AT_REQUIRED_STATUS,
                "raw copy: classification");
        check(result.persistedStatus().equals(Optional.of(ChunkStatus.FULL)),
                "raw copy: original status retained");
    }

    private static void rawDecoderPreservesSuccess() {
        DungeonPersistedChunkProbeResult result =
                DungeonPersistedChunkStatusDecoder.decode(new RawPersistedChunkProbeResult(
                        pos(),
                        ChunkStatus.STRUCTURE_STARTS,
                        0,
                        statusTag(ChunkStatus.FULL),
                        null
                ));
        check(result.classification()
                        == DungeonPersistedChunkProbeResult.Classification
                        .AVAILABLE_AT_REQUIRED_STATUS,
                "raw success: classification");
    }

    private static void rawDecoderPreservesMissingStatus() {
        DungeonPersistedChunkProbeResult result =
                DungeonPersistedChunkStatusDecoder.decode(new RawPersistedChunkProbeResult(
                        pos(),
                        ChunkStatus.FULL,
                        1,
                        new CompoundTag(),
                        null
                ));
        check(result.classification()
                        == DungeonPersistedChunkProbeResult.Classification
                        .NOT_PERSISTED,
                "raw missing: classification");
    }

    private static void rawDecoderPreservesExceptionalCompletion() {
        RuntimeException failure = new RuntimeException("scan failed");
        DungeonPersistedChunkProbeResult result =
                DungeonPersistedChunkStatusDecoder.decode(new RawPersistedChunkProbeResult(
                        pos(),
                        ChunkStatus.FULL,
                        0,
                        null,
                        failure
                ));
        check(result.classification()
                        == DungeonPersistedChunkProbeResult.Classification
                        .SCAN_FAILED,
                "raw exceptional: classification");
        check(result.failure().orElseThrow() == failure,
                "raw exceptional: failure retained");
    }

    private static void serverBackendDispatchesBeforeScannerExtraction()
            throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/io/github/naimjeg/obeliskdepths/dungeon/site/reader/ServerPersistedChunkProbeBackend.java"
        ));
        int callbackStart = source.indexOf("scanFuture.whenComplete");
        int attach = source.indexOf("completionBoundary.attach", callbackStart);
        check(callbackStart >= 0, "source: completion callback exists");
        check(attach > callbackStart, "source: boundary attach exists");
        String callbackBeforeAttach = source.substring(callbackStart, attach);
        check(!callbackBeforeAttach.contains("getMissingFieldCount()"),
                "source: callback does not read missing field count");
        check(!callbackBeforeAttach.contains("getResult()"),
                "source: callback does not read scan result");

        String boundarySource = Files.readString(Path.of(
                "src/main/java/io/github/naimjeg/obeliskdepths/dungeon/site/reader/PersistedProbeCompletionBoundary.java"
        ));
        check(boundarySource.contains("this.ownerExecutor.execute"),
                "source: boundary dispatches to owner");
        check(boundarySource.contains("!this.ownerThreadCheck.isOwnerThread()"),
                "source: boundary asserts owner thread");
        check(boundarySource.contains("extractor.extract"),
                "source: boundary extracts after dispatch");
        check(boundarySource.contains("this.decoder.decode(raw)"),
                "source: boundary decodes after extraction");
    }

    private static void executorRejectionCompletesWithoutExtraction() {
        AtomicInteger submitted = new AtomicInteger();
        AtomicInteger extractions = new AtomicInteger();
        PersistedProbeCompletionBoundary.OwnerExecutor rejectingExecutor =
                task -> {
                    submitted.incrementAndGet();
                    throw new RuntimeException("owner executor rejected submission");
                };

        PersistedProbeCompletionBoundary boundary =
                new PersistedProbeCompletionBoundary(
                        rejectingExecutor,
                        () -> false,
                        raw -> DungeonPersistedChunkStatusDecoder.decode(raw)
                );

        CompletableFuture<Void> storageFuture = new CompletableFuture<>();
        CompletableFuture<DungeonPersistedChunkProbeResult> resultFuture =
                boundary.attach(
                        storageFuture,
                        pos(),
                        ChunkStatus.STRUCTURE_STARTS,
                        (chunkPos, requiredStatus, throwable) -> {
                            extractions.incrementAndGet();
                            return new RawPersistedChunkProbeResult(
                                        chunkPos,
                                        requiredStatus,
                                        0,
                                        statusTag(ChunkStatus.FULL),
                                        throwable
                            );
                        }
                );

        storageFuture.complete(null);

        check(submitted.get() == 1,
                "rejection: attempt made");
        check(resultFuture.isCompletedExceptionally(),
                "rejection: result exceptionally completed");
        check(extractions.get() == 0,
                "rejection: mutable visitor extraction not attempted");
        try {
            resultFuture.getNow(null);
            check(false, "rejection: should throw");
        } catch (CompletionException e) {
            check(e.getCause().getMessage().contains(
                    "owner executor rejected submission"),
                    "rejection: exception message preserved");
        }
    }

    private static void cancelledRawCompletionTransfersToOwner() {
        DungeonAsyncTestSupport.ControlledOwnerExecutor ownerExecutor =
                new DungeonAsyncTestSupport.ControlledOwnerExecutor();
        AtomicBoolean extractionWasOwner = new AtomicBoolean();
        PersistedProbeCompletionBoundary boundary =
                new PersistedProbeCompletionBoundary(
                        ownerExecutor::execute,
                        ownerExecutor::isOwnerThread,
                        DungeonPersistedChunkStatusDecoder::decode
                );

        CompletableFuture<Void> storageFuture = new CompletableFuture<>();
        CompletableFuture<DungeonPersistedChunkProbeResult> resultFuture =
                boundary.attach(
                        storageFuture,
                        pos(),
                        ChunkStatus.STRUCTURE_STARTS,
                        (chunkPos, requiredStatus, throwable) -> {
                            extractionWasOwner.set(
                                    ownerExecutor.isOwnerThread()
                            );
                            return new RawPersistedChunkProbeResult(
                                    chunkPos,
                                    requiredStatus,
                                    0,
                                    null,
                                    throwable
                            );
                        }
                );

        ownerExecutor.setOwnerThread(false);
        storageFuture.cancel(false);
        check(ownerExecutor.pendingTaskCount() == 1,
                "cancelled raw: one owner dispatch");
        check(!resultFuture.isDone(),
                "cancelled raw: waits for owner extraction");

        ownerExecutor.drain();
        check(extractionWasOwner.get(),
                "cancelled raw: extraction on owner");
        DungeonPersistedChunkProbeResult result = resultFuture.getNow(null);
        check(result.classification()
                        == DungeonPersistedChunkProbeResult.Classification
                        .SCAN_FAILED,
                "cancelled raw: terminal scan failure result");
    }

    private static void ownerThreadRawCompletionUsesZeroDispatches() {
        AtomicInteger dispatches = new AtomicInteger();
        DungeonAsyncTestSupport.ControlledOwnerExecutor ownerExecutor =
                new DungeonAsyncTestSupport.ControlledOwnerExecutor();
        ownerExecutor.setOwnerThread(true);

        PersistedProbeCompletionBoundary boundary =
                new PersistedProbeCompletionBoundary(
                        task -> {
                            dispatches.incrementAndGet();
                            ownerExecutor.execute(task);
                        },
                        ownerExecutor::isOwnerThread,
                        raw -> DungeonPersistedChunkStatusDecoder.decode(raw)
                );

        CompletableFuture<Void> storageFuture = new CompletableFuture<>();
        CompletableFuture<DungeonPersistedChunkProbeResult> resultFuture =
                boundary.attach(
                        storageFuture,
                        pos(),
                        ChunkStatus.STRUCTURE_STARTS,
                        (chunkPos, requiredStatus, throwable) ->
                                new RawPersistedChunkProbeResult(
                                        chunkPos,
                                        requiredStatus,
                                        0,
                                        statusTag(ChunkStatus.FULL),
                                        throwable
                                )
                );

        storageFuture.complete(null);

        check(dispatches.get() == 0,
                "owner inline: zero queued dispatches");
        check(ownerExecutor.pendingTaskCount() == 0,
                "owner inline: no pending tasks");

        check(resultFuture.isDone(),
                "owner inline: result completed");
    }

    private static void decoderFailureOnOwnerThread() {
        DungeonAsyncTestSupport.ControlledOwnerExecutor ownerExecutor =
                new DungeonAsyncTestSupport.ControlledOwnerExecutor();

        RuntimeException decoderFailure = new RuntimeException(
                "decoder synthetic failure");
        PersistedProbeCompletionBoundary boundary =
                new PersistedProbeCompletionBoundary(
                        ownerExecutor::execute,
                        ownerExecutor::isOwnerThread,
                        raw -> { throw decoderFailure; }
                );

        CompletableFuture<Void> storageFuture = new CompletableFuture<>();
        CompletableFuture<DungeonPersistedChunkProbeResult> resultFuture =
                boundary.attach(
                        storageFuture,
                        pos(),
                        ChunkStatus.STRUCTURE_STARTS,
                        (chunkPos, requiredStatus, throwable) ->
                                new RawPersistedChunkProbeResult(
                                        chunkPos,
                                        requiredStatus,
                                        0,
                                        statusTag(ChunkStatus.FULL),
                                        throwable
                                )
                );

        storageFuture.complete(null);
        ownerExecutor.drain();

        check(resultFuture.isCompletedExceptionally(),
                "decoder failure: exceptionally completed");
        try {
            resultFuture.getNow(null);
            check(false, "decoder failure: should throw");
        } catch (CompletionException e) {
            check(e.getCause() == decoderFailure,
                    "decoder failure: exception preserved");
        }
    }

    private static CompoundTag statusTag(ChunkStatus status) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Status", status.getName());
        return tag;
    }

    private static ChunkPos pos() {
        return new ChunkPos(0, 0);
    }

    private static void check(boolean condition, String message) {
        DungeonAsyncTestSupport.check(condition, message);
    }
}
