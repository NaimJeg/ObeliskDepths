package io.github.naimjeg.obeliskdepths.dungeon.preparation.chunk;

import io.github.naimjeg.obeliskdepths.registry.ModTicketTypes;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

public final class DungeonChunkLeaseManager {
    private final Map<ChunkPos, LeaseEntry> entries = new HashMap<>();
    private final DungeonChunkTicketBackend backend;
    private long nextEntryToken;
    private boolean cleared;

    DungeonChunkLeaseManager(DungeonChunkTicketBackend backend) {
        this.backend = backend;
        this.nextEntryToken = 0L;
        this.cleared = false;
    }

    public static DungeonChunkLeaseManager createForLevel(ServerLevel level) {
        return new DungeonChunkLeaseManager(new ServerChunkTicketBackend(level));
    }

    public DungeonChunkLease acquire(ChunkPos chunkPos) {
        assertOwnerThread();
        if (this.cleared) {
            throw new IllegalStateException("Chunk lease manager has been cleared");
        }

        LeaseEntry entry = this.entries.get(chunkPos);
        if (entry == null) {
            long token = ++this.nextEntryToken;
            DungeonChunkLoadRequest request = this.backend.acquire(chunkPos);
            entry = new LeaseEntry(
                    token,
                    request.completion(),
                    1,
                    DungeonChunkLeaseState.PENDING,
                    request.ticketInstalled()
            );
            this.entries.put(chunkPos, entry);

            LeaseEntry capturedEntry = entry;
            request.completion().whenComplete((result, throwable) -> {
                this.backend.executeOnOwnerThread(() -> {
                    LeaseEntry current = this.entries.get(chunkPos);
                    if (this.cleared || current != capturedEntry) {
                        return;
                    }
                    if (throwable != null) {
                        current.state = DungeonChunkLeaseState.FAILED;
                    } else {
                        current.state = DungeonChunkLeaseState.READY;
                    }
                });
            });
        } else {
            entry.refCount++;
        }

        return new DungeonChunkLease(chunkPos, entry.token, this);
    }

    void release(ChunkPos chunkPos, long entryToken) {
        assertOwnerThread();
        LeaseEntry entry = this.entries.get(chunkPos);
        if (entry == null || entry.token != entryToken) {
            return;
        }

        entry.refCount--;
        if (entry.refCount > 0) {
            return;
        }

        entry.state = DungeonChunkLeaseState.CANCELLED;
        this.entries.remove(chunkPos, entry);

        RuntimeException releaseFailure = null;
        if (entry.ticketInstalled) {
            try {
                // Backend release failures are infrastructure ticket cleanup failures.
                this.backend.release(chunkPos);
                entry.ticketInstalled = false;
            } catch (RuntimeException exception) {
                releaseFailure = exception;
            }
        }

        if (releaseFailure != null) {
            throw releaseFailure;
        }
    }

    DungeonChunkLeaseState stateFor(ChunkPos chunkPos, long entryToken) {
        assertOwnerThread();
        LeaseEntry entry = this.entries.get(chunkPos);
        if (entry == null || entry.token != entryToken) {
            return DungeonChunkLeaseState.CANCELLED;
        }
        return entry.state;
    }

    public void clear() {
        assertOwnerThread();
        if (this.cleared && this.entries.isEmpty()) {
            return;
        }
        this.cleared = true;
        Map<ChunkPos, LeaseEntry> entriesToRelease = new HashMap<>(this.entries);
        this.entries.clear();

        RuntimeException aggregateFailure = null;
        for (Map.Entry<ChunkPos, LeaseEntry> mapEntry : entriesToRelease.entrySet()) {
            LeaseEntry entry = mapEntry.getValue();
            entry.state = DungeonChunkLeaseState.CANCELLED;
            if (entry.ticketInstalled) {
                try {
                    // Backend release failures are infrastructure ticket cleanup failures.
                    this.backend.release(mapEntry.getKey());
                    entry.ticketInstalled = false;
                } catch (RuntimeException exception) {
                    if (aggregateFailure == null) {
                        aggregateFailure = new IllegalStateException(
                                "Failed to release one or more dungeon preparation tickets"
                        );
                    }
                    aggregateFailure.addSuppressed(exception);
                }
            }
        }

        if (aggregateFailure != null) {
            throw aggregateFailure;
        }
    }

    public int activeLeaseCount() {
        assertOwnerThread();
        return this.entries.size();
    }

    int refCountFor(ChunkPos chunkPos) {
        assertOwnerThread();
        LeaseEntry entry = this.entries.get(chunkPos);
        if (entry == null) {
            return 0;
        }
        return entry.refCount;
    }

    boolean hasActiveEntry(ChunkPos chunkPos) {
        assertOwnerThread();
        return this.entries.containsKey(chunkPos);
    }

    void assertOwnerThread() {
        if (!this.backend.isOwnerThread()) {
            throw new IllegalStateException(
                    "Chunk lease manager must be accessed on the owning server thread"
            );
        }
    }

    long tokenFor(ChunkPos chunkPos) {
        assertOwnerThread();
        LeaseEntry entry = this.entries.get(chunkPos);
        return entry == null ? 0L : entry.token;
    }

    private static final class LeaseEntry {
        final long token;
        final CompletableFuture<?> future;
        int refCount;
        DungeonChunkLeaseState state;
        boolean ticketInstalled;

        LeaseEntry(
                long token,
                CompletableFuture<?> future,
                int refCount,
                DungeonChunkLeaseState state,
                boolean ticketInstalled
        ) {
            this.token = token;
            this.future = future;
            this.refCount = refCount;
            this.state = state;
            this.ticketInstalled = ticketInstalled;
        }
    }

    private static final class ServerChunkTicketBackend implements DungeonChunkTicketBackend {
        private final ServerLevel level;

        ServerChunkTicketBackend(ServerLevel level) {
            this.level = level;
        }

        @Override
        public DungeonChunkLoadRequest acquire(ChunkPos chunkPos) {
            boolean ticketInstalled = false;
            try {
                ticketInstalled = true;
                CompletableFuture<?> completion =
                        this.level.getChunkSource().addTicketAndLoadWithRadius(
                                ModTicketTypes.DUNGEON_PREPARATION.get(),
                                chunkPos,
                                0
                        );
                return new DungeonChunkLoadRequest(completion, true);
            } catch (RuntimeException | Error e) {
                if (ticketInstalled) {
                    this.level.getChunkSource().removeTicketWithRadius(
                            ModTicketTypes.DUNGEON_PREPARATION.get(),
                            chunkPos,
                            0
                    );
                }
                throw e;
            }
        }

        @Override
        public void release(ChunkPos chunkPos) {
            this.level.getChunkSource().removeTicketWithRadius(
                    ModTicketTypes.DUNGEON_PREPARATION.get(),
                    chunkPos,
                    0
            );
        }

        @Override
        public boolean isOwnerThread() {
            return this.level.getServer().isSameThread();
        }

        @Override
        public void executeOnOwnerThread(Runnable task) {
            this.level.getServer().execute(task);
        }
    }
}
