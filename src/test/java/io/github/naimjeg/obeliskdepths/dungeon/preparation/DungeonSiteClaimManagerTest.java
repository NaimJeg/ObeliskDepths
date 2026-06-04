package io.github.naimjeg.obeliskdepths.dungeon.preparation;

import io.github.naimjeg.obeliskdepths.dungeon.site.DungeonSiteKey;

import java.util.Optional;

public final class DungeonSiteClaimManagerTest {
    private DungeonSiteClaimManagerTest() {
    }

    public static void main(String[] args) {
        DungeonAsyncTestSupport.bootstrapMinecraft();

        firstClaimSucceeds();
        secondOwnerCannotClaimSameKey();
        sameOwnerCannotCreateSecondActiveClaim();
        differentKeysAreIndependent();
        matchingReleaseSucceeds();
        releasedClaimCanBeRestoredExactly();
        restoreCannotReplaceAnotherClaim();
        staleReleaseCannotRemoveReplacement();
        doubleReleaseIsHarmless();
        clearAllRemovesEveryClaim();
        wrongThreadCallsAreRejected();
        tokensAreUnique();
        findReturnsActiveClaim();
        isOwnedByMatches();
        isOwnedByRejectsOtherOwner();
        isOwnedByRejectsMissingKey();
    }

    private static void releasedClaimCanBeRestoredExactly() {
        FakeBackend backend = new FakeBackend();
        DungeonSiteClaimManager manager = new DungeonSiteClaimManager(backend);
        DungeonSiteClaim claim = manager.tryClaim(
                new DungeonSiteKey(0, 0),
                DungeonPreparationJobId.create(),
                100L
        ).orElseThrow();
        check(manager.release(claim), "restore: released");
        check(manager.restore(claim), "restore: exact claim restored");
        check(manager.find(claim.key()).orElseThrow() == claim,
                "restore: identity retained");
    }

    private static void restoreCannotReplaceAnotherClaim() {
        FakeBackend backend = new FakeBackend();
        DungeonSiteClaimManager manager = new DungeonSiteClaimManager(backend);
        DungeonSiteKey key = new DungeonSiteKey(0, 0);
        DungeonSiteClaim old = manager.tryClaim(
                key, DungeonPreparationJobId.create(), 100L
        ).orElseThrow();
        manager.release(old);
        DungeonSiteClaim replacement = manager.tryClaim(
                key, DungeonPreparationJobId.create(), 200L
        ).orElseThrow();
        check(!manager.restore(old), "restore: replacement blocks stale claim");
        check(manager.find(key).orElseThrow() == replacement,
                "restore: replacement preserved");
    }

    private static void firstClaimSucceeds() {
        FakeBackend backend = new FakeBackend();
        DungeonSiteClaimManager mgr = new DungeonSiteClaimManager(backend);
        DungeonSiteKey key = new DungeonSiteKey(0, 0);
        DungeonPreparationJobId owner = DungeonPreparationJobId.create();

        Optional<DungeonSiteClaim> claim = mgr.tryClaim(key, owner, 100L);

        check(claim.isPresent(), "first claim: present");
        check(claim.get().key().equals(key), "first claim: key");
        check(claim.get().ownerJobId().equals(owner), "first claim: owner");
        check(claim.get().acquiredAtGameTime() == 100L, "first claim: game time");
        check(mgr.activeClaimCount() == 1, "first claim: count");
    }

    private static void secondOwnerCannotClaimSameKey() {
        FakeBackend backend = new FakeBackend();
        DungeonSiteClaimManager mgr = new DungeonSiteClaimManager(backend);
        DungeonSiteKey key = new DungeonSiteKey(0, 0);
        DungeonPreparationJobId first = DungeonPreparationJobId.create();
        DungeonPreparationJobId second = DungeonPreparationJobId.create();

        Optional<DungeonSiteClaim> claim1 = mgr.tryClaim(key, first, 100L);
        Optional<DungeonSiteClaim> claim2 = mgr.tryClaim(key, second, 200L);

        check(claim1.isPresent(), "second owner: first ok");
        check(claim2.isEmpty(), "second owner: second rejected");
        check(mgr.activeClaimCount() == 1, "second owner: count unchanged");
    }

    private static void sameOwnerCannotCreateSecondActiveClaim() {
        FakeBackend backend = new FakeBackend();
        DungeonSiteClaimManager mgr = new DungeonSiteClaimManager(backend);
        DungeonSiteKey key = new DungeonSiteKey(0, 0);
        DungeonPreparationJobId owner = DungeonPreparationJobId.create();

        Optional<DungeonSiteClaim> first = mgr.tryClaim(key, owner, 100L);
        Optional<DungeonSiteClaim> second = mgr.tryClaim(key, owner, 200L);

        check(first.isPresent(), "same owner: first ok");
        check(second.isEmpty(), "same owner: second rejected");
    }

    private static void differentKeysAreIndependent() {
        FakeBackend backend = new FakeBackend();
        DungeonSiteClaimManager mgr = new DungeonSiteClaimManager(backend);
        DungeonSiteKey key1 = new DungeonSiteKey(0, 0);
        DungeonSiteKey key2 = new DungeonSiteKey(1, 0);
        DungeonPreparationJobId owner = DungeonPreparationJobId.create();

        Optional<DungeonSiteClaim> claim1 = mgr.tryClaim(key1, owner, 100L);
        Optional<DungeonSiteClaim> claim2 = mgr.tryClaim(key2, owner, 200L);

        check(claim1.isPresent(), "independent: key1 ok");
        check(claim2.isPresent(), "independent: key2 ok");
        check(mgr.activeClaimCount() == 2, "independent: both active");
    }

    private static void matchingReleaseSucceeds() {
        FakeBackend backend = new FakeBackend();
        DungeonSiteClaimManager mgr = new DungeonSiteClaimManager(backend);
        DungeonSiteKey key = new DungeonSiteKey(0, 0);
        DungeonPreparationJobId owner = DungeonPreparationJobId.create();

        DungeonSiteClaim claim = mgr.tryClaim(key, owner, 100L).orElseThrow();
        boolean released = mgr.release(claim);

        check(released, "matching release: true");
        check(mgr.activeClaimCount() == 0, "matching release: removed");
    }

    private static void staleReleaseCannotRemoveReplacement() {
        FakeBackend backend = new FakeBackend();
        DungeonSiteClaimManager mgr = new DungeonSiteClaimManager(backend);
        DungeonSiteKey key = new DungeonSiteKey(0, 0);
        DungeonPreparationJobId owner1 = DungeonPreparationJobId.create();
        DungeonPreparationJobId owner2 = DungeonPreparationJobId.create();

        DungeonSiteClaim firstClaim = mgr.tryClaim(key, owner1, 100L).orElseThrow();
        mgr.release(firstClaim);
        DungeonSiteClaim secondClaim = mgr.tryClaim(key, owner2, 200L).orElseThrow();

        boolean staleRelease = mgr.release(firstClaim);
        check(!staleRelease, "stale: release returned false");
        check(mgr.activeClaimCount() == 1, "stale: replacement intact");
        check(mgr.find(key).orElseThrow() == secondClaim, "stale: replacement is current");
    }

    private static void doubleReleaseIsHarmless() {
        FakeBackend backend = new FakeBackend();
        DungeonSiteClaimManager mgr = new DungeonSiteClaimManager(backend);
        DungeonSiteKey key = new DungeonSiteKey(0, 0);
        DungeonPreparationJobId owner = DungeonPreparationJobId.create();

        DungeonSiteClaim claim = mgr.tryClaim(key, owner, 100L).orElseThrow();
        check(mgr.release(claim), "double: first release");
        check(!mgr.release(claim), "double: second release harmless");
        check(mgr.activeClaimCount() == 0, "double: gone");
    }

    private static void clearAllRemovesEveryClaim() {
        FakeBackend backend = new FakeBackend();
        DungeonSiteClaimManager mgr = new DungeonSiteClaimManager(backend);
        DungeonPreparationJobId owner = DungeonPreparationJobId.create();

        mgr.tryClaim(new DungeonSiteKey(0, 0), owner, 100L);
        mgr.tryClaim(new DungeonSiteKey(1, 0), owner, 200L);
        check(mgr.activeClaimCount() == 2, "clearAll: before");

        mgr.clearAll();
        check(mgr.activeClaimCount() == 0, "clearAll: after");
    }

    private static void wrongThreadCallsAreRejected() {
        FakeBackend backend = new FakeBackend();
        DungeonSiteClaimManager mgr = new DungeonSiteClaimManager(backend);
        backend.ownerThread = false;

        try {
            mgr.tryClaim(new DungeonSiteKey(0, 0), DungeonPreparationJobId.create(), 100L);
            check(false, "wrong thread tryClaim: should throw");
        } catch (IllegalStateException expected) {
        }

        try {
            mgr.release(new DungeonSiteClaim(new DungeonSiteKey(0, 0), DungeonPreparationJobId.create(), 1L, 100L));
            check(false, "wrong thread release: should throw");
        } catch (IllegalStateException expected) {
        }

        try {
            mgr.find(new DungeonSiteKey(0, 0));
            check(false, "wrong thread find: should throw");
        } catch (IllegalStateException expected) {
        }

        try {
            mgr.isOwnedBy(new DungeonSiteKey(0, 0), DungeonPreparationJobId.create());
            check(false, "wrong thread isOwnedBy: should throw");
        } catch (IllegalStateException expected) {
        }

        try {
            mgr.activeClaimCount();
            check(false, "wrong thread count: should throw");
        } catch (IllegalStateException expected) {
        }
    }

    private static void tokensAreUnique() {
        FakeBackend backend = new FakeBackend();
        DungeonSiteClaimManager mgr = new DungeonSiteClaimManager(backend);
        DungeonPreparationJobId owner = DungeonPreparationJobId.create();

        DungeonSiteClaim c1 = mgr.tryClaim(new DungeonSiteKey(0, 0), owner, 100L).orElseThrow();
        mgr.release(c1);
        DungeonSiteClaim c2 = mgr.tryClaim(new DungeonSiteKey(0, 0), owner, 200L).orElseThrow();

        check(c1.token() != c2.token(), "tokens: unique");
    }

    private static void findReturnsActiveClaim() {
        FakeBackend backend = new FakeBackend();
        DungeonSiteClaimManager mgr = new DungeonSiteClaimManager(backend);
        DungeonSiteKey key = new DungeonSiteKey(0, 0);
        DungeonPreparationJobId owner = DungeonPreparationJobId.create();

        check(mgr.find(key).isEmpty(), "find: empty before");

        DungeonSiteClaim claim = mgr.tryClaim(key, owner, 100L).orElseThrow();
        check(mgr.find(key).orElseThrow() == claim, "find: found");

        mgr.release(claim);
        check(mgr.find(key).isEmpty(), "find: empty after");
    }

    private static void isOwnedByMatches() {
        FakeBackend backend = new FakeBackend();
        DungeonSiteClaimManager mgr = new DungeonSiteClaimManager(backend);
        DungeonSiteKey key = new DungeonSiteKey(0, 0);
        DungeonPreparationJobId owner = DungeonPreparationJobId.create();

        mgr.tryClaim(key, owner, 100L);
        check(mgr.isOwnedBy(key, owner), "isOwnedBy: match");
    }

    private static void isOwnedByRejectsOtherOwner() {
        FakeBackend backend = new FakeBackend();
        DungeonSiteClaimManager mgr = new DungeonSiteClaimManager(backend);
        DungeonSiteKey key = new DungeonSiteKey(0, 0);
        DungeonPreparationJobId owner = DungeonPreparationJobId.create();
        DungeonPreparationJobId other = DungeonPreparationJobId.create();

        mgr.tryClaim(key, owner, 100L);
        check(!mgr.isOwnedBy(key, other), "isOwnedBy: other rejected");
    }

    private static void isOwnedByRejectsMissingKey() {
        FakeBackend backend = new FakeBackend();
        DungeonSiteClaimManager mgr = new DungeonSiteClaimManager(backend);
        DungeonPreparationJobId owner = DungeonPreparationJobId.create();

        check(!mgr.isOwnedBy(new DungeonSiteKey(99, 99), owner), "isOwnedBy: missing");
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
}
