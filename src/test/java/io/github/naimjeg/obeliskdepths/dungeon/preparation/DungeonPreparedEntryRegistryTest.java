package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.id.DungeonInstanceId;
import io.github.naimjeg.obeliskdepths.dungeon.id.PortalSessionId;
import io.github.naimjeg.obeliskdepths.dungeon.session.SessionAccessPolicy;
import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DungeonPreparedEntryRegistryTest {
    private DungeonPreparedEntryRegistryTest() {
    }

    public static void main(String[] args) {
        DungeonAsyncTestSupport.bootstrapMinecraft();

        registerAndGet();
        duplicateRegistrationRejected();
        duplicateRejectionPreservesOriginal();
        removeClosesExactlyOnce();
        secondRemoveIsHarmless();
        exactRemoveDoesNotDeleteReplacement();
        clearAllClosesAll();
        clearAllAggregatesCloseFailures();
        clearAllContinuesAfterError();
        registeredSessionIdsAndChunkCount();
        maintenanceBatchesAreBoundedAndFair();
        removeAndCloseIfRemovesMatchingEntries();
        removeAndCloseIfAggregatesFailuresAndContinues();
        removeAndCloseIfIsIdempotentAfterFailure();
        handoffClosesStarterOnlyButRetainsOpen();
        wrongThreadCallsRejected();
        entryChunkListImmutable();
        entryRejectsNullAndDuplicateChunks();
        entryCloseIdempotent();
    }

    private static void exactRemoveDoesNotDeleteReplacement() {
        FakeBackend backend = new FakeBackend();
        DungeonPreparedEntryRegistry registry = new DungeonPreparedEntryRegistry(backend);
        PortalSessionId id = PortalSessionId.create();
        CountingTracker expectedTracker = new CountingTracker();
        CountingTracker replacementTracker = new CountingTracker();
        DungeonPreparedPortalEntry expected = makeEntry(
                id,
                new DungeonPreparationLeaseBundle(List.of(expectedTracker.lease)),
                expectedTracker
        );
        DungeonPreparedPortalEntry replacement = makeEntry(
                id,
                new DungeonPreparationLeaseBundle(List.of(replacementTracker.lease)),
                replacementTracker
        );
        registry.register(replacement);

        check(!registry.removeAndCloseExact(id, expected),
                "exact remove: replacement rejected");
        check(registry.get(id).orElseThrow() == replacement,
                "exact remove: replacement retained");
        check(expectedTracker.closeCount() == 0
                        && replacementTracker.closeCount() == 0,
                "exact remove: neither unrelated owner closed");
        registry.clearAll();
        expected.close();
    }

    private static void maintenanceBatchesAreBoundedAndFair() {
        DungeonPreparedEntryRegistry registry =
                new DungeonPreparedEntryRegistry(new FakeBackend());
        List<DungeonPreparedPortalEntry> entries = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            DungeonPreparedPortalEntry entry = makeEntry(
                    PortalSessionId.create(),
                    new DungeonPreparationLeaseBundle(List.of())
            );
            entries.add(entry);
            registry.register(entry);
        }

        java.util.Set<DungeonPreparedPortalEntry> observed =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (int batchIndex = 0; batchIndex < 3; batchIndex++) {
            List<DungeonPreparedPortalEntry> batch =
                    registry.nextMaintenanceBatch(2);
            check(batch.size() <= 2, "maintenance: fixed batch bound");
            observed.addAll(batch);
        }
        check(observed.size() == entries.size(),
                "maintenance: rotation reaches every entry without starvation");
        check(registry.size() == entries.size(),
                "maintenance: rotation preserves registry membership");
        registry.clearAll();
    }

    private static void registerAndGet() {
        FakeBackend backend = new FakeBackend();
        DungeonPreparedEntryRegistry registry = new DungeonPreparedEntryRegistry(backend);
        CountingTracker tracker = new CountingTracker();
        DungeonPreparationLeaseBundle bundle = new DungeonPreparationLeaseBundle(List.of(tracker.lease));
        DungeonPreparedPortalEntry entry = makeEntry(PortalSessionId.create(), bundle, tracker);

        registry.register(entry);
        check(registry.size() == 1, "register: size 1");
        check(registry.get(entry.portalSessionId()).orElseThrow() == entry, "register: get");
    }

    private static void duplicateRegistrationRejected() {
        FakeBackend backend = new FakeBackend();
        DungeonPreparedEntryRegistry registry = new DungeonPreparedEntryRegistry(backend);
        PortalSessionId id = PortalSessionId.create();
        CountingTracker t1 = new CountingTracker();
        CountingTracker t2 = new CountingTracker();
        DungeonPreparedPortalEntry first = makeEntry(id, new DungeonPreparationLeaseBundle(List.of(t1.lease)), t1);
        DungeonPreparedPortalEntry second = makeEntry(id, new DungeonPreparationLeaseBundle(List.of(t2.lease)), t2);

        registry.register(first);
        try {
            registry.register(second);
            check(false, "duplicate: should throw");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().contains("already has a prepared entry"), "duplicate: message");
        }
        check(registry.get(id).orElseThrow() == first, "duplicate: original preserved");
        check(t2.closeCount() == 0, "duplicate: rejected entry not closed");
        second.close();
        check(t2.closeCount() == 1,
                "duplicate: caller closes retained incoming ownership");
    }

    private static void duplicateRejectionPreservesOriginal() {
        FakeBackend backend = new FakeBackend();
        DungeonPreparedEntryRegistry registry = new DungeonPreparedEntryRegistry(backend);
        PortalSessionId id = PortalSessionId.create();
        CountingTracker t1 = new CountingTracker();
        CountingTracker t2 = new CountingTracker();
        DungeonPreparedPortalEntry first = makeEntry(id, new DungeonPreparationLeaseBundle(List.of(t1.lease)), t1);
        DungeonPreparedPortalEntry second = makeEntry(id, new DungeonPreparationLeaseBundle(List.of(t2.lease)), t2);

        registry.register(first);
        try { registry.register(second); } catch (IllegalStateException ignored) { }

        Optional<DungeonPreparedPortalEntry> found = registry.get(id);
        check(found.orElseThrow() == first, "dup preserve: original intact");
        check(t2.closeCount() == 0, "dup preserve: second not closed");
        second.close();
    }

    private static void removeClosesExactlyOnce() {
        FakeBackend backend = new FakeBackend();
        DungeonPreparedEntryRegistry registry = new DungeonPreparedEntryRegistry(backend);
        CountingTracker tracker = new CountingTracker();
        DungeonPreparationLeaseBundle bundle = new DungeonPreparationLeaseBundle(List.of(tracker.lease));
        PortalSessionId id = PortalSessionId.create();
        registry.register(makeEntry(id, bundle, tracker));

        Optional<DungeonPreparedPortalEntry> removed = registry.removeAndClose(id);
        check(removed.isPresent(), "remove: present");
        check(tracker.closeCount() == 1, "remove: closed once");
        check(registry.size() == 0, "remove: empty");
    }

    private static void secondRemoveIsHarmless() {
        FakeBackend backend = new FakeBackend();
        DungeonPreparedEntryRegistry registry = new DungeonPreparedEntryRegistry(backend);
        CountingTracker tracker = new CountingTracker();
        DungeonPreparationLeaseBundle bundle = new DungeonPreparationLeaseBundle(List.of(tracker.lease));
        PortalSessionId id = PortalSessionId.create();
        registry.register(makeEntry(id, bundle, tracker));

        Optional<DungeonPreparedPortalEntry> first = registry.removeAndClose(id);
        Optional<DungeonPreparedPortalEntry> second = registry.removeAndClose(id);

        check(first.isPresent(), "second remove: first ok");
        check(second.isEmpty(), "second remove: second empty");
        check(tracker.closeCount() == 1, "second remove: closed once total");
    }

    private static void clearAllClosesAll() {
        FakeBackend backend = new FakeBackend();
        DungeonPreparedEntryRegistry registry = new DungeonPreparedEntryRegistry(backend);
        CountingTracker t1 = new CountingTracker();
        CountingTracker t2 = new CountingTracker();
        registry.register(makeEntry(PortalSessionId.create(), new DungeonPreparationLeaseBundle(List.of(t1.lease)), t1));
        registry.register(makeEntry(PortalSessionId.create(), new DungeonPreparationLeaseBundle(List.of(t2.lease)), t2));

        registry.clearAll();
        check(t1.closeCount() == 1, "clearAll: t1 closed");
        check(t2.closeCount() == 1, "clearAll: t2 closed");
        check(registry.size() == 0, "clearAll: empty");
    }

    private static void clearAllAggregatesCloseFailures() {
        FakeBackend backend = new FakeBackend();
        DungeonPreparedEntryRegistry registry = new DungeonPreparedEntryRegistry(backend);
        CountingTracker t1 = new CountingTracker(true);
        CountingTracker t2 = new CountingTracker(true);
        registry.register(makeEntry(PortalSessionId.create(), new DungeonPreparationLeaseBundle(List.of(t1.lease)), t1));
        registry.register(makeEntry(PortalSessionId.create(), new DungeonPreparationLeaseBundle(List.of(t2.lease)), t2));

        try {
            registry.clearAll();
            check(false, "clearAll aggregate: should throw");
        } catch (RuntimeException exception) {
            check(exception.getSuppressed().length >= 1, "clearAll aggregate: has suppressed");
        }
        check(registry.size() == 0, "clearAll aggregate: cleared despite failures");
        check(t1.closeCount() == 1, "clearAll aggregate: t1 attempted");
        check(t2.closeCount() == 1, "clearAll aggregate: t2 attempted");
    }

    private static void registeredSessionIdsAndChunkCount() {
        FakeBackend backend = new FakeBackend();
        DungeonPreparedEntryRegistry registry = new DungeonPreparedEntryRegistry(backend);
        PortalSessionId firstId = PortalSessionId.create();
        PortalSessionId secondId = PortalSessionId.create();
        CountingTracker t1 = new CountingTracker();
        CountingTracker t2 = new CountingTracker();

        registry.register(makeEntry(firstId, new DungeonPreparationLeaseBundle(List.of(t1.lease)), t1));
        registry.register(makeEntry(secondId, new DungeonPreparationLeaseBundle(List.of(t2.lease)), t2));

        check(registry.registeredSessionIds().contains(firstId),
                "registered ids: first present");
        check(registry.registeredSessionIds().contains(secondId),
                "registered ids: second present");
        check(registry.preparedEntryChunkCount() == 2,
                "chunk count: summed");
    }

    private static void removeAndCloseIfRemovesMatchingEntries() {
        FakeBackend backend = new FakeBackend();
        DungeonPreparedEntryRegistry registry = new DungeonPreparedEntryRegistry(backend);
        PortalSessionId removeId = PortalSessionId.create();
        PortalSessionId keepId = PortalSessionId.create();
        CountingTracker removeTracker = new CountingTracker();
        CountingTracker keepTracker = new CountingTracker();

        registry.register(makeEntry(removeId, new DungeonPreparationLeaseBundle(List.of(removeTracker.lease)), removeTracker));
        registry.register(makeEntry(keepId, new DungeonPreparationLeaseBundle(List.of(keepTracker.lease)), keepTracker));

        int removed = registry.removeAndCloseIf(entry -> entry.portalSessionId().equals(removeId));

        check(removed == 1, "removeIf: one removed");
        check(registry.get(removeId).isEmpty(), "removeIf: removed absent");
        check(registry.get(keepId).isPresent(), "removeIf: kept present");
        check(removeTracker.closeCount() == 1, "removeIf: removed closed");
        check(keepTracker.closeCount() == 0, "removeIf: kept open");
    }

    private static void removeAndCloseIfAggregatesFailuresAndContinues() {
        FakeBackend backend = new FakeBackend();
        DungeonPreparedEntryRegistry registry = new DungeonPreparedEntryRegistry(backend);
        CountingTracker throwing = new CountingTracker(true);
        CountingTracker later = new CountingTracker();

        registry.register(makeEntry(PortalSessionId.create(), new DungeonPreparationLeaseBundle(List.of(throwing.lease)), throwing));
        registry.register(makeEntry(PortalSessionId.create(), new DungeonPreparationLeaseBundle(List.of(later.lease)), later));

        try {
            registry.removeAndCloseIf(entry -> true);
            check(false, "removeIf aggregate: should throw");
        } catch (RuntimeException exception) {
            check(exception.getSuppressed().length == 1,
                    "removeIf aggregate: suppressed close failure");
        }

        check(registry.size() == 0, "removeIf aggregate: registry cleared");
        check(throwing.closeCount() == 1, "removeIf aggregate: throwing attempted");
        check(later.closeCount() == 1, "removeIf aggregate: later attempted");
    }

    private static void removeAndCloseIfIsIdempotentAfterFailure() {
        FakeBackend backend = new FakeBackend();
        DungeonPreparedEntryRegistry registry = new DungeonPreparedEntryRegistry(backend);
        CountingTracker throwing = new CountingTracker(true);

        registry.register(makeEntry(PortalSessionId.create(), new DungeonPreparationLeaseBundle(List.of(throwing.lease)), throwing));
        try {
            registry.removeAndCloseIf(entry -> true);
        } catch (RuntimeException expected) {
        }

        int removedAgain = registry.removeAndCloseIf(entry -> true);
        check(removedAgain == 0, "removeIf idempotent: second cleanup empty");
        check(throwing.closeCount() == 1, "removeIf idempotent: no second close");
    }

    private static void handoffClosesStarterOnlyButRetainsOpen() {
        DungeonPreparedEntryRegistry registry =
                new DungeonPreparedEntryRegistry(new FakeBackend());
        PortalSessionId openSession = PortalSessionId.create();
        CountingTracker openTracker = new CountingTracker();
        DungeonPreparedPortalEntry openEntry = makeEntry(
                openSession,
                new DungeonPreparationLeaseBundle(List.of(openTracker.lease)),
                openTracker
        );
        registry.register(openEntry);

        check(DungeonPreparationRuntime.closePreparedEntryAfterHandoff(
                        registry,
                        openSession,
                        openEntry,
                        SessionAccessPolicy.OPEN
                ),
                "OPEN handoff keeps the shared prepared entry");
        check(registry.get(openSession).orElseThrow() == openEntry,
                "OPEN shared prepared entry remains registered");
        check(openTracker.closeCount() == 0,
                "OPEN shared prepared entry is not closed by one handoff");
        registry.clearAll();

        registry = new DungeonPreparedEntryRegistry(new FakeBackend());
        PortalSessionId starterSession = PortalSessionId.create();
        CountingTracker starterTracker = new CountingTracker();
        DungeonPreparedPortalEntry starterEntry = makeEntry(
                starterSession,
                new DungeonPreparationLeaseBundle(
                        List.of(starterTracker.lease)
                ),
                starterTracker
        );
        registry.register(starterEntry);

        check(!DungeonPreparationRuntime.closePreparedEntryAfterHandoff(
                        registry,
                        starterSession,
                        starterEntry,
                        SessionAccessPolicy.STARTER_ONLY
                ),
                "STARTER_ONLY handoff closes its one-shot prepared entry");
        check(registry.get(starterSession).isEmpty(),
                "STARTER_ONLY one-shot prepared entry is removed");
        check(starterTracker.closeCount() == 1,
                "STARTER_ONLY one-shot prepared entry is closed");
    }

    private static void wrongThreadCallsRejected() {
        FakeBackend backend = new FakeBackend();
        DungeonPreparedEntryRegistry registry = new DungeonPreparedEntryRegistry(backend);
        backend.ownerThread = false;
        CountingTracker tracker = new CountingTracker();
        DungeonPreparedPortalEntry entry = makeEntry(
                PortalSessionId.create(),
                new DungeonPreparationLeaseBundle(List.of(tracker.lease)),
                tracker
        );

        try {
            registry.register(entry);
            check(false, "wrong thread register: should throw");
        } catch (IllegalStateException expected) {
        }

        try {
            registry.get(PortalSessionId.create());
            check(false, "wrong thread get: should throw");
        } catch (IllegalStateException expected) {
        }

        try {
            registry.removeAndClose(PortalSessionId.create());
            check(false, "wrong thread remove: should throw");
        } catch (IllegalStateException expected) {
        }

        try {
            registry.size();
            check(false, "wrong thread size: should throw");
        } catch (IllegalStateException expected) {
        }
    }

    private static void entryChunkListImmutable() {
        CountingTracker tracker = new CountingTracker();
        DungeonPreparedPortalEntry entry = makeEntry(
                PortalSessionId.create(),
                new DungeonPreparationLeaseBundle(List.of(tracker.lease)),
                tracker
        );
        List<ChunkPos> chunks = entry.entryChunks();
        try {
            chunks.clear();
            check(false, "immutable chunks: should throw");
        } catch (UnsupportedOperationException expected) {
        }
    }

    private static void clearAllContinuesAfterError() {
        FakeBackend backend = new FakeBackend();
        DungeonPreparedEntryRegistry registry =
                new DungeonPreparedEntryRegistry(backend);
        ErrorLease errorLease = new ErrorLease();
        CountingLease ordinaryLease = new CountingLease();
        registry.register(makeEntry(
                PortalSessionId.create(),
                new DungeonPreparationLeaseBundle(List.of(errorLease))
        ));
        registry.register(makeEntry(
                PortalSessionId.create(),
                new DungeonPreparationLeaseBundle(List.of(ordinaryLease))
        ));

        try {
            registry.clearAll();
            check(false, "clearAll Error: should rethrow");
        } catch (AssertionError expected) {
        }

        check(errorLease.closed == 1,
                "clearAll Error: failing entry attempted once");
        check(ordinaryLease.closed == 1,
                "clearAll Error: remaining entry still closed");
        check(registry.size() == 0,
                "clearAll Error: registry remains empty");
    }

    private static void entryRejectsNullAndDuplicateChunks() {
        CountingTracker nullTracker = new CountingTracker();
        ArrayList<ChunkPos> withNull = new ArrayList<>();
        withNull.add(new ChunkPos(0, 0));
        withNull.add(null);
        try {
            new DungeonPreparedPortalEntry(
                    PortalSessionId.create(),
                    DungeonInstanceId.create(),
                    new DungeonSiteKey(0, 0),
                    new PreparedDungeonDestination(Vec3.ZERO),
                    withNull,
                    new DungeonPreparationLeaseBundle(List.of(nullTracker.lease)),
                    100L
            );
            check(false, "null chunk should be rejected");
        } catch (NullPointerException expected) {
        } finally {
            nullTracker.lease.close();
        }

        CountingTracker duplicateTracker = new CountingTracker();
        try {
            new DungeonPreparedPortalEntry(
                    PortalSessionId.create(),
                    DungeonInstanceId.create(),
                    new DungeonSiteKey(0, 0),
                    new PreparedDungeonDestination(Vec3.ZERO),
                    List.of(new ChunkPos(0, 0), new ChunkPos(0, 0)),
                    new DungeonPreparationLeaseBundle(
                            List.of(duplicateTracker.lease)
                    ),
                    100L
            );
            check(false, "duplicate chunk should be rejected");
        } catch (IllegalArgumentException expected) {
        } finally {
            duplicateTracker.lease.close();
        }
    }

    private static void entryCloseIdempotent() {
        CountingTracker tracker = new CountingTracker();
        DungeonPreparationLeaseBundle bundle = new DungeonPreparationLeaseBundle(List.of(tracker.lease));
        DungeonPreparedPortalEntry entry = makeEntry(PortalSessionId.create(), bundle, tracker);

        entry.close();
        check(tracker.closeCount() == 1, "close idempotent: first");
        entry.close();
        check(tracker.closeCount() == 1, "close idempotent: second no-op");
    }

    private static DungeonPreparedPortalEntry makeEntry(
            PortalSessionId sessionId,
            DungeonPreparationLeaseBundle bundle,
            CountingTracker tracker
    ) {
        return makeEntry(sessionId, bundle);
    }

    private static DungeonPreparedPortalEntry makeEntry(
            PortalSessionId sessionId,
            DungeonPreparationLeaseBundle bundle
    ) {
        return new DungeonPreparedPortalEntry(
                sessionId,
                DungeonInstanceId.create(),
                new DungeonSiteKey(0, 0),
                new PreparedDungeonDestination(new Vec3(0, 64, 0)),
                List.of(new ChunkPos(0, 0)),
                bundle,
                100L
        );
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class FakeBackend implements DungeonSiteClaimBackend {
        boolean ownerThread = true;

        @Override
        public boolean isOwnerThread() {
            return this.ownerThread;
        }
    }

    private static final class CountingLease implements AutoCloseable {
        final boolean throwOnClose;
        int closed;

        CountingLease() {
            this.throwOnClose = false;
        }

        CountingLease(boolean throwOnClose) {
            this.throwOnClose = throwOnClose;
        }

        @Override
        public void close() {
            this.closed++;
            if (this.throwOnClose) {
                throw new RuntimeException("synthetic close failure");
            }
        }
    }

    private static final class ErrorLease implements AutoCloseable {
        int closed;

        @Override
        public void close() {
            this.closed++;
            throw new AssertionError("synthetic lease cleanup Error");
        }
    }

    private static final class CountingTracker {
        final CountingLease lease;
        int closeCount;

        CountingTracker() {
            this(false);
        }

        CountingTracker(boolean throwOnClose) {
            this.lease = new CountingLease(throwOnClose);
        }

        int closeCount() {
            return this.lease.closed;
        }
    }
}
