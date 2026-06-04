package io.github.naimjeg.obeliskdepths.dungeon.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DungeonRuntimeRecoverySourceTest {
    private DungeonRuntimeRecoverySourceTest() {
    }

    public static void main(String[] args) throws IOException {
        testPhysicalTransitionKeepsPortalBindingSeparate();
        testMissingSessionRecoveryIsProductionNamedAndReservedOnly();
        testReservationAndRemovalRollbackAreConcrete();
        testTickOrderUpdatesRaidBeforeProgressBars();
        testScrollReturnTransactionOrdering();
        testReturnPathsUseExactPhysicalClear();
        testMultipartCleanupPendingBeforeMismatch();
        testClaimedPhysicalChestInvokesArtifactCleanup();
        testChunkLoadAndSiteActivationAreTargeted();
        testIdentityProtectionRemainsAuthoritative();
    }

    private static void testPhysicalTransitionKeepsPortalBindingSeparate() throws IOException {
        String presence = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/presence/DungeonPhysicalPresenceService.java");
        assertFalse(presence.contains("PHYSICAL_INSTANCE" + "_BY_PLAYER"),
                "physical presence service must not keep a runtime cache");
        assertTrue(presence.contains("reconcilePhysicalParticipant"),
                "current physical observation reconciles all persisted memberships");
        assertTrue(presence.contains("unregisterPhysicalParticipantFromAll"),
                "offline/cleared player removes every physical membership");
        assertFalse(presence.contains("findPhysicalInstance" + "ByPlayer"),
                "presence no longer asks for one arbitrary previous instance");
        assertFalse(presence.contains("PlayerDungeonTracker"), "physical presence must not overwrite portal return binding");
        assertFalse(presence.contains("addParticipant("), "physical entry must not add portal participant");

        String lifecycle = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/player/PlayerDungeonLifecycleService.java");
        assertTrue(lifecycle.contains("clearPlayerPhysicalPresence"), "logout/death/dimension change clear physical presence");
    }

    private static void testMissingSessionRecoveryIsProductionNamedAndReservedOnly() throws IOException {
        String lifecycle = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/session/DungeonSessionLifecycle.java");
        assertTrue(lifecycle.contains("recoverMissingSessionForPhysicalEntry"), "missing session recovery has production method");
        assertTrue(lifecycle.contains("requireReservedDungeon"), "recovery requires reserved snapshot");
        assertTrue(lifecycle.contains("data.portalSessions().findByInstance(instance.id(), dungeonLevel.getGameTime())"), "recovery uses authoritative portal session lookup");
        assertTrue(lifecycle.contains("portalSession.get().opener()"), "recovery starter comes from portal opener");
        assertTrue(lifecycle.contains("Refusing physical-entry session recovery without authoritative portal session"), "recovery refuses missing authoritative source");
        assertFalse(lifecycle.contains("instance.participants()"), "recovery must not choose arbitrary instance participant");
        assertTrue(lifecycle.contains("false,") && lifecycle.contains("DungeonSession.createActive"), "recovery does not activate tribute bonus");
    }

    private static void testReservationAndRemovalRollbackAreConcrete() throws IOException {
        String instance = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/instance/DungeonInstanceService.java");
        assertOrder(instance, "prevalidateReservation(data, site, territory, instance);", "data.territories().put(territory);", "reservation prevalidates before mutation");
        assertOrder(instance, "data.territories().put(territory);", "data.instances().put(instance);", "reservation writes territory before instance");
        assertOrder(instance, "data.instances().put(instance);", "data.sites().reserve(site, instance.id(), gameTime);", "reservation writes instance before site");
        assertOrder(instance, "data.sites().reserve(site, instance.id(), gameTime);", "data.roomStates().initializeRoomStates(instance, site);", "reservation writes room states last");
        assertTrue(instance.contains("rollbackFailedReservation("), "reservation has concrete rollback");
        assertOrder(instance, "data.roomStates().removeInstance(instance.id())", "data.sites().releaseReservation(instance.id(), site.key())", "reservation rollback removes room states before site");
        assertOrder(instance, "data.sites().releaseReservation(instance.id(), site.key())", "data.instances().remove(instance.id())", "reservation rollback releases site before instance");
        assertOrder(instance, "data.instances().remove(instance.id())", "data.territories().remove(territory.id())", "reservation rollback removes territory last");
        assertTrue(instance.contains(".releaseReservedDungeonRuntime"), "release delegates to aggregate root");
        assertTrue(instance.contains(".retireReservedDungeonRuntime"), "retire delegates to aggregate root");
        assertFalse(instance.contains("prevalidateRuntimeRemoval"), "old prevalidateRuntimeRemoval must not remain");
        assertFalse(instance.contains("DungeonRuntimeRemovalSnapshot"), "old DungeonRuntimeRemovalSnapshot must not remain");
        assertFalse(instance.contains("rollbackRuntimeRemoval"), "old rollbackRuntimeRemoval must not remain");
        assertFalse(instance.contains("removeRuntimeState"), "old removeRuntimeState must not remain");

        String saved = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/state/DungeonManagerSavedData.java");
        assertTrue(saved.contains("TeardownTransactionSnapshot"), "teardown snapshot must be in aggregate root");
        assertTrue(saved.contains("releaseReservedDungeonRuntime"), "release must be in aggregate root");
        assertTrue(saved.contains("retireReservedDungeonRuntime"), "retire must be in aggregate root");
        assertTrue(saved.contains("captureTeardownSnapshot"), "capture must be in aggregate root");
        assertTrue(saved.contains("applyTeardown"), "apply must be in aggregate root");
        assertTrue(saved.contains("rollbackTeardown"), "rollback must be in aggregate root");
        assertOrder(saved, "removeAllForInstance", "removeInstanceStates", "runtime teardown removes raids before room states");
        assertOrder(saved, "removeInstanceStates", ".territories.remove", "runtime teardown removes room states before territory");
        assertOrder(saved, ".territories.remove", ".instances.remove", "runtime teardown removes instance last");
    }

    private static void testTickOrderUpdatesRaidBeforeProgressBars() throws IOException {
        String tick = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/runtime/DungeonTickHandler.java");
        assertOrder(tick, "tickPhysicalPresence(level);", "tickRaids(level);", "physical presence before raids");
        assertOrder(tick, "tickPhysicalPresence(level);",
                "DungeonSessionCleanup.tickSessions(level);",
                "physical presence reconciles exact ownership before session cleanup");
        assertOrder(tick, "tickRaids(level);", "tickSessions(level);", "raids before progress/session tick block");

        String sessions = tick.substring(tick.indexOf("private static void tickSessions"));
        assertOrder(sessions, "DungeonSessionCleanup.tickSessions(level);", "DungeonSessionProgressBarService.tick(level);", "session runtime before progress bars");
    }

    private static void testScrollReturnTransactionOrdering() throws IOException {
        String service = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/player/PlayerDungeonReturnService.java");
        String scroll = service.substring(service.indexOf("public static PlayerDungeonReturnResult returnPlayerFromScroll"));
        assertOrder(scroll, "PlayerDungeonTracker.currentInstanceId(player)", "teleportToLevel", "scroll snapshots binding before teleport");
        assertOrder(scroll, "return PlayerDungeonReturnResult.TELEPORT_FAILED", "PlayerDungeonTracker.clear(effectivePlayer)", "failed scroll teleport does not clear tracker");
        assertTrue(scroll.contains("boundInstanceId.ifPresent"), "bound cleanup is limited to actual portal binding");
        assertTrue(scroll.contains("DungeonPhysicalPresenceService.clearPlayerPhysicalPresence("),
                "scroll return performs exact all-physical clear");
        assertFalse(scroll.contains("unregisterPhysicalParticipant("),
                "scroll return must not infer individual physical instances");

        String item = read("src/main/java/io/github/naimjeg/obeliskdepths/item/ReturnScrollItem.java");
        assertTrue(item.contains("ReturnScrollUseRules.isSuccessful(result)"), "item consumes only after successful scroll teleport");
    }

    private static void testReturnPathsUseExactPhysicalClear() throws IOException {
        String service = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/player/PlayerDungeonReturnService.java");
        String clear = "DungeonPhysicalPresenceService.clearPlayerPhysicalPresence(";
        assertTrue(countOccurrences(service, clear) >= 2,
                "returnPlayer and returnPlayerFromScroll both clear all physical membership");
        assertFalse(service.contains("DungeonSessionLifecycle.unregisterPhysicalParticipant("),
                "return paths must not infer individual physical sessions");
        assertFalse(service.contains("DungeonSessionPresence"),
                "return paths must not resolve physical instances for cleanup");
        assertFalse(service.contains("LinkedHashSet"),
                "return paths must not deduplicate inferred physical instances");
    }

    private static int countOccurrences(String source, String token) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    private static void testMultipartCleanupPendingBeforeMismatch() throws IOException {
        String cleanup = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/artifact/DungeonRuntimeArtifactCleanupService.java");
        assertOrder(cleanup, "areRewardStructureChunksLoaded", "isCompleteRewardStructure", "all part chunks are loaded before structure validation");
        assertTrue(cleanup.contains("artifact.markPending(nextRetry)"), "unloaded artifacts are marked pending");
        assertTrue(cleanup.contains("artifact.recordMismatch(nextRetry)"), "real mismatches retain diagnostics");

        String chest = read("src/main/java/io/github/naimjeg/obeliskdepths/block/ObeliskChestBlock.java");
        assertTrue(chest.contains("getRewardStructurePartPositions"), "chest exposes read-only part positions");
        assertTrue(chest.contains("areRewardStructureChunksLoaded"), "chest exposes chunk-loaded helper");
        assertTrue(chest.contains("return false;") && chest.contains("removeRewardStructure"), "removal cannot report partial success");
    }

    private static void testClaimedPhysicalChestInvokesArtifactCleanup() throws IOException {
        String reward = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/reward/DungeonRewardClaim.java");
        String completion = reward.substring(reward.indexOf("public static boolean completePhysicalRewardSpray"));
        assertOrder(completion, "data.rewards().markClaimed(reward)", "cleanupClaimedPhysicalRewardArtifact", "physical chest cleanup happens after claim commit");
        assertTrue(reward.contains("tryClaimVirtualReward"), "virtual reward path remains separate");
    }

    private static void testChunkLoadAndSiteActivationAreTargeted() throws IOException {
        String events = read("src/main/java/io/github/naimjeg/obeliskdepths/event/DungeonWorldAccessEvents.java");
        assertTrue(events.contains("ChunkEvent.Load"), "chunk-load cleanup handler is registered");
        assertTrue(events.contains("cleanupPendingArtifactsInChunk"), "chunk-load cleanup uses targeted artifact method");

        String instance = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/instance/DungeonInstanceService.java");
        assertOrder(instance, "reconcileStaleRewardArtifactsForSite", "DungeonInstanceFactory.create", "stale artifacts reconcile before activation");

        String cleanup = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/artifact/DungeonRuntimeArtifactCleanupService.java");
        assertTrue(cleanup.contains("site.bounds().contains(artifact.pos().get())"), "stale reconciliation selects persisted artifact positions within site");
        assertFalse(cleanup.contains("for (int x"), "artifact cleanup must not scan full dungeon bounds");
    }

    private static void testIdentityProtectionRemainsAuthoritative() throws IOException {
        String cleanup = read("src/main/java/io/github/naimjeg/obeliskdepths/dungeon/artifact/DungeonRuntimeArtifactCleanupService.java");
        assertTrue(cleanup.contains("chest.rewardId().filter"), "cleanup validates reward id before removal");
        assertTrue(cleanup.contains("chest.instanceId().filter"), "cleanup validates instance id before removal");
        String identityBranch = cleanup.substring(cleanup.indexOf("block entity identity mismatch"));
        assertOrder(identityBranch, "block entity identity mismatch", "return;", "identity mismatch does not delete block");
        assertOrder(identityBranch, "return;", "removeRewardStructure", "identity mismatch returns before any removal path");
    }

    private static String read(String file) throws IOException {
        return Files.readString(Path.of(file));
    }

    private static void assertOrder(
            String source,
            String first,
            String second,
            String message
    ) {
        int firstIndex = source.indexOf(first);
        int secondIndex = source.indexOf(second);
        if (firstIndex < 0 || secondIndex < 0 || firstIndex >= secondIndex) {
            throw new AssertionError(message + ": expected '" + first + "' before '" + second + "'");
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }
}
