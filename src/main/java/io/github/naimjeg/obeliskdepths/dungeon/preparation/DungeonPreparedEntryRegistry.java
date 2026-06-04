
package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.id.PortalSessionId;
import net.minecraft.server.level.ServerLevel;

import java.util.*;
import java.util.function.Predicate;

/**
 * Server-thread-confined registry of prepared portal entries.
 *
 * <p>Each entry owns a lease bundle; the registry is responsible for
 * deterministic cleanup.  Duplicate registration for the same portal
 * session is rejected (without closing the rejected entry).</p>
 *
 * <p>Entries are transient and never persisted.</p>
 */
public final class DungeonPreparedEntryRegistry {
    private final Map<PortalSessionId, DungeonPreparedPortalEntry> entries =
            new HashMap<>();
    private final ArrayDeque<PortalSessionId> maintenanceOrder =
            new ArrayDeque<>();
    private final DungeonSiteClaimBackend backend;
    private boolean cleared;

    DungeonPreparedEntryRegistry(DungeonSiteClaimBackend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.cleared = false;
    }

    public static DungeonPreparedEntryRegistry createForLevel(ServerLevel level) {
        return new DungeonPreparedEntryRegistry(
                new ServerEntryThreadBackend(level)
        );
    }

    public Optional<DungeonPreparedPortalEntry> get(PortalSessionId id) {
        assertOwnerThread();
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(this.entries.get(id));
    }

    /**
     * Registers a prepared entry.
     *
     * <p>If the portal session is already registered, the incoming entry
     * is rejected without being closed, leaving ownership with the caller.
     * The existing entry is left intact.</p>
     *
     * @param entry non-null entry to register
     * @throws IllegalStateException if the entry's portal session is already registered
     */
    public void register(DungeonPreparedPortalEntry entry) {
        assertOwnerThread();
        Objects.requireNonNull(entry, "entry");
        if (this.cleared) {
            throw new IllegalStateException("Entry registry has been cleared");
        }
        PortalSessionId id = entry.portalSessionId();
        if (this.entries.containsKey(id)) {
            throw new IllegalStateException(
                    "Portal session already has a prepared entry: " + id
            );
        }
        this.entries.put(id, entry);
        this.maintenanceOrder.addLast(id);
    }

    /**
     * Removes and closes the entry for the given portal session, if present.
     *
     * <p>The removed entry is closed exactly once.  A second call for
     * the same id returns empty without closing anything.</p>
     *
     * @param id portal session id
     * @return the removed entry, or empty if none was registered
     */
    public Optional<DungeonPreparedPortalEntry> removeAndClose(
            PortalSessionId id
    ) {
        assertOwnerThread();
        Objects.requireNonNull(id, "id");
        DungeonPreparedPortalEntry removed = this.entries.remove(id);
        if (removed != null) {
            this.maintenanceOrder.remove(id);
            removed.close();
        }
        return Optional.ofNullable(removed);
    }

    public boolean removeAndCloseExact(
            PortalSessionId id,
            DungeonPreparedPortalEntry expected
    ) {
        assertOwnerThread();
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(expected, "expected");
        if (!this.entries.remove(id, expected)) {
            return false;
        }
        this.maintenanceOrder.remove(id);
        expected.close();
        return true;
    }

    public int removeAndCloseIf(
            Predicate<DungeonPreparedPortalEntry> predicate
    ) {
        assertOwnerThread();
        Objects.requireNonNull(predicate, "predicate");

        List<DungeonPreparedPortalEntry> selected = new ArrayList<>();

        for (Map.Entry<PortalSessionId, DungeonPreparedPortalEntry> entry
                : this.entries.entrySet()) {
            if (predicate.test(entry.getValue())) {
                selected.add(entry.getValue());
            }
        }

        CloseFailures closeFailures = new CloseFailures();
        int removedCount = 0;

        for (DungeonPreparedPortalEntry expected : selected) {
            PortalSessionId id = expected.portalSessionId();
            if (!this.entries.remove(id, expected)) {
                continue;
            }
            this.maintenanceOrder.remove(id);

            removedCount++;

            try {
                expected.close();
            } catch (RuntimeException | Error failure) {
                closeFailures.capture(failure);
            }
        }

        closeFailures.throwIfPresent();

        return removedCount;
    }

    /**
     * Returns an immutable snapshot of every registered entry.
     *
     * <p>The caller can safely inspect entries without mutating the
     * registry.  This is the intended surface for reconciliation
     * algorithms that must analyze entries before deciding which
     * removals to apply.</p>
     */
    public List<DungeonPreparedPortalEntry> snapshotEntries() {
        assertOwnerThread();
        return List.copyOf(this.entries.values());
    }

    public List<PortalSessionId> registeredSessionIds() {
        assertOwnerThread();
        return List.copyOf(this.entries.keySet());
    }

    public int preparedEntryChunkCount() {
        assertOwnerThread();
        int chunks = 0;
        for (DungeonPreparedPortalEntry entry : this.entries.values()) {
            chunks += entry.entryChunks().size();
        }
        return chunks;
    }

    /**
     * Closes and removes every registered entry.
     *
     * <p>Aggregates close failures as suppressed exceptions and clears
     * the registry even when one or more closes fail.</p>
     */
    public void clearAll() {
        assertOwnerThread();
        if (this.cleared && this.entries.isEmpty()) {
            return;
        }
        this.cleared = true;
        CloseFailures closeFailures = new CloseFailures();
        Map<PortalSessionId, DungeonPreparedPortalEntry> snapshot =
                new HashMap<>(this.entries);
        this.entries.clear();
        this.maintenanceOrder.clear();

        for (DungeonPreparedPortalEntry entry : snapshot.values()) {
            try {
                entry.close();
            } catch (RuntimeException | Error failure) {
                closeFailures.capture(failure);
            }
        }

        closeFailures.throwIfPresent();
    }

    public int size() {
        assertOwnerThread();
        return this.entries.size();
    }

    void assertOwnerThread() {
        if (!this.backend.isOwnerThread()) {
            throw new IllegalStateException(
                    "Prepared entry registry must be accessed on the owning server thread"
            );
        }
    }

    private static final class ServerEntryThreadBackend
            implements DungeonSiteClaimBackend {
        private final ServerLevel level;

        ServerEntryThreadBackend(ServerLevel level) {
            this.level = Objects.requireNonNull(level, "level");
        }

        @Override
        public boolean isOwnerThread() {
            return this.level.getServer().isSameThread();
        }
    }

    private static final class CloseFailures {
        private RuntimeException ordinaryFailures;
        private Error firstError;

        void capture(Throwable failure) {
            if (failure instanceof Error error) {
                if (this.firstError == null) {
                    this.firstError = error;
                    if (this.ordinaryFailures != null) {
                        addSuppressed(this.firstError, this.ordinaryFailures);
                        this.ordinaryFailures = null;
                    }
                } else {
                    addSuppressed(this.firstError, error);
                }
                return;
            }
            if (this.firstError != null) {
                addSuppressed(this.firstError, failure);
                return;
            }
            if (this.ordinaryFailures == null) {
                this.ordinaryFailures = new IllegalStateException(
                        "Failed to close one or more prepared portal entries"
                );
            }
            this.ordinaryFailures.addSuppressed(failure);
        }

        void throwIfPresent() {
            if (this.firstError != null) {
                throw this.firstError;
            }
            if (this.ordinaryFailures != null) {
                throw this.ordinaryFailures;
            }
        }

        private static void addSuppressed(Throwable target, Throwable failure) {
            if (target != failure) {
                target.addSuppressed(failure);
            }
        }
    }

    /** Returns and rotates a bounded fair batch without scanning the registry. */
    public List<DungeonPreparedPortalEntry> nextMaintenanceBatch(int maximumEntries) {
        assertOwnerThread();
        if (maximumEntries <= 0 || this.entries.isEmpty()) {
            return List.of();
        }
        int count = Math.min(maximumEntries, this.entries.size());
        ArrayList<DungeonPreparedPortalEntry> batch = new ArrayList<>(count);
        int inspected = 0;
        int available = this.maintenanceOrder.size();
        while (inspected < available && batch.size() < count) {
            PortalSessionId id = this.maintenanceOrder.removeFirst();
            DungeonPreparedPortalEntry entry = this.entries.get(id);
            if (entry != null) {
                batch.add(entry);
                this.maintenanceOrder.addLast(id);
            }
            inspected++;
        }
        return List.copyOf(batch);
    }
}
