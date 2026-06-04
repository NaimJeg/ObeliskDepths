package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import io.github.naimjeg.obeliskdepths.dungeon.site.reader.DungeonPersistedChunkProbeBackend;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class DungeonAsyncTestSupport {
    private static boolean bootstrapped;

    private DungeonAsyncTestSupport() {
    }

    public static void bootstrapMinecraft() {
        if (bootstrapped) {
            return;
        }
        try {
            /*
             * Standalone JavaExec tests do not run through the NeoForge FML
             * loader, so full Bootstrap.bootStrap() currently fails while
             * loading modded feature flags. These tests only need built-in
             * registry registration to allow ChunkStatus and ChunkPos static
             * initialization, so mark the vanilla bootstrap gate explicitly.
             */
            Class<?> bootstrap = Class.forName("net.minecraft.server.Bootstrap");
            Field bootstrappedField =
                    bootstrap.getDeclaredField("isBootstrapped");
            bootstrappedField.setAccessible(true);
            bootstrappedField.setBoolean(null, true);
            bootstrapped = true;
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(
                    "Minecraft test bootstrap failed",
                    exception
            );
        }
    }

    public static List<DungeonSiteKey> candidates(int count) {
        ArrayList<DungeonSiteKey> keys = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            keys.add(new DungeonSiteKey(i, 0));
        }
        return List.copyOf(keys);
    }

    public static ChunkPos chunk(int index) {
        return new ChunkPos(index, 0);
    }

    public static DungeonPersistedChunkProbeResult available(
            ChunkPos chunkPos
    ) {
        return available(chunkPos, ChunkStatus.FULL);
    }

    public static DungeonPersistedChunkProbeResult available(
            ChunkPos chunkPos,
            ChunkStatus actualStatus
    ) {
        return new DungeonPersistedChunkProbeResult(
                chunkPos,
                DungeonPersistedChunkProbeResult.Classification
                        .AVAILABLE_AT_REQUIRED_STATUS,
                Optional.of(actualStatus),
                "",
                Optional.empty()
        );
    }

    public static DungeonPersistedChunkProbeResult notPersisted(
            ChunkPos chunkPos
    ) {
        return new DungeonPersistedChunkProbeResult(
                chunkPos,
                DungeonPersistedChunkProbeResult.Classification.NOT_PERSISTED,
                Optional.empty(),
                "not persisted",
                Optional.empty()
        );
    }

    public static DungeonPersistedChunkProbeResult failed(
            ChunkPos chunkPos,
            Throwable throwable
    ) {
        return new DungeonPersistedChunkProbeResult(
                chunkPos,
                DungeonPersistedChunkProbeResult.Classification.SCAN_FAILED,
                Optional.empty(),
                "failed",
                throwable == null ? Optional.empty() : Optional.of(throwable)
        );
    }

    public static DungeonSiteProbeReport emptyReport() {
        return new DungeonSiteProbeReport(
                List.of(),
                List.of(),
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                false
        );
    }

    public static DungeonSiteProbeReport requireCompleted(
            CompletionStage<DungeonSiteProbeReport> stage,
            String message
    ) {
        CompletableFuture<DungeonSiteProbeReport> future =
                stage.toCompletableFuture();
        check(future.isDone(), message + ": stage not complete");
        return future.getNow(null);
    }

    public static void requireNotCompleted(
            CompletionStage<DungeonSiteProbeReport> stage,
            String message
    ) {
        check(!stage.toCompletableFuture().isDone(), message);
    }

    public static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static final class ControlledOwnerExecutor {
        private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        private boolean ownerThread = true;

        public boolean isOwnerThread() {
            return this.ownerThread;
        }

        public void setOwnerThread(boolean ownerThread) {
            this.ownerThread = ownerThread;
        }

        public void execute(Runnable task) {
            this.queue.add(task);
        }

        public int pendingTaskCount() {
            return this.queue.size();
        }

        public void drain() {
            boolean previousOwnerThread = this.ownerThread;
            this.ownerThread = true;
            try {
                while (!this.queue.isEmpty()) {
                    this.queue.removeFirst().run();
                }
            } finally {
                this.ownerThread = previousOwnerThread;
            }
        }

        public void drainWithoutOwnerThread() {
            boolean previousOwnerThread = this.ownerThread;
            this.ownerThread = false;
            try {
                while (!this.queue.isEmpty()) {
                    this.queue.removeFirst().run();
                }
            } finally {
                this.ownerThread = previousOwnerThread;
            }
        }
    }

    public static final class ControlledProbeBackend
            implements DungeonPersistedChunkProbeBackend {
        public final ControlledOwnerExecutor ownerExecutor =
                new ControlledOwnerExecutor();
        private final List<PendingProbe> submissions = new ArrayList<>();
        private final Map<ChunkPos, DungeonPersistedChunkProbeResult>
                loadedResults = new HashMap<>();
        private final Map<ChunkPos, RuntimeException> synchronousFailures =
                new HashMap<>();
        private final Set<ChunkPos> nullFutureChunks = new HashSet<>();
        private final List<Boolean> loadedProbeOwnerThread = new ArrayList<>();

        public void addLoadedResult(
                ChunkPos chunkPos,
                DungeonPersistedChunkProbeResult result
        ) {
            this.loadedResults.put(chunkPos, result);
        }

        public void addSynchronousFailure(
                ChunkPos chunkPos,
                RuntimeException exception
        ) {
            this.synchronousFailures.put(chunkPos, exception);
        }

        public void addNullFutureChunk(ChunkPos chunkPos) {
            this.nullFutureChunks.add(chunkPos);
        }

        public int probeCalls() {
            return this.submissions.size();
        }

        public int loadedProbeCalls() {
            return this.loadedProbeOwnerThread.size();
        }

        public boolean loadedProbeWasOwnerThread(int index) {
            return this.loadedProbeOwnerThread.get(index);
        }

        public PendingProbe submission(int index) {
            return this.submissions.get(index);
        }

        public void completeAvailable(int submissionIndex) {
            PendingProbe submission = submission(submissionIndex);
            submission.future().complete(available(submission.chunkPos()));
        }

        public void complete(
                int submissionIndex,
                DungeonPersistedChunkProbeResult result
        ) {
            submission(submissionIndex).future().complete(result);
        }

        public void completeExceptionally(
                int submissionIndex,
                Throwable throwable
        ) {
            submission(submissionIndex).future().completeExceptionally(throwable);
        }

        @Override
        public CompletableFuture<DungeonPersistedChunkProbeResult> probe(
                ChunkPos chunkPos,
                ChunkStatus requiredStatus
        ) {
            RuntimeException failure =
                    this.synchronousFailures.remove(chunkPos);
            if (failure != null) {
                this.submissions.add(new PendingProbe(chunkPos, null));
                throw failure;
            }
            if (this.nullFutureChunks.remove(chunkPos)) {
                this.submissions.add(new PendingProbe(chunkPos, null));
                return null;
            }

            CompletableFuture<DungeonPersistedChunkProbeResult> future =
                    new CompletableFuture<>();
            this.submissions.add(new PendingProbe(chunkPos, future));
            return future;
        }

        @Override
        public Optional<DungeonPersistedChunkProbeResult> probeLoadedChunk(
                ChunkPos chunkPos,
                ChunkStatus requiredStatus
        ) {
            boolean ownerThread = isOwnerThread();
            this.loadedProbeOwnerThread.add(ownerThread);
            if (!ownerThread) {
                throw new IllegalStateException("loaded probe off owner thread");
            }
            return Optional.ofNullable(this.loadedResults.get(chunkPos));
        }

        @Override
        public boolean isOwnerThread() {
            return this.ownerExecutor.isOwnerThread();
        }
    }

    public record PendingProbe(
            ChunkPos chunkPos,
            CompletableFuture<DungeonPersistedChunkProbeResult> future
    ) {
    }
}
